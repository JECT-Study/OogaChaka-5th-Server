CREATE TABLE characters (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '캐릭터 ID',
    `user_id` BIGINT NOT NULL COMMENT '소유 사용자 ID',
    `name` VARCHAR(30) NULL COMMENT '캐릭터 이름',
    `growth_stage` VARCHAR(32) NOT NULL COMMENT '성장 단계',
    `exp` INT NOT NULL DEFAULT 0 COMMENT '경험치',
    `hatched_at` DATETIME(6) NULL COMMENT '부화 일시',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`)
) COMMENT='사용자의 캐릭터';

CREATE TABLE character_inventory (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '캐릭터 인벤토리 ID',
    `user_id` BIGINT NOT NULL COMMENT '소유 사용자 ID',
    `character_item_id` BIGINT NOT NULL COMMENT '캐릭터 아이템 ID',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    PRIMARY KEY (`id`)
) COMMENT='사용자 캐릭터 인벤토리';

-- 캐릭터ID와 관계를 갖지 않은 이유는 유저가 여러 캐릭터를 가질 수 있기 때문에
-- 캐릭터 장착 아이템은 유저와 캐릭터 인벤토리 아이템과 관계를 갖도록 설계했습니다.
CREATE TABLE character_equipment (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '캐릭터 장착 아이템 ID',
    `character_id` BIGINT NOT NULL COMMENT '캐릭터 ID',
    `character_inventory_id` BIGINT NOT NULL COMMENT '캐릭터 인벤토리 ID',
    `equip_slot` VARCHAR(32) NOT NULL COMMENT '장착 슬롯',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_character_equipment_slot` (`character_id`, `equip_slot`),
    UNIQUE KEY `uk_character_equipment_inventory` (`character_inventory_id`)
) COMMENT='캐릭터 장착 아이템';

CREATE TABLE character_item (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '캐릭터 아이템 ID',
    `name` VARCHAR(64) NOT NULL COMMENT '아이템 이름',
    `description` TEXT NULL COMMENT '아이템 설명',
    `image_url` VARCHAR(2048) NULL COMMENT '아이템 이미지 URL',
    `price` BIGINT NOT NULL COMMENT '아이템 가격',
    `equip_slot` VARCHAR(32) NOT NULL COMMENT '장착 슬롯',
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성화 여부',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`)
) COMMENT='캐릭터 아이템';

CREATE TABLE social_account (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '소셜 계정 ID',
    `user_id` BIGINT NOT NULL COMMENT '사용자 ID',
    `auth_provider` VARCHAR(32) NOT NULL COMMENT '소셜 구분',
    `provider_user_id_hash` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '소셜 계정 사용자 ID 해시',
    `email` VARCHAR(255) NULL COMMENT '소셜 이메일',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_social_account_provider_user` (`auth_provider`, `provider_user_id_hash`),
    UNIQUE KEY `uk_social_account_user` (`user_id`)
) COMMENT='사용자 소셜 계정';

CREATE TABLE family (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '가족 ID',
    `invite_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '가족 초대 코드',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_family_invite_code` (`invite_code`)
) COMMENT='가족';

CREATE TABLE family_member (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '가족 구성원 ID',
    `family_id` BIGINT NOT NULL COMMENT '가족 ID',
    `family_role` VARCHAR(32) NOT NULL COMMENT '가족 구성원 역할',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`)
) COMMENT='가족 구성원';

CREATE TABLE users (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 ID',
    `family_member_id` BIGINT NULL COMMENT '가족 구성원 ID',
    `nickname` VARCHAR(30) NOT NULL COMMENT '사용자 닉네임',
    `point` BIGINT NOT NULL DEFAULT 0 COMMENT '사용자 포인트',
    `profile_image_url` VARCHAR(2048) NULL COMMENT '사용자 프로필 이미지 URL',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_family_member` (`family_member_id`)
) COMMENT='사용자';

CREATE TABLE guardian_consent (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '보호자 동의 ID',
    `guardian_id` BIGINT NOT NULL COMMENT '보호자 사용자 ID',
    `child_id` BIGINT NOT NULL COMMENT '자녀 사용자 ID',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`)
) COMMENT='보호자 동의';

