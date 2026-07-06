package com.watermelon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Design System Showcase screen displaying the Watermelon MediaPlayer color palette.
 * Implements the "Color palette showcase" requirement from the UI Design System.
 */
@Composable
fun DesignSystemScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WatermelonColors.DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(WatermelonSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.lg)
    ) {
        // Header
        Text(
            text = "Watermelon Design System",
            style = WatermelonTypography.typography.displayLarge,
            color = WatermelonColors.DarkOnBackground,
            fontWeight = FontWeight.SemiBold
        )

        // Color Palette Section
        Text(
            text = "Color Palette",
            style = WatermelonTypography.typography.titleLarge,
            color = WatermelonColors.DarkOnBackground
        )

        Spacer(modifier = Modifier.height(WatermelonSpacing.sm))

        // Primary Colors
        ColorPaletteRow(
            title = "Primary",
            colors = listOf(
                ColorItem(WatermelonColors.Palette.WatermelonRed, "Watermelon Red", "#E63946", "Primary brand accent"),
                ColorItem(WatermelonColors.Accent, "Accent", "#E63946", "Primary actions")
            )
        )

        // Surface Colors
        ColorPaletteRow(
            title = "Surface",
            colors = listOf(
                ColorItem(WatermelonColors.Palette.DeepCarbon, "Deep Carbon", "#0D0D0D", "OLED background"),
                ColorItem(WatermelonColors.Palette.SlateGray, "Slate Gray", "#1A1A1A", "Elevated surfaces"),
                ColorItem(WatermelonColors.DarkSurface, "Surface", "#1A1A1A", "Card backgrounds")
            )
        )

        // Text Colors
        ColorPaletteRow(
            title = "Text",
            colors = listOf(
                ColorItem(WatermelonColors.Palette.PaperWhite, "Paper White", "#F1FAEE", "Primary text"),
                ColorItem(WatermelonColors.DarkOnSurface, "On Surface", "#F1FAEE", "Text on surfaces")
            )
        )

        // Accent Colors
        ColorPaletteRow(
            title = "Accent & Secondary",
            colors = listOf(
                ColorItem(WatermelonColors.Palette.SoftTeal, "Soft Teal", "#457B9D", "Secondary accent"),
                ColorItem(WatermelonColors.AccentVariant, "Accent Variant", "#457B9D", "Secondary actions")
            )
        )

        // Warning Colors
        ColorPaletteRow(
            title = "Warning & Status",
            colors = listOf(
                ColorItem(WatermelonColors.Palette.WarningYellow, "Warning Yellow", "#F4A261", "Warnings, buffering"),
                ColorItem(WatermelonColors.Warning, "Warning", "#F4A261", "Status indicators")
            )
        )

        Spacer(modifier = Modifier.height(WatermelonSpacing.xl))

        // Typography Section
        Text(
            text = "Typography",
            style = WatermelonTypography.typography.titleLarge,
            color = WatermelonColors.DarkOnBackground
        )

        Spacer(modifier = Modifier.height(WatermelonSpacing.sm))

        TypographyShowcase()

        Spacer(modifier = Modifier.height(WatermelonSpacing.xl))

        // Spacing Section
        Text(
            text = "Spacing System",
            style = WatermelonTypography.typography.titleLarge,
            color = WatermelonColors.DarkOnBackground
        )

        Spacer(modifier = Modifier.height(WatermelonSpacing.sm))

        SpacingShowcase()

        Spacer(modifier = Modifier.height(WatermelonSpacing.xl))

        // Shapes Section
        Text(
            text = "Shapes",
            style = WatermelonTypography.typography.titleLarge,
            color = WatermelonColors.DarkOnBackground
        )

        Spacer(modifier = Modifier.height(WatermelonSpacing.sm))

        ShapesShowcase()
    }
}

