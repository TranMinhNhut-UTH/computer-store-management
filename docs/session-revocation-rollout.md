# P01 employee session revocation rollout

V12 adds `app_user.session_version` to the local MySQL 8.0 schema. It changes neither permissions nor existing migration files. Steps 1–7 are a one-time rollout. Step 8 is daily startup. Running this guide requires separate approval to write to `computer_store_dev`. During rollout, leave all CSM instances and other database writers stopped. Run each block in order in the **same Windows PowerShell window**. `cls` only clears the display. A new window loses the variables and functions.

## 1. Check the checkout and tools

Confirm the workspace contains the reviewed P01 release. **Database** No connection.

```powershell
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath 'C:\Code\computer-store-management'
git branch --show-current
git status --short
git log -1 --oneline
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath 'backend/src/main/resources/operations/session/v1/V12__add_employee_session_version.sql')) {
    throw 'Check out the reviewed P01 release in this workspace first.'
}
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$mysql = Join-Path $mysqlBin 'mysql.exe'
$dump = Join-Path $mysqlBin 'mysqldump.exe'
foreach ($tool in @($mysql, $dump)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Missing MySQL tool $tool" }
    $version = & $tool --no-defaults --version
    if ($LASTEXITCODE -ne 0 -or $version -notmatch '\b8\.0\.') { throw 'MySQL 8.0 tools are required.' }
    $version
}
$localUser = Read-Host 'MySQL username for computer_store_dev'
if ([string]::IsNullOrWhiteSpace($localUser)) { throw 'MySQL username required.' }
$localSchema = 'computer_store_dev'
$localUrl = 'jdbc:mysql://127.0.0.1:3306/computer_store_dev'
$mysqlArgs = @('--no-defaults','--protocol=TCP','--host=127.0.0.1','--port=3306',"--user=$localUser",'--password')
```

**Success** Git displays the reviewed revision and both tools report 8.0. **On failure** correct the workspace or the MySQL installation path and repeat this step.

## 2. Freeze writes

Stop all CSM backends and CLI jobs that can write to this schema. Stop the frontend dev server if it was serving an active operator session. Use `Ctrl+C` in each process's original terminal. Keep the write freeze until V12 and its read-back pass. **Database** No writes.

```powershell
$confirmation = Read-Host 'After stopping all writers, type WRITES-FROZEN'
if ($confirmation -cne 'WRITES-FROZEN') { throw 'Stop all writers before proceeding.' }
$writeFreezeConfirmed = $true
Write-Host 'PASS: operator confirmed the write freeze.'
```

**Success** The freeze confirmation is printed. **On failure** leave the rollout stopped and identify any remaining writer. The prompt records operator confirmation. It does not enforce a server lock.

## 3. Verify the target and Flyway history

The preflight checks the real target, the exact successful migration checksums, and the column before/after rollout. MySQL prompts for the password on each connection. **Database** SELECT only.

```powershell
$checksums = @(-1142478933,947719693,253774843,-1732158983,155246154,-1416705504,982398228,151122022,985142827,1376292394,792526816,-710087236)
function Assert-P01State([ValidateSet(11,12)][int]$expectedVersion) {
    $sql = @"
SELECT CONCAT('target|', DATABASE(), '|', @@port, '|', VERSION());
SELECT CONCAT('history|', COALESCE(version,'NULL'), '|', COALESCE(checksum,'NULL'), '|', success, '|', type)
  FROM flyway_schema_history ORDER BY installed_rank;
SELECT CONCAT('column|', COLUMN_TYPE, '|', IS_NULLABLE, '|', COALESCE(COLUMN_DEFAULT,'NULL'))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='session_version';
"@
    $rows = @(& $mysql @mysqlArgs "--database=$localSchema" --batch --raw --skip-column-names "--execute=$sql")
    if ($LASTEXITCODE -ne 0 -or $rows.Count -lt 1) { throw 'MySQL read-only checks failed.' }
    if ($rows[0] -cnotmatch '^target\|computer_store_dev\|3306\|8\.0\.\d+(?:[-+][^|]+)?$') {
        throw 'Unexpected MySQL target.'
    }
    $history = @(for ($version = 1; $version -le $expectedVersion; $version++) {
        $type = if ($version -in (2..5)) { 'JDBC' } else { 'SQL' }
        "history|$version|$($checksums[$version - 1])|1|$type"
    })
    $expected = @($rows[0]) + $history
    if ($expectedVersion -eq 12) { $expected += 'column|bigint|NO|0' }
    if (($rows -join "`n") -cne ($expected -join "`n")) {
        throw 'Unexpected Flyway history or version column. Stop and inspect before continuing.'
    }
    Write-Host "PASS: $($rows[0]); V1-V$expectedVersion and column state verified."
}
Assert-P01State 11
```

