#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

import requests


# 直接编辑这里，然后运行：
#   python scripts/update_app_version.py
BASE_URL = "https://aitoy.funnysaltyfish.fun"
ADMIN_KEY = os.environ.get("AITOY_ADMIN_KEY", "FunnyAIToy")
PLATFORM = "android"
CHANNEL = "common"
PACKAGE_NAME = "com.funny.aitoy"
UPDATE_LOG = """AI Toy Bridge 0.3.0

第四个版本：
- 增加国内更多设备的理论支持，包括谜姬、醉清风等
- 控制模式重写，支持模式和强度分开控制的设备

欢迎各位尝试手动的设备。如果不支持的话，可以在群里反馈。给出对应的官方 App 或小程序名称（优先小程序），我将会尝试分析接入

第二个版本：
- 修改之前部分模式中错误编写的字段，现在应该支持更多设备了。
- 新增广播能力控制，理论上目前适配 Kisstoy 和 Cachito，欢迎再次尝试
- 优化控制体验：现在不需要额外点击按钮，直接拖动即可快速尝试，并可以保存设备并修改备注
- 使用手上的某个设备完成了实际验证，可用！
"""


@dataclass(frozen=True)
class AndroidPackageInfo:
    package_name: str
    version_code: int
    version_name: str


APK_SEARCH_DIRS = (
    Path("composeApp/build/outputs/apk/release"),
    Path("composeApp/build/outputs/apk"),
    Path("composeApp/release"),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="上传 AI Toy 应用版本")
    parser.add_argument("--base-url", default=BASE_URL)
    parser.add_argument("--admin-key", default=ADMIN_KEY)
    parser.add_argument("--apk", type=Path, help="不指定时自动查找最新 release APK")
    parser.add_argument("--package", default=PACKAGE_NAME, help="已安装应用包名")
    parser.add_argument("--adb", default="adb", help="adb 命令路径")
    parser.add_argument("--channel", default=CHANNEL)
    parser.add_argument("--platform", default=PLATFORM)
    parser.add_argument("--yes", action="store_true", help="跳过确认")
    return parser.parse_args()


def find_latest_apk(root: Path) -> Path:
    candidates: list[Path] = []
    for search_dir in APK_SEARCH_DIRS:
        resolved_dir = root / search_dir
        if not resolved_dir.is_dir():
            continue
        candidates.extend(
            path for path in resolved_dir.rglob("*.apk")
            if "debug" not in path.name.lower() and "release" in str(path).lower()
        )
    if not candidates:
        search_text = ", ".join(str(path) for path in APK_SEARCH_DIRS)
        raise FileNotFoundError(f"未找到 release APK，已搜索：{search_text}")
    return max(candidates, key=lambda path: path.stat().st_mtime)


def resolve_apk_path(root: Path, apk_arg: Path | None) -> Path:
    if apk_arg is None:
        return find_latest_apk(root)
    return apk_arg if apk_arg.is_absolute() else root / apk_arg


def run_adb(args: argparse.Namespace, *adb_args: str) -> str:
    try:
        return subprocess.run(
            [args.adb, *adb_args],
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        ).stdout
    except FileNotFoundError as exc:
        raise RuntimeError("未找到 adb，请确认 Android SDK platform-tools 已加入 PATH") from exc
    except subprocess.CalledProcessError as exc:
        output = (exc.stderr or exc.stdout or "").strip()
        raise RuntimeError(f"adb 执行失败：{output or exc}") from exc


def read_package_info(args: argparse.Namespace) -> AndroidPackageInfo:
    output = run_adb(args, "shell", "dumpsys", "package", args.package)
    if "Unable to find package" in output or "Packages:" not in output:
        raise RuntimeError(f"设备上未找到已安装应用：{args.package}，请先安装待发布版本")

    package_match = re.search(r"Package \[([^\]]+)]", output)
    version_code_match = re.search(r"\bversionCode=(\d+)\b", output)
    version_name_match = re.search(r"\bversionName=([^\r\n]+)", output)
    if not version_code_match or not version_name_match:
        raise RuntimeError("无法从 adb 输出解析应用版本信息")

    return AndroidPackageInfo(
        package_name=package_match.group(1) if package_match else args.package,
        version_code=int(version_code_match.group(1)),
        version_name=version_name_match.group(1).strip(),
    )


