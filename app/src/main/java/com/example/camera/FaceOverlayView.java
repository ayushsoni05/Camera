package com.example.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom overlay view that draws AR lens effects (dog ears, sunglasses, crown, mustache)
 * on top of detected face landmarks in real-time.
 */
public class FaceOverlayView extends View {

    private final Paint textPaint = new Paint();
    private final Paint circlePaint = new Paint();
    private final Paint stickerPaint = new Paint();

    private List<Face> faces = new ArrayList<>();
    private String activeLens = "None";
    private int previewWidth = 1;
    private int previewHeight = 1;
    private boolean isFrontFacing = true;

    public FaceOverlayView(Context context) {
        super(context);
        init();
    }

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(48f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

        circlePaint.setColor(Color.parseColor("#00F2FF"));
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(3f);
        circlePaint.setAntiAlias(true);

        stickerPaint.setAntiAlias(true);
        stickerPaint.setFilterBitmap(true);
    }

    public void setFaces(List<Face> faces, int imageWidth, int imageHeight, boolean frontFacing) {
        this.faces = faces;
        this.previewWidth = imageWidth;
        this.previewHeight = imageHeight;
        this.isFrontFacing = frontFacing;
        postInvalidate();
    }

    public void setActiveLens(String lens) {
        this.activeLens = lens;
        postInvalidate();
    }

