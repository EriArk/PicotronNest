package art.picotronnest.executionlab;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/** Experimental event adapter attached only by an authenticated keyboard-channel owner. */
final class KeyboardInput implements InputManager.InputDeviceListener {
    private static final int IME_SOURCE = Integer.MIN_VALUE;
    private static final int TOUCH_SOURCE = Integer.MIN_VALUE+1;
    private final KeyboardWriter writer;
    private final KeyboardState state;
    private final InputManager devices;
    private boolean connected = true;
    private int generation;
    private InputConnection latest;
    KeyboardInput(Context context, KeyboardWriter writer) {
        this.writer = writer;
        state = new KeyboardState((scan, down, repeat, modifiers) -> writer.key(scan, down, repeat, modifiers));
        devices = (InputManager)context.getSystemService(Context.INPUT_SERVICE);
        devices.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
    }
    synchronized boolean active() { return connected && writer.failure() == null; }
    synchronized boolean virtualKey(int scan,boolean down,boolean repeat) {
        if (!active()) return false;
        state.key(TOUCH_SOURCE,scan,down,repeat); return writer.failure()==null;
    }
    synchronized boolean virtualHeld(int scan) { return active() && state.held(TOUCH_SOURCE,scan); }
    synchronized boolean event(KeyEvent event) {
        if (!active() || event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) return false;
        // Controller navigation must not silently become editor arrow keys when cursor mode is disabled.
        if (event.isFromSource(android.view.InputDevice.SOURCE_GAMEPAD)
                || event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK)
                || event.isFromSource(android.view.InputDevice.SOURCE_DPAD)) return false;
        int scan = scan(event.getKeyCode());
        if (scan == 0) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        state.key(event.getDeviceId(), scan, down, down && event.getRepeatCount() > 0);
        if (down && !KeyboardState.modifier(scan) && !event.isCtrlPressed() && !event.isAltPressed()
                && !event.isMetaPressed() && (state.modifiers() & 14) == 0) {
            int cp = event.getUnicodeChar();
            // Dead-key composition/AltGr require a separate keyboard-layout probe.
            if (cp >= 32 && (cp & KeyCharacterMap.COMBINING_ACCENT) == 0 && Character.isValidCodePoint(cp))
                writer.text(new String(Character.toChars(cp)));
        }
        return true;
    }
    synchronized void releaseAll() { state.releaseAll(); generation++; }
    synchronized void disconnect() {
        if (!connected) return;
        // The peer is gone; forget local capture without inventing successful releases.
        connected = false; generation++; devices.unregisterInputDeviceListener(this);
    }
    @Override public void onInputDeviceRemoved(int id) {
        // End a modifier-assisted drag before removing that keyboard's modifier ownership.
        DesktopView.releasePointer();
        synchronized (this) { state.releaseDevice(id); }
    }
    @Override public void onInputDeviceAdded(int id) { }
    @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }

    synchronized InputConnection connection(View view, EditorInfo info) {
        if (!active()) return null;
        info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        info.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE;
        info.initialSelStart = info.initialSelEnd = -1;
        latest = new Connection(view, ++generation); return latest;
    }
    synchronized InputConnection latestConnection() { return latest; }
    private final class Connection extends BaseInputConnection {
        private final int token;
        private String composing = "";
        private boolean closed;
        Connection(View view, int token) { super(view, false); this.token = token; }
        private boolean valid() { return !closed && active() && token == generation; }
        @Override public boolean commitText(CharSequence value, int position) {
            synchronized (KeyboardInput.this) {
                if (!valid() || value == null || position != 1) return false;
                try { if (!writer.text(value.toString())) return false; }
                catch (IllegalArgumentException invalid) { return false; }
                composing = ""; return true;
            }
        }
        @Override public boolean setComposingText(CharSequence value, int position) {
            synchronized (KeyboardInput.this) {
                if (!valid() || value == null || position != 1) return false;
                try { KeyboardProtocol.text(value.toString()); }
                catch (IllegalArgumentException invalid) { return false; }
                composing = value.toString(); return true;
            }
        }
        @Override public boolean finishComposingText() {
            synchronized (KeyboardInput.this) { return valid() && commitText(composing, 1); }
        }
        @Override public boolean deleteSurroundingText(int before, int after) {
            synchronized (KeyboardInput.this) {
                if (!valid() || before < 0 || after < 0 || before > 32 || after > 32 || before + after > 32) return false;
                if (!composing.isEmpty()) {
                    if (after != 0) return false;
                    int remove=Math.min(before,composing.codePointCount(0,composing.length()));
                    composing = composing.substring(0, composing.offsetByCodePoints(composing.length(), -remove));
                    return true;
                }
                for (int i=0;i<before+after;i++) {
                    int scan=i<before ? 42 : 76;
                    state.key(IME_SOURCE, scan, true, true); state.key(IME_SOURCE, scan, false, false);
                }
                return writer.failure()==null;
            }
        }
        @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
            return deleteSurroundingText(before, after);
        }
        @Override public boolean sendKeyEvent(KeyEvent event) {
            synchronized (KeyboardInput.this) { return valid() && KeyboardInput.this.event(event); }
        }
        @Override public boolean performEditorAction(int action) {
            synchronized (KeyboardInput.this) {
                if (!valid()) return false;
                state.key(IME_SOURCE, 40, true, true); state.key(IME_SOURCE, 40, false, false); return true;
            }
        }
        @Override public CharSequence getTextBeforeCursor(int length, int flags) { return ""; }
        @Override public CharSequence getTextAfterCursor(int length, int flags) { return ""; }
        @Override public CharSequence getSelectedText(int flags) { return null; }
        @Override public boolean setSelection(int start, int end) { return false; }
        @Override public boolean setComposingRegion(int start, int end) { return false; }
        @Override public void closeConnection() {
            synchronized (KeyboardInput.this) { closed = true; composing = ""; }
        }
    }
    private static int scan(int code) {
        if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) return 4 + code - KeyEvent.KEYCODE_A;
        if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9) return 30 + code - KeyEvent.KEYCODE_1;
        if (code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12) return 58 + code - KeyEvent.KEYCODE_F1;
        switch (code) {
            case KeyEvent.KEYCODE_0: return 39; case KeyEvent.KEYCODE_ENTER: return 40;
            case KeyEvent.KEYCODE_ESCAPE: return 41; case KeyEvent.KEYCODE_DEL: return 42;
            case KeyEvent.KEYCODE_TAB: return 43; case KeyEvent.KEYCODE_SPACE: return 44;
            case KeyEvent.KEYCODE_MINUS: return 45; case KeyEvent.KEYCODE_EQUALS: return 46;
            case KeyEvent.KEYCODE_LEFT_BRACKET: return 47; case KeyEvent.KEYCODE_RIGHT_BRACKET: return 48;
            case KeyEvent.KEYCODE_BACKSLASH: return 49; case KeyEvent.KEYCODE_SEMICOLON: return 51;
            case KeyEvent.KEYCODE_APOSTROPHE: return 52; case KeyEvent.KEYCODE_GRAVE: return 53;
            case KeyEvent.KEYCODE_COMMA: return 54; case KeyEvent.KEYCODE_PERIOD: return 55;
            case KeyEvent.KEYCODE_SLASH: return 56; case KeyEvent.KEYCODE_INSERT: return 73;
            case KeyEvent.KEYCODE_MOVE_HOME: return 74; case KeyEvent.KEYCODE_PAGE_UP: return 75;
            case KeyEvent.KEYCODE_FORWARD_DEL: return 76; case KeyEvent.KEYCODE_MOVE_END: return 77;
            case KeyEvent.KEYCODE_PAGE_DOWN: return 78; case KeyEvent.KEYCODE_DPAD_RIGHT: return 79;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 80; case KeyEvent.KEYCODE_DPAD_DOWN: return 81;
            case KeyEvent.KEYCODE_DPAD_UP: return 82; case KeyEvent.KEYCODE_CTRL_LEFT: return 224;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return 225; case KeyEvent.KEYCODE_ALT_LEFT: return 226;
            case KeyEvent.KEYCODE_META_LEFT: return 227; case KeyEvent.KEYCODE_CTRL_RIGHT: return 228;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return 229; case KeyEvent.KEYCODE_ALT_RIGHT: return 230;
            case KeyEvent.KEYCODE_META_RIGHT: return 231; default: return 0;
        }
    }
}
