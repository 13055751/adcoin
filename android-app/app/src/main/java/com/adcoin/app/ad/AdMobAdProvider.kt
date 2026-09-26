package com.adcoin.app.ad

import android.app.Activity
import com.adcoin.app.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.UUID

/**
 * Google AdMob 激励视频。
 *
 * 注意：AdMob 的 SSV（服务端验证）由广告平台直接回调你后端配置的 URL，
 * App 侧只上报"本次观看"凭证；后端 AD_MODE=admob 时以 SSV 回调为准发币。
 */
class AdMobAdProvider : AdProvider {

    override val id: String = "admob"
    override val displayName: String = "AdMob"

    override fun isConfigured(): Boolean = BuildConfig.ADMOB_REWARDED_UNIT.isNotBlank()

    override fun showRewarded(
        activity: Activity,
        onReward: (String, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val adUnitId = BuildConfig.ADMOB_REWARDED_UNIT
        if (adUnitId.isBlank()) {
            onError("未配置 AdMob 广告位（-P adcoin.admobRewardedUnit）")
            return
        }
        RewardedAd.load(activity, adUnitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                onError("广告加载失败: ${error.message}")
            }

            override fun onAdLoaded(ad: RewardedAd) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        onError("广告展示失败: ${error.message}")
                    }
                }
                ad.show(activity) { _ ->
                    // AdMob 客户端拿不到交易号，SSV 靠广告平台回调后端；这里生成本地凭证
                    onReward("admob-" + UUID.randomUUID(), adUnitId)
                }
            }
        })
    }
}
