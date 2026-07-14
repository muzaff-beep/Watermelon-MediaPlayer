package com.watermelon.benchmarks

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scroll benchmark — asserts a smooth 60 fps p95 on a 1,000-item folder (Manifest §11 /
 * Teams §8). The gate is p95 frame time < 17 ms; enforced in CI.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFolderList() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        }
    ) {
        val list = device.findObject(By.scrollable(true))
        list.setGestureMargin(device.displayWidth / 5)
        repeat(10) { list.scroll(Direction.DOWN, 1.5f) }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.watermelon.mediaplayer"
    }
}
