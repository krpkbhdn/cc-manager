package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.review.Review
import com.github.krpkbhdn.ccmanager.review.ReviewImage
import com.github.krpkbhdn.ccmanager.review.ReviewStatus
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val thumbnailHeight = 72.dp
private val timeFormat = DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun ReviewListPane(projectId: Long, state: ReviewState, modifier: Modifier = Modifier) {
    val reviews = state.reviews(projectId)
    val selected = state.selectedReview(projectId)
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("Reviews", Modifier.weight(1f), style = Type.bodyMedium)
            CountBadge(reviews.size)
        }
        HorizontalRule()
        val list = rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), list, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(reviews, key = { it.id }) { review ->
                    ReviewRow(review, state.images(review.id).size, review.id == selected?.id) { state.select(projectId, review.id) }
                    if (review.id == selected?.id) Thumbnails(state, review, state.selectedImageIndex(projectId)) { state.select(projectId, review.id, it) }
                }
            }
            ThinScrollbar(list)
        }
    }
}

@Composable
private fun ReviewRow(review: Review, imageCount: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(if (selected) colors.surfaceSelected else Color.Transparent).clickable(onClick = onClick).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Label(review.title, style = Type.bodyMedium, tooltipWhenTruncated = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusTag(review.status)
            Label("$imageCount images", color = colors.textTertiary, style = Type.caption)
            Label(timeFormat.format(review.createdAt), Modifier.weight(1f), color = colors.textTertiary, style = Type.caption)
        }
    }
}

@Composable
private fun StatusTag(status: ReviewStatus) = when (status) {
    ReviewStatus.PENDING -> Chip("Pending", colors.warning)
    ReviewStatus.SENT -> Chip("Sent", colors.accent)
    ReviewStatus.APPROVED -> Chip("Approved", colors.success)
}

@Composable
private fun Thumbnails(state: ReviewState, review: Review, current: Int, onPick: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        state.images(review.id).forEachIndexed { index, image -> Thumbnail(state, image, index == current) { onPick(index) } }
    }
}

@Composable
private fun Thumbnail(state: ReviewState, image: ReviewImage, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.sm)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, if (selected) colors.accent else colors.border, shape).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Box(Modifier.fillMaxWidth().height(thumbnailHeight).background(colors.terminal), contentAlignment = Alignment.Center) {
            state.thumbnail(image)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Label(image.label, Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs), style = Type.small, tooltipWhenTruncated = true)
    }
}
