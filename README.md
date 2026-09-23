# lookup

An Android app that detects **distracted walking** — walking while looking at
your phone — and flashes a vivid **blue → amber → red** gradient bar over the
status bar as a peripheral-vision cue to look up.

## How it works (corrected architecture)

- **Always-on background service.** Once the user enables protection, a
  `specialUse` foreground service runs the detection continuously — it starts
  on boot (via `BootReceiver`), survives being backgrounded, and persists a
  single `systemEnabled` flag so an explicit "off" (dashboard toggle or
  notification Stop) is the only thing that stops it. The user never has to
  remember to turn it on for a walk.
- **Conditionally-visible overlay.** The warning bar is *not* persistent. A
  pure `OverlayGate` (show threshold 40, hide threshold 30, 250 ms rise / 400 ms
  fall debounce) attaches the overlay view to the `WindowManager` only for the
  duration of a detected distracted-walking moment, with a ~220 ms fade/scale
  transition. Outside those moments there is zero visual presence.
- **Dashboard UI.** The app itself is a thin dashboard over the service:
  onboarding (one-time), dashboard (master toggle + live service/bar status +
  sensitivity settings), and debug (live sensor chart + confidence). The app
  does not need to be open for the feature to work.

It fuses three signals, entirely on-device:

- **Accelerometer** — step detection on the linear-acceleration deviation from
  gravity, plus screen tilt from the low-passed gravity vector.
- **Proximity sensor** — near (pocket / against the body) strongly suppresses
  "phone in view"; treated as *unknown* on devices without the sensor.
- **Screen state** — the screen must be on for a distraction to count.

A pure-Kotlin `DetectionEngine` combines them into a `0–100` confidence
`StateFlow<Int>` with rolling self-calibration on the user's walking cadence.

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

## CI

`.github/workflows/build.yml` runs on every push to `main`: it checks out the
repo, sets up Temurin JDK 17 and the Android SDK, runs `./gradlew build` and
`./gradlew test`, and uploads the debug APK as a build artifact.

To download a built APK: open the repository on GitHub → **Actions** tab →
click the latest `build` workflow run → scroll to **Artifacts** → download
`lookup-debug-apk` (contains `app-debug.apk`, installable via `adb install`).

## Architecture

```
app/src/main/java/dev/lookup/
├── detection/
│   ├── DetectionEngine.kt    # pure Kotlin fusion engine (no Android deps)
│   ├── Model.kt              # DetectionSettings, EngineSnapshot, SamplePoint
│   ├── OverlayGate.kt        # pure visibility gate: threshold+hysteresis+debounce
│   └── OverlayPalette.kt     # shared blue→amber→red ARGB gradient math
├── service/
│   ├── OverlayService.kt     # specialUse foreground service, sensor wiring
│   ├── OverlayBarView.kt     # WindowManager TYPE_APPLICATION_OVERLAY bar
│   ├── BootReceiver.kt       # BOOT_COMPLETED / MY_PACKAGE_REPLACED restart
│   └── DetectionBus.kt       # service→UI StateFlow bridge
├── data/
│   └── SettingsRepository.kt # sensitivities, onboarding flag, systemEnabled
├── ui/
│   ├── OnboardingScreen.kt   # 3 pages: pitch, how it works, permissions+rationale
│   ├── HomeScreen.kt         # Dashboard / Debug tabs
│   ├── DashboardScreen.kt    # master toggle, live status, sliders, preview
│   ├── DebugScreen.kt        # live accel chart, step markers, confidence
│   ├── ConfidenceBar.kt      # Compose twin of the overlay bar
│   └── BatteryStatus.kt      # shared battery-exemption check helper
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

**Overlay visibility.** `OverlayGate` turns the bar on after confidence holds
≥ 40 for 250 ms, and off after it holds ≤ 30 for 400 ms; the 30–40 band holds
the current state so boundary noise can't flicker it. The bar is attached to
the `WindowManager` only while visible and detaches afterwards — a fixed 15 dp
transparent window hosts a drawn bar of 5 dp → 12 dp (no relayout), with a
220 ms fade/scale-in, 180 ms fade-out, full opacity throughout, and a 12 %
height pulse above confidence 70. Gradient stops: `#2563EB→#3B82F6`
(blue) → `#F59E0B→#FBBF24` (amber) → `#DC2626→#EF4444` (red).

**Permissions flow.** Overlay (`SYSTEM_ALERT_WINDOW`) is requested with a
rationale screen in onboarding and re-checked on every resume; notifications
are requested on API 33+; background exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
is requested in onboarding with the tradeoff stated plainly (skippable, strongly
recommended); the service runs as a `specialUse` foreground service on API 34+
and restarts on boot when `systemEnabled` is true. If overlay permission is
revoked mid-run, the service notices within ~2 s (or on the first failed window
update), detaches the bar, publishes `DetectionBus.overlayPermissionLost`, and
stops itself; the dashboard shows a re-grant banner. POST_NOTIFICATIONS denial
never blocks detection — the FGS keeps running with the notification suppressed.

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

