#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

import requests


# 直接编辑这里，然后运行：
#   python scripts/update_app_version.py
BASE_URL = "https://aitoy.funnysaltyfish.fun"
ADMIN_KEY = "FunnyTrans"
PLATFORM = "android"
CHANNEL = "common"
PACKAGE_NAME = "com.funny.aitoy"
UPDATE_LOG = """AI Toy Bridge 0.1.0

首个版本：
- 可以寻找并连接附近的蓝牙小玩具。
- 支持调节强度、立即停止和自动停止。
- 可以把手机作为桥接端，让 AI 伙伴控制已连接设备。
- 提供高级指令导入和调试日志，便于适配更多设备。
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
    resp.raise_for_status()
    payload = resp.json()
    if payload.get("code") != 0:
        raise RuntimeError(payload.get("msg") or payload)
    version = (payload.get("data") or {}).get("version") or {}
    print("上传完成")
    print(f"下载地址: {version.get('downloadUrl')}")
    print(f"下载页: {version.get('downloadPageUrl')}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"发布失败: {exc}", file=sys.stderr)
        raise SystemExit(1)
