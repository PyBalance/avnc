/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.gaurav.avnc.session

import androidx.test.platform.app.InstrumentationRegistry
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.gaurav.avnc.runOnMainSync
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.ui.vnc.input.Dispatcher
import com.gaurav.avnc.ui.vnc.input.KeyHandler
import com.gaurav.avnc.util.AppPreferences
import io.mockk.every
import io.mockk.mockk
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.vnc.VncClient
import com.gaurav.avnc.vnc.VncClient.ClipboardSendResult
import com.gaurav.avnc.vnc.VncClientTest.TestObserver
import com.gaurav.avnc.vnc.XKeySym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Opt-in integration test against a temporary WayVNC server and a controlled host text field. */
class LiveClipboardPasteTest {
    @Test
    fun copyOnlyThenPasteThroughOmarchy() {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("liveVncHost")
        val probe = args.getString("liveClipboardProbe")
        assumeTrue("A live server and clipboard/text-field probe are required", host != null && probe != null)
        val port = args.getString("liveVncPort")?.toInt() ?: 15900
        val text = "AVNC 中文剪贴板验证😀\nOmarchy paste test"
        val client = VncClient(TestObserver())
        val messenger = Messenger(client)
        val initialText = readProbe(probe!!, "text")
        var receiver: Thread? = null
        try {
            client.configure(1, false, 5, true)
            client.connect(host!!, port)
            receiver = thread(name = "live-vnc-receiver") {
                runCatching { while (client.connected) client.processServerMessage() }
            }

            // The extended clipboard capability arrives after the initial connection handshake.
            pollingAssert(timeout = 10000) {
                assertEquals(ClipboardSendResult.Sent, send(messenger, text, PasteShortcut.CopyOnly))
            }
            pollingAssert { assertEquals(text, readProbe(probe, "clipboard")) }
            assertEquals(initialText, readProbe(probe, "text"))

            assertEquals("The controlled host text field must have focus", "true", readProbe(probe, "focused"))
            assertEquals(ClipboardSendResult.Sent, send(messenger, text, PasteShortcut.Omarchy))
            pollingAssert(timeout = 10000) {
                val pasted = readProbe(probe, "text")
                assertTrue(pasted.contains(text))
                assertEquals(initialText.length + text.length, pasted.length)
                assertEquals(initialText, pasted.replaceFirst(text, ""))
            }

            assertEquals("true", readProbe(probe, "focused"))
            val clipboardBeforeTyping = readProbe(probe, "clipboard")
            val beforeTyping = readProbe(probe, "text")
            val keyText = " Abc@123 #! "
            val dispatcher = mockk<Dispatcher>()
            every { dispatcher.onXKey(any(), any(), any()) } answers {
                messenger.sendKey(firstArg(), secondArg(), thirdArg())
            }
            val keyHandler = KeyHandler(dispatcher, runOnMainSync { AppPreferences(targetContext) })
            val keyEvents = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(keyText.toCharArray())!!
            keyEvents.forEach { assertTrue(keyHandler.onKeyEvent(it)) }
            pollingAssert {
                val typed = readProbe(probe, "text")
                assertEquals(beforeTyping.length + keyText.length, typed.length)
                assertEquals(beforeTyping, typed.replaceFirst(keyText, ""))
            }
            // The Unicode fallback also has to turn an explicit draft newline into Enter.
            assertTrue(keyHandler.onKeyEvent(KeyEvent(SystemClock.uptimeMillis(), "a\nb", 0, 0)))
            pollingAssert { assertTrue(readProbe(probe, "text").contains("a\nb")) }
            assertEquals("Key input must leave the remote clipboard unchanged", clipboardBeforeTyping, readProbe(probe, "clipboard"))

            val beforeDelete = readProbe(probe, "text")
            try {
                assertTrue(messenger.sendKey(XKeySym.XK_BackSpace, 0, true))
                Thread.sleep(1100)
            } finally {
                messenger.sendKey(XKeySym.XK_BackSpace, 0, false)
            }
            pollingAssert {
                assertTrue("A held physical-style Backspace must delete repeatedly",
                           readProbe(probe, "text").length < beforeDelete.length - 1)
            }
            Thread.sleep(150)
            val stopped = readProbe(probe, "text")
            Thread.sleep(250)
            assertEquals("Deletion must stop after key-up", stopped, readProbe(probe, "text"))
        } finally {
            messenger.shutdown()
            client.cleanup()
            receiver?.join(3000)
        }
    }

    private fun send(messenger: Messenger, text: String, shortcut: PasteShortcut): ClipboardSendResult {
        val done = CountDownLatch(1)
        var result: ClipboardSendResult? = null
        assertTrue(messenger.sendTextViaClipboard(text, shortcut) {
            result = it
            done.countDown()
        })
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return result!!
    }

    private fun readProbe(base: String, path: String): String {
        val connection = URL("$base/$path").openConnection().apply {
            connectTimeout = 1500
            readTimeout = 1500
        }
        return connection.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
