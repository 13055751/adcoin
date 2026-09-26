package com.adcoin.app.ad

import android.app.Activity

/**
 * 广告 Provider 抽象：看一个激励视频广告，完成后回调 transactionId + adUnitId。
 * App 拿到后上报后端 /api/ad/claim（后端验真后发币）。
 */
interface AdProvider {

    /** 平台标识：mock | admob | pangle */
    val id: String

    val displayName: String

    /** 是否已配置真实广告位（mock 恒 true）。 */
    fun isConfigured(): Boolean

    /**
     * 展示激励视频广告。
     * @param onReward 广告完整观看后回调（UI 线程）
     * @param onError 失败回调（原因文本）
     */
    fun showRewarded(
        activity: Activity,
        onReward: (transactionId: String, adUnitId: String) -> Unit,
        onError: (String) -> Unit,
    )
}
