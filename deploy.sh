#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

IMAGE="${1:?배포할 이미지 주소가 필요합니다}"
REGION="ap-northeast-2"
PARAMETER="/ooga/app/runtime-gwangmin-20261108-01"
DEST="/opt/ooga/app"
SOURCE="$(cd "$(dirname "$0")" && pwd)"

if [ "$EUID" -ne 0 ]; then
    echo "[deploy] 관리자 권한으로 실행해야 합니다." >&2
    exit 1
fi

for command in aws docker curl; do
    command -v "$command" >/dev/null
done

docker compose version >/dev/null
install -d -m 750 "$DEST"

STAGE="$(mktemp -d "$DEST/.deploy.XXXXXX")"
BACKUP="$STAGE/previous"
mkdir "$BACKUP"

ACTIVATED=0
PRESERVE_BACKUP=0

cleanup() {
    if [ "$PRESERVE_BACKUP" = 1 ]; then
        echo "[deploy] 수동 복구를 위해 백업을 보존합니다: $BACKUP" >&2
    else
        rm -rf "$STAGE"
    fi
}
trap cleanup EXIT

wait_for_app() {
    local successes=0
    local attempt running status

    for attempt in $(seq 1 60); do
        running="$(docker inspect \
            --format '{{.State.Running}}' oogachaka-app 2>/dev/null || true)"

        status="$(curl --connect-timeout 2 --max-time 3 \
            -s -o /dev/null -w '%{http_code}' \
            http://127.0.0.1:8080/ || true)"

        if [ "$running" = true ] && [ "$status" = 401 ]; then
            successes=$((successes + 1))
            if [ "$successes" -ge 3 ]; then
                return 0
            fi
        else
            successes=0
        fi

        sleep 2
    done

    return 1
}

rollback() {
    result=$?
    trap - ERR
    set +e

    if [ "$ACTIVATED" = 1 ]; then
        PRESERVE_BACKUP=1
        restore_failed=0

        echo "[deploy] 실패: 이전 실행 설정으로 복구합니다." >&2

        for file in docker-compose.yml .env .env.runtime; do
            if [ -f "$BACKUP/$file" ]; then
                if ! install -m 600 "$BACKUP/$file" "$DEST/$file"; then
                    restore_failed=1
                fi
            else
                if ! rm -f "$DEST/$file"; then
                    restore_failed=1
                fi
            fi
        done

        if [ "$restore_failed" = 0 ] &&
           [ -f "$DEST/docker-compose.yml" ] &&
           [ -f "$DEST/.env" ]; then
            if docker compose -p app --project-directory "$DEST" \
                up -d --no-deps app && wait_for_app; then
                PRESERVE_BACKUP=0
                echo "[deploy] 이전 버전 기동을 확인했습니다." >&2
            else
                echo "[deploy] 이전 버전 기동 확인 실패. 수동 복구가 필요합니다." >&2
            fi
        else
            echo "[deploy] 이전 설정 복원 실패 또는 이전 배포 없음. 서버 확인이 필요합니다." >&2
        fi
    fi

    echo "[deploy] 배포 실패. 서버에서 앱 로그를 확인하세요." >&2
    exit "$result"
}
trap rollback ERR

echo "[deploy] 실행 설정 준비"
cp "$SOURCE/docker-compose.yml" "$STAGE/docker-compose.yml"
printf 'APP_IMAGE=%s\n' "$IMAGE" > "$STAGE/.env"

aws ssm get-parameter \
    --name "$PARAMETER" \
    --with-decryption \
    --region "$REGION" \
    --query Parameter.Value \
    --output text > "$STAGE/.env.runtime"

test -s "$STAGE/.env.runtime"
docker compose -p app --project-directory "$STAGE" config -q

echo "[deploy] 이미지 다운로드"
aws ecr get-login-password --region "$REGION" |
    docker login --username AWS --password-stdin "${IMAGE%%/*}"
docker pull "$IMAGE"

for file in docker-compose.yml .env .env.runtime; do
    if [ -f "$DEST/$file" ]; then
        cp "$DEST/$file" "$BACKUP/$file"
    fi
done

ACTIVATED=1
PRESERVE_BACKUP=1

for file in docker-compose.yml .env .env.runtime; do
    install -m 600 "$STAGE/$file" "$DEST/$file"
done

echo "[deploy] 컨테이너 갱신"
docker compose -p app --project-directory "$DEST" up -d --no-deps app

# 현재 기본 인증의 401 응답으로 기동 여부를 확인한다.
# 전용 헬스 체크 API가 생기면 경로와 성공 코드를 변경한다.
echo "[deploy] 기동 확인"
wait_for_app

PRESERVE_BACKUP=0
echo "[deploy] 기동 확인 완료: $IMAGE"