`OverlayGateTest` feeds synthetic confidence ramps on a virtual clock (40 ms
ticks), covering: sustained-rise appear timing, brief spikes ignored,
hysteresis-band hold, sustained-drop disappear timing, brief dips ignored
(no flicker), timer resets after band excursions, and inclusive boundary
values.

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
   exposed on the dashboard.
6. **Settings storage.** `SharedPreferences` behind a `StateFlow` — two
   sliders, an onboarding flag, and the `systemEnabled` boolean don't justify
   DataStore.
7. **Overlay visibility (§3).** Show at confidence ≥ 40 sustained 250 ms; hide
   at ≤ 30 sustained 400 ms (10-point hysteresis band holds state); 220 ms
   fade/scale-in, 180 ms fade-out; fixed 15 dp transparent window with the
   drawn bar scaling 5 dp → 12 dp (no window relayout, no per-frame
   `updateViewLayout`); full opacity at all visible confidences; 12 % height
   pulse above confidence 70 instead of an alpha dip. Gradient stops:
   `#2563EB→#3B82F6` / `#F59E0B→#FBBF24` / `#DC2626→#EF4444`.
8. **Package name** `dev.lookup`, application id `dev.lookup`.
9. **Sensor rates.** Accelerometer at `SENSOR_DELAY_GAME` (~50 Hz),
   proximity at `SENSOR_DELAY_NORMAL`. All engine timing derives from
   `SensorEvent.timestamp` (monotonic nanos), which keeps the engine pure and
   unit-testable; the gate reuses the snapshot timestamp, so debounces share
   that clock.
10. **Debug tab icon** uses the material `Build` icon and the Dashboard tab the
    `Home` icon (the icon set bundled with Compose is intentionally small; no
    extended-icons dependency).
11. **Boot receiver.** `exported=true` (protected system broadcast, no security
    risk), plus `MY_PACKAGE_REPLACED` so app updates don't silently disable the
    watch. The service additionally guards `systemEnabled` in `onCreate`
    against stale sticky restarts, and the notification Stop action persists
    `systemEnabled=false`.
12. **Dashboard toggle semantics.** The Switch reflects the *actual* service
    state (`DetectionBus.running`), not the persisted flag. Toggling on without
    overlay permission opens the grant screen instead of starting; the user flips
    it again after granting.
13. **Battery exemption.** Skippable in onboarding with the tradeoff stated
    plainly; the dashboard shows a "Fix" row while not exempt. The OEM prompt
    behavior itself is unverifiable here (see below).
14. **App identity.** Display name `lookup` (lowercase); launcher icon is a
    blue→amber→red gradient pill on dark `#1A1C20`; notification icon kept as
    the flat-white chevron glyph (already guideline-compliant). Version 0.2.0.

## Needs real-device verification

No Android device/emulator was available for this pass; everything below is
**unverified on hardware**:

- [ ] Overlay bar actually renders above the status bar on API 26–35
      (`TYPE_APPLICATION_OVERLAY` + `FLAG_LAYOUT_IN_SCREEN` z-order above
      status-bar icons varies by OEM/inset policy).
- [ ] Overlay visual clarity in real conditions: legibility in outdoor
      sunlight, peripheral-vision salience of the 5–12 dp bar, whether the
      220 ms/180 ms transitions feel instant-but-smooth and the 12 % height
      pulse reads at high confidence.
- [ ] Appear/disappear behavior on real walks: show-40/hide-30 thresholds and
      250/400 ms debounces may need tuning for real sensor noise and gait
      variability; watch for flicker at the boundary in the Debug tab.
- [ ] Boot persistence across OEMs: reboot → service restarts when enabled;
      credential-encrypted storage is unavailable before first unlock, so
      behavior on direct-boot devices needs checking.
- [ ] Real-world behavior of the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
      prompt (wording, grant rate, OEM-specific battery managers that ignore
      it, e.g. aggressive task killers).
- [ ] START_STICKY restart reliability after the process is killed
      (swipe-away, low-memory killer) with and without the exemption.
- [ ] Overlay permission flow end-to-end: onboarding rationale → grant in
      system settings → "Start protection" enabled.
- [ ] API 34+ `specialUse` FGS starts without
      `ForegroundServiceTypeException` (Play-review constraints on specialUse
      are a *release* concern, not a build one).
- [ ] POST_NOTIFICATIONS runtime prompt appears on API 33+ and denial keeps
      detection running (notification suppressed).
- [ ] Mid-run overlay revocation detaches the bar and shows the dashboard
      banner within ~2 s.
- [ ] Real accelerometer step detection across gaits (the 0.45× median
      threshold may need tuning for real sensor noise floors).
- [ ] Real proximity sensor polarity (near = value < maxRange/2) across
      vendors; devices without proximity fall back to the 0.78 unknown factor.
- [ ] Tilt band 55°–120° feels right in-hand; tune with the Debug tab's live
      tilt readout.
- [ ] Screen-state gating via `ACTION_SCREEN_OFF` broadcasts while the FGS
      runs (doze/aggressive battery managers on some OEMs).
- [ ] Battery drain at ~50 Hz continuous sensing (target: negligible over a
      30-min walk).
- [ ] Notification tap reopens the app; "Stop" action stops the service and
      persists the off state (no boot restart afterwards).