**Success** V1–V11 and the absence of the new column are confirmed. **On failure** stop. Inspect the target or any failed, missing, or unexpected history rows. An already applied V12 should be checked using `Assert-P01State 12` rather than rerunning the migration.

## 4. Create a fresh backup

Generate a unique pre-V12 backup outside the repository while the write freeze is in effect. The MySQL client prompts for the password. **Database** Backup reads only. The dump is written to a new local file.

```powershell
if ($writeFreezeConfirmed -ne $true) { throw 'Confirm the write freeze first.' }
$backupName = 'computer_store_dev_pre_v12_{0}_{1}' -f (Get-Date -Format 'yyyyMMdd_HHmmss'), ([guid]::NewGuid().ToString('N'))
$backupRoot = Join-Path $env:USERPROFILE 'CSM-Backups'
$null = New-Item -ItemType Directory -Path $backupRoot -Force
$backupDir = Join-Path $backupRoot $backupName
$null = New-Item -ItemType Directory -Path $backupDir
$backupFile = Join-Path $backupDir 'computer_store_dev.sql'
& $dump @mysqlArgs --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --routines --events --triggers --dump-date "--result-file=$backupFile" --databases $localSchema
if ($LASTEXITCODE -ne 0) { throw 'Backup failed. Keep the write freeze and take a fresh backup before rollout.' }
Write-Host "Backup created at $backupFile"
```

**Success** The path points outside the repo and mysqldump exits successfully. **On failure** stop and correct the client or backup privileges. Any partial dump cannot be used for the rollout.

## 5. Verify the backup

Check the database marker, schema selection, final completion marker, size, and SHA-256. Keep the hash file beside the dump. **Database** No connection.

```powershell
$backupInfo = Get-Item -LiteralPath $backupFile
if ($backupInfo.Length -le 0) { throw 'Backup is empty.' }
$databaseMarkers = @(Select-String -LiteralPath $backupFile -Pattern '^-- Current Database: ')
$useMarkers = @(Select-String -LiteralPath $backupFile -Pattern '^USE ')
$identifier = ([char]96).ToString() + $localSchema + ([char]96).ToString()
if ($databaseMarkers.Count -ne 1 -or $databaseMarkers[0].Line -cne "-- Current Database: $identifier" -or
        $useMarkers.Count -ne 1 -or $useMarkers[0].Line -cne "USE $identifier;" -or
        -not ((Get-Content -LiteralPath $backupFile -Tail 6) -match '^-- Dump completed on ')) {
    throw 'Expected target or completion marker was missing.'
}
$backupHash = (Get-FileHash -LiteralPath $backupFile -Algorithm SHA256).Hash
Set-Content -LiteralPath (Join-Path $backupDir 'SHA256.txt') -Value $backupHash -Encoding ascii
Write-Host "PASS: $($backupInfo.Length) bytes at $backupFile"
Write-Host "SHA-256 $backupHash"
```

**Success** The verified path, byte count and SHA-256 are printed. **On failure** take and verify a new backup before proceeding. These checks do not prove restoration. Rehearse recovery on a disposable MySQL instance. The dump includes database contents and selects the named schema, so keep it private and never load it on a server holding the development or production schema during a rehearsal.

## 6. Build the exact runner

Compile with Java 21 and resolve the runtime classpath. This is a local build step and does not start Spring Boot. **Database** No connection.

