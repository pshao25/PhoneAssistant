# PhoneAssistant

PhoneAssistant is an Android accessibility assistant that guides a user through visible phone controls one step at a time. It highlights the suggested control but never clicks or navigates automatically; the user performs each action and confirms before requesting the next step.

## Project structure

- `app/`: Android app built with Kotlin, Jetpack Compose, and `AccessibilityService`
- `backend/`: FastAPI guidance API with local and Gemini providers
- `gradle/`: Gradle wrapper and version catalog

## Prerequisites

- Android Studio with Android SDK 37
- JDK 25 for the Gradle daemon toolchain
- Python 3.12 or later
- [uv](https://docs.astral.sh/uv/)

## Run the backend

```powershell
cd backend
Copy-Item .env.example .env
# Set GEMINI_API_KEY in .env when using the Gemini provider.
uv sync
uv run uvicorn app.main:app --host 0.0.0.0 --port 8001
```

The Android emulator connects to the backend at `http://10.0.2.2:8001`. See [backend/README.md](backend/README.md) for provider details.

## Build and test

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug

cd backend
uv run pytest
```

Enable **PhoneAssist screen reader** in Android Accessibility settings before starting guidance.

## Safety model

PhoneAssistant only reads the current accessibility hierarchy and displays overlays. It does not perform accessibility click actions, enter text into other apps, or automatically navigate the device.
