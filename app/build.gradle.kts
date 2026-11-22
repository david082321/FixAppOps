plugins {
    id("com.android.application")
}

android {
    namespace = "local.appops.compat"
    compileSdk = 37
    defaultConfig {
        applicationId = "local.appops.compat"
        minSdk = 34
        targetSdk = 37
        versionCode = 6
        versionName = "1.4"
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
    compileOnly("androidx.annotation:annotation:1.3.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.smali:dexlib2:2.5.2")
    implementation("com.google.guava:guava:27.1-android")
}
