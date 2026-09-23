# Agent Instructions for "lookup"

## What this is
An Android app that detects distracted walking (phone in view + walking
motion) using accelerometer + proximity sensor fusion, and shows a live
gradient (blue→amber→red) overlay bar above the status bar as a warning.

## Environment setup
No Android tooling (JDK, Android SDK, cmdline-tools) is installed yet.
Check for and install everything needed (JDK 17, Android SDK
platform-tools, platform 35, build-tools 35.0.0) before starting the
build. Set ANDROID_HOME and PATH as needed. Document exactly what you
installed and how in README.md under "Environment setup" so it's
reproducible later.

## Ground rules
- Kotlin, Jetpack Compose, minSdk 26, targetSdk 35.
- Don't stop to ask clarifying questions. Make a reasonable choice and
  log it under "Assumptions" in README.md.
- Commit after every working milestone (buildable state), with a clear
  message. Don't wait until everything is done to commit.
- Write unit tests for all pure logic (sensor fusion math, confidence
  scoring) using synthetic data — this is the only testing possible
  right now since no Android device is available for real-device testing.
- Do NOT attempt to verify overlay rendering, permission flow behavior,
  or real sensor behavior — these require a physical device and are out
  of scope for this pass. Flag them explicitly in README.md under
  "Needs real-device verification."
- Keep UI minimal, fluid, Material 3. No placeholder/boilerplate text.

## Priority order
1. Environment setup (JDK, Android SDK) — verify it works.
2. Project scaffold (Gradle, manifest, package structure) — must build.
3. DetectionEngine (sensor fusion logic) + full unit test coverage.
4. ForegroundService + overlay window (TYPE_APPLICATION_OVERLAY).
5. Permissions flow (overlay permission, foreground service type,
   POST_NOTIFICATIONS for API 33+).
6. UI: onboarding → settings (with live overlay preview) → debug screen
   (live sensor chart + confidence score).
7. README.md: setup instructions, Assumptions, Needs real-device
   verification checklist.

## Definition of done for this pass
- `./gradlew build` succeeds.
- `./gradlew test` passes with meaningful coverage on DetectionEngine.
- README fully documents environment setup, Assumptions, and what needs
  a real device to validate.
