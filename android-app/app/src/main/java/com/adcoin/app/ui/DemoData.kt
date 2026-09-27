package com.adcoin.app.ui

/**
 * 未登录（游客）模式下的演示数据。
 * 目的：没有部署后端时也能打开 App 预览各页面，而不是被登录页挡住。
 */
object DemoData {

    const val BALANCE = 1288.0
    const val LINKED_NAME = "Steve（演示）"

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

    const val ADS_WATCHED_TODAY = 3
}
