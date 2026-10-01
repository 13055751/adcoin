package com.adcoin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
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

/**
 * 好友：搜索添加 / 待处理请求 / 好友列表 / 点击转账。
 * session 为 null 时为游客模式：展示演示好友，操作引导去登录。
 */
@Composable
fun FriendScreen(session: Session?, onLoginRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val guest = session == null
    val auth = session?.let { ApiClient.bearer(it.token) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var friends by remember {
        mutableStateOf<List<FriendItem>>(
            if (guest) {
                DemoData.FRIENDS.map {
                    FriendItem(uuid = "demo-" + it.name, name = it.name, online = it.online, appUserId = "demo-" + it.name)
                }
            } else {
                emptyList<FriendItem>()
            }
        )
    }
    var pending by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var transferTarget by remember { mutableStateOf<FriendItem?>(null) }

    suspend fun loadAll() {
        val token = auth ?: return
        loading = true
        try {
            val f = ApiClient.api.friend(token, "list", emptyMap<String, Any?>())
            friends = f.friends ?: emptyList<FriendItem>()
            val p = ApiClient.api.friend(token, "pending", emptyMap<String, Any?>())
            pending = p.requests ?: emptyList<SearchResult>()
        } catch (e: Exception) {
            snackbar.showSnackbar("加载失败: ${e.message}")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(session?.token) { loadAll() }

    val requireLogin: () -> Unit = {
        scope.launch { snackbar.showSnackbar("演示模式：登录后才能使用好友功能") }
        onLoginRequest()
    }

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
                        val token = auth ?: return@Button requireLogin()
                        searching = true
                        scope.launch {
                            try {
                                val r = ApiClient.api.search(token, query.trim())
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
                if (results.isNotEmpty()) {
                    item { SectionTitle("搜索结果") }
                    items(results, key = { it.appUserId ?: it.username ?: it.playerName ?: "r" }) { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.username ?: r.name ?: "?", fontWeight = FontWeight.Medium)
                                Text(
                                    "游戏账号: ${r.playerName ?: r.name ?: "未知"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = {
                                val token = auth ?: return@OutlinedButton requireLogin()
                                scope.launch {
                                    val res = try {
                                        ApiClient.api.friend(token, "request", mapOf("otherAppUserId" to (r.appUserId ?: "")))
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar("发送失败: ${e.message}")
                                        return@launch
                                    }
                                    snackbar.showSnackbar(if (res.ok) "已发送好友请求" else (res.error ?: "发送失败"))
                                }
                            }) { Text("加好友") }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }

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
                                    val token = auth ?: return@TextButton requireLogin()
                                    scope.launch {
                                        val r = try {
                                            ApiClient.api.friend(token, "accept", mapOf("otherAppUserId" to (p.appUserId ?: "")))
                                        } catch (e: Exception) {
                                            snackbar.showSnackbar("操作失败: ${e.message}")
                                            return@launch
                                        }
                                        if (r.ok) { snackbar.showSnackbar("已接受"); loadAll() }
                                        else snackbar.showSnackbar(r.error ?: "操作失败")
                                    }
                                }) { Text("接受") }
                                TextButton(onClick = {
                                    val token = auth ?: return@TextButton requireLogin()
                                    scope.launch {
                                        try {
                                            ApiClient.api.friend(token, "reject", mapOf("otherAppUserId" to (p.appUserId ?: "")))
                                        } catch (_: Exception) { }
                                        loadAll()
                                    }
                                }) { Text("拒绝") }
                            }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }

                item { SectionTitle("我的好友（${friends.size}）" + if (guest) " · 演示" else "") }
                if (friends.isEmpty() && !loading) {
                    item { Text("还没有好友，用上方搜索添加吧。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(friends, key = { it.uuid ?: it.name ?: "f" }) { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (guest) requireLogin() else transferTarget = f
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (f.online == true) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                f.name ?: "?",
                                fontWeight = FontWeight.Medium,
                            )
                            if (f.online == true) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "在线",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                if (friends.isNotEmpty()) {
                    item {
                        Text(
                            "点击好友可转账" + if (guest) "（演示模式需先登录）" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                val token = auth ?: return@TransferDialog requireLogin()
                scope.launch {
                    val res = try {
                        ApiClient.api.transfer(
                            token,
                            mapOf(
                                "toAppUserId" to (target.appUserId ?: ""),
                                "amount" to amount.toDoubleOrNull(),
                                "clientTxId" to clientTxId,
                            ),
                        )
                    } catch (e: Exception) {
                        snackbar.showSnackbar("转账失败: ${e.message}")
                        return@launch
                    }
                    if (res.ok) {
                        snackbar.showSnackbar("已转出 ${formatNumber(amount.toDouble())} adcoins 给 ${target.name}")
                        transferTarget = null
                        loadAll()
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

/** 好友转账对话框（App 端） */
@Composable
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
