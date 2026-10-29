# Protected-app content flash — October 27, 2026

Historical implementation: the user subsequently approved replacing this cover/accessibility approach with the reference project's Usage Access monitor and single lock activity. See [the current implementation](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/REFERENCE_APPLOCK_2026-10-27.md>).

Reported device: Vivo Y33s, Android 13. The launch source (Home, Recents or notification) was not specified. No physical-device reproduction or frame measurement was performed by the assistant.

## Causes found in the source

- Raw application window-state events already trigger protection immediately, but added/active window events previously fell through to an 80 ms foreground check.
- `WindowManager.addView` schedules a draw; it does not synchronously present the cover. Starting an activity immediately afterwards can occupy the main thread before that cover renders.
- Repeated events can arrive after `addView` but before `isAttachedToWindow` becomes true, causing a second attempt to add the same window.
- Service readiness waited for authentication UI settings, even though protected-app settings were already available. Authentication-loading delays could therefore postpone protection itself.
- The lock activity inherited the general activity theme and transition animations.

## Changes

- Process added/active/focused window events immediately when their window actually has application focus. Only a focus-change event can override the foreground tracker's newer destination; adding an overlay cannot undo a Home action.
- Remove package-manager lookups from the normal protected-app event path.
- Keep one opaque, touch-blocking accessibility cover, including display insets/cutouts on supported SDKs, with window animations disabled.
- Start the authentication activity after the cover frame commits on hardware-accelerated SDK 29+ windows. Use the next animation callback after pre-draw for SDK 26–28/software rendering.
- Track window addition separately from attachment. Cancel pending listeners and obsolete frame callbacks on Home, authentication, replacement, service cleanup or window detachment.
- Retain the one-second watchdog. A cover that never draws returns the user Home; activity launch failures keep the existing recovery path. If adding the accessibility cover itself throws, the service logs that failure and falls back to its authentication activity.
- Give LockScreenActivity an explicit opaque theme and launch without transition animations. Keep the cover until that activity acknowledges its committed frame.
- Begin protection as soon as protection settings are available. UnlockScreen continues to disable credential input until its separate authentication settings load.

SDK declarations remain minimum 26, compile/target 36. No permissions or dependencies were added.

## Verification

Seven new pure unit regressions cover draw-before-launch ordering, Home cancellation, rapid app replacement, hide/show generations, already-drawn handoff, authentication/disabling cancellation and callback reentrancy. The existing ten foreground/Home regressions and ten relock-policy regressions remain.

Final verification completed successfully with the project Gradle 8.13 wrapper:

```text
gradlew.bat --no-daemon :app:testDebugUnitTest :app:compileReleaseKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug
```

- Debug, release and Android-test Kotlin compilation passed.
- All **38 unit tests** passed, with zero failures/errors, including the existing Home/relock regressions and the seven new cover regressions.
- Lint passed with zero errors, 161 warnings and 4 hints. No lint findings target the changed service/cover/theme code.
- `git diff --check` passed.

No APK was packaged or installed and no phone permissions were changed for this follow-up. The previously delivered APK predates this fix.

## Platform limit and device confirmation

This removes avoidable work after detection. It does **not** provide a supported interception point before another app starts drawing. Android accessibility reports changes in already-changing windows. A cover frame-commit callback also confirms submission to the swap chain, not the exact display-presentation time. A universal zero-exposed-frame guarantee cannot be made for this architecture.

References: [accessibility window events](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent), [frame-commit callback semantics](https://developer.android.com/reference/android/view/ViewTreeObserver#registerFrameCommitCallback(java.lang.Runnable)), [accessibility overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY).

On the Vivo Y33s, verify cold/warm launches from Home, Recents and notifications; PIN and biometric cancellation; rapid app switching; screen-off/rotation; and every selected relock mode. Measure visible frames on that device before treating the reported flash as resolved in practice.
