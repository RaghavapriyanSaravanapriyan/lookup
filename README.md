```
l      ooo    ooo    k  k   u   u  pppp
l     o   o  o   o  k k    u   u  p   p
l     o   o  o   o  kk     u   u  pppp
l     o   o  o   o  k k    u   u  p
lllll  ooo    ooo   k  k    uuu   p
```

look up while you walk — or don't. we built you a terminal for it.

> lookup lets you doomscroll while you walk. that's the whole pitch. a black-and-white trading floor for your bad habits, with one honest coloured tape that tells you when the sidewalk is about to collect its tax.

this is a fun project. take it seriously exactly as much as it takes you seriously, which is to say: completely, and not at all. :)

## opinions, stated plainly

- light mode is for people who look up. this app is black. the text is white. the charts are allowed colour because colour is earned on the floor, not decorated in the chrome.
- information density is a love language. if your dashboard doesn't look like it could clear a trade, why even have a dashboard.
- the phone is a slot machine you carry into traffic. lookup doesn't take the slot machine away — it just hires an oracle to watch the road while you pull the lever.
- warnings should be peripheral, not parental. a thin bar, blue to amber to red. no buzz, no scold, no streak broken. just tape.
- everything runs on-device. the cloud never hears about your 2am kebab pilgrimage. the oracle forgets nothing, but it tells no one.

## what it does

lookup watches how you walk and where your phone is, and warns you when you are walking while looking at the screen. a thin gradient bar appears above the status bar, shifting blue to amber to red as confidence rises. everything runs on-device.

the app around it is a terminal: a scrolling quote tape, a doomscroll index with its delta and zone, walking/view evidence quoted like bid and ask, a live confidence tape drawn tick-by-tick in blue/amber/red, a motion floor for raw accelerometer deviation, a full order book of fusion inputs, and a ledger of market events on the scrying floor. when protection is off, the tapes run a demo feed so the floor never looks dead. the oracle narrates throughout, because silence is for libraries.

## how it works

- always-on background monitoring. a foreground service fuses accelerometer, proximity, and screen state into a 0-100 distraction confidence score. once enabled, it restarts on boot and keeps running without the app open.
- conditional overlay. the bar only appears while confidence holds above threshold during a distracted-walking moment, then detaches. there is no persistent ui and no attention cost the rest of the time.
- gradient meaning. blue is low confidence, amber is rising, red means look up now. the same palette inks the terminal's tapes — the only colour in the building.
- zones. CLEAR below 30, WATCH below 40, OMEN below 70, RED CANDLE above. the gate shows at 40 sustained 250 ms and hides at 30 sustained 400 ms. the pulse swells above 70. yes, we named a safety threshold after candlesticks. no, we're not sorry.

## screenshots

screenshots coming after real-device testing. no images are included yet, and no working demo is claimed until the overlay rendering, permission flows, and sensor behavior are confirmed on hardware. the terminal looks mythical in the emulator, but mythical is not the same as verified.

## get the app

primary method, one click: download `app-debug.apk` from the latest release at https://github.com/RaghavapriyanSaravanapriyan/lookup/releases/latest and install it with `adb install app-debug.apk`. requires android 8.0 (api 26) or later, plus overlay permission granted in onboarding.

secondary method, latest commit: open the repo actions tab, pick the newest `build` run, and download the `lookup-debug-apk` artifact.

secondary method, local build: `./gradlew assembleDebug`. the apk lands at `app/build/outputs/apk/debug/app-debug.apk`.

## building from source

requirements: jdk 17, android sdk with platform 35 and build-tools 35.0.0, roughly 2 gb of free disk.

```bash
git clone https://github.com/RaghavapriyanSaravanapriyan/lookup.git
cd lookup
export JAVA_HOME=~/tools/jdk-17
export ANDROID_HOME=~/AndroidSdk
```

on a fresh debian 13 machine without android tooling, install everything under the home directory (no root required):

