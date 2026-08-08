package com.ottking.devcode.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.ottking.devcode.R;

public class DotProgressBar extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int dotCount = 5;
    private int activeDotIndex = 0;
    private float dotRadius = 10f;
    private float dotMargin = 20f;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable animateRunnable = new Runnable() {
        @Override
        public void run() {
            activeDotIndex = (activeDotIndex + 1) % dotCount;
            invalidate();
            handler.postDelayed(this, 250);
        }
    };

    public DotProgressBar(Context context) {
        super(context);
        init();
    }

    public DotProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DotProgressBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(animateRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(animateRunnable);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float totalWidth = (dotCount * dotRadius * 2) + ((dotCount - 1) * dotMargin);
        float startX = (getWidth() - totalWidth) / 2f + dotRadius;
        float centerY = getHeight() / 2f;

        int activeColor = getContext().getColor(R.color.gold_primary);
        int inactiveColor = getContext().getColor(R.color.card_bg_stroke);

        for (int i = 0; i < dotCount; i++) {
            float x = startX + i * (dotRadius * 2 + dotMargin);
            if (i == activeDotIndex) {
                paint.setColor(activeColor);
                canvas.drawCircle(x, centerY, dotRadius * 1.4f, paint);
            } else {
                paint.setColor(inactiveColor);
                canvas.drawCircle(x, centerY, dotRadius, paint);
            }
        }
    }
}
