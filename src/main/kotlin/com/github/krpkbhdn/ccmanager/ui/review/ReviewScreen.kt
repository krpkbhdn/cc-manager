package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.review.Review
import com.github.krpkbhdn.ccmanager.review.ReviewImage
import com.github.krpkbhdn.ccmanager.review.ReviewStatus
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.ConfirmDialog
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val listWidth = 240.dp
private val commentsWidth = 300.dp
private const val EMPTY_HINT = "An agent can submit screenshots for your review with the visual-review skill."

/** Review tab: submitted screenshots on the left, the image with marking tools in the middle, numbered comments on the right. */
@Composable
fun ReviewScreen(projectId: Long, state: ReviewState, modifier: Modifier = Modifier) {
    val review = state.selectedReview(projectId)
    if (state.reviews(projectId).isEmpty() || review == null) {
        EmptyState(Icons.Eye, "No reviews yet", EMPTY_HINT, modifier)
        return
    }
    val images = state.images(review.id)
    val image = images.getOrNull(state.selectedImageIndex(projectId))
    Row(modifier.fillMaxSize()) {
        ReviewListPane(projectId, state, Modifier.width(listWidth))
        VerticalRule()
        if (image == null) {
            EmptyState(Icons.Eye, "No images", "This review has no screenshots.", Modifier.weight(1f))
        } else {
            ReviewWorkspace(review, image, state, Modifier.weight(1f))
        }
    }
    state.sendTarget?.let { ReviewTargetDialog(it, { id -> state.sendTo(it.review, id) }, state::dismissSendTarget) }
}

@Composable
private fun ReviewWorkspace(review: Review, image: ReviewImage, state: ReviewState, modifier: Modifier) {
    val viewer = remember(image.id) { ReviewViewerState(image.width.toFloat(), image.height.toFloat()) }
    val marks = state.marks(image.id)
    Row(modifier) {
        Column(Modifier.weight(1f)) {
            ReviewToolbar(viewer, state.tool, state::chooseTool)
            HorizontalRule()
            Box(Modifier.weight(1f)) {
                if (state.cannotShow(image)) {
                    Label("Cannot show this image", Modifier.align(Alignment.Center), color = colors.textTertiary)
                } else {
                    ReviewImageView(viewer, state.bitmap(image), image.id, state)
                }
            }
            HorizontalRule()
            ReviewActions(review, state)
        }
        VerticalRule()
        ReviewCommentsPanel(
            image.id, marks, state.selectedMark,
            { state.selectedMark = it }, { index, text -> state.setComment(image.id, index, text) }, { state.deleteMark(image.id, it) },
            Modifier.width(commentsWidth),
        )
    }
}

@Composable
private fun ReviewActions(review: Review, state: ReviewState) {
    var confirmingDelete by remember { mutableStateOf(false) }
    val open = review.status != ReviewStatus.APPROVED
    Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        IconButton(Icons.Trash, { confirmingDelete = true }, tooltip = "Delete review", tint = colors.danger)
        Spacer(Modifier.weight(1f))
        AppButton("Approve", { state.approve(review) }, enabled = open, icon = Icons.Check)
        AppButton("Send to agent", { state.send(review) }, variant = ButtonVariant.PRIMARY, enabled = open, icon = Icons.Send)
    }
    if (confirmingDelete) {
        ConfirmDialog(ConfirmRequest("Delete review?", "\"${review.title}\" and its marks are removed for good.", "Delete") { state.delete(review) }) { confirmingDelete = false }
    }
}
