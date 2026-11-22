# Supplier runtime activation

The five Supplier grants are approved. Run the following blocks in a dedicated PowerShell terminal during a separately authorized maintenance window. Pause application and rollout writers before preflight and keep them paused through V7 read-back. The Supplier HTTP boundary stays disabled throughout database execution.

## 1. Verify the target and live state

The client prompts for the password without echo. Run the current block and stop if any assertion fails.

~~~powershell
$ErrorActionPreference = 'Stop'
$supplierRoot = 'C:\Code\computer-store-management'
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

If a verified pre-V6 backup already exists from the same uninterrupted write freeze, keep it and skip the dump block. In the original terminal, retain the recorded `$supplierBackup` and `$supplierBackupHash`. In a fresh terminal, rerun section 1 and set them using the existing file path and the SHA-256 recorded when the dump was created. Use `$supplierBackup = Read-Host 'Existing pre-V6 backup path'` and `$supplierBackupHash = Read-Host 'Recorded pre-V6 SHA-256'`. If the write freeze ended or the backup cannot be verified, take a fresh backup.

## 3. Build the runner and export the original Product migration sources

Run with JDK 21 available as `java` and `javac`. The block works directly from the Supplier workspace. It exports only three Java sources from the pinned Product commit into a unique directory under the ignored `backend/target`, verifies each Git blob, and compiles them against Supplier's Flyway dependency. The staged class directory stays available for both stages. The build leaves the checked-out branch and source files alone.

~~~powershell
$supplierRoot = 'C:\Code\computer-store-management'
$productRelease = '523061901a8f5881c196da36e3fbdc6596fdc4d7'
if ((git -C $supplierRoot branch --show-current) -cne 'feature/supplier-management') {
    throw 'Run this block on the Supplier branch.'
}
$verifiedProductCommit = git -C $supplierRoot rev-parse --verify "${productRelease}^{commit}"
if ($LASTEXITCODE -ne 0 -or $verifiedProductCommit -cne $productRelease) {
    throw 'Pinned Product commit is unavailable. Fetch origin feature/product-management, then check again.'
}

Set-Location (Join-Path $supplierRoot 'backend')
.\mvnw.cmd -DskipTests compile dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/supplier-rollout-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Supplier classpath build failed.' }
$runtimeClasspath = (Get-Content -LiteralPath 'target/supplier-rollout-classpath.txt' -Raw).Trim()

$productSourceDir = 'backend/src/main/java/com/computerstore/computer_store_backend/operations/product/v1'
$productSources = @(
    "$productSourceDir/FrozenProductRolloutV1.java",
    "$productSourceDir/V4__register_product_permissions.java",
    "$productSourceDir/V5__grant_product_permissions.java"
)
$buildDir = (New-Item -ItemType Directory -Path (Join-Path $PWD ("target\supplier-prior-" + [guid]::NewGuid()))).FullName
$archive = Join-Path $buildDir 'product-sources.zip'
$exportDir = Join-Path $buildDir 'sources'
$artifactRoot = (New-Item -ItemType Directory -Path (Join-Path $buildDir 'classes')).FullName
# Keep exported Java bytes identical to the Git blobs on Windows.
git -C $supplierRoot -c core.autocrlf=false -c core.eol=lf archive --format=zip "--output=$archive" $productRelease -- @productSources
if ($LASTEXITCODE -ne 0) { throw 'Pinned Product source export failed.' }
Expand-Archive -LiteralPath $archive -DestinationPath $exportDir
$sourceFiles = foreach ($source in $productSources) {
    $file = Join-Path $exportDir $source
    $expectedBlob = git -C $supplierRoot rev-parse --verify "${productRelease}:$source"
    if ($LASTEXITCODE -ne 0) { throw "Cannot resolve pinned source $source." }
    $actualBlob = git -C $supplierRoot hash-object --no-filters -- $file
    if ($LASTEXITCODE -ne 0 -or $actualBlob -cne $expectedBlob) {
        throw "Exported source does not match pinned blob $source."
    }
    $file
}
& javac --release 21 -encoding UTF-8 -classpath $runtimeClasspath -d $artifactRoot @sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Pinned Product migration compilation failed.' }

$supplierClasspath = @($artifactRoot, (Join-Path $PWD 'target\classes'), $runtimeClasspath) -join ';'
$supplierRunner = 'com.computerstore.computer_store_backend.operations.supplier.v1.SupplierPermissionMigrationRunner'
& java -cp $supplierClasspath $supplierRunner --verify-artifacts
if ($LASTEXITCODE -ne 0) { throw 'Released migration artifact verification failed.' }

function Assert-SupplierBackup {
    if (!(Test-Path -LiteralPath $supplierBackup -PathType Leaf) -or
        (Get-Item -LiteralPath $supplierBackup).Length -lt 1024 -or
        !(Select-String -LiteralPath $supplierBackup -SimpleMatch ('USE ' + [char]96 + 'computer_store_dev' + [char]96 + ';') -Quiet) -or
        !(Select-String -LiteralPath $supplierBackup -SimpleMatch 'Dump completed on' -Quiet) -or
        (Get-FileHash -LiteralPath $supplierBackup -Algorithm SHA256).Hash -ne $supplierBackupHash) {
        throw 'Current pre-V6 backup could not be verified.'
    }
}

function Invoke-SupplierStage([ValidateSet('A','B')][string]$stage) {
    Assert-SupplierBackup
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

The offline runner check loads the original Category and Product classes and verifies their versions, descriptions and checksums. Product V4 must be -1732158983 and V5 must be 155246154. The exported classes add no Flyway SQL resources to the classpath.

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
