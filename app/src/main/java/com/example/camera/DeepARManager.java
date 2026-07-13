package com.example.camera;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceView;

import ai.deepar.ar.DeepAR;
import ai.deepar.ar.AREventListener;
import ai.deepar.ar.DeepARImageFormat;
import android.graphics.Bitmap;
import android.media.Image;

import android.view.SurfaceHolder;

public class DeepARManager implements SurfaceHolder.Callback, AREventListener {

    private static final String LICENSE_KEY = "729e3e12146e77faf618ae464bc1baf44e6c11043d1ccf5f6306a675d522a6a409b1d16b0955294f";
    private DeepAR deepAR;
    private SurfaceView surfaceView;
    private CaptureCallback captureCallback;

    public interface CaptureCallback {
        void onScreenshotTaken(Bitmap bitmap);
        void onVideoRecordingFinished(String videoPath);
    }

    public DeepARManager(Context context, SurfaceView surfaceView) {
        this.surfaceView = surfaceView;
        this.surfaceView.getHolder().addCallback(this);
        try {
            deepAR = new DeepAR(context);
            deepAR.setLicenseKey(LICENSE_KEY);
            deepAR.initialize(context, this);
        } catch (Exception e) {
            Log.e("DeepARManager", "Failed to initialize DeepAR", e);
        }
    }

    private boolean isInitialized = false;
    private SurfaceHolder pendingSurfaceHolder;
    private int pendingWidth, pendingHeight;

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e("DeepARManager", "surfaceCreated");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.e("DeepARManager", "surfaceChanged: " + width + "x" + height);
        if (deepAR != null) {
            if (isInitialized) {
                deepAR.setRenderSurface(holder.getSurface(), width, height);
            } else {
                pendingSurfaceHolder = holder;
                pendingWidth = width;
                pendingHeight = height;
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e("DeepARManager", "surfaceDestroyed");
        if (deepAR != null) {
            deepAR.setRenderSurface(null, 0, 0);
        }
    }

    public void switchEffect(String path) {
        if (deepAR != null) {
            deepAR.switchEffect("effect", path);
        }
    }

    public void receiveFrame(java.nio.ByteBuffer buffer, int width, int height, int orientation, boolean mirror, ai.deepar.ar.DeepARImageFormat format, int stride) {
        if (deepAR != null && isInitialized) {
            deepAR.receiveFrame(buffer, width, height, orientation, mirror, format, stride);
        }
    }

    public void release() {
        if (deepAR != null) {
            deepAR.setRenderSurface(null, 0, 0);
            deepAR.release();
            deepAR = null;
        }
    }

    public void takeScreenshot(CaptureCallback callback) {
        this.captureCallback = callback;
        if (deepAR != null) {
            deepAR.takeScreenshot();
        }
    }

    public void startVideoRecording(String outputPath, CaptureCallback callback) {
        this.captureCallback = callback;
        if (deepAR != null) {
            deepAR.startVideoRecording(outputPath);
        }
    }

    public void stopVideoRecording() {
        if (deepAR != null) {
            deepAR.stopVideoRecording();
        }
    }

    // AREventListener implementation
    @Override public void screenshotTaken(Bitmap bitmap) {
        if (captureCallback != null) captureCallback.onScreenshotTaken(bitmap);
    }
    @Override public void videoRecordingStarted() {}
    @Override public void videoRecordingFinished() {}
    @Override public void videoRecordingFailed() {}
    @Override public void videoRecordingPrepared() {}
    @Override public void shutdownFinished() {}
    
    @Override 
    public void initialized() {
        Log.e("DeepARManager", "DeepAR Initialized!");
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(surfaceView.getContext(), "DeepAR Initialized!", android.widget.Toast.LENGTH_SHORT).show();
        });
        isInitialized = true;
        if (pendingSurfaceHolder != null && deepAR != null) {
            deepAR.setRenderSurface(pendingSurfaceHolder.getSurface(), pendingWidth, pendingHeight);
            pendingSurfaceHolder = null;
        }
    }
    
    @Override public void faceVisibilityChanged(boolean b) {}
    @Override public void imageVisibilityChanged(String s, boolean b) {}
    @Override public void frameAvailable(Image image) {}
    
    @Override 
    public void error(ai.deepar.ar.ARErrorType t, String s) {
        Log.e("DeepARManager", "DeepAR Error: " + t.name() + " - " + s);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(surfaceView.getContext(), "DeepAR Error: " + s, android.widget.Toast.LENGTH_LONG).show();
        });
    }
    
    @Override public void effectSwitched(String s) {}
}
