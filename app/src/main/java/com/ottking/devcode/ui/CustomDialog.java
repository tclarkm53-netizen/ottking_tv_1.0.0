package com.ottking.devcode.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ottking.devcode.R;
import com.ottking.devcode.utils.UIUtils;

public class CustomDialog {

    public interface OnClickListener {
        void onClick(Dialog dialog);
    }

    public static class Builder {
        private final Context context;
        private String title;
        private String message;
        private int iconResId = R.drawable.ic_info;
        private String positiveText;
        private OnClickListener positiveListener;
        private String neutralText;
        private OnClickListener neutralListener;
        private String negativeText;
        private OnClickListener negativeListener;
        private View customView;
        private boolean cancelable = true;
        private float widthPercent = 0.50f;
        private int backgroundDrawableRes = 0;
        private int titleTextColor = 0;
        private int positiveButtonDrawableRes = 0;

        public Builder(@NonNull Context context) {
            this.context = context;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder setIcon(int iconResId) {
            this.iconResId = iconResId;
            return this;
        }

        public Builder setPositiveButton(String text, OnClickListener listener) {
            this.positiveText = text;
            this.positiveListener = listener;
            return this;
        }

        public Builder setNeutralButton(String text, OnClickListener listener) {
            this.neutralText = text;
            this.neutralListener = listener;
            return this;
        }

        public Builder setNegativeButton(String text, OnClickListener listener) {
            this.negativeText = text;
            this.negativeListener = listener;
            return this;
        }

        public Builder setView(View customView) {
            this.customView = customView;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder setWidthPercent(float widthPercent) {
            this.widthPercent = widthPercent;
            return this;
        }

        public Builder setBackgroundDrawable(int drawableResId) {
            this.backgroundDrawableRes = drawableResId;
            return this;
        }

        public Builder setTitleTextColor(int color) {
            this.titleTextColor = color;
            return this;
        }

        public Builder setPositiveButtonDrawable(int drawableResId) {
            this.positiveButtonDrawableRes = drawableResId;
            return this;
        }

        private View findFirstFocusable(View view) {
            if (view == null || view.getVisibility() != View.VISIBLE) return null;
            if (view.isFocusable() && (view instanceof android.widget.EditText || view instanceof android.widget.Button || view instanceof androidx.appcompat.widget.SwitchCompat || view instanceof android.widget.RadioGroup || view instanceof android.widget.Spinner || view.isClickable())) {
                return view;
            }
            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = findFirstFocusable(vg.getChildAt(i));
                    if (child != null) return child;
                }
            }
            return null;
        }

        public Dialog create() {
            Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View view = LayoutInflater.from(context).inflate(R.layout.dialog_custom, null);
            dialog.setContentView(view);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setGravity(android.view.Gravity.CENTER);
                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                int width = (int) (metrics.widthPixels * widthPercent);
                dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            dialog.setCancelable(cancelable);

            View dialogRoot = view.findViewById(R.id.dialogRoot);
            if (dialogRoot != null && backgroundDrawableRes != 0) {
                dialogRoot.setBackgroundResource(backgroundDrawableRes);
            }

            ImageView imgIcon = view.findViewById(R.id.dialogIcon);
            TextView txtTitle = view.findViewById(R.id.dialogTitle);
            TextView txtMessage = view.findViewById(R.id.dialogMessage);
            FrameLayout customContainer = view.findViewById(R.id.dialogCustomContainer);
            Button btnNegative = view.findViewById(R.id.btnDialogNegative);
            Button btnNeutral = view.findViewById(R.id.btnDialogNeutral);
            Button btnPositive = view.findViewById(R.id.btnDialogPositive);
            ImageView btnClose = view.findViewById(R.id.btnCloseDialog);

            if (btnClose != null) {
                btnClose.setOnClickListener(v -> dialog.dismiss());
            }

            imgIcon.setImageResource(iconResId);

            if (title != null && !title.isEmpty()) {
                txtTitle.setText(title);
                if (titleTextColor != 0) {
                    txtTitle.setTextColor(titleTextColor);
                }
            } else {
                txtTitle.setVisibility(View.GONE);
            }

            if (positiveButtonDrawableRes != 0) {
                btnPositive.setBackgroundResource(positiveButtonDrawableRes);
            }

            if (message != null && !message.isEmpty()) {
                txtMessage.setText(message);
                txtMessage.setVisibility(View.VISIBLE);
            } else {
                txtMessage.setVisibility(View.GONE);
            }

            if (customView != null) {
                customContainer.removeAllViews();
                customContainer.addView(customView);
                customContainer.setVisibility(View.VISIBLE);
            } else {
                customContainer.setVisibility(View.GONE);
            }

            if (positiveText != null && !positiveText.isEmpty()) {
                btnPositive.setText(positiveText);
                btnPositive.setVisibility(View.VISIBLE);
                btnPositive.setOnClickListener(v -> {
                    if (positiveListener != null) {
                        positiveListener.onClick(dialog);
                    } else {
                        dialog.dismiss();
                    }
                });
                UIUtils.applyFocusAnimation(btnPositive, 1.06f, 8f);
            } else {
                btnPositive.setVisibility(View.GONE);
            }

            if (neutralText != null && !neutralText.isEmpty()) {
                btnNeutral.setText(neutralText);
                btnNeutral.setVisibility(View.VISIBLE);
                btnNeutral.setOnClickListener(v -> {
                    if (neutralListener != null) {
                        neutralListener.onClick(dialog);
                    } else {
                        dialog.dismiss();
                    }
                });
                UIUtils.applyFocusAnimation(btnNeutral, 1.06f, 8f);
            } else {
                btnNeutral.setVisibility(View.GONE);
            }

            if (negativeText != null && !negativeText.isEmpty()) {
                btnNegative.setText(negativeText);
                btnNegative.setVisibility(View.VISIBLE);
                btnNegative.setOnClickListener(v -> {
                    if (negativeListener != null) {
                        negativeListener.onClick(dialog);
                    } else {
                        dialog.dismiss();
                    }
                });
                UIUtils.applyFocusAnimation(btnNegative, 1.06f, 8f);
            } else {
                btnNegative.setVisibility(View.GONE);
            }

            dialog.setOnShowListener(d -> {
                View pos = view.findViewById(R.id.btnDialogPositive);
                View neg = view.findViewById(R.id.btnDialogNegative);
                View custom = view.findViewById(R.id.dialogCustomContainer);

                View focusTarget = null;
                if (custom != null && custom.getVisibility() == View.VISIBLE) {
                    focusTarget = findFirstFocusable(custom);
                }
                if (focusTarget == null && pos != null && pos.getVisibility() == View.VISIBLE) {
                    focusTarget = pos;
                }
                if (focusTarget == null && neg != null && neg.getVisibility() == View.VISIBLE) {
                    focusTarget = neg;
                }
                if (focusTarget != null) {
                    focusTarget.requestFocus();
                }
            });

            return dialog;
        }

        public Dialog show() {
            Dialog dialog = create();
            dialog.show();
            return dialog;
        }
    }
}
