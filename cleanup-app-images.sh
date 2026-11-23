#!/usr/bin/env bash
# Ubuntu App EC2에서 성공한 배포 뒤 실행한다. 다른 저장소·컨테이너·볼륨은 정리하지 않는다.
set -euo pipefail

image="${1:?현재 앱 이미지 주소가 필요합니다}"
previous_id="${2:-}"
repository="${image%%@*}"
if [[ "${repository##*/}" == *:* ]]; then
    repository="${repository%:*}"
fi
current_id="$(docker image inspect --format '{{.Id}}' "$image")"
# 직전 정상 이미지 ID를 확인하지 못한 경우 삭제 없이 끝낸다.
if [ -z "$previous_id" ]; then
    echo '[deploy] 직전 이미지가 없어 이미지 정리를 건너뜁니다.'
    exit 0
fi
cutoff=$(( $(date +%s) - 72 * 60 * 60 ))
candidates="$(docker image ls --no-trunc --quiet --filter "reference=$repository" | sort -u)"
for candidate in $candidates; do
    if [ "$candidate" = "$current_id" ] || [ "$candidate" = "$previous_id" ]; then
        continue
    fi
    created="$(docker image inspect --format '{{.Created}}' "$candidate")"
    created_epoch="$(date -d "$created" +%s)"
    if (( created_epoch >= cutoff )); then
        continue
    fi
    # -f를 사용하지 않는다. 중지된 컨테이너를 포함해 사용 중인 이미지는 Docker가 보호한다.
    if ! docker image rm "$candidate" >/dev/null; then
        echo '[deploy] 사용 중이거나 여러 참조가 있는 앱 이미지는 보존합니다.' >&2
    fi
done
