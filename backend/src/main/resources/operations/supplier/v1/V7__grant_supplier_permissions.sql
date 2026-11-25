-- Proposed Supplier V1 grants. V6 must be recorded successfully before V7 may write.
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT CASE WHEN (
           SELECT COUNT(*) FROM flyway_schema_history
           WHERE version = '6' AND success = 1 AND type = 'SQL' AND checksum = -1416705504
       ) = 1
       AND (SELECT COUNT(*) FROM flyway_schema_history) = 6
       AND (SELECT COUNT(*) FROM app_role WHERE code IN ('MANAGER', 'WAREHOUSE_STAFF')) = 2
       AND (
           SELECT COUNT(*) FROM permission
           WHERE code IN ('SUPPLIER_READ', 'SUPPLIER_CREATE', 'SUPPLIER_UPDATE', 'SUPPLIER_DEACTIVATE')
             AND CAST(name AS BINARY) = CAST(code AS BINARY) AND description IS NULL
       ) = 4
       AND (SELECT COUNT(*) FROM permission WHERE UPPER(LEFT(code, 9)) = 'SUPPLIER_') = 4
       AND NOT EXISTS (
           SELECT 1 FROM role_permission rp JOIN permission p ON p.id = rp.permission_id
           WHERE UPPER(LEFT(p.code, 9)) = 'SUPPLIER_'
       )
       THEN (SELECT id FROM app_role WHERE code = desired.role_code)
       ELSE (SELECT 0 UNION ALL SELECT 1) END,
       (SELECT id FROM permission WHERE code = desired.permission_code),
       UTC_TIMESTAMP(6)
FROM (
  SELECT 'MANAGER' AS role_code, 'SUPPLIER_READ' AS permission_code
  UNION ALL SELECT 'MANAGER', 'SUPPLIER_CREATE'
  UNION ALL SELECT 'MANAGER', 'SUPPLIER_UPDATE'
  UNION ALL SELECT 'MANAGER', 'SUPPLIER_DEACTIVATE'
  UNION ALL SELECT 'WAREHOUSE_STAFF', 'SUPPLIER_READ'
) AS desired;