    public void clearFaces() {
        this.faces = new ArrayList<>();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (faces == null || faces.isEmpty() || activeLens.equals("None")) return;

        float scaleX = (float) getWidth() / previewWidth;
        float scaleY = (float) getHeight() / previewHeight;

        for (Face face : faces) {
            float faceCenterX;
            float faceCenterY;

            FaceLandmark nose = face.getLandmark(FaceLandmark.NOSE_BASE);
            if (nose != null) {
                faceCenterX = translateX(nose.getPosition().x, scaleX);
                faceCenterY = nose.getPosition().y * scaleY;
            } else {
                float cx = face.getBoundingBox().centerX();
                float cy = face.getBoundingBox().centerY();
                faceCenterX = translateX(cx, scaleX);
                faceCenterY = cy * scaleY;
            }

            float faceWidth = face.getBoundingBox().width() * scaleX;
            float faceHeight = face.getBoundingBox().height() * scaleY;

            FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);
            FaceLandmark mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);
            FaceLandmark mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT);
            FaceLandmark mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT);

            switch (activeLens) {
                case "Dog":
                    drawDogLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight,
                            nose, mouthBottom, leftEye, rightEye, scaleX, scaleY);
                    break;
                case "Glasses":
                    drawGlassesLens(canvas, faceCenterX, faceCenterY, faceWidth,
                            leftEye, rightEye, scaleX, scaleY);
                    break;
                case "Crown":
                    drawCrownLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY, face);
                    break;
                case "Stache":
                    drawMustacheLens(canvas, faceCenterX, faceCenterY, faceWidth,
                            nose, mouthLeft, mouthRight, scaleX, scaleY);
                    break;
            }
        }
    }

    private float translateX(float x, float scaleX) {
        if (isFrontFacing) {
            return getWidth() - (x * scaleX);
        }
        return x * scaleX;
    }

    private void drawDogLens(Canvas canvas, float cx, float cy, float fw, float fh,
                             FaceLandmark nose, FaceLandmark mouth,
                             FaceLandmark leftEye, FaceLandmark rightEye,
                             float sx, float sy) {
        Paint earPaint = new Paint();
        earPaint.setAntiAlias(true);

        // Dog ears (triangles above eyes)
        float earSize = fw * 0.35f;
        float earTopY = cy - fh * 0.65f;

        // Left ear
        float leftX = cx - fw * 0.3f;
        earPaint.setColor(Color.parseColor("#8B4513"));
        android.graphics.Path leftEarPath = new android.graphics.Path();
        leftEarPath.moveTo(leftX - earSize * 0.4f, earTopY + earSize);
        leftEarPath.lineTo(leftX, earTopY - earSize * 0.3f);
        leftEarPath.lineTo(leftX + earSize * 0.4f, earTopY + earSize);
        leftEarPath.close();
        canvas.drawPath(leftEarPath, earPaint);

        // Right ear
        float rightX = cx + fw * 0.3f;
        android.graphics.Path rightEarPath = new android.graphics.Path();
        rightEarPath.moveTo(rightX - earSize * 0.4f, earTopY + earSize);
        rightEarPath.lineTo(rightX, earTopY - earSize * 0.3f);
        rightEarPath.lineTo(rightX + earSize * 0.4f, earTopY + earSize);
        rightEarPath.close();
        canvas.drawPath(rightEarPath, earPaint);

        // Inner ear (pink)
        earPaint.setColor(Color.parseColor("#FFB6C1"));
        float innerScale = 0.6f;
        android.graphics.Path leftInner = new android.graphics.Path();
        leftInner.moveTo(leftX - earSize * 0.4f * innerScale, earTopY + earSize * (1 - 0.1f));
        leftInner.lineTo(leftX, earTopY - earSize * 0.3f + earSize * 0.3f);
        leftInner.lineTo(leftX + earSize * 0.4f * innerScale, earTopY + earSize * (1 - 0.1f));
        leftInner.close();
        canvas.drawPath(leftInner, earPaint);

        android.graphics.Path rightInner = new android.graphics.Path();
        rightInner.moveTo(rightX - earSize * 0.4f * innerScale, earTopY + earSize * (1 - 0.1f));
        rightInner.lineTo(rightX, earTopY - earSize * 0.3f + earSize * 0.3f);
        rightInner.lineTo(rightX + earSize * 0.4f * innerScale, earTopY + earSize * (1 - 0.1f));
        rightInner.close();
        canvas.drawPath(rightInner, earPaint);

        // Dog nose (black circle at nose position)
        if (nose != null) {
            float noseX = translateX(nose.getPosition().x, sx);
            float noseY = nose.getPosition().y * sy;
            Paint nosePaint = new Paint();
            nosePaint.setColor(Color.BLACK);
            nosePaint.setAntiAlias(true);
            canvas.drawCircle(noseX, noseY, fw * 0.06f, nosePaint);
        }

        // Dog tongue (below mouth)
        if (mouth != null) {
            float mouthY = mouth.getPosition().y * sy;
            float mouthX = translateX(mouth.getPosition().x, sx);
            Paint tonguePaint = new Paint();
            tonguePaint.setColor(Color.parseColor("#FF6B6B"));
            tonguePaint.setAntiAlias(true);
            canvas.drawOval(mouthX - fw * 0.08f, mouthY,
                    mouthX + fw * 0.08f, mouthY + fh * 0.2f, tonguePaint);
        }
    }

    private void drawGlassesLens(Canvas canvas, float cx, float cy, float fw,
                                 FaceLandmark leftEye, FaceLandmark rightEye,
                                 float sx, float sy) {
        Paint glassPaint = new Paint();
        glassPaint.setAntiAlias(true);
        glassPaint.setStyle(Paint.Style.STROKE);
        glassPaint.setStrokeWidth(6f);
        glassPaint.setColor(Color.parseColor("#1A1A2E"));

        Paint lensPaint = new Paint();
        lensPaint.setAntiAlias(true);
        lensPaint.setColor(Color.parseColor("#4000F2FF"));

        float glassRadius = fw * 0.18f;

        if (leftEye != null && rightEye != null) {
            float lx = translateX(leftEye.getPosition().x, sx);
            float ly = leftEye.getPosition().y * sy;
            float rx = translateX(rightEye.getPosition().x, sx);
            float ry = rightEye.getPosition().y * sy;

            // Left lens
            canvas.drawCircle(lx, ly, glassRadius, lensPaint);
            canvas.drawCircle(lx, ly, glassRadius, glassPaint);

            // Right lens
            canvas.drawCircle(rx, ry, glassRadius, lensPaint);
            canvas.drawCircle(rx, ry, glassRadius, glassPaint);

            // Bridge
            canvas.drawLine(lx + glassRadius * 0.8f, ly,
                    rx - glassRadius * 0.8f, ry, glassPaint);

            // Temple arms
            glassPaint.setStrokeWidth(4f);
            canvas.drawLine(lx - glassRadius, ly, lx - glassRadius - fw * 0.15f, ly - 5, glassPaint);
            canvas.drawLine(rx + glassRadius, ry, rx + glassRadius + fw * 0.15f, ry - 5, glassPaint);
        } else {
            float eyeY = cy - fw * 0.08f;
            canvas.drawCircle(cx - fw * 0.2f, eyeY, glassRadius, lensPaint);
            canvas.drawCircle(cx - fw * 0.2f, eyeY, glassRadius, glassPaint);
            canvas.drawCircle(cx + fw * 0.2f, eyeY, glassRadius, lensPaint);
            canvas.drawCircle(cx + fw * 0.2f, eyeY, glassRadius, glassPaint);
            canvas.drawLine(cx - fw * 0.2f + glassRadius * 0.8f, eyeY,
                    cx + fw * 0.2f - glassRadius * 0.8f, eyeY, glassPaint);
        }
    }

    private void drawCrownLens(Canvas canvas, float cx, float cy, float fw, float fh,
                               float sx, float sy, Face face) {
        Paint crownPaint = new Paint();
        crownPaint.setAntiAlias(true);
        crownPaint.setColor(Color.parseColor("#FFD700"));

        float crownW = fw * 0.7f;
        float crownH = fw * 0.35f;
        float topY = cy - fh * 0.55f;

        android.graphics.Path crownPath = new android.graphics.Path();
        crownPath.moveTo(cx - crownW / 2, topY + crownH);
        crownPath.lineTo(cx - crownW / 2, topY + crownH * 0.3f);
        crownPath.lineTo(cx - crownW * 0.3f, topY + crownH * 0.6f);
        crownPath.lineTo(cx - crownW * 0.1f, topY);
        crownPath.lineTo(cx, topY + crownH * 0.45f);
        crownPath.lineTo(cx + crownW * 0.1f, topY);
        crownPath.lineTo(cx + crownW * 0.3f, topY + crownH * 0.6f);
        crownPath.lineTo(cx + crownW / 2, topY + crownH * 0.3f);
        crownPath.lineTo(cx + crownW / 2, topY + crownH);
        crownPath.close();
        canvas.drawPath(crownPath, crownPaint);

        // Jewels
        Paint jewelPaint = new Paint();
        jewelPaint.setAntiAlias(true);
        float jewelR = fw * 0.025f;

        jewelPaint.setColor(Color.parseColor("#FF2D55"));
        canvas.drawCircle(cx, topY + crownH * 0.55f, jewelR, jewelPaint);

        jewelPaint.setColor(Color.parseColor("#00F2FF"));
        canvas.drawCircle(cx - crownW * 0.2f, topY + crownH * 0.65f, jewelR * 0.8f, jewelPaint);
        canvas.drawCircle(cx + crownW * 0.2f, topY + crownH * 0.65f, jewelR * 0.8f, jewelPaint);
    }

    private void drawMustacheLens(Canvas canvas, float cx, float cy, float fw,
                                  FaceLandmark nose, FaceLandmark mouthL, FaceLandmark mouthR,
                                  float sx, float sy) {
        Paint stachePaint = new Paint();
        stachePaint.setAntiAlias(true);
        stachePaint.setColor(Color.parseColor("#3E2723"));
        stachePaint.setStyle(Paint.Style.FILL);

        float stacheY;
        float stacheCx;

        if (nose != null && mouthL != null) {
            float noseY = nose.getPosition().y * sy;
            float mouthLY = mouthL.getPosition().y * sy;
            stacheY = noseY + (mouthLY - noseY) * 0.4f;
            stacheCx = translateX(nose.getPosition().x, sx);
        } else {
            stacheY = cy + fw * 0.1f;
            stacheCx = cx;
        }

        float halfW = fw * 0.3f;
        float height = fw * 0.08f;

        android.graphics.Path path = new android.graphics.Path();
        // Left side
        path.moveTo(stacheCx, stacheY);
        path.quadTo(stacheCx - halfW * 0.5f, stacheY - height * 1.5f,
                stacheCx - halfW, stacheY - height * 0.3f);
        path.quadTo(stacheCx - halfW * 0.5f, stacheY + height,
                stacheCx, stacheY + height * 0.3f);
        // Right side
        path.moveTo(stacheCx, stacheY);
        path.quadTo(stacheCx + halfW * 0.5f, stacheY - height * 1.5f,
                stacheCx + halfW, stacheY - height * 0.3f);
        path.quadTo(stacheCx + halfW * 0.5f, stacheY + height,
                stacheCx, stacheY + height * 0.3f);
        path.close();

        canvas.drawPath(path, stachePaint);
    }
}
