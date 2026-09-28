# Vercel — Android Dashboard

A focused Android shell for the Vercel Dashboard, designed to make the full Vercel web dashboard experience practical on Android while retaining native mobile controls around it.

## ✨ App experience

- **Vercel** launcher name and Vercel-style monochrome triangle icon
- Direct access to the official Vercel Dashboard
- Vercel account authentication handled by Vercel
- Persistent cookies/session for normal dashboard navigation
- Android **Back** and in-dashboard history navigation
- **Back / Forward / Refresh** toolbar controls
- **Pull-to-refresh**
- Visible page **loading progress**
- Network **error state + Retry**
- External deep-link handling
- Web file chooser support for dashboard workflows that request files
- HTTPS-only navigation policy, Safe Browsing, and strict TLS certificate handling
- Responsive orientation and Android keyboard resizing
- WebView local-file access disabled and mixed-content blocking
- About/Credit screen: **Developed by Humayun Shariar Himu**

## 🧭 Design goal

The app is intentionally not a second, incomplete implementation of the Vercel dashboard. Vercel owns the dashboard experience and account permissions, so the Android app provides a mobile application shell around the official dashboard. This keeps account-specific navigation and available Vercel features aligned with the user's own Vercel account. The app does not recreate Vercel's backend or bypass account/team permissions.

## 🛠️ Build

Requirements:

- Android Studio / Android SDK
- JDK 17
- Gradle 8.10
- Android API 35

Build the debug APK:

```bash
gradle assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions builds and verifies a debug APK automatically on pushes to `main`, and stores it as a workflow artifact for 30 days. To publish a downloadable APK on the repository's **Releases** page, create and push a version tag such as `v1.2.1`; the tagged workflow attaches `app-debug.apk` to the GitHub Release. The APK is a debug build for testing, not a signed production release. A public repository is required for unrestricted public downloads; repository visibility must be changed by an owner in GitHub Settings.

## 📦 Project structure

```
app/
  src/main/
    java/com/himu/vercelapp/
      MainActivity.kt
      TokenStore.kt (legacy encrypted-token helper; not used by Dashboard shell)
      VercelApi.kt (API client prototype; not wired into the current UI)
    res/
      drawable/
      mipmap-anydpi-v26/
      values/
.github/workflows/
  android.yml
```

## ⚠️ Compatibility and release status

This is an independent WebView-based client shell, not a Vercel-maintained native app. Dashboard pages, authentication redirects, pop-ups, file workflows, and some browser-dependent features may behave differently across Android System WebView versions. Validate these flows on physical devices and with accounts having different roles before production distribution. The API client and encrypted token helper in the source are prototypes and are not connected to the active dashboard UI. A successful debug build does not by itself establish complete feature compatibility or Play Store readiness.

## 🔐 Security notes

- Dashboard traffic is HTTPS.
- JavaScript and DOM storage are enabled because the Vercel Dashboard requires them.
- File/content access from arbitrary local paths is disabled; file access from file URLs and universal file access are also disabled.
- Mixed HTTP/HTTPS content is blocked; plain HTTP navigation is upgraded to HTTPS.
- TLS certificate errors are rejected; the app never offers an insecure certificate bypass.
- Only `tel:` and `mailto:` non-web schemes are handed to Android intents; unknown schemes are blocked.
- Vercel authentication remains under Vercel's own login/session flow; the app does not ask users to paste a personal Vercel API token.

## 👤 Credit

**Developed by Humayun Shariar Himu**

This project is an independent Android client shell and is not presented as an official Vercel-maintained application.

## 📄 License

The repository's source code is provided for the project owner and development workflow. Vercel names, trademarks, logos, and dashboard content remain the property of their respective owners.
