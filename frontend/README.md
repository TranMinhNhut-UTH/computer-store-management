# Computer Store Frontend

React + Vite UI for your Spring Boot controllers.

## Run
1. Install Node.js LTS.
2. Open terminal in this folder.
3. Run `npm install`
4. Run `npm run dev`
5. Open the Vite URL, usually `http://localhost:5173`.

Default backend URL: `http://localhost:5080`, editable in the top-right field.

Includes auth/register/OTP/resend/login/refresh/logout/forgot/reset, profile, addresses, staff and audit logs. Staff and audit APIs require a manager JWT. OTP is delivered by the backend's configured email/SMS service, not displayed in the frontend.

If paged DTO field names differ, adjust the `loadStaff` / `loadLogs` collection parsing in `src/App.jsx`. Allow `http://localhost:5173` in backend CORS.
