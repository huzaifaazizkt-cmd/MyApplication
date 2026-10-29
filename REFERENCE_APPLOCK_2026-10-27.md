# CalculatorLock-based AppLock changes — October 27, 2026

Implemented the approved comparison plan in MyApplication2. `D:/Afaq'sSpace/Current Project/SparxSProj/CalculatorLock` was read as a reference and was not edited.

## Implementation checklist

- [x] Replace Accessibility monitoring with an ordinary, private `specialUse` foreground service using Usage Access.
- [x] Present the existing LockScreenActivity directly, without an intermediate white accessibility window. Retire PrivacyCurtain, DrawnCoverGate, ForegroundTracker and their obsolete tests/configuration.
- [x] Follow the reference's 350 ms monitoring cadence. Run UsageEvents queries on IO, deduplicate overlapping queries, preserve entries sharing a timestamp, and replay rapid switches for relock decisions while launching only for the final app.
- [x] Keep settings-based immediate/delayed relocking and screen-off mode. Convert event times to elapsed time for delayed batches; stale history cannot undo a newer authentication or explicit dismissal.
- [x] Stop polling on screen-off, resume after the device becomes interactive, and avoid showing the challenge over the device keyguard. Handle boot/update restart, disabled protection, missing permissions and failed launches.
- [x] Keep one pending challenge, bounded launch recovery, the existing secure activity/task, biometric ownership checks and the actual underlying app/deep-link return. Do not copy the reference's forced launcher fallback or fixed session defaults.
- [x] Keep the existing setup sequence: credential confirmation, the existing permission step, security question and app list. Replace only the required permission content. Password reset, vault and intruder flows retain their existing behaviour.
- [x] Require Usage Access and Display over other apps for monitoring. Remove the Accessibility service declaration and its setup requirement.
- [x] Keep notifications non-blocking. Keep camera/media grants specific to intruder/vault use. Show only missing mandatory permissions and only applicable OEM guidance; unknown vendor permission checks do not trap setup.
- [x] Replace QUERY_ALL_PACKAGES with launcher/home and specifically used settings-package queries. Do not add all-files access.
- [x] Extract app catalog, ViewModel, icon cache, filter and reusable row. Keep the app-list design/tabs/search, remove duplicate elevation/clipping, use stable package keys/content types and preserve scroll/search/tab state.
- [x] Deduplicate launcher results before label/icon work; load visible icons on IO with two workers and a bounded bitmap cache. Refresh metadata/icons for package, locale and default-launcher changes.
- [x] Keep pending app selection across permission/settings recreation, recheck installation before completing it, and prevent repeated concurrent selection writes.
- [x] Update permission/status/list messages in all eleven existing production resource sets (English plus ten translations). The Arabic regional qualifier inherits the Arabic messages.

## Main source locations

- [Monitor](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/service/AppLockService.kt>) and [UsageEvents reader](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/service/UsageForegroundReader.kt>).
- [Permission policy](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/service/AppLockPermissions.kt>) and [shared permission UI](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/PermissionRequiredDialog.kt>).
- [App catalog/icon cache](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/data/AppCatalog.kt>), [ViewModel](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/viewmodel/AppListViewModel.kt>) and [list UI](<D:/Afaq'sSpace/Test apps/ApplockN/MyApplication2/app/src/main/java/com/example/myapplication/Design/screens/AppListScreen.kt>).

## Permission behaviour

Usage Access and overlay access are the only standard blocking special-access steps. Verified Xiaomi popup restrictions are conditional. Notifications are optional on SDK 33+; a notification object/channel is still provided for the foreground service. Background/battery settings are optional recommendations with resolvable device-specific destinations. Camera is requested by intruder protection and media access by vault actions. Normal manifest permissions are not presented as runtime prompts.

Existing locked packages and credentials are retained. After updating from the Accessibility implementation, users must grant Usage Access before external app protection can resume. An enabled global protection setting with existing locked apps shows an actionable reminder until access is ready; no app is reported newly protected from a failed permission/write operation.

SDK and dependency declarations remain minimum SDK 26, compile/target SDK 36, AGP 8.13.2 and Gradle 8.13. No dependencies were added.

## Verification

Final verification passed with the project Gradle 8.13 wrapper:

```text
gradlew.bat --no-daemon :app:testDebugUnitTest :app:compileReleaseKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug
```

- Debug, release and Android-test Kotlin compilation passed.
- All **36 current unit tests** passed with zero failures/errors: relock policy 12, UsageEvents cursor 7, required-access policy 2, app-list filtering/search 4, credential hashing 3, safe media import 4, locale migration 3 and the existing example 1. Tests for the retired accessibility/cover classes were replaced by the Usage Access regressions.
- Lint passed with zero errors, 167 warnings and 3 hints. Existing resource/style/version/deprecation advisories remain. No StaticFieldLeak finding remains in the new ViewModel.
- Static checks parsed all 93 XML resources, confirmed translated-key coverage and base string references, and checked the merged manifest for a private specialUse service without Accessibility binding, QUERY_ALL_PACKAGES or MANAGE_EXTERNAL_STORAGE.
- `git diff --check` passed.

No APK was packaged or installed, and no phone permissions were changed. The previously delivered APK predates this migration.

## Device checks still required

- [ ] Vivo Y33s / Android 13: measure cold/warm launches from Home, Recents and notifications; confirm the extra white transition is gone.
- [ ] Repeated PIN/pattern/biometric unlocks, Home/Back/Recents, rapid switching, screen-off, process/service recreation and all selected relock delays.
- [ ] Permission grant/deny/revoke and return to setup; optional notification denial; applicable OEM background settings.
- [ ] App-list scroll frame times in a release/profileable build; large catalogs, search, toggles, rotation, navigation return, package changes and locale changes.
- [ ] Representative SDK 26/29/33/34/36 devices, especially OEM background/launch behaviour.

Usage Access detects activity changes after Android reports them and is polled every 350 ms. This architecture cannot guarantee that another app's first frame or Recents preview is never visible. Compilation and unit tests do not certify that visible timing or scroll performance has passed on the Vivo.
