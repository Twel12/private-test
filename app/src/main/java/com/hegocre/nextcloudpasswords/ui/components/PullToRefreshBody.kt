package com.hegocre.nextcloudpasswords.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import foundation.e.elib.compose.components.EIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshBody(
    isRefreshing: Boolean,
    onRefresh: () -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        state = pullRefreshState,
        onRefresh = onRefresh,
        isRefreshing = isRefreshing,
        indicator = {
            EIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                state = pullRefreshState) },
        content = { content() }
        //contentColor = MaterialTheme.colorScheme.primary,
        //containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    )
}