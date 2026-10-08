/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.pref

import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.gaurav.avnc.CleanPrefsRule
import com.gaurav.avnc.R
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.doClick
import com.gaurav.avnc.targetConfigContext
import com.gaurav.avnc.targetPrefs
import com.gaurav.avnc.ui.prefs.PrefsActivity
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class VirtualKeysEditorTest {
    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(PrefsActivity::class.java)

    @Rule @JvmField val prefsRule = CleanPrefsRule()

    private fun openEditor(title: Int = R.string.pref_customize_virtual_keys) {
        onView(withText(R.string.pref_input)).doClick()
        onView(withId(androidx.preference.R.id.recycler_view)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                        hasDescendant(withText(title)), ViewActions.click()
                )
        )
        onView(withText(title)).checkIsDisplayed()
        onView(withText(R.string.title_save)).checkIsDisplayed()
        onView(withText(R.string.title_cancel)).checkIsDisplayed()
    }

    @Test // User should be able to restore default config
    fun restoreDefaultConfig() {
        targetPrefs.edit { putString("vk_keys_layout", "Up,Down,Left,Right"); putString("vk_super_keys_layout", "C,X") }
        openEditor()
        openActionBarOverflowOrOptionsMenu(targetConfigContext)

        onView(withText(R.string.title_load_defaults)).doClick()
        onView(withText(R.string.title_save)).doClick()
        onView(withText(R.string.msg_saved)).checkWillBeDisplayed()

        // Saving default will clear the pref
        Assert.assertNull(targetPrefs.getString("vk_keys_layout", null))
        Assert.assertEquals("C,X", targetPrefs.getString("vk_super_keys_layout", null))
    }
    @Test
    fun restoreSuperDefaultsDoesNotChangeBaseLayoutOrRows() {
        targetPrefs.edit {
            putString("vk_keys_layout", "Tab,LeftCtrl")
            putString("vk_super_keys_layout", "X,C")
            putString("vk_row_count", "3")
        }
        openEditor(R.string.pref_customize_super_keys)
        openActionBarOverflowOrOptionsMenu(targetConfigContext)
        onView(withText(R.string.title_load_defaults)).doClick()
        onView(withText(R.string.title_save)).doClick()
        Assert.assertNull(targetPrefs.getString("vk_super_keys_layout", null))
        Assert.assertEquals("Tab,LeftCtrl", targetPrefs.getString("vk_keys_layout", null))
        Assert.assertEquals("3", targetPrefs.getString("vk_row_count", null))
    }

    @Test
    fun cancelSuperDefaultChangesKeepsBothLayouts() {
        targetPrefs.edit { putString("vk_keys_layout", "Tab,LeftCtrl"); putString("vk_super_keys_layout", "X,C") }
        openEditor(R.string.pref_customize_super_keys)
        openActionBarOverflowOrOptionsMenu(targetConfigContext)
        onView(withText(R.string.title_load_defaults)).doClick()
        onView(withText(R.string.title_cancel)).doClick()
        Assert.assertEquals("X,C", targetPrefs.getString("vk_super_keys_layout", null))
        Assert.assertEquals("Tab,LeftCtrl", targetPrefs.getString("vk_keys_layout", null))
    }

    @Test
    fun reorderSuperLayoutAndSaveOnlySuperGroup() {
        targetPrefs.edit { putString("vk_keys_layout", "Tab,LeftCtrl"); putString("vk_super_keys_layout", "X,C,V") }
        openEditor(R.string.pref_customize_super_keys)
        onView(withText("C")).doClick()
        onView(withId(R.id.move_up_btn)).doClick()
        onView(withText(R.string.title_save)).doClick()
        Assert.assertEquals("C,X,V", targetPrefs.getString("vk_super_keys_layout", null))
        Assert.assertEquals("Tab,LeftCtrl", targetPrefs.getString("vk_keys_layout", null))
    }

    @Test
    fun addingExistingSuperKeyDoesNotDuplicateItAndCrossGroupKeysAreAllowed() {
        targetPrefs.edit { putString("vk_keys_layout", "Tab,C,LeftCtrl"); putString("vk_super_keys_layout", "X,V") }
        openEditor(R.string.pref_customize_super_keys)
        repeat(2) {
            onView(withId(R.id.add_key_btn)).doClick()
            onView(withText("C")).inRoot(isPlatformPopup()).doClick()
        }
        onView(withText(R.string.title_save)).doClick()
        Assert.assertEquals("X,V,C", targetPrefs.getString("vk_super_keys_layout", null))
        Assert.assertEquals("Tab,C,LeftCtrl", targetPrefs.getString("vk_keys_layout", null))
    }

    @Test
    fun deletingSuperKeyDoesNotDeleteMatchingBaseKey() {
        targetPrefs.edit { putString("vk_keys_layout", "Tab,C,LeftCtrl"); putString("vk_super_keys_layout", "X,C") }
        openEditor(R.string.pref_customize_super_keys)
        onView(withText("C")).doClick()
        onView(withId(R.id.delete_btn)).doClick()
        onView(withText(R.string.title_save)).doClick()
        Assert.assertEquals("X", targetPrefs.getString("vk_super_keys_layout", null))
        Assert.assertEquals("Tab,C,LeftCtrl", targetPrefs.getString("vk_keys_layout", null))
    }

}