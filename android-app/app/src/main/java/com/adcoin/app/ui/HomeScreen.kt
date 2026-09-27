package com.adcoin.app.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adcoin.app.ad.AdManager
import com.adcoin.app.data.ApiClient
import com.adcoin.app.data.Session
import com.adcoin.app.data.SessionStore
import kotlinx.coroutines.launch

/**
 * 首页：余额 + 看广告赚币 + 绑定游戏账号。
 * session 为 null 时为游客模式：显示演示数据，操作引导去登录。
 */
@Composable
fun HomeScreen(session: Session?, onLoginRequest: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val provider = remember { AdManager.provider() }
    val guest = session == null

    var balance by remember { mutableStateOf(if (guest) DemoData.BALANCE else null as Double?) }
    var linkedName by remember { mutableStateOf(if (guest) DemoData.LINKED_NAME else session?.linkedPlayerName) }
    var refreshing by remember { mutableStateOf(false) }
    var watching by remember { mutableStateOf(false) }
    var showBindDialog by remember { mutableStateOf(false) }
    var bindCode by remember { mutableStateOf("") }
    var binding by remember { mutableStateOf(false) }

    suspend fun refresh() {
        val s = session ?: return
        refreshing = true
        try {
            val me = ApiClient.api.me(ApiClient.bearer(s.token))
            balance = me.balance
            if (me.linked) {
                val name = me.user?.linkedPlayerName
                linkedName = name
                if (name != null) SessionStore.updateLinked(name)
            }
        } catch (e: Exception) {
            snackbar.showSnackbar("网络错误: ${e.message}")
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(session?.token) { refresh() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
        ) {
            // ---- 余额卡 ----
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        if (guest) "我的 adcoins 余额（演示）" else "我的 adcoins 余额",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (refreshing && balance == null) {
                        CircularProgressIndicator(Modifier.width(28.dp).height(28.dp))
                    } else {
                        Text(
                            text = formatNumber(balance ?: 0.0),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (linkedName != null) "已绑定游戏账号：$linkedName"
                        else if (guest) "未登录 · 演示数据"
                        else "未绑定游戏账号",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---- 看广告 ----
            Button(
                onClick = {
                    if (guest) {
                        scope.launch { snackbar.showSnackbar("演示模式：登录后才能看广告赚币") }
                        onLoginRequest()
                        return@Button
                    }
                    val act = activity ?: return@Button
                    if (watching) return@Button
                    watching = true
                    provider.showRewarded(
                        act,
                        onReward = { txId, adUnit ->
                            val s = session
                            if (s == null) {
                                watching = false
                                return@showRewarded
                            }
                            scope.launch {
                                val res = try {
                                    ApiClient.api.claim(
                                        ApiClient.bearer(s.token),
                                        mapOf(
                                            "platform" to provider.id,
                                            "transactionId" to txId,
                                            "adUnitId" to adUnit,
                                        ),
                                    )
                                } catch (e: Exception) {
                                    watching = false
                                    snackbar.showSnackbar("发放失败: ${e.message}")
                                    return@launch
                                }
                                watching = false
                                if (res.ok) {
                                    snackbar.showSnackbar(
                                        if (res.duplicate == true) "该广告已结算过"
                                        else "看广告成功，奖励已发放！"
                                    )
                                    refresh()
                                } else {
                                    snackbar.showSnackbar("发放失败: ${res.error ?: "未知错误"}")
                                }
                            }
                        },
                        onError = { msg ->
                            watching = false
                            scope.launch { snackbar.showSnackbar(msg) }
                        },
                    )
                },
                enabled = !watching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (watching) {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("🎬 看广告 +赚币（${provider.displayName}）")
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- 绑定区 ----
            if (linkedName == null) {
                OutlinedButton(
                    onClick = {
                        if (guest) {
                            onLoginRequest()
                        } else {
                            showBindDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("🔗 绑定游戏账号")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "提示：先在游戏里输入 /adlink 获取 8 位绑定码，再在此绑定；未绑定无法领取奖励。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已绑定：$linkedName", fontWeight = FontWeight.Medium)
                    if (!guest) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val s = session ?: return@TextButton
                            scope.launch {
                                val r = try {
                                    ApiClient.api.unbind(ApiClient.bearer(s.token))
                                } catch (e: Exception) {
                                    snackbar.showSnackbar("解绑失败: ${e.message}")
                                    return@launch
                                }
                                if (r.ok) {
                                    linkedName = null
                                    SessionStore.updateLinked(null)
                                    snackbar.showSnackbar("已解绑")
                                } else {
                                    snackbar.showSnackbar(r.error ?: "解绑失败")
                                }
                            }
                        }) { Text("解绑") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (refreshing && balance != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }

    // ---- 绑定弹窗 ----
    if (showBindDialog) {
        AlertDialog(
            onDismissRequest = { if (!binding) showBindDialog = false },
            title = { Text("绑定游戏账号") },
            text = {
                Column {
                    Text("在游戏里输入 /adlink 获取绑定码，然后输入到下面：")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bindCode,
                        onValueChange = { bindCode = it.uppercase() },
                        label = { Text("绑定码") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = bindCode.length >= 4 && !binding,
                    onClick = {
                        val s = session
                        if (s == null) {
                            showBindDialog = false
                            onLoginRequest()
                            return@TextButton
                        }
                        binding = true
                        scope.launch {
                            val res = try {
                                ApiClient.api.bind(ApiClient.bearer(s.token), mapOf("code" to bindCode))
                            } catch (e: Exception) {
                                binding = false
                                snackbar.showSnackbar("绑定失败: ${e.message}")
                                return@launch
                            }
                            binding = false
                            if (res.ok) {
                                val name = res.user?.linkedPlayerName
                                linkedName = name
                                SessionStore.updateLinked(name)
                                showBindDialog = false
                                bindCode = ""
                                snackbar.showSnackbar("绑定成功：${name ?: "已绑定"}")
                            } else {
                                snackbar.showSnackbar("绑定失败: ${res.error ?: "未知错误"}")
                            }
                        }
                    },
                ) { Text("绑定") }
            },
            dismissButton = {
                TextButton(onClick = { showBindDialog = false }) { Text("取消") }
            },
        )
    }
}

private fun formatNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
