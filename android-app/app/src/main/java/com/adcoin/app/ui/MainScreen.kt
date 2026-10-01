package com.adcoin.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adcoin.app.data.ApiClient
import com.adcoin.app.data.Session
import kotlinx.coroutines.launch

/**
 * 主界面：底部导航（首页 / 好友 / 排行 / 我的）。
 * session 为 null 时进入游客（演示数据）模式——未登录也能浏览全部页面。
 * pendingCount = 待处理好友请求数（好友 tab 角标，同时给首页铃铛当红点）。
 */
@Composable
fun MainScreen(session: Session?, onLoginRequest: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var pendingCount by remember(session?.token) {
        mutableIntStateOf(if (session == null) 2 else 0) // 游客演示角标
    }
    LaunchedEffect(session?.token) {
        val s = session ?: return@LaunchedEffect
        try {
            val r = ApiClient.api.friend(ApiClient.bearer(s.token), "pending", emptyMap<String, Any?>())
            pendingCount = r.requests?.size ?: 0
        } catch (_: Exception) {
            // 网络失败保持 0
        }
    }

    Scaffold(
        topBar = {
            if (session == null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "未登录 · 演示数据（功能需登录后可用）",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onLoginRequest) { Text("登录/注册") }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, null) },
                    label = { Text("首页") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.People, null) },
                    label = { Text("好友") },
                    badge = {
                        if (pendingCount > 0) {
                            Badge { Text(pendingCount.toString()) }
                        }
                    },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Leaderboard, null) },
                    label = { Text("排行") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Person, null) },
                    label = { Text("我的") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    session = session,
                    onLoginRequest = onLoginRequest,
                    onNavigate = { tab = it },
                    pendingCount = pendingCount,
                )
                1 -> FriendScreen(session, onLoginRequest)
                2 -> LeaderboardScreen(session)
                else -> ProfileScreen(session, onLoginRequest)
            }
        }
    }
}
