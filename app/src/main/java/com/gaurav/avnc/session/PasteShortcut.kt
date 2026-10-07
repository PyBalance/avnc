/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gaurav.avnc.session

import com.gaurav.avnc.vnc.XKeySym

/** Exactly one chord is sent: trying several fallbacks could paste the text repeatedly. */
enum class PasteShortcut(val modifiers: List<Int>, val keySym: Int) {
    Omarchy(listOf(XKeySym.XK_Super_L), XKeySym.XK_v),
    CtrlV(listOf(XKeySym.XK_Control_L), XKeySym.XK_v),
    CtrlShiftV(listOf(XKeySym.XK_Control_L, XKeySym.XK_Shift_L), XKeySym.XK_v),
    ShiftInsert(listOf(XKeySym.XK_Shift_L), XKeySym.XK_Insert),
    CopyOnly(emptyList(), 0),
}
