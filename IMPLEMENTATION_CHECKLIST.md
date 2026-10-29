# AppLock implementation and release checklist

Agreed: relock “Immediately” means zero delay; recents exclusion affects AppLock only; production links stay explicit placeholders at the owner's request.

Latest October 29 follow-up: [lifetime Premium, Restore, ad removal and Intruder gating](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/BILLING_IMPLEMENTATION_2026-10-29.md>). Implementation and source checks are complete; no builds or device/Play tests were run. The linked checklist records owner-run commands and remaining validation. This supersedes the earlier billing source review.

Earlier October 29 work: the Vault video rendering fix below. Its final dependency selection is left uncompiled at the owner's request. Previous work includes the [October 27 approved CalculatorLock-based implementation](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/REFERENCE_APPLOCK_2026-10-27.md>) and PRO close fix, whose compilation, 41 unit tests and lint passed. The white accessibility cover and Accessibility service were retired in favour of Usage Access monitoring and one lock activity. The [build configuration](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/BUILD_COMPATIBILITY.md>) remains AGP 8.13.2, Gradle 8.13, compile/target SDK 36 and minimum SDK 26. Device playback, Vivo frame timing and release scroll-performance verification remain pending.

## Reported issues and causes

| Reported issue | Cause found | Implementation |
| --- | --- | --- |
| Protected content briefly visible / white flicker | Detection/activity launch delay and an extra white-cover presentation | Usage Access monitor with one opaque, animation-free lock activity; physical frame verification remains pending |
| Video opens an external player | Video selection explicitly launched an external chooser | Internal playback and the same preview/Unhide flow used for photos |
| Lock missing or delayed after Back/reopening | Stale visibility/foreground state, premature launch flags and incomplete departure/expiry handling | Separate policy and presentation state, no grant on Back, bounded foreground verification, launch watchdog and monotonic expiry |
| Rapidly tapping the PRO close button opens Create PIN | The outgoing screen handles a second tap after the first pop changes the previous entry; duplicate PRO entries have the same unsafe fallback | Consume close once per destination entry, return initialized users to Apps/Settings, and guard all PRO opening paths |
| Every hidden video flickers green in Vault while Gallery playback is normal | The Vault uses a SurfaceView-backed VideoView inside a moving Compose pager and prepares neighbouring players; this rendering path is the suspected cause, pending confirmation on the affected device | Media3 Compose TextureView rendering with first-frame shutter, decoder fallback and allocation only for the active authenticated video |

## Vault video rendering follow-up — October 29, 2026

- [x] Replace the native `VideoView` with Media3 ExoPlayer and Compose `ContentFrame`, explicitly selecting `SURFACE_TYPE_TEXTURE_VIEW`. Retain the existing controls, internal preview, file source and Unhide flow.
- [x] Keep a black shutter until the first decoded frame; preserve aspect ratio through the library's presentation state. Enable decoder fallback when initialization of the preferred decoder fails.
- [x] Allocate a player only for the active, authenticated, resumed page. Release on disposal/page departure, authentication gating or recomposed background state, and pause immediately on lifecycle pause independently of recomposition.
- [x] Check live authentication and lifecycle for playback requests and preparation callbacks; handle rapid pause/resume even if no intervening recomposition occurs.
- [x] Preserve position and user pause intent; support seeking, replay from completion, state restoration, audio focus/headphone removal, and the existing friendly error with Unhide still accessible.
- [x] Add four instrumentation regressions for real decoder/frame output through a TextureView, paused seeking/restoration/page changes/completion replay, background/auth gating, revocation during preparation, and unreadable media cleanup. Update the full Vault/Unhide test for the new renderer. These device tests have not been executed.
- [x] Source checks: all Vault video rendering uses the explicit TextureView path; playback uses the live auth/lifecycle gate; controls and Unhide remain separate; dependency versions match; `git diff --check` passed. Local Media3 1.10.0 metadata requires compile SDK 36 and requests a Compose BOM older than the project's existing BOM, avoiding the UI upgrade that 1.11.1 would introduce. The required ContentFrame/TextureView APIs exist in the selected artifact.
- [ ] Owner: compile/test/lint the final Media3 1.10.0 selection. Earlier checks against 1.11.1 compiled debug/release/Android-test Kotlin and passed 41 unit tests, but do not verify the final dependency selection. The owner stopped further build work on October 29; no Gradle process remained running when checked. No APK was packaged for this fix.
- [ ] Physical device: confirm the reported green flicker is resolved on the affected phone, and test Home/return, screen rotation, repeated swiping, seek/replay, headphones and representative portrait/landscape/HDR/codec clips. No APK packaging, installation or permission change is part of this follow-up.

