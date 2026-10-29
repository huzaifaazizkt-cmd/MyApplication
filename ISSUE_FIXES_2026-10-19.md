# AppLock fixes — October 19, 2026

Implementation covers all 12 reports in `C:/Users/786/Downloads/applock issue 1.txt`. SDK declarations remain **min 26, target 37, compile 37.1**. No Gradle build, APK installation, device test, or permission change was performed for these fixes, as requested. Previous test/build results are historical and do not certify this revision.

## Implementation checklist

| Report | Change | Status |
| --- | --- | --- |
| 1 — Active notification missing after grant | Permission success refreshes the service notification. Resumed lifecycle observes readiness; initialized apps request notifications after authentication to avoid competing prompts. Notifications request immediate visibility and track blocked/failed publication separately from service readiness. | Implemented; Infinix runtime check pending |
| 2 — Loading screen before unlock | One coherent authentication-settings snapshot warms in MainActivity and the service. External challenges wait for the initial snapshot before launching. The unlock spinner is removed; input stays disabled until ready, with fail-closed retry on read failure. | Implemented |
| 3 — Video controls overlap Unhide | Removed the floating MediaController. Playback controls occupy space inside the player; the header and Unhide use separate inset-aware layout space. Pager items use stable file keys. | Implemented; visual/device check pending |
| 4 — Slow wrong-pattern feedback | Added fast checks protected by a non-exportable Android Keystore HMAC key, bound to the credential type and salted verifier. New credentials enroll on save; existing ones enroll after successful PBKDF2 verification. Missing keys or stale records fall back safely. Checking state is visible; overlapping input is blocked. | Implemented; timing/Keystore tests pending |
| 5 — “Use Biometric” text | Replaced the visible retry text with an accessible icon. Automatic biometrics wait for RESUMED lifecycle and the drawn external challenge. Typing prevents a delayed prompt from interrupting input. | Implemented |
| 6 — Wrong-password vibration | Preserved preference/hardware checks. SDK 26–32 uses explicit sonification AudioAttributes; SDK 31+ gets the default vibrator via VibratorManager; SDK 33+ uses touch VibrationAttributes. Cancelled gestures do not cause error feedback. | Implemented; hardware/system setting checks pending |
| 7 — Missing splash/ad on relaunch | Fresh activity/task entry uses splash again. Startup has one navigation decision, premium/config/no-fill handling and a bounded ad/network wait. Late callbacks cannot navigate a disposed screen; a saved attempt marker prevents a second ad after recreation. External app locking never enters this flow. | Implemented |
| 8 — Unlock again on Back | Legacy unlock entries redirect instead of rendering a second unlock screen. Graph start does not reset when setup finishes. Returning to AppLock closes a stopped external challenge task, preventing it resurfacing through task history. Actual background departures still require AppLock authentication. | Implemented; exact OEM sequence pending |
| 9 — Card then processing | Awaiting Android consent and transferring are explicit stages of the same app overlay. Duplicate hide/restore/permission submissions are guarded. Consent request and pending selections survive recreation; cancellation and launch failure clear waiting state. Required system consent remains. | Implemented; consent/recreation checks pending |
| 10 — Intruder default/camera variation | Unset attempts default to Never (0); explicit 3/5/10 selections remain. The UI explains that photos require a threshold. Enabling checks for a front camera. Capture honors permission/lifecycle, retries transient failure once, reports availability privately in settings and rejects stale callbacks. Authentication/biometric entry cancels in-flight capture. | Implemented; camera/privacy checks pending |
| 11 — Misleading Accessibility warning | Actual permission enablement is separate from CONNECTING, READY and SETTINGS_UNAVAILABLE states. Notification and Xiaomi popup access have separate conditions/actions. A connecting service no longer falsely says Accessibility is disabled. | Implemented |
| 12 — Samsung authentication inconsistency | Cancelled patterns are discarded. One biometric prompt per process requires a resumed/focused owner; credential and biometric input cannot compete. Unconfirmed system events cannot dismiss challenges. Transient biometric windows are distinguished from real app departures; real departure events still start relock deadlines immediately. | Implemented; Samsung Android 16 check pending |

“Implemented” describes source changes, not a passing runtime result on each reported phone.

## Main source locations

- [Service/window handling](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/service/AppLockService.kt>) and [protection status](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/service/ProtectionStatus.kt>).
- [Authentication snapshot](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/data/AuthenticationSettings.kt>), [unlock UI](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/UnlockScreen.kt>) and [fast verifier](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/security/FastCredentialVerifier.kt>).
- [Main lifecycle/notifications](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/MainActivity.kt>), [navigation](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/navigation/NavGraph.kt>) and [startup](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/StartScreen.kt>).
- [Video playback](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/VaultVideoPlayer.kt>), [vault consent/layout](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/VaultScreen.kt>) and [camera](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/IntruderCamera.kt>).

## SDK branches reviewed

