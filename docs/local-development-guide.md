# Run CSM locally on Windows

Use two PowerShell terminals, one for Spring Boot and one for React/Vite.

## Prerequisites

- JDK 21. Check `java -version` and `javac -version`.
- The repository's `backend\mvnw.cmd` installs Maven 3.10.0 on first use. Check it with `.\mvnw.cmd -v` from `backend`.
- Node.js `^22.22.2 || ^24.15.0 || >=26.0.0` and npm. Check `node -v` and `npm.cmd -v`.
- MySQL 8.0 listening on `127.0.0.1:3306`, an existing migrated `computer_store_dev` schema for the branch, and local MySQL credentials. Browser sign-in uses an existing CSM employee account.

## Start the backend

Use a dedicated terminal. The password prompt hides input and passes the password through the process environment for the duration of the run.

```powershell
Set-Location 'C:\Code\computer-store-management\backend'

try {
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/computer_store_dev'
    $env:DB_USERNAME = Read-Host 'Local MySQL username'
    $csmDbSecret = Read-Host 'Local MySQL password' -AsSecureString
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or $csmDbSecret.Length -eq 0) {
        throw 'Enter your local MySQL username and password.'
    }
    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $csmDbSecret).Password

    $env:SPRING_FLYWAY_ENABLED = 'false'
    $env:SPRING_JPA_HIBERNATE_DDLAUTO = 'validate'
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SERVER_PORT = '8080'
    .\mvnw.cmd spring-boot:run
} finally {
    Remove-Item Env:DB_URL, Env:DB_USERNAME, Env:DB_PASSWORD, Env:SPRING_FLYWAY_ENABLED, `
        Env:SPRING_JPA_HIBERNATE_DDLAUTO, Env:SERVER_ADDRESS, Env:SERVER_PORT -ErrorAction SilentlyContinue
    if ($csmDbSecret) { $csmDbSecret.Dispose() }
}
```

Keep passwords out of source files, shell arguments, and shared logs.

## Start the frontend

```powershell
Set-Location 'C:\Code\computer-store-management\frontend'
npm.cmd ci
if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
npm.cmd run dev
```

Run `npm.cmd ci` on first setup and after changes to `package-lock.json`. On a routine restart, run `npm.cmd run dev` alone.

## Verify, stop, and restart

- Open <http://localhost:5173> and sign in with an existing employee account.
- From another PowerShell terminal, run both checks below. Each should return `Computer Store API is running`. The second also verifies the Vite `/api` proxy.

  ```powershell
  Invoke-RestMethod 'http://127.0.0.1:8080/api/health'
  Invoke-RestMethod 'http://localhost:5173/api/health'
  ```

- Press `Ctrl+C` in each server's terminal to stop it. If Windows asks to terminate the batch job, confirm. Restart the backend by rerunning its block and entering the credentials again. Restart Vite with `npm.cmd run dev` in `frontend`.

## Common startup errors

| Symptom | What to check |
|---|---|
| Maven reports `invalid target release` or Java is missing | Use JDK 21 and check that `java -version` and `.\mvnw.cmd -v` both report Java 21. |
| MySQL connection refused, access denied, or unknown database | Check the local MySQL service and port 3306, re-enter the MySQL credentials, and confirm `computer_store_dev` exists. |
| Hibernate reports missing tables or schema validation fails | Check that the schema matches the branch. Keep `ddl-auto=validate` and use the rollout documentation to investigate. |
| Port 8080 or 5173 is already in use | Run `Get-NetTCPConnection -State Listen -LocalPort 8080,5173` and stop the old CSM process from its own terminal. Vite uses a strict port and proxies to 8080. |
| Vite loads but login shows a connection error | Check both health URLs. If direct health works, use `http://localhost:5173` and confirm Vite is running from this repository's `frontend` directory. |

## Manual provisioning and migrations

Normal startup uses an already migrated schema with Flyway disabled and Hibernate validation enabled. Do not rerun the controlled V2–V7 migrations during startup or troubleshooting. Use the relevant runbook to investigate a history mismatch.

- Account provisioning uses separate opt-in CLIs. See [first ADMIN bootstrap](local-admin-bootstrap.md) or [local MANAGER provisioning](local-manager-provisioning.md) when explicitly needed.
- Migration operations require a separately approved execution, a verified backup, and history checks. See the [Category and Product permission rollout](authorization-runtime-permission-rollout.md) and [Supplier rollout](supplier-runtime-activation.md).
