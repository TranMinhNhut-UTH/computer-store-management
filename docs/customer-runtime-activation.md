# Customer V8/V9 local rollout

The four Customer grants are approved. Execute only in a separately authorized local maintenance window. Pause application and rollout writers from backup through V9 read-back. Use one PowerShell session in the existing workspace. Stop on any failed check.

## 1. Preflight on the actual database

The MySQL client prompts without echo and uses TCP to the local target.

~~~powershell
$ErrorActionPreference = 'Stop'
$customerRoot = 'C:\Code\computer-store-management'
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$customerMysql = Join-Path $mysqlBin 'mysql.exe'
$customerDump = Join-Path $mysqlBin 'mysqldump.exe'
$customerUser = Read-Host 'Local MySQL username'
if ([string]::IsNullOrWhiteSpace($customerUser)) { throw 'MySQL username is required.' }
$env:MYSQL_TEST_LOGIN_FILE = Join-Path ([IO.Path]::GetTempPath()) ("csm-no-login-" + [guid]::NewGuid())
$customerConnection = @('--protocol=TCP','--host=127.0.0.1','--port=3306',"--user=$customerUser",'--password')

$customerSql = @'
START TRANSACTION READ ONLY;
SELECT CONCAT('T|', DATABASE(), '|', @@port, '|', IF(VERSION() LIKE '8.0.%','MYSQL8','OTHER'));
SELECT CONCAT('H|', COALESCE(version,'NULL'), '|', COALESCE(CAST(checksum AS CHAR),'NULL'), '|', success, '|', type, '|', description)
FROM flyway_schema_history ORDER BY installed_rank;
SELECT CONCAT('R|', code) FROM app_role;
SELECT CONCAT('P|', code, '|', IF(CAST(name AS BINARY) = CAST(code AS BINARY) AND description IS NULL,1,0))
FROM permission WHERE UPPER(LEFT(code,9)) = 'CUSTOMER_';
SELECT CONCAT('G|', COALESCE(r.code,'MISSING_ROLE'), '|', p.code)
FROM role_permission rp JOIN permission p ON p.id = rp.permission_id
LEFT JOIN app_role r ON r.id = rp.role_id WHERE UPPER(LEFT(p.code,9)) = 'CUSTOMER_';
SELECT CONCAT('E|', TABLE_NAME, '|', ENGINE) FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('app_role','permission','role_permission','flyway_schema_history');
ROLLBACK;
'@

$customerHistory = @(
    'H|1|-1142478933|1|SQL|create initial schema',
    'H|2|947719693|1|JDBC|register category permissions',
    'H|3|253774843|1|JDBC|grant category permissions',
    'H|4|-1732158983|1|JDBC|register product permissions',
    'H|5|155246154|1|JDBC|grant product permissions',
    'H|6|-1416705504|1|SQL|register supplier permissions',
    'H|7|982398228|1|SQL|grant supplier permissions',
    'H|8|151122022|1|SQL|register customer permissions',
    'H|9|985142827|1|SQL|grant customer permissions'
)
$customerCodes = @('CUSTOMER_CREATE','CUSTOMER_READ','CUSTOMER_UPDATE')
$customerPermissions = @($customerCodes | ForEach-Object { "P|$_|1" })
$customerGrants = @('G|MANAGER|CUSTOMER_READ') + @($customerCodes | ForEach-Object { "G|SALES_STAFF|$_" })

function Assert-CustomerRows($actual, $expected, $label) {
    if ((@($actual) -join [char]10) -cne (@($expected) -join [char]10)) { throw "Unexpected $label. Stop rollout." }
}

function Assert-CustomerState([ValidateSet(7,8,9)][int]$version) {
    $rows = @(& $customerMysql --no-defaults @customerConnection --database=computer_store_dev --batch --skip-column-names "--execute=$customerSql")
    if ($LASTEXITCODE -ne 0) { throw 'Read-back failed.' }
    Assert-CustomerRows @($rows -cmatch '^T\|') @('T|computer_store_dev|3306|MYSQL8') 'live target'
    Assert-CustomerRows @($rows -cmatch '^H\|') $customerHistory[0..($version-1)] 'ordered Flyway history'
    Assert-CustomerRows @($rows -cmatch '^R\|' | Sort-Object) @('R|ADMIN','R|MANAGER','R|SALES_STAFF','R|WAREHOUSE_STAFF') 'roles'
    $engines = @('E|app_role|InnoDB','E|flyway_schema_history|InnoDB','E|permission|InnoDB','E|role_permission|InnoDB')
    Assert-CustomerRows @($rows -cmatch '^E\|' | Sort-Object) $engines 'table engines'
    $permissions = @($rows -cmatch '^P\|' | Sort-Object)
    $grants = @($rows -cmatch '^G\|' | Sort-Object)
    $expectedPermissions = if ($version -ge 8) { $customerPermissions }
    $expectedGrants = if ($version -eq 9) { $customerGrants }
    Assert-CustomerRows $permissions $expectedPermissions 'Customer permission rows'
    Assert-CustomerRows $grants $expectedGrants 'Customer grant rows'
    [pscustomobject]@{ Schema='computer_store_dev'; LastVersion=$version; Permissions=$permissions; Grants=$grants }
}

