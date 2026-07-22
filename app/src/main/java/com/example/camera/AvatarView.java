package com.example.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

public class AvatarView extends AppCompatImageView {

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
        
        // Clear previous drawable to show vector fallback while loading
        setImageDrawable(null);
        invalidate();

        // Generate premium DiceBear 10.x PNG URL
        String url = AvatarSvgGenerator.generateUrl(new AvatarState(skinColor, hairStyle, hairColor, outfitColor, expression), "png");
        
        // Load with Glide
        Glide.with(getContext().getApplicationContext())
             .load(url)
             .transform(new CircleCrop())
             .listener(new RequestListener<Drawable>() {
                 @Override
                 public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                     Log.e("AvatarView", "Glide failed loading DiceBear avatar, falling back to local vector drawing", e);
                     // Clear image to force onDraw vector rendering
                     post(() -> {
                         setImageDrawable(null);
                         invalidate();
                     });
                     return false;
                 }

                 @Override
                 public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                     return false; // let Glide draw it
                 }
             })
             .into(this);
    }

    public void setAvatarState(AvatarState state) {
        if (state == null) return;
        setAvatarState(state.skinColor, state.hairStyle, state.hairColor, state.outfitColor, state.expression);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getDrawable() != null) {
            // Draw Glide downloaded image
            super.onDraw(canvas);
        } else {
            // Draw vector fallback programmatically (looks clean and never broken)
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            canvas.save();
            canvas.scale(w / 100f, h / 100f);

            drawBody(canvas);
            drawHead(canvas);
            drawFaceDetails(canvas);
            drawHair(canvas);

            canvas.restore();
        }
    }

    private void drawBody(Canvas canvas) {
        fillPaint.setColor(Color.parseColor(outfitColor));
        RectF bodyRect = new RectF(16, 72, 84, 120);
        canvas.drawOval(bodyRect, fillPaint);
        canvas.drawOval(bodyRect, strokePaint);
        
        strokePaint.setStrokeWidth(2.0f);
        canvas.drawArc(new RectF(35, 68, 65, 80), 0, 180, false, strokePaint);
        strokePaint.setStrokeWidth(2.5f);
    }

    private void drawHead(Canvas canvas) {
        fillPaint.setColor(Color.parseColor(skinColor));
        canvas.drawCircle(50, 48, 25, fillPaint);
        canvas.drawCircle(50, 48, 25, strokePaint);
        
        canvas.drawCircle(24, 48, 4.5f, fillPaint);
        canvas.drawCircle(24, 48, 4.5f, strokePaint);
        canvas.drawCircle(76, 48, 4.5f, fillPaint);
        canvas.drawCircle(76, 48, 4.5f, strokePaint);
    }

    private void drawFaceDetails(Canvas canvas) {
        strokePaint.setStrokeWidth(2.0f);
        strokePaint.setColor(Color.parseColor(hairColor));
        canvas.drawArc(new RectF(33, 35, 45, 41), 180, 180, false, strokePaint);
        canvas.drawArc(new RectF(55, 35, 67, 41), 180, 180, false, strokePaint);

        strokePaint.setColor(Color.BLACK);
        strokePaint.setStrokeWidth(2.5f);

        canvas.drawArc(new RectF(47, 46, 53, 52), 0, 180, false, strokePaint);

        if (expression.equalsIgnoreCase("cool")) {
            fillPaint.setColor(Color.parseColor("#111119"));
            canvas.drawRoundRect(new RectF(30, 39, 47, 48), 4, 4, fillPaint);
            canvas.drawRoundRect(new RectF(30, 39, 47, 48), 4, 4, strokePaint);
            canvas.drawRoundRect(new RectF(53, 39, 70, 48), 4, 4, fillPaint);
            canvas.drawRoundRect(new RectF(53, 39, 70, 48), 4, 4, strokePaint);
            canvas.drawLine(47, 42, 53, 42, strokePaint);
            canvas.drawArc(new RectF(44, 55, 58, 63), 0, 180, false, strokePaint);
        } else if (expression.equalsIgnoreCase("wink")) {
            fillPaint.setColor(Color.BLACK);
            canvas.drawCircle(39, 44, 3f, fillPaint);
            canvas.drawArc(new RectF(57, 40, 67, 47), 180, 180, false, strokePaint);
            canvas.drawArc(new RectF(41, 54, 59, 66), 0, 180, false, strokePaint);
        } else if (expression.equalsIgnoreCase("surprised")) {
            fillPaint.setColor(Color.WHITE);
            canvas.drawCircle(39, 44, 4.5f, fillPaint);
            canvas.drawCircle(39, 44, 4.5f, strokePaint);
            canvas.drawCircle(61, 44, 4.5f, fillPaint);
            canvas.drawCircle(61, 44, 4.5f, strokePaint);
            fillPaint.setColor(Color.BLACK);
            canvas.drawCircle(39, 44, 1.8f, fillPaint);
            canvas.drawCircle(61, 44, 1.8f, fillPaint);
            canvas.drawOval(new RectF(46, 55, 54, 65), fillPaint);
        } else if (expression.equalsIgnoreCase("sleepy")) {
            // Draw closed squinty eyes
            canvas.drawArc(new RectF(34, 41, 44, 47), 180, 180, false, strokePaint);
            canvas.drawArc(new RectF(56, 41, 66, 47), 180, 180, false, strokePaint);
            canvas.drawArc(new RectF(44, 55, 56, 62), 0, 180, false, strokePaint);
        } else if (expression.equalsIgnoreCase("tired")) {
            // Draw tired sad eyes and droopy mouth
            fillPaint.setColor(Color.BLACK);
            canvas.drawCircle(39, 44, 2f, fillPaint);
            canvas.drawCircle(61, 44, 2f, fillPaint);
            canvas.drawArc(new RectF(33, 47, 45, 51), 0, 180, false, strokePaint);
            canvas.drawArc(new RectF(55, 47, 67, 51), 0, 180, false, strokePaint);
            canvas.drawArc(new RectF(43, 56, 57, 66), 180, 180, false, strokePaint);
        } else {
            fillPaint.setColor(Color.BLACK);
            canvas.drawCircle(39, 44, 3f, fillPaint);
            canvas.drawCircle(61, 44, 3f, fillPaint);
            fillPaint.setColor(Color.WHITE);
            canvas.drawCircle(38, 43, 0.8f, fillPaint);
            canvas.drawCircle(60, 43, 0.8f, fillPaint);
            canvas.drawArc(new RectF(41, 54, 59, 66), 0, 180, false, strokePaint);
        }
    }

    private void drawHair(Canvas canvas) {
        if (hairStyle.equalsIgnoreCase("bald")) return;

        fillPaint.setColor(Color.parseColor(hairColor));

        if (hairStyle.equalsIgnoreCase("short")) {
            canvas.drawArc(new RectF(22, 20, 78, 52), 180, 180, true, fillPaint);
            Path path = new Path();
            path.moveTo(23, 44); path.lineTo(26, 32); path.lineTo(38, 30); path.lineTo(44, 25);
            path.lineTo(50, 31); path.lineTo(56, 25); path.lineTo(62, 30); path.lineTo(74, 32);
            path.lineTo(77, 44); path.lineTo(74, 36); path.lineTo(66, 24); path.lineTo(50, 21);
            path.lineTo(34, 24); path.lineTo(26, 36); path.close();
            canvas.drawPath(path, fillPaint);
            canvas.drawPath(path, strokePaint);
        } else if (hairStyle.equalsIgnoreCase("medium")) {
            canvas.drawArc(new RectF(21, 19, 79, 53), 180, 180, true, fillPaint);
            RectF leftFlap = new RectF(20, 32, 27, 56);
            canvas.drawRoundRect(leftFlap, 4, 4, fillPaint);
            canvas.drawRoundRect(leftFlap, 4, 4, strokePaint);
            RectF rightFlap = new RectF(73, 32, 80, 56);
            canvas.drawRoundRect(rightFlap, 4, 4, fillPaint);
            canvas.drawRoundRect(rightFlap, 4, 4, strokePaint);
            Path path = new Path();
            path.moveTo(26, 38); path.lineTo(38, 28); path.lineTo(50, 31); path.lineTo(62, 28); path.lineTo(74, 38);
            canvas.drawPath(path, strokePaint);
        } else if (hairStyle.equalsIgnoreCase("long")) {
            canvas.drawArc(new RectF(21, 18, 79, 52), 180, 180, true, fillPaint);
            RectF leftLock = new RectF(18, 30, 26, 75);
            canvas.drawRoundRect(leftLock, 6, 6, fillPaint);
            canvas.drawRoundRect(leftLock, 6, 6, strokePaint);
            RectF rightLock = new RectF(74, 30, 82, 75);
            canvas.drawRoundRect(rightLock, 6, 6, fillPaint);
            canvas.drawRoundRect(rightLock, 6, 6, strokePaint);
            Path path = new Path();
            path.moveTo(25, 34); path.quadTo(38, 26, 50, 34); path.quadTo(62, 26, 75, 34);
            canvas.drawPath(path, strokePaint);
        }
    }
}
