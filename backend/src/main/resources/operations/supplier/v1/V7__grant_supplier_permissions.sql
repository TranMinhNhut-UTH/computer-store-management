-- Proposed Supplier grants V1. Register and verify the catalog before applying this stage.
-- Scalar lookups plus NOT NULL and FK constraints abort the entire insert if any role or permission is missing.
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT (SELECT id FROM app_role WHERE code = desired.role_code),
       (SELECT id FROM permission WHERE code = desired.permission_code
           AND BINARY name = BINARY code AND description IS NULL),
       UTC_TIMESTAMP(6)
FROM (
  SELECT 'MANAGER' AS role_code, 'SUPPLIER_READ' AS permission_code
  UNION ALL SELECT 'MANAGER', 'SUPPLIER_CREATE'
  UNION ALL SELECT 'MANAGER', 'SUPPLIER_UPDATE'
  UNION ALL SELECT 'MANAGER', 'SUPPLIER_DEACTIVATE'
  UNION ALL SELECT 'WAREHOUSE_STAFF', 'SUPPLIER_READ'
) AS desired;
