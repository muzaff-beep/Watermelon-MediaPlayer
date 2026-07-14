package com.watermelon.benchmarks

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup benchmark — measures time from Activity.onCreate() to the first folder-list render.
 * The folder index (Phase 1) must be visible before Phase 2 completes; target < 2 s on a
 * Galaxy A23 (Manifest §11 / Teams §8).
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupToFolderList() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        // First folder list visible == Phase 1 sweep rendered.
        device.wait(Until.hasObject(By.scrollable(true)), 2_000)
    }

    companion object {
        private const val TARGET_PACKAGE = "com.watermelon.mediaplayer"
    }
}
