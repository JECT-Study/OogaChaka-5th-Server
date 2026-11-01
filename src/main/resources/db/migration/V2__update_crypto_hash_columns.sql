-- 적용 전 기존 소셜 해시가 현재 HMAC 정책의 소문자 hex 64자리인지 확인한다.
-- 기존 평문/다른 해시가 있다면 별도 데이터 전환 절차가 필요하다.
-- MODIFY COLUMN은 기존 복합 UNIQUE와 사용자별 UNIQUE를 그대로 유지한다.
ALTER TABLE social_account
    MODIFY COLUMN `provider_user_id_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL COMMENT '소셜 계정 사용자 ID HMAC-SHA256 해시';

-- 빈 테이블을 전제로 한다. 기존 행이 있으면 NOT NULL/UNIQUE 추가가 실패할 수 있다.
-- 적용 전에 token 복호화/현재 HMAC 키 기반 백필 및 중복 정리 절차를 별도로 준비한다.
-- 임의 기본값이나 SQL SHA2로 기존 행을 채우지 않는다.
ALTER TABLE device_token
    ADD COLUMN `token_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL COMMENT '디바이스 토큰 HMAC-SHA256 해시' AFTER `token`,
    ADD UNIQUE KEY `uk_device_token_token_hash` (`token_hash`);
