/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ToggleButton
import android.widget.Toast
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.isVisible
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.VirtualKeysBinding
import com.gaurav.avnc.session.PasteShortcut
import com.gaurav.avnc.ui.vnc.input.InputHandler
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.util.addOnGlobalLayoutListener
import com.gaurav.avnc.util.isTrue
import com.gaurav.avnc.util.toggleKeyboard
import kotlin.math.sign


/**
 * Virtual keys allow the user to input keys which are not normally found on
 * keyboards but can be useful for controlling remote server.
 *
 * This class manages the inflation & visibility of virtual keys.
 */
class VirtualKeys(private val activity: VncActivity, private val inputHandler: InputHandler) {

    private val viewModel = activity.viewModel
    private val pref = activity.viewModel.pref
    private val inputView = activity.binding.inputView
    private val stub = activity.binding.virtualKeysStub
    private val toggleKeys = mutableSetOf<ToggleButton>()
    private val lockedToggleKeys = mutableSetOf<ToggleButton>()
    private val heldKeys = mutableMapOf<View, Int>()
    private val keyCharMap by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }
    private var openedWithKb = false
    private var closedByPiPMode = false
    private val pressedSuperKeys = mutableSetOf<Int>()
    private val baseKeyViews = mutableListOf<View>()
    private var synchronizingToggles = false

    val container: View? get() = stub.root

    fun show(saveVisibility: Boolean = false) {
        init()
        container?.visibility = View.VISIBLE
        if (saveVisibility) pref.runInfo.showVirtualKeys = true
    }

    fun hide(saveVisibility: Boolean = false) {
        releaseHeldKeys()
        // A persistent Super must not remain held when its release button is hidden.
        toggleKeys.filter { it.tag == VirtualKey.LeftSuper && it.isChecked }.forEach { it.isChecked = false }
        container?.visibility = View.GONE
        openedWithKb = false //Reset flag
        if (saveVisibility) pref.runInfo.showVirtualKeys = false
    }

    fun onKeyboardOpen() {
        if (pref.input.vkOpenWithKeyboard && container?.visibility != View.VISIBLE) {
            show()
            openedWithKb = true
        }
    }

    fun onKeyboardClose() {
        if (openedWithKb) {
            hide()
            openedWithKb = false
        }

        // Scenario: User uses the TextBox to send text to server, and hides the keyboard. User
        // wants to end the session now, so he swipes-up from bottom to bring up the nav bar, but
        // the TextBox also sees that swipe-up and it shows the keyboard. Now tap on Back navigation
        // button will hide the keyboard instead of ending the session. User must switch away from
        // text-page to break this loop. So we clear the focus here to avoid this issue.
        (stub.binding as? VirtualKeysBinding)?.textBox?.let { if (it.isFocused) it.clearFocus() }
    }

    fun onConnected() {
        if (pref.runInfo.showVirtualKeys && !viewModel.inPiPMode.isTrue)
            show()
    }

    fun onDisconnected() {
        releaseMetaKeys()
        pressedSuperKeys.clear()
        (stub.binding as? VirtualKeysBinding)?.let(::updateKeyGroups)
    }

    fun releaseMetaKeys() {
        releaseHeldKeys()
        toggleKeys.forEach {
            if (it.isChecked)
                it.isChecked = false
        }
    }

    private fun releaseUnlockedMetaKeys() {
        toggleKeys.forEach {
            // Super stays active for repeated workspace/clipboard shortcuts until explicitly released.
            if (it.isChecked && it.tag != VirtualKey.LeftSuper && lockedToggleKeys.none { locked -> locked.tag == it.tag })
                it.isChecked = false
        }
    }

    private fun onAfterKeyEvent(event: KeyEvent) {
        if (event.keyCode == KeyEvent.KEYCODE_META_LEFT || event.keyCode == KeyEvent.KEYCODE_META_RIGHT) {
            if (event.action == KeyEvent.ACTION_DOWN) pressedSuperKeys.add(event.keyCode)
            else if (event.action == KeyEvent.ACTION_UP) pressedSuperKeys.remove(event.keyCode)
            (stub.binding as? VirtualKeysBinding)?.let { binding ->
                val visible = pressedSuperKeys.isNotEmpty()
                if (binding.superKeys.isVisible != visible) {
                    binding.superKeys.isVisible = visible
                    updateKeyGroups(binding)
                    binding.keysScroll.post { binding.keysScroll.scrollTo(0, 0) }
                }
            }
        }
        if (event.action == KeyEvent.ACTION_UP && !KeyEvent.isModifierKey(event.keyCode))
            releaseUnlockedMetaKeys()
    }

    private fun onPiPModeChanged(inPiPMode: Boolean) {
        if (inPiPMode && container?.isVisible == true) {
            hide()
            closedByPiPMode = true
        } else if (!inPiPMode && closedByPiPMode) {
            show()
            closedByPiPMode = false
        }
    }

    private fun init() {
        if (stub.isInflated)
            return

        stub.viewStub?.inflate()
        val binding = stub.binding as VirtualKeysBinding
        initTextPage(binding)
        initKeys(binding)
        initPager(binding)
        inputHandler.onAfterKeyEventListeners += ::onAfterKeyEvent
        viewModel.inPiPMode.observe(activity) { onPiPModeChanged(it) }
    }

    /**
     * To keep everything in single XML layout file, things are done in a slightly weird way.
     * Both keys & text pages are initially attached to temporary View. After inflation, they
     * are detached and passed onto ViewPager adapter. Adapter will insert them at proper place.
     */
    private fun initPager(binding: VirtualKeysBinding) {
        val root = binding.root
        val pager = binding.pager
        val pages = listOf(binding.keysPage, binding.textPage)

        binding.tmpPageHost.apply {
            removeAllViews()
            (parent as ViewGroup).removeView(this)
        }

        // Setup pager
        pager.offscreenPageLimit = pages.size
        pager.adapter = object : PagerAdapter() {
            override fun getCount() = pages.size
            override fun isViewFromObject(view: View, obj: Any) = (view === obj)
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                pages[position].let {
                    container.addView(it)
                    return it
                }
            }

            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                container.removeView(obj as View)
            }
        }
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            val textPageIndex = pages.indexOf(binding.textPage)
            override fun onPageSelected(position: Int) {
                if (ViewCompat.getRootWindowInsets(root)?.isVisible(Type.ime()) == true) {
                    if (position == textPageIndex) binding.textBox.requestFocus()
                    else inputView.requestFocus()
                }
                pref.runInfo.virtualKeysTextBoxVisible = (position == textPageIndex)
            }
        })

        // Setup Layout. Keys grid is the primary View used for deciding size of Virtual keys.
        // All keys are shown if screen is wide enough. Otherwise width is limited to FrameView,
        // and HorizontalScrollView is relied upon to access all keys.
        // NOTE: Paddings in root/pager view is NOT handled by this code.

        // Text controls always have two rows even with a one-row key layout.
        // Fill the available viewport so pinned modifiers cannot be scrolled out of reach.
        fun updateSize() {
            val w = inputView.width
            val spec = MeasureSpec.makeMeasureSpec(w.coerceAtLeast(1), MeasureSpec.EXACTLY)
            val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            binding.textPage.measure(spec, unspecified)
            binding.keysHost.measure(unspecified, unspecified)
            binding.fixedKeys.measure(unspecified, unspecified)
            val h = maxOf(binding.keysHost.measuredHeight, binding.fixedKeys.measuredHeight,
                          binding.textPage.measuredHeight)
            if (w > 0 && h > 0 && (root.width != w || root.height != h))
                root.layoutParams = root.layoutParams.apply { width = w; height = h }
        }
        updateSize()
        addOnGlobalLayoutListener(activity, root) { updateSize() }

        // Switch to text page if it was active last time
        if (pref.runInfo.virtualKeysTextBoxVisible)
            pager.setCurrentItem(pages.indexOf(binding.textPage), false)
    }


    private fun initTextPage(binding: VirtualKeysBinding) {
        binding.textPageBackBtn.setOnClickListener { binding.pager.setCurrentItem(0, true) }
        binding.textBox.setText(viewModel.textDraft)
        binding.textBox.doAfterTextChanged { viewModel.textDraft = it?.toString().orEmpty() }
        fun updateMode() {
            val clipboard = viewModel.textSendMode == "clipboard"
            binding.textSendBtn.isVisible = !clipboard
            binding.textCopyBtn.isVisible = clipboard
            binding.textClipboardBtn.isVisible = clipboard
        }
        binding.textModeGroup.check(if (viewModel.textSendMode == "clipboard") R.id.text_mode_clipboard else R.id.text_mode_keys)
        updateMode()
        binding.textModeGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                viewModel.textSendMode = if (id == R.id.text_mode_clipboard) "clipboard" else "keys"
                updateMode()
            }
        }
        fun sendClipboard(shortcut: PasteShortcut) {
            val text = binding.textBox.text?.toString().orEmpty()
            if (text.isEmpty() || viewModel.clipboardSending.isTrue) return
            releaseMetaKeys()
            if (!viewModel.sendTextViaClipboard(text, shortcut))
                Toast.makeText(activity, R.string.msg_text_send_failed, Toast.LENGTH_SHORT).show()
        }
        binding.textCopyBtn.setOnClickListener { sendClipboard(PasteShortcut.CopyOnly) }
        binding.textClipboardBtn.setOnClickListener { sendClipboard(pref.server.pasteShortcut) }
        binding.textSendBtn.setOnClickListener { handleTextBoxAction(binding.textBox) }
        binding.textBox.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                if (viewModel.textSendMode == "clipboard") sendClipboard(pref.server.pasteShortcut)
                else handleTextBoxAction(binding.textBox)
                true
            } else false // An explicit keyboard newline stays in the draft.
        }
        binding.textBox.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) inputView.requestFocus()
        }
        binding.textBox.onTextCopyListener = { viewModel.sendClipboardText() }
        viewModel.clipboardSending.observe(activity) { sending ->
            binding.textSendBtn.isEnabled = !sending
            binding.textCopyBtn.isEnabled = !sending
            binding.textClipboardBtn.isEnabled = !sending
        }
    }

    private fun initKeys(binding: VirtualKeysBinding) {
        listOf(binding.fixedKeys, binding.superKeys, binding.keys).forEach { it.rowCount = pref.input.vkRowCount }
        binding.superKeys.orientation = GridLayout.HORIZONTAL
        binding.superKeys.columnCount = (VirtualKeyLayoutConfig.getLayout(pref, VirtualKeyLayoutTarget.Super).size + pref.input.vkRowCount - 1) / pref.input.vkRowCount
        fun createKey(vk: VirtualKey): View {
            val view = VirtualKeyViewFactory.create(binding.root.context, vk)
            view.tag = vk
            if (vk == VirtualKey.ToggleKeyboard) view.setOnClickListener { toggleKeyboard(inputView) }
            else if (vk == VirtualKey.CloseKeys) view.setOnClickListener { hide(true) }
            else if (vk.keyCode != null) {
                if (view is ToggleButton) initToggleKey(view, vk.keyCode)
                else initNormalKey(view, vk.keyCode)
            }
            return view
        }
        VirtualKeyLayoutConfig.getLayout(pref, VirtualKeyLayoutTarget.Super).forEach {
            binding.superKeys.addView(createKey(it))
        }
        VirtualKeyLayoutConfig.getLayout(pref).forEach { baseKeyViews += createKey(it) }
        updateKeyGroups(binding)
    }

    private fun updateKeyGroups(binding: VirtualKeysBinding) {
        val superActive = pressedSuperKeys.isNotEmpty()
        val pinned = baseKeyViews.filter {
            val vk = it.tag as VirtualKey
            (pref.input.vkFixedModifiers && vk.isToggle) || (superActive && vk == VirtualKey.LeftSuper)
        }
        // Reparent existing views; never modify the stored layout or lose checked/held state.
        baseKeyViews.forEach { view ->
            val parent = if (view in pinned) binding.fixedKeys else binding.keys
            if (view.parent !== parent) {
                (view.parent as? ViewGroup)?.removeView(view)
                parent.addView(view)
            }
        }
        // Re-establish the original relative order when Super moves back into the base group.
        listOf(binding.fixedKeys, binding.keys).forEach { grid ->
            val ordered = baseKeyViews.filter { (it in pinned) == (grid === binding.fixedKeys) }
            if (ordered.indices.any { grid.getChildAt(it) !== ordered[it] }) {
                grid.removeAllViews()
                ordered.forEach { grid.addView(it) }
            }
        }
        binding.fixedKeys.isVisible = pinned.isNotEmpty()
        binding.superKeys.isVisible = superActive
    }

    private fun initToggleKey(key: ToggleButton, keyCode: Int) {
        key.setOnCheckedChangeListener { _, isChecked ->
            if (synchronizingToggles) return@setOnCheckedChangeListener
            synchronizingToggles = true
            toggleKeys.filter { it !== key && it.tag == key.tag }.forEach { it.isChecked = isChecked }
            synchronizingToggles = false
            sendKey(keyCode, isChecked)
            if (!isChecked) lockedToggleKeys.removeAll { it.tag == key.tag }
        }
        key.setOnLongClickListener {
            key.toggle()
            if (key.isChecked) lockedToggleKeys.add(key)
            true
        }

        if ((keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT) && pref.input.vkUseSuperWithSingleTap)
            key.setOnClickListener {
                key.isChecked = true
                key.isChecked = false
            }

        toggleKeys.add(key)
    }

    private fun initNormalKey(key: View, keyCode: Int) {
        check(key !is ToggleButton) { "use initToggleKey()" }
        if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL ||
            keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                              KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
            initHeldKey(key, keyCode)
            return
        }
        key.setOnClickListener { sendKey(keyCode) }
        makeKeyRepeatable(key)
    }

    /** Hold the key down so the remote OS performs normal keyboard autorepeat. */
    @SuppressLint("ClickableViewAccessibility")
    private fun initHeldKey(key: View, keyCode: Int) {
        // Accessibility clicks have no touch sequence and still perform one full keystroke.
        key.setOnClickListener { if (key !in heldKeys) sendKey(keyCode) }
        key.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (key !in heldKeys) {
                        if (sendKey(keyCode, true)) {
                            heldKeys[key] = keyCode
                            key.isPressed = true
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (key in heldKeys) {
                        key.performClick() // Announce the click without emitting another deletion.
                        heldKeys.remove(key)
                        sendKey(keyCode, false)
                    }
                    key.isPressed = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    heldKeys.remove(key)?.let { sendKey(it, false) }
                    key.isPressed = false
                }
            }
            true
        }
    }

    private fun releaseHeldKeys() {
        val keys = heldKeys.toMap()
        heldKeys.clear()
        keys.forEach { (view, keyCode) ->
            view.isPressed = false
            sendKey(keyCode, false)
        }
    }

    /**
     * When a View is touched, we schedule a callback to to simulate a click.
     * As long as finger stays on the view, we keep repeating this callback.
     */
    private fun makeKeyRepeatable(keyView: View) {
        keyView.setOnTouchListener(object : View.OnTouchListener {
            private var doRepeat = false

            private fun repeat(v: View) {
                if (doRepeat) {
                    v.performClick()
                    v.postDelayed({ repeat(v) }, ViewConfiguration.getKeyRepeatDelay().toLong())
                }
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        doRepeat = true
                        v.postDelayed({ repeat(v) }, ViewConfiguration.getKeyRepeatTimeout().toLong())
                    }

                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        doRepeat = false
                    }
                }
                return false
            }
        })
    }

    private fun handleTextBoxAction(textBox: EditText) {
        if (viewModel.clipboardSending.isTrue) return
        val text = textBox.text?.toString().orEmpty()
        if (text.isEmpty()) return
        if (!viewModel.connected || viewModel.client?.inputEnabled != true) {
            Toast.makeText(activity, R.string.msg_text_send_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val events = keyCharMap.getEvents(text.toCharArray())
        releaseMetaKeys()
        // Preserve the normal Android key mapping. Fallback handles Unicode code points
        // and maps draft newlines to Enter in KeyHandler.
        val queued = if (events == null)
            inputHandler.onKeyEvent(KeyEvent(SystemClock.uptimeMillis(), text, 0, 0))
        else {
            var allQueued = true
            events.forEach { if (!inputHandler.onKeyEvent(it)) allQueued = false }
            allQueued
        }
        if (queued) textBox.setText("")
        else Toast.makeText(activity, R.string.msg_text_send_failed, Toast.LENGTH_SHORT).show()
    }

    private fun sendKey(keyCode: Int) {
        sendKey(keyCode, true)
        sendKey(keyCode, false)
    }

    private fun sendKey(keyCode: Int, isDown: Boolean): Boolean {
        val action = if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        return inputHandler.onVkKeyEvent(KeyEvent(action, keyCode))
    }

}

/**
 * NOTE: Names of these enums may be persisted in app preferences. So if any key name
 *       is ever modified, add a migration to handle old name.
 */
enum class VirtualKey(
        /**
         * [KeyEvent] keycode to be generated when this key is pressed.
         */
        val keyCode: Int? = null,

        /**
         * If key name is not appropriate for UI, use this to set the label.
         */
        val label: String? = null,

        /**
         * If icon is set, this key will be rendered as an ImageButton.
         */
        val icon: Int? = null,

        /**
         * Short description of the key, if the label itself isn't sufficient.
         */
        val description: String? = null,

        val isToggle: Boolean = false,
) {

    // Special actions
    ToggleKeyboard(description = "Toggle keyboard", icon = R.drawable.ic_keyboard),
    CloseKeys(description = "Close virtual keys", icon = R.drawable.ic_clear),

    // Meta keys
    LeftShift(keyCode = KeyEvent.KEYCODE_SHIFT_LEFT, label = "Shift", isToggle = true),
    LeftCtrl(keyCode = KeyEvent.KEYCODE_CTRL_LEFT, label = "Ctrl", isToggle = true),
    LeftAlt(keyCode = KeyEvent.KEYCODE_ALT_LEFT, label = "Alt", isToggle = true),
    LeftSuper(keyCode = KeyEvent.KEYCODE_META_LEFT, label = "Super", icon = R.drawable.ic_super_key, isToggle = true),

    Num1(keyCode = KeyEvent.KEYCODE_1, label = "1"),
    Num2(keyCode = KeyEvent.KEYCODE_2, label = "2"),
    Num3(keyCode = KeyEvent.KEYCODE_3, label = "3"),
    Num4(keyCode = KeyEvent.KEYCODE_4, label = "4"),
    Num5(keyCode = KeyEvent.KEYCODE_5, label = "5"),
    C(keyCode = KeyEvent.KEYCODE_C, label = "C"),
    V(keyCode = KeyEvent.KEYCODE_V, label = "V"),
    X(keyCode = KeyEvent.KEYCODE_X, label = "X"),
    Space(keyCode = KeyEvent.KEYCODE_SPACE),
    Enter(keyCode = KeyEvent.KEYCODE_ENTER),
    Backspace(keyCode = KeyEvent.KEYCODE_DEL, label = "⌫", icon = R.drawable.ic_keyboard_backspace, description = "Backspace"),

    Esc(keyCode = KeyEvent.KEYCODE_ESCAPE),
    Tab(keyCode = KeyEvent.KEYCODE_TAB),
    Home(keyCode = KeyEvent.KEYCODE_MOVE_HOME),
    End(keyCode = KeyEvent.KEYCODE_MOVE_END),
    PgUp(keyCode = KeyEvent.KEYCODE_PAGE_UP),
    PgDn(keyCode = KeyEvent.KEYCODE_PAGE_DOWN),
    Insert(keyCode = KeyEvent.KEYCODE_INSERT),
    Delete(keyCode = KeyEvent.KEYCODE_FORWARD_DEL, label = "Del", description = "Delete"),

    // Arrow keys
    Left(keyCode = KeyEvent.KEYCODE_DPAD_LEFT, icon = R.drawable.ic_keyboard_arrow_left),
    Right(keyCode = KeyEvent.KEYCODE_DPAD_RIGHT, icon = R.drawable.ic_keyboard_arrow_right),
    Up(keyCode = KeyEvent.KEYCODE_DPAD_UP, icon = R.drawable.ic_keyboard_arrow_up),
    Down(keyCode = KeyEvent.KEYCODE_DPAD_DOWN, icon = R.drawable.ic_keyboard_arrow_down),

    F1(keyCode = KeyEvent.KEYCODE_F1),
    F2(keyCode = KeyEvent.KEYCODE_F2),
    F3(keyCode = KeyEvent.KEYCODE_F3),
    F4(keyCode = KeyEvent.KEYCODE_F4),
    F5(keyCode = KeyEvent.KEYCODE_F5),
    F6(keyCode = KeyEvent.KEYCODE_F6),
    F7(keyCode = KeyEvent.KEYCODE_F7),
    F8(keyCode = KeyEvent.KEYCODE_F8),
    F9(keyCode = KeyEvent.KEYCODE_F9),
    F10(keyCode = KeyEvent.KEYCODE_F10),
    F11(keyCode = KeyEvent.KEYCODE_F11),
    F12(keyCode = KeyEvent.KEYCODE_F12),
}

/**
 * Users can change the layout of keys in app settings.
 * Layout configuration is stored as a simple list of key-names.
 */
enum class VirtualKeyLayoutTarget { Base, Super }

object VirtualKeyLayoutConfig {

    private val DEFAULT_LAYOUT = listOf(VirtualKey.Backspace, VirtualKey.ToggleKeyboard, VirtualKey.CloseKeys, VirtualKey.Esc, VirtualKey.LeftSuper,
                                        VirtualKey.Tab, VirtualKey.LeftCtrl, VirtualKey.LeftShift, VirtualKey.LeftAlt,
                                        VirtualKey.Home, VirtualKey.Left, VirtualKey.Up, VirtualKey.Down, VirtualKey.End,
                                        VirtualKey.Right, VirtualKey.PgUp, VirtualKey.PgDn)

    /**
     * In older versions, before users could customize key layout, there was a pref to
     * 'Show all' keys. This layout is used for compatibility with that pref.
     */
    private val DEFAULT_LAYOUT_ALL = DEFAULT_LAYOUT +
                                     listOf(VirtualKey.Insert, VirtualKey.Delete, VirtualKey.F1, VirtualKey.F2, VirtualKey.F3,
                                            VirtualKey.F4, VirtualKey.F5, VirtualKey.F6, VirtualKey.F7, VirtualKey.F8,
                                            VirtualKey.F9, VirtualKey.F10, VirtualKey.F11, VirtualKey.F12)


    private val DEFAULT_SUPER_LAYOUT = listOf(VirtualKey.Num1, VirtualKey.Num2, VirtualKey.Num3,
            VirtualKey.Num4, VirtualKey.Num5, VirtualKey.C, VirtualKey.V, VirtualKey.X,
            VirtualKey.Space, VirtualKey.Enter)

    fun getDefaultLayout(pref: AppPreferences, target: VirtualKeyLayoutTarget = VirtualKeyLayoutTarget.Base): List<VirtualKey> {
        if (target == VirtualKeyLayoutTarget.Super) return DEFAULT_SUPER_LAYOUT
        return if (pref.input.vkShowAll) DEFAULT_LAYOUT_ALL else DEFAULT_LAYOUT
    }

    fun getLayout(pref: AppPreferences, target: VirtualKeyLayoutTarget = VirtualKeyLayoutTarget.Base): List<VirtualKey> {
        val saved = if (target == VirtualKeyLayoutTarget.Super) pref.input.vkSuperLayout else pref.input.vkLayout
        val keys = saved?.split(',')?.mapNotNull { name -> VirtualKey.entries.find { it.name == name } }?.distinct()
        return keys?.takeIf { it.isNotEmpty() } ?: getDefaultLayout(pref, target)
    }

    fun setLayout(pref: AppPreferences, keys: List<VirtualKey>, target: VirtualKeyLayoutTarget = VirtualKeyLayoutTarget.Base) {
        require(keys.isNotEmpty() && keys == keys.distinct())
        val saved = if (keys == getDefaultLayout(pref, target)) null else keys.joinToString(",") { it.name }
        if (target == VirtualKeyLayoutTarget.Super) pref.input.vkSuperLayout = saved
        else pref.input.vkLayout = saved
    }

}

/**
 * Factory for creating individual key [View]s.
 */
object VirtualKeyViewFactory {

    /**
     * There are three types of Views that are generated:
     *
     * [ToggleButton] - if [key] is a toggle
     * [ImageButton]  - if [key] has an icon (label will be ignored)
     * [Button]       - in all other cases
     */
    fun create(context: Context, key: VirtualKey): View {
        val view = if (key.isToggle) createToggle(context, key) else createSimple(context, key)
        view.layoutParams = GridLayout.LayoutParams().apply {
            width = GridLayout.LayoutParams.WRAP_CONTENT
            height = GridLayout.LayoutParams.WRAP_CONTENT
            setGravity(Gravity.CENTER)
        }
        return view
    }

    private fun createSimple(context: Context, key: VirtualKey): View {
        return if (key.icon != null)
            ImageButton(context, null, 0, selectStyle(key))
                    .apply {
                        setImageDrawable(ContextCompat.getDrawable(context, key.icon))
                        contentDescription = getDescription(key)
                    }
        else
            Button(context, null, 0, selectStyle(key))
                    .apply {
                        text = getLabel(key)
                        key.description?.let { contentDescription = it }
                    }
    }

    private fun createToggle(context: Context, key: VirtualKey): View {
        val view = ToggleButton(context, null, 0, selectStyle(key))
        view.isClickable = true

        if (key.icon != null) {
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(key.icon, 0, 0, 0)
            view.contentDescription = getDescription(key)
        } else {
            val label = getLabel(key)
            view.text = label
            view.textOff = label
            view.textOn = label
        }

        return view
    }

    private fun selectStyle(key: VirtualKey): Int {
        if (key == VirtualKey.CloseKeys || key == VirtualKey.ToggleKeyboard)
            return R.style.VirtualKey_Special

        if (key.isToggle) {
            return if (key.icon != null) R.style.VirtualKey_Toggle_Image else R.style.VirtualKey_Toggle
        }

        return R.style.VirtualKey
    }

    private fun getLabel(virtualKey: VirtualKey) = virtualKey.label ?: virtualKey.name
    private fun getDescription(virtualKey: VirtualKey) = virtualKey.description ?: getLabel(virtualKey)
}

/**
 * Simple extension to add hook for Copy action.
 */
class VkEditText(context: Context, attributeSet: AttributeSet? = null) : AppCompatEditText(context, attributeSet) {

    init {
        // inputType=textMultiLine resets XML's line/scroll flags during inflation.
        // Keep newlines in the draft, but display one compact, scrollable row.
        maxLines = 1
        setHorizontallyScrolling(true)
    }

    var onTextCopyListener: (() -> Unit)? = null

    override fun onTextContextMenuItem(id: Int): Boolean {
        val result = super.onTextContextMenuItem(id)
        if (result && (id == android.R.id.cut || id == android.R.id.copy)) {
            onTextCopyListener?.invoke()
        }
        return result
    }
}

/**
 * Stock [HorizontalScrollView] intercepts all scroll events irrespective of whether
 * it can actually scroll or not. It makes it unsuitable for use as child/parent of
 * another horizontally scrollable View, e.g. ViewPager.
 *
 * [NestableHorizontalScrollView] fixes this by only intercepting events when it is scrollable.
 */
class NestableHorizontalScrollView(context: Context, attributeSet: AttributeSet? = null) :
        HorizontalScrollView(context, attributeSet) {
    /**
     * Direction of current horizontal scrolling.
     * See [canScrollHorizontally].
     */
    private var hScrollDirection = 0
    private val gestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            hScrollDirection = 0
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            hScrollDirection = distanceX.sign.toInt()
            return true
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        if (hScrollDirection != 0 && !canScrollHorizontally(hScrollDirection))
            return false

        return super.onInterceptTouchEvent(ev)
    }
}
