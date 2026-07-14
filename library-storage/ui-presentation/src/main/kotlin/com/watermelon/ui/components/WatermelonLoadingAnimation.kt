package com.watermelon.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.watermelon.ui.R

/**
 * Loops the Watermelon branded Lottie animation
 *  1. Rename the file to index_loading.json (no hyphens) and place in:
 *     ui-presentation/src/main/res/raw/index_loading.json
 *  2. Add to libs.versions.toml:
 *     [versions]  lottie = "6.4.0"
 *     [libraries] lottie-compose = { group = "com.airbnb.android", name = "lottie-compose", version.ref = "lottie" }
 *  3. Add to ui-presentation/build.gradle.kts:
 *     implementation(libs.lottie.compose)
 */

@Composable
fun WatermelonLoadingAnimation(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.index_loading)
    )
    LottieAnimation(
        composition = composition,
        iterations  = LottieConstants.IterateForever,
        modifier    = modifier
    )
}
