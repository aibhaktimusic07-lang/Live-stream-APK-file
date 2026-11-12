package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.PlaylistItem
import com.example.security.CredentialsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("24/7 Live Studio", appName)
    }

    @Test
    fun `playlist item duration formatting`() {
        val item = PlaylistItem(
            id = 1,
            uriString = "content://media/test.mp4",
            title = "Test Broadcast Video",
            durationMs = 3661000L, // 1h 1m 1s
            sizeBytes = 150 * 1024 * 1024L
        )

        assertEquals("01:01:01", item.formattedDuration)
        assertTrue(item.formattedSize.contains("MB"))
    }

    @Test
    fun `credentials key redaction`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val creds = CredentialsManager(context)
        val redacted = creds.redactKey("live_123456789_abcdef")

        assertTrue(redacted.contains("••••••••"))
        assertTrue(!redacted.contains("123456789"))
    }
}
