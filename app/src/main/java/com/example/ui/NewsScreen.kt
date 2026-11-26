package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.news.NewsArticle
import com.example.news.NewsCategory
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldTelemetry
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceElevated

@Composable
fun NewsScreen(
    viewModel: LeonViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val newsState by viewModel.newsState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (newsState.articles.isEmpty() && !newsState.isLoading) {
            viewModel.loadNews(newsState.category, newsState.searchQuery, speakDigest = false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .weight(1f)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("news_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to HUD",
                        tint = ArcCyan
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.Newspaper,
                    contentDescription = null,
                    tint = ArcCyan
                )

                Text(
                    text = "LIVE NEWS HUB",
                    style = MaterialTheme.typography.titleLarge,
                    color = ArcCyan,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("news_screen_title")
                )

                IconButton(
                    onClick = {
                        viewModel.loadNews(newsState.category, newsState.searchQuery, speakDigest = false)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("news_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh live news",
                        tint = EmeraldTelemetry
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NewsCategory.entries.forEach { cat ->
                    val selected = newsState.category == cat && newsState.searchQuery.isBlank()
                    FilterChip(
                        selected = selected,
                        onClick = {
                            viewModel.updateNewsSearchQuery("")
                            viewModel.loadNews(category = cat, query = "", speakDigest = false)
                        },
                        label = {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ArcCyan,
                            selectedLabelColor = DeepObsidian,
                            containerColor = HudSurface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = HudBorder,
                            selectedBorderColor = ArcCyan
                        ),
                        modifier = Modifier.testTag("news_cat_${cat.wireValue}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search bar + Voice Read Aloud bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newsState.searchQuery,
                    onValueChange = { viewModel.updateNewsSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = "खबर खोजें (जैसे: Cricket, AI, India)...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = ArcCyan)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.loadNews(newsState.category, newsState.searchQuery, speakDigest = false)
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArcCyan,
                        unfocusedBorderColor = HudBorder,
                        focusedContainerColor = HudSurface,
                        unfocusedContainerColor = HudSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("news_search_input")
                )

                Button(
                    onClick = {
                        if (isSpeaking) viewModel.stopSpeaking() else viewModel.readNewsAloud()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpeaking) MaterialTheme.colorScheme.error else EmeraldTelemetry,
                        contentColor = DeepObsidian
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("news_read_aloud_button")
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop speaking" else "Read news aloud",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = if (isSpeaking) "Stop" else "सुनें",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    newsState.isLoading && newsState.articles.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = ArcCyan)
                                Text(
                                    text = "ताज़ा लाइव खबरें लोड हो रही हैं...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ArcCyan
                                )
                            }
                        }
                    }

                    newsState.errorMessage != null && newsState.articles.isEmpty() -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = HudSurface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Live News अभी लोड नहीं हो सकी",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = newsState.errorMessage ?: "Internet connection required.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = {
                                        viewModel.loadNews(newsState.category, newsState.searchQuery, speakDigest = false)
                                    }
                                ) {
                                    Text("Retry Now")
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("news_articles_list")
                        ) {
                            items(
                                items = newsState.articles,
                                key = { "${it.link}_${it.title.hashCode()}" }
                            ) { article ->
                                NewsArticleCard(
                                    article = article,
                                    onSpeakArticle = { viewModel.speakMessage(article.title) },
                                    onOpenArticle = { viewModel.openNewsArticle(article.link) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsArticleCard(
    article: NewsArticle,
    onSpeakArticle: () -> Unit,
    onOpenArticle: () -> Unit
) {
    Card(
        onClick = onOpenArticle,
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, HudBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = HudSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = article.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = article.publishedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSpeakArticle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Speak headline aloud",
                        tint = EmeraldTelemetry
                    )
                }
                IconButton(
                    onClick = onOpenArticle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Read full article",
                        tint = ArcCyan
                    )
                }
            }
        }
    }
}
