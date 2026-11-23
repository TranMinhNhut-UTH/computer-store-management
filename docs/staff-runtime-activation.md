# IAM-005 local rollout on Windows

This one-time procedure installs the six proposed staff permissions and their six ADMIN assignments, then enables Staff Account Management. Start only after the exact V10/V11 payload and this local execution have been approved. The application remains disabled for this workflow until V11 live verification passes and the explicit startup flag is supplied.

Run Steps 1–8 in one dedicated PowerShell terminal at `C:\Code\computer-store-management`, one block at a time. `cls` clears only the screen. A new terminal loses the variables and functions. Use only `127.0.0.1:3306/computer_store_dev` and keep credentials in the prompts. Stop on failure. If a migration was attempted, preserve the backup and inspect the live history before further execution.

## 1. Verify the workspace

Checks source and worktree. **Database** No connection.

~~~powershell
$ErrorActionPreference = 'Stop'
Set-Location 'C:\Code\computer-store-management'
$staffRoot = (Get-Location).Path
git status --short --branch
if ($LASTEXITCODE -ne 0) { throw 'Git status failed.' }
if (!(Test-Path 'backend/src/main/resources/operations/staff/v1/V11__grant_staff_permissions.sql')) {
    throw 'Use the checkout containing the reviewed IAM-005 release.'
}
~~~

**PASS** The reviewed checkout contains V10 and V11. **If it fails** resolve the workspace before proceeding.

## 2. Configure MySQL tools

Locates the MySQL 8.0 Windows tools and sets a fixed TCP target. Enter your local username. The tools prompt for a password later. **Database** No connection.

