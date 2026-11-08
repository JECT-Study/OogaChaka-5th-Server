-- 부모 사용자 ID 목록으로 푸시 수신 기기를 조회할 때 사용할 비고유 인덱스다.
-- 기존 token_hash UNIQUE는 유지하며, 다른 도메인의 family_member 스키마는 변경하지 않는다.
CREATE INDEX `idx_device_token_user` ON device_token (`user_id`);
