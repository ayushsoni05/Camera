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
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
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

    // Recording duration timer
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long recordingStartTime = 0;
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

    // Gallery Paging Logic
    private List<MediaItem> galleryItems = new ArrayList<>();
    private GalleryAdapter galleryAdapter;

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

        initializeUI();
        setupFilterCarousel();
        setupModeSelector();

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            initializeExtensionsAndStartCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void initializeUI() {
        ImageButton captureButton = findViewById(R.id.image_capture_button);
        MaterialButton flipButton = findViewById(R.id.btnFlip);
        MaterialButton flashButton = findViewById(R.id.btnFlash);
        MaterialButton gridButton = findViewById(R.id.btnGrid);
        MaterialButton timerButton = findViewById(R.id.btnTimer);
        SeekBar zoomSlider = findViewById(R.id.zoom_slider);

        if (captureButton != null) captureButton.setOnClickListener(v -> handleCapture());
        if (flipButton != null) flipButton.setOnClickListener(v -> swapCamera());
        if (flashButton != null) flashButton.setOnClickListener(v -> toggleFlash(flashButton));
        if (gridButton != null) gridButton.setOnClickListener(v -> toggleGrid());
        if (timerButton != null) timerButton.setOnClickListener(v -> cycleTimer());
        
        setupZoomAndFocus(zoomSlider);
        loadLastSavedThumbnail();

        View galleryContainer = findViewById(R.id.gallery_container);
        if (galleryContainer != null) {
            galleryContainer.setOnClickListener(v -> openGallery());
        }
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
                tv.setPadding(32, 16, 32, 16);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setTextSize(12);

                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setCornerRadius(30);

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
                    params.setMargins(12, 0, 12, 0);
                    tv.setLayoutParams(params);
                }

                tv.setOnClickListener(v -> {
                    currentFilter = filterName;
                    Toast.makeText(MainActivity.this, "Filter: " + currentFilter, Toast.LENGTH_SHORT).show();
                    applyFilterEffects(filterName);
                    notifyDataSetChanged();
                });
            }

            @Override
            public int getItemCount() { return filtersList.length; }
        });
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

    private void applyFilterToSavedImage(Uri uri, String filterName) {
        if (filterName.equals("Original") || uri == null) {
            loadLastSavedThumbnail();
            return;
        }
        cameraExecutor.execute(() -> {
            try {
                android.graphics.Bitmap srcBitmap;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    srcBitmap = android.graphics.ImageDecoder.decodeBitmap(
                            android.graphics.ImageDecoder.createSource(getContentResolver(), uri),
                            (decoder, info, source) -> decoder.setMutableRequired(true)
                    );
                } else {
                    srcBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    srcBitmap = srcBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
                }

                android.graphics.Bitmap filteredBitmap = android.graphics.Bitmap.createBitmap(
                        srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig());
                
                android.graphics.Canvas canvas = new android.graphics.Canvas(filteredBitmap);
                android.graphics.Paint paint = new android.graphics.Paint();
                android.graphics.ColorMatrix colorMatrix = getColorMatrixForFilter(filterName);
                paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(colorMatrix));
                canvas.drawBitmap(srcBitmap, 0, 0, paint);
                
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                    filteredBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out);
                }
                
                srcBitmap.recycle();
                filteredBitmap.recycle();
                
                runOnUiThread(() -> loadLastSavedThumbnail());
            } catch (Exception e) {
                Log.e(TAG, "Error applying filter to saved image", e);
                runOnUiThread(() -> loadLastSavedThumbnail());
            }
        });
    }

    private void setupModeSelector() {
        TextView modePhoto = findViewById(R.id.mode_photo);
        TextView modeVideo = findViewById(R.id.mode_video);
        TextView modePortrait = findViewById(R.id.mode_portrait);
        TextView modeNight = findViewById(R.id.mode_night);
        TextView modeHdr = findViewById(R.id.mode_hdr);

        if (modePhoto != null) modePhoto.setOnClickListener(v -> setCaptureMode(CaptureMode.PHOTO));
        if (modeVideo != null) modeVideo.setOnClickListener(v -> setCaptureMode(CaptureMode.VIDEO));
        if (modePortrait != null) modePortrait.setOnClickListener(v -> setCaptureMode(CaptureMode.PORTRAIT));
        if (modeNight != null) modeNight.setOnClickListener(v -> setCaptureMode(CaptureMode.NIGHT));
        if (modeHdr != null) modeHdr.setOnClickListener(v -> setCaptureMode(CaptureMode.HDR));
    }

    private void setCaptureMode(CaptureMode mode) {
        if (activeMode == mode) return;
        activeMode = mode;

        highlightModeText(mode);

        ImageButton shutter = findViewById(R.id.image_capture_button);
        if (shutter != null) {
            if (mode == CaptureMode.VIDEO) {
                shutter.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF2D55")));
            } else {
                shutter.setBackgroundTintList(null);
            }
        }

        startCamera();
    }

    private void highlightModeText(CaptureMode mode) {
        TextView[] views = {
            findViewById(R.id.mode_photo),
            findViewById(R.id.mode_video),
            findViewById(R.id.mode_portrait),
            findViewById(R.id.mode_night),
            findViewById(R.id.mode_hdr)
        };
        CaptureMode[] modes = {
            CaptureMode.PHOTO, CaptureMode.VIDEO, CaptureMode.PORTRAIT, CaptureMode.NIGHT, CaptureMode.HDR
        };

        for (int i = 0; i < views.length; i++) {
            TextView v = views[i];
            if (v == null) continue;
            if (modes[i] == mode) {
                v.setTextColor(android.graphics.Color.WHITE);
                v.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                v.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"));
                v.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
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

        runOnUiThread(() -> {
            View port = findViewById(R.id.mode_portrait);
            View night = findViewById(R.id.mode_night);
            View hdr = findViewById(R.id.mode_hdr);
            if (port != null) port.setVisibility(isPortraitSupported ? View.VISIBLE : View.GONE);
            if (night != null) night.setVisibility(isNightSupported ? View.VISIBLE : View.GONE);
            if (hdr != null) hdr.setVisibility(isHdrSupported ? View.VISIBLE : View.GONE);
        });
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
        if (activeMode == CaptureMode.VIDEO) {
            handleVideoRecording();
        } else {
            if (timerSeconds > 0) {
                Toast.makeText(this, "Capturing in " + timerSeconds + "s...", Toast.LENGTH_SHORT).show();
                findViewById(R.id.image_capture_button).postDelayed(this::takePhoto, timerSeconds * 1000L);
            } else {
                takePhoto();
            }
        }
    }

    private void handleVideoRecording() {
        if (videoCapture == null) return;
        
        ImageButton captureButton = findViewById(R.id.image_capture_button);
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
            return;
        }

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
                            if (captureButton != null) {
                                captureButton.setImageResource(android.R.drawable.ic_media_pause);
                            }
                            View timerCont = findViewById(R.id.recording_timer_container);
                            if (timerCont != null) timerCont.setVisibility(View.VISIBLE);
                            startRecordingTimer();
                        });
                    } else if (recordEvent instanceof androidx.camera.video.VideoRecordEvent.Finalize) {
                        androidx.camera.video.VideoRecordEvent.Finalize finalizeEvent = (androidx.camera.video.VideoRecordEvent.Finalize) recordEvent;
                        runOnUiThread(() -> {
                            if (captureButton != null) {
                                captureButton.setImageResource(0);
                            }
                            View timerCont = findViewById(R.id.recording_timer_container);
                            if (timerCont != null) timerCont.setVisibility(View.GONE);
                            stopRecordingTimer();
                        });
                        
                        if (!finalizeEvent.hasError()) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Video Recorded!", Toast.LENGTH_SHORT).show();
                                loadLastSavedThumbnail();
                            });
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

    private void startRecordingTimer() {
        recordingStartTime = System.currentTimeMillis();
        timerHandler.post(timerRunnable);
    }

    private void stopRecordingTimer() {
        timerHandler.removeCallbacks(timerRunnable);
        View dot = findViewById(R.id.recording_dot);
        if (dot != null) dot.setVisibility(View.VISIBLE);
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

        if (viewFinder != null) {
            viewFinder.setOnTouchListener((v, e) -> {
                scaleDetector.onTouchEvent(e);
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

                if (activeMode == CaptureMode.VIDEO) {
                    androidx.camera.video.Recorder recorder = new androidx.camera.video.Recorder.Builder()
                            .setQualitySelector(androidx.camera.video.QualitySelector.from(androidx.camera.video.Quality.HIGHEST))
                            .build();
                    videoCapture = androidx.camera.video.VideoCapture.withOutput(recorder);
                    camera = provider.bindToLifecycle(this, selector, preview, videoCapture);
                } else {
                    imageCapture = new ImageCapture.Builder()
                            .setFlashMode(flashMode)
                            .build();
                    camera = provider.bindToLifecycle(this, selector, preview, imageCapture);
                }

                runOnUiThread(() -> applyFilterEffects(currentFilter));

            } catch (Exception e) { Log.e(TAG, "Binding failed", e); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;
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
                Toast.makeText(getBaseContext(), "Aura Captured!", Toast.LENGTH_SHORT).show();
                Uri uri = o.getSavedUri();
                applyFilterToSavedImage(uri, currentFilter);
            }
            @Override public void onError(@NonNull ImageCaptureException e) { Log.e(TAG, "Fail", e); }
        });
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

    private void openGallery() {
        cameraExecutor.execute(() -> {
            galleryItems = getCapturedMedia();
            runOnUiThread(() -> {
                if (galleryItems.isEmpty()) {
                    Toast.makeText(this, "No media captured yet", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                View overlay = findViewById(R.id.gallery_overlay);
                if (overlay != null) {
                    overlay.setVisibility(View.VISIBLE);
                }
                
                androidx.viewpager2.widget.ViewPager2 viewPager = findViewById(R.id.gallery_viewpager);
                if (viewPager != null) {
                    galleryAdapter = new GalleryAdapter(this, galleryItems);
                    viewPager.setAdapter(galleryAdapter);
                }
                
                View backBtn = findViewById(R.id.gallery_btn_back);
                if (backBtn != null) {
                    backBtn.setOnClickListener(v -> {
                        if (overlay != null) overlay.setVisibility(View.GONE);
                        loadLastSavedThumbnail();
                    });
                }
                
                View shareBtn = findViewById(R.id.gallery_btn_share);
                if (shareBtn != null) {
                    shareBtn.setOnClickListener(v -> {
                        if (viewPager != null) {
                            int currentPos = viewPager.getCurrentItem();
                            if (currentPos >= 0 && currentPos < galleryItems.size()) {
                                MediaItem item = galleryItems.get(currentPos);
                                shareMedia(item.uri, item.isImage);
                            }
                        }
                    });
                }
                
                View deleteBtn = findViewById(R.id.gallery_btn_delete);
                if (deleteBtn != null) {
                    deleteBtn.setOnClickListener(v -> {
                        if (viewPager != null) {
                            int currentPos = viewPager.getCurrentItem();
                            if (currentPos >= 0 && currentPos < galleryItems.size()) {
                                MediaItem item = galleryItems.get(currentPos);
                                new androidx.appcompat.app.AlertDialog.Builder(this)
                                        .setTitle("Delete Media")
                                        .setMessage("Are you sure you want to delete this?")
                                        .setPositiveButton("Delete", (dialog, which) -> deleteMedia(item, currentPos))
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            }
                        }
                    });
                }
            });
        });
    }

    private void shareMedia(Uri uri, boolean isImage) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType(isImage ? "image/jpeg" : "video/mp4");
        shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(android.content.Intent.createChooser(shareIntent, "Share Media"));
    }

    private void deleteMedia(MediaItem item, int position) {
        try {
            getContentResolver().delete(item.uri, null, null);
            galleryItems.remove(position);
            if (galleryAdapter != null) {
                galleryAdapter.notifyItemRemoved(position);
            }
            
            if (galleryItems.isEmpty()) {
                View overlay = findViewById(R.id.gallery_overlay);
                if (overlay != null) overlay.setVisibility(View.GONE);
                ImageView lastImage = findViewById(R.id.last_image_preview);
                if (lastImage != null) lastImage.setImageResource(R.drawable.ic_gallery);
            } else {
                loadLastSavedThumbnail();
            }
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Delete failed", e);
            Toast.makeText(this, "Could not delete file", Toast.LENGTH_SHORT).show();
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
}
