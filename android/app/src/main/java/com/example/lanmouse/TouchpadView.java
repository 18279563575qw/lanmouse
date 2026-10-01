package com.example.lanmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class TouchpadView extends View {
    public interface Listener {
        void onMove(float dx, float dy);

        void onButton(String button, String action);

        void onScroll(int delta);
    }

    private static final int INVALID_POINTER = -1;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path clipPath = new Path();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Listener listener;
    private float sensitivity = 1.5f;
    private float touchSlop;
    private float scrollStep;
    private int activePointerId = INVALID_POINTER;
    private float lastX;
    private float lastY;
    private float movedDistance;
    private long downTime;
    private boolean twoFingerActive;
    private boolean twoFingerMoved;
    private long twoFingerDownTime;
    private float lastCentroidY;
    private float scrollAccumulator;
    private boolean dragging;
    private boolean hapticsEnabled = true;
    private boolean touchActive;
    private float touchX;
    private float touchY;

    private final Runnable dragStartRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isEnabled() || twoFingerActive || activePointerId == INVALID_POINTER) {
                return;
            }
            dragging = true;
            dispatchHaptic(HapticFeedbackConstants.LONG_PRESS);
            if (listener != null) {
                listener.onButton("left", "down");
            }
        }
    };

    public TouchpadView(Context context) {
        super(context);
        initialize();
    }

    private void initialize() {
        setFocusable(true);
        setClickable(true);
        setHapticFeedbackEnabled(true);
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        float density = getResources().getDisplayMetrics().density;
        scrollStep = 20f * density;

        backgroundPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Math.max(1f, density));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(Math.max(1f, density));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void setHapticFeedbackEnabled(boolean enabled) {
        super.setHapticFeedbackEnabled(enabled);
        hapticsEnabled = enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean wasEnabled = isEnabled();
        super.setEnabled(enabled);
        if (wasEnabled && !enabled) {
            if (dragging && listener != null) {
                listener.onButton("left", "up");
            }
            resetTouchState();
        }
        invalidate();
    }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = Math.max(0.25f, Math.min(3f, sensitivity));
    }

    public float getSensitivity() {
        return sensitivity;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                lastX = event.getX(0);
                lastY = event.getY(0);
                touchX = lastX;
                touchY = lastY;
                touchActive = true;
                movedDistance = 0f;
                downTime = event.getEventTime();
                twoFingerActive = false;
                twoFingerMoved = false;
                scrollAccumulator = 0f;
                dragging = false;
                handler.removeCallbacks(dragStartRunnable);
                handler.postDelayed(dragStartRunnable, ViewConfiguration.getLongPressTimeout());
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2) {
                    handler.removeCallbacks(dragStartRunnable);
                    twoFingerActive = true;
                    twoFingerMoved = false;
                    twoFingerDownTime = event.getEventTime();
                    lastCentroidY = centroidY(event);
                    scrollAccumulator = 0f;
                    touchX = centroidX(event);
                    touchY = lastCentroidY;
                    touchActive = true;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (twoFingerActive) {
                    if (event.getPointerCount() >= 2) {
                        float centroid = centroidY(event);
                        float delta = centroid - lastCentroidY;
                        twoFingerMoved = twoFingerMoved || Math.abs(delta) > touchSlop * 0.25f;
                        scrollAccumulator += delta;
                        lastCentroidY = centroid;
                        touchX = centroidX(event);
                        touchY = centroid;
                        invalidate();

                        while (Math.abs(scrollAccumulator) >= scrollStep) {
                            int clicks = (int) (scrollAccumulator / scrollStep);
                            scrollAccumulator -= clicks * scrollStep;
                            if (clicks != 0) {
                                if (listener != null) {
                                    listener.onScroll(clicks * 120);
                                }
                                dispatchHaptic(HapticFeedbackConstants.CLOCK_TICK);
                            }
                        }
                    }
                    return true;
                }

                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex < 0) {
                    return true;
                }

                float x = event.getX(pointerIndex);
                float y = event.getY(pointerIndex);
                float dx = x - lastX;
                float dy = y - lastY;
                lastX = x;
                lastY = y;
                touchX = x;
                touchY = y;
                movedDistance += Math.abs(dx) + Math.abs(dy);
                invalidate();

                if (!dragging && movedDistance > touchSlop) {
                    handler.removeCallbacks(dragStartRunnable);
                }

                if (listener != null && (Math.abs(dx) >= 0.01f || Math.abs(dy) >= 0.01f)) {
                    listener.onMove(dx * sensitivity, dy * sensitivity);
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                return true;

            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(dragStartRunnable);
                long duration = event.getEventTime() - downTime;

                if (dragging) {
                    if (listener != null) {
                        listener.onButton("left", "up");
                    }
                } else if (twoFingerActive) {
                    long twoFingerDuration = event.getEventTime() - twoFingerDownTime;
                    if (!twoFingerMoved && twoFingerDuration < 350
                            && movedDistance < touchSlop * 2f) {
                        performClick();
                        dispatchHaptic(HapticFeedbackConstants.CONTEXT_CLICK);
                        if (listener != null) {
                            listener.onButton("right", "click");
                        }
                    }
                } else if (duration < 320 && movedDistance < touchSlop) {
                    performClick();
                    dispatchHaptic(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (listener != null) {
                        listener.onButton("left", "click");
                    }
                }

                resetTouchState();
                return true;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(dragStartRunnable);
                if (dragging && listener != null) {
                    listener.onButton("left", "up");
                }
                resetTouchState();
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void dispatchHaptic(int feedbackConstant) {
        if (hapticsEnabled) {
            performHapticFeedback(feedbackConstant);
        }
    }

    private float centroidX(MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            sum += event.getX(i);
        }
        return sum / event.getPointerCount();
    }

    private float centroidY(MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            sum += event.getY(i);
        }
        return sum / event.getPointerCount();
    }

    private void resetTouchState() {
        handler.removeCallbacks(dragStartRunnable);
        activePointerId = INVALID_POINTER;
        twoFingerActive = false;
        twoFingerMoved = false;
        dragging = false;
        scrollAccumulator = 0f;
        touchActive = false;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(dragStartRunnable);
        if (dragging && listener != null) {
            listener.onButton("left", "up");
        }
        resetTouchState();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float inset = density * 1.5f;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float radius = 18f * density;

        boolean enabled = isEnabled();
        backgroundPaint.setColor(getContext().getColor(
                enabled ? R.color.touchpad : R.color.touchpad_disabled));
        canvas.drawRoundRect(bounds, radius, radius, backgroundPaint);

        clipPath.reset();
        clipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);

        if (enabled && touchActive) {
            int accent = getContext().getColor(R.color.accent);
            glowPaint.setStyle(Paint.Style.FILL);
            glowPaint.setColor(colorWithAlpha(accent, 0.045f));
            canvas.drawCircle(touchX, touchY, density * 76f, glowPaint);
            glowPaint.setColor(colorWithAlpha(accent, 0.065f));
            canvas.drawCircle(touchX, touchY, density * 54f, glowPaint);
            glowPaint.setColor(colorWithAlpha(accent, 0.09f));
            canvas.drawCircle(touchX, touchY, density * 34f, glowPaint);
        }

        gridPaint.setColor(getContext().getColor(
                enabled ? R.color.touchpad_grid : R.color.touchpad_grid_disabled));
        float step = 42f * density;
        for (float x = bounds.left + step; x < bounds.right; x += step) {
            canvas.drawLine(x, bounds.top + density * 12f, x, bounds.bottom - density * 12f,
                    gridPaint);
        }
        for (float y = bounds.top + step; y < bounds.bottom; y += step) {
            canvas.drawLine(bounds.left + density * 12f, y, bounds.right - density * 12f, y,
                    gridPaint);
        }

        canvas.restore();

        if (!enabled) {
            borderPaint.setColor(getContext().getColor(R.color.outline));
        } else if (touchActive) {
            borderPaint.setColor(getContext().getColor(R.color.outline_focused));
        } else {
            borderPaint.setColor(colorWithAlpha(
                    getContext().getColor(R.color.outline_focused),
                    0.48f));
        }
        borderPaint.setStrokeWidth(density * (touchActive ? 1.6f : 1.1f));
        canvas.drawRoundRect(bounds, radius, radius, borderPaint);
    }

    private int colorWithAlpha(int color, float alpha) {
        return Color.argb(
                Math.round(Color.alpha(color) * alpha),
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }
}
