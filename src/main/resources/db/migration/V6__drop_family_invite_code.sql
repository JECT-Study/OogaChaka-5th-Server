-- 자녀 초대코드는 Redis에 5분·1회용으로 두므로 가족 테이블의 초대코드 컬럼과 UNIQUE 키를 제거한다.
ALTER TABLE family
    DROP INDEX `uk_family_invite_code`,
    DROP COLUMN `invite_code`;
