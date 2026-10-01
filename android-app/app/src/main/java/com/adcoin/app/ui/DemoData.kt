package com.adcoin.app.ui

/**
 * 未登录（游客）模式下的演示数据。
 * 目的：没有部署后端时也能打开 App 预览各页面，而不是被登录页挡住。
 */
object DemoData {

    const val BALANCE = 1288.0
    const val LINKED_NAME = "Steve（演示）"
    const val DAILY_USED = 3
    const val DAILY_LIMIT = 20
    const val AD_REWARD = 50.0

    data class DemoFriend(val name: String, val online: Boolean, val balance: Double)

    val FRIENDS = listOf(
        DemoFriend("Alex", online = true, balance = 320.0),
        DemoFriend("Notch", online = false, balance = 1500.0),
        DemoFriend("Herobrine", online = true, balance = 66.0),
    )

    val LEADERBOARD = listOf(
        "Notch" to 1500.0,
        "Steve（演示）" to 1288.0,
        "Alex" to 320.0,
        "Herobrine" to 66.0,
    )

    /** 演示动态：标题、时间、金额文本、颜色（true=收入/绿 false=支出/红 null=中性）。 */
    data class DemoTx(val title: String, val time: String, val amount: String, val positive: Boolean?)

    val TRANSACTIONS = listOf(
        DemoTx("看广告获得奖励", "09:20", "+50", true),
        DemoTx("转出给 Alex", "09:15", "-20", false),
        DemoTx("看广告获得奖励", "昨天 21:04", "+50", true),
        DemoTx("绑定游戏账号 Steve", "昨天 20:58", "✓", null),
    )

    /** 演示玩家在排行榜中的名次（1-based）。 */
    fun demoRank(): Int = LEADERBOARD.indexOfFirst { it.first == "Steve（演示）" } + 1
}
