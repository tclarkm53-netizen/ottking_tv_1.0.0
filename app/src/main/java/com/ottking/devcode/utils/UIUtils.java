package com.ottking.devcode.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class UIUtils {

    public static void hideSystemUI(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
            controller.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    public static int dpToPx(Context context, int dp) {
        if (context == null) return dp;
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static void applyFocusAnimation(View view) {
        applyFocusAnimation(view, 1.06f, 10f);
    }

    public static void applyFocusAnimation(View view, float scale, float elevationDp) {
        if (view == null) return;
        view.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, scale, elevationDp));
    }

    public static void animateFocus(View v, boolean hasFocus) {
        animateFocus(v, hasFocus, 1.06f, 10f);
    }

    public static void animateFocus(View v, boolean hasFocus, float scale, float elevationDp) {
        if (v == null) return;
        if (v.isInTouchMode()) {
            v.setScaleX(1.0f);
            v.setScaleY(1.0f);
            v.setTranslationZ(0f);
            return;
        }

        float targetScale = hasFocus ? scale : 1.0f;
        float targetElevation = hasFocus ? dpToPx(v.getContext(), (int) elevationDp) : 0f;

        v.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .translationZ(targetElevation)
                .setDuration(90)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }
}
