package com.adcoin.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adcoin.app.BuildConfig
import com.adcoin.app.data.ApiClient
import com.adcoin.app.data.Session
import com.adcoin.app.data.SessionStore
import kotlinx.coroutines.launch

/** 我的：账号信息、广告模式、构建配置、退出登录。未登录时提供登录入口。 */
@Composable
fun ProfileScreen(session: Session?, onLoginRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
            Text("我的", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (session == null) {
                        InfoRow("登录状态", "未登录（演示模式）")
                    } else {
                        InfoRow("用户名", session.username)
                        InfoRow("App ID", session.appUserId)
                        InfoRow("绑定账号", session.linkedPlayerName ?: "未绑定")
                    }
                    InfoRow("后端地址", BuildConfig.API_BASE_URL)
                    InfoRow("广告模式", BuildConfig.AD_MODE)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (session == null) {
                Button(onClick = onLoginRequest, modifier = Modifier.fillMaxWidth()) {
                    Text("登录 / 注册")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "未部署后端也能用演示模式浏览；登录后可用真实余额、好友与转账。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedButton(
                    onClick = {
                        val s = session
                        scope.launch {
                            val r = try {
                                ApiClient.api.unbind(ApiClient.bearer(s.token))
                            } catch (e: Exception) {
                                snackbar.showSnackbar("解绑失败: ${e.message}")
                                return@launch
                            }
                            SessionStore.updateLinked(null)
                            snackbar.showSnackbar(if (r.ok) "已解绑" else (r.error ?: "解绑失败"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("解绑游戏账号") }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { scope.launch { SessionStore.clear() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("退出登录") }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
