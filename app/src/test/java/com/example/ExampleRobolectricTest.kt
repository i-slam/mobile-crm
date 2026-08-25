package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CallRecord
import com.example.data.model.WhatsAppTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("Call Notes & Quick Share", appName)
  }

  @Test
  fun `verify whatsapp template formatting`() {
    val template = WhatsAppTemplate.getDefaultTemplates().first()
    val formatted = template.formatMessage(
      phoneNumber = "+15551234567",
      showroomName = "Test Showroom",
      showroomAddress = "123 Main St",
      showroomMapsUrl = "https://maps.google.com/?q=Test"
    )
    assertNotNull(formatted)
    assert(formatted.contains("https://maps.google.com/?q=Test"))
    assert(formatted.contains("Test Showroom"))
  }
}

