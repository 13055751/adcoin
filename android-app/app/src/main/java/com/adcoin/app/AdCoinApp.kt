package com.adcoin.app

import android.app.Application
import android.content.Context
import com.adcoin.app.BuildConfig
import com.google.android.gms.ads.MobileAds

class AdCoinApp : Application() {

    companion object {
        lateinit var context: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext

        // AdMob 初始化（需真实 App ID；默认是官方测试 ID）
        if (BuildConfig.ADMOB_APP_ID.isNotBlank()) {
            MobileAds.initialize(this) { }
        }

        // Pangle 初始化（需真实 App ID；未配置则跳过）
        if (BuildConfig.PANGLE_APP_ID.isNotBlank()) {
            initPangle()
        }
    }

    private fun initPangle() {
        try {
            // 国内版 Pangle SDK；API 以官方文档为准
            // com.bytedance.sdk.openadsdk.TTAdSdk.init(this,
            //     com.bytedance.sdk.openadsdk.TTAdSdkConfig.Builder()
            //         .setAppId(BuildConfig.PANGLE_APP_ID)
            //         .build())
        } catch (e: Throwable) {
            android.util.Log.w("AdCoin", "Pangle 初始化失败（未配置或 SDK 缺失）: ${e.message}")
        }
    }
}
