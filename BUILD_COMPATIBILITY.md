# Build compatibility — October 23, 2026

The project uses the owner's AGP 8.13.2 / Gradle 8.13 setup. The previous AGP 9 configuration relied on defaults and Kotlin support that are not present in AGP 8.

| Setting | Current value |
| --- | --- |
| Android Gradle Plugin | 8.13.2 |
| Gradle wrapper | 8.13, with the official binary-distribution SHA-256 |
| Gradle JVM | 21, matching the existing daemon JVM configuration |
| Kotlin Android / Compose compiler plugins | 2.2.10 |
| Java / Kotlin bytecode target | 11 |
| Compile / target SDK | 36 / 36 |
| Minimum SDK | 26 |
| AndroidX Core | 1.17.0 |
| AndroidX Lifecycle | 2.10.0 |

- Explicitly enabled `android.useAndroidX=true`. AGP 8 defaults this property to false; AGP 9 defaults it to true.
- Applied the Kotlin Android plugin, kept its version aligned with the Compose compiler plugin, and explicitly matched Java/Kotlin bytecode targets.
- Replaced the AGP 9 release optimization block with `isMinifyEnabled=false`, preserving disabled minification.
- Lowered compile/target SDK from 37.1/37 to 36/36 for AGP 8 compatibility. The minimum SDK remains 26.
- Lowered Core from 1.19.1 (requires SDK 37) to 1.17.0. Lowered Lifecycle from 2.11.0 because its Compose artifacts require SDK 37 and AGP 9.1. Declared the app's Lifecycle Compose dependencies explicitly with the same version.
- Retained the other dependency versions; dependency metadata checks determine compatibility without disabling validation or forcing incompatible transitive versions.
- Corrected the Gradle wrapper distribution checksum to match 8.13, so a fresh download can pass verification.

References: [AndroidX configuration defaults](https://developer.android.com/jetpack/androidx), [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin), [official Gradle checksums](https://gradle.org/release-checksums/).

## Verification

The project wrapper completed the following checks successfully with Gradle 8.13:

```text
gradlew.bat --no-daemon :app:checkDebugAarMetadata :app:testDebugUnitTest :app:compileReleaseKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug
```

- Debug, release and Android-test dependency metadata checks passed.
- Debug, release and Android-test Kotlin sources compiled.
- All 31 unit tests passed, with zero failures or errors.
- Android lint passed with zero errors, 161 warnings and 4 hints. Warnings include existing resource/style/deprecation advisories and suggestions to upgrade the deliberately older AGP, SDK and dependency versions; they are not suppressed.
- `git diff --check` passed.

No APK was packaged or installed, no device tests were run, and no device permissions were changed. The previously delivered APK remains the October 21 build. The configuration checks do not replace physical-device verification.

## Vault renderer follow-up — October 29, 2026

Added matching Media3 ExoPlayer and Media3 UI Compose **1.10.0** dependencies for embedded TextureView video output. SDK levels and build tools remain as declared above. The selected UI artifact's local AAR metadata requires compile SDK 36. Its Compose BOM dependency is 2024.12.01, older than the project's existing 2026.02.01 BOM. Version 1.11.1 was replaced because it would raise the Compose BOM to 2026.05.00 and affect unrelated UI.

Earlier checks with 1.11.1 passed debug/release/Android-test Kotlin compilation and 41 unit tests. These results do not verify the final 1.10.0 dependency selection. The owner requested no further builds on October 29; final compilation, tests, lint and device playback verification are left to the owner. Source/diff checks passed, the required Compose renderer APIs exist in the selected artifact, and no Gradle process remained running when checked. No APK was packaged or installed for this follow-up. Owner-run commands are recorded in `IMPLEMENTATION_CHECKLIST.md`.
