package com.adcoin.app.ad

import android.app.Activity
import android.app.AlertDialog
import java.util.UUID

/**
 * 模拟广告（联调/演示用）：弹窗"播放"，点按钮视为看完，生成 mock transactionId。
 * 配合后端 AD_MODE=mock 可全链路跑通，无需任何广告账号。
 */
class MockAdProvider : AdProvider {

    override val id: String = "mock"
    override val displayName: String = "模拟广告"

    override fun isConfigured(): Boolean = true

    override fun showRewarded(
        activity: Activity,
        onReward: (String, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        AlertDialog.Builder(activity)
            .setTitle("模拟广告")
            .setMessage("正在播放广告…\n（开发模式：点击\"看完了\"即可领取奖励）")
            .setCancelable(false)
            .setPositiveButton("看完了") { _, _ ->
                onReward("mock-" + UUID.randomUUID(), "mock-ad-unit")
            }
            .show()
    }
}
