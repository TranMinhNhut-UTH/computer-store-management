# Local MANAGER account for Category E2E

Use an existing authorized MANAGER account if one is already available. This tool is for a local operator who needs to create a separate employee account on `computer_store_dev`. It creates an active account and one assignment to the existing `MANAGER` role. Every permission already assigned to that role applies to the new account.

The Maven profile compiles the CLI to `target/test-classes`. The application JAR excludes it, even when built with the profile. Run it from an interactive development terminal only. The CLI starts a standalone JPA factory with schema validation and uses the existing ADMIN bootstrap field validation, but it has a separate entrypoint and transaction. It does not start Spring Boot, register an HTTP route, or invoke Flyway.

## Run from PowerShell

Use Java 21. Check that Category V2/V3 have succeeded and that the Category grants are correct before provisioning for E2E. Run from the repository root:

```powershell
Set-Location 'C:\Code\computer-store-management\backend'
.\mvnw.cmd -Plocal-manager-provisioning test-compile dependency:build-classpath `
    '-DincludeScope=runtime' '-Dmdep.outputFile=target/local-manager-runtime-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Local CLI build failed.' }

$managerClasspath = 'target/test-classes;target/classes;' +
    (Get-Content -LiteralPath 'target/local-manager-runtime-classpath.txt' -Raw).Trim()

try {
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/computer_store_dev'
    $env:DB_USERNAME = Read-Host 'Local MySQL username'
    $managerDbSecret = Read-Host 'Local MySQL password' -AsSecureString
    try {
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $managerDbSecret).Password
    }
    finally {
        $managerDbSecret.Dispose()
        Remove-Variable managerDbSecret
    }

    java -cp $managerClasspath com.computerstore.computer_store_backend.identity.security.LocalManagerProvisioning `
        --provision-manager --ack-local-development-only
    if ($LASTEXITCODE -ne 0) { throw 'Provisioning stopped. Check local account state before retrying.' }
}
finally {
    Remove-Item Env:DB_PASSWORD, Env:DB_USERNAME, Env:DB_URL -ErrorAction SilentlyContinue
}
```

Use a dedicated terminal because the example clears its three database environment variables on exit. Database credentials are passed through the environment, and the employee password and confirmation are read without echo by Java. Keep credentials out of command arguments, files, and logs. Use a local database operator authorized to read identity metadata and insert the account and role assignment. Provide a unique username, employee full name, and a new password distinct from the MySQL password. The username follows login normalization. The password must be nonblank and fit BCrypt's 72-byte UTF-8 limit.

Both flags are required. The JDBC URL must match the literal in the example, including the port and absence of query parameters. Inside the transaction the tool checks the connected schema and the InnoDB engines of both write tables. It rejects missing MANAGER roles and duplicate usernames. A database constraint handles concurrent username collisions. Account and role assignment commit together. If the CLI reports failure, inspect the account before retrying.

## Verify the account

Using a trusted MySQL session on `computer_store_dev`, run this read-only query with the normalized username you chose. Keep the output limited to account state and role codes.

```sql
SELECT u.username, u.is_active, r.code AS role_code
FROM computer_store_dev.app_user AS u
LEFT JOIN computer_store_dev.user_role AS ur ON ur.user_id = u.id
LEFT JOIN computer_store_dev.app_role AS r ON r.id = ur.role_id
WHERE u.username = 'replace-with-normalized-local-username';
```

Expect one row with `is_active = 1` and `role_code = MANAGER`. When a separate E2E run is authorized, sign in with that account and verify Category list/read/create/update/deactivate using test fixtures. Use login and mutation CSRF tokens from the same browser session and refresh the token after login.

IAM-005 remains TODO. This operator tool does not provide user listing, editing, locking, password reset, or general role assignment. The ADMIN bootstrap remains dedicated to the first ADMIN. CAT-001 remains IN_PROGRESS until Category E2E is verified.
