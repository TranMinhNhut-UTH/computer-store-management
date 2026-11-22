# Supplier runtime activation

The five Supplier grants are approved. Run the following blocks in a dedicated PowerShell terminal during a separately authorized maintenance window. Pause application and rollout writers before preflight and keep them paused through V7 read-back. The Supplier HTTP boundary stays disabled throughout database execution.

## 1. Verify the target and live state

The client prompts for the password without echo. Run the current block and stop if any assertion fails.

~~~powershell
$ErrorActionPreference = 'Stop'
$supplierRoot = 'C:\Code\computer-store-management-supplier'
$productRoot = 'C:\Code\computer-store-management'
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$supplierMysql = Join-Path $mysqlBin 'mysql.exe'
$supplierDump = Join-Path $mysqlBin 'mysqldump.exe'
$supplierUser = Read-Host 'Local MySQL username'
if ([string]::IsNullOrWhiteSpace($supplierUser)) { throw 'MySQL username is required.' }
$env:MYSQL_TEST_LOGIN_FILE = Join-Path ([IO.Path]::GetTempPath()) ("csm-no-login-" + [guid]::NewGuid())
$supplierConnection = @('--protocol=TCP','--host=127.0.0.1','--port=3306',"--user=$supplierUser",'--password')

$supplierSql = @'
START TRANSACTION READ ONLY;
SELECT CONCAT('T|', DATABASE(), '|', @@port, '|', IF(VERSION() LIKE '8.0.%','MYSQL8','OTHER'));
SELECT CONCAT('H|', COALESCE(version,'NULL'), '|', COALESCE(CAST(checksum AS CHAR),'NULL'), '|', success, '|', type, '|', description)
FROM flyway_schema_history ORDER BY installed_rank;
SELECT CONCAT('R|', code) FROM app_role;
SELECT CONCAT('P|', code, '|', IF(CAST(name AS BINARY) = CAST(code AS BINARY) AND description IS NULL,1,0))
FROM permission WHERE UPPER(LEFT(code,9)) = 'SUPPLIER_';
SELECT CONCAT('G|', COALESCE(r.code,'MISSING_ROLE'), '|', p.code)
FROM role_permission rp JOIN permission p ON p.id = rp.permission_id
LEFT JOIN app_role r ON r.id = rp.role_id WHERE UPPER(LEFT(p.code,9)) = 'SUPPLIER_';
SELECT CONCAT('E|', TABLE_NAME, '|', ENGINE) FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('app_role','permission','role_permission','flyway_schema_history');
ROLLBACK;
'@

$supplierHistory = @(
    'H|1|-1142478933|1|SQL|create initial schema',
    'H|2|947719693|1|JDBC|register category permissions',
    'H|3|253774843|1|JDBC|grant category permissions',
    'H|4|-1732158983|1|JDBC|register product permissions',
    'H|5|155246154|1|JDBC|grant product permissions',
    'H|6|-1416705504|1|SQL|register supplier permissions',
    'H|7|982398228|1|SQL|grant supplier permissions'
)
$supplierCodes = @('SUPPLIER_CREATE','SUPPLIER_DEACTIVATE','SUPPLIER_READ','SUPPLIER_UPDATE')
$supplierPermissions = @($supplierCodes | ForEach-Object { "P|$_|1" })
$supplierGrants = @($supplierCodes | ForEach-Object { "G|MANAGER|$_" }) + @('G|WAREHOUSE_STAFF|SUPPLIER_READ')

function Assert-SupplierRows($actual, $expected, $label) {
    if ((@($actual) -join [char]10) -cne (@($expected) -join [char]10)) { throw "Unexpected $label. Stop rollout." }
}

