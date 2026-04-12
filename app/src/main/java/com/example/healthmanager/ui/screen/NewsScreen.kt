package com.example.healthmanager.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthmanager.network.HealthArticle
import com.example.healthmanager.navigation.Screen
import com.example.healthmanager.ui.viewmodel.NewsViewModel

/**
 * 健康资讯分类
 */
private val healthCategories = listOf(
    "饮食" to "健康饮食指南",
    "睡眠" to "睡眠健康管理",
    "运动" to "科学运动方法",
    "减压" to "心理压力调节"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(navController: NavController) {
    val viewModel: NewsViewModel = viewModel()
    val context = LocalContext.current
    
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val articles by viewModel.articles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.errorMsg.collectAsState()
    var selectedArticle by remember { mutableStateOf<HealthArticle?>(null) }

    // 加载文章
    fun loadArticles(category: String) {
        selectedCategory = category
        viewModel.loadArticles(category)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when {
                            selectedArticle != null -> "文章详情"
                            selectedCategory != null -> selectedCategory!!
                            else -> "健康资讯"
                        }
                    ) 
                },
                navigationIcon = {
                    if (selectedCategory != null || selectedArticle != null) {
                        IconButton(onClick = { 
                            selectedArticle = null
                            if (selectedCategory != null) {
                                selectedCategory = null
                                viewModel.clear()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
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
                    icon = { Icon(Icons.Default.Article, contentDescription = "资讯") },
                    label = { Text("资讯") },
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
    ) { padding ->
        when {
            selectedArticle != null -> {
                ArticleDetailContent(
                    article = selectedArticle!!,
                    onOpenLink = { url ->
                        if (url.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
            }
            selectedCategory != null -> {
                CategoryArticlesContent(
                    category = selectedCategory!!,
                    articles = articles,
                    isLoading = isLoading,
                    errorMsg = errorMsg,
                    onRefresh = { loadArticles(selectedCategory!!) },
                    onArticleClick = { selectedArticle = it },
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                CategoryHomeContent(
                    categories = healthCategories,
                    onCategoryClick = { loadArticles(it) },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun CategoryHomeContent(
    categories: List<Pair<String, String>>,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("选择健康专区", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        
        categories.forEach { (category, description) ->
            val icon = when (category) {
                "饮食" -> "🍽️"
                "睡眠" -> "😴"
                "运动" -> "🏃"
                "减压" -> "🧘"
                else -> "📖"
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(category) }
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("$icon $category", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("→", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CategoryArticlesContent(
    category: String,
    articles: List<HealthArticle>?,
    isLoading: Boolean,
    errorMsg: String?,
    onRefresh: () -> Unit,
    onArticleClick: (HealthArticle) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            errorMsg != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRefresh) { Text("重试") }
                }
            }
            articles.isNullOrEmpty() -> {
                Text(
                    "暂无文章，点击重试",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(articles) { article ->
                        ArticleCard(
                            article = article,
                            onClick = { onArticleClick(article) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(
    article: HealthArticle,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                article.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                article.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
            if (article.source.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "来源: ${article.source}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun ArticleDetailContent(
    article: HealthArticle,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                article.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (article.source.isNotEmpty()) {
                    Text(
                        "来源: ${article.source}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item {
            HorizontalDivider()
        }
        item {
            Text(
                article.description,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )
        }
        if (article.link.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onOpenLink(article.link) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("阅读原文")
                }
            }
        }
    }
}