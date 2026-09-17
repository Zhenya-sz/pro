# Backend security handoff

The supplied PHP files are not present in `Zhenya-sz/pro` (the repository currently contains the Android client only), so they were not mixed into the Android branch. The following backend changes are required before production deployment:

- Move `DB_PASS` and the JWT signing secret to `.env`; rotate both values because credentials were exposed in source.
- Disable `display_errors` and never return exception messages, SQL, traces, passwords, JWTs, or raw request bodies in production responses/logs.
- Remove `RAW INPUT`, `POST`, `FILES`, and registration password logging from `index.php` and `AuthController.php`.
- Replace wildcard CORS with the exact application origin; do not combine `Access-Control-Allow-Origin: *` with credentials.
- Use `https://api.fitnesslemon.ru` consistently for `APP_URL`, media URLs, Android `BASE_URL`, and redirects.
- Canonicalize and validate every requested filename before serving media; reject `..`, path separators, symlinks, and files outside the upload root.
- Replace shell-concatenated FFmpeg commands with `proc_open` argument handling or strict `escapeshellarg` plus canonical path checks.
- Validate uploads using `finfo`, size limits, generated extensions, and non-executable upload directories; do not trust the client MIME or filename extension.
- Protect chat endpoints with membership checks: `getParticipants`, `markAsRead`, deletion, encrypted-file access, and story-chat operations must all verify the current user belongs to the chat.
- Fix route ordering for `DELETE /messages/{chat}/messages/{message}/everyone` and parse `/messages/get-file/{token}` from the correct path segment.
- Use atomic booking updates/transactions with row locking and restore the workout balance on cancellation.
- Migrate FCM from the legacy server-key endpoint to FCM HTTP v1 and store service-account credentials outside the repository.
- Add security headers, request size limits, CSRF protection where cookie auth is used, and rate limits for login/register/upload endpoints.
