package com.fireflicker.fireplex2

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.fireflicker.fireplex2.data.ExoPlayerSettings

enum class DeviceProfile(val key: String, val label: String) {
    Auto("auto", "Auto Detect"),
    FireStickLite("fire_stick_lite", "Fire Stick Lite"),
    GoogleTv("google_tv", "Google TV"),
    FastWifi("fast_wifi", "Fast WiFi"),
    SlowWifi("slow_wifi", "Slow WiFi"),
    MobileData("mobile_data", "Mobile Data");

    companion object {
        fun fromKey(key: String?): DeviceProfile = entries.firstOrNull { it.key == key } ?: Auto
    }
}

data class DeviceProfileConfig(
    val player: PlayerChoice = PlayerChoice.Exo,
    val streamMode: String,
    val exoSettings: ExoPlayerSettings
)

fun detectedDeviceProfile(context: Context): DeviceProfile {
    val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
    val model = Build.MODEL.orEmpty().lowercase()
    val uiMode = context.getSystemService(UiModeManager::class.java)?.currentModeType

    return when {
        manufacturer.contains("amazon") || model.contains("aft") -> DeviceProfile.FireStickLite
        uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
            model.contains("google tv") || model.contains("chromecast") -> DeviceProfile.GoogleTv
        else -> DeviceProfile.FastWifi
    }
}

fun deviceProfileConfig(profile: DeviceProfile, context: Context): DeviceProfileConfig {
    val resolved = if (profile == DeviceProfile.Auto) detectedDeviceProfile(context) else profile
    return when (resolved) {
        DeviceProfile.FireStickLite -> DeviceProfileConfig(
            streamMode = "auto",
            exoSettings = ExoPlayerSettings(preBufferSeconds = 30, zoomMode = "best_fit", subtitlesEnabled = false, volumePercent = 100)
        )
        DeviceProfile.GoogleTv -> DeviceProfileConfig(
            streamMode = "auto",
            exoSettings = ExoPlayerSettings(preBufferSeconds = 20, zoomMode = "best_fit", subtitlesEnabled = false, volumePercent = 100)
        )
        DeviceProfile.FastWifi -> DeviceProfileConfig(
            streamMode = "auto",
            exoSettings = ExoPlayerSettings(preBufferSeconds = 10, zoomMode = "best_fit", subtitlesEnabled = false, volumePercent = 100)
        )
        DeviceProfile.SlowWifi -> DeviceProfileConfig(
            streamMode = "auto",
            exoSettings = ExoPlayerSettings(preBufferSeconds = 40, zoomMode = "best_fit", subtitlesEnabled = false, volumePercent = 100)
        )
        DeviceProfile.MobileData -> DeviceProfileConfig(
            streamMode = "auto",
            exoSettings = ExoPlayerSettings(preBufferSeconds = 45, zoomMode = "best_fit", subtitlesEnabled = false, volumePercent = 90)
        )
        DeviceProfile.Auto -> error("Auto profile must be resolved before configuration")
    }
}
