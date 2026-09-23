# Lookup

An Android app that detects **distracted walking** — walking while looking at
your phone — and shows a live **blue → amber → red** gradient bar over the
status bar as a warning.

It fuses three signals, entirely on-device:

- **Accelerometer** — step detection on the linear-acceleration deviation from
  gravity, plus screen tilt from the low-passed gravity vector.
- **Proximity sensor** — near (pocket / against the body) strongly suppresses
  "phone in view"; treated as *unknown* on devices without the sensor.
- **Screen state** — the screen must be on for a distraction to count.

A pure-Kotlin `DetectionEngine` combines them into a `0–100` confidence
`StateFlow<Int>` with rolling self-calibration on the user's walking cadence.
A foreground service hosts the engine and draws the overlay bar.

## Environment setup

Everything is installed under the user home directory — no root required. On a
fresh Debian 13 (x86_64) machine:

```bash
# 1. JDK 17 (Temurin) — the distro may only ship Java 21, and AGP wants 17.
curl -sL -o /tmp/jdk17.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p ~/tools
tar -xzf /tmp/jdk17.tar.gz -C ~/tools
mv ~/tools/jdk-17.0.* ~/tools/jdk-17

# 2. Android SDK — cmdline-tools first, then packages via sdkmanager.
mkdir -p ~/AndroidSdk/cmdline-tools
curl -sL -o /tmp/cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q /tmp/cmdline-tools.zip -d /tmp/sdk-tmp
mv /tmp/sdk-tmp/cmdline-tools ~/AndroidSdk/cmdline-tools/latest

export JAVA_HOME=~/tools/jdk-17
export ANDROID_HOME=~/AndroidSdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# 3. Gradle (only needed once, to generate the wrapper).
curl -sL -o /tmp/gradle.zip \
  "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip"
unzip -q /tmp/gradle.zip -d ~/tools
```

For every Gradle invocation used in this project:

```bash
export JAVA_HOME=~/tools/jdk-17
export ANDROID_HOME=~/AndroidSdk
```

`local.properties` (git-ignored) points the build at the SDK:

```
sdk.dir=/home/raghav/AndroidSdk
```

Installed versions, exactly as used for this pass:

| Component          | Version                                        | Location       |
|--------------------|------------------------------------------------|----------------|
| JDK                | Temurin 17.0.20.1                              | `~/tools/jdk-17` |
| Android platform   | 35 (rev 02)                                    | `~/AndroidSdk/platforms/android-35` |
| Build tools        | 35.0.0                                         | `~/AndroidSdk/build-tools/35.0.0` |
| Platform tools     | r37.0.1 (adb 1.0.41)                            | `~/AndroidSdk/platform-tools` |
| cmdline-tools      | 11076708                                       | `~/AndroidSdk/cmdline-tools/latest` |
| Gradle             | 8.10.2 (wrapper)                               | project `./gradlew` |

Toolchain: AGP 8.7.3 · Kotlin 2.0.21 (Compose compiler plugin) · Compose BOM
2024.12.01 · minSdk 26 · target/compileSdk 35.

## Build & test

```bash
./gradlew build   # assembles debug+release, runs lint and unit tests
./gradlew test    # unit tests only
```

Definition of done for this pass was: `./gradlew build` succeeds and
`./gradlew test` passes with meaningful `DetectionEngine` coverage.

## Architecture

```
app/src/main/java/dev/lookup/
├── detection/
│   ├── DetectionEngine.kt    # pure Kotlin fusion engine (no Android deps)
│   ├── Model.kt              # DetectionSettings, EngineSnapshot, SamplePoint
│   └── OverlayPalette.kt     # shared blue→amber→red ARGB gradient math
├── service/
│   ├── OverlayService.kt     # specialUse foreground service, sensor wiring
│   ├── OverlayBarView.kt     # WindowManager TYPE_APPLICATION_OVERLAY bar
│   └── DetectionBus.kt      # service→UI StateFlow bridge
├── data/
│   └── SettingsRepository.kt# persisted sensitivities + onboarding flag
├── ui/
│   ├── OnboardingScreen.kt  # 3 pages: pitch, how it works, permissions+rationale
│   ├── HomeScreen.kt        # Settings / Debug tabs
│   ├── SettingsScreen.kt    # start/stop, sensitivity sliders, live preview
│   ├── DebugScreen.kt       # live accel chart, step markers, confidence
│   └── ConfidenceBar.kt     # Compose twin of the overlay bar
├── LookupApp.kt              # Application: settings init
└── MainActivity.kt           # nav: onboarding → home
```

**Engine scoring.** `confidence = walkingScore × phoneInViewScore × 100`,
EMA-smoothed (τ = 0.45 s). `walkingScore` comes from step cadence (median step
interval over a rolling window, freshness-decaying ~1.4 s after the last
step). `phoneInViewScore` is 0 when the screen is off; otherwise a weighted mix
of tilt band (55°–120° from face-up, widened by look-sensitivity) and proximity
(near caps the score at 0.12).

