package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.graphics.ImageBitmap
import com.github.krpkbhdn.ccmanager.ui.web.browser.Availability
import com.github.krpkbhdn.ccmanager.ui.web.browser.JcefPageHost
import com.github.krpkbhdn.ccmanager.ui.web.browser.JcefRuntime
import com.github.krpkbhdn.ccmanager.ui.web.browser.WebPageHost
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/** "Outline all" colours by depth and follows border-radius: `CCM_REAL_JCEF=1 ./gradlew test --tests "*InspectorOutlineRealTest"`. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InspectorOutlineRealTest {
    private val timeoutMillis = 10_000L
    private lateinit var host: WebPageHost

    @BeforeAll
    fun startBrowser() {
        assumeTrue(System.getenv("CCM_REAL_JCEF") == "1", "set CCM_REAL_JCEF=1 to run the real browser")
        assertThat(JcefRuntime.availability()).isEqualTo(Availability.Ready)
        val dir = Files.createDirectories(Path.of("build/inspector-outline-test"))
        dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        val page = Files.writeString(dir.resolve("outline.html"), PAGE).toUri().toString()
        host = JcefPageHost.open(dir)
        host.resize(640, 520, 1f)
        host.load(page)
        val end = System.currentTimeMillis() + timeoutMillis
        while ((host.page.value.url != page || host.page.value.loading) && System.currentTimeMillis() < end) Thread.sleep(20)
        Thread.sleep(500)
        host.runScript("window.__ccm.setOptions({showAll: true})")
        Thread.sleep(800)
    }

    @AfterAll
    fun stopBrowser() {
        if (!::host.isInitialized) return
        host.close()
        JcefRuntime.shutdown()
    }

    private fun pixel(bitmap: ImageBitmap, x: Int, y: Int) = IntArray(1).also { bitmap.readPixels(it, x, y, 1, 1) }[0]

    @Test
    fun outlines_of_different_depths_have_different_colours() {
        val bitmap = host.frame.value!!.bitmap

        val outer = pixel(bitmap, 20, 120)
        val inner = pixel(bitmap, 60, 120)

        assertThat(outer).isNotEqualTo(WHITE)
        assertThat(inner).isNotEqualTo(WHITE)
        assertThat(outer).isNotEqualTo(inner)
    }

    @Test
    fun a_rounded_corner_is_round() {
        val bitmap = host.frame.value!!.bitmap

        val edge = pixel(bitmap, 60, 120)
        val corner = pixel(bitmap, 60, 60)

        assertThat(corner).isNotEqualTo(edge)
        assertThat(pixel(bitmap, 61, 61)).isEqualTo(pixel(bitmap, 66, 66))
    }

    @Test
    fun only_the_rounded_corners_of_a_box_are_round() {
        val bitmap = host.frame.value!!.bitmap

        assertThat(pixel(bitmap, 300, 20)).describedAs("rounded top-left").isEqualTo(WHITE)
        assertThat(pixel(bitmap, 399, 20)).describedAs("rounded top-right").isEqualTo(WHITE)
        assertThat(pixel(bitmap, 300, 119)).describedAs("square bottom-left").isNotEqualTo(WHITE)
        assertThat(pixel(bitmap, 399, 119)).describedAs("square bottom-right").isNotEqualTo(WHITE)
        assertThat(pixel(bitmap, 350, 20)).describedAs("top edge").isNotEqualTo(WHITE)
    }

    private companion object {
        const val WHITE = -1
        const val PAGE = """<!doctype html><meta charset=utf-8>
<style>body{margin:0;height:400px;background:#fff}div{position:absolute;box-sizing:border-box}</style>
<div style="left:20px;top:20px;width:200px;height:200px"><div style="left:40px;top:40px;width:120px;height:120px;border-radius:60px"></div></div>
<div style="left:300px;top:20px;width:100px;height:100px;border-radius:8px 8px 0 0"></div>
"""
    }
}
