# Vercel for Android

**A mobile-friendly Android shell for the official Vercel Dashboard**

[![Android CI](https://github.com/MyselfHumayunShariarHimu/vercel/actions/workflows/android.yml/badge.svg)](https://github.com/MyselfHumayunShariarHimu/vercel/actions/workflows/android.yml)
[![Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Developed by Humayun Shariar Himu**

[⬇ Direct APK download](https://raw.githubusercontent.com/MyselfHumayunShariarHimu/vercel/main/downloads/Vercel-Android-debug.apk) · [Releases](https://github.com/MyselfHumayunShariarHimu/vercel/releases) · [Report a bug](https://github.com/MyselfHumayunShariarHimu/vercel/issues)

---

## Overview

Vercel for Android provides a native Android app shell around the official [Vercel Dashboard](https://vercel.com/dashboard). Sign in through Vercel to access the dashboard associated with your own account, teams, projects, and permissions.

This project intentionally uses the official dashboard rather than presenting a partial, separately implemented dashboard. Vercel remains responsible for its web experience, authentication, data, and access controls.

> **Status:** Independent WebView-based Android client shell. It is not developed, maintained, or officially endorsed by Vercel.

## Features

- Official Vercel Dashboard access and Vercel-managed sign-in
- Persistent web cookies/session for normal dashboard navigation
- Native Android toolbar with Back, Forward, Refresh, and About
- Android system Back and in-dashboard history navigation
- Pull-to-refresh and page-loading progress
- Main-page network error state with Retry
- Android file picker bridge for web file chooser requests
- HTTPS navigation, Safe Browsing, and strict TLS certificate handling
- Mixed-content blocking and disabled WebView local-file access
- Responsive orientation and keyboard resize support
- Monochrome triangle launcher icon
- About screen credit: **Developed by Humayun Shariar Himu**

Dashboard functionality depends on Vercel's current website, account permissions, Android System WebView, and device behavior.

## Download and install

### Direct APK from this repository

GitHub Actions is configured to build a debug APK and commit it to:

**downloads/Vercel-Android-debug.apk**

[**Download Vercel-Android-debug.apk**](https://raw.githubusercontent.com/MyselfHumayunShariarHimu/vercel/main/downloads/Vercel-Android-debug.apk)

If the file is not available yet, check the [Android Actions workflow](https://github.com/MyselfHumayunShariarHimu/vercel/actions/workflows/android.yml) for a successful build and revisit the link. The repository APK is refreshed after a successful push-triggered build on main. Pull-request and manually dispatched builds upload a temporary workflow artifact instead.

### Install on Android

1. Download the APK to your Android device.
2. Open the downloaded file.
3. If prompted, allow installation from that source in Android settings.
4. Complete installation and open the app.
5. Sign in only through the Vercel-hosted login page.

Android may show a warning because this is a debug build. Install only if you trust the source and have reviewed the project.

## Build from source

### Requirements

- Android Studio with Android SDK Platform 35
- JDK 17
- Gradle 8.10

Build from the repository root:

    gradle assembleDebug

The APK is generated at:

    app/build/outputs/apk/debug/app-debug.apk

Install to a connected Android device using Android Debug Bridge:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

You can also open the project in Android Studio and build or run it from the IDE.

## Automated builds and releases

Workflow: [.github/workflows/android.yml](.github/workflows/android.yml)

- Pushes to main build and verify the debug APK.
- Successful push builds copy the APK to downloads/Vercel-Android-debug.apk and commit it to the repository for direct download (the APK is explicitly unignored in .gitignore).
- Pull requests build the app and upload a temporary workflow artifact.
- Workflow artifacts are retained for 30 days.
- Push a version tag such as v1.2.1 to build the APK and attach it to a GitHub Release.

View [workflow runs](https://github.com/MyselfHumayunShariarHimu/vercel/actions).

**Release status:** The automated APK is a debug/testing build, not a production-signed release or a Play Store-ready artifact. Production distribution requires a release build, protected signing credentials, and device testing. Never commit keystores, passwords, API tokens, or other secrets to the repository.

## Project structure

    .
    ├── .github/workflows/android.yml
    ├── app/
    │   ├── build.gradle.kts
    │   └── src/main/
    │       ├── AndroidManifest.xml
    │       ├── java/com/himu/vercelapp/
    │       │   ├── MainActivity.kt
    │       │   ├── TokenStore.kt
    │       │   └── VercelApi.kt
    │       └── res/
    ├── downloads/                 # CI-generated APK
    ├── build.gradle.kts
    ├── gradle.properties
    ├── settings.gradle.kts
    ├── LICENSE
    └── README.md

MainActivity.kt contains the active WebView app shell. TokenStore.kt and VercelApi.kt are prototype/legacy utilities and are not connected to the active dashboard interface.

## Security and privacy

- Dashboard traffic uses HTTPS and authentication is handled by Vercel.
- The current app shell does not ask users to paste a personal Vercel API token.
- JavaScript and DOM storage are enabled because the dashboard requires browser capabilities.
- Local file access and file-URL cross-access are disabled.
- Mixed HTTP/HTTPS content is blocked; plain HTTP navigation is upgraded to HTTPS.
- TLS certificate errors are cancelled; the app does not offer an insecure bypass.
- Unknown URL schemes are blocked; tel: and mailto: links are handed to Android.
- WebView debugging is disabled.

Keep Android and Android System WebView updated. Review the source and test sign-in and session behavior on your own device.

## Compatibility and known limitations

The Vercel Dashboard may change independently of this Android client. Browser-dependent flows—including OAuth redirects, pop-ups, uploads/downloads, deployment actions, or team administration—may behave differently across devices and WebView versions.

Before wider distribution, validate sign-in/sign-out, session restoration, navigation, project and deployment actions, file workflows, external links, team roles, orientation, keyboard behavior, poor connectivity, and process recreation.

This app does not bypass Vercel permissions, implement Vercel's backend, or guarantee that every dashboard feature works in WebView. The API client and encrypted token helper are not integrated into the current UI.

## Contributing and issue reports

Please use [GitHub Issues](https://github.com/MyselfHumayunShariarHimu/vercel/issues) to report bugs or suggest focused improvements. Include Android and WebView versions, app version, reproduction steps, and a sanitized error description. Never include passwords, session cookies, access tokens, or private project data.

## Credits and trademark

**Developed by Humayun Shariar Himu.**

This is an independent client project and does not claim affiliation with or endorsement by Vercel. Vercel names, trademarks, logos, and dashboard content belong to their respective owners. Confirm applicable trademark and distribution requirements before public release.

## License

See [LICENSE](LICENSE) for the source-code license. Third-party names, trademarks, and services remain subject to their respective owners' terms.
