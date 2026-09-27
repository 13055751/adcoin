package com.adcoin.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adcoin.app.data.ApiClient
import com.adcoin.app.data.Session

/** 排行榜：后端 /api/leaderboard → 插件 /api/v1/top。未登录时展示演示数据。 */
@Composable
fun LeaderboardScreen(session: Session?) {
    val snackbar = remember { SnackbarHostState() }
    val guest = session == null
    var rows by remember {
        mutableStateOf<List<Pair<Int, Pair<String, Double>>>>(
            if (guest) {
                DemoData.LEADERBOARD.mapIndexed { i, p -> i + 1 to p }
            } else {
                emptyList<Pair<Int, Pair<String, Double>>>()
            }
        )
    }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(session?.token) {
        val s = session ?: return@LaunchedEffect
        loading = true
        try {
            val res = ApiClient.api.leaderboard(ApiClient.bearer(s.token))
            rows = (res.top ?: emptyList()).mapIndexed { i, e ->
                i + 1 to ((e.name ?: "?") to e.balance)
            }
        } catch (e: Exception) {
            snackbar.showSnackbar("加载失败: ${e.message}")
        } finally {
            loading = false
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
            Text(
                "adcoins 排行榜" + if (guest) "（演示）" else "",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            if (loading) {
                CircularProgressIndicator()
            } else if (rows.isEmpty()) {
                Text("暂时没有数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    itemsIndexed(rows) { _, row ->
                        val rank = row.first
                        val name = row.second.first
                        val balance = row.second.second
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "#$rank",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(40.dp),
                                )
                                Text(name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text(formatNumber(balance), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
