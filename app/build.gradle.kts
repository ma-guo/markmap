plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zuxing.markmap"
    compileSdk = 33

    signingConfigs {
        create("release") {
            storeFile = file("./keys/mark.keys")
            storePassword = "markmap"
            keyAlias = "mark"
            keyPassword = "markmap"
        }
    }

    defaultConfig {
        applicationId = "com.zuxing.markmap"
        minSdk = 28
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")

    implementation("com.baidu.lbsyun:BaiduMapSDK_Map:7.6.4")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Location:9.6.4")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Search:7.6.4")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Util:7.6.4")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    implementation("com.google.code.gson:gson:2.10.1")
}