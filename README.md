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
- Android-safe HTTPS-only network configuration
- About/Credit screen: **Developed by Humayun Shariar Himu**

## 🧭 Design goal

The app is intentionally not a second, incomplete implementation of the Vercel dashboard. Vercel owns the dashboard experience and account permissions, so the Android app provides a mobile application shell around the official dashboard. This keeps account-specific navigation and available Vercel features aligned with the user's own Vercel account.

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

GitHub Actions builds and verifies the APK automatically on pushes to `main`.

## 📦 Project structure

```
app/
  src/main/
    java/com/himu/vercelapp/
      MainActivity.kt
    res/
      drawable/
      mipmap-anydpi-v26/
      values/
.github/workflows/
  android.yml
```

## 🔐 Security notes

- Dashboard traffic is HTTPS.
- JavaScript and DOM storage are enabled because the Vercel Dashboard requires them.
- File/content access from arbitrary local paths is disabled.
- Vercel authentication remains under Vercel's own login/session flow; the app does not ask users to paste a personal Vercel API token.

## 👤 Credit

**Developed by Humayun Shariar Himu**

This project is an independent Android client shell and is not presented as an official Vercel-maintained application.

## 📄 License

The repository's source code is provided for the project owner and development workflow. Vercel names, trademarks, logos, and dashboard content remain the property of their respective owners.
