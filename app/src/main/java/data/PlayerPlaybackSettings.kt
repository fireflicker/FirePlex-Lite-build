package com.fireflicker.fireplex2.data

import kotlinx.serialization.Serializable

@Serializable
data class ExoPlayerSettings(
    val preBufferSeconds: Int = 20,
    val zoomMode: String = "fill",
    val subtitlesEnabled: Boolean = false,
    val volumePercent: Int = 100
)
