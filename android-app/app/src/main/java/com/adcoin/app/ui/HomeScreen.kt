package com.adcoin.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adcoin.app.ad.AdManager
import com.adcoin.app.data.ApiClient
import com.adcoin.app.data.Session
import com.adcoin.app.data.SessionStore
import kotlinx.coroutines.launch

/**
 * 首页（方案 A · M3）：
 * 大标题 + primaryContainer 余额卡 + FilledButton 看广告 + tonal 功能入口 + 最近动态。
 * session 为 null 时为游客模式（演示数据）。
 */
@Composable
fun HomeScreen(session: Session?, onLoginRequest: () -> Unit, onNavigate: (Int) -> Unit) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // ---- 标题区（方案 A AppBar）----
            Text("AdCoin", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    linkedName != null -> "已绑定 · $linkedName"
                    guest -> "游客模式 · 演示数据"
                    else -> "未绑定游戏账号"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // ---- 余额卡（primaryContainer）----
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "ADCOINS 余额",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (refreshing && balance == null) {
                        CircularProgressIndicator(
                            Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Text(
                            formatNumber(balance ?: 0.0),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        "adcoins",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- 看广告（FilledButton，主 CTA）----
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
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (watching) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("看广告 · 赚 adcoins")
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- 功能入口（tonal 卡）----
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureTile(
                    icon = Icons.Filled.Link,
                    label = "绑定",
                    onClick = { if (guest) onLoginRequest() else showBindDialog = true },
                    modifier = Modifier.weight(1f),
                )
                FeatureTile(
                    icon = Icons.Filled.Groups,
                    label = "好友",
                    onClick = { if (guest) onLoginRequest() else onNavigate(1) },
                    modifier = Modifier.weight(1f),
                )
                FeatureTile(
                    icon = Icons.Filled.Leaderboard,
                    label = "排行",
                    onClick = { onNavigate(2) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))

            // ---- 最近动态 ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "最近动态",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (guest) {
                        ActivityRow(Icons.Filled.PlayArrow, "广告奖励", "+50", MaterialTheme.colorScheme.primary)
                        ActivityRow(Icons.Filled.Groups, "转出给 Alex", "-20", MaterialTheme.colorScheme.error)
                        ActivityRow(Icons.Filled.Link, "绑定 Steve", "✓", MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(
                            "暂无记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
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

/** M3 tonal 功能入口卡。 */
@Composable
private fun FeatureTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** M3 列表行（图标 tile + 文本 + 金额）。 */
@Composable
private fun ActivityRow(icon: ImageVector, text: String, amount: String, amountColor: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
        )
    }
}

private fun formatNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
