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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import com.adcoin.app.ad.AdManager
import com.adcoin.app.data.ApiClient
import com.adcoin.app.data.Session
import com.adcoin.app.data.SessionStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 动态流的一行。 */
private data class TxUi(val title: String, val time: String, val amount: String, val positive: Boolean?)

/**
 * 首页（方案 B · 清爽钱包 · M3）：
 * 大数字余额 + 趋势胶囊 + 药丸 CTA + 今日进度卡 + 快捷操作 + 时间线动态。
 * session 为 null 时为游客模式（演示数据）；state 只存真实值，演示值渲染时常量注入。
 */
@Composable
fun HomeScreen(
    session: Session?,
    onLoginRequest: () -> Unit,
    onNavigate: (Int) -> Unit,
    pendingCount: Int = 0,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val provider = remember { AdManager.provider() }
    val guest = session == null

    // ---- 真实数据状态（按 session token 重置）----
    var balance by remember(session?.token) { mutableStateOf<Double?>(null) }
    var linkedName by remember(session?.token) { mutableStateOf<String?>(null) }
    var dailyUsed by remember(session?.token) { mutableIntStateOf(0) }
    var dailyLimit by remember(session?.token) { mutableIntStateOf(20) }
    var adReward by remember(session?.token) { mutableStateOf(50.0) }
    var rank by remember(session?.token) { mutableStateOf<Int?>(null) }
    var friendCount by remember(session?.token) { mutableIntStateOf(0) }
    var friendOnline by remember(session?.token) { mutableIntStateOf(0) }
    var txs by remember(session?.token) { mutableStateOf<List<TxUi>?>(null) } // null=未加载
    var refreshing by remember { mutableStateOf(false) }
    var watching by remember { mutableStateOf(false) }
    var showBindDialog by remember { mutableStateOf(false) }
    var bindCode by remember { mutableStateOf("") }
    var binding by remember { mutableStateOf(false) }

    suspend fun refresh() {
        val s = session ?: return
        refreshing = true
        val auth = ApiClient.bearer(s.token)
        runCatching {
            val me = ApiClient.api.me(auth)
            balance = me.balance
            dailyUsed = me.dailyUsed
            dailyLimit = me.dailyLimit
            adReward = me.adReward
            if (me.linked) {
                val name = me.user?.linkedPlayerName
                linkedName = name
                if (name != null) SessionStore.updateLinked(name)
            }
        }
        runCatching {
            val lb = ApiClient.api.leaderboard(auth)
            val uuid = ApiClient.api.me(auth).user?.linkedPlayerUuid
            rank = lb.top?.indexOfFirst { it.uuid == uuid }?.takeIf { it >= 0 }?.plus(1)
        }
        runCatching {
            val fl = ApiClient.api.friend(auth, "list", emptyMap<String, Any?>())
            val fs = fl.friends ?: emptyList()
            friendCount = fs.size
            friendOnline = fs.count { it.online == true }
        }
        runCatching {
            val tr = ApiClient.api.transactions(auth)
            txs = (tr.entries ?: emptyList()).map { e ->
                if (e.adNetwork == "transfer") {
                    TxUi("收到好友转币", fmtTime(e.ts), "+" + fmtAmt(e.amount), true)
                } else {
                    TxUi("看广告获得奖励", fmtTime(e.ts), "+" + fmtAmt(e.amount), true)
                }
            }
        }
        refreshing = false
    }

    LaunchedEffect(session?.token) { refresh() }

    // ---- 渲染用值：游客=演示常量，登录=真实 state ----
    val vBalance = if (guest) DemoData.BALANCE else (balance ?: 0.0)
    val vLinked = if (guest) DemoData.LINKED_NAME else linkedName
    val vUsed = if (guest) DemoData.DAILY_USED else dailyUsed
    val vLimit = if (guest) DemoData.DAILY_LIMIT else dailyLimit
    val vReward = if (guest) DemoData.AD_REWARD else adReward
    val vRank = if (guest) DemoData.demoRank() else rank
    val vFriendCount = if (guest) DemoData.FRIENDS.size else friendCount
    val vFriendOnline = if (guest) DemoData.FRIENDS.count { it.online } else friendOnline
    val vTxs: List<TxUi> = if (guest) {
        DemoData.TRANSACTIONS.map { TxUi(it.title, it.time, it.amount, it.positive) }
    } else {
        txs ?: emptyList()
    }
    val remain = (vLimit - vUsed).coerceAtLeast(0)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // ---- 顶栏：标题 + 铃铛(角标) + 设置 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AdCoin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (guest) {
                    Text(
                        "登录/注册",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onLoginRequest).padding(8.dp),
                    )
                } else {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { onNavigate(1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Notifications, "通知",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        if (pendingCount > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onNavigate(3) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Settings, "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- 余额大数字 + 趋势胶囊 ----
            Text(
                "ADCOINS 余额",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    formatNumber(vBalance),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (guest) {
                    TrendPill("▲ 今日 +150")
                } else if (vUsed > 0) {
                    TrendPill("今日已看 $vUsed 次")
                }
            }
            Text(
                when {
                    guest -> "已绑定 ${DemoData.LINKED_NAME} · 演示数据"
                    vLinked != null -> "已绑定 $vLinked"
                    else -> "未绑定游戏账号"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // ---- CTA：药丸主按钮 + 排行榜方形按钮 ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                    val res = runCatching {
                                        ApiClient.api.claim(
                                            ApiClient.bearer(s.token),
                                            mapOf(
                                                "platform" to provider.id,
                                                "transactionId" to txId,
                                                "adUnitId" to adUnit,
                                            ),
                                        )
                                    }.getOrElse {
                                        watching = false
                                        snackbar.showSnackbar("发放失败: ${it.message}")
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
                        .weight(1f)
                        .height(54.dp),
                ) {
                    if (watching) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("看广告赚 ${fmtAmt(vReward)} adcoins", fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onNavigate(2) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Leaderboard, "排行榜",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- 今日进度卡 ----
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("今日看广告进度", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "$vUsed / $vLimit 次",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    LinearProgressIndicator(
                        progress = { if (vLimit <= 0) 0f else vUsed.toFloat() / vLimit },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (remain > 0) "再看 $remain 次可得 ${fmtAmt(remain * vReward)} adcoins"
                        else "今日次数已用完，明天再来",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            // ---- 快捷操作 ----
            Text("快捷操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard(
                    icon = Icons.Filled.People,
                    value = "$vFriendCount 位好友",
                    sub = "$vFriendOnline 人在线",
                    onClick = { if (guest) onLoginRequest() else onNavigate(1) },
                    modifier = Modifier.weight(1f),
                )
                QuickCard(
                    icon = Icons.Filled.Leaderboard,
                    value = if (vRank != null) "第 $vRank 名" else "未上榜",
                    sub = "adcoins 持有榜",
                    onClick = { onNavigate(2) },
                    modifier = Modifier.weight(1f),
                )
                QuickCard(
                    icon = Icons.Filled.Link,
                    value = if (vLinked != null) "已绑定" else "未绑定",
                    sub = vLinked ?: "去绑定",
                    onClick = {
                        when {
                            guest -> onLoginRequest()
                            vLinked != null -> scope.launch { snackbar.showSnackbar("已绑定 $vLinked，解绑去「我的」") }
                            else -> showBindDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(18.dp))

            // ---- 最近动态（时间线）----
            Text("最近动态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    if (!guest && txs == null && !refreshing) {
                        Text(
                            "加载中…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    } else if (vTxs.isEmpty()) {
                        Text(
                            "还没有动态，看一个广告试试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    } else {
                        vTxs.forEach { tx ->
                            TxRow(tx)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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
                            val res = runCatching {
                                ApiClient.api.bind(ApiClient.bearer(s.token), mapOf("code" to bindCode))
                            }.getOrElse {
                                binding = false
                                snackbar.showSnackbar("绑定失败: ${it.message}")
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

// ---------------------------------------------------------------- 小组件

/** 趋势胶囊（M3 secondaryContainer）。 */
@Composable
private fun TrendPill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 快捷操作卡（图标 + 主值 + 副文案）。 */
@Composable
private fun QuickCard(
    icon: ImageVector,
    value: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp)) {
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 时间线动态行：彩点 + 标题/时间 + 金额。 */
@Composable
private fun TxRow(tx: TxUi) {
    val amountColor = when (tx.positive) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(amountColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                tx.time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(tx.amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = amountColor)
    }
}

private fun fmtTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

private fun fmtAmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

private fun formatNumber(v: Double): String =
    if (v == v.toLong().toDouble()) String.format(Locale.getDefault(), "%,d", v.toLong())
    else String.format(Locale.getDefault(), "%,.2f", v)