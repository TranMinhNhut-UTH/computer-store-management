-- Proposed Supplier V1. Review the target and rollout separately before registering this location.
-- The invalid scalar branch aborts the statement on any preflight mismatch, even without strict mode.
INSERT INTO permission (code, name, description, created_at, updated_at)
SELECT CASE WHEN (
           SELECT COUNT(DISTINCT version) FROM flyway_schema_history
           WHERE success = 1 AND (
                 (version = '1' AND type = 'SQL' AND checksum = -1142478933)
              OR (version = '2' AND type = 'JDBC' AND checksum = 947719693)
              OR (version = '3' AND type = 'JDBC' AND checksum = 253774843)
              OR (version = '4' AND type = 'JDBC' AND checksum = -1732158983)
              OR (version = '5' AND type = 'JDBC' AND checksum = 155246154))
       ) = 5
       AND (SELECT COUNT(*) FROM flyway_schema_history) = 5
       AND NOT EXISTS (
           SELECT 1 FROM permission WHERE UPPER(LEFT(code, 9)) = 'SUPPLIER_'
       ) THEN desired.code ELSE (SELECT 'invalid' UNION ALL SELECT 'supplier baseline') END,
       desired.code, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
  SELECT 'SUPPLIER_READ' AS code
  UNION ALL SELECT 'SUPPLIER_CREATE'
  UNION ALL SELECT 'SUPPLIER_UPDATE'
  UNION ALL SELECT 'SUPPLIER_DEACTIVATE'
) AS desired;
