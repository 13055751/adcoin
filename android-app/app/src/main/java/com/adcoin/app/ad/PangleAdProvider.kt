package com.adcoin.app.ad

import android.app.Activity
import com.adcoin.app.BuildConfig
import java.util.UUID

/**
 * Pangle（穿山甲）激励视频。
 *
 * 真实接入：取消注释并引用 SDK 类（国内版 com.pangle.cn:ads-sdk，包名 com.bytedance.sdk.openadsdk）：
 *   1) Application 初始化 TTAdSdk.init(...)
 *   2) 加载激励视频 TTAdNative#loadRewardVideoAd
 *   3) onRewardVerify 回调携带 transId → 上报后端；后端 AD_MODE=pangle 时调 Pangle 服务端验证
 *
 * 因 SDK 版本 API 时有变化，此处保留类型安全的外壳，接入时按官方文档调整。
 */
class PangleAdProvider : AdProvider {

    override val id: String = "pangle"
    override val displayName: String = "Pangle 穿山甲"

    override fun isConfigured(): Boolean =
        BuildConfig.PANGLE_APP_ID.isNotBlank() && BuildConfig.PANGLE_REWARDED_SLOT.isNotBlank()

    override fun showRewarded(
        activity: Activity,
        onReward: (String, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!isConfigured()) {
            onError("未配置 Pangle（-P adcoin.pangleAppId / adcoin.pangleRewardedSlot）")
            return
        }
        // ============ 真实接入骨架（按官方文档补全） ============
        // val ttAdNative = TTAdSdk.getAdManager().createAdNative(activity)
        // val adSlot = AdSlot.Builder().setCodeId(BuildConfig.PANGLE_REWARDED_SLOT).build()
        // ttAdNative.loadRewardVideoAd(adSlot, object : TTAdNative.RewardVideoAdListener {
        //     override fun onError(code: Int, message: String?) { onError("Pangle code=$code $message") }
        //     override fun onRewardVideoAdLoad(ad: TTAd) { }
        //     override fun onRewardVideoCached() { }
        //     override fun onRewardVideoAdShow() { }
        //     override fun onRewardVideoAdClose() { }
        //     override fun onRewardVerify(rewardVerify: RewardVerify?) {
        //         onReward(rewardVerify?.transId ?: "pangle-${UUID.randomUUID()}", BuildConfig.PANGLE_REWARDED_SLOT)
        //     }
        //     override fun onRewardVideoAdShowFail(errorCode: Int) { onError("展示失败 $errorCode") }
        // })
        onError("Pangle 真实接入见注释骨架（申请到穿山甲账号后按官方文档补全）")
    }
}
