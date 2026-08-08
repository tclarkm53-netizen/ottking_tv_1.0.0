package com.ottking.devcode.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
public class InputFocusHelperTest {

    @Test
    public void keepsInputInactiveUntilActivation() {
        Context context = ApplicationProvider.getApplicationContext();
        EditText editText = new EditText(context);
        AtomicBoolean activated = new AtomicBoolean(false);

        InputFocusHelper.bind(editText, context, () -> activated.set(true), () -> {});

        editText.requestFocus();
        assertFalse(editText.isCursorVisible());

        InputFocusHelper.activate(context, editText, () -> activated.set(true));

        assertTrue(editText.isCursorVisible());
        assertTrue(activated.get());
    }
}
