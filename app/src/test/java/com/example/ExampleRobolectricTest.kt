package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Backlight HDR", appName)
  }

  @Test
  fun `preferences save and restore camera settings properly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.CameraPreferences(context)

    prefs.setLens(com.example.model.CameraLens.FRONT)
    assertEquals(com.example.model.CameraLens.FRONT, prefs.getLens())

    prefs.setHdrMode(com.example.model.HdrMode.MULTI_FRAME_HDR)
    assertEquals(com.example.model.HdrMode.MULTI_FRAME_HDR, prefs.getHdrSettings().mode)

    prefs.setEvCompensation(1.3f)
    assertEquals(1.3f, prefs.getEvCompensation(), 0.01f)

    prefs.setHistogramVisible(false)
    assertEquals(false, prefs.isHistogramVisible())
  }
}
