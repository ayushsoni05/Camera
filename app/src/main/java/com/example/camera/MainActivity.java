package com.example.camera;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements android.hardware.SensorEventListener {

    private static final String TAG = "PrismaticAura";
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    
    private static final String[] REQUIRED_PERMISSIONS =
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ?
                    new String[]{
                            Manifest.permission.CAMERA, 
                            Manifest.permission.RECORD_AUDIO, 
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    } :
                    new String[]{
                            Manifest.permission.CAMERA, 
                            Manifest.permission.RECORD_AUDIO
                    };

    private ImageCapture imageCapture;
    private androidx.camera.video.VideoCapture<androidx.camera.video.Recorder> videoCapture;
    private androidx.camera.video.Recording activeRecording;
    
    private PreviewView viewFinder;
    private Camera camera;
    private ExecutorService cameraExecutor;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;
    private int timerSeconds = 0;

    // Sensor for 3D UI & Digital Leveling
    private android.hardware.SensorManager sensorManager;
    private android.hardware.Sensor rotationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private boolean hasVibratedLevel = false;

    // Filter Logic
    private String currentFilter = "Original";
    private final String[] filtersList = generateFiltersList();

    // Mode Selector Logic
    private enum CaptureMode {
        PHOTO, VIDEO, PORTRAIT, NIGHT, HDR
    }
    private CaptureMode activeMode = CaptureMode.PHOTO;
    private androidx.camera.extensions.ExtensionsManager extensionsManager;
    private boolean isPortraitSupported = false;
    private boolean isNightSupported = false;
    private boolean isHdrSupported = false;

    // Recording duration progress ring
    private int recordingProgressValue = 0;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (activeRecording != null) {
                recordingProgressValue += 1;
                ProgressBar pg = findViewById(R.id.record_progress);
                if (pg != null) {
                    pg.setProgress(recordingProgressValue);
                }
                if (recordingProgressValue >= 100) {
                    stopVideoRecordingForSnap();
                } else {
                    progressHandler.postDelayed(this, 150); // 150ms * 100 = 15 seconds limit
                }
            }
        }
    };

    // Face detector and Lenses
    private FaceDetector faceDetector;
    private String activeLensName = "None";
    private final String[] lensesList = {"None", "Dog", "Glasses", "Crown", "Stache"};
    private boolean isSmileShutterEnabled = true;

    // Post-capture State
    private Uri capturedUri;
    private boolean capturedIsPhoto = true;
    private String postCaptureFilter = "Original";

    // Memories Drawer Logic
    private List<MediaItem> galleryItems = new ArrayList<>();
    private MemoriesGridAdapter memoriesAdapter;
    private GalleryAdapter viewerAdapter;
    private GestureDetector viewFinderGestureDetector;

    private static class MediaItem {
        Uri uri;
        boolean isImage;
        long dateAdded;
        MediaItem(Uri uri, boolean isImage, long dateAdded) {
            this.uri = uri;
            this.isImage = isImage;
            this.dateAdded = dateAdded;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        if (viewFinder != null) {
            viewFinder.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        }

        sensorManager = (android.hardware.SensorManager) getSystemService(android.content.Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR);
        }

        // Initialize ML Kit Face Detector
        FaceDetectorOptions faceOptions = new FaceDetectorOptions.Builder()
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();
        faceDetector = FaceDetection.getClient(faceOptions);

        initializeUI();
        setupFilterCarousel();
        setupLensesCarousel();
        setupGestureDetectors();

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            initializeExtensionsAndStartCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Check if first-run tutorial is needed
        showTutorialIfNeeded();
    }

    private void showTutorialIfNeeded() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("is_first_run_snap", true);
        if (isFirstRun) {
            View tut = findViewById(R.id.tutorial_overlay);
            if (tut != null) tut.setVisibility(View.VISIBLE);
            findViewById(R.id.tutorial_dismiss).setOnClickListener(v -> {
                if (tut != null) tut.setVisibility(View.GONE);
                prefs.edit().putBoolean("is_first_run_snap", false).apply();
            });
        }
    }

    private void initializeUI() {
        ImageButton captureButton = findViewById(R.id.image_capture_button);
        MaterialButton flipButton = findViewById(R.id.btnFlip);
        MaterialButton flashButton = findViewById(R.id.btnFlash);
        MaterialButton gridButton = findViewById(R.id.btnGrid);
        MaterialButton timerButton = findViewById(R.id.btnTimer);
        SeekBar zoomSlider = findViewById(R.id.zoom_slider);

        if (flipButton != null) flipButton.setOnClickListener(v -> swapCamera());
        if (flashButton != null) flashButton.setOnClickListener(v -> toggleFlash(flashButton));
        if (gridButton != null) gridButton.setOnClickListener(v -> toggleGrid());
        if (timerButton != null) timerButton.setOnClickListener(v -> cycleTimer());
        
        setupZoomAndFocus(zoomSlider);
        setupShutterTouchGestures(captureButton);
        setupPostCaptureControls();
        setupMemoriesControls();
        loadLastSavedThumbnail();
    }

    private void setupGestureDetectors() {
        viewFinderGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                swapCamera();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && e2 != null && e1.getY() - e2.getY() > 150 && Math.abs(velocityY) > 150) {
                    openMemoriesDrawer();
                    return true;
                }
                return false;
            }
        });

        if (viewFinder != null) {
            viewFinder.setOnTouchListener((v, e) -> {
                viewFinderGestureDetector.onTouchEvent(e);
                return true;
            });
        }
    }

    private void setupShutterTouchGestures(ImageButton shutter) {
        if (shutter == null) return;

        shutter.setOnTouchListener(new View.OnTouchListener() {
            private float initialY = 0;
            private boolean isRecordingMode = false;
            private final Handler longPressHandler = new Handler(Looper.getMainLooper());
            private final Runnable startRecordingRunnable = () -> {
                isRecordingMode = true;
                startVideoRecordingForSnap();
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getRawY();
                        isRecordingMode = false;
                        longPressHandler.postDelayed(startRecordingRunnable, 400); // 400ms threshold for video
                        shutter.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (isRecordingMode) {
                            float diffY = initialY - event.getRawY();
                            if (diffY > 50 && camera != null) {
                                float zoomPercent = Math.min(1.0f, (diffY - 50) / 400f);
                                camera.getCameraControl().setLinearZoom(zoomPercent);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        longPressHandler.removeCallbacks(startRecordingRunnable);
                        shutter.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                        if (isRecordingMode) {
                            stopVideoRecordingForSnap();
                        } else {
                            handleCapture();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void setupFilterCarousel() {
        androidx.recyclerview.widget.RecyclerView filterRecycler = findViewById(R.id.filter_list);
        if (filterRecycler == null) return;
        
        filterRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this, 
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        
        filterRecycler.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<FilterViewHolder>() {
            @NonNull
            @Override
            public FilterViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, 
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                return new FilterViewHolder(tv);
            }

            @Override
            public void onBindViewHolder(@NonNull FilterViewHolder holder, int position) {
                String filterName = filtersList[position];
                TextView tv = (TextView) holder.itemView;
                tv.setText(filterName);
                tv.setPadding(28, 12, 28, 12);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setTextSize(11);

                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setCornerRadius(24);

                if (filterName.equals(currentFilter)) {
                    tv.setTextColor(android.graphics.Color.parseColor("#00F2FF"));
                    gd.setColor(android.graphics.Color.parseColor("#4D00F2FF"));
                    gd.setStroke(2, android.graphics.Color.parseColor("#00F2FF"));
                } else {
                    tv.setTextColor(android.graphics.Color.WHITE);
                    gd.setColor(android.graphics.Color.parseColor("#26FFFFFF"));
                    gd.setStroke(1, android.graphics.Color.parseColor("#1AFFFFFF"));
                }
                tv.setBackground(gd);

                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tv.getLayoutParams();
                if (params != null) {
                    params.setMargins(8, 0, 8, 0);
                    tv.setLayoutParams(params);
                }

                tv.setOnClickListener(v -> {
                    currentFilter = filterName;
                    applyFilterEffects(filterName);
                    notifyDataSetChanged();
                });
            }

            @Override
            public int getItemCount() { return filtersList.length; }
        });
    }

    private void setupLensesCarousel() {
        androidx.recyclerview.widget.RecyclerView lensesRecycler = findViewById(R.id.lenses_carousel);
        if (lensesRecycler == null) return;

        lensesRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this,
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));

        LensesAdapter adapter = new LensesAdapter(lensesList, activeLensName, lensName -> {
            activeLensName = lensName;
            FaceOverlayView overlay = findViewById(R.id.face_overlay);
            if (overlay != null) {
                overlay.setActiveLens(activeLensName);
            }
        });
        lensesRecycler.setAdapter(adapter);
    }

    private String[] generateFiltersList() {
        return new String[] {
            "Original", "Noir", "Retro", "Cyberpunk", "Cold Glitch", "Sunset Warm", 
            "Forest Green", "Dramatic", "Matrix Green", "Ocean Blue", "Polaroid Faded", "Acid Neon"
        };
    }

    private static class FilterViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        public FilterViewHolder(@NonNull View itemView) { super(itemView); }
    }

    private void applyFilterEffects(String filterName) {
        android.graphics.ColorMatrix matrix = getColorMatrixForFilter(filterName);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
        if (viewFinder != null) {
            viewFinder.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
        }
    }

    private android.graphics.ColorMatrix getColorMatrixForFilter(String filterName) {
        android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
        if (filterName.equals("Noir")) {
            matrix.setSaturation(0f);
        } else if (filterName.equals("Retro")) {
            matrix.set(new float[] {
                0.393f, 0.769f, 0.189f, 0, 0,
                0.349f, 0.686f, 0.168f, 0, 0,
                0.272f, 0.534f, 0.131f, 0, 0,
                0,      0,      0,      1, 0
            });
        } else if (filterName.equals("Cyberpunk")) {
            matrix.set(new float[] {
                1.2f, 0,    0.2f, 0, 0,
                0,    1.0f, 0,    0, 0,
                0.4f, 0,    1.4f, 0, 0,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Cold Glitch")) {
            matrix.set(new float[] {
                0.8f, 0,    0,    0, 0,
                0,    1.1f, 0,    0, 0,
                0,    0,    1.5f, 0, 0,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Sunset Warm")) {
            matrix.set(new float[] {
                1.3f, 0,    0,    0, 0,
                0,    0.9f, 0,    0, 0,
                0,    0,    0.7f, 0, 0,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Forest Green")) {
            matrix.set(new float[] {
                0.8f, 0,    0,    0, 0,
                0,    1.3f, 0,    0, 0,
                0,    0,    0.8f, 0, 0,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Dramatic")) {
            matrix.set(new float[] {
                1.2f, 0,    0,    0, -20f,
                0,    1.2f, 0,    0, -20f,
                0,    0,    1.2f, 0, -20f,
                0,    0,    0,    1, 0
            });
            android.graphics.ColorMatrix sat = new android.graphics.ColorMatrix();
            sat.setSaturation(0.6f);
            matrix.postConcat(sat);
        } else if (filterName.equals("Matrix Green")) {
            matrix.set(new float[] {
                0.3f, 0,    0,    0, 0,
                0,    1.5f, 0,    0, 0,
                0,    0,    0.3f, 0, 0,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Ocean Blue")) {
            matrix.set(new float[] {
                0.4f, 0,    0,    0, 0,
                0,    1.1f, 0,    0, 0,
                0,    0,    1.6f, 0, 0,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Polaroid Faded")) {
            matrix.set(new float[] {
                0.9f, 0.1f, 0.1f, 0, 10f,
                0.1f, 0.9f, 0.1f, 0, 10f,
                0.1f, 0.1f, 0.9f, 0, 20f,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Acid Neon")) {
            matrix.setSaturation(2.0f);
        }
        return matrix;
    }

    private void setupPostCaptureControls() {
        View textBtn = findViewById(R.id.post_btn_text);
        View doodleBtn = findViewById(R.id.post_btn_doodle);
        View muteBtn = findViewById(R.id.post_btn_mute);
        View closeBtn = findViewById(R.id.post_btn_close);
        View saveBtn = findViewById(R.id.post_btn_save);
        View shareBtn = findViewById(R.id.post_btn_share);
        DoodleView doodleView = findViewById(R.id.doodle_canvas);
        TextView textOverlay = findViewById(R.id.text_overlay);

        if (textBtn != null) {
            textBtn.setOnClickListener(v -> {
                EditText input = new EditText(this);
                if (textOverlay != null && textOverlay.getVisibility() == View.VISIBLE) {
                    input.setText(textOverlay.getText());
                }
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Add Snap Text")
                        .setView(input)
                        .setPositiveButton("Done", (dialog, which) -> {
                            String txt = input.getText().toString();
                            if (!txt.trim().isEmpty()) {
                                textOverlay.setText(txt);
                                textOverlay.setVisibility(View.VISIBLE);
                            } else {
                                textOverlay.setVisibility(View.GONE);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // Make text draggable
        if (textOverlay != null) {
            textOverlay.setOnTouchListener(new View.OnTouchListener() {
                float dX, dY;
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            dX = view.getX() - event.getRawX();
                            dY = view.getY() - event.getRawY();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            view.setX(event.getRawX() + dX);
                            view.setY(event.getRawY() + dY);
                            break;
                        default:
                            return false;
                    }
                    return true;
                }
            });
        }

        if (doodleBtn != null) {
            doodleBtn.setOnClickListener(v -> {
                if (doodleView != null) {
                    if (doodleView.getVisibility() == View.VISIBLE) {
                        doodleView.setVisibility(View.GONE);
                        doodleBtn.setBackgroundTintList(null);
                    } else {
                        doodleView.setVisibility(View.VISIBLE);
                        doodleBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                android.graphics.Color.parseColor("#00F2FF")));
                    }
                }
            });
        }

        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> {
                findViewById(R.id.post_capture_layer).setVisibility(View.GONE);
                android.widget.VideoView vv = findViewById(R.id.post_capture_video);
                if (vv != null && vv.isPlaying()) vv.stopPlayback();
                startCamera();
            });
        }

        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> saveFinalizedSnap());
        }

        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> {
                if (capturedUri != null) {
                    shareMedia(capturedUri, capturedIsPhoto);
                }
            });
        }

        // Swipe horizontal on post capture to change filters
        View postCaptureLayer = findViewById(R.id.post_capture_layer);
        if (postCaptureLayer != null) {
            postCaptureLayer.setOnTouchListener(new View.OnTouchListener() {
                private float initialX = 0;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = event.getX();
                            return true;
                        case MotionEvent.ACTION_UP:
                            float diffX = event.getX() - initialX;
                            if (Math.abs(diffX) > 150) {
                                cyclePostCaptureFilter(diffX > 0);
                            }
                            return true;
                    }
                    return false;
                }
            });
        }
    }

    private void cyclePostCaptureFilter(boolean forward) {
        int curIndex = 0;
        for (int i = 0; i < filtersList.length; i++) {
            if (filtersList[i].equals(postCaptureFilter)) {
                curIndex = i;
                break;
            }
        }
        if (forward) {
            curIndex = (curIndex + 1) % filtersList.length;
        } else {
            curIndex = (curIndex - 1 + filtersList.length) % filtersList.length;
        }
        postCaptureFilter = filtersList[curIndex];
        
        ImageView previewImage = findViewById(R.id.post_capture_image);
        if (previewImage != null) {
            android.graphics.ColorMatrix matrix = getColorMatrixForFilter(postCaptureFilter);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
            previewImage.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
        }
        
        TextView label = findViewById(R.id.post_filter_label);
        if (label != null) {
            label.setVisibility(View.VISIBLE);
            label.setText(postCaptureFilter);
            label.onCancelPendingInputEvents();
            label.postDelayed(() -> label.setVisibility(View.GONE), 1000);
        }
    }

    private void saveFinalizedSnap() {
        if (capturedUri == null) return;
        if (!capturedIsPhoto) {
            // It's a video, we just save it directly (already written to MediaStore)
            Toast.makeText(this, "Video Saved to Memories!", Toast.LENGTH_SHORT).show();
            findViewById(R.id.post_capture_layer).setVisibility(View.GONE);
            loadLastSavedThumbnail();
            startCamera();
            return;
        }

        Toast.makeText(this, "Saving snap...", Toast.LENGTH_SHORT).show();

        cameraExecutor.execute(() -> {
            try {
                android.graphics.Bitmap srcBitmap;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    srcBitmap = android.graphics.ImageDecoder.decodeBitmap(
                            android.graphics.ImageDecoder.createSource(getContentResolver(), capturedUri),
                            (decoder, info, source) -> decoder.setMutableRequired(true)
                    );
                } else {
                    srcBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), capturedUri);
                    srcBitmap = srcBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
                }

                android.graphics.Bitmap finalBitmap = android.graphics.Bitmap.createBitmap(
                        srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig());
                android.graphics.Canvas canvas = new android.graphics.Canvas(finalBitmap);

                // 1. Draw base with color filter
                android.graphics.Paint paint = new android.graphics.Paint();
                android.graphics.ColorMatrix matrix = getColorMatrixForFilter(postCaptureFilter);
                paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
                canvas.drawBitmap(srcBitmap, 0, 0, paint);

                // 2. Draw doodle overlay
                DoodleView doodleView = findViewById(R.id.doodle_canvas);
                if (doodleView != null && doodleView.getVisibility() == View.VISIBLE) {
                    android.graphics.Bitmap doodleBmp = doodleView.exportBitmap();
                    android.graphics.Bitmap scaledDoodle = android.graphics.Bitmap.createScaledBitmap(
                            doodleBmp, srcBitmap.getWidth(), srcBitmap.getHeight(), true);
                    canvas.drawBitmap(scaledDoodle, 0, 0, null);
                }

                // 3. Draw text overlay
                TextView tv = findViewById(R.id.text_overlay);
                if (tv != null && tv.getVisibility() == View.VISIBLE) {
                    android.graphics.Paint textPaint = new android.graphics.Paint();
                    textPaint.setColor(tv.getCurrentTextColor());
                    // Scale text size proportional to resolution
                    textPaint.setTextSize(tv.getTextSize() * (srcBitmap.getWidth() / (float) viewFinder.getWidth()));
                    textPaint.setAntiAlias(true);
                    textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    
                    float relX = tv.getX() / (float) viewFinder.getWidth() * srcBitmap.getWidth();
                    float relY = (tv.getY() + tv.getBaseline()) / (float) viewFinder.getHeight() * srcBitmap.getHeight();
                    canvas.drawText(tv.getText().toString(), relX, relY, textPaint);
                }

                // Write back
                try (java.io.OutputStream out = getContentResolver().openOutputStream(capturedUri)) {
                    finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out);
                }

                srcBitmap.recycle();
                finalBitmap.recycle();

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Saved to Memories!", Toast.LENGTH_SHORT).show();
                    findViewById(R.id.post_capture_layer).setVisibility(View.GONE);
                    
                    // Reset doodle & text
                    if (doodleView != null) {
                        doodleView.clearCanvas();
                        doodleView.setVisibility(View.GONE);
                    }
                    if (tv != null) {
                        tv.setText("");
                        tv.setVisibility(View.GONE);
                    }
                    
                    loadLastSavedThumbnail();
                    startCamera();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error finalizing snap", e);
            }
        });
    }

    private void launchPostCapturePreview(Uri uri, boolean isPhoto) {
        capturedUri = uri;
        capturedIsPhoto = isPhoto;
        postCaptureFilter = "Original";

        runOnUiThread(() -> {
            findViewById(R.id.post_capture_layer).setVisibility(View.VISIBLE);
            
            ImageView previewImg = findViewById(R.id.post_capture_image);
            android.widget.VideoView previewVid = findViewById(R.id.post_capture_video);
            View muteBtn = findViewById(R.id.post_btn_mute);

            // Apply default identity matrix
            if (previewImg != null) {
                previewImg.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            if (isPhoto) {
                if (previewImg != null) {
                    previewImg.setVisibility(View.VISIBLE);
                    previewImg.setImageURI(uri);
                }
                if (previewVid != null) previewVid.setVisibility(View.GONE);
                if (muteBtn != null) muteBtn.setVisibility(View.GONE);
            } else {
                if (previewImg != null) previewImg.setVisibility(View.GONE);
                if (previewVid != null) {
                    previewVid.setVisibility(View.VISIBLE);
                    previewVid.setVideoURI(uri);
                    previewVid.setOnPreparedListener(mp -> {
                        mp.setLooping(true);
                        previewVid.start();
                    });
                }
                if (muteBtn != null) {
                    muteBtn.setVisibility(View.VISIBLE);
                    muteBtn.setOnClickListener(v -> {
                        // Simple mute/unmute simulation
                        Toast.makeText(MainActivity.this, "Audio toggled", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void setupMemoriesControls() {
        androidx.recyclerview.widget.RecyclerView grid = findViewById(R.id.memories_grid);
        if (grid != null) {
            grid.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        }

        View backBtn = findViewById(R.id.memories_btn_back);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> {
                findViewById(R.id.memories_drawer).setVisibility(View.GONE);
                loadLastSavedThumbnail();
            });
        }
    }

    private void openMemoriesDrawer() {
        cameraExecutor.execute(() -> {
            galleryItems = getCapturedMedia();
            runOnUiThread(() -> {
                View drawer = findViewById(R.id.memories_drawer);
                if (drawer != null) {
                    drawer.setVisibility(View.VISIBLE);
                }
                
                androidx.recyclerview.widget.RecyclerView grid = findViewById(R.id.memories_grid);
                if (grid != null) {
                    memoriesAdapter = new MemoriesGridAdapter(this, galleryItems, position -> {
                        // Open in pager viewer
                        openMemoriesFullscreenViewer(position);
                    });
                    grid.setAdapter(memoriesAdapter);
                }
            });
        });
    }

    private void openMemoriesFullscreenViewer(int startPosition) {
        View viewer = findViewById(R.id.memories_viewer);
        if (viewer != null) viewer.setVisibility(View.VISIBLE);

        androidx.viewpager2.widget.ViewPager2 viewPager = findViewById(R.id.gallery_viewpager);
        if (viewPager != null) {
            viewerAdapter = new GalleryAdapter(this, galleryItems);
            viewPager.setAdapter(viewerAdapter);
            viewPager.setCurrentItem(startPosition, false);
        }

        View backBtn = findViewById(R.id.viewer_btn_back);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> {
                if (viewer != null) viewer.setVisibility(View.GONE);
                openMemoriesDrawer(); // refresh drawer grid
            });
        }

        View shareBtn = findViewById(R.id.gallery_btn_share);
        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> {
                if (viewPager != null) {
                    int pos = viewPager.getCurrentItem();
                    if (pos >= 0 && pos < galleryItems.size()) {
                        MediaItem item = galleryItems.get(pos);
                        shareMedia(item.uri, item.isImage);
                    }
                }
            });
        }

        View deleteBtn = findViewById(R.id.gallery_btn_delete);
        if (deleteBtn != null) {
            deleteBtn.setOnClickListener(v -> {
                if (viewPager != null) {
                    int pos = viewPager.getCurrentItem();
                    if (pos >= 0 && pos < galleryItems.size()) {
                        MediaItem item = galleryItems.get(pos);
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Delete Snap")
                                .setMessage("Delete this snap permanently?")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    deleteMedia(item, pos);
                                    if (galleryItems.isEmpty()) {
                                        if (viewer != null) viewer.setVisibility(View.GONE);
                                        findViewById(R.id.memories_drawer).setVisibility(View.GONE);
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                }
            });
        }
    }

    private void initializeExtensionsAndStartCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                ListenableFuture<androidx.camera.extensions.ExtensionsManager> extFuture = 
                        androidx.camera.extensions.ExtensionsManager.getInstanceAsync(this, provider);
                extFuture.addListener(() -> {
                    try {
                        extensionsManager = extFuture.get();
                        checkExtensionAvailability();
                    } catch (Exception e) {
                        Log.e(TAG, "Extensions init failed", e);
                    }
                    startCamera();
                }, ContextCompat.getMainExecutor(this));
            } catch (Exception e) {
                Log.e(TAG, "Provider init failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void checkExtensionAvailability() {
        if (extensionsManager == null) return;
        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        try {
            isPortraitSupported = extensionsManager.isExtensionAvailable(selector, androidx.camera.extensions.ExtensionMode.BOKEH);
            isNightSupported = extensionsManager.isExtensionAvailable(selector, androidx.camera.extensions.ExtensionMode.NIGHT);
            isHdrSupported = extensionsManager.isExtensionAvailable(selector, androidx.camera.extensions.ExtensionMode.HDR);
        } catch (Exception e) {
            Log.e(TAG, "Error checking extensions availability", e);
        }
    }

    @Override
    public void onSensorChanged(android.hardware.SensorEvent event) {
        if (event.sensor.getType() == android.hardware.Sensor.TYPE_ROTATION_VECTOR) {
            android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            android.hardware.SensorManager.getOrientation(rotationMatrix, orientationAngles);
            
            float pitch = (float) Math.toDegrees(orientationAngles[1]);
            float roll = (float) Math.toDegrees(orientationAngles[2]);
            
            View container = findViewById(R.id.capture_container);
            if (container != null) {
                container.setTranslationX(roll * 2f);
                container.setTranslationY(pitch * 2f);
            }
            
            View flash = findViewById(R.id.btnFlash);
            if (flash != null) {
                flash.setRotationX(pitch * 0.5f);
                flash.setRotationY(-roll * 0.5f);
            }

            View levelLine = findViewById(R.id.level_line);
            if (levelLine != null) {
                float closestTarget = 0;
                if (Math.abs(roll - 90) < 45) closestTarget = 90;
                else if (Math.abs(roll + 90) < 45) closestTarget = -90;
                else if (Math.abs(roll - 180) < 45) closestTarget = 180;
                else if (Math.abs(roll + 180) < 45) closestTarget = -180;

                float diff = Math.abs(roll - closestTarget);
                levelLine.setRotation(-(roll - closestTarget));

                if (diff < 1.0f) {
                    levelLine.setBackgroundColor(android.graphics.Color.parseColor("#39FF14"));
                    triggerHapticFeedbackOnce();
                } else {
                    levelLine.setBackgroundColor(android.graphics.Color.WHITE);
                    hasVibratedLevel = false;
                }
            }
        }
    }

    private void triggerHapticFeedbackOnce() {
        if (!hasVibratedLevel) {
            if (viewFinder != null) {
                viewFinder.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }
            hasVibratedLevel = true;
        }
    }

    @Override
    public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, android.hardware.SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    private void handleCapture() {
        if (timerSeconds > 0) {
            Toast.makeText(this, "Capturing in " + timerSeconds + "s...", Toast.LENGTH_SHORT).show();
            findViewById(R.id.image_capture_button).postDelayed(this::takePhoto, timerSeconds * 1000L);
        } else {
            takePhoto();
        }
    }

    private void swapCamera() {
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK) ?
                CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        checkExtensionAvailability();
        startCamera();
    }

    private void toggleFlash(MaterialButton btn) {
        flashMode = (flashMode + 1) % 3;
        int icon = R.drawable.ic_flash_off;
        if (flashMode == ImageCapture.FLASH_MODE_ON) icon = R.drawable.ic_flash_on;
        else if (flashMode == ImageCapture.FLASH_MODE_AUTO) icon = R.drawable.ic_flash_auto;
        btn.setIconResource(icon);
        if (imageCapture != null) imageCapture.setFlashMode(flashMode);
    }

    private void toggleGrid() {
        View h1 = findViewById(R.id.grid_line_h1);
        int nextVis = (h1 != null && h1.getVisibility() == View.VISIBLE) ? View.GONE : View.VISIBLE;
        View[] lines = {
            findViewById(R.id.grid_line_h1),
            findViewById(R.id.grid_line_h2),
            findViewById(R.id.grid_line_v1),
            findViewById(R.id.grid_line_v2)
        };
        for (View l : lines) if (l != null) l.setVisibility(nextVis);
    }

    private void cycleTimer() {
        timerSeconds = (timerSeconds == 0) ? 3 : (timerSeconds == 3 ? 10 : 0);
        Toast.makeText(this, "Timer: " + (timerSeconds == 0 ? "Off" : timerSeconds + "s"), Toast.LENGTH_SHORT).show();
    }

    private void setupZoomAndFocus(SeekBar slider) {
        if (slider != null) {
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int p, boolean u) {
                    if (u && camera != null) camera.getCameraControl().setLinearZoom(p / 100f);
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {
                    sb.postDelayed(() -> sb.setVisibility(View.GONE), 2000);
                }
            });
        }

        android.view.ScaleGestureDetector scaleDetector = new android.view.ScaleGestureDetector(this,
                new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(android.view.ScaleGestureDetector d) {
                if (camera == null) return true;
                float z = camera.getCameraInfo().getZoomState().getValue().getLinearZoom();
                camera.getCameraControl().setLinearZoom(Math.max(0f, Math.min(1f, z + (d.getScaleFactor()-1f)*2f)));
                if (slider != null) {
                    slider.setVisibility(View.VISIBLE);
                    slider.setProgress((int) (z * 100));
                }
                return true;
            }
        });

        // We combine the viewFinderGestureDetector and scaleDetector in onTouch
        if (viewFinder != null) {
            viewFinder.setOnTouchListener((v, e) -> {
                scaleDetector.onTouchEvent(e);
                viewFinderGestureDetector.onTouchEvent(e);
                if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                    if (!scaleDetector.isInProgress()) {
                        focusAndShowControls(e.getX(), e.getY());
                    }
                }
                return true;
            });
        }
    }

    private void focus(float x, float y) {
        if (camera == null || viewFinder == null) return;
        MeteringPoint point = viewFinder.getMeteringPointFactory().createPoint(x, y);
        camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).build());
    }

    private void focusAndShowControls(float x, float y) {
        focus(x, y);

        View focusRing = findViewById(R.id.focus_ring);
        if (focusRing != null) {
            focusRing.setVisibility(View.VISIBLE);
            focusRing.setX(x - focusRing.getWidth() / 2f);
            focusRing.setY(y - focusRing.getHeight() / 2f);
            focusRing.setScaleX(1.5f);
            focusRing.setScaleY(1.5f);
            
            focusRing.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(250)
                    .withEndAction(() -> {
                        focusRing.postDelayed(() -> focusRing.setVisibility(View.GONE), 3000);
                    })
                    .start();
        }

        SeekBar expSlider = findViewById(R.id.exposure_slider);
        if (expSlider != null && camera != null) {
            androidx.camera.core.ExposureState exposureState = camera.getCameraInfo().getExposureState();
            if (exposureState.isExposureCompensationSupported()) {
                expSlider.setVisibility(View.VISIBLE);
                
                float density = getResources().getDisplayMetrics().density;
                float offset = 48 * density;
                
                expSlider.setX(x + offset - expSlider.getWidth() / 2f);
                expSlider.setY(y - expSlider.getHeight() / 2f);
                
                android.util.Range<Integer> range = exposureState.getExposureCompensationRange();
                expSlider.setMax(range.getUpper() - range.getLower());
                expSlider.setProgress(exposureState.getExposureCompensationIndex() - range.getLower());
                
                expSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && camera != null) {
                            int targetIndex = progress + range.getLower();
                            camera.getCameraControl().setExposureCompensationIndex(targetIndex);
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {
                        seekBar.postDelayed(() -> seekBar.setVisibility(View.GONE), 3000);
                    }
                });
            } else {
                expSlider.setVisibility(View.GONE);
            }
        }
    }

    private void startCamera() {
        if (!allPermissionsGranted()) return;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                provider.unbindAll();

                Preview preview = new Preview.Builder().build();
                if (viewFinder != null) {
                    preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                }

                CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

                if (extensionsManager != null) {
                    int extMode = -1;
                    if (activeMode == CaptureMode.PORTRAIT && isPortraitSupported) {
                        extMode = androidx.camera.extensions.ExtensionMode.BOKEH;
                    } else if (activeMode == CaptureMode.NIGHT && isNightSupported) {
                        extMode = androidx.camera.extensions.ExtensionMode.NIGHT;
                    } else if (activeMode == CaptureMode.HDR && isHdrSupported) {
                        extMode = androidx.camera.extensions.ExtensionMode.HDR;
                    }

                    if (extMode != -1 && extensionsManager.isExtensionAvailable(selector, extMode)) {
                        selector = extensionsManager.getExtensionEnabledCameraSelector(selector, extMode);
                    }
                }

                // ImageAnalysis use case for live ML Kit face stickers tracking
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                boolean isFront = (lensFacing == CameraSelector.LENS_FACING_FRONT);
                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
                    android.media.Image mediaImage = imageProxy.getImage();
                    if (mediaImage != null) {
                        com.google.mlkit.vision.common.InputImage image =
                                com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                        faceDetector.process(image)
                                .addOnSuccessListener(faces -> {
                                    FaceOverlayView overlay = findViewById(R.id.face_overlay);
                                    if (overlay != null) {
                                        overlay.setFaces(faces, imageProxy.getWidth(), imageProxy.getHeight(), isFront);
                                        
                                        // Auto-smile capture triggers photo capture automatically!
                                        if (activeMode == CaptureMode.PHOTO && !faces.isEmpty() && isSmileShutterEnabled) {
                                            for (com.google.mlkit.vision.face.Face face : faces) {
                                                if (face.getSmilingProbability() != null && face.getSmilingProbability() > 0.85f) {
                                                    // Throttle smile shutter to avoid loops
                                                    isSmileShutterEnabled = false;
                                                    runOnUiThread(() -> {
                                                        Toast.makeText(MainActivity.this, "Smiling! Taking Snap...", Toast.LENGTH_SHORT).show();
                                                        takePhoto();
                                                        new Handler(Looper.getMainLooper()).postDelayed(() -> isSmileShutterEnabled = true, 5000);
                                                    });
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Face analysis failed", e))
                                .addOnCompleteListener(task -> imageProxy.close());
                    } else {
                        imageProxy.close();
                    }
                });

                if (activeMode == CaptureMode.VIDEO) {
                    androidx.camera.video.Recorder recorder = new androidx.camera.video.Recorder.Builder()
                            .setQualitySelector(androidx.camera.video.QualitySelector.from(androidx.camera.video.Quality.HIGHEST))
                            .build();
                    videoCapture = androidx.camera.video.VideoCapture.withOutput(recorder);
                    camera = provider.bindToLifecycle(this, selector, preview, videoCapture, imageAnalysis);
                } else {
                    imageCapture = new ImageCapture.Builder()
                            .setFlashMode(flashMode)
                            .build();
                    camera = provider.bindToLifecycle(this, selector, preview, imageCapture, imageAnalysis);
                }

                runOnUiThread(() -> applyFilterEffects(currentFilter));

            } catch (Exception e) { Log.e(TAG, "Binding failed", e); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        // Selfie Soft Flash simulation
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            View flashOverlay = findViewById(R.id.selfie_flash_overlay);
            if (flashOverlay != null) {
                flashOverlay.setVisibility(View.VISIBLE);
                flashOverlay.animate().alpha(1.0f).setDuration(100).withEndAction(() -> {
                    capturePhotoFlow();
                    flashOverlay.animate().alpha(0.0f).setDuration(300).withEndAction(() -> flashOverlay.setVisibility(View.GONE));
                });
                return;
            }
        }
        capturePhotoFlow();
    }

    private void capturePhotoFlow() {
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis());
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PrismaticAura");
        }

        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv).build();

        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults o) {
                Uri uri = o.getSavedUri();
                launchPostCapturePreview(uri, true);
            }
            @Override public void onError(@NonNull ImageCaptureException e) { Log.e(TAG, "Fail", e); }
        });
    }

    private void startVideoRecordingForSnap() {
        if (videoCapture == null) {
            activeMode = CaptureMode.VIDEO;
            startCamera();
            new Handler(Looper.getMainLooper()).postDelayed(this::startVideoRecordingFlow, 600);
        } else {
            startVideoRecordingFlow();
        }
    }

    private void startVideoRecordingFlow() {
        long timestamp = System.currentTimeMillis();
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(timestamp);
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PrismaticAura");
        }

        androidx.camera.video.MediaStoreOutputOptions options = new androidx.camera.video.MediaStoreOutputOptions.Builder(
                getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(cv)
                .build();

        boolean hasAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        try {
            androidx.camera.video.PendingRecording recordingBuilder = videoCapture.getOutput().prepareRecording(this, options);
            if (hasAudioPermission) {
                recordingBuilder = recordingBuilder.withAudioEnabled();
            }
            
            activeRecording = recordingBuilder.start(ContextCompat.getMainExecutor(this), new androidx.core.util.Consumer<androidx.camera.video.VideoRecordEvent>() {
                @Override
                public void accept(androidx.camera.video.VideoRecordEvent recordEvent) {
                    if (recordEvent instanceof androidx.camera.video.VideoRecordEvent.Start) {
                        runOnUiThread(() -> {
                            ProgressBar pg = findViewById(R.id.record_progress);
                            if (pg != null) {
                                pg.setVisibility(View.VISIBLE);
                                pg.setProgress(0);
                            }
                            View timerCont = findViewById(R.id.recording_timer_container);
                            if (timerCont != null) timerCont.setVisibility(View.VISIBLE);
                            startRecordingTimer();
                        });
                    } else if (recordEvent instanceof androidx.camera.video.VideoRecordEvent.Finalize) {
                        androidx.camera.video.VideoRecordEvent.Finalize finalizeEvent = (androidx.camera.video.VideoRecordEvent.Finalize) recordEvent;
                        runOnUiThread(() -> {
                            ProgressBar pg = findViewById(R.id.record_progress);
                            if (pg != null) pg.setVisibility(View.GONE);
                            View timerCont = findViewById(R.id.recording_timer_container);
                            if (timerCont != null) timerCont.setVisibility(View.GONE);
                            stopRecordingTimer();
                        });
                        
                        if (!finalizeEvent.hasError()) {
                            Uri savedUri = finalizeEvent.getOutputResults().getOutputUri();
                            launchPostCapturePreview(savedUri, false);
                        } else {
                            Log.e(TAG, "Video recording error: " + finalizeEvent.getError());
                        }
                        activeRecording = null;
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Recording security exception", e);
        }
    }

    private void stopVideoRecordingForSnap() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
    }

    private void startRecordingTimer() {
        recordingStartTime = System.currentTimeMillis();
        timerHandler.post(timerRunnable);
    }

    private void stopRecordingTimer() {
        timerHandler.removeCallbacks(timerRunnable);
        View dot = findViewById(R.id.recording_dot);
        if (dot != null) dot.setVisibility(View.VISIBLE);
    }

    private boolean allPermissionsGranted() {
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void loadLastSavedThumbnail() {
        cameraExecutor.execute(() -> {
            List<MediaItem> media = getCapturedMedia();
            if (!media.isEmpty()) {
                MediaItem lastItem = media.get(0);
                runOnUiThread(() -> {
                    ImageView lastImage = findViewById(R.id.last_image_preview);
                    if (lastImage != null) {
                        lastImage.setImageURI(null);
                        lastImage.setImageURI(lastItem.uri);
                    }
                });
            }
        });
    }

    private List<MediaItem> getCapturedMedia() {
        List<MediaItem> list = new ArrayList<>();
        
        Uri imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED };
        String selection = null;
        String[] selectionArgs = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
            selectionArgs = new String[]{ "%PrismaticAura%" };
        }
        try (android.database.Cursor cursor = getContentResolver().query(imagesUri, projection, selection, selectionArgs, null)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    long date = cursor.getLong(dateCol);
                    Uri uri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    list.add(new MediaItem(uri, true, date));
                }
            }
        } catch (Exception e) { Log.e(TAG, "Query images error", e); }

        Uri videosUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] vidProjection = { MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED };
        String vidSelection = null;
        String[] vidSelectionArgs = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vidSelection = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";
            vidSelectionArgs = new String[]{ "%PrismaticAura%" };
        }
        try (android.database.Cursor cursor = getContentResolver().query(videosUri, vidProjection, vidSelection, vidSelectionArgs, null)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    long date = cursor.getLong(dateCol);
                    Uri uri = android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    list.add(new MediaItem(uri, false, date));
                }
            }
        } catch (Exception e) { Log.e(TAG, "Query videos error", e); }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list.sort((o1, o2) -> Long.compare(o2.dateAdded, o1.dateAdded));
        } else {
            java.util.Collections.sort(list, (o1, o2) -> Long.compare(o2.dateAdded, o1.dateAdded));
        }
        return list;
    }

    private void shareMedia(Uri uri, boolean isImage) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType(isImage ? "image/jpeg" : "video/mp4");
        shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(android.content.Intent.createChooser(shareIntent, "Share Snap"));
    }

    private void deleteMedia(MediaItem item, int position) {
        try {
            getContentResolver().delete(item.uri, null, null);
            galleryItems.remove(position);
            if (viewerAdapter != null) {
                viewerAdapter.notifyItemRemoved(position);
            }
            if (memoriesAdapter != null) {
                memoriesAdapter.notifyItemRemoved(position);
            }
            loadLastSavedThumbnail();
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Delete failed", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            initializeExtensionsAndStartCamera();
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // Pager adapter for Memories Fullscreen Viewer
    private static class GalleryAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<GalleryAdapter.ViewHolder> {
        private final List<MediaItem> items;
        private final android.content.Context context;

        GalleryAdapter(android.content.Context context, List<MediaItem> items) {
            this.context = context;
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            FrameLayout root = new FrameLayout(parent.getContext());
            root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setId(View.generateViewId());
            imageView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            root.addView(imageView);
            
            android.widget.VideoView videoView = new android.widget.VideoView(parent.getContext());
            videoView.setId(View.generateViewId());
            FrameLayout.LayoutParams vvParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            vvParams.gravity = android.view.Gravity.CENTER;
            videoView.setLayoutParams(vvParams);
            videoView.setVisibility(View.GONE);
            root.addView(videoView);

            ImageView playButton = new ImageView(parent.getContext());
            playButton.setId(View.generateViewId());
            FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(128, 128);
            pbParams.gravity = android.view.Gravity.CENTER;
            playButton.setLayoutParams(pbParams);
            playButton.setImageResource(android.R.drawable.ic_media_play);
            playButton.setVisibility(View.GONE);
            root.addView(playButton);
            
            return new ViewHolder(root, imageView, videoView, playButton);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MediaItem item = items.get(position);
            if (item.isImage) {
                holder.imageView.setVisibility(View.VISIBLE);
                holder.videoView.setVisibility(View.GONE);
                holder.playButton.setVisibility(View.GONE);
                holder.imageView.setImageURI(item.uri);
            } else {
                holder.imageView.setVisibility(View.GONE);
                holder.videoView.setVisibility(View.VISIBLE);
                holder.playButton.setVisibility(View.VISIBLE);
                holder.videoView.setVideoURI(item.uri);
                
                holder.playButton.setOnClickListener(v -> {
                    holder.playButton.setVisibility(View.GONE);
                    holder.videoView.start();
                });
                holder.videoView.setOnCompletionListener(mp -> {
                    holder.playButton.setVisibility(View.VISIBLE);
                });
                holder.videoView.setOnClickListener(v -> {
                    if (holder.videoView.isPlaying()) {
                        holder.videoView.pause();
                        holder.playButton.setVisibility(View.VISIBLE);
                    } else {
                        holder.videoView.start();
                        holder.playButton.setVisibility(View.GONE);
                    }
                });
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ImageView imageView;
            android.widget.VideoView videoView;
            ImageView playButton;
            ViewHolder(View itemView, ImageView iv, android.widget.VideoView vv, ImageView pb) {
                super(itemView);
                this.imageView = iv;
                this.videoView = vv;
                this.playButton = pb;
            }
        }
    }

    // Adapter for Lenses switcher (Snapchat Lens layout style)
    private static class LensesAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<LensesAdapter.ViewHolder> {
        private final String[] lenses;
        private String activeLens;
        private final OnLensClickListener listener;

        interface OnLensClickListener {
            void onLensClick(String lensName);
        }

        LensesAdapter(String[] lenses, String activeLens, OnLensClickListener listener) {
            this.lenses = lenses;
            this.activeLens = activeLens;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.MarginLayoutParams(160, 160));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextSize(11);
            tv.setPadding(8, 8, 8, 8);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String name = lenses[position];
            holder.textView.setText(name);
            
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            
            if (name.equals(activeLens)) {
                holder.textView.setTextColor(android.graphics.Color.parseColor("#00F2FF"));
                gd.setColor(android.graphics.Color.parseColor("#1A00F2FF"));
                gd.setStroke(4, android.graphics.Color.parseColor("#00F2FF"));
            } else {
                holder.textView.setTextColor(android.graphics.Color.WHITE);
                gd.setColor(android.graphics.Color.parseColor("#4D000000"));
                gd.setStroke(2, android.graphics.Color.parseColor("#4DFFFFFF"));
            }
            holder.textView.setBackground(gd);

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.textView.getLayoutParams();
            if (params != null) {
                params.setMargins(16, 0, 16, 0);
                holder.textView.setLayoutParams(params);
            }

            holder.textView.setOnClickListener(v -> {
                activeLens = name;
                notifyDataSetChanged();
                if (listener != null) listener.onLensClick(name);
            });
        }

        @Override
        public int getItemCount() { return lenses.length; }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(TextView tv) { super(tv); this.textView = tv; }
        }
    }

    // Memories 3-column Grid View Adapter
    private static class MemoriesGridAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<MemoriesGridAdapter.ViewHolder> {
        private final List<MediaItem> items;
        private final android.content.Context context;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(int position);
        }

        MemoriesGridAdapter(android.content.Context context, List<MediaItem> items, OnItemClickListener listener) {
            this.context = context;
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            int width = parent.getWidth() / 3;
            iv.setLayoutParams(new ViewGroup.LayoutParams(width, width));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setPadding(4, 4, 4, 4);
            return new ViewHolder(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MediaItem item = items.get(position);
            holder.imageView.setImageURI(item.uri);
            holder.imageView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(position);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ImageView imageView;
            ViewHolder(ImageView iv) { super(iv); this.imageView = iv; }
        }
    }

    private long recordingStartTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (activeRecording == null) return;
            long millis = System.currentTimeMillis() - recordingStartTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            
            TextView timerText = findViewById(R.id.recording_timer);
            if (timerText != null) {
                timerText.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
            }
            
            View dot = findViewById(R.id.recording_dot);
            if (dot != null) {
                dot.setVisibility(dot.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
            }
            
            timerHandler.postDelayed(this, 1000);
        }
    };
}
