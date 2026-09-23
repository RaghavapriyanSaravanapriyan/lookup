# emulator test results — lookup v0.2.0

date: 2026-09-23. no real device was available; every test below ran on the
android emulator and is verifiable through adb. no app source was modified in
this pass (only this file and `docs/emulator-overlay-conf100.png` were added).

## environment

- host: debian 13 x86_64, 16 cpus, 30 gb ram, kvm accessible (`/dev/kvm` acl)
- emulator 37.1.11.0, system image `system-images;android-35;google_apis;x86_64`
- avd `lookup-test`: pixel 7 profile, 3072 mb ram, `swiftshader_indirect`,
  headless (`-no-window -no-audio -no-boot-anim -no-snapshot`)
- app: `dev.lookup` versioncode 1 versionname 0.2.0, fresh `./gradlew assembleDebug`
- ui automation: `adb exec-out uiautomator dump /dev/tty` parsed for text and
  bounds plus `adb shell input tap x y`; no vision-based assertions except the
  single screenshot in test 6, which was read back and inspected.

## 0. emulator setup — pass

installed `emulator` and the api 35 `google_apis` x86_64 image via sdkmanager,
created the avd with avdmanager, booted headless. `adb devices` showed
`emulator-5554 device`, `sys.boot_completed=1`, android 15.

## 1. install and launch smoke test — pass

commands:

```bash
adb logcat -c
adb install -r app/build/outputs/apk/debug/app-debug.apk  # Success
adb shell am start -n dev.lookup/.MainActivity
```

- `dumpsys activity activities` showed `dev.lookup/.MainActivity` resumed.
- `logcat AndroidRuntime:E` was empty: no crash on cold start.
- app-process warnings triaged, all benign and non-fatal:
  - `Unexpected CPU variant for x86: x86_64` (art on emulator)
  - `base.dm: No such file or directory` (debug build ships no dex metadata)
  - `HWUI Unknown dataspace 0`, `Failed to initialize 101010-2 format`
    (swiftshader software gl)
  - compose `SnapshotStateList ... failed lock verification and will run
    slower` (known unoptimized-dex artifact on x86_64 emulator debug builds)
- remaining logcat warnings were system_server/emulator noise (selinux
  denials on shell sockets, frozen-process oneway calls, missing
  `persistent_data_block` service).

## 2. onboarding flow — pass

walked all three pages with tap automation (`Next`, `Back` verified).

