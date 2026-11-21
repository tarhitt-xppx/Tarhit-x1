package com.imaginstudio.imagetools.pixellab.model

import android.graphics.Color
import android.graphics.Typeface

object AppDefaults {
    const val DIM_PRESET_LOW = 480
    const val DIM_PRESET_DEFAULT = 1280
    const val DIM_PRESET_HIGH = 1920
    const val DIM_PRESET_VERY_HIGH = 2560
    const val DIM_PRESET_ULTRA = 3264

    val defaultColors: List<Int> = listOf(
        "#000000", "#ffffff", "#f44336", "#ffcdd2", "#b71c1c", "#e91e63",
        "#f8bbd0", "#880e4f", "#9c27b0", "#673ab7", "#3f51b5", "#2196f3",
        "#bbdefb", "#0d47a1", "#03a9f4", "#00bcd4", "#009688", "#4caf50",
        "#c8e6c9", "#1b5e20", "#8bc34a", "#cddc39", "#ffeb3b", "#fff9c4",
        "#f57f17", "#ffc107", "#ff9800", "#ff5722", "#795548", "#9e9e9e",
        "#f5f5f5", "#e0e0e0", "#bdbdbd", "#757575", "#212121", "#607d8b"
    ).map { Color.parseColor(it) }

    val defaultGradients: List<GradientFill> = listOf(
        GradientFill(GradientDirection.LINEAR_V, 180f, listOf(GradientStop(0f, Color.BLACK), GradientStop(1f, Color.WHITE))),
        GradientFill(GradientDirection.LINEAR_H, 90f, listOf(GradientStop(0f, Color.BLACK), GradientStop(1f, Color.WHITE))),
        GradientFill(GradientDirection.LINEAR_V, 180f, listOf(GradientStop(0f, Color.parseColor("#99daff")), GradientStop(1f, Color.parseColor("#008080")))),
        GradientFill(GradientDirection.LINEAR_V, 180f, listOf(GradientStop(0f, Color.parseColor("#aec320")), GradientStop(1f, Color.parseColor("#80940f")))),
        GradientFill(GradientDirection.LINEAR_V, 180f, listOf(GradientStop(0f, Color.parseColor("#d4a883")), GradientStop(1f, Color.parseColor("#9c8fa1")))),
        GradientFill(GradientDirection.LINEAR_H, 90f, listOf(GradientStop(0f, Color.parseColor("#239eca")), GradientStop(1f, Color.parseColor("#036082")))),
        GradientFill(GradientDirection.LINEAR_H, 90f, listOf(GradientStop(0f, Color.parseColor("#9e9e9e")), GradientStop(1f, Color.parseColor("#2196f3")))),
        GradientFill(GradientDirection.LINEAR_V, 180f, listOf(GradientStop(0f, Color.parseColor("#b3c9bc")), GradientStop(1f, Color.parseColor("#4b696b")))),
        GradientFill(GradientDirection.LINEAR_V, 180f, listOf(GradientStop(0f, Color.parseColor("#ffc107")), GradientStop(1f, Color.parseColor("#00bcd4")))),
        GradientFill(GradientDirection.LINEAR_V, 180f, listOf(GradientStop(0f, Color.WHITE), GradientStop(1f, Color.parseColor("#b6b6b6")))),
        GradientFill(GradientDirection.LINEAR_V, 180f, listOf(GradientStop(0f, Color.parseColor("#2f93c7")), GradientStop(1f, Color.parseColor("#1f5e8a")))),
        GradientFill(GradientDirection.RADIAL, 0f, listOf(GradientStop(0f, Color.parseColor("#800080")), GradientStop(1f, Color.BLACK))),
        GradientFill(GradientDirection.RADIAL, 0f, listOf(GradientStop(0f, Color.parseColor("#f0f0f0")), GradientStop(1f, Color.parseColor("#cfcfcf")))),
        GradientFill(GradientDirection.RADIAL, 0f, listOf(GradientStop(0f, Color.parseColor("#fba939")), GradientStop(1f, Color.parseColor("#ba2b60")))),
        GradientFill(GradientDirection.RADIAL, 0f, listOf(GradientStop(0f, Color.parseColor("#239eca")), GradientStop(1f, Color.parseColor("#036082"))))
    )

    val fontMap: Map<String, Typeface> = mapOf(
        "Sans Regular" to Typeface.SANS_SERIF,
        "Sans Bold" to Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
        "Sans Condensed" to Typeface.create("sans-serif-condensed", Typeface.BOLD),
        "Serif Regular" to Typeface.SERIF,
        "Serif Bold" to Typeface.create(Typeface.SERIF, Typeface.BOLD),
        "Monospace" to Typeface.MONOSPACE,
        "Cursive / Script" to Typeface.create("cursive", Typeface.NORMAL),
        "Casual" to Typeface.create("casual", Typeface.BOLD)
    )

    val quotesEn: Map<String, List<String>> = mapOf(
        "Inspiration" to listOf(
            "Believe you can and you are halfway there.",
            "Turn your wounds into wisdom.",
            "Every moment is a fresh beginning.",
            "Create the things you wish existed."
        ),
        "Success" to listOf(
            "Success is not final, failure is not fatal.",
            "Work hard in silence, let success make the noise.",
            "Quality is not an act, it is a habit."
        ),
        "Motivation" to listOf(
            "Focus on progress, not perfection.",
            "Your only limit is your mind.",
            "Dream big. Start small. Act now."
        )
    )

    val quotesFr: Map<String, List<String>> = mapOf(
        "Inspiration" to listOf(
            "Fais de ta vie un rêve, et d'un rêve, une réalité.",
            "Il n'est jamais trop tard pour devenir ce que vous auriez pu être."
        ),
        "Succès" to listOf(
            "La créativité, c'est l'intelligence qui s'amuse.",
            "Le meilleur moyen de prédire l'avenir est de le créer."
        )
    )
}
