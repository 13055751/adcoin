plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 可在 gradle.properties 或 -P 覆盖：
//   adcoin.apiBaseUrl        后端地址（默认模拟器访问宿主机 10.0.2.2:8787）
//   adcoin.adMode            mock | admob | pangle（默认 mock）
//   adcoin.admobAppId        真实 AdMob App ID
//   adcoin.admobRewardedUnit 真实 AdMob 激励视频广告位
//   adcoin.pangleAppId       真实 Pangle App ID
//   adcoin.pangleRewardedSlot 真实 Pangle 激励视频广告位
val apiBaseUrl: String = (project.findProperty("adcoin.apiBaseUrl") as String?) ?: "http://10.0.2.2:8787"
val adMode: String = (project.findProperty("adcoin.adMode") as String?) ?: "mock"
val admobAppId: String = (project.findProperty("adcoin.admobAppId") as String?) ?: "ca-app-pub-3940256099942544~3347511713"
val admobRewardedUnit: String = (project.findProperty("adcoin.admobRewardedUnit") as String?) ?: "ca-app-pub-3940256099942544/5224354917"
val pangleAppId: String = (project.findProperty("adcoin.pangleAppId") as String?) ?: ""
val pangleRewardedSlot: String = (project.findProperty("adcoin.pangleRewardedSlot") as String?) ?: ""

android {
    namespace = "com.adcoin.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.adcoin.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "AD_MODE", "\"$adMode\"")
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_REWARDED_UNIT", "\"$admobRewardedUnit\"")
        buildConfigField("String", "PANGLE_APP_ID", "\"$pangleAppId\"")
        buildConfigField("String", "PANGLE_REWARDED_SLOT", "\"$pangleRewardedSlot\"")
        manifestPlaceholders["admobAppId"] = admobAppId
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    // Pangle 依赖按需添加：PangleAdProvider 目前是注释骨架（无需 SDK 编译期依赖）。
    // 真实接入时取消注释并添加仓库依赖（repo.pangle.cn）：
    // implementation(libs.pangle.ads)
    debugImplementation(libs.androidx.ui.tooling)
}