- notifications: tapping `Allow notifications` opened the real system runtime
  dialog (`Allow lookup to send you notifications?` with allow / don't allow).
  deny path: `don't allow` left `POST_NOTIFICATIONS: granted=false
  (USER_SET)`, the card kept its action button, no crash. grant path: `allow`
  gave `granted=true`, the button disappeared and the status icon flipped to
  granted.
- battery exemption: tapping `Allow in background` opened the real system
  dialog (`Let app always run in background?` with deny / allow plus the
  battery-life warning text). deny path: package absent from `dumpsys
  deviceidle whitelist`, button stayed, no crash. grant path: `user,dev.lookup`
  appeared in the whitelist and the button disappeared.
- overlay: tapping `Allow overlay` opened the real settings page
  (`com.android.settings/.spa.SpaActivity`, display-over-other-apps list
  showing `lookup` as not allowed). deny path: back-navigation out of settings
  left the grant ungranted and the app on onboarding with no crash. grant
  path: `appops set dev.lookup SYSTEM_ALERT_WINDOW allow` while the settings
  page was open (equivalent end state to flipping the settings toggle), back
  to the app, the resume refresh enabled `Start protection` and cleared the
  grant hint.
- tapping `Start protection` completed onboarding and landed on the dashboard
  showing service running.

## 3. dashboard functionality — pass

- service start verified beyond ui state: `dumpsys activity services
  dev.lookup` listed `OverlayService` with `isForeground=true`, the ongoing
  notification `dev.lookup|42` was posted, and prefs held
  `system_enabled=true` plus `onboarding_completed=true`.
- master switch off (tap at the switch node): service record count went to 0,
  `system_enabled=false`, ui showed `Stopped`. switch on: record back,
  `system_enabled=true`.
- battery `Fix` row: absent while exempt; after `dumpsys deviceidle whitelist
  -dev.lookup` plus a home-then-relaunch resume cycle, the row `Battery
  optimization is on ...` with `Fix` appeared. tapping `Fix` reopened the
  system dialog, `Allow` re-whitelisted the package, and the row disappeared.
  (note: `am start` onto the already-foregrounded activity does not produce a
  resume event, so the row only refreshes on a real pause/resume — correct
  lifecycle behavior, not a bug.)

## 4. synthetic sensor injection — pass

method: persistent emulator-console session (telnet + auth token) sending
`sensor set acceleration x:y:z` at a measured 15.0 hz. base gravity
`(0, 9.81, 0)` gives tilt 90 degrees; walking adds per-step sine bumps on x.
`proximity` set to 5, which the debug screen reported as `Far`. screen stayed
on via `stay_on_while_plugged_in`.

- walk (`amp 3.0 m/s²`, `100 spm`, 30 s): debug screen read cadence `100 spm`
  exactly, tilt `90°`, confidence climbing 8 to 100, walking and phone-in-view
  evidence 100 percent. `OverlayGate` armed (`Warning bar 100 percent`) and an
  overlay window attached: `Window{... u0 dev.lookup}` with
  `appop=SYSTEM_ALERT_WINDOW`, `ty=APPLICATION_OVERLAY` (type 2038),
  `mBaseLayer=111000`, gravity top, full width.
- motion stop (injection process ended): confidence decayed to 0, the overlay
  window detached (only systemui's own overlay window remained), the service
  stayed alive, no crash.
- stationary (`still`, 15 s): confidence stayed 0, no overlay window attached.
- sub-threshold (`amp 0.5 m/s²`, below the 0.72 step threshold, 18 s):
  `0 spm`, 0 percent walking, confidence 0, no overlay. rhythmic motion the
  engine should ignore is ignored through the real pipeline.

## 5. boot persistence — pass

- enabled case: with `system_enabled=true`, `adb reboot`. after boot completed
  plus 20 s and without launching the app, `dumpsys activity services`
  listed `OverlayService` and its notification `dev.lookup|42` was posted.
- disabled case: launched the app, toggled protection off (`system_enabled`
  persisted `false`, service record gone), `adb reboot`. at boot plus 20 s and
  again at plus 50 s there was no `OverlayService` record. the app process was
  briefly present, which is expected: the system spawns the process to deliver
  `BOOT_COMPLETED` to `BootReceiver`, which correctly did nothing.

## 6. debug screen verification — pass

during the walking injection in test 4 the debug screen showed confidence
`100`, walking / phone-in-view / warning-bar evidence rows at 100 percent
with progress bars, cadence `100 spm`, tilt `90°`, screen `On`, proximity
`Far`. the screenshot `docs/emulator-overlay-conf100.png`, read back and
inspected, additionally confirms the canvas rendering automation cannot see:
a vivid full-width red bar pinned above the status bar icons at the top edge;
the linear-acceleration chart with regular step peaks, vertical step-marker
lines aligned to the peaks, the amber dashed threshold line labeled
`threshold 0.88` (self-calibration visibly adapted upward from 0.72), and the
`m/s²` / `last 10 s` axis labels. light theme renders correctly.

## 7. lint and build sanity — pass

```bash
./gradlew clean build   # BUILD SUCCESSFUL
./gradlew test          # BUILD SUCCESSFUL
```

- `testDebugUnitTest`: 21 tests, 0 failures, 0 errors.
- `testReleaseUnitTest`: 21 tests, 0 failures, 0 errors.
- lint: 0 errors. remaining warnings are the documented leftovers
  (dependency-version bumps, battery-life policy note, the required
  `mipmap-anydpi-v26` folder).

## not testable in emulator — deferred to real-device pass

- real gait accuracy across users, shoes, terrain, and phone placements.
- real oem battery-killer behavior (the emulator honors the exemption list;
  vendor task killers do not necessarily).
- outdoor sunlight legibility and true peripheral-vision salience of the bar.
- direct-boot behavior before first unlock (credential-encrypted storage).
- play-store policy review of the battery exemption and `specialUse` type
  (this build is side-loaded; policy is a release concern).
- real proximity-sensor polarity across vendors.
- real doze/standby broadcast timing for screen-state gating.
- tilt-band feel in hand and gate threshold tuning on real sensor noise.
