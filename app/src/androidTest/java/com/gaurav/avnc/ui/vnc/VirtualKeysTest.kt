/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.os.SystemClock
import androidx.core.content.edit
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.ViewPagerActions
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.CleanPrefsRule
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.VirtualKeysBinding
import com.gaurav.avnc.VncSessionScenario
import com.gaurav.avnc.VncSessionTest
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkIsNotDisplayed
import com.gaurav.avnc.checkKeyboardIsHidden
import com.gaurav.avnc.checkKeyboardIsDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.doClick
import com.gaurav.avnc.doLongClick
import com.gaurav.avnc.doTypeText
import com.gaurav.avnc.runOnMainSync
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.getClipboardText
import com.gaurav.avnc.setClipboardText
import com.gaurav.avnc.closeSystemDialogs
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.vnc.XKeySym
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualKeysTest : VncSessionTest() {

    @JvmField
    @Rule
    val prefsRule = CleanPrefsRule()

    @JvmField
    @Rule
    val clipboardOverlayRule = object : ExternalResource() {
        override fun before() = closeSystemDialogs()
        override fun after() = closeSystemDialogs()
    }

    @Test
    fun basicTest() {
        vncSession.run {
            // Should be visible
            onView(withText("Ctrl")).checkIsDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
            onView(withText("Tab")).checkIsDisplayed()

            // Send Tab
            onView(withText("Tab")).doClick()
        }

        //Tab should be received by the server
        assertEquals(arrayListOf(XKeySym.XK_Tab), vncSession.server.receivedKeyDowns)
    }

    @Test
    fun showAllKeys() {
        targetPrefs.edit { putBoolean("vk_show_all", true) }

        vncSession.run {
            onView(withText("Insert")).perform(scrollTo()).checkIsDisplayed()
            onView(withText("Delete")).perform(scrollTo()).checkIsDisplayed()
            onView(withText("F1")).perform(scrollTo()).checkIsDisplayed()
        }
    }


    @Test
    fun openWithToolbar() {
        targetPrefs.edit { putBoolean("run_info_show_virtual_keys", false) }

        vncSession.run {
            onView(withText("Ctrl")).check(doesNotExist())
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.virtual_keys_btn)).doClick()

            // Should be visible
            onView(withText("Ctrl")).checkIsDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
            onView(withText("Tab")).checkIsDisplayed()

            // Send Tab
            onView(withText("Tab")).doClick()
        }

        //Tab should be received by the server
        assertEquals(arrayListOf(XKeySym.XK_Tab), vncSession.server.receivedKeyDowns)
    }

    @Test
    fun openAlongWithKeyboard() {
        targetPrefs.edit {
            putBoolean("vk_open_with_keyboard", true)
            putBoolean("run_info_show_virtual_keys", false)
        }

        vncSession.run {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.keyboard_btn)).doClick()
            onIdle()

            // Should be visible
            onView(withText("Ctrl")).checkIsDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
            onView(withText("Tab")).checkIsDisplayed()
        }
    }

    @Test
    fun toggleKeyboard() {
        vncSession.run {
            val toggleBtnMatcher = withContentDescription("Toggle keyboard")

            onView(toggleBtnMatcher).checkIsDisplayed().doClick()
            onView(withId(R.id.input_view)).checkKeyboardIsDisplayed()

            onView(toggleBtnMatcher).checkIsDisplayed().doClick()
            onView(withId(R.id.input_view)).checkKeyboardIsHidden()
        }
    }

    @Test
    fun visibilityShouldBeSavedAcrossSessions() {
        VncSessionScenario().run {
            // Should open by default, so close it
            onView(withContentDescription("Close virtual keys")).checkWillBeDisplayed().doClick()
            onView(withText("Ctrl")).checkIsNotDisplayed()
        }

        VncSessionScenario().run {
            // Was closed, so should remain closed
            onIdle()
            Thread.sleep(400)
            onView(withText("Ctrl")).check(doesNotExist())


            // Now open it
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.virtual_keys_btn)).doClick()

            onView(withText("Ctrl")).checkWillBeDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
        }

        VncSessionScenario().run {
            // Should open by default now
            onView(withText("Ctrl")).checkWillBeDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
            onView(withText("Tab")).checkIsDisplayed()
        }
    }

    @Test
    fun textBoxVisibilityShouldBeSavedAcrossSessions() {
        VncSessionScenario().run {
            onView(withId(R.id.pager)).perform(ViewPagerActions.scrollToLast(false))
            onView(withHint(R.string.hint_send_text_to_server)).checkIsDisplayed()
        }

        VncSessionScenario().run {
            onView(withHint(R.string.hint_send_text_to_server)).checkWillBeDisplayed()
        }
    }

    @Test
    fun textBoxInput() {
        val text = "abcxyzABCXYZ1234567890{}[]()`~@#$%^&*_+-=/*"

        vncSession.run {
            onView(withText("Ctrl")).checkWillBeDisplayed()

            onView(withId(R.id.pager)).perform(ViewPagerActions.scrollToLast(false))
            onView(withHint(R.string.hint_send_text_to_server))
                    .checkIsDisplayed()
                    .doTypeText(text)
                    .perform(pressImeActionButton())
        }

        val sentByClient = text.toCharArray().map { it.code }.toList()
        val receivedOnServer = vncSession.server.receivedKeyDowns.filter { it != XKeySym.XK_Shift_L }.toList()

        assertEquals(sentByClient, receivedOnServer)
    }

    @Test
    fun superWithSingleTap() {
        targetPrefs.edit { putBoolean("vk_use_super_with_single_tap", true) }
        vncSession.run {
            onView(withContentDescription("Super")).checkWillBeDisplayed().doClick()
        }

        assertEquals(listOf(Pair(XKeySym.XK_Super_L, true), Pair(XKeySym.XK_Super_L, false)),
                     vncSession.server.receivedKeySyms)
    }

    @Test
    fun superShortcutsPersistThroughOtherModifiersAndRepeatedKeys() {
        vncSession.run {
            onView(withText("1")).checkIsNotDisplayed()
            onView(withContentDescription("Super")).doClick()
            onView(withText("1")).checkWillBeDisplayed().doClick()
            onView(withText("2")).doClick()
            onView(withContentDescription("Super")).perform(scrollTo()).check(matches(isChecked()))
            onView(withText("Ctrl")).perform(scrollTo()).doClick()
            onView(withText("v")).perform(scrollTo()).doClick()
            onView(withContentDescription("Super")).perform(scrollTo()).check(matches(isChecked()))
            onView(withText("Ctrl")).check(matches(isNotChecked()))
            onView(withText("1")).perform(scrollTo()).checkIsDisplayed()
            onView(withContentDescription("Super")).perform(scrollTo()).doClick()
            onView(withText("1")).checkIsNotDisplayed()
        }
        assertEquals(listOf(XKeySym.XK_Super_L, XKeySym.XK_1, XKeySym.XK_2,
                            XKeySym.XK_Control_L, XKeySym.XK_v), vncSession.server.receivedKeyDowns)
        assertEquals(1, vncSession.server.receivedKeySyms.count { it == (XKeySym.XK_Super_L to false) })
    }

    @Test
    fun clipboardTextBoxUsesDraftWithAutomaticSyncDisabled() {
        targetPrefs.edit { putBoolean("clipboard_sync", false) }
        vncSession.run {
            onView(withId(R.id.pager)).perform(ViewPagerActions.scrollToLast(false))
            onView(withId(R.id.text_box)).perform(replaceText("clipboard draft"))
            onView(withId(R.id.text_clipboard_btn)).doClick()
            onView(withText(R.string.title_clipboard_copy_only)).doClick()
            pollingAssert { assertEquals("clipboard draft", vncSession.server.receivedCutText) }
            onView(withId(R.id.text_box)).check(matches(withText("clipboard draft")))
        }
        assertEquals(emptyList<Int>(), vncSession.server.receivedKeyDowns)
    }

    @Test
    fun explicitClipboardMustSurviveAutomaticFocusSync() {
        val draft = "new clipboard draft"
        targetPrefs.edit { putBoolean("clipboard_sync", true) }
        vncSession.run {
            setClipboardText("stale phone clipboard")
            closeSystemDialogs()
            onView(withId(R.id.pager)).perform(ViewPagerActions.scrollToLast(false))
            onView(withId(R.id.text_box)).perform(replaceText(draft))
            onView(withId(R.id.text_clipboard_btn)).doClick()
            onView(withText(R.string.title_clipboard_copy_only)).doClick()
            pollingAssert { Assert.assertTrue(vncSession.server.receivedClipboardTexts.contains(draft)) }
            pollingAssert { assertEquals(draft, getClipboardText()) }
            var sync: Job? = null
            pollingAssert {
                vncSession.onActivity { sync = it.viewModel.sendClipboardText() }
                Assert.assertNotNull(sync)
            }
            runBlocking { sync?.join() }
            // A sender-queue marker ensures the focus-sync packet has been processed by the server.
            vncSession.onActivity {
                it.viewModel.messenger?.sendKey(XKeySym.XK_F12, 0, true)
                it.viewModel.messenger?.sendKey(XKeySym.XK_F12, 0, false)
            }
            pollingAssert { Assert.assertTrue(XKeySym.XK_F12 in vncSession.server.receivedKeyDowns) }
            assertEquals(draft, vncSession.server.receivedCutText)
        }
        assertEquals(draft, vncSession.server.receivedCutText)
    }

    @Test
    fun backspaceHasOnePressAndOneReleaseWhenHeld() {
        vncSession.run {
            onView(withContentDescription("Backspace")).checkWillBeDisplayed().doLongClick()
        }
        assertEquals(listOf(XKeySym.XK_BackSpace to true, XKeySym.XK_BackSpace to false),
                     vncSession.server.receivedKeySyms.toList())
    }

    @Test
    fun unicodeDraftSurvivesDefaultClipboardSync() {
        vncSession.server.stop()
        val session = VncSessionScenario(utf8Clipboard = true)
        val draft = "中文验证😀\nsecond line"
        session.run {
            setClipboardText("old phone text")
            closeSystemDialogs()
            onView(withId(R.id.pager)).perform(ViewPagerActions.scrollToLast(false))
            onView(withId(R.id.text_box)).perform(replaceText(draft))
            onView(withId(R.id.text_clipboard_btn)).doClick()
            onView(withText(R.string.title_clipboard_copy_only)).doClick()
            pollingAssert { assertEquals(draft, session.server.receivedCutText) }
            pollingAssert { assertEquals(draft, getClipboardText()) }
            var sync: Job? = null
            pollingAssert {
                session.onActivity { sync = it.viewModel.sendClipboardText() }
                Assert.assertNotNull(sync)
            }
            runBlocking { sync?.join() }
            session.onActivity {
                it.viewModel.messenger?.sendKey(XKeySym.XK_F12, 0, true)
                it.viewModel.messenger?.sendKey(XKeySym.XK_F12, 0, false)
            }
            pollingAssert { Assert.assertTrue(XKeySym.XK_F12 in session.server.receivedKeyDowns) }
            assertEquals(draft, session.server.receivedCutText)
        }
        assertEquals(draft, session.server.receivedCutText)
    }

    @Test
    fun cancelAndHideReleaseHeldBackspace() {
        vncSession.run {
            onView(withContentDescription("Backspace")).checkWillBeDisplayed()
            vncSession.onActivity { activity ->
                val binding = activity.binding.virtualKeysStub.binding as VirtualKeysBinding
                val key = binding.keys.findViewWithTag<View>(VirtualKey.Backspace)
                val now = SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_DOWN).forEach { action ->
                    MotionEvent.obtain(now, now, action, 1f, 1f, 0).let {
                        key.dispatchTouchEvent(it)
                        it.recycle()
                    }
                }
                activity.virtualKeys.hide()
            }
        }
        assertEquals(listOf(XKeySym.XK_BackSpace to true, XKeySym.XK_BackSpace to false,
                            XKeySym.XK_BackSpace to true, XKeySym.XK_BackSpace to false),
                     vncSession.server.receivedKeySyms.toList())
    }

    @Test
    fun savedLayoutGetsBackspaceOnceAndRemainsEditable() {
        targetPrefs.edit { putString("vk_keys_layout", "Tab,LeftCtrl") }
        var prefs = runOnMainSync { AppPreferences(targetContext) }
        assertEquals(listOf(VirtualKey.Backspace, VirtualKey.Tab, VirtualKey.LeftCtrl), VirtualKeyLayoutConfig.getLayout(prefs))
        targetPrefs.edit { putString("vk_keys_layout", "Tab,LeftCtrl") }
        prefs = runOnMainSync { AppPreferences(targetContext) }
        assertEquals(listOf(VirtualKey.Tab, VirtualKey.LeftCtrl), VirtualKeyLayoutConfig.getLayout(prefs))
    }

    @Test
    fun hidingKeysReleasesPersistentSuper() {
        vncSession.run {
            onView(withContentDescription("Super")).checkWillBeDisplayed().doClick()
            onView(withContentDescription("Close virtual keys")).perform(scrollTo()).doClick()
        }
        assertEquals(listOf(XKeySym.XK_Super_L to true, XKeySym.XK_Super_L to false),
                     vncSession.server.receivedKeySyms.toList())
    }

    @Test
    fun vkModifierKeysShouldApplyToKeyPressedOnKeyboard() {
        vncSession.run {
            onView(withText("Shift")).checkWillBeDisplayed().doClick()
            onView(withId(R.id.input_view)).doTypeText("a") // Should be sent as uppercase A to server
        }
        assertEquals(listOf(XKeySym.XK_Shift_L, XKeySym.XK_A), vncSession.server.receivedKeyDowns)
    }

    @Test
    fun unlockedToggleKeysShouldBeReleasedWithNextKey() {
        vncSession.run {
            onView(withText("Shift")).checkWillBeDisplayed().doClick()

            onView(withText("Shift")).check(matches(isChecked()))
            onView(withId(R.id.frame_view)).perform(pressKey(KeyEvent.KEYCODE_A))
            onView(withText("Shift")).check(matches(isNotChecked()))
        }
    }

    @Test
    fun lockedToggleKeysShouldRemainLockedAfterNextKeys() {
        vncSession.run {
            onView(withText("Shift")).checkWillBeDisplayed().doLongClick() // Long-click locks the key

            onView(withText("Shift")).check(matches(isChecked()))
            onView(withId(R.id.frame_view))
                    .perform(pressKey(KeyEvent.KEYCODE_A))
                    .perform(pressKey(KeyEvent.KEYCODE_B))
                    .perform(pressKey(KeyEvent.KEYCODE_C))
            onView(withText("Shift")).check(matches(isChecked()))
        }
    }

    @Test
    fun defaultConfigTest() {
        val prefs = runOnMainSync { AppPreferences(targetContext) }
        val defaultKeys = VirtualKeyLayoutConfig.getDefaultLayout(prefs)

        targetPrefs.edit { putBoolean("vk_show_all", true) }
        val defaultAllKeys = VirtualKeyLayoutConfig.getDefaultLayout(prefs)

        Assert.assertNotEquals(defaultKeys, defaultAllKeys)

        // For now, duplicate keys are not allowed
        assertEquals(defaultKeys, defaultKeys.distinct())
        assertEquals(defaultAllKeys, defaultAllKeys.distinct())
    }

    @Test
    fun corruptedConfigTest() {
        targetPrefs.edit { putString("vk_keys_layout", "foobar") }
        val prefs = runOnMainSync { AppPreferences(targetContext) }

        val keys = VirtualKeyLayoutConfig.getLayout(prefs)
        val defaultKeys = VirtualKeyLayoutConfig.getDefaultLayout(prefs)

        // If for some reason layout pref is corrupted, default config should be loaded
        assertEquals(defaultKeys, keys)
    }
}
