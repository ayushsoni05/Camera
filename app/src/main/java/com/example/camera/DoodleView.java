package com.example.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom drawing/doodle canvas for the post-capture screen.
 * Supports freehand drawing with customizable color and brush size.
 */
public class DoodleView extends View {

    private final Paint drawPaint = new Paint();
    private final List<DrawStroke> strokes = new ArrayList<>();
    private Path currentPath;
    private int currentColor = Color.parseColor("#FF2D55");
    private float brushSize = 8f;

    private static class DrawStroke {
        Path path;
        int color;
        float width;
        DrawStroke(Path p, int c, float w) {
            this.path = p;
            this.color = c;
            this.width = w;
        }
    }

    public DoodleView(Context context) {
        super(context);
        init();
    }

    public DoodleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DoodleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        drawPaint.setAntiAlias(true);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setDrawColor(int color) {
        this.currentColor = color;
    }

    public void setBrushSize(float size) {
        this.brushSize = size;
    }

    public void clearCanvas() {
        strokes.clear();
        invalidate();
    }

    public void undoLast() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
        }
    }

    public Bitmap exportBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        draw(c);
        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (DrawStroke stroke : strokes) {
            drawPaint.setColor(stroke.color);
            drawPaint.setStrokeWidth(stroke.width);
            canvas.drawPath(stroke.path, drawPaint);
        }
        if (currentPath != null) {
            drawPaint.setColor(currentColor);
            drawPaint.setStrokeWidth(brushSize);
            canvas.drawPath(currentPath, drawPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath = new Path();
                currentPath.moveTo(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (currentPath != null) {
                    currentPath.lineTo(x, y);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (currentPath != null) {
                    strokes.add(new DrawStroke(currentPath, currentColor, brushSize));
                    currentPath = null;
                    invalidate();
                }
                return true;
        }
        return false;
    }
}
