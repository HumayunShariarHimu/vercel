plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace="com.himu.vercelapp"
 compileSdk=35
 defaultConfig {
  applicationId="com.himu.vercelapp"
  minSdk=26
  targetSdk=35
  versionCode=1
  versionName="1.0.0"
  buildConfigField("String","VERCEL_CLIENT_ID","\"CONFIGURE_IN_GITHUB_SECRETS\"")
 }
 buildFeatures { compose=true; buildConfig=true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.browser:browser:1.8.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