```powershell
Push-Location backend
try {
    .\mvnw.cmd -version
    if ($LASTEXITCODE -ne 0) { throw 'Check Java 21 and the Maven wrapper.' }
    .\mvnw.cmd -DskipTests package dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/p01-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Runner build failed.' }
    $p01ClassPath = @((Join-Path $PWD 'target/classes'), (Get-Content -LiteralPath 'target/p01-classpath.txt' -Raw).Trim()) -join ';'
    if (-not (Test-Path -LiteralPath 'target/classes/com/computerstore/computer_store_backend/operations/session/v1/SessionVersionMigrationRunner.class')) {
        throw 'Compiled runner not found.'
    }
} finally {
    Pop-Location
}
```

**Success** Maven reports `BUILD SUCCESS` and the compiled runner exists. **On failure** correct Java/Maven or the checkout and rebuild. No migration runs as part of the build.

## 7. Apply V12 once and verify

Recheck the current verified backup and V1–V11. Run the exact command only with separate approval. The runner verifies the loopback target and Flyway history, applies only V12, then checks history, column metadata and that all employees were initialized to version zero. **Database** V12 DDL and one Flyway history entry. The following verification is read-only.

```powershell
if ($writeFreezeConfirmed -ne $true -or [string]::IsNullOrWhiteSpace($backupHash) -or
        (Get-FileHash -LiteralPath $backupFile -Algorithm SHA256).Hash -cne $backupHash -or
        [string]::IsNullOrWhiteSpace($p01ClassPath)) { throw 'Complete and verify steps 1-6 first.' }
Assert-P01State 11
if ((Read-Host 'With execution approved, type APPLY-V12') -cne 'APPLY-V12') { throw 'V12 was not started.' }
$secure = Read-Host 'MySQL password for V12' -AsSecureString
$secretPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$previousP01Env = @{ DB_URL = $env:DB_URL; DB_USERNAME = $env:DB_USERNAME; DB_PASSWORD = $env:DB_PASSWORD }
try {
    $env:DB_URL = $localUrl
    $env:DB_USERNAME = $localUser
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPtr)
    & java -cp $p01ClassPath com.computerstore.computer_store_backend.operations.session.v1.SessionVersionMigrationRunner --apply-p01-session-version --confirm-target=computer_store_dev
    if ($LASTEXITCODE -ne 0) { throw 'P01 runner stopped. Keep writes frozen and inspect live state before any further action.' }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPtr)
    $secure.Dispose()
    foreach ($key in $previousP01Env.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousP01Env[$key], 'Process')
    }
    Remove-Variable previousP01Env
}
Assert-P01State 12
```

**Success** The runner prints `PASS: V12 applied and verified (checksum -710087236).` The independent read-back shows successful V1–V12 and `session_version BIGINT NOT NULL DEFAULT 0`. **On failure** keep writes frozen. Inspect `flyway_schema_history` and `information_schema.COLUMNS`. MySQL DDL can commit even when a later check fails. Do not rerun, repair, or remove the column automatically. Use the verified backup and a separately approved recovery plan if restoration is necessary.

## 8. Start the application and smoke-test

Start after V12 read-back succeeds. Normal startup keeps Flyway disabled and Hibernate schema validation enabled. The local Staff gate needs the existing ignored `backend/config/staff-local.properties` file for Staff actions. **Database** Application startup validates schema. Sign-in and protected reads are used here. Run lock/unlock and role mutation smoke only against disposable test data with separate authorization.

Backend PowerShell window:

```powershell
Set-Location -LiteralPath 'C:\Code\computer-store-management\backend'
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/computer_store_dev'
$env:DB_USERNAME = Read-Host 'MySQL username'
$secret = Read-Host 'MySQL password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=8080 --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=validate'
    if ($LASTEXITCODE -ne 0) { throw 'Backend stopped with an error. Verify the target and V12 before retrying.' }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $secret.Dispose()
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}
```

Frontend PowerShell window:

```powershell
Set-Location -LiteralPath 'C:\Code\computer-store-management\frontend'
npm.cmd ci
if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency install failed.' }
npm.cmd run dev
```

**Success** Open `http://127.0.0.1:8080/api/health` for HTTP 200 and `http://localhost:5173` to sign in freshly. A valid existing employee can open their permitted routes. Use `Ctrl+C` in each terminal to stop. **On failure** stop the affected process, recheck V12 and database target, and keep validation enabled. Do not disable schema validation to bypass a missed deployment. On a disposable fixture, verify lock, unlock, and role change each reject previously authenticated sessions and that fresh sign-in restores only current permissions.
