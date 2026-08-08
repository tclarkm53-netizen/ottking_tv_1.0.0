package com.ottking.devcode.utils;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.ottking.devcode.viewmodel.FocusViewModel;

/**
 * Centralized FocusManager class that saves and restores focus state using FocusViewModel.
 * Ensures that returning from sub-menus or refreshing lists restores focus to the previously active element.
 */
public class FocusManager {

    private static FocusManager instance;

    private FocusManager() {}

    public static synchronized FocusManager getInstance() {
        if (instance == null) {
            instance = new FocusManager();
        }
        return instance;
    }

    /**
     * Obtains the FocusViewModel associated with the given activity.
     */
    public FocusViewModel getViewModel(AppCompatActivity activity) {
        if (activity == null) return null;
        return new ViewModelProvider(activity).get(FocusViewModel.class);
    }

    /**
     * Attaches a focus change listener to automatically record focus and trigger animation.
     */
    public void trackFocus(AppCompatActivity activity, String screenKey, View view) {
        trackFocus(activity, screenKey, view, null);
    }

    /**
     * Attaches a focus change listener with an optional custom focus callback.
     */
    public void trackFocus(AppCompatActivity activity, String screenKey, View view, OnFocusChangedListener listener) {
        if (view == null || activity == null) return;
        view.setOnFocusChangeListener((v, hasFocus) -> {
            UIUtils.animateFocus(v, hasFocus);
            if (hasFocus) {
                saveFocus(activity, screenKey, v);
            }
            if (listener != null) {
                listener.onFocusChanged(v, hasFocus);
            }
        });
    }

    /**
     * Explicitly saves the focused view state to the FocusViewModel.
     */
    public void saveFocus(AppCompatActivity activity, String screenKey, View view) {
        if (activity == null || view == null) return;
        FocusViewModel vm = getViewModel(activity);
        if (vm == null) return;

        if (view.getId() != View.NO_ID) {
            vm.saveFocusId(screenKey, view.getId());
        }
        if (view.getTag() != null) {
            vm.saveFocusTag(screenKey, view.getTag().toString());
        }
    }

    /**
     * Saves the focused item position for a specific group (e.g., RecyclerView).
     */
    public void savePosition(AppCompatActivity activity, String screenKey, String groupKey, int position) {
        if (activity == null) return;
        FocusViewModel vm = getViewModel(activity);
        if (vm != null) {
            vm.saveFocusPosition(screenKey, groupKey, position);
        }
    }

    /**
     * Attempts to restore focus to the previously focused view on the screen.
     * Returns true if focus was successfully restored.
     */
    public boolean restoreFocus(AppCompatActivity activity, String screenKey, View defaultFallbackView) {
        if (activity == null) return false;
        FocusViewModel vm = getViewModel(activity);
        if (vm == null || !vm.hasSavedFocus(screenKey)) {
            if (defaultFallbackView != null && defaultFallbackView.getVisibility() == View.VISIBLE) {
                return defaultFallbackView.requestFocus();
            }
            return false;
        }

        // Try restoring by saved View ID
        int savedId = vm.getLastFocusId(screenKey, View.NO_ID);
        if (savedId != View.NO_ID) {
            View targetView = activity.findViewById(savedId);
            if (targetView != null && targetView.getVisibility() == View.VISIBLE && targetView.isFocusable()) {
                if (targetView.requestFocus()) {
                    return true;
                }
            }
        }

        // Try restoring by saved View Tag
        String savedTag = vm.getLastFocusTag(screenKey);
        if (savedTag != null) {
            View root = activity.findViewById(android.R.id.content);
            if (root != null) {
                View targetView = root.findViewWithTag(savedTag);
                if (targetView != null && targetView.getVisibility() == View.VISIBLE && targetView.isFocusable()) {
                    if (targetView.requestFocus()) {
                        return true;
                    }
                }
            }
        }

        // Fallback to default view
        if (defaultFallbackView != null && defaultFallbackView.getVisibility() == View.VISIBLE) {
            return defaultFallbackView.requestFocus();
        }

        return false;
    }

    /**
     * Restores focus to a specific item position inside a RecyclerView.
     */
    public boolean restoreRecyclerViewFocus(AppCompatActivity activity, String screenKey, String groupKey, RecyclerView recyclerView, int defaultPosition) {
        if (activity == null || recyclerView == null || recyclerView.getAdapter() == null) return false;
        FocusViewModel vm = getViewModel(activity);
        if (vm == null) return false;

        int targetPosition = vm.getLastFocusPosition(screenKey, groupKey, defaultPosition);
        if (targetPosition >= 0 && targetPosition < recyclerView.getAdapter().getItemCount()) {
            recyclerView.scrollToPosition(targetPosition);
            recyclerView.post(() -> {
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(targetPosition);
                if (holder != null && holder.itemView != null) {
                    holder.itemView.requestFocus();
                }
            });
            return true;
        }
        return false;
    }

    /**
     * Interface for intercepting back press events within a screen.
     */
    public interface BackPressInterceptor {
        /**
         * @return true if the back press was handled/consumed internally in the screen (e.g., closing sub-menus or stepping back focus),
         *         false to proceed with standard navigation/finish.
         */
        boolean onBackPress();
    }

    /**
     * Registers a global BackPress callback on the activity that integrates with FocusManager.
     */
    public void setupBackPressHandler(AppCompatActivity activity, String screenKey, BackPressInterceptor interceptor) {
        if (activity == null) return;
        activity.getOnBackPressedDispatcher().addCallback(activity, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress(activity, screenKey, interceptor, this);
            }
        });
    }

    /**
     * Centralized back press handler for TV remote KEYCODE_BACK and back events.
     */
    public boolean handleBackPress(AppCompatActivity activity, String screenKey, BackPressInterceptor interceptor, androidx.activity.OnBackPressedCallback callback) {
        if (activity == null) return false;

        // Save current focused view state before processing back action
        View currentFocus = activity.getCurrentFocus();
        if (currentFocus != null) {
            saveFocus(activity, screenKey, currentFocus);
        }

        // Try local interceptor (sub-menu or overlay step back)
        if (interceptor != null && interceptor.onBackPress()) {
            return true;
        }

        // If not consumed by local interceptor, proceed with back navigation
        if (callback != null) {
            callback.setEnabled(false);
            activity.getOnBackPressedDispatcher().onBackPressed();
            callback.setEnabled(true);
        } else {
            activity.finish();
        }
        return true;
    }

    public interface OnFocusChangedListener {
        void onFocusChanged(View view, boolean hasFocus);
    }
}
