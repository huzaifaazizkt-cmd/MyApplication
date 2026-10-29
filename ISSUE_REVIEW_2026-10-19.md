# AppLock issue review — October 19, 2026

This is the pre-fix review snapshot. Subsequent implementation is tracked in [October 19 fixes](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/ISSUE_FIXES_2026-10-19.md>); source line numbers below refer to the review snapshot.

Reviewed all 12 items in `C:/Users/786/Downloads/applock issue 1.txt` against the current workspace source. This is a source review, not a reproduction on the reported phones. No application source was changed, no build/install/test was run, and no device permissions were changed.

“Confirmed” below means the implementation contains the described behavior or a concrete defect. “Candidate” means the code supports an explanation, but the reported device-specific cause still needs runtime evidence. Older test results do not verify these new reports or establish which revision was installed on those phones.

## 1. Active notification missing after granting notifications

**Confirmed integration gap; exact Infinix cause unverified.** The permission-result callback only logs success. It does not ask the connected service to refresh its notification. The service publishes before notification permission is granted and when protection settings change; it does not explicitly republish on the grant. The builder also uses the default foreground-notification visibility policy. Failures from `startForeground()` are logged without a corresponding foreground-notification health state.

The permission prompt is triggered only from `onResume()` if the service-ready flag is already true. That also leaves a timing gap when service readiness arrives after resume.

Evidence: [permission callback and lifecycle](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/MainActivity.kt:36>), [notification publication](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/service/AppLockService.kt:221>).

Recommendation: explicitly refresh after grant, observe readiness while the activity is resumed, distinguish notification/channel restrictions from service readiness, and request immediate foreground-notification visibility where supported. A delayed notification and one that never appears are different failures. Inspect the drawer as well as the status-bar icon, channel settings, and service logs on the affected phone.

