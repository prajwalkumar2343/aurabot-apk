# Aura Launcher

Aura is being migrated into a native Android assistant launcher. The active mobile client now lives in `android/` and the existing Python API remains in `backend/`.

## Android

Build the native launcher:

```bash
cd android
./gradlew assembleDebug
```

Configure the backend URL with a Gradle property:

```bash
./gradlew assembleDebug -PauraBackendUrl=http://10.0.2.2:8001
```

The Android app is a Home launcher. After installing the debug APK, choose Aura from Android's default Home app settings.

## Backend

The native app keeps the existing API contract:

Run the API from the modular FastAPI entrypoint:

```bash
cd backend
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET/POST /api/memories`
- `GET/POST/PATCH /api/todos`
- `POST /api/assistant/chat`
- `POST /api/transcribe`

Guest mode works without login for launcher basics. Cloud assistant, memory, and tasks require a bearer token.
