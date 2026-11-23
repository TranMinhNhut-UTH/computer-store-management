# IAM-005 local rollout on Windows

## 1. Purpose and one-time usage

This rollout installs six proposed Staff permissions and six explicit ADMIN mappings on `127.0.0.1:3306/computer_store_dev`, then enables the Staff routes for the local backend. Start only after approval of that exact payload and this local execution. Steps 1–11 are one-time. The backend stays stopped from the write freeze through rollout verification. The regular startup commands are in Step 12.

Use one PowerShell terminal through Step 11. `cls` clears only the screen. Opening a new terminal loses variables and functions. Keep passwords in the terminal prompts. **Database** No connection in this step.

~~~powershell
. {
    Set-Location -LiteralPath 'C:\Code\computer-store-management' -ErrorAction Stop
    $ErrorActionPreference = 'Stop'
    $staffRoot = (Get-Location).Path
    Write-Host "PASS: Using $staffRoot"
}
~~~

**PASS** The expected workspace prints. **If it fails** locate the existing repository before proceeding.

## 2. Verify the Git branch

Confirm the reviewed release checkout and an untouched tracked worktree before relying on the migration runner or patch. Untracked Product files and other personal files may remain. **Database** No connection.

~~~powershell
. {
    $branch = git branch --show-current
    if ($LASTEXITCODE -ne 0 -or $branch -cnotin @('feature/staff-account-management','main')) {
        throw 'Use feature/staff-account-management, or main after the release is merged.'
    }
    $tracked = @(git status --porcelain=v1 --untracked-files=no)
    if ($LASTEXITCODE -ne 0 -or $tracked.Count -ne 0) { throw 'Tracked changes must be resolved before rollout.' }
    foreach ($file in @('backend/src/main/resources/operations/staff/v1/V10__register_staff_permissions.sql',
                         'backend/src/main/resources/operations/staff/v1/V11__grant_staff_permissions.sql',
                         'docs/staff-http-release.patch')) {
        git ls-files --error-unmatch -- $file
        if ($LASTEXITCODE -ne 0) { throw "Reviewed release file is missing: $file" }
    }
    git apply --check docs/staff-http-release.patch
    if ($LASTEXITCODE -ne 0) { throw 'Release patch does not apply. Resolve the current local activation state first.' }
    Write-Host "PASS: Reviewed release files on $branch. Local activation patch is ready."
}
~~~

**PASS** The branch, three tracked rollout paths and patch check pass. **If it fails** resolve the checkout or existing activation state without discarding local changes.

## 3. Configure MySQL tools

Check the local MySQL 8.0 client binaries and fix the TCP target at `127.0.0.1:3306`. Enter the username. MySQL prompts for a password on each connection. **Database** No connection.

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
    $staffExpectedVersion = 9
    $staffFrozen = $false
    $staffRunnerReady = $false
    Write-Host 'PASS: MySQL tools configured for 127.0.0.1:3306.'
}
~~~

**PASS** Both client versions and the local target print. **If it fails** correct the MySQL 8.0 installation path or username.

## 4. Verify live Flyway V1–V9

Compare actual history and checksums with the rollout baseline. Check the exact roles, an existing active Admin, InnoDB tables and absence of Staff permissions or grants. **Database** Read-only transaction.

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
    $staffVerified = 0
    Assert-StaffState $staffExpectedVersion
    $staffVerified = $staffExpectedVersion
}
~~~

**PASS** `V9, 0 Staff permissions, 0 Staff grants`. **If it fails** stop. If V10 or V11 already appears, inspect history and read-back before deciding how to resume. Never rerun a previously applied version.

## 5. Freeze database writes

Stop the backend and any other writers using their own consoles, and pause jobs and other operators that can write to this schema. Keep MySQL running. The port check detects the usual CSM listener. The confirmation covers writers using other ports or connections. Maintain the freeze until Step 10 has passed. **Database** No connection.

~~~powershell
. {
    if ($staffVerified -ne 9) { throw 'Complete Step 4 first.' }
    $staffFrozen = $false
    if (Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -eq 8080) {
        throw 'Port 8080 still has a listener. Stop CSM before continuing.'
    }
    if ((Read-Host 'Type FROZEN after stopping CSM and all other schema writers') -cne 'FROZEN') {
        throw 'An uninterrupted write freeze is required.'
    }
    $staffFrozen = $true
    Write-Host 'PASS: Operator confirmed that all database writes are frozen.'
}
~~~

