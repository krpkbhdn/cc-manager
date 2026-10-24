package com.github.krpkbhdn.ccmanager.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 1.75f

private fun lucide(name: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = VIEWPORT.dp, defaultHeight = VIEWPORT.dp, viewportWidth = VIEWPORT, viewportHeight = VIEWPORT)
        .addPath(
            pathData = PathParser().parsePathString(lucidePaths.getValue(name)).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()

/** The icon set: Lucide stroke icons parsed once into vectors; tint them at the call site. */
object Icons {
    val Plus = lucide("plus")
    val Close = lucide("x")
    val Check = lucide("check")
    val ChevronDown = lucide("chevron-down")
    val ChevronRight = lucide("chevron-right")
    val ChevronLeft = lucide("chevron-left")
    val Folder = lucide("folder")
    val FolderOpen = lucide("folder-open")
    val FolderPlus = lucide("folder-plus")
    val Bell = lucide("bell")
    val Gauge = lucide("gauge")
    val Refresh = lucide("refresh-cw")
    val Edit = lucide("pencil")
    val Trash = lucide("trash")
    val Home = lucide("house")
    val Diff = lucide("git-compare")
    val Activity = lucide("activity")
    val Terminal = lucide("terminal")
    val Alert = lucide("triangle-alert")
    val Info = lucide("info")
    val Copy = lucide("copy")
    val Code = lucide("code")
    val Play = lucide("play")
    val Search = lucide("search")
    val Sun = lucide("sun")
    val Moon = lucide("moon")
    val Monitor = lucide("monitor")
    val PanelLeftClose = lucide("panel-left-close")
    val PanelLeftOpen = lucide("panel-left-open")
    val PanelRightClose = lucide("panel-right-close")
    val PanelRightOpen = lucide("panel-right-open")
    val Bot = lucide("bot")
    val Sparkles = lucide("sparkles")
    val CircleCheck = lucide("circle-check")
    val CircleAlert = lucide("circle-alert")
    val Command = lucide("command")
    val Zap = lucide("zap")
    val Grid = lucide("layout-grid")
    val ExternalLink = lucide("external-link")
    val Rewind = lucide("rotate-ccw")
    val Compact = lucide("minimize-2")
    val Tasks = lucide("list-checks")
    val SquareTerminal = lucide("square-terminal")
    val Cpu = lucide("cpu")
    val FilePlus = lucide("file-plus")
    val FileMinus = lucide("file-minus")
    val FileEdit = lucide("file-pen")
    val Branch = lucide("git-branch")
    val ArrowRight = lucide("arrow-right")
    val Clock = lucide("clock")
    val Eraser = lucide("eraser")
    val Book = lucide("book-open")
    val Shield = lucide("shield-check")
    val Hand = lucide("hand")
    val More = lucide("ellipsis")
    val Enter = lucide("corner-down-left")
    val ArrowUp = lucide("arrow-up")
    val ArrowDown = lucide("arrow-down")
    val Stop = lucide("circle-stop")
    val Message = lucide("message-square")
    val Compass = lucide("compass")
    val Layers = lucide("layers")
    val Brain = lucide("brain")
    val Eye = lucide("eye")
    val Plug = lucide("plug")
    val Log = lucide("scroll-text")
    val Commit = lucide("git-commit-horizontal")
    val Merge = lucide("git-merge")
    val Tag = lucide("tag")
    val Archive = lucide("archive")
    val Download = lucide("download")
    val Pull = lucide("arrow-down-to-line")
    val Push = lucide("arrow-up-from-line")
    val ChevronsUpDown = lucide("chevrons-up-down")
    val FolderTree = lucide("folder-tree")
    val List = lucide("list")
    val Undo = lucide("undo-2")
    val Minus = lucide("minus")
    val CircleDot = lucide("circle-dot")
    val User = lucide("user")
    val Globe = lucide("globe")
    val Hash = lucide("hash")
    val Link = lucide("link")
}