Android documents [notification permission behavior](https://developer.android.com/develop/ui/compose/notifications/notification-permission) and the [default foreground-notification deferral policy](https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder#setForegroundServiceBehavior(int)). Neither proves the particular Infinix cause without logs.

## 2. Loading screen before the external unlock screen

**Confirmed.** Every new unlock composition begins with `credentialsLoaded = false`, reads several DataStore flows sequentially, and displays a full-screen progress indicator until they finish. The service's privacy cover is a separate opaque white view; it must not be removed merely to conceal the delay. On a cold process or slower storage, initialization is more noticeable.

Evidence: [settings loading](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/UnlockScreen.kt:182>), [loading indicator](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/UnlockScreen.kt:348>), [privacy cover](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/service/PrivacyCurtain.kt:11>).

Recommendation: preload one coherent authentication/settings snapshot, invalidate it on changes, and display a stable unlock shell with input disabled until ready. Measure event-to-cover, activity-to-first-frame, and first-frame-to-input-ready separately.

## 3. Video controls overlap Unhide

**Confirmed layout risk; exact button identity/screen needs confirmation.** The video uses a programmatically created platform `MediaController`, while Unhide is a separate Compose bottom overlay. The player's fixed padding does not make both pieces share one layout. Android documents that these controls live in a [floating window](https://developer.android.com/reference/android/widget/MediaController), so they can overlap application controls.

The project does not call `setPrevNextListeners()`. Standard Next/Previous controls normally remain hidden until that call; the report may mean rewind/fast-forward or an OEM variant. Also, successful Unhide closes this preview. If the overlapping screen is literally opened after successful Unhide, identify whether it is AppLock or the Gallery/player.

Evidence: [platform controls](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/VaultVideoPlayer.kt:28>), [preview/player layout](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/VaultScreen.kt:615>).

Recommendation: keep playback controls and Unhide inside one measured layout with navigation-bar insets; avoid relying on fixed padding beneath a floating controller.

## 4. Wrong-pattern feedback delayed

**Confirmed delayed-feedback path; duration not measured.** The wrong-pattern message appears only after asynchronous credential verification completes. The verifier performs 120,000 PBKDF2 iterations, which can cost noticeably more on slower devices. Input remains drawable while an earlier verification is running, although a second submission is suppressed. The 1.5-second delay in `showPatternError()` clears an existing error; it does not delay initially showing it.

Evidence: [verification before feedback](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/UnlockScreen.kt:577>), [credential work](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/security/CredentialHash.kt:11>).

Recommendation: show a clear checking state, prevent overlapping gestures, avoid repeated storage initialization, and measure verification time. Do not restore plaintext comparison or weaken credential protection simply to hide the delay.

## 5. “Use Biometric” text at the bottom

**Confirmed intentional UI.** This is an explicit retry action, displayed whenever the saved biometric setting is enabled. An automatic prompt is separately attempted after a fixed 150 ms delay.

Evidence: [automatic attempt](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/UnlockScreen.kt:194>), [retry text](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/UnlockScreen.kt:632>).

If the intended design excludes this text, replace it with a suitable biometric icon/action while preserving a way to retry after cancellation. It is not an OEM-generated message.

## 6. No vibration on a wrong password

**Not explained by a missing vibration implementation.** `VIBRATE` is declared; the saved preference gates feedback; both pre-Android-12 and Android-12+ vibrator paths exist. Wrong-password vibration happens after verification. The default setting is off.

Evidence: [vibration implementation](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/UnlockScreen.kt:96>), [wrong-attempt feedback](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/UnlockScreen.kt:214>), [stored preference](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/data/DataStoreManager.kt:175>).

Check the persisted setting, foreground/lifecycle state, vibrator availability and exception log on each affected device. Android's [vibrator API](https://developer.android.com/reference/android/os/Vibrator) documents foreground requirements. No exact OEM cause is established here. Do not classify a silent phone as a missing manifest permission without checking these paths.

## 7. Splash and ad absent after removing AppLock from Recents

**Confirmed routing behavior, inconsistent with the reported startup expectation.** For an initialized app, the graph starts at `appList` behind an authentication gate. It skips `StartScreen`, where the splash/interstitial flow lives. Ad initialization for an initialized app waits for authentication. Removing a recent task also must not be assumed to equal process death.

Evidence: [initialized-app destination](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/navigation/NavGraph.kt:77>), [ad initialization gate](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/MainActivity.kt:84>), [splash ad route](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/StartScreen.kt:212>).

Recommendation: define cold/task relaunch separately from ordinary resume and restore the required startup presentation without putting network/ad availability in the external app-protection path. An ad impression still depends on configuration, eligibility and loading; it cannot be guaranteed on every launch.

## 8. Back displays another unlock screen after returning from a system screen

**Relevant lifecycle behavior confirmed; the exact Back sequence remains unverified.** `MainActivity.onStop()` clears AppLock's authentication except for configuration changes, including when leaving for a full-screen system screen. External app authorization is held separately by the service; unlocking a protected external app does not authenticate AppLock's own UI. The navigation graph also retains both a legacy `unlockScreen` destination and the newer global authentication overlay.

Evidence: [background locking](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/MainActivity.kt:211>), [external grant](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/LockScreenActivity.kt:51>), [legacy destination](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/navigation/NavGraph.kt:95>), [overlay](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/navigation/NavGraph.kt:328>).

Capture which system screen is involved, activity/task order, and navigation destination before/after Back. Consolidate authentication presentation and remove obsolete/restored unlock entries as appropriate. Do not globally remove background authentication or grant AppLock access merely because an external app was unlocked.

## 9. Gallery hide shows a card before the processing screen

**Two-stage flow confirmed; the particular card and two-second duration remain unverified.** On Android 11+, the app requests MediaStore write consent before beginning its own transfer overlay. `processingRequest` becomes true only after the consent result. There is no intentional two-second delay in this path. The system/provider consent UI can therefore be followed by AppLock's own processing UI.

Evidence: [write-consent request](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/VaultScreen.kt:249>), [consent result and transfer](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/VaultScreen.kt:152>), [processing overlay](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/VaultScreen.kt:432>).

Recommendation: model awaiting-consent and transferring as distinct stages, prevent duplicate submission while awaiting consent, and use one consistent app-owned progress presentation. Required system consent cannot simply be bypassed. Identify the first card before treating it as duplicate app UI.

## 10. Intruder defaults to 3 Attempts and capture varies by phone

**Default of 3 confirmed; cross-device camera cause unverified.** UI initialization and DataStore both default to three attempts. An explicitly saved Never value (`0`) is preserved and disables capture; enabling Intruder does not replace a saved zero. Therefore changing the default to Never would also mean enabling Intruder alone produces no photos until a threshold is selected.

Capture additionally requires camera permission, a resumed activity and an available front camera. Pending capture is cancelled on stop; saved observation delays are also honored. Camera/provider failures are logged rather than reported as an actionable capture status.

Evidence: [attempt defaults and zero handling](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/data/DataStoreManager.kt:210>), [UI default](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/IntruderScreen.kt:152>), [camera conditions/errors](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/IntruderCamera.kt:35>).

Check thresholds 0/3/5/10, saved delay, lifecycle cancellation, permission state and camera callbacks. Supported Android 12+ devices also have a system-wide [camera privacy toggle](https://developer.android.com/about/versions/12/behavior-changes-all#mic-camera-toggles), separate from app permission. Confirm the desired default before changing this behavior.

## 11. Xiaomi shows “Enable AppLock in Accessibility Settings”

**Confirmed overly broad status message.** The condition is simply protection enabled plus `isConnected == false`. That flag is false during connection/settings loading and is also set false on settings-read failures. The UI does not distinguish those conditions from Accessibility actually being disabled. This message is not a direct test of Xiaomi popup permission.

Evidence: [warning condition](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/SettingsScreen.kt:219>), [readiness/retry state](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/service/AppLockService.kt:94>).

Recommendation: separate permission disabled, connecting, ready and settings unavailable states; keep OEM popup access separate. If Accessibility is actually disabled, the enable message is correct. Runtime service state is needed to classify the reported Xiaomi instance.

## 12. Samsung Android 16 shows inconsistent authentication screens/feedback

**Concrete shared defects and a plausible OEM interaction; exact Samsung trigger unverified.**

- The service handles raw window-state package names as foreground app changes. Its exclusions cover core System UI, keyboards and permission/install controllers. A transient biometric/system window owned by another non-excluded package can be treated as an unprotected app, clearing the target and finishing the lock activity. Returning to the protected app can relaunch it. Whether this is the actual Samsung event sequence requires window-event logs.
- Biometric auto-launch uses a fixed delay and silently returns when the activity is not resumed. It is not coordinated with an in-progress PIN/pattern submission. Retargeting recreates unlock content and schedules another automatic attempt.
- Pattern `onDragCancel` submits the partial pattern as a completed attempt. An interrupted gesture can therefore produce wrong-pattern feedback, vibration and an intruder attempt even though the user did not finish entering it. This defect is not Samsung-specific.

Evidence: [window filtering/app-switch handling](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/service/AppLockService.kt:118>), [unprotected-window dismissal](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/service/AppLockService.kt:158>), [prompt lifecycle check](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/FingerprintHelper.kt:19>), [cancelled gesture submission](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/UnlockScreen.kt:861>).

Recommendation: coordinate challenge, credential verification and biometric presentation as one lifecycle-aware flow; discard cancelled gestures; distinguish transient system/authentication windows from actual app departures using window evidence. Avoid guessing vendor package names or bypassing real app switches with an overly broad ignore rule.

## Suggested order and validation limits

1. Stabilize authentication/window transitions and cancelled gestures (12, 8); keep protection effective while changing the UI.
2. Repair notification refresh/status reporting (1, 11).
3. Remove avoidable initialization and feedback delays (2, 4), then verify vibration and intruder capture on affected phones (6, 10).
4. Fix video control ownership/layout and consent/progress presentation (3, 9).
5. Align biometric retry presentation, startup splash/ad routing and the Intruder default with the intended product behavior (5, 7, 10).

Necessary follow-up evidence is a matching source/APK revision and a short reproduction trace for the affected OEMs: timestamps, lifecycle state, window package/type, current target, prompt state, service/notification state and camera error codes. Do not log PINs, patterns, answers or private media contents. No new runtime claim is made by this review.
