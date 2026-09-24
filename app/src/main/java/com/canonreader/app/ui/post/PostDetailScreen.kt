package com.canonreader.app.ui.post

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.canonreader.app.domain.Neighbour
import com.canonreader.app.domain.Post
import com.canonreader.app.domain.readingMinutes
import com.canonreader.app.domain.toArrivalString
import com.canonreader.app.domain.toDisplayString
import com.canonreader.app.ui.components.ErrorState
import com.canonreader.app.ui.components.HtmlContent
import com.canonreader.app.ui.components.OverflowMenu
import com.canonreader.app.ui.components.SelectionClearProvider
import com.canonreader.app.ui.components.SelectableSurface
import com.canonreader.app.ui.theme.CanonGold
import com.canonreader.app.ui.theme.CanonInk

/**
 * Hosts the tapped post in a [HorizontalPager] over the deck it came from, so the
 * reader can swipe to the next/previous post in that list.
 */
@Composable
fun PostDetailScreen(
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenWork: (workId: String, title: String) -> Unit,
    onOpenAuthor: (authorId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: PostPagerViewModel = hiltViewModel(),
) {
    val ids = viewModel.ids
    val pagerState = rememberPagerState(initialPage = viewModel.startIndex) { ids.size }
    HorizontalPager(
        state = pagerState,
        key = { page -> ids[page] },
    ) { page ->
        PostDetailPage(
            id = ids[page],
            onBack = onBack,
            onOpenPost = onOpenPost,
            onOpenWork = onOpenWork,
            onOpenAuthor = onOpenAuthor,
            onOpenSettings = onOpenSettings,
            onOpenAbout = onOpenAbout,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostDetailPage(
    id: Long,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenWork: (workId: String, title: String) -> Unit,
    onOpenAuthor: (authorId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: PostDetailViewModel = hiltViewModel(key = "post-$id"),
) {
    LaunchedEffect(id) { viewModel.bind(id) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CanonGold,
                    titleContentColor = CanonInk,
                    navigationIconContentColor = CanonInk,
                    actionIconContentColor = CanonInk,
                ),
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.post?.let { post ->
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, post.title)
                                    putExtra(Intent.EXTRA_TEXT, shareText(post))
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share post"))
                            },
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share post")
                        }
                    }
                    IconButton(onClick = viewModel::toggleSave) {
                        Icon(
                            if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove from saved" else "Save post",
                        )
                    }
                    OverflowMenu(
                        tint = CanonInk,
                        onOpenSettings = onOpenSettings,
                        onOpenAbout = onOpenAbout,
                    )
                },
            )
        },
    ) { innerPadding ->
        val post = state.post
        if (post == null) {
            if (state.isLoading) {
                Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                ErrorState(
                    message = "This post couldn't be opened.",
                    modifier = Modifier.padding(innerPadding),
                )
            }
            return@Scaffold
        }

        SelectionClearProvider(clearKey = id) {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    PostHeader(post = post, onOpenWork = onOpenWork, onOpenAuthor = onOpenAuthor)
                }
                item {
                    HtmlContent(
                        html = post.html,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        selectable = true,
                    )
                }
                item {
                    Text(
                        text = sourceCredit(post),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                    )
                }
                item { HorizontalDivider(Modifier.padding(top = 4.dp)) }
                item {
                    ContinueCard(
                        post = post,
                        previous = state.previous,
                        next = state.next,
                        onOpenPost = onOpenPost,
                        onOpenWork = onOpenWork,
                        onOpenAuthor = onOpenAuthor,
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PostHeader(
    post: Post,
    onOpenWork: (workId: String, title: String) -> Unit,
    onOpenAuthor: (authorId: String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
        SelectableSurface {
            Text(
                text = post.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "by ${post.authorName}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onOpenAuthor(post.authorId) },
        )
        Text(
            text = listOfNotNull(
                post.publishedAt?.toDisplayString(),
                "${readingMinutes(post.words)} min read",
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = listOf(post.workTitle, post.section).filter { it.isNotBlank() }.joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.clickable { onOpenWork(post.workId, post.workTitle) },
        )
    }
}

@Composable
private fun ContinueCard(
    post: Post,
    previous: Neighbour?,
    next: Neighbour?,
    onOpenPost: (Long) -> Unit,
    onOpenWork: (workId: String, title: String) -> Unit,
    onOpenAuthor: (authorId: String) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                next == null -> Text(
                    "The end of ${post.workTitle}.",
                    style = MaterialTheme.typography.titleSmall,
                )
                next.isPublished -> {
                    Text(
                        "Next in ${post.workTitle}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        next.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenPost(next.id) },
                    )
                }
                else -> {
                    Text(
                        "The next part of ${post.workTitle}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        next.publishedAt?.let { "arrives ${it.toArrivalString()}" } ?: "arrives soon",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (previous != null && previous.isPublished) {
                TextButton(onClick = { onOpenPost(previous.id) }) {
                    Text("← Previous: ${previous.title}", maxLines = 1)
                }
            }
            TextButton(onClick = { onOpenWork(post.workId, post.workTitle) }) {
                Text("All of ${post.workTitle} so far")
            }
            TextButton(onClick = { onOpenAuthor(post.authorId) }) {
                Text("More from ${post.authorName}")
            }
        }
    }
}

private fun sourceCredit(post: Post): String = buildString {
    append(post.workTitle)
    post.translator?.let { append(", translated by ").append(it) }
    append(". Public-domain text from Standard Ebooks or Project Gutenberg. The words are the author's; only the cuts and headings are ours.")
}

private fun shareText(post: Post): String = buildString {
    append(post.title).append("\n\n")
    append(post.excerpt).append("\n\n")
    append("— ").append(post.authorName).append(", ").append(post.workTitle)
}