Assert-CustomerState 7
~~~

This requires successful V1–V7 in recorded order, no V8/V9, and zero Customer permissions and grants. Stop on unexpected history or Customer rows.

## 2. Verify the backup and build the manual runner

Use a current full backup created during the same uninterrupted write freeze. Keep it in a private directory outside Git. If a new backup is needed, use the [verified backup procedure](supplier-runtime-activation.md#2-create-and-verify-a-current-backup) after setting $supplierDump = $customerDump and $supplierConnection = $customerConnection in PowerShell. Run only the backup block and retain the dump and its recorded SHA-256. A structural and checksum check does not replace a disposable restore test.

~~~powershell
$customerBackup = Read-Host 'Current pre-V8 backup path'
$customerBackupHash = Read-Host 'Recorded SHA-256'
function Assert-CustomerBackup {
    if (!(Test-Path -LiteralPath $customerBackup -PathType Leaf) -or
        (Get-Item -LiteralPath $customerBackup).Length -lt 1024 -or
        !(Select-String -LiteralPath $customerBackup -SimpleMatch ('USE ' + [char]96 + 'computer_store_dev' + [char]96 + ';') -Quiet) -or
        !(Select-String -LiteralPath $customerBackup -SimpleMatch 'Dump completed on' -Quiet) -or
        (Get-FileHash -LiteralPath $customerBackup -Algorithm SHA256).Hash -ne $customerBackupHash) {
        throw 'Current backup could not be verified.'
    }
}
Assert-CustomerBackup

Set-Location (Join-Path $customerRoot 'backend')
.\mvnw.cmd -DskipTests compile dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/customer-rollout-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Runner build failed.' }
$customerClasspath = @((Join-Path $PWD 'target/classes'), (Get-Content target/customer-rollout-classpath.txt -Raw).Trim()) -join ';'
$customerRunner = 'com.computerstore.computer_store_backend.operations.customer.v1.CustomerPermissionMigrationRunner'
& java -cp $customerClasspath $customerRunner --verify-artifacts
if ($LASTEXITCODE -ne 0) { throw 'Migration artifact verification failed.' }

function Invoke-CustomerStage([ValidateSet('A','B')][string]$stage) {
    Assert-CustomerBackup
    $secret = Read-Host "Local MySQL password for stage $stage" -AsSecureString
    try {
        $env:DB_USERNAME = $customerUser
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
        & java -cp $customerClasspath $customerRunner --customer-permission-rollout "--stage=$stage" --confirm-target=computer_store_dev
        if ($LASTEXITCODE -ne 0) { throw "Stage $stage stopped. Inspect history before any retry or repair." }
    } finally {
        Remove-Item Env:DB_USERNAME,Env:DB_PASSWORD -ErrorAction SilentlyContinue
        $secret.Dispose()
    }
}
~~~

The runner uses the original V1–V7 sources available in this checkout. It validates resolved checksums and installed history, binds the target to local MySQL, and permits only the selected pending migration. The password is inherited by the Java process and cleared afterward.

## 3. Run one stage at a time

Stage A must record V8 at checksum 151122022 with three Customer permissions and zero Customer grants.

~~~powershell
Assert-CustomerState 7
Invoke-CustomerStage A
Assert-CustomerState 8
~~~

After Stage A read-back passes, Stage B must record V9 at checksum 985142827 with exactly four grants. MANAGER gets READ. SALES_STAFF gets READ, CREATE and UPDATE.

~~~powershell
Assert-CustomerState 8
Invoke-CustomerStage B
Assert-CustomerState 9
Remove-Item Env:MYSQL_TEST_LOGIN_FILE -ErrorAction SilentlyContinue
~~~

## 4. Release core Customer routes

After successful live V9 read-back, check and apply the prepared [Customer route patch](customer-http-release.patch).

~~~powershell
Set-Location $customerRoot
git apply --check docs/customer-http-release.patch
if ($LASTEXITCODE -ne 0) { throw 'Customer patch does not apply to this checkout.' }
git apply docs/customer-http-release.patch
if ($LASTEXITCODE -ne 0) { throw 'Customer route patch failed.' }
~~~

From backend, run `.\mvnw.cmd '-Dtest=CustomerSecurityTest,SecurityConfigTest,CategorySecurityTest,ProductRouteReadinessTest,SupplierSecurityTest' package` before deploying the release.

Use the [local development guide](local-development-guide.md) with Flyway disabled. Smoke-test anonymous denial, Manager read-only access, Sales core create/edit with CSRF, and denial for Admin and Warehouse. Check that contact fields and status mutations are unavailable and that Product, Supplier and Category still work.