def read_metadata_package_info(apk_path: Path) -> AndroidPackageInfo:
    metadata_path = apk_path.parent / "output-metadata.json"
    if not metadata_path.is_file():
        raise FileNotFoundError(f"未找到版本信息文件：{metadata_path}")

    with metadata_path.open("r", encoding="utf-8") as file:
        metadata = json.load(file)
    elements = metadata.get("elements") or []
    if not elements:
        raise RuntimeError("版本信息文件缺少 elements")
    element = elements[0]
    return AndroidPackageInfo(
        package_name=metadata.get("applicationId") or PACKAGE_NAME,
        version_code=int(element["versionCode"]),
        version_name=str(element["versionName"]),
    )


def resolve_package_info(args: argparse.Namespace, apk_path: Path) -> AndroidPackageInfo:
    try:
        return read_metadata_package_info(apk_path)
    except Exception as metadata_error:
        print(f"未能从构建产物读取版本信息，改用 adb：{metadata_error}")
        return read_package_info(args)


def sanitize_archive_part(value: str) -> str:
    safe_value = re.sub(r"[^0-9A-Za-z._-]+", "-", value.strip())
    return safe_value.strip(".-") or "unknown"


def archive_release(root: Path, apk_path: Path, package_info: AndroidPackageInfo, channel: str) -> Path:
    archive_dir_name = (
        f"{sanitize_archive_part(package_info.version_name)}-"
        f"{sanitize_archive_part(channel)}"
    )
    archive_dir = root / "archive" / archive_dir_name
    archive_dir.mkdir(parents=True, exist_ok=True)

    shutil.copy2(apk_path, archive_dir / apk_path.name)

    metadata_path = apk_path.parent / "output-metadata.json"
    if metadata_path.is_file():
        shutil.copy2(metadata_path, archive_dir / metadata_path.name)

    baseline_profiles_dir = apk_path.parent / "baselineProfiles"
    if baseline_profiles_dir.is_dir():
        shutil.copytree(
            baseline_profiles_dir,
            archive_dir / baseline_profiles_dir.name,
            dirs_exist_ok=True,
        )

    return archive_dir


def response_error_message(resp: requests.Response) -> str:
    try:
        payload = resp.json()
    except ValueError:
        payload = None

    if isinstance(payload, dict):
        message = payload.get("msg") or payload.get("message") or payload
    else:
        message = resp.text.strip()

    detail = str(message).strip()
    if detail:
        return f"{resp.status_code} {resp.reason}: {detail}"
    return f"{resp.status_code} {resp.reason}"


def main() -> int:
    args = parse_args()
    root = Path(__file__).resolve().parents[1]
    apk_path = resolve_apk_path(root, args.apk)
    if not apk_path.is_file():
        raise FileNotFoundError(f"未找到应用文件：{apk_path}")

    package_info = resolve_package_info(args, apk_path)

    print(f"上传文件: {apk_path}")
    print(f"已安装包: {package_info.package_name}")
    print(f"版本: {package_info.version_name} ({package_info.version_code})")
    print(f"平台/渠道: {args.platform}/{args.channel}")
    print(f"最低支持版本: {package_info.version_code}")
    print("更新日志:")
    print(UPDATE_LOG.strip())
    if not args.yes:
        input("确认上传请按 Enter...")

    data = {
        "platform": args.platform,
        "channel": args.channel,
        "versionCode": package_info.version_code,
        "versionName": package_info.version_name,
        "minSupportedVersionCode": package_info.version_code,
        "fileExtension": apk_path.suffix.lstrip(".") or "apk",
        "updateLog": UPDATE_LOG.strip(),
    }
    with apk_path.open("rb") as file:
        resp = requests.post(
            f"{args.base_url.rstrip('/')}/api/app/versions",
            headers={"X-Admin-Key": args.admin_key},
            data=data,
            files={"apk": (apk_path.name, file, "application/vnd.android.package-archive")},
            timeout=(15, 600),
        )
    if not resp.ok:
        raise RuntimeError(response_error_message(resp))
    payload = resp.json()
    if payload.get("code") != 0:
        raise RuntimeError(payload.get("msg") or payload)
    version = (payload.get("data") or {}).get("version") or {}
    print("上传完成")
    print(f"下载地址: {version.get('downloadUrl')}")
    print(f"下载页: {version.get('downloadPageUrl')}")
    archive_dir = archive_release(root, apk_path, package_info, args.channel)
    print(f"本地归档: {archive_dir}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"发布失败: {exc}", file=sys.stderr)
        raise SystemExit(1)
