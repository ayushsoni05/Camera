package com.example.camera;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class MultiTouchListener implements View.OnTouchListener {

    private static final int INVALID_POINTER_ID = -1;
    private int activePointerId = INVALID_POINTER_ID;
    private float prevX, prevY;
    private ScaleGestureDetector scaleDetector;
    private RotateGestureDetector rotateDetector;

    public MultiTouchListener() {
        scaleDetector = new ScaleGestureDetector();
        rotateDetector = new RotateGestureDetector();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        scaleDetector.onTouchEvent(view, event);
        rotateDetector.onTouchEvent(view, event);

        int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                prevX = event.getRawX();
                prevY = event.getRawY();
                activePointerId = event.getPointerId(0);
                view.bringToFront();
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex != -1) {
                    float currX = event.getRawX();
                    float currY = event.getRawY();

                    float deltaX = currX - prevX;
                    float deltaY = currY - prevY;

                    // Drag translations
                    view.setTranslationX(view.getTranslationX() + deltaX);
                    view.setTranslationY(view.getTranslationY() + deltaY);

                    prevX = currX;
                    prevY = currY;
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                activePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                int pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    prevX = event.getX(newPointerIndex);
                    prevY = event.getY(newPointerIndex);
                    activePointerId = event.getPointerId(newPointerIndex);
                }
                break;
            }
        }
        return true;
    }

    // A helper to detect scaling pinch gestures
    private static class ScaleGestureDetector {
        private float fX, fY, sX, sY;
        private float initDistance;

        void onTouchEvent(View view, MotionEvent event) {
            int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        fX = event.getX(0);
                        fY = event.getY(0);
                        sX = event.getX(1);
                        sY = event.getY(1);
                        initDistance = (float) Math.hypot(fX - sX, fY - sY);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 2 && initDistance > 10) {
                        float nfX = event.getX(0);
                        float nfY = event.getY(0);
                        float nsX = event.getX(1);
                        float nsY = event.getY(1);
                        float newDistance = (float) Math.hypot(nfX - nsX, nfY - nsY);
                        float scale = newDistance / initDistance;
                        
                        float scaleX = view.getScaleX() * scale;
                        float scaleY = view.getScaleY() * scale;
                        
                        // Limit scales to prevent vanishing / filling screen
                        scaleX = Math.max(0.4f, Math.min(scaleX, 6.0f));
                        scaleY = Math.max(0.4f, Math.min(scaleY, 6.0f));

                        view.setScaleX(scaleX);
                        view.setScaleY(scaleY);
                        initDistance = newDistance;
                    }
                    break;
            }
        }
    }

    // A helper to detect two-finger rotation gestures
    private static class RotateGestureDetector {
        private float startAngle = 0;

        void onTouchEvent(View view, MotionEvent event) {
            int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        startAngle = getAngle(event);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 2) {
                        float angle = getAngle(event);
                        float rotation = view.getRotation() + (angle - startAngle);
                        view.setRotation(rotation);
                        startAngle = angle;
                    }
                    break;
            }
        }

        private float getAngle(MotionEvent event) {
            double deltaX = (event.getX(0) - event.getX(1));
            double deltaY = (event.getY(0) - event.getY(1));
            double radians = Math.atan2(deltaY, deltaX);
            return (float) Math.toDegrees(radians);
        }
    }
}
