# Lifetime Premium implementation — October 29, 2026

Implemented the owner's client-only, one-time purchase flow. No Gradle/build/compiler/package/install/device commands were run for this implementation. Compilation and Play license testing remain owner-run verification, not completed checks.

## Implemented behavior

- One app-owned `umRepository`, one Play Billing transport, one shared observable entitlement, and separate paywall state. Removed unused/duplicate `Billing`, `BillingManager` and `AppClass` implementations.
- Keeps the existing `lifetime` resource/product ID and Billing Library 9.1.0. Uses INAPP purchases, selects an eligible buy offer, passes its matching offer token, and never consumes the lifetime product. No dependency or SDK level change was needed for billing.
- Returning initialized users query Google Play on activity foreground entry. First-time setup does not make a startup billing query; PRO always checks ownership so a reinstall can recover an existing purchase.
- Entry checks have a bounded wait and a checking indicator. Store failures preserve cached Premium. Confirmed missing ownership removes access; transient failure is not interpreted as revocation. Authentication remains above the entry-check UI.
- PRO loads real store pricing, displays loading/processing/pending/error states, disables unavailable or repeated purchase requests, and offers Retry. The hard-coded price is removed.
- Restore is at the top start/left of PRO, with a rounded 12 dp app-blue background and white text. It queries ownership independently of product-price availability, handles no previous purchase/pending/failure, and displays a restored-success message.
- Purchases are confirmed through a fresh Play owned-purchase query, matching the configured product, PURCHASED state and non-empty token. Entitlement is persisted before acknowledgment. Fulfillment survives paywall disposal; acknowledgment retries have backoff and recover from owned-purchase queries after process restart. Store/token data is not logged.
- Successful purchases display Congratulations once for the originating PRO entry. Continue returns initialized users to the Apps/main tab; first-time buyers continue PIN/permission setup before main. The prior per-entry close guard remains in place for repeated close/Continue taps and outgoing callbacks.
- Pending purchases do not grant access. Completion can be recovered on foreground return; already-owned responses recover ownership instead of treating it as a new payment failure. Cancelled/failed checkout leaves a retryable paywall.
- Banner removal reacts to Premium changes and destroys the existing ad view through disposal. Startup and welcome interstitials check the same entitlement before loading/showing. Unknown/checking entitlement does not request an ad. The standalone unregistered `testactivity` prototype is not part of app navigation and was left unchanged.
- Unlimited photo/video hiding is removed from the Premium benefits UI; Intruder, No Ads and Priority Support remain. Vault storage behavior is unchanged.
- Intruder has a PRO badge and sends free users to PRO. Its restored/direct screen also has a feature gate. Actual attempt counting, enabling, permission request callbacks, camera startup, retry and image commit all enforce entitlement. Revocation cancels pending capture. Existing Intruder photos/preferences are retained; counters reset on entitlement changes.
- Terms and Privacy footer actions are wired. Dummy destinations remain at the owner's request; the Terms destination is `https://example.com/terms` until replaced.
- Added 23 billing messages in English and all ten translated language sets; Saudi Arabic inherits the Arabic translations.

## Source verification completed

- Parsed all 104 main resource XML files successfully. No duplicate per-locale strings and no unresolved base string references from main Kotlin sources.
- All 23 billing messages match across the eleven language resource sets.
- Inspected the cached Billing 9.1.0 classes for the one-time offer, rental, token, product query, purchase query and acknowledgment APIs. Checked the declared Activity Compose artifact contains `LocalActivity`.
- Checked changed Kotlin source delimiters and `git diff --check`. These are source checks, not compilation.
- Reviewed registration/navigation, purchase/restore ownership, repeated taps, paywall disposal/reopening, entry timeouts, cache errors, acknowledgment retries, revocation, late capture callbacks and onboarding routing.
- Added six billing policy/state unit cases. Extended the atomic Intruder instrumentation regression to preserve/restore Premium and added a free-user/revocation attempt-gating case. These tests have not been executed.

## Owner-run verification required

From `D:\Afaq'sSpace\Test apps\ApplockN\MyApplication2`:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:compileReleaseKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug
```

If an APK is needed for manual testing, the owner can run:

```powershell
.\gradlew.bat :app:assembleDebug
```

- [ ] Confirm the app/package and active `lifetime` non-consumable buy option in Play Console, eligible regions and actual pricing. Configure a license tester account for the same package. Source checks cannot confirm Console configuration.
- [ ] First install: no startup store query, normal welcome/language/onboarding/PIN/permissions flow, PRO loading/Retry, unpaid Intruder locked and no camera permission request.
- [ ] Purchase from Apps and Settings: one payment sheet for repeated taps, correct localized price, Congratulations, one Continue navigation to Apps/main, banner removed, Intruder usable.
- [ ] Purchase during onboarding: Congratulations then PIN/permissions, with Premium retained; main must remain inaccessible before setup completes.
- [ ] Restore after clear-data/reinstall and manual Restore with owned/no-owned/offline/pending results. Initial auto-detection of an owned purchase shows Premium active/Continue; a manual restore shows the restored-success message.
- [ ] Slow approved/declined pending payments, native checkout cancellation, interrupted connection, rotation, leaving/reopening PRO, process death before callback, after fulfillment and during acknowledgment retry.
- [ ] Cached Premium offline, first unpaid/offline entry, store query timeout, failed persistence, acknowledgment failure/recovery, and confirmed refund-with-revocation. A plain refund without revocation need not remove the store entitlement.
- [ ] Repeated PRO close/Continue, Back/Home/authentication during entry checking, first-time setup, and language changes. Authentication behavior was deliberately retained.
- [ ] Intruder access/capture/permission checks, cancellation on background/revocation and retained existing photos. Verify Vault import/playback, AppLock service/locked-app flow and other existing settings remain functional.

This implementation uses Google Play Billing ownership queries without a separate backend, as requested. Play Console configuration and license-test results are still required before a release claim can be made.
