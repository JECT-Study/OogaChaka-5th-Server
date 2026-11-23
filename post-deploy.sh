#!/usr/bin/env bash
# 배포가 성공한 뒤 별도 프로세스·시간 제한에서 실행한다. 이력 저장 실패 시 삭제하지 않는다.
set -euo pipefail
umask 077

image="${1:?현재 이미지 주소가 필요합니다}"
context="${2:?배포에서 기록한 이전 이미지 파일이 필요합니다}"
DEST=/opt/ooga/app
SOURCE="$(cd "$(dirname "$0")" && pwd)"

[ -f "$context" ] || { echo '[deploy] 이전 이미지 기록이 없어 정리를 건너뜁니다.'; exit 0; }
exec 9>>"$DEST/.deploy.lock"
if ! flock -n 9; then
    echo '[deploy] 다른 배포가 진행 중이므로 이미지 정리를 건너뜁니다.'
    exit 0
fi
current_id="$(docker image inspect --format '{{.Id}}' "$image")"
running_id="$(docker inspect --format '{{.Image}}' oogachaka-app)"
# 앞 배포와 정리 사이에 더 최신 버전이 적용됐다면 앞 버전 기준으로 삭제하지 않는다.
if [ "$running_id" != "$current_id" ] ||
   [ "$(docker inspect --format '{{.State.Running}}' oogachaka-app)" != true ]; then
    echo '[deploy] 현재 컨테이너가 배포 결과와 달라 정리를 건너뜁니다.'
    exit 0
fi

state_tmp="$(mktemp "$DEST/.successful-images.XXXXXX")"
trap 'rm -f "$state_tmp"' EXIT
python3 - "$DEST/.successful-images.json" "$context" "$current_id" "$state_tmp" <<'PY'
import json
from pathlib import Path
import re
import sys

state, context, current, output = sys.argv[1:]
valid = lambda value: isinstance(value, str) and bool(re.fullmatch(r"sha256:[0-9a-f]{64}", value))
before = Path(context).read_text().strip()
if not valid(current) or (before and not valid(before)):
    raise SystemExit("[deploy] 이미지 ID 형식이 올바르지 않아 삭제하지 않습니다.")
previous = before if before != current else ""
if before == current:
    # 같은 버전 재배포는 이전 기록이 현재 컨테이너와 맞는 경우에만 과거 버전을 신뢰한다.
    try:
        old = json.loads(Path(state).read_text())
    except (FileNotFoundError, ValueError):
        old = {}
    if isinstance(old, dict) and old.get("current") == current and valid(old.get("previous")):
        previous = old["previous"]
Path(output).write_text(json.dumps({"current": current, "previous": previous}) + "\n")
PY
# 한 파일을 같은 파일시스템에서 rename한다. 이전 두 파일은 신뢰하지 않고 실제 교체 전 ID를 사용한다.
mv -f "$state_tmp" "$DEST/.successful-images.json"
previous_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["previous"])' "$DEST/.successful-images.json")"
bash "$SOURCE/cleanup-app-images.sh" "$image" "$previous_id"