**PASS** The operator confirmation prints. **If it fails** stop the remaining writers and confirm again before backup.

## 6. Create a fresh backup

Recheck the live target under the write freeze, then create a new private backup in the Windows user profile. The script renames the partial file only after `mysqldump` succeeds. **Database** Read-only dump. Only the backup file changes.

~~~powershell
. {
    if (!$staffFrozen -or $staffVerified -ne 9) { throw 'Complete Steps 4 and 5 first.' }
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

**PASS** The new dump path prints under `%USERPROFILE%\CSM-Backups`. **If it fails** preserve any partial file privately, fix the cause and take a fresh backup while writes remain frozen.

## 7. Verify the backup and SHA-256

Check the schema selector, dump completion marker and file size, then record SHA-256 next to the dump. The runner checks this hash again before writes. **Database** No connection.

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

**PASS** Path, size and a recorded SHA-256 print. **If it fails** stop and take a fresh backup. A structural check does not certify recovery. Test restoration only on disposable MySQL. If the freeze was broken, repeat Steps 4–7 before V10.

## 8. Build and verify the migration runner

Compile with Java 21 and the Maven wrapper. Verify the original Category and Product Java artifacts and prepare a function for explicit Stage A or B execution. Defining the function does not run it. **Database** No connection.

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
        if (!$staffRunnerReady) { throw 'Complete Step 8 first.' }
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

**PASS** Maven reports `BUILD SUCCESS` and the predecessor artifact check passes. **If it fails** fix the JDK, Maven or checkout artifacts before starting a stage.

## 9. Execute V10 and verify read-back

Check V9 again and execute only Stage A after verifying the backup hash. **Database write** The stage registers six permissions and records Flyway V10. The postcheck is read-only.

~~~powershell
. {
    if (!$staffFrozen -or $staffVerified -ne 9) { throw 'Stage A requires this session to have verified V9.' }
    Assert-StaffState 9
    $staffVerified = 0
    Invoke-StaffStage A
    Assert-StaffState 10
    $staffVerified = 10
}
~~~

**PASS** V10 checksum `1376292394`, six permissions and zero Staff grants. **If it fails** keep the backup and freeze. Inspect live history with read-only queries before retry, repair or any later step.

## 10. Execute V11 and verify read-back

Check V10 again and execute only Stage B. **Database write** The stage creates six explicit ADMIN mappings and records Flyway V11. The postcheck is read-only.

~~~powershell
. {
    if (!$staffFrozen -or $staffVerified -ne 10) { throw 'Stage B requires this session to have verified V10.' }
    Assert-StaffState 10
    $staffVerified = 0
    Invoke-StaffStage B
    Assert-StaffState 11
    $staffVerified = 11
}
~~~

**PASS** V11 checksum `792526816` and exactly six ADMIN pairs. **If it fails** keep Staff disabled and preserve the backup and current history. Review recovery before any retry or repair.

## 11. Apply the Staff HTTP release patch

Repeat the live V11 check before adding the operator-local activation file. Normal startup stays disabled because the file is loaded only by an explicit backend argument. The patch does not change the other route sets. **Database** Read-only verification. The patch changes one ignored local file.

~~~powershell
. {
    if ($staffVerified -ne 11) { throw 'Step 10 must verify live V11 before applying the release patch.' }
    Assert-StaffState 11
    Set-Location $staffRoot
    git apply --check docs/staff-http-release.patch
    if ($LASTEXITCODE -ne 0) { throw 'Patch check failed. Leave Staff disabled.' }
    git apply docs/staff-http-release.patch
    if ($LASTEXITCODE -ne 0) { throw 'Patch failed. Leave Staff disabled.' }
    $env:MYSQL_TEST_LOGIN_FILE = $staffLoginFileBefore
    $staffFrozen = $false
    Write-Host 'PASS: Staff activation file installed locally. The backend is still stopped.'
}
~~~

**PASS** The activation file is installed and the backend is still stopped. The write freeze may now end. **If it fails** leave Staff disabled and resolve the read-back or patch problem. If a terminal closed after V11 succeeded and the patch was never applied, run Steps 1–3 in the new terminal, then run `$staffExpectedVersion = 11` and paste Step 4 unchanged. Continue only if the read-back prints `V11, 6 Staff permissions, 6 Staff grants`. Go directly to Step 11. Skip both migration stages.

## 12. Run tests, start BE/FE and smoke-test

Run release checks in the rollout terminal before starting the upgraded app. This test set uses isolated MVC and authorization fixtures. **Database** No connection during checks.

~~~powershell
. {
    if ($staffVerified -ne 11 -or !(Test-Path -LiteralPath (Join-Path $staffRoot 'backend/config/staff-local.properties') -PathType Leaf)) {
        throw 'Verify V11 and apply the local patch before release checks.'
    }
    Set-Location (Join-Path $staffRoot 'backend')
    .\mvnw.cmd '-Dtest=SecurityConfigTest,AuthenticationHttpTest,PermissionPolicyMappingTest,CurrentActorServiceTest,RoleResolutionServiceTest,CategorySecurityTest,ProductRouteReadinessTest,SupplierSecurityTest,CustomerSecurityTest,ReportsSecurityTest' package
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests or package failed.' }

    Set-Location (Join-Path $staffRoot 'frontend')
    npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'Frontend install failed.' }
    node --test --test-concurrency=1 src/app/AppLayout.test.js src/features/employees/StaffPage.test.js
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
    npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    Write-Host 'PASS: Backend tests and package, frontend tests and build.'
}
~~~