CREATE TABLE mission (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '미션 ID',
    `family_id` BIGINT NOT NULL COMMENT '가족 ID',
    `created_user_id` BIGINT NOT NULL COMMENT '미션 생성자 ID',
    `assigned_child_id` BIGINT NOT NULL COMMENT '미션 담당 자녀 ID',
    `title` VARCHAR(64) NOT NULL COMMENT '미션 제목',
    `content` TEXT NULL COMMENT '미션 내용',
    `start_at` DATETIME(6) NULL COMMENT '미션 시작 일시',
    `end_at` DATETIME(6) NULL COMMENT '미션 종료 일시',
    `reward_point` BIGINT NOT NULL DEFAULT 0 COMMENT '미션 보상 포인트',
    `status` VARCHAR(32) NOT NULL COMMENT '미션 상태',
    `image_url` VARCHAR(2048) NULL COMMENT '미션 이미지 URL',
    `requested_at` DATETIME(6) NULL COMMENT '미션 완료 요청 일시',
    `rejection_reason` VARCHAR(255) NULL COMMENT '미션 완료 요청 거절 사유',
    `completed_at` DATETIME(6) NULL COMMENT '미션 완료 일시',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`)
) COMMENT='미션';

CREATE TABLE market_product (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '마켓 상품 ID',
    `family_id` BIGINT NOT NULL COMMENT '가족 ID',
    `created_user_id` BIGINT NOT NULL COMMENT '마켓 상품 생성자 ID',
    `name` VARCHAR(64) NOT NULL COMMENT '마켓 상품 이름',
    `description` TEXT NULL COMMENT '마켓 상품 설명',
    `image_url` VARCHAR(2048) NULL COMMENT '마켓 상품 이미지 URL',
    `price` BIGINT NOT NULL DEFAULT 0 COMMENT '마켓 상품 가격',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`)
) COMMENT='마켓 상품';

CREATE TABLE order_hist (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '주문 내역 ID',
    `user_id` BIGINT NOT NULL COMMENT '사용자 ID',
    `market_product_id` BIGINT NULL COMMENT '마켓 상품 ID',
    `character_item_id` BIGINT NULL COMMENT '캐릭터 아이템 ID',
    `product_name` VARCHAR(64) NOT NULL COMMENT '상품 이름',
    `spent_points` BIGINT NOT NULL COMMENT '사용 포인트',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_order_hist_product` CHECK ((`market_product_id` IS NOT NULL AND `character_item_id` IS NULL) OR (`market_product_id` IS NULL AND `character_item_id` IS NOT NULL))
) COMMENT='주문 내역';

CREATE TABLE point_hist (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '포인트 내역 ID',
    `user_id` BIGINT NOT NULL COMMENT '사용자 ID',
    `amount` BIGINT NOT NULL COMMENT '포인트 증감액',
    `reason` VARCHAR(30) NULL COMMENT '포인트 증감 사유',
    `point_reference_type` VARCHAR(32) NULL COMMENT '포인트 참조 타입',
    `point_reference_id` BIGINT NULL COMMENT '포인트 참조 ID',
    `after_point` BIGINT NOT NULL COMMENT '포인트 증감 후 잔액',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_point_hist_reference` CHECK ((`point_reference_type` IS NULL AND `point_reference_id` IS NULL) OR (`point_reference_type` IS NOT NULL AND `point_reference_id` IS NOT NULL))
) COMMENT='포인트 내역';

CREATE TABLE device_token (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '디바이스 토큰 ID',
    `user_id` BIGINT NOT NULL COMMENT '사용자 ID',
    `token` TEXT NOT NULL COMMENT '디바이스 토큰',
    `device_platform` VARCHAR(32) NOT NULL COMMENT '디바이스 플랫폼',
    `last_synced_at` DATETIME(6) NOT NULL COMMENT '마지막 동기화 일시',
    `created_at` DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at` DATETIME(6) NOT NULL COMMENT '최종 수정 일시',
    PRIMARY KEY (`id`)
) COMMENT='디바이스 토큰';