@Composable
private fun ColorPaletteRow(
    title: String,
    colors: List<ColorItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
    ) {
        Text(
            text = title,
            style = WatermelonTypography.typography.labelLarge,
            color = WatermelonColors.DarkOnSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
        ) {
            colors.forEach { colorItem ->
                ColorSwatch(
                    color = colorItem.color,
                    name = colorItem.name,
                    hex = colorItem.hex,
                    description = colorItem.description
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    name: String,
    hex: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .aspectRatio(1f)
                .background(color, WatermelonShapes.card)
        )

        Spacer(modifier = Modifier.height(WatermelonSpacing.xs))

        Text(
            text = name,
            style = WatermelonTypography.typography.labelMedium,
            color = WatermelonColors.DarkOnSurface
        )

        Text(
            text = hex,
            style = WatermelonTypography.typography.labelSmall,
            color = WatermelonColors.DarkOnSurfaceVariant
        )

        Text(
            text = description,
            style = WatermelonTypography.typography.labelSmall,
            color = WatermelonColors.DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun TypographyShowcase() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
    ) {
        TypographySample("Display Large", WatermelonTypography.typography.displayLarge)
        TypographySample("Title Large", WatermelonTypography.typography.titleLarge)
        TypographySample("Title Medium", WatermelonTypography.typography.titleMedium)
        TypographySample("Body Large", WatermelonTypography.typography.bodyLarge)
        TypographySample("Body Medium", WatermelonTypography.typography.bodyMedium)
        TypographySample("Label Large", WatermelonTypography.typography.labelLarge)
        TypographySample("Label Medium", WatermelonTypography.typography.labelMedium)
        TypographySample("Label Small", WatermelonTypography.typography.labelSmall)
        TypographySample("Timecode", WatermelonTypography.timecode)
    }
}

@Composable
private fun TypographySample(
    name: String,
    style: androidx.compose.ui.text.TextStyle
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "The quick brown fox jumps over the lazy dog 1234567890",
            style = style,
            color = WatermelonColors.DarkOnSurface
        )
        Text(
            text = name,
            style = WatermelonTypography.typography.labelSmall,
            color = WatermelonColors.DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun SpacingShowcase() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
    ) {
        SpacingSample("xs", WatermelonSpacing.xs)
        SpacingSample("sm", WatermelonSpacing.sm)
        SpacingSample("md", WatermelonSpacing.md)
        SpacingSample("lg", WatermelonSpacing.lg)
        SpacingSample("xl", WatermelonSpacing.xl)
        SpacingSample("hairline", WatermelonSpacing.hairline)
    }
}

@Composable
private fun SpacingSample(
    name: String,
    spacing: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(spacing, 40.dp)
                .background(WatermelonColors.Accent, WatermelonShapes.sharp)
        )
        Spacer(modifier = Modifier.size(WatermelonSpacing.sm))
        Text(
            text = "$name: ${spacing.value}dp",
            style = WatermelonTypography.typography.bodyMedium,
            color = WatermelonColors.DarkOnSurface
        )
    }
}

@Composable
private fun ShapesShowcase() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
        ) {
            ShapeSample("Sharp", WatermelonShapes.sharp)
            ShapeSample("Small", WatermelonShapes.small)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
        ) {
            ShapeSample("Control", WatermelonShapes.control)
            ShapeSample("Card", WatermelonShapes.card)
        }
    }
}

@Composable
private fun ShapeSample(
    name: String,
    shape: androidx.compose.foundation.shape.RoundedCornerShape
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(WatermelonColors.Accent, shape)
        )
        Spacer(modifier = Modifier.height(WatermelonSpacing.xs))
        Text(
            text = name,
            style = WatermelonTypography.typography.labelMedium,
            color = WatermelonColors.DarkOnSurface
        )
    }
}

data class ColorItem(
    val color: Color,
    val name: String,
    val hex: String,
    val description: String
)

@Preview
@Composable
private fun DesignSystemScreenPreview() {
    DesignSystemScreen(onBack = {})
}