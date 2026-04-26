package com.example.healthmanager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthmanager.navigation.Screen
import com.example.healthmanager.ui.viewmodel.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(navController: NavController) {
    val viewModel: AssistantViewModel = viewModel()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun sendMessage() {
        if (inputText.isBlank()) return
        val content = inputText
        inputText = ""
        viewModel.sendMessage(content)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("健康助手") },
                actions = {
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "清空对话")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                    label = { Text("首页") },
                    selected = false,
                    onClick = { navController.navigate(Screen.Home.route) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Forum, contentDescription = "助手") },
                    label = { Text("助手") },
                    selected = true,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                    label = { Text("我的") },
                    selected = false,
                    onClick = { navController.navigate(Screen.Profile.route) }
                )
            }
        }
    ) { paddingValues ->
        val imeInsets = WindowInsets.ime.asPaddingValues()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                
                items(messages) { message ->
                    ChatMessageItem(message = message)
                }
                
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "正在思考...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入你的健康问题...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { sendMessage() },
                        enabled = inputText.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (message.isUser) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                fontSize = 14.sp,
                color = if (message.isUser) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}