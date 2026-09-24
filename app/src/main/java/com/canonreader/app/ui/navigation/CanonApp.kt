package com.canonreader.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.canonreader.app.notifications.PendingOpenHolder
import com.canonreader.app.ui.about.AboutScreen
import com.canonreader.app.ui.author.AuthorScreen
import com.canonreader.app.ui.explore.ExploreScreen
import com.canonreader.app.ui.feed.FeedScreen
import com.canonreader.app.ui.post.PostDetailScreen
import com.canonreader.app.ui.postlist.ListKind
import com.canonreader.app.ui.postlist.PostListScreen
import com.canonreader.app.ui.saved.SavedScreen
import com.canonreader.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val FEED = "feed"
    const val EXPLORE = "explore"
    const val SAVED = "saved"
    const val POST = "post/{id}"
    const val AUTHOR = "author/{authorId}"
    const val POSTS = "posts?kind={kind}&arg={arg}&title={title}"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
    fun post(id: Long) = "post/$id"
    fun author(id: String) = "author/${Uri.encode(id)}"
    fun posts(kind: String, arg: String, title: String) =
        "posts?kind=${Uri.encode(kind)}&arg=${Uri.encode(arg)}&title=${Uri.encode(title)}"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.FEED, "Feed", Icons.Filled.Home),
    BottomTab(Routes.EXPLORE, "Explore", Icons.Filled.Search),
    BottomTab(Routes.SAVED, "Saved", Icons.Filled.Bookmarks),
)

@Composable
fun CanonApp(pendingOpenHolder: PendingOpenHolder) {
    val navController = rememberNavController()
    val pendingPostId by pendingOpenHolder.pendingPostId.collectAsStateWithLifecycle()

    LaunchedEffect(pendingPostId) {
        pendingPostId?.let { id ->
            navController.navigate(Routes.post(id)) { launchSingleTop = true }
            pendingOpenHolder.consume()
        }
    }

    val openPost: (Long) -> Unit = { id -> navController.navigate(Routes.post(id)) }
    val openAuthor: (String) -> Unit = { id -> navController.navigate(Routes.author(id)) }
    val openWork: (String, String) -> Unit = { workId, title ->
        navController.navigate(Routes.posts(ListKind.WORK, workId, title))
    }
    val openSettings: () -> Unit = { navController.navigate(Routes.SETTINGS) }
    val openAbout: () -> Unit = { navController.navigate(Routes.ABOUT) }
    val openSaved: () -> Unit = { navController.navigate(Routes.SAVED) }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeTabs(
                onOpenPost = openPost,
                onOpenAuthor = openAuthor,
                onSearch = { query ->
                    navController.navigate(Routes.posts(ListKind.SEARCH, query, "“$query”"))
                },
                onOpenSaved = openSaved,
                onOpenSettings = openSettings,
                onOpenAbout = openAbout,
            )
        }
        composable(Routes.SAVED) {
            SavedScreen(
                onBack = { navController.popBackStack() },
                onOpenPost = openPost,
                onOpenSettings = openSettings,
                onOpenAbout = openAbout,
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.AUTHOR,
            arguments = listOf(navArgument("authorId") { type = NavType.StringType }),
        ) {
            AuthorScreen(
                onBack = { navController.popBackStack() },
                onOpenAuthorPosts = { id, name -> navController.navigate(Routes.posts(ListKind.AUTHOR, id, name)) },
                onOpenWork = openWork,
                onOpenSettings = openSettings,
                onOpenAbout = openAbout,
            )
        }
        composable(
            route = Routes.POSTS,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("arg") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) {
            PostListScreen(
                onBack = { navController.popBackStack() },
                onOpenPost = openPost,
                onOpenSettings = openSettings,
                onOpenAbout = openAbout,
            )
        }
        composable(
            route = Routes.POST,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            PostDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenPost = openPost,
                onOpenWork = openWork,
                onOpenAuthor = openAuthor,
                onOpenSettings = openSettings,
                onOpenAbout = openAbout,
            )
        }
    }
}

/**
 * Bottom navigation is tap-driven so inner surfaces can own horizontal swipes
 * without gesture conflict.
 */
@Composable
private fun HomeTabs(
    onOpenPost: (Long) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { bottomTabs.size })
    val scope = rememberCoroutineScope()
    var barVisible by remember { mutableStateOf(true) }

    LaunchedEffect(pagerState.currentPage) { barVisible = true }

    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -SCROLL_REVEAL_THRESHOLD) barVisible = false
                else if (available.y > SCROLL_REVEAL_THRESHOLD) barVisible = true
                return Offset.Zero
            }
        }
    }

    val showSavedTab: () -> Unit = { scope.launch { pagerState.animateScrollToPage(bottomTabs.size - 1) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            AnimatedVisibility(
                visible = barVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NavigationBar {
                    bottomTabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = null,
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(hideOnScroll),
        ) { page ->
            when (bottomTabs[page].route) {
                Routes.FEED -> FeedScreen(
                    onOpenPost = onOpenPost,
                    onOpenSaved = showSavedTab,
                    onOpenSettings = onOpenSettings,
                    onOpenAbout = onOpenAbout,
                )
                Routes.EXPLORE -> ExploreScreen(
                    onSearch = onSearch,
                    onOpenAuthor = onOpenAuthor,
                    onOpenPost = onOpenPost,
                    onOpenSaved = showSavedTab,
                    onOpenSettings = onOpenSettings,
                    onOpenAbout = onOpenAbout,
                )
                Routes.SAVED -> SavedScreen(
                    onBack = {},
                    onOpenPost = onOpenPost,
                    onOpenSettings = onOpenSettings,
                    onOpenAbout = onOpenAbout,
                    showBack = false,
                )
            }
        }
    }
}

private const val SCROLL_REVEAL_THRESHOLD = 1f
