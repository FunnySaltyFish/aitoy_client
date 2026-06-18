#!/usr/bin/env python3
from __future__ import annotations

import os
import argparse
import json
import urllib.parse
import urllib.request


BASE_URL = "https://aitoy.funnysaltyfish.fun"
ADMIN_KEY = os.environ.get("AITOY_ADMIN_KEY", "FunnyAIToy")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="读取 AI Toy 最近崩溃日志")
    parser.add_argument("--base-url", default=BASE_URL)
    parser.add_argument("--admin-key", default=ADMIN_KEY)
    parser.add_argument("--page", type=int, default=1)
    parser.add_argument("--size", type=int, default=10)
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--keyword")
    parser.add_argument("--json", action="store_true", help="输出原始 JSON")
    return parser.parse_args()


def request_json(args: argparse.Namespace) -> dict:
    params = {
        "page": args.page,
        "size": args.size,
        "platform": "android",
        "versionCode": args.version_code,
        "keyword": args.keyword,
    }
    query = urllib.parse.urlencode({k: v for k, v in params.items() if v not in (None, "")})
    req = urllib.request.Request(
        f"{args.base_url.rstrip('/')}/api/diagnostics/crashes?{query}",
        headers={"X-Admin-Key": args.admin_key},
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    if payload.get("code") != 0:
        raise RuntimeError(payload.get("msg") or payload)
    return payload


def main() -> int:
    args = parse_args()
    payload = request_json(args)
    data = payload.get("data") or {}
    if args.json:
        print(json.dumps(data, ensure_ascii=False, indent=2))
        return 0
    items = data.get("items") or []
    print(f"崩溃日志：{len(items)} / {data.get('total', 0)}")
    for index, item in enumerate(items, 1):
        print()
        print(f"[{index}] {item.get('reportId')}  {item.get('versionName')} ({item.get('versionCode')})")
        print(f"设备: {item.get('deviceModel')}  Android {item.get('androidVersion')}")
        print(f"Fingerprint: {item.get('fingerprint')}")
        trace = (item.get("exceptionTrace") or "").strip()
        print(trace[:4000])
        logs = item.get("recentLogs") or []
        if logs:
            print("\n最近日志:")
            for line in logs[-30:]:
                print(line)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