~~~powershell
. {
    $mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
    $staffMysql = Join-Path $mysqlBin 'mysql.exe'
    $staffDump = Join-Path $mysqlBin 'mysqldump.exe'
    foreach ($tool in @($staffMysql, $staffDump)) {
        if (!(Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Tool not found: $tool" }
        & $tool --version
        if ($LASTEXITCODE -ne 0) { throw "Tool check failed: $tool" }
    }

    $staffUser = Read-Host 'Local MySQL username'
    if ([string]::IsNullOrWhiteSpace($staffUser)) { throw 'Enter your local MySQL username.' }
    $staffLoginFileBefore = $env:MYSQL_TEST_LOGIN_FILE
    $env:MYSQL_TEST_LOGIN_FILE = Join-Path ([IO.Path]::GetTempPath()) ('csm-no-login-' + [guid]::NewGuid())
    $staffConnection = @('--protocol=TCP', '--host=127.0.0.1', '--port=3306', "--user=$staffUser", '--password')
    $staffVerified = 0
    $staffRunnerReady = $false
    Write-Host 'PASS: MySQL tools configured for 127.0.0.1:3306.'
}
~~~

**PASS** Both clients print versions and the local connection settings are ready. **If it fails** correct the MySQL client installation path.

## 3. Verify live V1–V9

Checks exact ordered Flyway checksums, four roles, an active Admin, InnoDB tables and absence of staff permissions/grants. **Database** Read-only transaction.

~~~powershell
. {
    $staffSql = @'
    START TRANSACTION READ ONLY;
    SELECT CONCAT('T|', DATABASE(), '|', @@port, '|', VERSION());
    SELECT CONCAT('H|', COALESCE(version,'NULL'), '|', COALESCE(CAST(checksum AS CHAR),'NULL'), '|', success, '|', type, '|', description)
    FROM flyway_schema_history ORDER BY installed_rank;
    SELECT CONCAT('R|', code) FROM app_role;
    SELECT CONCAT('A|', COUNT(DISTINCT u.id)) FROM app_user u JOIN user_role ur ON ur.user_id=u.id
    JOIN app_role r ON r.id=ur.role_id WHERE r.code='ADMIN' AND u.is_active=TRUE;
    SELECT CONCAT('P|', code, '|', IF(CAST(name AS BINARY) = CAST(code AS BINARY) AND description IS NULL,1,0))
    FROM permission WHERE UPPER(LEFT(code,9)) = 'EMPLOYEE_';
    SELECT CONCAT('G|', COALESCE(r.code,'MISSING_ROLE'), '|', p.code)
    FROM role_permission rp JOIN permission p ON p.id = rp.permission_id
    LEFT JOIN app_role r ON r.id = rp.role_id WHERE UPPER(LEFT(p.code,9)) = 'EMPLOYEE_';
    SELECT CONCAT('E|', TABLE_NAME, '|', ENGINE) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('app_role','permission','role_permission','flyway_schema_history');
    ROLLBACK;
'@

    $staffHistory = @(
        'H|1|-1142478933|1|SQL|create initial schema',
        'H|2|947719693|1|JDBC|register category permissions',
        'H|3|253774843|1|JDBC|grant category permissions',
        'H|4|-1732158983|1|JDBC|register product permissions',
        'H|5|155246154|1|JDBC|grant product permissions',
        'H|6|-1416705504|1|SQL|register supplier permissions',
        'H|7|982398228|1|SQL|grant supplier permissions',
        'H|8|151122022|1|SQL|register customer permissions',
        'H|9|985142827|1|SQL|grant customer permissions',
        'H|10|1376292394|1|SQL|register staff permissions',
        'H|11|792526816|1|SQL|grant staff permissions'
    )
    $staffCodes = @('EMPLOYEE_CREATE','EMPLOYEE_LOCK','EMPLOYEE_READ','EMPLOYEE_ROLE_ASSIGN','EMPLOYEE_UNLOCK','EMPLOYEE_UPDATE')
    $staffPermissions = @($staffCodes | ForEach-Object { "P|$_|1" })
    $staffGrants = @($staffCodes | ForEach-Object { "G|ADMIN|$_" })

    function Assert-StaffRows($actual, $expected, $label) {
        if ((@($actual) -join [char]10) -cne (@($expected) -join [char]10)) { throw "Unexpected $label. Stop rollout." }
    }

    function Assert-StaffState([ValidateSet(9,10,11)][int]$version) {
        $rows = @(& $staffMysql --no-defaults @staffConnection --database=computer_store_dev --batch --skip-column-names "--execute=$staffSql")
        if ($LASTEXITCODE -ne 0) { throw 'Read-back failed.' }
        $target = @($rows -cmatch '^T\|')
        if ($target.Count -ne 1 -or $target[0] -cnotmatch '^T\|computer_store_dev\|3306\|8\.0\.[0-9]+(?:[-+][^|]+)?$') {
            throw 'Unexpected live database, port, or MySQL version.'
        }
        Assert-StaffRows @($rows -cmatch '^H\|') $staffHistory[0..($version-1)] 'ordered Flyway history'
        Assert-StaffRows @($rows -cmatch '^R\|' | Sort-Object) @('R|ADMIN','R|MANAGER','R|SALES_STAFF','R|WAREHOUSE_STAFF') 'roles'
        $activeAdmins = @($rows -cmatch '^A\|[1-9][0-9]*$')
        if ($activeAdmins.Count -ne 1) { throw 'An existing active ADMIN is required.' }
        $engines = @('E|app_role|InnoDB','E|flyway_schema_history|InnoDB','E|permission|InnoDB','E|role_permission|InnoDB')
        Assert-StaffRows @($rows -cmatch '^E\|' | Sort-Object) $engines 'table engines'
        $permissions = @($rows -cmatch '^P\|' | Sort-Object)
        $grants = @($rows -cmatch '^G\|' | Sort-Object)
        $expectedPermissions = if ($version -ge 10) { $staffPermissions }
        $expectedGrants = if ($version -eq 11) { $staffGrants }
        Assert-StaffRows $permissions $expectedPermissions 'Staff permission rows'
        Assert-StaffRows $grants $expectedGrants 'Staff grant rows'
        $serverVersion = ($target[0] -split '\|')[3]
        Write-Host "PASS: computer_store_dev on 127.0.0.1:3306 (MySQL $serverVersion), V$version, $($permissions.Count) Staff permissions, $($grants.Count) Staff grants."
    }
    Assert-StaffState 9
    $staffVerified = 9
}
~~~

**PASS** `V9, 0 Staff permissions, 0 Staff grants`. **If it fails** stop. Investigate mismatched history, target, administrator availability or pre-existing staff grants. Leave existing grants untouched.

## 4. Freeze writes and create a new backup

Stop CSM and any other writers using their own consoles. Keep the MySQL service running and maintain the freeze through Step 8. The port 8080 check catches the usual backend listener. The backup path is created automatically outside Git. **Database** Read-only dump, with filesystem writes.

~~~powershell
. {
    if ($staffVerified -ne 9) { throw 'Complete Step 3 first.' }
    if (Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -eq 8080) {
        throw 'Port 8080 still has a listener. Check that the backend and all writers are stopped.'
    }
    Assert-StaffState 9

    $backupRoot = Join-Path $env:USERPROFILE 'CSM-Backups'
    [void](New-Item -ItemType Directory -Path $backupRoot -Force)
    if ((Get-Item -LiteralPath $backupRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'CSM-Backups must be a local directory without redirection.'
    }
    $backupRoot = (Resolve-Path -LiteralPath $backupRoot).Path
    if ($backupRoot.Equals($staffRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $backupRoot.StartsWith($staffRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The backup must be outside the repository.'
    }
    $backupStamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $backupDir = Join-Path $backupRoot ('staff-pre-v10-' + $backupStamp + '-' + [guid]::NewGuid().ToString('N'))
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

    $staffBackupHash = $null
    $staffBackup = Join-Path $backupDir 'computer_store_dev.sql'
    $partial = Join-Path $backupDir 'computer_store_dev.partial'
    $dumpOptions = @('--single-transaction', '--quick', '--skip-lock-tables', '--no-tablespaces',
        '--set-gtid-purged=OFF', '--column-statistics=0', '--default-character-set=utf8mb4',
        '--routines', '--events', '--triggers', '--hex-blob', '--comments', '--dump-date',
        "--result-file=$partial", '--databases', 'computer_store_dev')
    & $staffDump --no-defaults @staffConnection @dumpOptions
    if ($LASTEXITCODE -ne 0) { throw 'mysqldump failed. Keep the partial file private and do not use it.' }
    Move-Item -LiteralPath $partial -Destination $staffBackup
    Write-Host "PASS: Dump created at $staffBackup"
}
~~~

**PASS** The dump path is printed under the user’s `CSM-Backups` directory. **If it fails** correct the cause and start a fresh backup while writes remain frozen.

## 5. Verify the backup

Verifies the dump target, completion marker and size and records SHA-256. The runner step rechecks this hash before writes. **Database** No connection.

~~~powershell
. {
    function Get-VerifiedStaffBackup {
        if (!$staffBackup -or !(Test-Path -LiteralPath $staffBackup -PathType Leaf)) {
            throw 'Staff backup is missing. Complete Step 4.'
        }
        $file = Get-Item -LiteralPath $staffBackup
        if ($file.Length -le 0) { throw 'Staff backup is empty.' }

        $targets = @(Select-String -LiteralPath $file.FullName -Pattern '^\s*USE\s+' |
            ForEach-Object { $_.Line.Trim() })
        if ($targets.Count -ne 1 -or $targets[0] -cne 'USE `computer_store_dev`;') {
            throw 'Dump does not select exactly computer_store_dev.'
        }

        $lastLine = Get-Content -LiteralPath $file.FullName -Tail 30 |
            Where-Object { $_.Trim() } | Select-Object -Last 1
        if ($lastLine -cnotmatch '^-- Dump completed on .+$') {
            throw 'Dump completion marker is missing.'
        }
        [pscustomobject]@{
            Path = $file.FullName
            Bytes = $file.Length
            SHA256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        }
    }

    function Assert-StaffBackup {
        if (!$staffBackupHash) { throw 'Complete Step 5 first.' }
        $verified = Get-VerifiedStaffBackup
        if ($verified.SHA256 -cne $staffBackupHash) { throw 'Staff backup checksum changed.' }
    }

    $verified = Get-VerifiedStaffBackup
    $hashFile = "$staffBackup.sha256"
    Set-Content -LiteralPath $hashFile -Value $verified.SHA256 -Encoding ASCII
    $staffBackupHash = $verified.SHA256
    $verified | Format-List
    Write-Host "SHA-256 record: $hashFile"
    Write-Host 'PASS: Verified the pre-V10 backup and recorded its SHA-256.'
}
~~~

**PASS** Path, byte count and SHA-256 are printed and the hash is recorded beside the dump. **If it fails** stop and take a new backup. Structural verification does not prove restoration. Test restores only on a disposable instance. If the freeze ended before V10, repeat Steps 3–5.

## 6. Compile the runner without running migrations

Builds the classpath with Java 21, and verifies the four prior Java migration artifacts. Defining the stage function does not run it. **Database** No connection.

~~~powershell
. {
    Assert-StaffBackup
    $staffRunnerReady = $false
    Set-Location (Join-Path $staffRoot 'backend')
    java -version
    if ($LASTEXITCODE -ne 0) { throw 'Java is missing.' }
    .\mvnw.cmd -v
    if ($LASTEXITCODE -ne 0) { throw 'Maven wrapper check failed.' }
    .\mvnw.cmd -DskipTests compile dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/staff-rollout-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Runner build failed.' }

    $staffClasspath = @((Join-Path $PWD 'target/classes'),
        (Get-Content -LiteralPath 'target/staff-rollout-classpath.txt' -Raw).Trim()) -join ';'
    $staffRunner = 'com.computerstore.computer_store_backend.operations.staff.v1.StaffPermissionMigrationRunner'
    & java -cp $staffClasspath $staffRunner --verify-artifacts
    if ($LASTEXITCODE -ne 0) { throw 'Migration artifact verification failed.' }

    function Invoke-StaffStage([ValidateSet('A','B')][string]$stage) {
        if (!$staffRunnerReady) { throw 'Complete Step 6 first.' }
        Assert-StaffBackup
        $secret = Read-Host "Local MySQL password for stage $stage" -AsSecureString
        try {
            if (!$secret -or $secret.Length -eq 0) { throw 'Enter your local MySQL password.' }
            $env:DB_USERNAME = $staffUser
            $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
            & java -cp $staffClasspath $staffRunner --staff-permission-rollout --approve-iam-005 "--stage=$stage" --confirm-target=computer_store_dev
            if ($LASTEXITCODE -ne 0) { throw "Stage $stage stopped. Inspect history before any retry or repair." }
        } finally {
            Remove-Item Env:DB_USERNAME,Env:DB_PASSWORD -ErrorAction SilentlyContinue
            if ($secret) { $secret.Dispose() }
        }
    }
    $staffRunnerReady = $true
    Write-Host 'PASS: Staff runner ready. No stage was executed.'
}
~~~

**PASS** Maven reports `BUILD SUCCESS`, predecessor artifacts match, and no stage was executed. **If it fails** check Java, Maven and checkout artifacts.

## 7. Register the six staff permissions

Runs Stage A after rechecking V9 and backup. **Database write** Registers six rows and Flyway V10.

~~~powershell
. {
    if ($staffVerified -ne 9) { throw 'Stage A requires this session to have verified V9.' }
    Assert-StaffState 9
    $staffVerified = 0
    Invoke-StaffStage A
    Assert-StaffState 10
    $staffVerified = 10
}
~~~

**PASS** V10 checksum `1376292394`, six compatible permissions, zero staff grants. **If it fails** keep the write freeze and preserve the verified backup. Inspect live history with read-only queries. Do not retry or repair automatically.

## 8. Grant the permissions to ADMIN

Runs Stage B after rechecking V10 and backup. **Database write** Inserts six role mappings and Flyway V11.

~~~powershell
. {
    if ($staffVerified -ne 10) { throw 'Stage B requires this session to have verified V10.' }
    Assert-StaffState 10
    $staffVerified = 0
    Invoke-StaffStage B
    Assert-StaffState 11
    $staffVerified = 11
    $env:MYSQL_TEST_LOGIN_FILE = $staffLoginFileBefore
}
~~~

**PASS** V11 checksum `792526816` and six exact ADMIN pairs, with no other staff grants. End the freeze after the read-back succeeds. **If it fails** leave the feature disabled. Record history and data state. Review recovery with the operator before any retry or repair.

## 9. Start the backend after verified V11

Daily command in a fresh terminal after one-time rollout. Leave the feature flag unset on other targets until verified. This command explicitly disables startup Flyway, validates Hibernate schema and binds to localhost. **Database** Startup validates the existing schema. Later staff actions write only when authorized.

~~~powershell
& {
    $ErrorActionPreference = 'Stop'
    Set-Location 'C:\Code\computer-store-management\backend'
    try {
        $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/computer_store_dev'
        $env:DB_USERNAME = Read-Host 'Local MySQL username'
        $appSecret = Read-Host 'Local MySQL password' -AsSecureString
        if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or !$appSecret -or $appSecret.Length -eq 0) {
            throw 'Enter your local MySQL username and password.'
        }
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $appSecret).Password
        .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--spring.flyway.enabled=false --csm.staff-management.enabled=true --spring.jpa.hibernate.ddl-auto=validate --server.address=127.0.0.1 --server.port=8080'
        if ($LASTEXITCODE -ne 0) { throw 'Backend startup or shutdown reported an error.' }
    } finally {
        Remove-Item Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD -ErrorAction SilentlyContinue
        if ($appSecret) { $appSecret.Dispose() }
    }
}
~~~

**PASS** Spring Boot listens on `127.0.0.1:8080`. **If it fails** stop with Ctrl+C and check the reported target, JDBC credentials and schema validation. Keep Flyway disabled.

## 10. Start the frontend and smoke-test

Daily command in a separate terminal. Use `npm.cmd run dev` alone on routine restarts with installed dependencies. **Database** Frontend startup and GET checks make no writes.

~~~powershell
Set-Location 'C:\Code\computer-store-management\frontend'
npm.cmd ci
if ($LASTEXITCODE -ne 0) { throw 'Frontend install failed.' }
npm.cmd run dev
~~~

**PASS** Vite listens on `http://localhost:5173`. Open `/employees` with an existing Admin. Verify the list loads, a record opens and no sensitive authentication fields appear. Verify staff navigation is absent and the direct route is denied for an existing non-Admin if available. Use a disposable fixture for create, edit, assignment, lock and unlock smoke. **If it fails** record the role, route and HTTP status. Stop both servers with Ctrl+C. Steps 1–8 are one-time rollout commands and must not run on a routine restart.
