package com.ottking.devcode.utils;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class InputFocusHelper {
    private static final long DOUBLE_TAP_WINDOW_MS = 280L;
    private static final Map<EditText, Long> LAST_TAP_TIMES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<EditText, Boolean> ACTIVE_INPUTS = Collections.synchronizedMap(new WeakHashMap<>());

    private InputFocusHelper() {
    }

    public static void bind(EditText editText, Context context, Runnable onActivated, Runnable onFocused) {
        if (editText == null || context == null) return;

        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.setCursorVisible(false);

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            EditText input = (EditText) v;
            if (!hasFocus) {
                input.setCursorVisible(false);
                setActive(input, false);
                hideKeyboard(context, input);
            } else {
                boolean isActive = Boolean.TRUE.equals(ACTIVE_INPUTS.get(input));
                if (isActive) {
                    input.setCursorVisible(true);
                } else {
                    input.setCursorVisible(false);
                    hideKeyboard(context, input);
                }
                if (onFocused != null) onFocused.run();
            }
        });

        editText.setOnTouchListener((v, event) -> {
            EditText input = (EditText) v;
            if (event == null) return false;

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                input.setFocusableInTouchMode(true);
                input.requestFocus();
                input.setCursorVisible(false);
                setActive(input, false);
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                long now = SystemClock.elapsedRealtime();
                Long lastTapTime = LAST_TAP_TIMES.get(input);
                boolean isDoubleTap = lastTapTime != null && (now - lastTapTime) <= DOUBLE_TAP_WINDOW_MS;
                LAST_TAP_TIMES.put(input, now);

                if (isDoubleTap) {
                    activate(context, input, onActivated);
                } else {
                    input.setFocusableInTouchMode(true);
                    input.requestFocus();
                    input.setCursorVisible(false);
                    setActive(input, false);
                    hideKeyboard(context, input);
                    if (onFocused != null) onFocused.run();
                }
                return true;
            }

            return false;
        });

        editText.setOnKeyListener((v, keyCode, event) -> {
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                activate(context, (EditText) v, onActivated);
                return true;
            }
            return false;
        });
    }

    public static void activate(Context context, EditText editText, Runnable onActivated) {
        if (editText == null || context == null) return;
        editText.setFocusableInTouchMode(true);
        editText.setCursorVisible(true);
        editText.requestFocus();
        setActive(editText, true);
        if (onActivated != null) onActivated.run();
        showKeyboard(context, editText);
    }

    public static void deactivate(Context context, EditText editText) {
        if (editText == null) return;
        setActive(editText, false);
        editText.setCursorVisible(false);
        hideKeyboard(context, editText);
    }

    private static void setActive(EditText editText, boolean active) {
        ACTIVE_INPUTS.put(editText, active);
    }

    private static void showKeyboard(Context context, EditText editText) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private static void hideKeyboard(Context context, EditText editText) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && editText != null) {
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        }
    }
}
