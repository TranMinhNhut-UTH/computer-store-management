-- Proposed Customer V1 grants. V8 must be recorded successfully before V9 may write.
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT CASE WHEN (
           SELECT COUNT(*) FROM flyway_schema_history
           WHERE version = '8' AND success = 1 AND type = 'SQL' AND checksum = 151122022
       ) = 1
       AND (SELECT COUNT(*) FROM flyway_schema_history) = 8
       AND (SELECT COUNT(*) FROM app_role WHERE code IN ('MANAGER', 'SALES_STAFF')) = 2
       AND (
           SELECT COUNT(*) FROM permission
           WHERE code IN ('CUSTOMER_READ', 'CUSTOMER_CREATE', 'CUSTOMER_UPDATE')
             AND CAST(name AS BINARY) = CAST(code AS BINARY) AND description IS NULL
       ) = 3
       AND (SELECT COUNT(*) FROM permission WHERE UPPER(LEFT(code, 9)) = 'CUSTOMER_') = 3
       AND NOT EXISTS (
           SELECT 1 FROM role_permission rp JOIN permission p ON p.id = rp.permission_id
           WHERE UPPER(LEFT(p.code, 9)) = 'CUSTOMER_'
       )
       THEN (SELECT id FROM app_role WHERE code = desired.role_code)
       ELSE (SELECT 0 UNION ALL SELECT 1) END,
       (SELECT id FROM permission WHERE code = desired.permission_code),
       UTC_TIMESTAMP(6)
FROM (
  SELECT 'MANAGER' AS role_code, 'CUSTOMER_READ' AS permission_code
  UNION ALL SELECT 'SALES_STAFF', 'CUSTOMER_READ'
  UNION ALL SELECT 'SALES_STAFF', 'CUSTOMER_CREATE'
  UNION ALL SELECT 'SALES_STAFF', 'CUSTOMER_UPDATE'
) AS desired;