function Assert-SupplierState([ValidateSet(5,6,7)][int]$version) {
    $rows = @(& $supplierMysql --no-defaults @supplierConnection --database=computer_store_dev --batch --skip-column-names "--execute=$supplierSql")
    if ($LASTEXITCODE -ne 0) { throw 'Read-back failed.' }
    Assert-SupplierRows @($rows -cmatch '^T\|') @('T|computer_store_dev|3306|MYSQL8') 'live target'
    Assert-SupplierRows @($rows -cmatch '^H\|') $supplierHistory[0..($version-1)] 'ordered Flyway history'
    Assert-SupplierRows @($rows -cmatch '^R\|' | Sort-Object) @('R|ADMIN','R|MANAGER','R|SALES_STAFF','R|WAREHOUSE_STAFF') 'roles'
    $engines = @('E|app_role|InnoDB','E|flyway_schema_history|InnoDB','E|permission|InnoDB','E|role_permission|InnoDB')
    Assert-SupplierRows @($rows -cmatch '^E\|' | Sort-Object) $engines 'table engines'
    $permissions = @($rows -cmatch '^P\|' | Sort-Object)
    $grants = @($rows -cmatch '^G\|' | Sort-Object)
    $expectedPermissions = if ($version -ge 6) { $supplierPermissions }
    $expectedGrants = if ($version -eq 7) { $supplierGrants }
    Assert-SupplierRows $permissions $expectedPermissions 'Supplier permission rows'
    Assert-SupplierRows $grants $expectedGrants 'Supplier grant rows'
    [pscustomobject]@{ Schema='computer_store_dev'; LastVersion=$version; Permissions=$permissions; Grants=$grants }
}

Assert-SupplierState 5
~~~

This requires successful V1–V5 in recorded order, with V6/V7 absent and zero Supplier permissions and grants. Stop on any additional migration or preexisting Supplier data.

## 2. Create and verify a current backup

Take a full backup of the verified schema during this write-frozen window. The dump stays outside the repository. A checksum and structural markers verify the file, with restore verification performed separately on disposable infrastructure.

~~~powershell
$backupRoot = Join-Path $env:USERPROFILE 'CSM-Backups'
[void](New-Item -ItemType Directory -Path $backupRoot -Force)
if ((Get-Item -LiteralPath $backupRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) {
    throw 'Choose a non-redirected backup root.'
}
$backupDir = Join-Path $backupRoot ("supplier-pre-v6-" + [guid]::NewGuid())
[void](New-Item -ItemType Directory -Path $backupDir)
$acl = Get-Acl -LiteralPath $backupDir
$acl.SetAccessRuleProtection($true, $false)
foreach ($rule in @($acl.Access)) { $acl.RemoveAccessRuleAll($rule) }
foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User,
    [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
        $sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
}
Set-Acl -LiteralPath $backupDir -AclObject $acl
$partial = Join-Path $backupDir 'computer_store_dev.partial'
& $supplierDump --no-defaults @supplierConnection --single-transaction --quick --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --column-statistics=0 --default-character-set=utf8mb4 --routines --events --triggers --hex-blob "--result-file=$partial" --databases computer_store_dev
if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $partial -PathType Leaf) -or
    (Get-Item -LiteralPath $partial).Length -lt 1024 -or
    !(Select-String -LiteralPath $partial -SimpleMatch ('USE ' + [char]96 + 'computer_store_dev' + [char]96 + ';') -Quiet) -or
    !(Select-String -LiteralPath $partial -SimpleMatch 'Dump completed on' -Quiet)) {
    throw 'Backup failed or lacks required markers.'
}
$supplierBackup = Join-Path $backupDir 'computer_store_dev.sql'
Move-Item -LiteralPath $partial -Destination $supplierBackup
$supplierBackupHash = (Get-FileHash -LiteralPath $supplierBackup -Algorithm SHA256).Hash
Get-Item -LiteralPath $supplierBackup | Select-Object FullName,Length
$supplierBackupHash
~~~

## 3. Build the runner and stage the original migration classes

Use the released Product checkout at 5230619. The classpath imports only its V4/V5 classes and frozen payload, so Flyway sees one V1 resource and the original V2–V5 implementations. The artifact check runs without connecting to MySQL.

~~~powershell
$productRelease = '523061901a8f5881c196da36e3fbdc6596fdc4d7'
if ((git -C $productRoot rev-parse HEAD) -cne $productRelease -or
    @(git -C $productRoot status --porcelain --untracked-files=no).Count) {
    throw 'Expected the clean released Product source at 5230619.'
}
Push-Location (Join-Path $productRoot 'backend')
try {
    .\mvnw.cmd -DskipTests compile
    if ($LASTEXITCODE -ne 0) { throw 'Product artifact build failed.' }
} finally { Pop-Location }

