-- 기존 VIEWER 계정을 OPERATOR로 전환합니다.
-- ddl-auto=create 환경에서는 새 DB가 생성되므로 실행할 필요가 없습니다.
UPDATE members
SET role = 'OPERATOR'
WHERE role = 'VIEWER';
