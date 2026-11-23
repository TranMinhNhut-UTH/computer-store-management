-- Frozen IAM-005 permission registration candidate. Run only through the controlled runner.
INSERT INTO permission (code, name, description, created_at, updated_at)
SELECT CASE WHEN (
           SELECT COUNT(*) FROM flyway_schema_history
       ) = 9 AND (
           SELECT COUNT(*) FROM flyway_schema_history
           WHERE version = '9' AND success = 1 AND type = 'SQL' AND checksum = 985142827
       ) = 1
       AND NOT EXISTS (SELECT 1 FROM permission WHERE UPPER(LEFT(code, 9)) = 'EMPLOYEE_')
       THEN desired.code ELSE (SELECT 'invalid' UNION ALL SELECT 'staff baseline') END,
       desired.code, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
  SELECT 'EMPLOYEE_CREATE' AS code
  UNION ALL SELECT 'EMPLOYEE_LOCK'
  UNION ALL SELECT 'EMPLOYEE_READ'
  UNION ALL SELECT 'EMPLOYEE_ROLE_ASSIGN'
  UNION ALL SELECT 'EMPLOYEE_UNLOCK'
  UNION ALL SELECT 'EMPLOYEE_UPDATE'
) AS desired;
