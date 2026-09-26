package com.adcoin.app.ad

import com.adcoin.app.BuildConfig

/** 按构建配置选广告 Provider（-P adcoin.adMode=mock|admob|pangle）。 */
object AdManager {

    fun provider(): AdProvider = when (BuildConfig.AD_MODE.lowercase()) {
        "admob" -> AdMobAdProvider()
        "pangle" -> PangleAdProvider()
        else -> MockAdProvider()
    }
}
