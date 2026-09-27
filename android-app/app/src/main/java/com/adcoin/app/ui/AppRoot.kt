package com.adcoin.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.adcoin.app.data.Session
import com.adcoin.app.data.SessionStore

/**
 * 会话路由：
 * - 已登录 → 主界面（真实数据）
 * - 未登录 → **仍然进入主界面**（游客/演示数据），可随时点“登录”进入登录页
 *
 * 这样即使没有部署后端，也能打开 App 查看界面与各页面。
 */
@Composable
fun AppRoot() {
    val session by SessionStore.session.collectAsState(initial = null)
    var showLogin by remember { mutableStateOf(false) }

    val current: Session? = session
    when {
        current != null -> MainScreen(session = current, onLoginRequest = { })
        showLogin -> LoginScreen(onBack = { showLogin = false })
        else -> MainScreen(session = null, onLoginRequest = { showLogin = true })
    }
}
