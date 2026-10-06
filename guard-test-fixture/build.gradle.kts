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
    System.getenv("SCROLL_GUARD_SIGNING_STORE")?.let { pinnedStore ->
        signingConfigs.getByName("debug").storeFile = file(pinnedStore)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
