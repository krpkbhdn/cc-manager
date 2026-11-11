package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.MarkPoint
import com.github.krpkbhdn.ccmanager.review.ReviewFeedbackWriter
import com.github.krpkbhdn.ccmanager.review.ReviewImageAnnotator
import com.github.krpkbhdn.ccmanager.review.ReviewImageStore
import com.github.krpkbhdn.ccmanager.review.ReviewProperties
import com.github.krpkbhdn.ccmanager.review.ReviewService
import com.github.krpkbhdn.ccmanager.review.SubmittedImage
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource

private const val WIDTH = 1280
private const val HEIGHT = 800
private const val SIDEBAR_WIDTH = 240
private const val CARD_SIZE = 3
private const val BYTES_20_MB = 20L * 1024 * 1024
private const val MAX_SIDE = 8192
private const val MAX_PIXELS = 36_000_000L
private const val MAX_REVIEWS = 50

/** A real [ReviewService] on a throwaway SQLite file, plus the sample review the `--review` preview flag shows. */
internal object ReviewPreviewData {
    @Volatile
    lateinit var current: ReviewService
        private set
    private lateinit var jdbc: JdbcClient

    fun service(): ReviewService {
        val file = Files.createTempFile("ccm-preview-review", ".db").also { it.toFile().deleteOnExit() }
        val dataSource = DriverManagerDataSource("jdbc:sqlite:$file?journal_mode=WAL&busy_timeout=5000&foreign_keys=on")
        Flyway.configure().dataSource(dataSource).load().migrate()
        jdbc = JdbcClient.create(dataSource)
        val properties = ReviewProperties(Duration.ofDays(7), Duration.ofHours(1), 20, BYTES_20_MB, MAX_SIDE, MAX_PIXELS, MAX_REVIEWS)
        return ReviewService(jdbc, ReviewImageStore(properties), ReviewFeedbackWriter(ReviewImageAnnotator()),
            org.springframework.transaction.support.TransactionTemplate(org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource)), properties).also { current = it }
    }

    /** Submits "Sample: settings screen" with three generated screenshots and one mark of each kind incl. a freeform. */
    fun seed(projectId: Long) {
        jdbc.sql("INSERT OR IGNORE INTO project (id, name, path, created_at) VALUES (?, 'cc-manager', 'preview', 'now')").param(projectId).update()
        val dir = Files.createTempDirectory("ccm-preview-shots")
        val images = listOf("Settings", "Settings, narrow", "Settings, dark").mapIndexed { index, label -> SubmittedImage(screenshot(dir, index), label) }
        val review = current.submit(projectId, null, "Sample: settings screen", "Check spacing and alignment.", images)
        val stored = current.images(review.id())
        current.saveMarks(stored[0].id(), listOf(
            MarkDraft(MarkKind.RECTANGLE, 0.22, 0.30, 0.34, 0.16, "This card is 6px lower than its neighbour."),
            MarkDraft(MarkKind.ELLIPSE, 0.70, 0.62, 0.14, 0.12, "The toggle is not centred on the row."),
            MarkDraft(MarkKind.PIN, 0.12, 0.84, 0.0, 0.0, "Sidebar footer is cut off."),
            MarkDraft(MarkKind.FREEFORM, 0.40, 0.66, 0.2, 0.2, "This area has uneven padding.",
                listOf(MarkPoint(0.40, 0.70), MarkPoint(0.50, 0.66), MarkPoint(0.60, 0.72), MarkPoint(0.56, 0.86), MarkPoint(0.44, 0.84))),
        ))
    }

    private fun screenshot(dir: Path, index: Int): Path {
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0xF4F5F7)
        g.fillRect(0, 0, WIDTH, HEIGHT)
        g.color = Color(0x2B2D30)
        g.fillRect(0, 0, SIDEBAR_WIDTH, HEIGHT)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 20)
        g.color = Color.WHITE
        repeat(6) { g.drawString("Menu item ${it + 1}", 28, 70 + it * 44) }
        g.color = Color(0x1F2328)
        g.drawString("Settings, variant ${index + 1}", SIDEBAR_WIDTH + 40, 60)
        repeat(CARD_SIZE * 2) { n ->
            val x = SIDEBAR_WIDTH + 40 + (n % CARD_SIZE) * 330
            val y = 110 + (n / CARD_SIZE) * 230 + if (n == 1) 6 else 0
            g.color = Color.WHITE
            g.fillRoundRect(x, y, 300, 190, 16, 16)
            g.color = Color(0x3574F0)
            g.fillRoundRect(x + 20, y + 20, 120, 16, 8, 8)
            g.color = Color(0xC9CCD6)
            g.fillRoundRect(x + 20, y + 60, 240 - n * 9, 12, 6, 6)
            g.fillRoundRect(x + 20, y + 90, 200, 12, 6, 6)
        }
        g.dispose()
        return dir.resolve("shot-${index + 1}.png").also { ImageIO.write(image, "png", it.toFile()) }
    }
}
