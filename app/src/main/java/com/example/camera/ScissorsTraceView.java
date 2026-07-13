package com.example.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ScissorsTraceView extends View {

    public interface ScissorsCropListener {
        void onStickerCropped(Bitmap stickerBitmap);
    }

    private Path tracePath = new Path();
    private Paint tracePaint = new Paint();
    private Bitmap sourceBitmap;
    private ScissorsCropListener listener;

    public ScissorsTraceView(Context context) {
        super(context);
        init();
    }

    public ScissorsTraceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        tracePaint.setAntiAlias(true);
        tracePaint.setColor(Color.parseColor("#00F2FF")); // Cyan neon line
        tracePaint.setStyle(Paint.Style.STROKE);
        tracePaint.setStrokeWidth(8f);
        tracePaint.setStrokeCap(Paint.Cap.ROUND);
        tracePaint.setStrokeJoin(Paint.Join.ROUND);
        // Add dotted stroke shadow/glow effect
        tracePaint.setShadowLayer(6f, 0f, 0f, Color.parseColor("#8000F2FF"));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setSourceBitmap(Bitmap bitmap) {
        this.sourceBitmap = bitmap;
        tracePath.reset();
        invalidate();
    }

    public void setCropListener(ScissorsCropListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(tracePath, tracePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                tracePath.reset();
                tracePath.moveTo(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                tracePath.lineTo(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                tracePath.close();
                cropBitmap();
                tracePath.reset();
                invalidate();
                break;
        }
        return true;
    }

    private void cropBitmap() {
        if (sourceBitmap == null || listener == null) return;

        try {
            // Create a matching transparent sticker bitmap
            Bitmap cropped = Bitmap.createBitmap(sourceBitmap.getWidth(), sourceBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(cropped);

            // Create paint for clipping path
            Paint clipPaint = new Paint();
            clipPaint.setAntiAlias(true);
            clipPaint.setColor(Color.WHITE);

            // Draw shape/path on transparent canvas
            canvas.drawPath(tracePath, clipPaint);

            // Intersect with source image using SRC_IN
            clipPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(sourceBitmap, 0, 0, clipPaint);

            // Extract minimum bounding rect/trim transparency if wanted, 
            // or pass directly (scaled down for sticker placement)
            listener.onStickerCropped(cropped);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
