package com.example.healthmanager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthmanager.navigation.Screen
import com.example.healthmanager.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val viewModel: ProfileViewModel = viewModel()
    val user by viewModel.currentUser.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()

    // 表单状态，使用用户数据初始化
    var nickname by remember(user) { mutableStateOf(user?.nickname ?: "") }
    var gender by remember(user) { mutableStateOf(user?.gender ?: "未设置") }
    var age by remember(user) { mutableStateOf(user?.age?.toString() ?: "") }
    var height by remember(user) { mutableStateOf(user?.height?.toString() ?: "") }
    var weight by remember(user) { mutableStateOf(user?.weight?.toString() ?: "") }
    var targetSteps by remember(user) { mutableStateOf(user?.targetSteps?.toString() ?: "8000") }
    var targetCalories by remember(user) { mutableStateOf(user?.targetCalories?.toString() ?: "2000") }

    var genderExpanded by remember { mutableStateOf(false) }
    val genderOptions = listOf("男", "女", "未设置")

    LaunchedEffect(updateResult) {
        if (updateResult != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearUpdateResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人资料") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
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
                    selected = false,
                    onClick = { navController.navigate(Screen.Assistant.route) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                    label = { Text("我的") },
                    selected = true,
                    onClick = { }
                )
            }
        },
        snackbarHost = {
            updateResult?.let {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("基本信息", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = genderExpanded,
                onExpandedChange = { genderExpanded = it }
            ) {
                OutlinedTextField(
                    value = gender,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("性别") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genderExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) {
                    genderOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { gender = option; genderExpanded = false }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("年龄") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("身高(cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("体重(kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Divider()

            Text("每日目标", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = targetSteps,
                onValueChange = { targetSteps = it },
                label = { Text("每日步数目标（步）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = targetCalories,
                onValueChange = { targetCalories = it },
                label = { Text("每日热量摄入目标（kcal）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.updateProfile(
                        nickname = nickname,
                        gender = gender,
                        age = age.toIntOrNull() ?: 0,
                        height = height.toFloatOrNull() ?: 0f,
                        weight = weight.toFloatOrNull() ?: 0f,
                        targetSteps = targetSteps.toIntOrNull() ?: 8000,
                        targetCalories = targetCalories.toIntOrNull() ?: 2000
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("保存修改", fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = {
                    viewModel.prefs.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("退出登录", fontSize = 16.sp)
            }
        }
    }
}
