package com.watermelon.ui.screens

import com.watermelon.ui.R

enum class VideoRatio(val label: String, val ratio: Float?) {
    FILL("Fill", null), ORIGINAL("Original", null),
    RATIO_16_9("16:9", 16f / 9f), RATIO_4_3("4:3", 4f / 3f), RATIO_21_9("21:9", 21f / 9f)
}

enum class ScreenOrientation(val iconRes: Int) {
    AUTO(R.drawable.ic_orientation_auto),
    PORTRAIT(R.drawable.ic_orientation_portrait),
    LANDSCAPE(R.drawable.ic_orientation_landscape)
}
