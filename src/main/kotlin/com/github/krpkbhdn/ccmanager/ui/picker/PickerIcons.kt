package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.icons.lucidePaths

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 1.75f

private val pickerPaths = mapOf(
    "arrow-left" to "m12 19-7-7 7-7M19 12H5",
    "download" to "M12 15V3M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4m4-5 5 5 5-5",
    "briefcase" to "M16 20V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16M4 6h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
    "file-text" to "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7zM14 2v4a2 2 0 0 0 2 2h4M10 9H8M16 13H8M16 17H8",
)

private fun vector(name: String): ImageVector =
    ImageVector.Builder(name, VIEWPORT.dp, VIEWPORT.dp, VIEWPORT, VIEWPORT).addPath(
        pathData = PathParser().parsePathString(pickerPaths[name] ?: lucidePaths.getValue(name)).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

/** Lucide glyphs the shared icon set lacks, built from the same path data. */
internal object PickerIcons {
    val ArrowLeft = vector("arrow-left")
    val Download = vector("download")
    val Briefcase = vector("briefcase")
    val Document = vector("file-text")
    val Drive = vector("hard-drive")
    val Desktop = vector("monitor")
}
