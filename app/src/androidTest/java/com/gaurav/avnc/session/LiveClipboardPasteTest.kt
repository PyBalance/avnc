/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.gaurav.avnc.session

import androidx.test.platform.app.InstrumentationRegistry
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
