plugins {
    id("com.android.application")
}

android {
    namespace = "local.appops.compat"
    compileSdk = 37
    defaultConfig {
        applicationId = "local.appops.compat"
        minSdk = 36
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"
    }
    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
