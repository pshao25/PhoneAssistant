# PhoneAssist Guidance API

Run the deterministic provider for local Android integration:

```powershell
cd backend
uv sync
uv run uvicorn app.main:app --host 0.0.0.0 --port 8001
```

The Android emulator reaches this service at `http://10.0.2.2:8001`.

To use Gemini, create a private `.env` file from the checked-in example:

```powershell
Copy-Item .env.example .env
# Open .env locally and replace only the GEMINI_API_KEY value.
uv run uvicorn app.main:app --host 0.0.0.0 --port 8001
```

The backend loads `.env` automatically. Shell environment variables take precedence over file values.
The `.env` file is ignored by Git, so the API key remains on the development machine and is never
included in the Android app.