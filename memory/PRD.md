# Aura — Always-Listening Mobile Assistant

## Vision
An Android-first mobile assistant (iOS-compatible) that feels alive: it listens,
thinks, and speaks back. It stays quietly present in the background, stores a
personal memory of what matters, and keeps a clean to-do list — all wrapped in
a strict black & white, minimalist, brutalist-inspired UI.

## Stack
- **Frontend:** Expo React Native (SDK 54) + expo-router, expo-av, expo-speech,
  expo-notifications, expo-keep-awake, AsyncStorage, react-native-reanimated
- **Backend:** FastAPI + Motor (MongoDB) + PyJWT + bcrypt + emergentintegrations
- **LLM:** Gemini (`gemini-3-flash-preview`) via Emergent Universal Key for
  transcription (audio input → text) and assistant chat
- **TTS:** On-device `expo-speech` (native Android/iOS voice). Gemini 3 Flash
  returns text only; native TTS is used for audio output.
- **Supabase gateway:** MOCKED – `POST /api/gateway/supabase` returns
  `{"mocked": true}` ready to swap for a real Supabase client later.

## Screens
1. **Splash** (`/`) – routes based on auth + onboarding state
2. **Login / Register** (`/login`, `/register`) – JWT email/password
3. **Permissions** (`/permissions`) – mic / notifications / background / battery
4. **(tabs)** – bottom tab navigation
   - **Assistant** – big record button, live transcript, chat history, always-on
     toggle, pulsing animation reflecting state (idle / listening / thinking /
     speaking)
   - **Memory** – cardless list, 1px borders, add via bottom-sheet modal
   - **To-Do** – checkboxes, inline add, long-press to delete
   - **Settings** – account, live permission statuses, re-review, logout

## Backend endpoints (all prefixed `/api`)
- `POST /auth/register` / `POST /auth/login` / `GET /auth/me` /
  `POST /auth/logout` / `POST /auth/refresh`
- `GET/POST /memories`, `DELETE /memories/{id}`
- `GET/POST /todos`, `PATCH/DELETE /todos/{id}`
- `POST /assistant/chat` – sends user text to Gemini, returns reply
- `POST /transcribe` – accepts `{audio_base64, mime_type}`, returns text (Gemini
  multimodal)
- `POST /gateway/supabase` – MOCKED

## Android background & permissions
`app.json` declares `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `WAKE_LOCK`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`,
`RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. The runtime
onboarding screen requests microphone + notifications directly and links to OS
settings for battery optimization. `expo-keep-awake` is used when the user
turns on the "Always On" toggle so the screen can keep listening actively.

> True 24/7 microphone capture on Android requires a native foreground service
> which is only possible in an EAS production build (not Expo Go). The UI,
> permissions, and backend pipeline are all wired; the foreground service only
> needs to be added during the EAS build step.

## Auth
See `/app/memory/test_credentials.md`. Admin is auto-seeded on startup.

## Design system
`/app/design_guidelines.json` — Swiss & High-Contrast, Cabinet Grotesk / IBM
Plex Sans, pure #000/#FFF, 1px white/10 borders, stark circular record button,
no color. Fully respected by the implementation.
