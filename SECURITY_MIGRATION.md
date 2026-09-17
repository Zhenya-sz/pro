# Architecture follow-up

The Android branch now contains the security and cache primitives needed for the next migration step.

## Safe rollout

1. Deploy the API under `https://api.fitnesslemon.ru/` with a certificate valid for that hostname.
2. Keep the legacy DataStore token format readable during one app release.
3. On the first successful read, encrypt the legacy access/refresh token with `EncryptedTokenStore` and write it back.
4. Remove legacy values only after the encrypted values have been verified.
5. Run unit tests and an offline smoke test before merging the branch.

The PHP snippets supplied in chat are a separate backend project and are intentionally not copied into this Android repository. Apply `BACKEND_SECURITY_HANDOFF.md` to that server repository, especially secret rotation and removal of raw request/token logging.
