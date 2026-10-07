/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.gaurav.avnc.session

import com.gaurav.avnc.TestServer
import com.gaurav.avnc.vnc.VncClient
import com.gaurav.avnc.vnc.VncClient.ClipboardSendResult
import com.gaurav.avnc.vnc.VncClientTest.TestObserver
import com.gaurav.avnc.vnc.XKeySym
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ClipboardPasteTest {
    private fun withConnection(utf8: Boolean = false,
                               test: (VncClient, Messenger, TestServer) -> Unit) {
        val server = TestServer(utf8Clipboard = utf8)
        val client = VncClient(TestObserver())
        val messenger = Messenger(client)
        try {
            server.start()
            client.connect(server.host, server.port)
            client.processServerMessage() // Clipboard caps (UTF-8) or initial framebuffer
            test(client, messenger, server)
        } finally {
            messenger.shutdown()
            client.cleanup()
            server.stop()
        }
    }

    private fun send(messenger: Messenger, text: String, shortcut: PasteShortcut): ClipboardSendResult {
        val done = CountDownLatch(1)
        var result: ClipboardSendResult? = null
        assertTrue(messenger.sendTextViaClipboard(text, shortcut) {
            result = it
            done.countDown()
        })
        assertTrue("Clipboard task did not finish", done.await(5, TimeUnit.SECONDS))
        return result!!
    }

    @Test
    fun unicodeClipboardBeforeOmarchyPaste() = withConnection(utf8 = true) { client, messenger, server ->
        val text = "中文测试😀\nsecond line"
        assertEquals(ClipboardSendResult.Sent, send(messenger, text, PasteShortcut.Omarchy))
        messenger.shutdown()
        client.cleanup()
        server.awaitStop()
        assertEquals(text, server.receivedCutText)
        assertEquals(listOf(6, 4, 4, 4, 4), server.receivedInputMessages.toList())
        assertEquals(listOf(XKeySym.XK_Super_L to true, XKeySym.XK_v to true,
                            XKeySym.XK_v to false, XKeySym.XK_Super_L to false), server.receivedKeySyms.toList())
    }

    @Test
    fun sameTextCanBeCopiedAgainWithoutAutomaticSync() = withConnection { client, messenger, server ->
        repeat(2) { assertEquals(ClipboardSendResult.Sent, send(messenger, "same text", PasteShortcut.CopyOnly)) }
        messenger.shutdown()
        client.cleanup()
        server.awaitStop()
        assertEquals(listOf("same text", "same text"), server.receivedClipboardTexts.toList())
        assertTrue(server.receivedKeySyms.isEmpty())
    }

    @Test
    fun noLossyUnicodeOrPasteOnLegacyServer() = withConnection { client, messenger, server ->
        assertEquals(ClipboardSendResult.UnsupportedText, send(messenger, "中文😀", PasteShortcut.Omarchy))
        messenger.shutdown()
        client.cleanup()
        server.awaitStop()
        assertTrue(server.receivedInputMessages.isEmpty())
    }

    @Test
    fun viewOnlyDoesNotCopyOrPaste() = withConnection { client, messenger, server ->
        client.setInputDisabled(true)
        assertEquals(ClipboardSendResult.Unavailable, send(messenger, "abc", PasteShortcut.CtrlV))
        messenger.shutdown()
        client.cleanup()
        server.awaitStop()
        assertTrue(server.receivedInputMessages.isEmpty())
    }

    @Test
    fun terminalPasteReleasesModifiersInReverseOrder() = withConnection { client, messenger, server ->
        assertEquals(ClipboardSendResult.Sent, send(messenger, "abc", PasteShortcut.CtrlShiftV))
        messenger.shutdown()
        client.cleanup()
        server.awaitStop()
        assertEquals(listOf(XKeySym.XK_Control_L to true, XKeySym.XK_Shift_L to true,
                            XKeySym.XK_v to true, XKeySym.XK_v to false,
                            XKeySym.XK_Shift_L to false, XKeySym.XK_Control_L to false), server.receivedKeySyms.toList())
    }

    @Test
    fun disconnectedTaskIsNotQueued() {
        val client = VncClient(TestObserver())
        val messenger = Messenger(client)
        try {
            assertFalse(messenger.sendTextViaClipboard("abc", PasteShortcut.Omarchy) { fail("Should not run") })
        } finally {
            messenger.shutdown()
            client.cleanup()
        }
    }
}
