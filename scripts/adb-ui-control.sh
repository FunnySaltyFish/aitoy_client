#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
PACKAGE="${PACKAGE:-com.funny.aitoy.debug}"
ACTION="${1:-help}"
DEVICE_ADDRESS="${2:-01:00:30:07:16:E3}"

tag_to_bounds() {
  local tag="$1"
  local xml
  local xml_file
  local coords
  local attempt
  for attempt in $(seq 1 8); do
    xml="$("$ADB" exec-out uiautomator dump /dev/tty 2>/dev/null)"
    xml_file="$(mktemp)"
    printf '%s' "$xml" > "$xml_file"
    if coords="$(python - "$tag" "$PACKAGE" "$xml_file" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

tag, package, xml_file = sys.argv[1:4]
xml = open(xml_file, encoding="utf-8").read()
xml = xml[xml.find("<?xml"):]
xml = xml[:xml.find("</hierarchy>") + len("</hierarchy>")]
root = ET.fromstring(xml)
expected = {tag, f"{package}:id/{tag}"}
for node in root.iter("node"):
    if node.attrib.get("resource-id") in expected:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib["bounds"])
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            print(x1, y1, x2, y2)
            raise SystemExit(0)
raise SystemExit(1)
PY
    )"; then
      rm -f "$xml_file"
      printf '%s\n' "$coords"
      return 0
    fi
    rm -f "$xml_file"
    "$ADB" shell input swipe 600 2200 600 700 350
    sleep 1
  done
  echo "找不到 Tag: $tag" >&2
  return 1
}

tap_tag() {
  local tag="$1"
  local x1 y1 x2 y2 x y
  read -r x1 y1 x2 y2 < <(tag_to_bounds "$tag")
  x=$(( (x1 + x2) / 2 ))
  y=$(( (y1 + y2) / 2 ))
  "$ADB" shell input tap "$x" "$y"
}

set_intensity() {
  local level="${1:-1}"
  local x1 y1 x2 y2 x y
  if (( level < 1 || level > 3 )); then
    echo "DSJM 强度仅支持 1、2、3" >&2
    return 1
  fi
  read -r x1 y1 x2 y2 < <(tag_to_bounds intensity_slider)
  x=$(( x1 + (x2 - x1) * (level - 1) / 2 ))
  y=$(( (y1 + y2) / 2 ))
  "$ADB" shell input tap "$x" "$y"
}

case "$ACTION" in
  scan)
    tap_tag scan_toggle
    ;;
  connect)
    tag="device_connect_${DEVICE_ADDRESS//:/_}"
    tap_tag "$tag"
    ;;
  test)
    tap_tag control_test
    ;;
  intensity)
    set_intensity "${2:-1}"
    ;;
  stop)
    tap_tag control_stop
    ;;
  dsjm-smoke)
    tap_tag scan_toggle
    sleep 6
    tap_tag "device_connect_${DEVICE_ADDRESS//:/_}"
    sleep 5
    set_intensity 1
    tap_tag control_test
    sleep 2
    tap_tag control_stop
    ;;
  logs)
    "$ADB" logcat -d -s AiToyBle AiToyRelay
    ;;
  *)
    cat <<EOF
用法：
  $0 scan
  $0 connect [BLE_ADDRESS]
  $0 test
  $0 intensity <1|2|3>
  $0 stop
  $0 dsjm-smoke [BLE_ADDRESS]
  $0 logs
EOF
    ;;
esac
