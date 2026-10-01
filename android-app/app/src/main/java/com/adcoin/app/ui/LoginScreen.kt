package com.adcoin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.adcoin.app.data.ApiClient
import com.adcoin.app.data.Session
import com.adcoin.app.data.SessionStore
import kotlinx.coroutines.launch

/** 登录/注册页。可返回主界面（游客模式），登录成功后自动回到主界面。 */
@Composable
fun LoginScreen(onBack: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registerMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.MonetizationOn, "AdCoin",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("AdCoin", style = MaterialTheme.typography.displaySmall)
        Text("看广告赚游戏币", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(if (registerMode) "密码（至少 6 位）" else "密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (busy) return@Button
                busy = true
                error = null
                scope.launch {
                    val res = try {
                        if (registerMode) {
                            ApiClient.api.register(mapOf("username" to username, "password" to password))
                        } else {
                            ApiClient.api.login(mapOf("username" to username, "password" to password))
                        }
                    } catch (e: Exception) {
                        busy = false
                        error = "连不上后端（${e.message}）。可先返回用演示模式浏览界面。"
                        return@launch
                    }
                    busy = false
                    if (res.ok && res.token != null) {
                        SessionStore.save(Session(
                            token = res.token,
                            username = res.user?.username ?: username,
                            appUserId = res.user?.appUserId ?: "",
                            linkedPlayerName = res.user?.linkedPlayerName,
                        ))
                    } else {
                        error = res.error ?: "请求失败"
                    }
                }
            },
            enabled = username.isNotBlank() && password.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.width(18.dp).height(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(if (registerMode) "注册并登录" else "登录")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { registerMode = !registerMode; error = null },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (registerMode) "已有账号？去登录" else "没有账号？去注册")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("先不登录，用演示模式浏览")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "提示：未部署后端时无法登录，但可用演示模式查看所有界面。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
