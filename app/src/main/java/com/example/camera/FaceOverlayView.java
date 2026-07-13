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
                case "Neon Devil":
                    drawNeonDevilLens(canvas, faceCenterX, faceCenterY, faceWidth,
                            leftEye, rightEye, scaleX, scaleY);
                    break;
                case "Angel Halo":
                    drawAngelHaloLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Cyberpunk HUD":
                    drawCyberpunkHUDLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY, face);
                    break;
                case "Bunny":
                    drawBunnyLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Cat":
                    drawCatLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Flower Crown":
                    drawFlowerCrownLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Beard":
                    drawBeardLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Ghost":
                    drawGhostLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Star Eyes":
                    drawStarEyesLens(canvas, faceCenterX, faceCenterY, faceWidth, leftEye, rightEye, scaleX, scaleY);
                    break;
                case "Heart Eyes":
                    drawHeartEyesLens(canvas, faceCenterX, faceCenterY, faceWidth, leftEye, rightEye, scaleX, scaleY);
                    break;
                case "Fire Head":
                    drawFireHeadLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Rainbow Mouth":
                    drawRainbowMouthLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Alien":
                    drawAlienLens(canvas, faceCenterX, faceCenterY, faceWidth, leftEye, rightEye, scaleX, scaleY);
                    break;
                case "Pirate":
                    drawPirateLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, leftEye, rightEye, scaleX, scaleY);
                    break;
                case "Clown":
                    drawClownLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, nose, scaleX, scaleY);
                    break;
                case "Superhero":
                    drawSuperheroLens(canvas, faceCenterX, faceCenterY, faceWidth, leftEye, rightEye, scaleX, scaleY);
                    break;
                case "Vampire":
                    drawVampireLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Wizard":
                    drawWizardLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Space Helmet":
                    drawSpaceHelmetLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
                    break;
                case "Butterfly":
                    drawButterflyLens(canvas, faceCenterX, faceCenterY, faceWidth, faceHeight, scaleX, scaleY);
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
        // 1. Furry gradient ears
        Paint earPaint = new Paint();
        earPaint.setAntiAlias(true);
        earPaint.setStyle(Paint.Style.FILL);
        earPaint.setShadowLayer(10f, 4f, 4f, 0x4D000000); // 3D drop shadow

        float earSize = fw * 0.35f;
        float earTopY = cy - fh * 0.65f;

        // Left Ear (Brown base)
        float leftX = cx - fw * 0.35f;
        android.graphics.LinearGradient leftBaseGrad = new android.graphics.LinearGradient(
                leftX - earSize, earTopY, leftX + earSize, earTopY + earSize,
                Color.parseColor("#5C3A21"), Color.parseColor("#3D271D"), android.graphics.Shader.TileMode.CLAMP);
        earPaint.setShader(leftBaseGrad);

        android.graphics.Path leftEarPath = new android.graphics.Path();
        leftEarPath.moveTo(leftX - earSize * 0.5f, earTopY + earSize * 0.3f);
        leftEarPath.quadTo(leftX - earSize * 0.7f, earTopY - earSize * 0.2f, leftX, earTopY - earSize * 0.5f);
        leftEarPath.quadTo(leftX + earSize * 0.5f, earTopY + earSize * 0.2f, leftX + earSize * 0.4f, earTopY + earSize * 0.8f);
        leftEarPath.quadTo(leftX, earTopY + earSize * 1.1f, leftX - earSize * 0.5f, earTopY + earSize * 0.3f);
        leftEarPath.close();
        canvas.drawPath(leftEarPath, earPaint);

        // Left Inner Ear (Pink gradient)
        Paint innerPaint = new Paint();
        innerPaint.setAntiAlias(true);
        innerPaint.setStyle(Paint.Style.FILL);
        android.graphics.LinearGradient leftInnerGrad = new android.graphics.LinearGradient(
                leftX, earTopY, leftX, earTopY + earSize * 0.8f,
                Color.parseColor("#FFD1DC"), Color.parseColor("#FFA6C9"), android.graphics.Shader.TileMode.CLAMP);
        innerPaint.setShader(leftInnerGrad);

        android.graphics.Path leftInnerPath = new android.graphics.Path();
        leftInnerPath.moveTo(leftX - earSize * 0.25f, earTopY + earSize * 0.35f);
        leftInnerPath.quadTo(leftX - earSize * 0.35f, earTopY, leftX, earTopY - earSize * 0.2f);
        leftInnerPath.quadTo(leftX + earSize * 0.25f, earTopY + earSize * 0.2f, leftX + earSize * 0.2f, earTopY + earSize * 0.7f);
        leftInnerPath.quadTo(leftX, earTopY + earSize * 0.9f, leftX - earSize * 0.25f, earTopY + earSize * 0.35f);
        leftInnerPath.close();
        canvas.drawPath(leftInnerPath, innerPaint);

        // Right Ear (Brown base)
        float rightX = cx + fw * 0.35f;
        android.graphics.LinearGradient rightBaseGrad = new android.graphics.LinearGradient(
                rightX - earSize, earTopY, rightX + earSize, earTopY + earSize,
                Color.parseColor("#5C3A21"), Color.parseColor("#3D271D"), android.graphics.Shader.TileMode.CLAMP);
        earPaint.setShader(rightBaseGrad);

        android.graphics.Path rightEarPath = new android.graphics.Path();
        rightEarPath.moveTo(rightX + earSize * 0.5f, earTopY + earSize * 0.3f);
        rightEarPath.quadTo(rightX + earSize * 0.7f, earTopY - earSize * 0.2f, rightX, earTopY - earSize * 0.5f);
        rightEarPath.quadTo(rightX - earSize * 0.5f, earTopY + earSize * 0.2f, rightX - earSize * 0.4f, earTopY + earSize * 0.8f);
        rightEarPath.quadTo(rightX, earTopY + earSize * 1.1f, rightX + earSize * 0.5f, earTopY + earSize * 0.3f);
        rightEarPath.close();
        canvas.drawPath(rightEarPath, earPaint);

        // Right Inner Ear (Pink gradient)
        android.graphics.LinearGradient rightInnerGrad = new android.graphics.LinearGradient(
                rightX, earTopY, rightX, earTopY + earSize * 0.8f,
                Color.parseColor("#FFD1DC"), Color.parseColor("#FFA6C9"), android.graphics.Shader.TileMode.CLAMP);
        innerPaint.setShader(rightInnerGrad);

        android.graphics.Path rightInnerPath = new android.graphics.Path();
        rightInnerPath.moveTo(rightX + earSize * 0.25f, earTopY + earSize * 0.35f);
        rightInnerPath.quadTo(rightX + earSize * 0.35f, earTopY, rightX, earTopY - earSize * 0.2f);
        rightInnerPath.quadTo(rightX - earSize * 0.25f, earTopY + earSize * 0.2f, rightX - earSize * 0.2f, earTopY + earSize * 0.7f);
        rightInnerPath.quadTo(rightX, earTopY + earSize * 0.9f, rightX + earSize * 0.25f, earTopY + earSize * 0.35f);
        rightInnerPath.close();
        canvas.drawPath(rightInnerPath, innerPaint);

        // 2. Glossy 3D Nose button
        if (nose != null) {
            float noseX = translateX(nose.getPosition().x, sx);
            float noseY = nose.getPosition().y * sy;
            float noseR = fw * 0.08f;

            Paint nosePaint = new Paint();
            nosePaint.setAntiAlias(true);
            android.graphics.RadialGradient noseGrad = new android.graphics.RadialGradient(
                    noseX - noseR * 0.2f, noseY - noseR * 0.2f, noseR * 1.2f,
                    Color.parseColor("#424242"), Color.parseColor("#121212"), android.graphics.Shader.TileMode.CLAMP);
            nosePaint.setShader(noseGrad);
            canvas.drawCircle(noseX, noseY, noseR, nosePaint);

            // Specular reflection dot
            Paint reflPaint = new Paint();
            reflPaint.setAntiAlias(true);
            reflPaint.setColor(Color.WHITE);
            reflPaint.setAlpha(200);
            canvas.drawCircle(noseX - noseR * 0.3f, noseY - noseR * 0.3f, noseR * 0.25f, reflPaint);
        }

        // 3. 3D tongue (fades down)
        if (mouth != null) {
            float mouthY = mouth.getPosition().y * sy;
            float mouthX = translateX(mouth.getPosition().x, sx);
            float tongueW = fw * 0.16f;
            float tongueH = fh * 0.18f;

            Paint tonguePaint = new Paint();
            tonguePaint.setAntiAlias(true);
            android.graphics.LinearGradient tongueGrad = new android.graphics.LinearGradient(
                    mouthX, mouthY, mouthX, mouthY + tongueH,
                    Color.parseColor("#FF8B8B"), Color.parseColor("#FF3B30"), android.graphics.Shader.TileMode.CLAMP);
            tonguePaint.setShader(tongueGrad);
            canvas.drawOval(mouthX - tongueW / 2, mouthY, mouthX + tongueW / 2, mouthY + tongueH, tonguePaint);

            // Center tongue crease line
            Paint linePaint = new Paint();
            linePaint.setAntiAlias(true);
            linePaint.setColor(Color.parseColor("#CC2A2A"));
            linePaint.setStrokeWidth(3f);
            linePaint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(mouthX, mouthY + 2, mouthX, mouthY + tongueH - 6, linePaint);
        }
    }

    private void drawGlassesLens(Canvas canvas, float cx, float cy, float fw,
                                 FaceLandmark leftEye, FaceLandmark rightEye,
                                 float sx, float sy) {
        if (leftEye == null || rightEye == null) return;

        float lx = translateX(leftEye.getPosition().x, sx);
        float ly = leftEye.getPosition().y * sy;
        float rx = translateX(rightEye.getPosition().x, sx);
        float ry = rightEye.getPosition().y * sy;

        float glassRadius = fw * 0.22f;

        // 1. Gold Frame Paint
        Paint framePaint = new Paint();
        framePaint.setAntiAlias(true);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(7f);
        android.graphics.LinearGradient frameGrad = new android.graphics.LinearGradient(
                lx - glassRadius, ly, rx + glassRadius, ry,
                new int[]{Color.parseColor("#FFE5A3"), Color.parseColor("#D4AF37"), Color.parseColor("#AA7C11")},
                null, android.graphics.Shader.TileMode.CLAMP);
        framePaint.setShader(frameGrad);
        framePaint.setShadowLayer(8f, 2f, 4f, 0x66000000); // 3D frame shadow

        // 2. Sunset Lens Paint
        Paint lensPaint = new Paint();
        lensPaint.setAntiAlias(true);
        lensPaint.setStyle(Paint.Style.FILL);
        android.graphics.LinearGradient lensGrad = new android.graphics.LinearGradient(
                lx, ly - glassRadius, lx, ly + glassRadius,
                new int[]{Color.parseColor("#E6FF0055"), Color.parseColor("#E6FFFC00")}, // Pink to Yellow sunset gradient
                null, android.graphics.Shader.TileMode.CLAMP);
        lensPaint.setShader(lensGrad);

        // Draw left teardrop lens
        android.graphics.Path leftLens = new android.graphics.Path();
        leftLens.addCircle(lx, ly, glassRadius, android.graphics.Path.Direction.CW);
        canvas.drawPath(leftLens, lensPaint);
        canvas.drawPath(leftLens, framePaint);

        // Draw right teardrop lens
        lensPaint.setShader(new android.graphics.LinearGradient(
                rx, ry - glassRadius, rx, ry + glassRadius,
                new int[]{Color.parseColor("#E6FF0055"), Color.parseColor("#E6FFFC00")},
                null, android.graphics.Shader.TileMode.CLAMP));
        android.graphics.Path rightLens = new android.graphics.Path();
        rightLens.addCircle(rx, ry, glassRadius, android.graphics.Path.Direction.CW);
        canvas.drawPath(rightLens, lensPaint);
        canvas.drawPath(rightLens, framePaint);

        // 3. Double bridge connector (gold bars)
        Paint bridgePaint = new Paint();
        bridgePaint.setAntiAlias(true);
        bridgePaint.setStyle(Paint.Style.STROKE);
        bridgePaint.setStrokeWidth(5f);
        bridgePaint.setShader(frameGrad);

        canvas.drawLine(lx + glassRadius * 0.7f, ly - glassRadius * 0.1f, rx - glassRadius * 0.7f, ry - glassRadius * 0.1f, bridgePaint);
        canvas.drawLine(lx + glassRadius * 0.5f, ly - glassRadius * 0.4f, rx - glassRadius * 0.5f, ry - glassRadius * 0.4f, bridgePaint);

        // 4. White Diagonal Glare reflection lines
        Paint glarePaint = new Paint();
        glarePaint.setAntiAlias(true);
        glarePaint.setColor(Color.WHITE);
        glarePaint.setAlpha(120); // 45% opacity
        glarePaint.setStrokeWidth(8f);
        glarePaint.setStrokeCap(Paint.Cap.ROUND);

        canvas.drawLine(lx - glassRadius * 0.4f, ly - glassRadius * 0.4f, lx + glassRadius * 0.2f, ly + glassRadius * 0.6f, glarePaint);
        canvas.drawLine(rx - glassRadius * 0.4f, ry - glassRadius * 0.4f, rx + glassRadius * 0.2f, ry + glassRadius * 0.6f, glarePaint);
    }

    private void drawCrownLens(Canvas canvas, float cx, float cy, float fw, float fh,
                               float sx, float sy, Face face) {
        Paint crownPaint = new Paint();
        crownPaint.setAntiAlias(true);
        crownPaint.setStyle(Paint.Style.FILL);

        float crownW = fw * 0.75f;
        float crownH = fw * 0.40f;
        float topY = cy - fh * 0.60f;

        // 1. Volumetric gold gradient shading
        android.graphics.LinearGradient goldGrad = new android.graphics.LinearGradient(
                cx - crownW / 2, topY, cx + crownW / 2, topY + crownH,
                new int[]{Color.parseColor("#FFF3D3"), Color.parseColor("#E6C25E"), Color.parseColor("#D4AF37"), Color.parseColor("#967417")},
                null, android.graphics.Shader.TileMode.CLAMP);
        crownPaint.setShader(goldGrad);
        crownPaint.setShadowLayer(15f, 4f, 6f, 0x66000000); // Premium shadow

        // 2. medieval crown vector path
        android.graphics.Path crownPath = new android.graphics.Path();
        crownPath.moveTo(cx - crownW / 2, topY + crownH);
        crownPath.lineTo(cx - crownW / 2, topY + crownH * 0.25f);
        crownPath.lineTo(cx - crownW * 0.3f, topY + crownH * 0.55f);
        crownPath.lineTo(cx - crownW * 0.15f, topY + crownH * 0.1f);
        crownPath.lineTo(cx, topY + crownH * 0.45f);
        crownPath.lineTo(cx + crownW * 0.15f, topY + crownH * 0.1f);
        crownPath.lineTo(cx + crownW * 0.3f, topY + crownH * 0.55f);
        crownPath.lineTo(cx + crownW / 2, topY + crownH * 0.25f);
        crownPath.lineTo(cx + crownW / 2, topY + crownH);
        crownPath.close();
        canvas.drawPath(crownPath, crownPaint);

        // 3. Thick base band
        Paint bandPaint = new Paint();
        bandPaint.setAntiAlias(true);
        bandPaint.setShader(new android.graphics.LinearGradient(
                cx - crownW/2, topY + crownH - 12, cx + crownW/2, topY + crownH,
                new int[]{Color.parseColor("#D4AF37"), Color.parseColor("#FFF3D3"), Color.parseColor("#967417")},
                null, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(cx - crownW / 2, topY + crownH - 8, cx + crownW / 2, topY + crownH, bandPaint);

        // 4. Shiny Ruby & Sapphire Jewels (Radial Gradients)
        Paint jewelPaint = new Paint();
        jewelPaint.setAntiAlias(true);
        float jewelR = fw * 0.035f;

        // Ruby Center (Red radial)
        android.graphics.RadialGradient rubyGrad = new android.graphics.RadialGradient(
                cx, topY + crownH * 0.55f, jewelR,
                Color.parseColor("#FF5C75"), Color.parseColor("#C60021"), android.graphics.Shader.TileMode.CLAMP);
        jewelPaint.setShader(rubyGrad);
        canvas.drawCircle(cx, topY + crownH * 0.55f, jewelR, jewelPaint);

        // Sapphire Left (Blue radial)
        android.graphics.RadialGradient sapphireGrad = new android.graphics.RadialGradient(
                cx - crownW * 0.25f, topY + crownH * 0.65f, jewelR * 0.8f,
                Color.parseColor("#5CE1FF"), Color.parseColor("#005FC6"), android.graphics.Shader.TileMode.CLAMP);
        jewelPaint.setShader(sapphireGrad);
        canvas.drawCircle(cx - crownW * 0.25f, topY + crownH * 0.65f, jewelR * 0.8f, jewelPaint);

        // Sapphire Right
        jewelPaint.setShader(sapphireGrad);
        canvas.drawCircle(cx + crownW * 0.25f, topY + crownH * 0.65f, jewelR * 0.8f, jewelPaint);
    }

    private void drawMustacheLens(Canvas canvas, float cx, float cy, float fw,
                                  FaceLandmark nose, FaceLandmark mouthL, FaceLandmark mouthR,
                                  float sx, float sy) {
        Paint stachePaint = new Paint();
        stachePaint.setAntiAlias(true);
        stachePaint.setStyle(Paint.Style.FILL);
        stachePaint.setShadowLayer(5f, 0f, 2f, 0x80000000); // 3D offset shadow

        float stacheY;
        float stacheCx;

        if (nose != null && mouthL != null) {
            float noseY = nose.getPosition().y * sy;
            float mouthLY = mouthL.getPosition().y * sy;
            stacheY = noseY + (mouthLY - noseY) * 0.45f;
            stacheCx = translateX(nose.getPosition().x, sx);
        } else {
            stacheY = cy + fw * 0.12f;
            stacheCx = cx;
        }

        float halfW = fw * 0.32f;
        float height = fw * 0.1f;

        // Textured charcoal linear gradient
        android.graphics.LinearGradient stacheGrad = new android.graphics.LinearGradient(
                stacheCx, stacheY - height, stacheCx, stacheY + height,
                Color.parseColor("#2C1D18"), Color.parseColor("#120A07"), android.graphics.Shader.TileMode.CLAMP);
        stachePaint.setShader(stacheGrad);

        // Curly handlebar mustache path
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(stacheCx, stacheY);
        path.quadTo(stacheCx - halfW * 0.4f, stacheY - height * 0.8f, stacheCx - halfW * 0.8f, stacheY - height * 0.2f);
        path.quadTo(stacheCx - halfW * 1.1f, stacheY + height * 0.5f, stacheCx - halfW * 0.8f, stacheY + height * 0.6f);
        path.quadTo(stacheCx - halfW * 0.5f, stacheY + height * 0.4f, stacheCx, stacheY + height * 0.1f);

        path.moveTo(stacheCx, stacheY);
        path.quadTo(stacheCx + halfW * 0.4f, stacheY - height * 0.8f, stacheCx + halfW * 0.8f, stacheY - height * 0.2f);
        path.quadTo(stacheCx + halfW * 1.1f, stacheY + height * 0.5f, stacheCx + halfW * 0.8f, stacheY + height * 0.6f);
        path.quadTo(stacheCx + halfW * 0.5f, stacheY + height * 0.4f, stacheCx, stacheY + height * 0.1f);
        path.close();
        canvas.drawPath(path, stachePaint);
    }

    private void drawNeonDevilLens(Canvas canvas, float cx, float cy, float fw,
                                   FaceLandmark leftEye, FaceLandmark rightEye,
                                   float sx, float sy) {
        float leftHornX = cx - fw * 0.25f;
        float rightHornX = cx + fw * 0.25f;
        float hornBaseY = cy - fw * 0.45f;
        float hornTipY = cy - fw * 0.78f;

        // 1. Draw glowing neon wings (Dual-stroke white core + red glow)
        Paint glowPaint = new Paint();
        glowPaint.setAntiAlias(true);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(16f);
        glowPaint.setColor(Color.parseColor("#FF003B"));
        glowPaint.setShadowLayer(25f, 0f, 0f, Color.parseColor("#FFFF2D55")); // Neon red glow

        android.graphics.Path leftHorn = new android.graphics.Path();
        leftHorn.moveTo(leftHornX - fw * 0.06f, hornBaseY);
        leftHorn.quadTo(leftHornX - fw * 0.05f, hornBaseY - fw * 0.15f, leftHornX - fw * 0.18f, hornTipY);
        leftHorn.quadTo(leftHornX + fw * 0.02f, hornBaseY - fw * 0.15f, leftHornX + fw * 0.06f, hornBaseY);

        android.graphics.Path rightHorn = new android.graphics.Path();
        rightHorn.moveTo(rightHornX - fw * 0.06f, hornBaseY);
        rightHorn.quadTo(rightHornX - fw * 0.02f, hornBaseY - fw * 0.15f, rightHornX + fw * 0.18f, hornTipY);
        rightHorn.quadTo(rightHornX + fw * 0.05f, hornBaseY - fw * 0.15f, rightHornX + fw * 0.06f, hornBaseY);

        // Draw outer thick red glow path
        canvas.drawPath(leftHorn, glowPaint);
        canvas.drawPath(rightHorn, glowPaint);

        // Draw inner hot white neon core path
        Paint corePaint = new Paint();
        corePaint.setAntiAlias(true);
        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeWidth(6f);
        corePaint.setColor(Color.WHITE);
        canvas.drawPath(leftHorn, corePaint);
        canvas.drawPath(rightHorn, corePaint);

        // 2. Glowing Neon Eyes
        if (leftEye != null && rightEye != null) {
            float lx = translateX(leftEye.getPosition().x, sx);
            float ly = leftEye.getPosition().y * sy;
            float rx = translateX(rightEye.getPosition().x, sx);
            float ry = rightEye.getPosition().y * sy;

            Paint eyeGlow = new Paint();
            eyeGlow.setAntiAlias(true);
            eyeGlow.setStyle(Paint.Style.FILL);
            eyeGlow.setColor(Color.parseColor("#FF2D55"));
            eyeGlow.setShadowLayer(16f, 0f, 0f, Color.parseColor("#FFFF2D55"));

            canvas.drawCircle(lx, ly, fw * 0.03f, eyeGlow);
            canvas.drawCircle(rx, ry, fw * 0.03f, eyeGlow);

            // White center iris
            Paint eyeCore = new Paint();
            eyeCore.setAntiAlias(true);
            eyeCore.setColor(Color.WHITE);
            canvas.drawCircle(lx, ly, fw * 0.012f, eyeCore);
            canvas.drawCircle(rx, ry, fw * 0.012f, eyeCore);
        }
    }

    private void drawAngelHaloLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        float haloX = cx;
        float haloY = cy - fh * 0.65f;
        float rx = fw * 0.38f;
        float ry = fw * 0.10f;

        // 1. Dual-stroke Glowing Angel Halo
        Paint glowPaint = new Paint();
        glowPaint.setAntiAlias(true);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(14f);
        glowPaint.setColor(Color.parseColor("#FFFC00"));
        glowPaint.setShadowLayer(25f, 0f, 0f, Color.parseColor("#FFFFFC00")); // Neon yellow glow

        canvas.drawOval(haloX - rx, haloY - ry, haloX + rx, haloY + ry, glowPaint);

        Paint corePaint = new Paint();
        corePaint.setAntiAlias(true);
        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeWidth(5f);
        corePaint.setColor(Color.WHITE);
        canvas.drawOval(haloX - rx, haloY - ry, haloX + rx, haloY + ry, corePaint);

        // 2. Natural soft radial-gradient cheek blush (no solid circles)
        Paint blushPaint = new Paint();
        blushPaint.setAntiAlias(true);
        blushPaint.setStyle(Paint.Style.FILL);

        float blushR = fw * 0.15f;
        float blushY = cy + fh * 0.15f;

        // Left Cheek Gradient
        float leftCx = cx - fw * 0.28f;
        android.graphics.RadialGradient leftBlushGrad = new android.graphics.RadialGradient(
                leftCx, blushY, blushR,
                new int[]{Color.parseColor("#73FF6B6B"), Color.parseColor("#00FF6B6B")}, // 45% pink to 0% transparent
                null, android.graphics.Shader.TileMode.CLAMP);
        blushPaint.setShader(leftBlushGrad);
        canvas.drawCircle(leftCx, blushY, blushR, blushPaint);

        // Right Cheek Gradient
        float rightCx = cx + fw * 0.28f;
        android.graphics.RadialGradient rightBlushGrad = new android.graphics.RadialGradient(
                rightCx, blushY, blushR,
                new int[]{Color.parseColor("#73FF6B6B"), Color.parseColor("#00FF6B6B")},
                null, android.graphics.Shader.TileMode.CLAMP);
        blushPaint.setShader(rightBlushGrad);
        canvas.drawCircle(rightCx, blushY, blushR, blushPaint);
    }

    private void drawCyberpunkHUDLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy, Face face) {
        Paint hudPaint = new Paint();
        hudPaint.setAntiAlias(true);
        hudPaint.setStyle(Paint.Style.STROKE);
        hudPaint.setStrokeWidth(3f);
        hudPaint.setColor(Color.parseColor("#39FF14")); 
        
        float r = fw * 0.45f;
        canvas.drawCircle(cx, cy, r, hudPaint);
        
        canvas.drawLine(cx - r * 1.1f, cy, cx - r * 0.9f, cy, hudPaint);
        canvas.drawLine(cx + r * 0.9f, cy, cx + r * 1.1f, cy, hudPaint);
        canvas.drawLine(cx, cy - r * 1.1f, cx, cy - r * 0.9f, hudPaint);
        canvas.drawLine(cx, cy + r * 0.9f, cx, cy + r * 1.1f, hudPaint);
        
        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.parseColor("#39FF14"));
        textPaint.setTextSize(fw * 0.06f);
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        
        float textY = cy - fh * 0.4f;
        canvas.drawText("SYS_OK", cx - fw * 0.18f, textY, textPaint);
        canvas.drawText("LOCK_99.4%", cx - fw * 0.18f, textY + fw * 0.08f, textPaint);
    }

    private void drawBunnyLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);

        // Bunny Ears
        float earW = fw * 0.2f;
        float earH = fh * 0.6f;
        float earY = cy - fh * 0.8f;

        // Left ear
        p.setColor(Color.WHITE);
        canvas.drawOval(cx - fw * 0.25f - earW/2, earY, cx - fw * 0.25f + earW/2, earY + earH, p);
        p.setColor(Color.parseColor("#FFFFB6C1"));
        canvas.drawOval(cx - fw * 0.25f - earW*0.3f, earY + earH*0.1f, cx - fw * 0.25f + earW*0.3f, earY + earH*0.9f, p);

        // Right ear
        p.setColor(Color.WHITE);
        canvas.drawOval(cx + fw * 0.25f - earW/2, earY, cx + fw * 0.25f + earW/2, earY + earH, p);
        p.setColor(Color.parseColor("#FFFFB6C1"));
        canvas.drawOval(cx + fw * 0.25f - earW*0.3f, earY + earH*0.1f, cx + fw * 0.25f + earW*0.3f, earY + earH*0.9f, p);

        // Whiskers & nose
        p.setColor(Color.parseColor("#FFFFB6C1"));
        canvas.drawCircle(cx, cy, fw * 0.06f, p); // nose
        p.setColor(Color.WHITE);
        p.setStrokeWidth(3f);
        canvas.drawLine(cx - fw*0.08f, cy, cx - fw*0.35f, cy - 10, p);
        canvas.drawLine(cx - fw*0.08f, cy + 5, cx - fw*0.35f, cy + 10, p);
        canvas.drawLine(cx + fw*0.08f, cy, cx + fw*0.35f, cy - 10, p);
        canvas.drawLine(cx + fw*0.08f, cy + 5, cx + fw*0.35f, cy + 10, p);
    }

    private void drawCatLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);

        // Ears (Triangles)
        float earTopY = cy - fh * 0.65f;
        float earSize = fw * 0.3f;

        // Left Ear
        p.setColor(Color.parseColor("#FF424242")); // Dark gray
        android.graphics.Path leftPath = new android.graphics.Path();
        leftPath.moveTo(cx - fw * 0.4f, cy - fh * 0.4f);
        leftPath.lineTo(cx - fw * 0.25f, earTopY);
        leftPath.lineTo(cx - fw * 0.1f, cy - fh * 0.4f);
        leftPath.close();
        canvas.drawPath(leftPath, p);

        p.setColor(Color.parseColor("#FFFFC0CB")); // Pink inner
        android.graphics.Path leftInner = new android.graphics.Path();
        leftInner.moveTo(cx - fw * 0.35f, cy - fh * 0.41f);
        leftInner.lineTo(cx - fw * 0.25f, earTopY + earSize * 0.2f);
        leftInner.lineTo(cx - fw * 0.15f, cy - fh * 0.41f);
        leftInner.close();
        canvas.drawPath(leftInner, p);

        // Right Ear
        p.setColor(Color.parseColor("#FF424242"));
        android.graphics.Path rightPath = new android.graphics.Path();
        rightPath.moveTo(cx + fw * 0.1f, cy - fh * 0.4f);
        rightPath.lineTo(cx + fw * 0.25f, earTopY);
        rightPath.lineTo(cx + fw * 0.4f, cy - fh * 0.4f);
        rightPath.close();
        canvas.drawPath(rightPath, p);

        p.setColor(Color.parseColor("#FFFFC0CB"));
        android.graphics.Path rightInner = new android.graphics.Path();
        rightInner.moveTo(cx + fw * 0.15f, cy - fh * 0.41f);
        rightInner.lineTo(cx + fw * 0.25f, earTopY + earSize * 0.2f);
        rightInner.lineTo(cx + fw * 0.35f, cy - fh * 0.41f);
        rightInner.close();
        canvas.drawPath(rightInner, p);

        // Cat nose & whiskers
        p.setColor(Color.parseColor("#FFFFC0CB"));
        canvas.drawCircle(cx, cy, fw * 0.05f, p);
        p.setColor(Color.BLACK);
        p.setStrokeWidth(3f);
        canvas.drawLine(cx - fw*0.06f, cy, cx - fw*0.32f, cy - 5, p);
        canvas.drawLine(cx - fw*0.06f, cy + 5, cx - fw*0.32f, cy + 15, p);
        canvas.drawLine(cx + fw*0.06f, cy, cx + fw*0.32f, cy - 5, p);
        canvas.drawLine(cx + fw*0.06f, cy + 5, cx + fw*0.32f, cy + 15, p);
    }

    private void drawFlowerCrownLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);

        float crownY = cy - fh * 0.48f;
        int[] colors = {Color.RED, Color.parseColor("#FFFF69B4"), Color.YELLOW, Color.parseColor("#FF00FFFF"), Color.parseColor("#FFFF00FF")};
        
        // Draw leaves behind
        p.setColor(Color.GREEN);
        for (int i = -3; i <= 3; i++) {
            float fx = cx + (i * fw * 0.15f);
            canvas.drawRect(fx - 15, crownY - 5, fx + 15, crownY + 5, p);
        }

        // Draw flower blossoms
        for (int i = -3; i <= 3; i++) {
            float fx = cx + (i * fw * 0.15f);
            p.setColor(colors[Math.abs(i) % colors.length]);
            canvas.drawCircle(fx, crownY, fw * 0.06f, p);
            p.setColor(Color.WHITE);
            canvas.drawCircle(fx, crownY, fw * 0.02f, p); // Center
        }
    }

    private void drawBeardLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.parseColor("#FF5C4033")); // Dark brown
        p.setStyle(Paint.Style.FILL);

        // Mustache + chin beard
        float beardTopY = cy + fh * 0.25f;
        android.graphics.Path beard = new android.graphics.Path();
        beard.moveTo(cx - fw * 0.4f, cy + fh * 0.05f); // Cheeks left
        beard.quadTo(cx - fw * 0.35f, beardTopY, cx - fw * 0.2f, beardTopY + fh * 0.25f);
        beard.lineTo(cx + fw * 0.2f, beardTopY + fh * 0.25f);
        beard.quadTo(cx + fw * 0.35f, beardTopY, cx + fw * 0.4f, cy + fh * 0.05f);
        beard.quadTo(cx, beardTopY + fh * 0.12f, cx - fw * 0.4f, cy + fh * 0.05f);
        beard.close();
        canvas.drawPath(beard, p);
    }

    private void drawGhostLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);

        // Snapchat ghost floating above head
        float gx = cx;
        float gy = cy - fh * 0.75f;
        float gw = fw * 0.3f;
        float gh = fh * 0.25f;

        // Ghost body
        p.setColor(Color.YELLOW); // Snapchat background color
        canvas.drawRoundRect(gx - gw * 1.2f, gy - gh * 1.2f, gx + gw * 1.2f, gy + gh * 1.2f, 30f, 30f, p);

        p.setColor(Color.WHITE);
        android.graphics.Path body = new android.graphics.Path();
        body.moveTo(gx - gw*0.6f, gy + gh*0.5f);
        body.cubicTo(gx - gw*0.6f, gy - gh, gx + gw*0.6f, gy - gh, gx + gw*0.6f, gy + gh*0.5f);
        body.quadTo(gx + gw*0.8f, gy + gh*0.7f, gx + gw*0.9f, gy + gh*0.8f);
        body.lineTo(gx - gw*0.9f, gy + gh*0.8f);
        body.quadTo(gx - gw*0.8f, gy + gh*0.7f, gx - gw*0.6f, gy + gh*0.5f);
        body.close();
        canvas.drawPath(body, p);

        // Eyes & smile
        p.setColor(Color.BLACK);
        canvas.drawCircle(gx - gw*0.2f, gy - gh*0.1f, 5, p);
        canvas.drawCircle(gx + gw*0.2f, gy - gh*0.1f, 5, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        canvas.drawArc(gx - gw*0.15f, gy + gh*0.1f, gx + gw*0.15f, gy + gh*0.3f, 0, 180, false, p);
    }

    private void drawStarEyesLens(Canvas canvas, float cx, float cy, float fw, FaceLandmark leftEye, FaceLandmark rightEye, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.parseColor("#FFFFD700")); // Gold
        p.setStyle(Paint.Style.FILL);

        float r = fw * 0.15f;
        if (leftEye != null) {
            float lx = translateX(leftEye.getPosition().x, sx);
            float ly = leftEye.getPosition().y * sy;
            drawStar(canvas, lx, ly, r, p);
        }
        if (rightEye != null) {
            float rx = translateX(rightEye.getPosition().x, sx);
            float ry = rightEye.getPosition().y * sy;
            drawStar(canvas, rx, ry, r, p);
        }
    }

    private void drawStar(Canvas canvas, float cx, float cy, float r, Paint paint) {
        android.graphics.Path star = new android.graphics.Path();
        double angle = Math.PI / 5;
        for (int i = 0; i < 10; i++) {
            double radius = (i % 2 == 0) ? r : r * 0.4;
            double currAngle = i * angle - Math.PI / 2;
            float x = (float) (cx + Math.cos(currAngle) * radius);
            float y = (float) (cy + Math.sin(currAngle) * radius);
            if (i == 0) star.moveTo(x, y);
            else star.lineTo(x, y);
        }
        star.close();
        canvas.drawPath(star, paint);
    }

    private void drawHeartEyesLens(Canvas canvas, float cx, float cy, float fw, FaceLandmark leftEye, FaceLandmark rightEye, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.RED);
        p.setStyle(Paint.Style.FILL);

        float r = fw * 0.14f;
        if (leftEye != null) {
            float lx = translateX(leftEye.getPosition().x, sx);
            float ly = leftEye.getPosition().y * sy;
            drawHeart(canvas, lx, ly, r, p);
        }
        if (rightEye != null) {
            float rx = translateX(rightEye.getPosition().x, sx);
            float ry = rightEye.getPosition().y * sy;
            drawHeart(canvas, rx, ry, r, p);
        }
    }

    private void drawHeart(Canvas canvas, float cx, float cy, float r, Paint paint) {
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx, cy + r * 0.35f);
        path.cubicTo(cx - r * 0.8f, cy - r * 0.6f, cx - r * 1.5f, cy + r * 0.35f, cx, cy + r * 1.2f);
        path.cubicTo(cx + r * 1.5f, cy + r * 0.35f, cx + r * 0.8f, cy - r * 0.6f, cx, cy + r * 0.35f);
        canvas.drawPath(path, paint);
    }

    private void drawFireHeadLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);

        float fireY = cy - fh * 0.58f;
        float r = fw * 0.35f;

        // Orange base
        p.setColor(Color.parseColor("#FFFF8C00"));
        android.graphics.Path f1 = new android.graphics.Path();
        f1.moveTo(cx - r, fireY);
        f1.quadTo(cx - r*0.5f, fireY - r*1.5f, cx, fireY - r * 2.2f);
        f1.quadTo(cx + r*0.5f, fireY - r*1.5f, cx + r, fireY);
        f1.quadTo(cx, fireY + r*0.2f, cx - r, fireY);
        canvas.drawPath(f1, p);

        // Yellow inner
        p.setColor(Color.YELLOW);
        android.graphics.Path f2 = new android.graphics.Path();
        f2.moveTo(cx - r*0.6f, fireY);
        f2.quadTo(cx - r*0.3f, fireY - r*1.1f, cx, fireY - r * 1.7f);
        f2.quadTo(cx + r*0.3f, fireY - r*1.1f, cx + r*0.6f, fireY);
        f2.quadTo(cx, fireY + r*0.1f, cx - r*0.6f, fireY);
        canvas.drawPath(f2, p);
    }

    private void drawRainbowMouthLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);

        float mouthY = cy + fh * 0.28f;
        float streamW = fw * 0.35f;
        float streamH = getHeight() - mouthY;

        int[] colors = {Color.RED, Color.parseColor("#FFFF7F00"), Color.YELLOW, Color.GREEN, Color.BLUE, Color.parseColor("#FF4B0082"), Color.parseColor("#FF9400D3")};
        float stripW = streamW / colors.length;

        for (int i = 0; i < colors.length; i++) {
            p.setColor(colors[i]);
            float leftX = cx - (streamW / 2) + (i * stripW);
            canvas.drawRect(leftX, mouthY, leftX + stripW, mouthY + streamH, p);
        }
    }

    private void drawAlienLens(Canvas canvas, float cx, float cy, float fw, FaceLandmark leftEye, FaceLandmark rightEye, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);

        // Big green alien eyes
        p.setColor(Color.parseColor("#FF39FF14")); // Lime neon
        p.setStyle(Paint.Style.FILL);

        float eyeW = fw * 0.16f;
        float eyeH = fw * 0.24f;

        if (leftEye != null) {
            float lx = translateX(leftEye.getPosition().x, sx);
            float ly = leftEye.getPosition().y * sy;
            canvas.drawOval(lx - eyeW, ly - eyeH*0.6f, lx + eyeW, ly + eyeH*0.6f, p);
            p.setColor(Color.BLACK);
            canvas.drawCircle(lx, ly, 10, p);
            p.setColor(Color.parseColor("#FF39FF14"));
        }
        if (rightEye != null) {
            float rx = translateX(rightEye.getPosition().x, sx);
            float ry = rightEye.getPosition().y * sy;
            canvas.drawOval(rx - eyeW, ry - eyeH*0.6f, rx + eyeW, ry + eyeH*0.6f, p);
            p.setColor(Color.BLACK);
            canvas.drawCircle(rx, ry, 10, p);
        }

        // Antennae
        p.setColor(Color.parseColor("#FF39FF14"));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f);
        canvas.drawLine(cx - fw*0.25f, cy - fw*0.35f, cx - fw*0.4f, cy - fw*0.75f, p);
        canvas.drawLine(cx + fw*0.25f, cy - fw*0.35f, cx + fw*0.4f, cy - fw*0.75f, p);

        p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - fw*0.4f, cy - fw*0.75f, 15, p);
        canvas.drawCircle(cx + fw*0.4f, cy - fw*0.75f, 15, p);
    }

    private void drawPirateLens(Canvas canvas, float cx, float cy, float fw, float fh, FaceLandmark leftEye, FaceLandmark rightEye, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);

        // Pirate Hat (black curve)
        p.setColor(Color.BLACK);
        float hatY = cy - fh * 0.48f;
        android.graphics.Path hat = new android.graphics.Path();
        hat.moveTo(cx - fw*0.7f, hatY);
        hat.cubicTo(cx - fw*0.5f, hatY - fh*0.4f, cx + fw*0.5f, hatY - fh*0.4f, cx + fw*0.7f, hatY);
        hat.lineTo(cx + fw*0.3f, hatY - fh*0.12f);
        hat.lineTo(cx - fw*0.3f, hatY - fh*0.12f);
        hat.close();
        canvas.drawPath(hat, p);

        // White Skull
        p.setColor(Color.WHITE);
        canvas.drawCircle(cx, hatY - fh*0.18f, fw*0.06f, p);
        canvas.drawRect(cx - 10, hatY - fh*0.13f, cx + 10, hatY - fh*0.08f, p);

        // Black eyepatch over right eye
        if (rightEye != null) {
            p.setColor(Color.BLACK);
            float rx = translateX(rightEye.getPosition().x, sx);
            float ry = rightEye.getPosition().y * sy;
            canvas.drawCircle(rx, ry, fw * 0.12f, p);
            // Strap
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5f);
            canvas.drawLine(cx - fw*0.4f, cy - fh*0.3f, cx + fw*0.5f, cy - fh*0.1f, p);
        }
    }

    private void drawClownLens(Canvas canvas, float cx, float cy, float fw, float fh, FaceLandmark nose, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);

        // Round red nose
        p.setColor(Color.RED);
        p.setStyle(Paint.Style.FILL);
        if (nose != null) {
            float nx = translateX(nose.getPosition().x, sx);
            float ny = nose.getPosition().y * sy;
            canvas.drawCircle(nx, ny, fw * 0.12f, p);
        } else {
            canvas.drawCircle(cx, cy, fw * 0.12f, p);
        }

        // Custom cheek rouge
        p.setColor(Color.parseColor("#4DFF00FF")); // Soft magenta
        canvas.drawCircle(cx - fw*0.3f, cy + fh*0.12f, fw*0.1f, p);
        canvas.drawCircle(cx + fw*0.3f, cy + fh*0.12f, fw*0.1f, p);
    }

    private void drawSuperheroLens(Canvas canvas, float cx, float cy, float fw, FaceLandmark leftEye, FaceLandmark rightEye, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.parseColor("#E6121212")); // Dark black mask
        p.setStyle(Paint.Style.FILL);

        // Mask shape around both eyes
        if (leftEye != null && rightEye != null) {
            float lx = translateX(leftEye.getPosition().x, sx);
            float ly = leftEye.getPosition().y * sy;
            float rx = translateX(rightEye.getPosition().x, sx);
            float ry = rightEye.getPosition().y * sy;

            float midX = (lx + rx) / 2;
            float midY = (ly + ry) / 2;
            float maskW = fw * 0.95f;
            float maskH = fw * 0.28f;

            canvas.drawRoundRect(midX - maskW/2, midY - maskH/2, midX + maskW/2, midY + maskH/2, 25f, 25f, p);
            
            // Eye holes cut out (draw white circles)
            p.setColor(Color.WHITE);
            canvas.drawCircle(lx, ly, fw * 0.07f, p);
            canvas.drawCircle(rx, ry, fw * 0.07f, p);
        }
    }

    private void drawVampireLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);

        // Red glowing eyes
        p.setColor(Color.parseColor("#80FF0000")); // red glow
        p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - fw * 0.24f, cy - fh * 0.12f, fw * 0.09f, p);
        canvas.drawCircle(cx + fw * 0.24f, cy - fh * 0.12f, fw * 0.09f, p);

        // Vampire Fangs
        p.setColor(Color.WHITE);
        float mouthY = cy + fh * 0.24f;
        android.graphics.Path fangL = new android.graphics.Path();
        fangL.moveTo(cx - fw*0.12f, mouthY);
        fangL.lineTo(cx - fw*0.08f, mouthY + fw*0.12f);
        fangL.lineTo(cx - fw*0.04f, mouthY);
        fangL.close();
        canvas.drawPath(fangL, p);

        android.graphics.Path fangR = new android.graphics.Path();
        fangR.moveTo(cx + fw*0.04f, mouthY);
        fangR.lineTo(cx + fw*0.08f, mouthY + fw*0.12f);
        fangR.lineTo(cx + fw*0.12f, mouthY);
        fangR.close();
        canvas.drawPath(fangR, p);
    }

    private void drawWizardLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);

        // Tall pointed hat
        p.setColor(Color.parseColor("#FF3F51B5")); // Deep blue
        p.setStyle(Paint.Style.FILL);
        float hatY = cy - fh * 0.48f;
        android.graphics.Path hat = new android.graphics.Path();
        hat.moveTo(cx - fw*0.65f, hatY);
        hat.lineTo(cx, hatY - fh * 1.3f);
        hat.lineTo(cx + fw*0.65f, hatY);
        hat.close();
        canvas.drawPath(hat, p);

        // Gold stars on hat
        p.setColor(Color.YELLOW);
        canvas.drawCircle(cx - 15, hatY - fh*0.4f, 8, p);
        canvas.drawCircle(cx + 25, hatY - fh*0.6f, 10, p);
    }

    private void drawSpaceHelmetLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);

        // Circular helmet bubble
        p.setColor(Color.parseColor("#2600E5FF")); // Translucent neon cyan
        p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, fw * 0.95f, p);

        p.setColor(Color.parseColor("#8000E5FF"));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f);
        canvas.drawCircle(cx, cy, fw * 0.95f, p);

        // HUD Text
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(fw * 0.05f);
        p.setColor(Color.parseColor("#FF00E5FF"));
        canvas.drawText("O2_100%", cx - fw * 0.15f, cy + fw * 0.82f, p);
    }

    private void drawButterflyLens(Canvas canvas, float cx, float cy, float fw, float fh, float sx, float sy) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);

        // Orange butterflies on forehead
        float bY = cy - fh * 0.45f;
        float bSize = fw * 0.15f;

        // Butterfly 1 (left)
        p.setColor(Color.parseColor("#FFFF8C00")); // Orange
        canvas.drawOval(cx - fw*0.35f - bSize, bY - bSize, cx - fw*0.35f, bY + bSize, p);
        canvas.drawOval(cx - fw*0.35f, bY - bSize, cx - fw*0.35f + bSize, bY + bSize, p);
        p.setColor(Color.BLACK);
        canvas.drawRect(cx - fw*0.35f - 2, bY - bSize*1.1f, cx - fw*0.35f + 2, bY + bSize*1.1f, p);

        // Butterfly 2 (right)
        p.setColor(Color.parseColor("#FFFF8C00"));
        canvas.drawOval(cx + fw*0.35f - bSize, bY - bSize, cx + fw*0.35f, bY + bSize, p);
        canvas.drawOval(cx + fw*0.35f, bY - bSize, cx + fw*0.35f + bSize, bY + bSize, p);
        p.setColor(Color.BLACK);
        canvas.drawRect(cx + fw*0.35f - 2, bY - bSize*1.1f, cx + fw*0.35f + 2, bY + bSize*1.1f, p);
    }
}
