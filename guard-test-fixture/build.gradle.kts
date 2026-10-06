plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.instagram.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.instagram.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "test-fixture"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
