# Aura Home

Aura is a widget-canvas Android Home launcher. Its primary interface is the 3×3 eyes surface: users speak or type there, durable agent runs continue in the background, and typed work surfaces appear on Home as reports, meeting tools, progress, reminders, and approval cards. The conventional app entry remains a secondary route to conversation history, permissions, models, tasks, memories, automations, and settings.

Fresh installs offer two execution modes. **Continue with Google** verifies a
Google ID token against the managed backend, creates an isolated Aura account,
and uses Aura-owned MongoDB and Gemini credentials. **Continue locally** never
calls Aura's backend: the user supplies a TLS MongoDB seed-list URI, database
name, model provider, model, and API key. Database and provider credentials use
separate Android Keystore keys. Local setup performs a real database ping before
it advances and does not replace the current session until every local setting
has validated and persisted.

## Android

Build the native launcher:

```bash
cd android
./gradlew assembleSideloadDebug
```

The default `sideload` variant omits direct SMS; direct-message automations open
a user-reviewable SMS draft instead. Cross-app UI automation is not available.
The explicitly selected `unrestricted` variant
(`assembleUnrestrictedDebug`) retains direct SMS for controlled development
devices.

Configure the backend URL with a Gradle property:

```bash
./gradlew assembleSideloadDebug -PauraBackendUrl=http://10.0.2.2:8001
```

Managed onboarding uses Android Credential Manager. Supply the OAuth web client
ID that represents the Aura backend when building the app:

```bash
./gradlew assembleSideloadDebug \
  -PauraBackendUrl=https://api.example.com \
  -PauraGoogleWebClientId=1234567890-example.apps.googleusercontent.com
```

The package name and signing-certificate SHA-1 for `com.aura.app` must be
registered in the same Google Auth Platform project. The web client ID is
public configuration; provider API keys must never be passed as Gradle
properties or embedded in the APK.

After installing the debug APK, choose Aura from Android's default Home app settings. Aura can also host widgets from installed apps. Opening the Aura app icon routes to the secondary settings/history experience rather than replacing the Home canvas.

Aura-generated surfaces are validated before persistence. Compact and expanded surfaces render as native cards. Full-screen report surfaces may contain static, self-contained HTML; their WebView disables JavaScript, storage, file/content access, network loads, and external navigation.

## Backend

The native app keeps the existing API contract:

Run the API from the modular FastAPI entrypoint:

```bash
cd backend
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

For production, run the API and agent worker as separate processes with the
same credential-encryption key:

```bash
export AGENT_CREDENTIAL_KEY="a-long-random-secret-from-your-secret-manager"
export GOOGLE_WEB_CLIENT_ID="1234567890-example.apps.googleusercontent.com"
export MANAGED_GEMINI_API_KEY="your-server-owned-gemini-key"
export AGENT_EMBEDDED_WORKER=false
uvicorn app.main:app --host 0.0.0.0 --port 8001
python -m app.worker
```

`AGENT_EMBEDDED_WORKER` defaults to `true` only in local development. A
single-service production deployment also sets `AGENT_CONTINUOUS_CPU=true` and
must use one warm Cloud Run instance with CPU throttling disabled. The API
atomically admits encrypted, idempotent work into MongoDB; workers claim it with
expiring leases and fencing tokens.
Provider keys are removed when a run reaches a terminal state. Keep
`AGENT_CREDENTIAL_KEY` in a secret manager and rotate it only after draining the
queue.

- `POST /api/auth/login`
- `POST /api/auth/google/challenge` — issue a signed, five-minute sign-in nonce
- `POST /api/auth/google` — verify a Google ID token and provision an Aura-managed account
- `GET /api/auth/me`
- `GET/POST /api/memories`
- `GET/POST/PATCH /api/todos`
- `POST /api/assistant/chat`
- `POST /api/assistant/runs` — admit a durable assistant run and return its id;
  clients should send a stable `Idempotency-Key` header for retries
- `GET /api/assistant/runs/{run_id}` — poll root/subagent phase and results
- `POST /api/assistant/runs/{run_id}/cancel` — stop exposing an active run and prevent late results from overwriting it
- `POST /api/transcribe`

The launcher uses the durable run API. Aura may delegate up to three concurrent,
depth-one reasoning tasks to `researcher`, `planner`, or `reviewer` subagents.
Children have no device-action tools; consequential phone actions still pass
through the existing typed action and confirmation policies.

Guest mode works without login for launcher basics. Cloud assistant, memory, and tasks require a bearer token.

Direct MongoDB local mode is limited to standard `mongodb://` seed-list URIs
and MongoDB Server 8.0 or older. MongoDB removed Android support after the 3.x
Java driver, and that final Android-capable line cannot connect to MongoDB 8.1+.
Use a dedicated database user limited to the selected database and required
CRUD operations.

Google challenges are stateless until a verified login consumes them. Consumed
nonce fingerprints are retained briefly under a unique TTL index, preventing
replay without allowing anonymous challenge requests to amplify database writes.

For the requested single-service deployment, see
[`backend/CLOUD_RUN_DEPLOYMENT.md`](backend/CLOUD_RUN_DEPLOYMENT.md).
