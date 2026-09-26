package com.adcoin.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.adcoin.app.data.Session
import com.adcoin.app.data.SessionStore

/** 会话路由：未登录 → 登录页；已登录 → 主界面。 */
@Composable
fun AppRoot() {
    val session by SessionStore.session.collectAsState(initial = null)
    val current: Session? = session
    if (current == null) {
        LoginScreen()
    } else {
        MainScreen(current)
    }
}
