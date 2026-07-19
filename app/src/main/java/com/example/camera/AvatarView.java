package com.example.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class AvatarView extends View {

    private String skinColor = "#FFE0BD";
    private String hairStyle = "short";
    private String hairColor = "#090806";
    private String outfitColor = "#6366F1";
    private String expression = "happy";

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AvatarView(Context context) {
        super(context);
        init();
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.BLACK);
        strokePaint.setStrokeWidth(2.5f);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        
        fillPaint.setStyle(Paint.Style.FILL);
    }

    public void setAvatarState(String skinColor, String hairStyle, String hairColor, String outfitColor, String expression) {
        this.skinColor = skinColor;
        this.hairStyle = hairStyle;
        this.hairColor = hairColor;
        this.outfitColor = outfitColor;
        this.expression = expression;
        invalidate(); // Redraw view
    }

    public void setAvatarState(AvatarState state) {
        if (state == null) return;
        setAvatarState(state.skinColor, state.hairStyle, state.hairColor, state.outfitColor, state.expression);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Save canvas state and scale to 100x100 virtual grid
        canvas.save();
        canvas.scale(w / 100f, h / 100f);

        drawBody(canvas);
        drawHead(canvas);
        drawFaceDetails(canvas);
        drawHair(canvas);

        canvas.restore();
    }

    private void drawBody(Canvas canvas) {
        // Draw shoulders / clothes
        fillPaint.setColor(Color.parseColor(outfitColor));
        RectF bodyRect = new RectF(16, 72, 84, 120);
        canvas.drawOval(bodyRect, fillPaint);
        canvas.drawOval(bodyRect, strokePaint);
        
        // Draw collar outline
        strokePaint.setStrokeWidth(2.0f);
        canvas.drawArc(new RectF(35, 68, 65, 80), 0, 180, false, strokePaint);
        strokePaint.setStrokeWidth(2.5f);
    }

    private void drawHead(Canvas canvas) {
        // Draw face base
        fillPaint.setColor(Color.parseColor(skinColor));
        canvas.drawCircle(50, 48, 25, fillPaint);
        canvas.drawCircle(50, 48, 25, strokePaint);
        
        // Draw ears
        canvas.drawCircle(24, 48, 4.5f, fillPaint);
        canvas.drawCircle(24, 48, 4.5f, strokePaint);
        canvas.drawCircle(76, 48, 4.5f, fillPaint);
        canvas.drawCircle(76, 48, 4.5f, strokePaint);
    }

    private void drawFaceDetails(Canvas canvas) {
        // Eyebrows
        strokePaint.setStrokeWidth(2.0f);
        strokePaint.setColor(Color.parseColor(hairColor));
        canvas.drawArc(new RectF(33, 35, 45, 41), 180, 180, false, strokePaint);
        canvas.drawArc(new RectF(55, 35, 67, 41), 180, 180, false, strokePaint);

        strokePaint.setColor(Color.BLACK);
        strokePaint.setStrokeWidth(2.5f);

        // Nose
        canvas.drawArc(new RectF(47, 46, 53, 52), 0, 180, false, strokePaint);

        // Expression specific: eyes and mouth
        if (expression.equalsIgnoreCase("cool")) {
            // Draw sunglasses
            fillPaint.setColor(Color.parseColor("#111119"));
            canvas.drawRoundRect(new RectF(30, 39, 47, 48), 4, 4, fillPaint);
            canvas.drawRoundRect(new RectF(30, 39, 47, 48), 4, 4, strokePaint);
            canvas.drawRoundRect(new RectF(53, 39, 70, 48), 4, 4, fillPaint);
            canvas.drawRoundRect(new RectF(53, 39, 70, 48), 4, 4, strokePaint);
            
            // Bridge line
            canvas.drawLine(47, 42, 53, 42, strokePaint);
            
            // Smirk mouth
            canvas.drawArc(new RectF(44, 55, 58, 63), 0, 180, false, strokePaint);

        } else if (expression.equalsIgnoreCase("wink")) {
            // Left eye open
            fillPaint.setColor(Color.BLACK);
            canvas.drawCircle(39, 44, 3f, fillPaint);
            
            // Right eye closed wink arc
            canvas.drawArc(new RectF(57, 40, 67, 47), 180, 180, false, strokePaint);
            
            // Smile mouth
            canvas.drawArc(new RectF(41, 54, 59, 66), 0, 180, false, strokePaint);

        } else if (expression.equalsIgnoreCase("surprised")) {
            // Wide open eyes
            fillPaint.setColor(Color.WHITE);
            canvas.drawCircle(39, 44, 4.5f, fillPaint);
            canvas.drawCircle(39, 44, 4.5f, strokePaint);
            canvas.drawCircle(61, 44, 4.5f, fillPaint);
            canvas.drawCircle(61, 44, 4.5f, strokePaint);
            
            // Small pupil
            fillPaint.setColor(Color.BLACK);
            canvas.drawCircle(39, 44, 1.8f, fillPaint);
            canvas.drawCircle(61, 44, 1.8f, fillPaint);
            
            // Surprised mouth (open circle)
            canvas.drawOval(new RectF(46, 55, 54, 65), fillPaint);

        } else {
            // Default "happy"
            fillPaint.setColor(Color.BLACK);
            canvas.drawCircle(39, 44, 3f, fillPaint);
            canvas.drawCircle(61, 44, 3f, fillPaint);
            
            // Catch light
            fillPaint.setColor(Color.WHITE);
            canvas.drawCircle(38, 43, 0.8f, fillPaint);
            canvas.drawCircle(60, 43, 0.8f, fillPaint);
            
            // Smile
            canvas.drawArc(new RectF(41, 54, 59, 66), 0, 180, false, strokePaint);
        }
    }

    private void drawHair(Canvas canvas) {
        if (hairStyle.equalsIgnoreCase("bald")) return;

        fillPaint.setColor(Color.parseColor(hairColor));

        if (hairStyle.equalsIgnoreCase("short")) {
            // Short hair cap
            canvas.drawArc(new RectF(22, 20, 78, 52), 180, 180, true, fillPaint);
            
            // Sideburns and spikes path
            Path path = new Path();
            path.moveTo(23, 44);
            path.lineTo(26, 32);
            path.lineTo(38, 30);
            path.lineTo(44, 25);
            path.lineTo(50, 31);
            path.lineTo(56, 25);
            path.lineTo(62, 30);
            path.lineTo(74, 32);
            path.lineTo(77, 44);
            path.lineTo(74, 36);
            path.lineTo(66, 24);
            path.lineTo(50, 21);
            path.lineTo(34, 24);
            path.lineTo(26, 36);
            path.close();
            canvas.drawPath(path, fillPaint);
            canvas.drawPath(path, strokePaint);

        } else if (hairStyle.equalsIgnoreCase("medium")) {
            // Side ear covers + hair cap
            canvas.drawArc(new RectF(21, 19, 79, 53), 180, 180, true, fillPaint);
            
            // Left & Right medium flaps
            RectF leftFlap = new RectF(20, 32, 27, 56);
            canvas.drawRoundRect(leftFlap, 4, 4, fillPaint);
            canvas.drawRoundRect(leftFlap, 4, 4, strokePaint);
            
            RectF rightFlap = new RectF(73, 32, 80, 56);
            canvas.drawRoundRect(rightFlap, 4, 4, fillPaint);
            canvas.drawRoundRect(rightFlap, 4, 4, strokePaint);
            
            // Spikes front outline
            Path path = new Path();
            path.moveTo(26, 38);
            path.lineTo(38, 28);
            path.lineTo(50, 31);
            path.lineTo(62, 28);
            path.lineTo(74, 38);
            canvas.drawPath(path, strokePaint);

        } else if (hairStyle.equalsIgnoreCase("long")) {
            // Long hair locks hanging down to shoulders + hair cap
            canvas.drawArc(new RectF(21, 18, 79, 52), 180, 180, true, fillPaint);
            
            // Left & Right long locks
            RectF leftLock = new RectF(18, 30, 26, 75);
            canvas.drawRoundRect(leftLock, 6, 6, fillPaint);
            canvas.drawRoundRect(leftLock, 6, 6, strokePaint);
            
            RectF rightLock = new RectF(74, 30, 82, 75);
            canvas.drawRoundRect(rightLock, 6, 6, fillPaint);
            canvas.drawRoundRect(rightLock, 6, 6, strokePaint);
            
            // Forehead locks
            Path path = new Path();
            path.moveTo(25, 34);
            path.quadTo(38, 26, 50, 34);
            path.quadTo(62, 26, 75, 34);
            canvas.drawPath(path, strokePaint);
        }
    }
}