Implementation reference: [Android's Media3 Compose surface guidance](https://developer.android.com/media/media3/ui/surface) and [Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3). Media3 ExoPlayer and UI Compose use the same version, 1.10.0; the declared SDK range remains minimum 26, compile/target 36. These changes address the rendering path; unit tests alone cannot prove the device's display no longer flickers.

Owner-run verification from `MyApplication2` (not executed for the final dependency selection):

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:compileReleaseKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug
```

Focused playback tests on a connected test phone; this command builds and installs the test/app APKs, and the tests use only a private synthetic video without granting media permissions:

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.VaultVideoPlayerTest"
```

## PRO close follow-up — October 27, 2026

- [x] Move navigation out of the PRO screen into its graph destination; route the close button and system Back through the same guarded callback.
- [x] Ignore repeated and stale close callbacks; reopening PRO creates an independent guard.
- [x] Return initialized users to their Apps/Settings tab, remove duplicate PRO entries, and recover to Apps if the expected main entry is absent. PIN creation remains the first-time setup continuation only.
- [x] Guard all three PRO opening paths against duplicate destinations and clicks during navigation transitions.
- [x] Five new guard regression tests passed; the full current unit suite passes **41 tests**, zero failures/errors/skips. Debug, release and Android-test Kotlin compilation passed. These JVM tests verify callback ownership and single-use behavior; they do not replace physical tap testing.
- [x] Android lint passed with zero errors; the existing 167 warnings and 3 hints remain. `git diff --check` passed.
- [ ] Device: rapid close taps, close plus system Back, reopening PRO, Apps/Settings return, and first-time setup. No APK was packaged or installed for this fix.

## Implementation

- [x] Decouple and test relock policy, app grants, monotonic expiry and return cancellation.
- [x] Present one opaque lock activity; handle lock visibility, blocked launches, Back/Home/Recents, rapid switching, screen-off, rotation and service restart without a separate white curtain.
- [x] Gate protected AppLock screens on background return and restored navigation; keep startup ads isolated from external app protection.
- [x] Require authenticated internal password-reset authorization; reject hostile extras.
- [x] Persist vault copies and metadata before original deletion; preserve partial successes and interrupted operations.
- [x] Handle old Android write permissions and newer selected-media access.
- [x] Internal video preview with Unhide, lifecycle pause/release, errors and rotation handling.
- [x] Persistent recents exclusion without closing the app; secure sensitive task previews.
- [x] Biometric availability/retry and lifecycle cancellation.
- [x] Vibration, intruder defaults/atomic counters/cancellation and settings persistence.
- [x] Older Android language restoration, including external lock UI.
- [x] Privacy Policy, Share and Rate Us with replaceable resource destinations.
- [x] Credential/backup handling, actionable errors and service status.

## Validation

- [x] Prior revision: debug APK and test APK builds, release Kotlin compilation, 21 unit tests and Android lint (zero errors).
- [x] October 21: built and signature-verified the owner's requested debug APK containing the October 19 fixes. No installation was performed.
- [x] October 21 Home-navigation follow-up: debug Kotlin compilation, 31 unit tests and Android lint passed (zero errors). See `ISSUE_FIXES_2026-10-19.md` for the race and regression coverage.
- [x] October 21, 12:05 local time: packaged and signature-verified the newly requested debug APK including the Home-navigation follow-up.
- [ ] Device-test the Home-navigation follow-up; no installation or permission change was performed by the assistant.
- [x] Pixel 6a / Android 17 (API 37): hostile reset intents, background return, recreated auth gate, authorized recovery and revocation after backgrounding (4 instrumentation tests).
- [x] All relock delay boundaries, repeated Home events, return cancellation, independent screen-off grants and expiry without another event (10 unit tests).
- [x] Concurrent intruder attempts and the Never/capture-disabled setting (1 instrumentation test).
- [ ] Device: external lock Back/Home/Recents, rapid switches, screen-off and blocked-launch recovery. The owner selected app-only tests and requested that device permissions remain unchanged.
- [ ] Vivo Y33s / Android 13: measure cold/warm protected-app launches from Home, Recents and notifications with the approved reference-based monitor; verify app-list scroll frame times in release/profileable mode. No new APK was packaged or installed for this follow-up.
- [ ] Device: all relock durations and independent screen-off grants while actually protecting external apps.
- [x] Pixel 6a: partial import failure preserves the first file and restores matching bytes (instrumentation test passed).
- [ ] Device: permission denial/revocation, rotation during consent and forced process interruption of transfers.
- [x] Pixel 6a: internal video playback, authentication pause/resume and Unhide using a synthetic clip (1 instrumentation test).
- [ ] Device: video background/foreground transitions, rotation and unsupported codecs across devices.
- [ ] Device: settings/cold restart persistence on old/current Android and representative OEMs.
- [ ] Release owner: replace dummy links/package/ad/billing configuration and configure signing.

Usage Access reports activity changes after launch and the monitor polls every 350 ms. Zero exposed frames across arbitrary devices cannot be guaranteed; measure this on target devices before release. Checkboxes distinguish implementation from device validation.

## Evidence and release limits

- Previously executed unit tests: **21 passed**, zero failures/errors (relock 10, credential hashing 3, safe import 4, locale migration 3, existing example 1). Previously executed physical-device instrumentation: **8 passed**, zero failures (authentication 4, intruder settings 1, video preview 1, partial transfer 1, existing example 1). These results predate the independent review fixes below.
- Android lint: zero errors; 154 warnings and 5 hints remain, covering existing resource/style/dependency/deprecation advisories. This is not a warning-free release claim. Reports are in `app/build/reports/`.
- All new user-visible messages have resources for the supported languages. Norwegian resources now use `nb-NO`; original saved `se` preferences migrate to it. Older Android also restores the saved app language.
- Authentication grants and reset authorization stay in process memory. Returning from the background or restarting the process requires authentication. Reset intents cannot create authorization. Credentials use salted verifiers; interrupted setup no longer stores a second plaintext credential, and setup waits for the verifier to persist before continuing.
- Vault transfers use atomic metadata, staged copies and per-item commits. Relock deadlines use monotonic elapsed time. Sensitive backups and task snapshots are disabled. Intruder photos are private app files; metadata failures retain the photo for recovery, and image caches/preview decoding are bounded.
- The isolated emulator failed to finish booting; its processes and temporary files were removed. Phone tests used only this project app and synthetic media. Verified afterwards: AppLock accessibility disabled, overlay permission at default, camera/notification/media permissions denied. No permissions were granted.
- Production URLs remain dummy resources at the owner's explicit request. Device/OEM validation and owner configuration remain release gates; passing builds alone does not certify production readiness.

## Independent source verification — October 16, 2026

- Reviewed service launch/visibility/departure state, relock policy, auth/reset gates, transfer ordering/recovery, playback lifecycle, permission branches, settings persistence and locale restoration.
- Parsed all 95 main-source XML files successfully. No unresolved base string references. All new translated messages exist in each of the ten translated locales. `git diff --check` passed.
- Fixed late video preparation/control requests: playback now checks authentication, current page and RESUMED lifecycle at the actual `start()` call.
- Fixed interrupted vault indexing: completed private files omitted from a valid or missing index are recovered; partial `.part` files remain excluded. Added an isolated regression test in `VaultRecoveryTest`.
- Fixed recovery settings being saved separately: question and hashed answer now commit together, preventing a changed question being paired with the old answer after cancellation. Save errors leave the settings dialog open.
- Fixed the reset-password settings subtitle always saying Pattern: it now reflects the stored pattern or four/six-digit PIN.
- Extended the video regression to check real activity pause/resume as well as the authentication gate.
- The newly added/extended tests have **not been run**, and these latest edits have **not been compiled**. The assistant's in-progress verification build was stopped at the owner's request; building and device testing are left to the owner.
