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

/** 排行榜：后端 /api/leaderboard → 插件 /api/v1/top。 */
@Composable
fun LeaderboardScreen(session: Session) {
    val snackbar = remember { SnackbarHostState() }
    var top by remember { mutableStateOf<List<Pair<Int, Triple<String, String, Double>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(session.token) {
        try {
            val res = ApiClient.api.leaderboard(ApiClient.bearer(session.token))
            val list = res.top ?: emptyList()
            top = list.mapIndexed { index, e -> index + 1 to Triple(e.name ?: "?", e.uuid ?: "", e.balance) }
        } catch (e: Exception) {
            snackbar.showSnackbar("加载失败: ${e.message}")
        } finally {
            loading = false
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
            Text("adcoins 排行榜", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (loading) {
                CircularProgressIndicator()
            } else if (top.isEmpty()) {
                Text("暂时没有数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    itemsIndexed(top) { _, (rank, entry) ->
                        val (name, _, balance) = entry
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
