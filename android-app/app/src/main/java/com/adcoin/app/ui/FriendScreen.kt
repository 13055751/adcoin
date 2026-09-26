package com.adcoin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adcoin.app.data.ApiClient
import com.adcoin.app.data.FriendItem
import com.adcoin.app.data.SearchResult
import com.adcoin.app.data.Session
import kotlinx.coroutines.launch

/** 好友：搜索添加 / 待处理请求 / 好友列表。 */
@Composable
fun FriendScreen(session: Session) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val auth = ApiClient.bearer(session.token)

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var friends by remember { mutableStateOf<List<FriendItem>>(emptyList()) }
    var pending by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var transferTarget by remember { mutableStateOf<FriendItem?>(null) }
    var transferAmount by remember { mutableStateOf("") }
    var transferring by remember { mutableStateOf(false) }

    suspend fun loadAll() {
        try {
            val f = ApiClient.api.friend(auth, "list", emptyMap())
            friends = f.friends ?: emptyList()
            val p = ApiClient.api.friend(auth, "pending", emptyMap())
            pending = p.requests ?: emptyList()
        } catch (e: Exception) {
            snackbar.showSnackbar("加载失败: ${e.message}")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(session.token) { loadAll() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
            // ---- 搜索添加 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索用户名添加好友") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = query.isNotBlank() && !searching,
                    onClick = {
                        searching = true
                        scope.launch {
                            try {
                                val r = ApiClient.api.search(auth, query.trim())
                                results = r.results ?: emptyList()
                            } catch (e: Exception) {
                                snackbar.showSnackbar("搜索失败: ${e.message}")
                            } finally {
                                searching = false
                            }
                        }
                    },
                ) { Text("搜索") }
            }
            if (searching) {
                Spacer(Modifier.height(4.dp))
                CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
            }

            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                // 搜索结果
                if (results.isNotEmpty()) {
                    item { SectionTitle("搜索结果") }
                    items(results, key = { it.appUserId ?: it.username ?: it.playerName ?: "r" }) { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.username ?: "?", fontWeight = FontWeight.Medium)
                                Text(
                                    "游戏账号: ${r.playerName ?: "未知"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val res = ApiClient.api.friend(auth, "request", mapOf("otherAppUserId" to (r.appUserId ?: "")))
                                    snackbar.showSnackbar(
                                        if (res.ok) "已发送好友请求"
                                        else res.error ?: "发送失败"
                                    )
                                }
                            }) { Text("加好友") }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }

                // 待处理请求
                if (pending.isNotEmpty()) {
                    item { SectionTitle("待处理请求（${pending.size}）") }
                    items(pending, key = { it.appUserId ?: it.name ?: "p" }) { p ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name ?: p.username ?: "?", fontWeight = FontWeight.Medium)
                                    Text("想加你为好友", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        val r = ApiClient.api.friend(auth, "accept", mapOf("otherAppUserId" to (p.appUserId ?: "")))
                                        if (r.ok) { snackbar.showSnackbar("已接受"); loadAll() }
                                        else snackbar.showSnackbar(r.error ?: "操作失败")
                                    }
                                }) { Text("接受") }
                                TextButton(onClick = {
                                    scope.launch {
                                        ApiClient.api.friend(auth, "reject", mapOf("otherAppUserId" to (p.appUserId ?: "")))
                                        loadAll()
                                    }
                                }) { Text("拒绝") }
                            }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }

                // 好友列表
                item { SectionTitle("我的好友（${friends.size}）") }
                if (friends.isEmpty() && !loading) {
                    item { Text("还没有好友，用上方搜索添加吧。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(friends, key = { it.uuid ?: it.name ?: "f" }) { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { transferTarget = f }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            if (f.online == true) "🟢 ${f.name ?: "?"}" else "⚪ ${f.name ?: "?"}",
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (friends.isNotEmpty()) {
                    item { Text("点击好友可转账", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    // ---- 转账对话框 ----
    transferTarget?.let { target ->
        TransferDialog(
            target = target,
            onDismiss = { transferTarget = null },
            onTransfer = { amount, clientTxId ->
                scope.launch {
                    val res = ApiClient.api.transfer(
                        auth,
                        mapOf(
                            "toAppUserId" to (target.appUserId ?: ""),
                            "amount" to amount.toDoubleOrNull()?.let { it },
                            "clientTxId" to clientTxId,
                        ),
                    )
                    if (res.ok) {
                        snackbar.showSnackbar("已转出 ${formatNumber(amount.toDouble())} adcoins 给 ${target.name}")
                        transferTarget = null
                    } else {
                        snackbar.showSnackbar(res.error ?: "转账失败")
                    }
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

/** 好友转账对话框（App 端） */@Composable
private fun TransferDialog(
    target: FriendItem,
    onDismiss: () -> Unit,
    onTransfer: (amount: String, clientTxId: String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("转账给 ${target.name ?: "?"}") },
        text = {
            Column {
                Text("从你的 adcoins 余额转出：")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("金额") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = { onTransfer(amount, "c-${System.currentTimeMillis()}") },
            ) { Text("转账") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun formatNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
