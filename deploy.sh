#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

IMAGE="${1:?배포할 이미지 주소가 필요합니다}"
REGION="ap-northeast-2"
PARAMETER="/ooga/app/runtime-gwangmin-20261108-01"
DEST="/opt/ooga/app"
SOURCE="$(cd "$(dirname "$0")" && pwd)"
APP_CONTAINER="oogachaka-app"
APP_PROJECT="app"

if [ "$EUID" -ne 0 ]; then
    echo "[deploy] 관리자 권한으로 실행해야 합니다." >&2
    exit 1
fi

for command in aws docker curl flock; do
    command -v "$command" >/dev/null
done

docker compose version >/dev/null
install -d -m 750 "$DEST"

# Keep the same lock through setup, startup checks and rollback.
exec 9>>"$DEST/.deploy.lock"
if ! flock -n 9; then
    echo "[deploy] 배포 잠금을 얻지 못했습니다. 다른 배포가 실행 중인지 확인하세요." >&2
    exit 1
fi

STAGE="$(mktemp -d "$DEST/.deploy.XXXXXX")"
BACKUP="$STAGE/previous"
mkdir "$BACKUP"

ACTIVATED=0
PRESERVE_BACKUP=0
LEGACY_CONTAINER=""
LEGACY_RUNNING=false

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
            --format '{{.State.Running}}' "$APP_CONTAINER" 2>/dev/null || true)"

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

# Authenticate Docker to the ECR registry in the image's region.
login_to_ecr() {
    local registry="$1" region="$2"

    aws ecr get-login-password --region "$region" |
        docker login --username AWS --password-stdin "$registry"
}

# Resolve the restored app image and download it only if it is missing locally.
prepare_rollback_image() {
    local previous_image registry
    previous_image="$(docker compose -p app --project-directory "$DEST" \
        config --images app)" || return 1
    [ -n "$previous_image" ] || return 1

    if docker image inspect "$previous_image" >/dev/null 2>&1; then
        return 0
    fi

    registry="${previous_image%%/*}"
    if [[ "$registry" =~ ^[0-9]{12}\.dkr\.ecr\.([a-z0-9-]+)\.amazonaws\.com(\.cn)?$ ]]; then
        login_to_ecr "$registry" "${BASH_REMATCH[1]}" || return 1
    fi
    docker pull "$previous_image"
}

# Find only the container occupying the app name, including a stopped one.
find_app_container() {
    docker container ls --all --quiet --no-trunc --filter "name=^/${APP_CONTAINER}$"
}

container_owner() {
    docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}/{{index .Config.Labels "com.docker.compose.service"}}' "$1"
}

# Restore the original container even when the previous deployment has no Compose files.
restore_legacy_container() {
    local current owner
    current="$(find_app_container)" || return 1

    if [ "$current" != "$LEGACY_CONTAINER" ]; then
        if [ -n "$current" ]; then
            owner="$(container_owner "$current")" || return 1
            [ "$owner" = "$APP_PROJECT/app" ] || return 1
            docker rm -f "$current" || return 1
        fi
        docker rename "$LEGACY_CONTAINER" "$APP_CONTAINER" || return 1
    fi

    if [ "$LEGACY_RUNNING" = true ]; then
        docker start "$LEGACY_CONTAINER" && wait_for_app
    fi
}

rollback() {
    local result="$1"
    trap - ERR
    trap '' INT TERM

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

        if [ -n "$LEGACY_CONTAINER" ]; then
            restore_legacy_container || restore_failed=1
        elif [ "$restore_failed" = 0 ] &&
           [ -f "$DEST/docker-compose.yml" ] &&
           [ -f "$DEST/.env" ]; then
            { prepare_rollback_image &&
                docker compose -p app --project-directory "$DEST" \
                    up -d --no-deps --pull never app && wait_for_app; } || restore_failed=1
        else
            restore_failed=1
        fi

        if [ "$restore_failed" = 0 ]; then
            PRESERVE_BACKUP=0
            echo "[deploy] 이전 배포 상태를 복구했습니다." >&2
        else
            echo "[deploy] 이전 배포 복구 실패. 서버에서 확인해 주세요." >&2
        fi
    fi

    echo "[deploy] 배포 실패. 서버에서 앱 로그를 확인하세요." >&2
    exit "$result"
}
trap 'rollback "$?"' ERR
trap 'rollback 130' INT
trap 'rollback 143' TERM

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
login_to_ecr "${IMAGE%%/*}" "$REGION"
docker pull "$IMAGE"

for file in docker-compose.yml .env .env.runtime; do
    if [ -f "$DEST/$file" ]; then
        cp "$DEST/$file" "$BACKUP/$file"
    fi
done

current_container="$(find_app_container)"
if [ -n "$current_container" ]; then
    current_owner="$(container_owner "$current_container")"
    if [ "$current_owner" != "$APP_PROJECT/app" ] ||
       [ ! -s "$BACKUP/docker-compose.yml" ] ||
       [ ! -s "$BACKUP/.env" ] ||
       [ ! -s "$BACKUP/.env.runtime" ]; then
        LEGACY_RUNNING="$(docker inspect --format '{{.State.Running}}' "$current_container")"
        LEGACY_CONTAINER="$current_container"

        # Compose finds containers by project and service labels even after a rename.
        if [ "$current_owner" = "$APP_PROJECT/app" ]; then
            APP_PROJECT="ooga-app-migration"
        fi
    fi
fi

ACTIVATED=1
PRESERVE_BACKUP=1

for file in docker-compose.yml .env .env.runtime; do
    install -m 600 "$STAGE/$file" "$DEST/$file"
done

if [ -n "$LEGACY_CONTAINER" ]; then
    legacy_name="${APP_CONTAINER}.previous-${STAGE##*.}"
    if [ "$LEGACY_RUNNING" = true ]; then
        docker stop "$LEGACY_CONTAINER"
    fi
    docker rename "$LEGACY_CONTAINER" "$legacy_name"
    echo "[deploy] 기존 컨테이너 보관: $legacy_name"
fi

echo "[deploy] 컨테이너 갱신"
docker compose -p "$APP_PROJECT" --project-directory "$DEST" up -d --no-deps app

# 현재 기본 인증의 401 응답으로 기동 여부를 확인한다.
# 전용 헬스 체크 API가 생기면 경로와 성공 코드를 변경한다.
echo "[deploy] 기동 확인"
wait_for_app

ACTIVATED=0
PRESERVE_BACKUP=0
if [ -n "$LEGACY_CONTAINER" ]; then
    docker rm "$LEGACY_CONTAINER" ||
        echo "[deploy] 보관한 기존 컨테이너를 정리하지 못했습니다: $legacy_name" >&2
fi
echo "[deploy] 기동 확인 완료: $IMAGE"
