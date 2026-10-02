package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActionType
import com.example.data.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GestureFlow", appName)
  }

  @Test
  fun `verify gesture types and actions are defined`() {
    val gestures = GestureType.entries
    val actions = ActionType.entries
    assertEquals(12, gestures.size)
    assertNotNull(actions.find { it == ActionType.TOGGLE_FLASHLIGHT })
    assertNotNull(actions.find { it == ActionType.MEDIA_PLAY_PAUSE })
    assertNotNull(actions.find { it == ActionType.LAUNCH_APP })
  }

  @Test
  fun `verify gesture signature matching produces high score for identical profiles`() {
    val rads = FloatArray(36) { 1.5f }
    val sig1 = com.example.data.model.GestureSignature(
      radialDistances = rads,
      fingerCount = 5,
      aspectRatio = 1.2f,
      solidity = 0.6f,
      palmRadiusRatio = 0.3f,
      peakAngles = listOf(0.5, 1.2, 1.8, 2.4, 3.0)
    )
    val sig2 = com.example.data.model.GestureSignature(
      radialDistances = rads.copyOf(),
      fingerCount = 5,
      aspectRatio = 1.2f,
      solidity = 0.6f,
      palmRadiusRatio = 0.3f,
      peakAngles = listOf(0.5, 1.2, 1.8, 2.4, 3.0)
    )
    val score = sig1.similarityWith(sig2)
    assertEquals(1.0f, score, 0.01f)
  }
}
