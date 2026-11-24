-- 부모 초대 화면에서 자녀의 연결 여부를 표시한다.
-- 부모는 가족 생성 시, 자녀는 초대코드로 처음 입장할 때 연결 일시를 기록한다.
ALTER TABLE family_member
    ADD COLUMN `connected_at` DATETIME(6) NULL COMMENT '연결 일시' AFTER `family_role`;
