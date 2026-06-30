#!/usr/bin/env python3
"""Generate the local Buttplug support matrix as CSV.

The Kotlin registry is the source of truth for local handler status. The YAML
files are the source of truth for protocol ids, communication types, and output
types.
"""

from __future__ import annotations

import argparse
import csv
import os
import re
import sys
from pathlib import Path
from typing import Any

import yaml


DEFAULT_PROTOCOL_DIR = Path("composeApp/src/commonMain/composeResources/files/buttplug/protocols")
DEFAULT_REGISTRY = Path(
    "composeApp/src/commonMain/kotlin/com/funny/aitoy/buttplug/ButtplugSupportMatrix.kt"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--protocol-dir", type=Path, default=DEFAULT_PROTOCOL_DIR)
    parser.add_argument("--registry", type=Path, default=DEFAULT_REGISTRY)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def load_registry(path: Path) -> dict[str, dict[str, str]]:
    entries: dict[str, dict[str, str]] = {}
    for block in descriptor_blocks(path.read_text(encoding="utf-8")):
        protocol_id = string_value(block, "protocolId")
        if not protocol_id:
            continue
        entries[protocol_id] = {
            "handler_source": enum_value(block, "source") or "",
            "support_status": enum_value(block, "status") or "",
            "notes": string_value(block, "notes") or "",
        }
    for file_name, notes in [
        ("SimpleVibrateProtocolPlans.kt", "Simple vibrate handler ported from Buttplug protocol_impl"),
        ("StatefulVibrateProtocolPlans.kt", "Stateful vibrate handler ported from Buttplug protocol_impl"),
        ("ScalarProtocolPlans.kt", "Scalar output handler ported from Buttplug protocol_impl"),
        ("LinearProtocolPlans.kt", "Linear output handler ported from Buttplug protocol_impl"),
        ("MixedOutputProtocolPlans.kt", "Mixed scalar/linear output handler ported from Buttplug protocol_impl"),
        ("LeloProtocolPlans.kt", "Lelo security handshake and output handler ported from Buttplug protocol_impl"),
        ("HoneyPlayBoxProtocolPlans.kt", "HoneyPlayBox signed frame handler ported from Buttplug protocol_impl"),
        ("VibCrafterProtocolPlans.kt", "VibCrafter AES authentication handler ported from Buttplug protocol_impl"),
        ("FlufferProtocolPlans.kt", "Fluffer AES authentication and scalar handler ported from Buttplug protocol_impl"),
        ("HandyProtocolPlans.kt", "The Handy protobuf handler ported from Buttplug protocol_impl"),
        ("LegacyStpihkalProtocolPlans.kt", "Legacy STPIHKAL LevelCmd/LinearCmd handler ported from Buttplug device config"),
        ("LovenseProtocolPlans.kt", "Lovense DeviceType and output handler ported from Buttplug protocol_impl"),
        ("SpecializedProtocolPlans.kt", "Specialized handler ported from Buttplug protocol_impl"),
        ("ExternalButtplugTransportPlans.kt", "Controlled through external Buttplug/Intiface transport"),
    ]:
        for protocol_id in load_plan_protocol_ids(path.with_name(file_name)):
            entries.setdefault(
                protocol_id,
                {
                    "handler_source": "ButtplugProtocolImpl",
                    "support_status": "Controllable",
                    "notes": notes,
                },
            )
    return entries


def load_plan_protocol_ids(path: Path) -> list[str]:
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8")
    ids = re.findall(r'protocolId[^=]*=\s*"([^"]+)"', text)
    for block in re.findall(r'protocolIds[^=]*=\s*setOf\((.*?)\)', text, flags=re.S):
        ids.extend(re.findall(r'"([^"]+)"', block))
    return ids


def descriptor_blocks(text: str) -> list[str]:
    blocks: list[str] = []
    collecting = False
    balance = 0
    current: list[str] = []
    for line in text.splitlines():
        if not collecting and "ButtplugLocalHandlerDescriptor(" not in line:
            continue
        collecting = True
        current.append(line)
        balance += line.count("(") - line.count(")")
        if collecting and balance <= 0:
            blocks.append("\n".join(current))
            collecting = False
            balance = 0
            current = []
    return blocks


def string_value(block: str, key: str) -> str:
    match = re.search(rf"{key}\s*=\s*\"([^\"]*)\"", block)
    return match.group(1) if match else ""


def enum_value(block: str, key: str) -> str:
    match = re.search(rf"{key}\s*=\s*(?:Buttplug\w+\.)?(\w+)", block)
    return match.group(1) if match else ""


def yaml_dict(path: Path) -> dict[str, Any]:
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    return data if isinstance(data, dict) else {}


def communication_types(protocol: dict[str, Any]) -> list[str]:
    result: set[str] = set()
    for item in protocol.get("communication") or []:
        if isinstance(item, dict):
            result.update(str(key).lower() for key in item.keys())
    return sorted(result)


def output_types(protocol: dict[str, Any]) -> list[str]:
    result: set[str] = set()
    collect_output_types(protocol.get("defaults"), result)
    for config in protocol.get("configurations") or []:
        collect_output_types(config, result)
    return sorted(result)


def collect_output_types(node: Any, result: set[str]) -> None:
    if not isinstance(node, dict):
        return
    for feature in node.get("features") or []:
        if not isinstance(feature, dict):
            continue
        output = feature.get("output")
        if isinstance(output, dict):
            result.update(str(key) for key in output.keys())


def inferred_status(protocol: dict[str, Any], handler: dict[str, str] | None) -> str:
    if handler:
        return handler["support_status"]
    comms = set(communication_types(protocol))
    if (not comms or "btle" in comms) and not output_types(protocol):
        return "RecognizedNoOutput"
    if not comms or "btle" in comms:
        return "RecognizedMissingHandler"
    return "RecognizedUnsupportedTransport"


def rows(protocol_dir: Path, registry: dict[str, dict[str, str]]) -> list[dict[str, str]]:
    matrix: list[dict[str, str]] = []
    for path in sorted(protocol_dir.glob("*.yml")):
        protocol_id = path.stem
        protocol = yaml_dict(path)
        handler = registry.get(protocol_id)
        matrix.append(
            {
                "protocol_id": protocol_id,
                "display_name": str((protocol.get("defaults") or {}).get("name") or protocol_id),
                "communication_types": "|".join(communication_types(protocol)),
                "output_types": "|".join(output_types(protocol)),
                "support_status": inferred_status(protocol, handler),
                "handler_source": handler["handler_source"] if handler else "",
                "notes": handler["notes"] if handler else "",
            }
        )
    return matrix


def main() -> int:
    args = parse_args()
    registry = load_registry(args.registry)
    matrix = rows(args.protocol_dir, registry)
    output = args.output.open("w", newline="", encoding="utf-8") if args.output else sys.stdout
    with output:
        writer = csv.DictWriter(
            output,
            fieldnames=[
                "protocol_id",
                "display_name",
                "communication_types",
                "output_types",
                "support_status",
                "handler_source",
                "notes",
            ],
        )
        writer.writeheader()
        writer.writerows(matrix)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BrokenPipeError:
        os._exit(0)