```bash
# jdk 17 (temurin)
curl -sL -o /tmp/jdk17.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p ~/tools
tar -xzf /tmp/jdk17.tar.gz -C ~/tools
mv ~/tools/jdk-17.0.* ~/tools/jdk-17

# android sdk: command line tools first, then packages
mkdir -p ~/AndroidSdk/cmdline-tools
curl -sL -o /tmp/cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q /tmp/cmdline-tools.zip -d /tmp/sdk-tmp
mv /tmp/sdk-tmp/cmdline-tools ~/AndroidSdk/cmdline-tools/latest
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

`local.properties` (git-ignored) points the build at the sdk:

```
sdk.dir=/home/raghav/AndroidSdk
```

toolchain: agp 8.7.3, kotlin 2.0.21 with the compose compiler plugin, compose bom 2024.12.01, minsdk 26, target and compile sdk 35.

```bash
./gradlew build   # assembles debug and release, runs lint and unit tests
./gradlew test    # unit tests only
```

ci runs both commands on every push to `main` (see `.github/workflows/build.yml`) and uploads the debug apk as an artifact.

## architecture

`DetectionEngine` ([app/src/main/java/dev/lookup/detection/DetectionEngine.kt](app/src/main/java/dev/lookup/detection/DetectionEngine.kt)) is pure kotlin with no android dependencies. it estimates gravity with a 0.18 s low-pass filter, derives screen tilt and linear-acceleration deviation, detects steps by peak thresholding, estimates cadence from median step intervals, and scores confidence as walking by phone-in-view by 100, ema-smoothed. it self-calibrates step threshold and cadence baseline to the user's gait.

`OverlayGate` ([app/src/main/java/dev/lookup/detection/OverlayGate.kt](app/src/main/java/dev/lookup/detection/OverlayGate.kt)) decides bar visibility: appear at confidence 40 or above sustained 250 ms, disappear at 30 or below sustained 400 ms, with the 30-40 band holding state.

`OverlayService` ([app/src/main/java/dev/lookup/service/OverlayService.kt](app/src/main/java/dev/lookup/service/OverlayService.kt)) is a `specialUse` foreground service hosting the engine. it attaches the overlay view to the window manager only while the gate is armed, with a 220 ms fade and scale in and a 180 ms fade out, inside a fixed 15 dp window with the drawn bar scaling 5 dp to 12 dp.

`BootReceiver` ([app/src/main/java/dev/lookup/service/BootReceiver.kt](app/src/main/java/dev/lookup/service/BootReceiver.kt)) restarts the service on boot completed and on package replaced when the persisted `systemEnabled` flag is set.

the terminal shell is deliberately thin over the service:

- theme ([app/src/main/java/dev/lookup/ui/theme/Theme.kt](app/src/main/java/dev/lookup/ui/theme/Theme.kt)): forced-dark monochrome. no light mode. primary is white on black; the only colour tokens live for charts.
- design tokens ([app/src/main/java/dev/lookup/ui/terminal/Terminal.kt](app/src/main/java/dev/lookup/ui/terminal/Terminal.kt)): panels, quote cells, stat cells, halt banners, the oracle's one-liners, zone labels. chrome never colours.
- trading charts ([app/src/main/java/dev/lookup/ui/charts/TradingCharts.kt](app/src/main/java/dev/lookup/ui/charts/TradingCharts.kt)): `ConfidenceTape` (segmented blue/amber/red stroke with white core sheen and last-tick glow), `AccelFloor` (white deviation line, amber threshold, step volume ticks), `TickerTape` (infinite marquee of quotes), plus a rolling tape buffer that runs live on-device data when protected and a fast synthetic demo feed when halted.
- terminal ([app/src/main/java/dev/lookup/ui/DashboardScreen.kt](app/src/main/java/dev/lookup/ui/DashboardScreen.kt)): masthead with live clock and LIVE/HALT pill, doomscroll-index hero with delta/high/low and oracle line, evidence book, both tapes, the full fusion order book, master switch, gain sliders, calibration.
- scrying floor ([app/src/main/java/dev/lookup/ui/DebugScreen.kt](app/src/main/java/dev/lookup/ui/DebugScreen.kt)): confidence autopsy, raw motion tape, event ledger (bar attaches, steps, zone crossings), full fusion book.
- onboarding ([app/src/main/java/dev/lookup/ui/OnboardingScreen.kt](app/src/main/java/dev/lookup/ui/OnboardingScreen.kt)): three clearance pages — DOOMSCROLL BUT MAKE IT PEDESTRIAN, THREE OMENS, CLEARANCE — on pure black with the demo bar as the sole colour.

unit tests live in [app/src/test/java/dev/lookup/detection/](app/src/test/java/dev/lookup/detection/): 12 engine tests on synthetic 50 hz sensor data and 9 gate tests on synthetic confidence ramps, 21 total.

## how this repo is built

fully agentically engineered — the agent writes the code, runs the builds, and keeps the tapes fast. the human drives only architecture and design, points at the horizon, and says things like "more aura." this is a fun project :) and it is built like one: opinionated, a little mythical, allergic to beige.

if a line of copy made you snort, that was the design review. if a chart made you feel like a sidewalk fund manager, that was the acceptance test.

## assumptions

decisions made without asking, per the repo ground rules:

1. `specialUse` foreground service type, since continuous sensor fusion has no better-matching type, with a descriptive subtype property.
2. phone-in-view model: screen on, tilt within 55-120 degrees from face-up (widened by sensitivity), proximity near caps the score at 0.12, missing proximity hardware degrades to a 0.78 factor.
3. product scoring: confidence needs both walking and a visible screen, so pocket-walking and sitting-with-phone stay quiet by design.
4. tilt semantics: angle between screen normal and vertical, gravity from a 0.18 s low-pass.
5. self-calibration: threshold near 0.45x rolling median step amplitude (clamped, sensitivity-scaled), baseline cadence ema from sustained walking, idle decay of the amplitude estimate, reset exposed on the dashboard.
6. sharedpreferences behind stateflow for sensitivities, the onboarding flag, and the `systemEnabled` boolean. no datastore.
7. overlay visibility: show at 40 sustained 250 ms, hide at 30 sustained 400 ms, fixed 15 dp window, drawn bar 5 dp to 12 dp, full opacity while visible, 12 percent height pulse above confidence 70, gradient stops `#2563EB` to `#3B82F6` to `#F59E0B` to `#FBBF24` to `#DC2626` to `#EF4444`.
8. package and application id `dev.lookup`.
9. accelerometer at game rate near 50 hz, proximity at normal rate, all engine and gate timing derived from the monotonic sensor timestamp.
10. bundled material core icons only for the dashboard and debug tabs.
11. boot receiver exported with boot completed and package replaced actions; the service guards `systemEnabled` against stale restarts; notification stop persists the off state.
12. the dashboard switch reflects actual service state; enabling without overlay permission opens the grant screen first.
13. the battery exemption is skippable with the tradeoff stated plainly; the dashboard shows a fix row while not exempt.
14. display name `lookup`, gradient-pill launcher icon on dark `#1A1C20`, flat-white notification glyph, version 0.2.0.
15. polish pass: all ui copy in `strings.xml` with plurals, 250 ms fade and scale navigation transitions, night theme with dark window background, overlay gradient shader cached per width and colors with identical pixels, backups disabled via `allowBackup=false` plus data-extraction and full-backup excludes, `mipmap-anydpi-v26` kept because adaptive icons require the v26 qualifier (moving it breaks the build, verified), dependency-version and battery-life lint warnings left as-is (upgrade risk, and the exemption is the core feature on a side-loaded app).
16. terminal rebuild: forced-dark black-and-white chrome, always. colour is fenced inside `ConfidenceTape`, `AccelFloor`, and the warning bar. tabs renamed TERMINAL and FLOOR. demo tape synthesizes ~11 ticks/s when halted so the floor never looks dead — demo data is clearly labelled and never touches the engine. oracle lines and zone labels are chrome personality, hardcoded next to the tapes rather than in `strings.xml`, because they are the market's voice, not localizable prose. window background is pure `#000000` in both day and night resources.

