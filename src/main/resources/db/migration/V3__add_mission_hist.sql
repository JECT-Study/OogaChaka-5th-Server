-- 미션 상세에서 등록·승인·완료 요청·반려 흐름을 시간순으로 보여주기 위한 이력이다.
-- 같은 액션이 반복될 수 있어 액션마다 한 행을 남기고, 남긴 이력은 수정하지 않는다.
CREATE TABLE mission_hist (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '미션 이력 ID',
    `mission_id` BIGINT NOT NULL COMMENT '미션 ID',
    `actor_user_id` BIGINT NOT NULL COMMENT '액션 수행 사용자 ID',
    `action_type` VARCHAR(32) NOT NULL COMMENT '미션 액션 유형',
    `message` VARCHAR(255) NULL COMMENT '요청 코멘트 또는 반려 사유',
    `image_url` VARCHAR(2048) NULL COMMENT '완료 요청 인증 사진 URL',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    PRIMARY KEY (`id`)
) COMMENT='미션 이력';

-- 완료 요청 일시·반려 사유·인증 사진은 반복될 수 있어 mission_hist로 옮기고 미션에서는 제거한다.
ALTER TABLE mission
    DROP COLUMN `requested_at`,
    DROP COLUMN `rejection_reason`,
    DROP COLUMN `image_url`;
