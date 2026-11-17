-- 컨테이너를 처음 만들 때만 실행된다. 로컬 전용 계정이다.
-- 공용 DB처럼 앱 계정(데이터 조회·변경)과 Flyway 계정(스키마 변경)을 나눈다.
CREATE USER 'app'@'%' IDENTIFIED BY 'app';
GRANT SELECT, INSERT, UPDATE, DELETE ON oogachaka.* TO 'app'@'%';

CREATE USER 'migrator'@'%' IDENTIFIED BY 'migrator';
GRANT ALL PRIVILEGES ON oogachaka.* TO 'migrator'@'%';
