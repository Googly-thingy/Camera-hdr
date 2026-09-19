package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.CameraLens
import com.example.model.HdrMode
import com.example.model.HdrSettings
import com.example.model.HistogramChannel

class CameraPreferences(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getLens(): CameraLens {
    val name = prefs.getString(KEY_LENS, CameraLens.BACK.name) ?: CameraLens.BACK.name
    return try {
      CameraLens.valueOf(name)
    } catch (e: Exception) {
      CameraLens.BACK
    }
  }

  fun setLens(lens: CameraLens) {
    prefs.edit().putString(KEY_LENS, lens.name).apply()
  }

  fun getHdrSettings(): HdrSettings {
    val modeName = prefs.getString(KEY_HDR_MODE, HdrMode.BACKLIGHT_BOOST.name) ?: HdrMode.BACKLIGHT_BOOST.name
    val mode = try {
      HdrMode.valueOf(modeName)
    } catch (e: Exception) {
      HdrMode.BACKLIGHT_BOOST
    }
    val shadowLift = prefs.getFloat(KEY_SHADOW_LIFT, 0.70f)
    val highlightRecovery = prefs.getFloat(KEY_HIGHLIGHT_RECOVERY, 0.75f)
    val fillLight = prefs.getBoolean(KEY_FILL_LIGHT, true)
    val showZebra = prefs.getBoolean(KEY_SHOW_ZEBRA, true)

    return HdrSettings(
      mode = mode,
      shadowLift = shadowLift,
      highlightRecovery = highlightRecovery,
      fillLightEmulation = fillLight,
      showZebraAlerts = showZebra
    )
  }

  fun setHdrSettings(settings: HdrSettings) {
    prefs.edit()
      .putString(KEY_HDR_MODE, settings.mode.name)
      .putFloat(KEY_SHADOW_LIFT, settings.shadowLift)
      .putFloat(KEY_HIGHLIGHT_RECOVERY, settings.highlightRecovery)
      .putBoolean(KEY_FILL_LIGHT, settings.fillLightEmulation)
      .putBoolean(KEY_SHOW_ZEBRA, settings.showZebraAlerts)
      .apply()
  }

  fun setHdrMode(mode: HdrMode) {
    prefs.edit().putString(KEY_HDR_MODE, mode.name).apply()
  }

  fun isHistogramVisible(): Boolean {
    return prefs.getBoolean(KEY_HISTOGRAM_VISIBLE, true)
  }

  fun setHistogramVisible(visible: Boolean) {
    prefs.edit().putBoolean(KEY_HISTOGRAM_VISIBLE, visible).apply()
  }

  fun getHistogramChannel(): HistogramChannel {
    val name = prefs.getString(KEY_HISTOGRAM_CHANNEL, HistogramChannel.RGB.name) ?: HistogramChannel.RGB.name
    return try {
      HistogramChannel.valueOf(name)
    } catch (e: Exception) {
      HistogramChannel.RGB
    }
  }

  fun setHistogramChannel(channel: HistogramChannel) {
    prefs.edit().putString(KEY_HISTOGRAM_CHANNEL, channel.name).apply()
  }

  fun getEvCompensation(): Float {
    return prefs.getFloat(KEY_EV_COMPENSATION, 0.0f)
  }

  fun setEvCompensation(ev: Float) {
    prefs.edit().putFloat(KEY_EV_COMPENSATION, ev).apply()
  }

  fun isFlashOn(): Boolean {
    return prefs.getBoolean(KEY_FLASH_ON, false)
  }

  fun setFlashOn(flash: Boolean) {
    prefs.edit().putBoolean(KEY_FLASH_ON, flash).apply()
  }

  companion object {
    private const val PREFS_NAME = "backlight_hdr_camera_prefs"
    private const val KEY_LENS = "camera_lens"
    private const val KEY_HDR_MODE = "hdr_mode"
    private const val KEY_SHADOW_LIFT = "shadow_lift"
    private const val KEY_HIGHLIGHT_RECOVERY = "highlight_recovery"
    private const val KEY_FILL_LIGHT = "fill_light"
    private const val KEY_SHOW_ZEBRA = "show_zebra"
    private const val KEY_HISTOGRAM_VISIBLE = "histogram_visible"
    private const val KEY_HISTOGRAM_CHANNEL = "histogram_channel"
    private const val KEY_EV_COMPENSATION = "ev_compensation"
    private const val KEY_FLASH_ON = "flash_on"
  }
}
