#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

import requests


# 直接编辑这里，然后运行：
#   python scripts/update_app_version.py --yes
BASE_URL = "https://aitoy.funnysaltyfish.fun"
ADMIN_KEY = "FunnyTrans"
PLATFORM = "android"
CHANNEL = "common"
VERSION_CODE = 1
VERSION_NAME = "1.0"
MIN_SUPPORTED_VERSION_CODE = 1
APK_PATH = Path("composeApp/release/composeApp-release.apk")
UPDATE_LOG = """AI Toy 1.0

- 优化设备连接体验。
- 完善应用更新与下载流程。
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="上传 AI Toy 应用版本")
    parser.add_argument("--base-url", default=BASE_URL)
    parser.add_argument("--admin-key", default=ADMIN_KEY)
    parser.add_argument("--apk", type=Path, default=APK_PATH)
    parser.add_argument("--version-code", type=int, default=VERSION_CODE)
    parser.add_argument("--version-name", default=VERSION_NAME)
    parser.add_argument("--min-supported-version-code", type=int, default=MIN_SUPPORTED_VERSION_CODE)
    parser.add_argument("--channel", default=CHANNEL)
    parser.add_argument("--platform", default=PLATFORM)
    parser.add_argument("--yes", action="store_true", help="跳过确认")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = Path(__file__).resolve().parents[1]
    apk_path = args.apk if args.apk.is_absolute() else root / args.apk
    if not apk_path.is_file():
        raise FileNotFoundError(f"未找到应用文件：{apk_path}")

    print(f"上传文件: {apk_path}")
    print(f"版本: {args.version_name} ({args.version_code})")
    print(f"平台/渠道: {args.platform}/{args.channel}")
    print(f"最低支持版本: {args.min_supported_version_code}")
    if not args.yes:
        input("确认上传请按 Enter...")

    data = {
        "platform": args.platform,
        "channel": args.channel,
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "minSupportedVersionCode": args.min_supported_version_code,
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
    raise SystemExit(main())
