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
}