## needs real-device verification

no android device or emulator was available for the detection pass; everything below is unverified on hardware:

- [ ] overlay renders above the status bar and its icons across oems and api 26 to 35.
- [ ] visual clarity in real conditions: sunlight legibility, peripheral salience of the 5 to 12 dp bar, transition feel, pulse legibility at high confidence.
- [ ] gate thresholds and debounce timing on real walks; flicker watch at the boundary via the debug tab.
- [ ] boot persistence across oems, including first-unlock behavior where credential-encrypted storage is unavailable.
- [ ] battery-exemption prompt wording, grant rate, and oem battery managers that ignore it.
- [ ] sticky restart after process kill, with and without the exemption.
- [ ] overlay permission flow end to end.
- [ ] `specialUse` foreground service start on api 34 and later.
- [ ] post-notifications prompt and denial behavior on api 33 and later.
- [ ] mid-run overlay revocation detaches the bar and raises the dashboard banner within about 2 s.
- [ ] step detection across real gaits and sensor noise floors.
- [ ] proximity polarity across vendors.
- [ ] tilt band feel in hand.
- [ ] screen on and off broadcasts under doze.
- [ ] battery drain over a 30 minute walk.
- [ ] notification tap reopens the app; stop persists the off state with no boot restart after.
- [ ] terminal legibility in sunlight: white-on-black mono at 9-11 sp, tape colours under glare, marquee readability while actually walking (the irony is not lost on us).
- [ ] demo-vs-live confusion check: nobody should mistake the halted demo tape for real protection.

## license

mit, see [LICENSE](LICENSE).

## contributing

issues and pull requests are welcome. agent-driven contributions follow [AGENTS.md](AGENTS.md). bring opinions; leave beige at the door.
