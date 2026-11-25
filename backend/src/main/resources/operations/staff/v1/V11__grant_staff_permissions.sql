-- Frozen IAM-005 grants. The complete staff slice is ADMIN-only.
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT CASE WHEN (
           SELECT COUNT(*) FROM flyway_schema_history
       ) = 10 AND (
           SELECT COUNT(*) FROM flyway_schema_history
           WHERE version = '10' AND success = 1 AND type = 'SQL' AND checksum = 1376292394
       ) = 1
       AND (SELECT COUNT(*) FROM app_role) = 4
       AND (SELECT COUNT(*) FROM app_role WHERE code IN ('ADMIN','MANAGER','SALES_STAFF','WAREHOUSE_STAFF')) = 4
       AND EXISTS (
           SELECT 1 FROM user_role ur JOIN app_user u ON u.id = ur.user_id
           JOIN app_role r ON r.id = ur.role_id WHERE r.code = 'ADMIN' AND u.is_active = TRUE
       )
       AND (SELECT COUNT(*) FROM permission WHERE UPPER(LEFT(code, 9)) = 'EMPLOYEE_') = 6
       AND (
           SELECT COUNT(*) FROM permission WHERE code IN ('EMPLOYEE_CREATE', 'EMPLOYEE_LOCK', 'EMPLOYEE_READ', 'EMPLOYEE_ROLE_ASSIGN', 'EMPLOYEE_UNLOCK', 'EMPLOYEE_UPDATE')
               AND CAST(name AS BINARY) = CAST(code AS BINARY) AND description IS NULL
       ) = 6
       AND NOT EXISTS (
           SELECT 1 FROM role_permission rp JOIN permission p ON p.id = rp.permission_id
           WHERE UPPER(LEFT(p.code, 9)) = 'EMPLOYEE_'
       )
       THEN (SELECT id FROM app_role WHERE code = 'ADMIN') ELSE (SELECT 0 UNION ALL SELECT 1) END,
       (SELECT id FROM permission WHERE code = desired.code), UTC_TIMESTAMP(6)
FROM (
  SELECT 'EMPLOYEE_CREATE' AS code
  UNION ALL SELECT 'EMPLOYEE_LOCK'
  UNION ALL SELECT 'EMPLOYEE_READ'
  UNION ALL SELECT 'EMPLOYEE_ROLE_ASSIGN'
  UNION ALL SELECT 'EMPLOYEE_UNLOCK'
  UNION ALL SELECT 'EMPLOYEE_UPDATE'
) AS desired;
