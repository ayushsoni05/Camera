package com.example.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom drawing/doodle canvas for the post-capture screen.
 * Supports freehand drawing, undo/redo, color change, and JSON serialization.
 */
public class DoodleView extends View {

    private final Paint drawPaint = new Paint();
    private Path currentPath;
    private int currentColor = Color.parseColor("#FF2D55");
    private float brushSize = 8f;

    private final List<DrawStroke> strokes = new ArrayList<>();
    private final List<DrawStroke> undoneStrokes = new ArrayList<>();

    public static class DrawStroke {
        int color;
        float width;
        Path path;
        List<Point> points = new ArrayList<>();

        DrawStroke(int color, float width) {
            this.color = color;
            this.width = width;
            this.path = new Path();
        }
    }

    public static class Point {
        float x;
        float y;
        Point(float x, float y) {
            this.x = x;
            this.y = y;
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
        currentPath = new Path();
    }

    public void setDrawColor(int color) {
        this.currentColor = color;
    }

    public void setBrushSize(float size) {
        this.brushSize = size;
    }

    public void clearCanvas() {
        strokes.clear();
        undoneStrokes.clear();
        currentPath.reset();
        invalidate();
    }

    public android.graphics.Bitmap exportBitmap() {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(getWidth(), getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        draw(c);
        return bitmap;
    }

    public void undoLast() {
        if (!strokes.isEmpty()) {
            undoneStrokes.add(strokes.remove(strokes.size() - 1));
            invalidate();
        }
    }

    public void redoLast() {
        if (!undoneStrokes.isEmpty()) {
            strokes.add(undoneStrokes.remove(undoneStrokes.size() - 1));
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (DrawStroke stroke : strokes) {
            drawPaint.setColor(stroke.color);
            drawPaint.setStrokeWidth(stroke.width);
            canvas.drawPath(stroke.path, drawPaint);
        }
        if (currentPath != null && !currentPath.isEmpty()) {
            drawPaint.setColor(currentColor);
            drawPaint.setStrokeWidth(brushSize);
            canvas.drawPath(currentPath, drawPaint);
        }
    }

    private float lastX, lastY;
    private DrawStroke activeStroke;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getVisibility() != View.VISIBLE) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                undoneStrokes.clear();
                currentPath.reset();
                currentPath.moveTo(x, y);
                lastX = x;
                lastY = y;
                
                activeStroke = new DrawStroke(currentColor, brushSize);
                activeStroke.points.add(new Point(x, y));
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(x - lastX);
                float dy = Math.abs(y - lastY);
                if (dx >= 4 || dy >= 4) {
                    currentPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
                    lastX = x;
                    lastY = y;
                    if (activeStroke != null) {
                        activeStroke.points.add(new Point(x, y));
                    }
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                currentPath.lineTo(x, y);
                if (activeStroke != null) {
                    activeStroke.points.add(new Point(x, y));
                    
                    Path pathCopy = new Path();
                    if (!activeStroke.points.isEmpty()) {
                        Point start = activeStroke.points.get(0);
                        pathCopy.moveTo(start.x, start.y);
                        for (int i = 1; i < activeStroke.points.size(); i++) {
                            Point p = activeStroke.points.get(i);
                            pathCopy.lineTo(p.x, p.y);
                        }
                    }
                    activeStroke.path = pathCopy;
                    strokes.add(activeStroke);
                }
                currentPath.reset();
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    public String getDrawingPathsJson() {
        try {
            JSONArray arr = new JSONArray();
            for (DrawStroke stroke : strokes) {
                JSONObject obj = new JSONObject();
                obj.put("color", stroke.color);
                obj.put("width", stroke.width);
                JSONArray pts = new JSONArray();
                for (Point p : stroke.points) {
                    JSONObject pt = new JSONObject();
                    pt.put("x", p.x);
                    pt.put("y", p.y);
                    pts.put(pt);
                }
                obj.put("points", pts);
                arr.put(obj);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    public void setDrawingPathsFromJson(String json) {
        clearCanvas();
        if (json == null || json.isEmpty() || json.equals("[]")) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                int color = obj.getInt("color");
                float width = (float) obj.getDouble("width");
                JSONArray pts = obj.getJSONArray("points");
                
                DrawStroke stroke = new DrawStroke(color, width);
                Path path = new Path();
                if (pts.length() > 0) {
                    JSONObject first = pts.getJSONObject(0);
                    float fx = (float) first.getDouble("x");
                    float fy = (float) first.getDouble("y");
                    path.moveTo(fx, fy);
                    stroke.points.add(new Point(fx, fy));

                    for (int j = 1; j < pts.length(); j++) {
                        JSONObject p = pts.getJSONObject(j);
                        float px = (float) p.getDouble("x");
                        float py = (float) p.getDouble("y");
                        path.lineTo(px, py);
                        stroke.points.add(new Point(px, py));
                    }
                }
                stroke.path = path;
                strokes.add(stroke);
            }
            invalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