**PASS** Backend tests/package, frontend tests and production build succeed. **If it fails** stop before app startup and fix the failing check.

Afterward, start the backend from a fresh PowerShell terminal. This is the daily backend command after the verified rollout. It explicitly loads the ignored file, disables Flyway and binds to the local server. **Database** Startup validates the existing schema. Staff mutations can write after authorized use.

~~~powershell
& {
    $ErrorActionPreference = 'Stop'
    Set-Location 'C:\Code\computer-store-management\backend'
    $activation = 'config/staff-local.properties'
    if (!(Test-Path -LiteralPath $activation -PathType Leaf) -or
        (Get-Content -LiteralPath $activation -Raw).Trim() -cne 'csm.staff-management.enabled=true') {
        throw 'The verified local Staff activation file is missing or invalid.'
    }
    try {
        $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/computer_store_dev'
        $env:DB_USERNAME = Read-Host 'Local MySQL username'
        $appSecret = Read-Host 'Local MySQL password' -AsSecureString
        if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or !$appSecret -or $appSecret.Length -eq 0) {
            throw 'Enter your local MySQL username and password.'
        }
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $appSecret).Password
        .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--spring.flyway.enabled=false --spring.config.additional-location=file:./config/staff-local.properties --spring.jpa.hibernate.ddl-auto=validate --server.address=127.0.0.1 --server.port=8080'
        if ($LASTEXITCODE -ne 0) { throw 'Backend startup or shutdown reported an error.' }
    } finally {
        Remove-Item Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD -ErrorAction SilentlyContinue
        if ($appSecret) { $appSecret.Dispose() }
    }
}
~~~

**PASS** Spring Boot listens on `127.0.0.1:8080`. **If it fails** check the file, MySQL credentials, schema, or port. Keep Flyway disabled.

Start the frontend in another terminal. This is the daily frontend command after dependencies have been installed by the release check. **Database** No connection at startup.

~~~powershell
Set-Location 'C:\Code\computer-store-management\frontend'
npm.cmd run dev
~~~

**PASS** Vite listens on `http://localhost:5173`. **If it fails** check Node, dependencies, and port 5173.

From a third terminal, check backend health and the anonymous Staff route via the frontend proxy. **Database** Read-only checks.

~~~powershell
. {
    Invoke-RestMethod 'http://127.0.0.1:8080/api/health'
    $status = curl.exe --silent --show-error --output NUL --write-out '%{http_code}' 'http://localhost:5173/api/v1/employees'
    if ($LASTEXITCODE -ne 0 -or $status -cne '401') { throw "Expected anonymous Staff HTTP 401, got $status." }
    Write-Host 'PASS: Backend health and anonymous Staff denial verified.'
}
~~~

**PASS** Health responds and anonymous Staff returns `401`. Open `http://localhost:5173/employees` with an existing Admin to verify list and detail. An existing non-Admin should have no Staff menu entry and receive `403` on direct access. Use disposable data for mutation smoke. **If it fails** record the role, route and HTTP status and stop release. Stop both processes with Ctrl+C. To disable Staff on a later run, omit the additional-location argument and restart the backend.