| SDK | Relevant paths |
| --- | --- |
| 26–28 | AndroidX biometrics, HMAC Keystore with PBKDF2 fallback, legacy media permissions, AudioAttributes vibration, locale restoration, older task-ID/draw acknowledgement. |
| 29 | RecoverableSecurityException consent, journaled MediaStore restore/publish, frame-commit acknowledgement, legacy write permission where required. |
| 30 | MediaStore write consent, bounded batches, saved consent state and cancellation. |
| 31–32 | VibratorManager, compatibility foreground-notification visibility behavior, camera privacy/lifecycle. |
| 33 | POST_NOTIFICATIONS grant/refresh, granular media permissions, VibrationAttributes. |
| 34–37 | Selected-media access, declared special-use foreground-service type/permission, lifecycle/window-aware authentication, guarded newer APIs. |

Camera hardware/privacy toggles, system haptic settings, notification channels, accessibility enablement and OEM background restrictions remain system/user-controlled; the app does not bypass them. No claim that every OEM firmware has passed testing is made. Accessibility observes windows after launch, so universal zero-exposed-frame behavior cannot be guaranteed.

The fast path uses the public [Android Keystore API](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec); hardware backing depends on the device. Keys are not exported into preferences. PBKDF2 remains available when fast verification is unavailable, and legacy migration/re-enrollment can still take the slower path once.

## Verification and regressions

- Kotlin grammar checks cover all 70 source/test files. A pre-existing multiline nullable-cast grammar limitation in XiaomiPermissionHelper is normalized only in parser input; that source remains unchanged.
- XML parsing, base string references, translated-key coverage and `git diff --check` were checked. New messages exist in all ten translated locales.
- Added/extended regressions cover cancelled-pattern attempts, Back after background re-authentication, video control overlap, and fast-verifier mismatch/context/key-change fallback. Existing video lifecycle, vault partial-transfer and recovery tests remain.
- Authentication tests preserve/restore premium state to avoid ad/network flakiness. Synthetic MediaStore tests are SDK 30+ so they do not change legacy storage permissions.
- Instrumentation regressions have **not been compiled or executed**. Subsequent debug compilation, unit tests and lint are recorded below; OEM/device checks remain pending.

## Device checklist after building

- [ ] Notification allow/deny/later grant, channel restrictions and service reconnect on Android 13+.
- [ ] Cold/warm external locking, Back/Home/Recents, rapid switching, screen-off and every relock delay.
- [ ] Biometric cancel/retry, interrupted input, rotation and Samsung Android 16 system windows.
- [ ] Haptics with app preference and device touch-vibration settings on/off.
- [ ] Video play/pause/seek/replay, background/rotation/landscape, unsupported codec and Unhide.
- [ ] Hide/restore consent accept/cancel, repeated taps, recreation and process interruption on SDK 26/29/30/33/34+.
- [ ] Intruder Never/3/5/10, permission revocation, camera privacy toggle, busy camera, background and successful-auth cancellation on Vivo/Infinix/Xiaomi.
- [ ] Startup after Recents removal, premium, offline/no-fill, slow fetch, rotation and stale callbacks.

Dummy links, store/ad/billing configuration and release signing remain owner configuration, as agreed previously.

## Debug APK — October 21, 2026

At the owner's request, `:app:assembleDebug` completed successfully for this revision. The generated `app/build/outputs/apk/debug/app-debug.apk` is 38,070,191 bytes; Android `apksigner verify --verbose --print-certs` passed with the Android Debug certificate and APK Signature Scheme v2. No installation, device permission change, or runtime test was performed. The earlier pending unit/instrumentation, lint and OEM device checks remain pending for this revision.

## Home navigation after unlocking — October 21, 2026

- Found a race between immediate relock expiry and foreground-window updates: a Home event revokes the grant, then the expiry callback can still read the departing protected app and launch its challenge over Home. While a launch was pending, foreground reconciliation also skipped cancellation.
- Added a small foreground-event tracker that rejects a stale snapshot until the destination is observed or a newer matching focus-change event confirms another destination. Old window events cannot supersede newer ones. Home cancels queued challenges immediately; verified departures can also cancel a pending launch.
- LockScreenActivity checks that its challenge is still required before creating, resuming or accepting a new challenge. Obsolete launches cancel camera/biometric work and finish without redirecting the user away from their destination. Closing activities cannot accept a late unlock callback or start a new biometric prompt.
- Ten new unit regressions cover the reported Home/expiry sequence, repeated stale snapshots, delayed relocking, quick return, switching protected apps, out-of-order events, confirmed focus changes, screen-off mode and reset after disconnection.
- `:app:testDebugUnitTest :app:lintDebug` passed: **31 unit tests, zero failures/errors; lint zero errors, 154 warnings and 4 hints**. Debug Kotlin compilation and `git diff --check` passed. No APK was packaged and no device permissions were changed for this follow-up. The APK delivered earlier on October 21 predates this Home-navigation fix. Physical-device Home/gesture navigation still needs confirmation.
- Subsequently, at the owner's request, `:app:assembleDebug` passed and a new APK containing the Home-navigation fix was generated on October 21 at 12:05:37 local time. `apksigner verify` passed using the same Android Debug certificate. Artifact: `app/build/outputs/apk/debug/app-debug.apk`, 38,070,191 bytes, SHA-256 `E1D15AF8673DC90AF062E0B34EDDF924302D7B6889FD4ED17E7DA61177EC6C1E`. No installation or permission change was performed.