Set-Location (Join-Path $supplierRoot 'backend')
.\mvnw.cmd -DskipTests compile dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/supplier-rollout-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Supplier classpath build failed.' }

$productPackage = 'com\computerstore\computer_store_backend\operations\product\v1'
$artifactRoot = Join-Path $PWD ("target\supplier-prior-" + [guid]::NewGuid())
$artifactPackage = Join-Path $artifactRoot $productPackage
[void](New-Item -ItemType Directory -Path $artifactPackage -Force)
$originalPackage = Join-Path $productRoot ("backend\target\classes\" + $productPackage)
Get-ChildItem -LiteralPath $originalPackage -File |
    Where-Object { $_.Name -match '^(FrozenProductRolloutV1(?:\$.+)?|V4__register_product_permissions|V5__grant_product_permissions)\.class$' } |
    Copy-Item -Destination $artifactPackage
$supplierClasspath = (@((Join-Path $PWD 'target\classes'), $artifactRoot,
    (Get-Content -LiteralPath 'target/supplier-rollout-classpath.txt' -Raw).Trim()) -join ';')
$supplierRunner = 'com.computerstore.computer_store_backend.operations.supplier.v1.SupplierPermissionMigrationRunner'
& java -cp $supplierClasspath $supplierRunner --verify-artifacts
if ($LASTEXITCODE -ne 0) { throw 'Released migration artifact verification failed.' }

function Invoke-SupplierStage([ValidateSet('A','B')][string]$stage) {
    if ((Get-FileHash -LiteralPath $supplierBackup -Algorithm SHA256).Hash -cne $supplierBackupHash) {
        throw 'Verified backup checksum changed.'
    }
    $secret = Read-Host "Local MySQL password for stage $stage" -AsSecureString
    try {
        $env:DB_USERNAME = $supplierUser
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
        & java -cp $supplierClasspath $supplierRunner --supplier-permission-rollout "--stage=$stage" --confirm-target=computer_store_dev
        if ($LASTEXITCODE -ne 0) { throw "Stage $stage stopped. Inspect history and rows before any retry." }
    } finally {
        Remove-Item Env:DB_USERNAME,Env:DB_PASSWORD -ErrorAction SilentlyContinue
        $secret.Dispose()
    }
}
~~~

## 4. Apply V6, then V7

After execution authorization, run Stage A only. Expect V6 success at checksum -1416705504, four Supplier permissions with name equal to code and NULL descriptions, and zero grants.

~~~powershell
Assert-SupplierState 5
Invoke-SupplierStage A
Assert-SupplierState 6
~~~

After live V6 read-back passes, run Stage B. Expect V7 success at checksum 982398228 and exactly five pairs. MANAGER gets all four permissions and WAREHOUSE_STAFF gets READ only. ADMIN and SALES_STAFF get none.

~~~powershell
Assert-SupplierState 6
Invoke-SupplierStage B
Assert-SupplierState 7
Remove-Item Env:MYSQL_TEST_LOGIN_FILE -ErrorAction SilentlyContinue
~~~

On any mismatch or uncertain outcome, stop and inspect live history and rows. Do not retry or repair automatically.

## 5. Release the HTTP boundary

Only after successful live V7 read-back, apply the [release patch](supplier-http-release.patch). The patch targets the Supplier branch. If Product has been integrated, apply it only after reviewing the combined security chain and confirming both modules' route rules remain intact.

~~~powershell
Set-Location $supplierRoot
git apply --check docs/supplier-http-release.patch
if ($LASTEXITCODE -ne 0) { throw 'Review the patch against this checkout.' }
git apply docs/supplier-http-release.patch
if ($LASTEXITCODE -ne 0) { throw 'Supplier release patch failed to apply.' }
~~~

For the local smoke test, start the application with Flyway disabled (set SPRING_FLYWAY_ENABLED=false in its process environment) and Hibernate schema validation enabled. The default application classpath does not register the full live history. Verify anonymous 401, ADMIN and SALES_STAFF 403, Warehouse read-only, Manager operations with CSRF, and 409 when an open purchase draft references the Supplier. Run Product and Category regression checks if their routes are present on the release branch.
