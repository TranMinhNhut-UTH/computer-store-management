# Customer V8/V9 local rollout on Windows

Keep the Customer application stopped until Step 10, including when the checkout already contains the release patch. Use this guide for the separately authorized rollout on MySQL 8.0 at `127.0.0.1:3306/computer_store_dev`. The approved Customer grants are Manager read and Sales Staff read, create and update. Set aside a maintenance window and make sure you can stop every process or person writing to this schema before the backup.

Open a dedicated PowerShell terminal. Paste **one numbered block at a time** and wait for its success message. The blocks share variables and functions. `cls` only clears the screen. Closing the terminal or opening a new one loses the variables and functions. Steps 1–10 use the same rollout terminal. Step 10 uses two additional terminals only for app startup. If the rollout terminal closes after a migration was attempted, stop, preserve the backup and inspect live history before deciding how to resume. Keep the write freeze in place.

## Step 1 — Open PowerShell and verify the branch

**GIT / READ-ONLY.** From the single workspace, verify the branch and inspect the worktree before loading anything.

~~~powershell
. {
    $ErrorActionPreference = 'Stop'
    Set-Location 'C:\Code\computer-store-management'
    $customerRoot = (Get-Location).Path

    $branch = git branch --show-current
    if ($LASTEXITCODE -ne 0 -or $branch -cne 'feature/customer-management') {
        throw 'Open the feature/customer-management checkout before continuing.'
    }
    $customerSourceState = @(git status --porcelain=v1 --untracked-files=no)
    if ($LASTEXITCODE -ne 0 -or $customerSourceState.Count) {
        throw 'Resolve tracked worktree or staged changes before rollout.'
    }
    git status --short --branch
    if ($LASTEXITCODE -ne 0) { throw 'Git status failed.' }
    git apply --reverse --check docs/customer-http-release.patch 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "PASS: $branch. Tracked files are clean and the release patch is already in source."
    } else {
        git apply --check docs/customer-http-release.patch
        if ($LASTEXITCODE -ne 0) { throw 'The Customer release patch does not match this checkout.' }
        Write-Host "PASS: $branch. Tracked files are clean and the release patch applies."
    }
}
~~~

**Expected** The branch is `feature/customer-management` and the patch either applies cleanly or is already present. Personal untracked files, including the local Product runbook and patch, may appear. **If it fails** stop and resolve the checkout or missing patch without discarding local work. **Database** No connection or changes.

## Step 2 — Configure the MySQL tools

**LOCAL SETUP.** Check the MySQL client and dump tool and enter your local MySQL username. The tools prompt for the password without echo when they connect in later steps.