**Rolling self-calibration.** The step threshold tracks 0.45× the rolling
median step amplitude (clamped 0.70–2.60 m/s², scaled ±35 % by motion
sensitivity), a baseline cadence is learned from sustained walking (relaxing
the cadence needed for a full walking score to 80 % of the user's own), and the
amplitude estimate decays back to the population default while idle so the
thresholds follow gait changes in both directions.

**Permissions flow.** Overlay (`SYSTEM_ALERT_WINDOW`) is requested with a
rationale screen in onboarding and re-checked on every resume; notifications
are requested on API 33+; the service runs as a `specialUse` foreground service
on API 34+. If overlay permission is revoked mid-run, the service notices
within ~2 s (or on the first failed window update), publishes
`DetectionBus.overlayPermissionLost`, and stops itself; the Settings screen
shows a re-grant banner. POST_NOTIFICATIONS denial never blocks detection —
the FGS keeps running with the notification suppressed.

## Unit tests

`app/src/test/java/dev/lookup/detection/` — `DetectionEngineTest` drives the
engine at 50 Hz with synthetic accelerometer frames (gravity + per-step
deviation bumps + gaussian noise + optional hand gestures and sway), covering:

- standing still, phone flat on a table
- standing still, phone raised in hand
- walking with the screen off (gait detected, **no** warning)
- walking with the phone in a pocket (proximity near)
- sitting with the phone raised (reading/typing gestures)
- distracted walking (the danger case — high confidence)
- confidence decay after stopping
- self-calibration across strong → idle → weak gait
- calibration reset
- screen off suppresses / screen on recovers
- sensor-gap robustness and out-of-order timestamp handling

## Assumptions

Decisions made without asking (per the ground rules):

1. **`specialUse` FGS type.** Android 14 requires a typed foreground service;
   continuous sensor fusion has no better-matching type (`health` requires
   body-sensor permissions we don't need), so the service declares
   `specialUse` with a descriptive `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
2. **"Phone in view" model.** Screen on AND (tilt within 55°–120° from
   face-up, widened by look-sensitivity) AND proximity not near. Proximity
   *near* caps the in-view score at 0.12 regardless of tilt; missing proximity
   hardware degrades gracefully to a 0.78 factor.
3. **Product scoring.** Confidence is the product of the two sub-scores
   (walking × in-view), so a warning needs *both* walking and a visible
   screen. Pocket-walking and sitting-with-phone stay quiet by design.
4. **Tilt semantics.** Tilt is the angle between the screen normal and
   vertical: 0° = flat face-up, 90° = upright facing the user, 180° =
   face-down. Gravity is estimated with a 0.18 s exponential low-pass.
5. **Self-calibration design.** Threshold ≈ 0.45× rolling median step
   amplitude (clamped, sensitivity-scaled), baseline cadence EMA from
   sustained walking, and idle decay of the amplitude estimate (τ = 12 s after
   4 s idle) so thresholds follow gait changes in both directions. `reset` is
   exposed in Settings.
6. **Settings storage.** `SharedPreferences` behind a `StateFlow` — two
   sliders and an onboarding flag don't justify DataStore.
7. **Overlay geometry.** Thin full-width bar, `Gravity.TOP` with
   `FLAG_LAYOUT_IN_SCREEN`, height 3 dp → 9 dp with confidence, gentle alpha
   pulse above confidence 70. Blue < 40 ≤ amber < 70 ≤ red.
8. **Package name** `dev.lookup`, application id `dev.lookup`.
9. **Sensor rates.** Accelerometer at `SENSOR_DELAY_GAME` (~50 Hz),
   proximity at `SENSOR_DELAY_NORMAL`. All engine timing derives from
   `SensorEvent.timestamp` (monotonic nanos), which keeps the engine pure and
   unit-testable.
10. **Debug tab icon** uses the material `Build` icon (the icon set bundled
    with Compose is intentionally small; no extended-icons dependency).

## Needs real-device verification

No Android device/emulator was available for this pass; everything below is
**unverified on hardware**:

- [ ] Overlay bar actually renders above the status bar on API 26–35
      (`TYPE_APPLICATION_OVERLAY` + `FLAG_LAYOUT_IN_SCREEN` behavior varies by
      OEM/inset policy).
- [ ] Overlay permission flow end-to-end: onboarding rationale → grant in
      system settings → "Start protection" enabled.
- [ ] API 34+ `specialUse` FGS starts without
      `ForegroundServiceTypeException` (Play-review constraints on specialUse
      are a *release* concern, not a build one).
- [ ] POST_NOTIFICATIONS runtime prompt appears on API 33+ and denial keeps
      detection running (notification suppressed).
- [ ] Mid-run overlay revocation stops the service and shows the Settings
      banner within ~2 s.
- [ ] Real accelerometer step detection across gaits (the 0.45× median
      threshold may need tuning for real sensor noise floors).
- [ ] Real proximity sensor polarity (near = value < maxRange/2) across
      vendors; devices without proximity fall back to the 0.78 unknown factor.
- [ ] Tilt band 55°–120° feels right in-hand; tune with the Debug tab's live
      tilt readout.
- [ ] Screen-state gating via `ACTION_SCREEN_OFF` broadcasts while the FGS
      runs (doze/aggressive- battery managers on some OEMs).
- [ ] Battery drain at ~50 Hz continuous sensing (target: negligible over a
      30-min walk).
- [ ] Notification tap reopens the app; "Stop" action stops the service.