~~~powershell
. {
    $mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
    $customerMysql = Join-Path $mysqlBin 'mysql.exe'
    $customerDump = Join-Path $mysqlBin 'mysqldump.exe'
    foreach ($tool in @($customerMysql, $customerDump)) {
        if (!(Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Tool not found: $tool" }
        & $tool --version
        if ($LASTEXITCODE -ne 0) { throw "Tool check failed: $tool" }
    }

    $customerUser = Read-Host 'Local MySQL username'
    if ([string]::IsNullOrWhiteSpace($customerUser)) { throw 'Enter your local MySQL username.' }
    $customerLoginFileBefore = $env:MYSQL_TEST_LOGIN_FILE
    $env:MYSQL_TEST_LOGIN_FILE = Join-Path ([IO.Path]::GetTempPath()) ('csm-no-login-' + [guid]::NewGuid())
    $customerConnection = @('--protocol=TCP', '--host=127.0.0.1', '--port=3306', "--user=$customerUser", '--password')
    $customerVerified = 0
    $customerRunnerReady = $false
    Write-Host 'PASS: MySQL tools configured for 127.0.0.1:3306.'
}
~~~

**Expected** Both tools print their versions and the configuration check passes. **If it fails** use the actual MySQL 8.0 installation's `bin` directory or obtain valid local credentials. Passwords belong only in the CLI or secure terminal prompts. **Database** No connection or changes.

## Step 3 — Verify live V1–V7 history

**READ-ONLY.** Define the verification functions and read the actual schema, Flyway history, roles, table engines, and Customer rows. At the MySQL prompt, enter the local MySQL password.

~~~powershell
. {
    $customerSql = @'
    START TRANSACTION READ ONLY;
    SELECT CONCAT('T|', DATABASE(), '|', @@port, '|', VERSION());
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
        $target = @($rows -cmatch '^T\|')
        if ($target.Count -ne 1 -or $target[0] -cnotmatch '^T\|computer_store_dev\|3306\|8\.0\.[0-9]+(?:[-+][^|]+)?$') {
            throw 'Unexpected live database, port, or MySQL version.'
        }
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
        $serverVersion = ($target[0] -split '\|')[3]
        Write-Host "PASS: computer_store_dev on 127.0.0.1:3306 (MySQL $serverVersion), V$version, $($permissions.Count) Customer permissions, $($grants.Count) Customer grants."
    }
    Assert-CustomerState 7
    $customerVerified = 7
}
~~~

**Expected** `PASS: computer_store_dev on 127.0.0.1:3306 (MySQL 8.0.x), V7, 0 Customer permissions, 0 Customer grants.` History contains exactly successful V1–V7 with the checksums in the block. **If it fails** stop and investigate the target, credentials or reported history or state. An existing V8 or V9 requires a separate review before resuming. **Database** Read-only transaction.

## Step 4 — Freeze writes and create a fresh backup

**BACKUP.** Stop the CSM backend and every other writer to `computer_store_dev` before pasting this block. Use `Ctrl+C` in the backend's own terminal and pause any jobs, CLI operators or other applications that can write. Keep MySQL running and the write freeze in place through Step 8. The port check catches a local backend still listening on the usual port. Coordinate separately with writers on any other port or connection.

The block rechecks live V7, then creates a new private directory under your Windows user profile. `mysqldump` writes to a partial file and renames it only after the command succeeds. Enter the MySQL password at each prompt.

~~~powershell
. {
    if ($customerVerified -ne 7) { throw 'Complete Step 3 first.' }
    if (Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -eq 8080) {
        throw 'Port 8080 still has a listener. Check that the backend and all writers are stopped.'
    }
    Assert-CustomerState 7

    $backupRoot = Join-Path $env:USERPROFILE 'CSM-Backups'
    [void](New-Item -ItemType Directory -Path $backupRoot -Force)
    if ((Get-Item -LiteralPath $backupRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'CSM-Backups must be a local directory without redirection.'
    }
    $backupRoot = (Resolve-Path -LiteralPath $backupRoot).Path
    if ($backupRoot.Equals($customerRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $backupRoot.StartsWith($customerRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The backup must be outside the repository.'
    }
    $backupStamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $backupDir = Join-Path $backupRoot ('customer-pre-v8-' + $backupStamp + '-' + [guid]::NewGuid().ToString('N'))
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

    $customerBackupHash = $null
    $customerBackup = Join-Path $backupDir 'computer_store_dev.sql'
    $partial = Join-Path $backupDir 'computer_store_dev.partial'
    $dumpOptions = @('--single-transaction', '--quick', '--skip-lock-tables', '--no-tablespaces',
        '--set-gtid-purged=OFF', '--column-statistics=0', '--default-character-set=utf8mb4',
        '--routines', '--events', '--triggers', '--hex-blob', '--comments', '--dump-date',
        "--result-file=$partial", '--databases', 'computer_store_dev')
    & $customerDump --no-defaults @customerConnection @dumpOptions
    if ($LASTEXITCODE -ne 0) { throw 'mysqldump failed. Keep the partial file private and do not use it.' }
    Move-Item -LiteralPath $partial -Destination $customerBackup
    Write-Host "PASS: Dump created at $customerBackup"
}
~~~

**Expected** The output shows a new path under `%USERPROFILE%\CSM-Backups\customer-pre-v8-*`. **If it fails** stop before migration. Resolve the listener, filesystem, dump privileges or other error and create a fresh backup under the write freeze. **Database** Reads the local schema. Writes the backup file only.

## Step 5 — Verify the backup and record SHA-256

**BACKUP VERIFICATION.** Check that the file is nonempty, selects exactly `computer_store_dev`, and ends with the dump completion marker. Record its path, byte count and hash automatically. The same verification function will recheck the file before each migration stage.

~~~powershell
. {
    function Get-VerifiedCustomerBackup {
        if (!$customerBackup -or !(Test-Path -LiteralPath $customerBackup -PathType Leaf)) {
            throw 'Customer backup is missing. Complete Step 4.'
        }
        $file = Get-Item -LiteralPath $customerBackup
        if ($file.Length -le 0) { throw 'Customer backup is empty.' }

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

    function Assert-CustomerBackup {
        if (!$customerBackupHash) { throw 'Complete Step 5 first.' }
        $verified = Get-VerifiedCustomerBackup
        if ($verified.SHA256 -cne $customerBackupHash) { throw 'Customer backup checksum changed.' }
    }

    $verified = Get-VerifiedCustomerBackup
    $hashFile = "$customerBackup.sha256"
    Set-Content -LiteralPath $hashFile -Value $verified.SHA256 -Encoding ASCII
    $customerBackupHash = $verified.SHA256
    $verified | Format-List
    Write-Host "SHA-256 record: $hashFile"
    Write-Host 'PASS: Verified the pre-V8 backup and recorded its SHA-256.'
}
~~~

**Expected** The full backup path, a positive byte count, and a 64-character SHA-256 saved beside the dump. **If it fails** stop. Find the cause and take a fresh backup while writes remain frozen. **Database** No connection or changes.

Structural checks and SHA-256 confirm the file's markers and allow later byte-for-byte verification. They do not prove that a restore will succeed. For that evidence, restore into a disposable MySQL instance isolated from the local development server. The dump contains sensitive data and a `USE computer_store_dev` statement. Keep it private and never run it against the active schema for a test. If the write freeze is broken before V8, repeat Steps 3–5.

## Step 6 — Build and verify the runner

**OFFLINE BUILD.** Compile with Java 21 using the Maven wrapper, generate the runtime classpath and verify the original Category and Product Java migration artifacts. The selected stage later verifies resolved V1–V9 metadata against the live history. This block defines the execution function without invoking it.

~~~powershell
. {
    Assert-CustomerBackup
    $customerRunnerReady = $false
    Set-Location (Join-Path $customerRoot 'backend')
    java -version
    if ($LASTEXITCODE -ne 0) { throw 'Java is missing.' }
    .\mvnw.cmd -v
    if ($LASTEXITCODE -ne 0) { throw 'Maven wrapper check failed.' }
    .\mvnw.cmd -DskipTests compile dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/customer-rollout-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Runner build failed.' }

    $customerClasspath = @((Join-Path $PWD 'target/classes'),
        (Get-Content -LiteralPath 'target/customer-rollout-classpath.txt' -Raw).Trim()) -join ';'
    $customerRunner = 'com.computerstore.computer_store_backend.operations.customer.v1.CustomerPermissionMigrationRunner'
    & java -cp $customerClasspath $customerRunner --verify-artifacts
    if ($LASTEXITCODE -ne 0) { throw 'Migration artifact verification failed.' }

    function Invoke-CustomerStage([ValidateSet('A','B')][string]$stage) {
        if (!$customerRunnerReady) { throw 'Complete Step 6 first.' }
        Assert-CustomerBackup
        $secret = Read-Host "Local MySQL password for stage $stage" -AsSecureString
        try {
            if (!$secret -or $secret.Length -eq 0) { throw 'Enter your local MySQL password.' }
            $env:DB_USERNAME = $customerUser
            $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
            & java -cp $customerClasspath $customerRunner --customer-permission-rollout "--stage=$stage" --confirm-target=computer_store_dev
            if ($LASTEXITCODE -ne 0) { throw "Stage $stage stopped. Inspect history before any retry or repair." }
        } finally {
            Remove-Item Env:DB_USERNAME,Env:DB_PASSWORD -ErrorAction SilentlyContinue
            if ($secret) { $secret.Dispose() }
        }
    }
    $customerRunnerReady = $true
    Write-Host 'PASS: Customer runner ready. No stage was executed.'
}
~~~

**Expected** Java 21, Maven `BUILD SUCCESS`, and `Category V2/V3 and Product V4/V5 artifacts match expected metadata.` **If it fails** check the JDK, Maven wrapper, dependencies and branch. Stop before running a stage. **Database** No connection or changes. The runner uses the fixed local JDBC target. The secured password prompt supplies a temporary process environment variable for Java, then clears it.

## Step 7 — Execute only V8 and verify read-back

**DATABASE WRITE.** Proceed only in the approved maintenance window, with the write freeze still in place. Reconfirm V7, run Stage A, then verify live V8. Enter the local MySQL password at each prompt.

~~~powershell
. {
    if ($customerVerified -ne 7) { throw 'Stage A requires this session to have verified V7.' }
    Assert-CustomerState 7
    $customerVerified = 0
    Invoke-CustomerStage A
    Assert-CustomerState 8
    $customerVerified = 8
}
~~~

**Expected** The runner reports `Customer V8 applied`. The separate read-back verifies checksum `151122022`, exactly `CUSTOMER_READ`, `CUSTOMER_CREATE` and `CUSTOMER_UPDATE` with `name = code` and null descriptions, and zero Customer grants. **If it fails** keep the freeze and backup. Inspect live history and permission state with read-only checks before any retry, repair or Stage B. **Database** V8 registers three permissions and records Flyway V8. Verification reads only.

## Step 8 — Execute only V9 and verify read-back

**DATABASE WRITE.** Proceed after Step 7 passed. Reconfirm live V8, run Stage B, then verify live V9. Enter the local MySQL password at each prompt.

~~~powershell
. {
    if ($customerVerified -ne 8) { throw 'Stage B requires this session to have verified V8.' }
    Assert-CustomerState 8
    $customerVerified = 0
    Invoke-CustomerStage B
    Assert-CustomerState 9
    $customerVerified = 9
    $env:MYSQL_TEST_LOGIN_FILE = $customerLoginFileBefore
}
~~~

**Expected** The runner reports `Customer V9 applied`. The separate read-back verifies checksum `985142827`, three permissions and exactly four grants. `MANAGER` has `CUSTOMER_READ`. `SALES_STAFF` has read, create and update. `ADMIN` and `WAREHOUSE_STAFF` have zero Customer grants. **If it fails** keep the freeze and backup, inspect live state and stop before Step 9. **Database** V9 creates four mappings and records Flyway V9. Verification reads only. The freeze can end once live V9 verification passes.

## Step 9 — Apply the Customer HTTP release patch

**GIT / SOURCE CHANGE.** In the same rollout terminal, check the patch before applying it. It enables GET list, GET detail, POST create and PUT update. Permission checks, CSRF and the Category, Product and Supplier routes stay in place.

~~~powershell
. {
    if ($customerVerified -ne 9) { throw 'Verified live V9 is required before route activation.' }
    Set-Location $customerRoot
    git apply --reverse --check docs/customer-http-release.patch 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host 'PASS: Customer routes are already enabled in this checkout.'
    } else {
        git apply --check docs/customer-http-release.patch
        if ($LASTEXITCODE -ne 0) { throw 'Customer patch check failed. Stop before changing source.' }
        git apply docs/customer-http-release.patch
        if ($LASTEXITCODE -ne 0) { throw 'Customer patch did not apply.' }
        Write-Host 'PASS: Customer route patch applied locally.'
    }
    git status --short
    if ($LASTEXITCODE -ne 0) { throw 'Git status failed.' }
}
~~~

**Expected** The released checkout requires no source change. A newly applied patch makes three tracked changes: `SecurityConfig.java`, `CustomerSecurityTest.java` and `SecurityConfigTest.java`. Existing untracked files may also appear. **If it fails** stop before starting the patched app and inspect the checkout. **Database** No connection or changes.

## Step 10 — Run tests, build and smoke-test

**LOCAL TESTS AND BUILD.** Run the route and frontend checks before starting the app. Use Node.js 22.22.2 or later in the 22 line, 24.15.0 or later in the 24 line, or Node.js 26 or later.

~~~powershell
. {
    Set-Location (Join-Path $customerRoot 'backend')
    .\mvnw.cmd '-Dtest=CustomerSecurityTest,CustomerServiceTest,SecurityConfigTest,AuthenticationHttpTest,CategorySecurityTest,ProductRouteReadinessTest,SupplierSecurityTest' package
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests or package failed.' }

    Set-Location (Join-Path $customerRoot 'frontend')
    npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    node --test --test-concurrency=1 src/api/customers.test.js src/features/customers/CustomerPage.test.js src/app/AppLayout.test.js
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
    npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    Set-Location $customerRoot
    Write-Host 'PASS: Backend package and frontend checks.'
}
~~~

**Expected** Maven `BUILD SUCCESS`, no test failures and a Vite production build. **If it fails** stop before app startup. Inspect the failing check and rerun the tests after fixing it. **Database** These tests and builds make no database changes.

**LIVE SMOKE.** After the builds pass, open a separate clean PowerShell terminal for the backend. Enter the local MySQL credentials at the prompts. Flyway is disabled and Hibernate validates the schema at startup.

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
        .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=validate --server.address=127.0.0.1 --server.port=8080'
        if ($LASTEXITCODE -ne 0) { throw 'Backend startup or shutdown reported an error.' }
    } finally {
        Remove-Item Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD -ErrorAction SilentlyContinue
        if ($appSecret) { $appSecret.Dispose() }
    }
}
~~~

Open another clean PowerShell terminal for the frontend.

~~~powershell
Set-Location 'C:\Code\computer-store-management\frontend'
npm.cmd run dev
~~~

From the rollout terminal, check the direct backend and Vite proxy before signing in.

~~~powershell
. {
    Invoke-RestMethod 'http://127.0.0.1:8080/api/health'
    Invoke-RestMethod 'http://localhost:5173/api/health'
    curl.exe --silent --show-error --output NUL --write-out '%{http_code}\n' 'http://localhost:5173/api/v1/customers'
}
~~~

Open `http://localhost:5173/customers`. Use existing employee accounts and separate browser sessions or sign out between roles. Check statuses in the browser Network tab without copying request headers, credentials, cookies or tokens.

| Check | Expected |
|---|---|
| Health direct and through Vite | Both show `Computer Store API is running` |
| Anonymous Customer GET | `401` |
| Manager | List and detail `200`, with no create or edit action |
| Sales Staff | List and detail `200`, create `201` and update `200` through the UI using one approved local smoke fixture |
| Admin and Warehouse Staff | Customer GET `403` for each role |
| Customer contract | Responses and forms expose no contact fields. There is no deactivate or reactivate action |
| Category, Product and Supplier | Existing authorized list actions still work with an eligible account |

**If it fails** stop release and record the role, operation and status. If an account is unavailable, record that check as pending. For startup failures, check that MySQL is listening on 3306, the schema validates and ports 8080 and 5173 are free. Keep Flyway disabled. **Database** Startup and GET smoke checks read. Successful Sales Staff create and update deliberately write the approved test record. No additional migration runs. Stop both servers from their own terminals with `Ctrl+C` when finished.
