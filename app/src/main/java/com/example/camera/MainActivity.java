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
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.GridLayout;
import android.widget.RelativeLayout;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.VideoView;
import org.json.JSONArray;
import org.json.JSONObject;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.Button;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;

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
import androidx.viewpager2.widget.ViewPager2;
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
    private LocationRepository locationRepo;
    private DeepARManager deepARManager;
    private ChatRepository chatRepo;
    
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
    
    private androidx.camera.view.PreviewView viewFinder;
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
    private final String[] lensesList = {
        "None", "Dog", "Glasses", "Crown", "Stache", "Neon Devil", "Angel Halo", "Cyberpunk HUD",
        "Bunny", "Cat", "Flower Crown", "Beard", "Ghost", "Star Eyes", "Heart Eyes", "Fire Head",
        "Rainbow Mouth", "Alien", "Pirate", "Clown", "Superhero", "Vampire", "Wizard", "Space Helmet",
        "Butterfly"
    };
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

    // Splash screen views
    private View splashScreen;
    private ImageView splashLottie;
    private TextView splashTitle;

    // Navigation layout layers
    private View cameraLayer;
    private View mapLayer;
    private View chatLayer;
    private View storiesLayer;
    private View spotlightLayer;

    // Story System State
    private StoryDatabaseHelper storyDb;
    private android.media.MediaPlayer storyMusicPlayer;
    private String selectedMusicTrack = null;
    private String storyPrivacy = "EVERYONE";
    
    // Story Playback State
    private List<StoryItem> activeStorySegments = new ArrayList<>();
    private int currentStorySegmentIndex = 0;
    private final Handler storyProgressHandler = new Handler(Looper.getMainLooper());
    private Runnable storyProgressRunnable;
    private int storyProgressTick = 0;
    private static final int STORY_PHOTO_DURATION_MS = 5000; // 5 seconds
    
    // Friends list for Profile and Search
    private final List<String> profileFriendsList = new ArrayList<>();

    // State variables for Camera Page features
    private boolean isBurstModeActive = false;
    private int selectedAspectMode = 0; // 0 = 16:9, 1 = 4:3, 2 = 1:1
    private boolean isNightBoostActive = false;
    private final List<Uri> multiSnapCapturedUris = new ArrayList<>();
    private boolean isMultiSnapActive = false;

    // Bottom Navigation buttons & icons
    private View navCreate;
    private View navScan;
    private View navClose;
    private View navBrowse;
    private View navExplore;

    private ImageView navCreateIcon;
    private ImageView navScanIcon;
    private View navCloseIcon;
    private ImageView navBrowseIcon;
    private ImageView navExploreIcon;

    private View navCreateCapsule;
    private View navScanCapsule;
    private View navCloseCapsule;
    private View navBrowseCapsule;
    private View navExploreCapsule;

    private TextView navCreateLabel;
    private TextView navScanLabel;
    private TextView navCloseLabel;
    private TextView navBrowseLabel;
    private TextView navExploreLabel;

    private View navCreateDot;
    private View navScanDot;
    private View navCloseDot;
    private View navBrowseDot;
    private View navExploreDot;

    private View captureContainer;
    private View lensesCarousel;
    private TextView filterNameIndicator;
    private WebView mapWebView;
    private VideoView spotlightVideoView;



    // Interactive Chat views
    private View chatPanel;
    private LinearLayout chatMessagesContainer;
    private EditText chatInput;
    private ScrollView chatScroll;
    private String activeChatFriend = "";
    private String activeChatFriendId = "";
    private String currentTestingUserId = "currentUser"; // swappable to "peerUser"
    private String replyToMessageId = null;
    private String replyToMessageText = null;
    private String searchQuery = null;
    private boolean isGroupChat = false;
    private String currentGroupId = null;

    // Spotlight views & data lists
    private int currentSpotlightIndex = 0;
    private int currentTab = 3; // default to camera tab
    private final List<SpotlightItem> spotlightItems = new ArrayList<>();
    private boolean isLocationTracking = false;
    private android.location.LocationListener locationListener;
    private final String[] spotlightUrls = {
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"
    };
    private ImageView spotlightBgImage;
    private View spotlightColorShape;
    private TextView spotlightCreator;
    private TextView spotlightCaption;
    private TextView spotlightMusic;
    private TextView spotlightLikes;

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

    private static class ChatFriend {
        String id;
        String name;
        String statusText;
        String time;
        int statusIcon;
        boolean hasStreaks;
        int streaksCount;
        ChatFriend(String id, String name, String statusText, String time, int statusIcon, boolean hasStreaks, int streaksCount) {
            this.id = id;
            this.name = name;
            this.statusText = statusText;
            this.time = time;
            this.statusIcon = statusIcon;
            this.hasStreaks = hasStreaks;
            this.streaksCount = streaksCount;
        }
    }

    public static class SpotlightItem {
        public String creator;
        public String caption;
        public String music;
        public String likes;
        public String videoUrl;
        public SpotlightItem(String creator, String caption, String music, String likes, String videoUrl) {
            this.creator = creator;
            this.caption = caption;
            this.music = music;
            this.likes = likes;
            this.videoUrl = videoUrl;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Remove the purple status bar line and make status bar completely transparent/edge-to-edge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }
        
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        Log.e(TAG, "viewFinder is " + (viewFinder != null ? "FOUND" : "NULL"));
        if (viewFinder != null) {
            viewFinder.setImplementationMode(androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE);
            // Disabled DeepAR conflict to ensure native CameraX takes exclusive ownership of the SurfaceView
            deepARManager = null;
            Log.e(TAG, "DeepARManager disabled for native CameraX fallback");
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

        cameraExecutor = Executors.newSingleThreadExecutor();

        initializeUI();
        setupFilterCarousel();
        setupLensCarousel();
        setupGestureDetectors();

        if (allPermissionsGranted()) {
            initializeExtensionsAndStartCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Check if first-run tutorial is needed
        showTutorialIfNeeded();

        setupSplashScreen();
        setupBottomNavigation();
        setupChatSystem();
        
        // Initialize Stories Database and Expiration thread
        storyDb = new StoryDatabaseHelper(this);
        StoryExpirationManager.getInstance(this).setListener(this::setupStoriesSystem);
        StoryExpirationManager.getInstance(this).startAutoCleanup();

        setupStoriesSystem();
        setupSpotlightSystem();

        // Explicitly switch to the camera tab (tab index 3) at boot for correct initialization and layout styling.
        switchTab(3);
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

    private void setupSplashScreen() {
        splashScreen = findViewById(R.id.splash_screen_overlay);
        splashLottie = findViewById(R.id.splash_lottie);
        splashTitle = findViewById(R.id.splash_title);
        TextView splashSlogan = findViewById(R.id.splash_slogan);

        if (splashScreen != null && splashLottie != null && splashTitle != null) {
            // Setup initial animated states
            splashTitle.setAlpha(0f);
            splashTitle.setTranslationY(40f);
            
            if (splashSlogan != null) {
                splashSlogan.setAlpha(0f);
                splashSlogan.setTranslationY(30f);
            }
            
            splashLottie.setScaleX(0.2f);
            splashLottie.setScaleY(0.2f);
            splashLottie.setAlpha(0f);

            // 1. Logo overshoot scale-in animation
            splashLottie.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .alpha(1f)
                    .setDuration(900)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                    .withEndAction(() -> {
                        splashLottie.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(250)
                                .start();
                    })
                    .start();

            // 2. Title fade-in and slide-up animation
            splashTitle.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(900)
                    .setStartDelay(350)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();

            // 3. Slogan fade-in and slide-up
            if (splashSlogan != null) {
                splashSlogan.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(900)
                        .setStartDelay(600)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            }

            // 4. Cinematic Curtain slide-up exit animation (reveals the camera)
            splashScreen.post(() -> {
                splashScreen.animate()
                        .translationY(-splashScreen.getHeight())
                        .alpha(0f)
                        .setDuration(1100)
                        .setStartDelay(2600)
                        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                        .withEndAction(() -> {
                            splashScreen.setVisibility(View.GONE);
                        })
                        .start();
                
                // Cinematic scaling out of logo during curtain slide
                splashLottie.animate()
                        .scaleX(0.7f)
                        .scaleY(0.7f)
                        .setDuration(1000)
                        .setStartDelay(2600)
                        .start();
            });
        }
    }

    private void setupBottomNavigation() {
        cameraLayer = findViewById(R.id.camera_layer);
        mapLayer = findViewById(R.id.map_layer);
        chatLayer = findViewById(R.id.chat_layer);
        storiesLayer = findViewById(R.id.stories_layer);
        spotlightLayer = findViewById(R.id.spotlight_layer);

        navCreate = findViewById(R.id.nav_create);
        navScan = findViewById(R.id.nav_scan);
        navClose = findViewById(R.id.nav_close);
        navBrowse = findViewById(R.id.nav_browse);
        navExplore = findViewById(R.id.nav_explore);

        Log.e(TAG, "setupBottomNavigation: navCreate=" + (navCreate != null) + 
                   ", navScan=" + (navScan != null) + 
                   ", navClose=" + (navClose != null) + 
                   ", navBrowse=" + (navBrowse != null) + 
                   ", navExplore=" + (navExplore != null));

        navCreateIcon = findViewById(R.id.nav_create_icon);
        navScanIcon = findViewById(R.id.nav_scan_icon);
        navCloseIcon = findViewById(R.id.nav_close_icon_preview);
        navBrowseIcon = findViewById(R.id.nav_browse_icon);
        navExploreIcon = findViewById(R.id.nav_explore_icon);

        navCreateCapsule = findViewById(R.id.nav_create_capsule);
        navScanCapsule = findViewById(R.id.nav_scan_capsule);
        navCloseCapsule = findViewById(R.id.nav_close_capsule);
        navBrowseCapsule = findViewById(R.id.nav_browse_capsule);
        navExploreCapsule = findViewById(R.id.nav_explore_capsule);

        navCreateLabel = findViewById(R.id.nav_create_label);
        navScanLabel = findViewById(R.id.nav_scan_label);
        navCloseLabel = findViewById(R.id.nav_close_label);
        navBrowseLabel = findViewById(R.id.nav_browse_label);
        navExploreLabel = findViewById(R.id.nav_explore_label);

        navCreateDot = findViewById(R.id.nav_create_dot);
        navScanDot = findViewById(R.id.nav_scan_dot);
        navCloseDot = findViewById(R.id.nav_close_dot);
        navBrowseDot = findViewById(R.id.nav_browse_dot);
        navExploreDot = findViewById(R.id.nav_explore_dot);

        captureContainer = findViewById(R.id.capture_container);
        lensesCarousel = findViewById(R.id.lenses_carousel);
        filterNameIndicator = findViewById(R.id.filter_name_indicator);

        mapWebView = findViewById(R.id.map_webview);
        if (mapWebView != null) {
            mapWebView.getSettings().setJavaScriptEnabled(true);
            mapWebView.getSettings().setDomStorageEnabled(true);
            mapWebView.getSettings().setAllowFileAccess(true);
            mapWebView.getSettings().setAllowContentAccess(true);
            mapWebView.getSettings().setAllowFileAccessFromFileURLs(true);
            mapWebView.getSettings().setAllowUniversalAccessFromFileURLs(true);
            mapWebView.setWebViewClient(new WebViewClient());
            mapWebView.loadUrl("file:///android_asset/map_index.html");
        }

        ViewPager2 spotlightViewPager = findViewById(R.id.spotlight_viewpager);
        if (spotlightViewPager != null) {
            if (spotlightItems.isEmpty()) {
                spotlightItems.add(new SpotlightItem("@alex_snaps", "Trying out the new custom filters on SnapTake! #aesthetic #camera", "🎵 Original Sound - alex_snaps", "2.4k", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"));
                spotlightItems.add(new SpotlightItem("@coding_ninja", "Procedural graphics render in real-time Android! 🚀 #dev #code", "🎵 Synth Wave Mix", "15.8k", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"));
                spotlightItems.add(new SpotlightItem("@travel_bug", "Sunset in Santorini is just magical... 🌅 #santorini #travel", "🎵 Chill Vibes Only", "8.9k", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"));
                spotlightItems.add(new SpotlightItem("@reels_maker", "Walking through the cyberpunk streets at night! 🌆 #cyberpunk #neon", "🎵 Neon Rider", "12.1k", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"));
                spotlightItems.add(new SpotlightItem("@fitness_guru", "Morning routine prep: crushing those heavy deadlifts! 🏋️ #gym #motivation", "🎵 Power Beast Beats", "31.4k", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4"));
                spotlightItems.add(new SpotlightItem("@food_diaries", "This delicious hot sizzling cheese pull is unreal! 🧀🍕 #foodie #pizza", "🎵 Tasty Eats Lounge", "25.6k", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4"));
            }
            final List<SpotlightItem> backup = new ArrayList<>(spotlightItems);
            SpotlightAdapter adapter = new SpotlightAdapter(this, spotlightItems);
            spotlightViewPager.setAdapter(adapter);
            spotlightViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    if (position >= spotlightItems.size() - 2) {
                        int prevSize = spotlightItems.size();
                        spotlightItems.addAll(backup);
                        spotlightViewPager.getAdapter().notifyItemRangeInserted(prevSize, backup.size());
                    }
                }
            });
        }
        
        if (navCreate != null) navCreate.setOnClickListener(v -> switchTab(1));
        if (navScan != null) navScan.setOnClickListener(v -> switchTab(2));
        if (navClose != null) navClose.setOnClickListener(v -> switchTab(3));
        if (navBrowse != null) navBrowse.setOnClickListener(v -> switchTab(4));
        if (navExplore != null) navExplore.setOnClickListener(v -> switchTab(5));

        // Floating shutter button: tap=photo, long-press=video
        View shutterFab = findViewById(R.id.nav_shutter_fab);
        if (shutterFab != null) {
            shutterFab.setOnClickListener(v -> {
                if (currentTab == 3) {
                    takePhoto();
                } else {
                    switchTab(3);
                }
            });
            shutterFab.setOnLongClickListener(v -> {
                if (currentTab != 3) switchTab(3);
                startVideoRecordingForSnap();
                return true;
            });
        }
    }

    private boolean isGhostModeEnabled = false;
    private double lastUserLat = 34.0522;
    private double lastUserLng = -118.2437;
    
    private static class MockFriendLocation {
        String name;
        String emoji;
        double latOffset;
        double lngOffset;
        double currentLat;
        double currentLng;
        
        MockFriendLocation(String name, String emoji, double latOffset, double lngOffset) {
            this.name = name;
            this.emoji = emoji;
            this.latOffset = latOffset;
            this.lngOffset = lngOffset;
        }
    }
    
    private static class MockHotspot {
        String name;
        double latOffset;
        double lngOffset;
        String[] storyUrls;
        
        MockHotspot(String name, double latOffset, double lngOffset, String[] storyUrls) {
            this.name = name;
            this.latOffset = latOffset;
            this.lngOffset = lngOffset;
            this.storyUrls = storyUrls;
        }
    }
    
    private final List<MockFriendLocation> mapFriends = new ArrayList<>();
    private final List<MockHotspot> mapHotspots = new ArrayList<>();
    private android.os.Handler mapUpdateHandler;
    private Runnable mapUpdateRunnable;

    private void setupLocationMap() {
        if (mapFriends.isEmpty()) {
            mapFriends.add(new MockFriendLocation("Alex", "👦", 0.003, -0.005));
            mapFriends.add(new MockFriendLocation("Jessica", "👧", -0.004, 0.006));
            mapFriends.add(new MockFriendLocation("Sam", "👨", 0.007, 0.002));
            mapFriends.add(new MockFriendLocation("Sarah", "👩", -0.006, -0.004));
        }
        if (mapHotspots.isEmpty()) {
            mapHotspots.add(new MockHotspot("Santa Monica Beach", 0.012, -0.012, new String[]{
                "https://assets.mixkit.co/videos/preview/mixkit-waves-breaking-in-the-ocean-1527-large.mp4",
                "https://assets.mixkit.co/videos/preview/mixkit-aerial-view-of-a-beach-with-waves-11880-large.mp4"
            }));
            mapHotspots.add(new MockHotspot("Snap Café & Lounge", -0.002, -0.003, new String[]{
                "https://assets.mixkit.co/videos/preview/mixkit-pouring-hot-coffee-into-a-cup-42207-large.mp4"
            }));
            mapHotspots.add(new MockHotspot("Tech Plaza Festival", 0.008, 0.008, new String[]{
                "https://assets.mixkit.co/videos/preview/mixkit-dj-playing-music-at-a-club-42588-large.mp4"
            }));
        }

        if (mapWebView != null) {
            mapWebView.getSettings().setJavaScriptEnabled(true);
            mapWebView.getSettings().setDomStorageEnabled(true);
            mapWebView.getSettings().setAllowFileAccess(true);
            mapWebView.addJavascriptInterface(new MapJavaScriptInterface(), "AndroidMap");
            mapWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    refreshMapUI();
                }
            });
            mapWebView.loadUrl("file:///android_asset/map_index.html");
        }

        androidx.appcompat.widget.SwitchCompat ghostSwitch = findViewById(R.id.map_ghost_mode_switch);
        if (ghostSwitch != null) {
            ghostSwitch.setChecked(isGhostModeEnabled);
            ghostSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                isGhostModeEnabled = isChecked;
                showToast(isChecked ? "Ghost Mode Enabled 👻" : "Ghost Mode Disabled 🟢");
                refreshMapUI();
            });
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }

        startMapLoop();
    }

    private void startMapLoop() {
        if (mapUpdateHandler != null) return;
        mapUpdateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mapUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                for (MockFriendLocation f : mapFriends) {
                    f.currentLat = lastUserLat + f.latOffset + (Math.random() - 0.5) * 0.0003;
                    f.currentLng = lastUserLng + f.lngOffset + (Math.random() - 0.5) * 0.0003;
                }
                refreshMapUI();
                mapUpdateHandler.postDelayed(this, 6000);
            }
        };
        mapUpdateHandler.post(mapUpdateRunnable);
    }

    private void refreshMapUI() {
        if (mapWebView == null) return;
        mapWebView.post(() -> {
            mapWebView.loadUrl("javascript:updateUserLocation(" + lastUserLat + ", " + lastUserLng + ", " + isGhostModeEnabled + ")");
            
            for (MockFriendLocation f : mapFriends) {
                mapWebView.loadUrl("javascript:updateFriendLocation('" + f.name + "', " + f.currentLat + ", " + f.currentLng + ", '" + f.emoji + "')");
            }
            
            for (MockHotspot h : mapHotspots) {
                double hLat = lastUserLat + h.latOffset;
                double hLng = lastUserLng + h.lngOffset;
                mapWebView.loadUrl("javascript:addHotspot('" + h.name + "', " + hLat + ", " + hLng + ")");
            }
            
            updateNearbyFriendsDrawer();
        });
    }

    private void updateNearbyFriendsDrawer() {
        LinearLayout container = findViewById(R.id.map_nearby_scroll_container);
        if (container == null) return;
        container.removeAllViews();

        for (MockFriendLocation f : mapFriends) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setBackground(ContextCompat.getDrawable(this, R.drawable.glass_rec_pill));
            item.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2E2E3E")));
            item.setPadding(24, 16, 24, 16);
            item.setGravity(android.view.Gravity.CENTER);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(260, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 16, 0);
            item.setLayoutParams(lp);

            TextView avatar = new TextView(this);
            avatar.setText(f.emoji);
            avatar.setTextSize(24);
            item.addView(avatar);

            TextView name = new TextView(this);
            name.setText(f.name);
            name.setTextColor(android.graphics.Color.WHITE);
            name.setTextSize(12);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            name.setGravity(android.view.Gravity.CENTER);
            name.setPadding(0, 8, 0, 4);
            item.addView(name);

            TextView distance = new TextView(this);
            double dist = Math.round(Math.sqrt(Math.pow(f.latOffset, 2) + Math.pow(f.lngOffset, 2)) * 111.0 * 10.0) / 10.0;
            distance.setText(dist + " km");
            distance.setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"));
            distance.setTextSize(10);
            item.addView(distance);

            item.setOnClickListener(v -> {
                if (mapWebView != null) {
                    mapWebView.loadUrl("javascript:map.panTo([" + f.currentLat + ", " + f.currentLng + "])");
                    showToast("Panning to " + f.name);
                }
            });
            container.addView(item);
        }

        for (MockHotspot h : mapHotspots) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setBackground(ContextCompat.getDrawable(this, R.drawable.glass_rec_pill));
            item.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#443C22")));
            item.setPadding(24, 16, 24, 16);
            item.setGravity(android.view.Gravity.CENTER);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(260, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 16, 0);
            item.setLayoutParams(lp);

            TextView avatar = new TextView(this);
            avatar.setText("🔥");
            avatar.setTextSize(24);
            item.addView(avatar);

            TextView name = new TextView(this);
            name.setText(h.name);
            name.setTextColor(android.graphics.Color.WHITE);
            name.setTextSize(11);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            name.setGravity(android.view.Gravity.CENTER);
            name.setPadding(0, 8, 0, 4);
            item.addView(name);

            TextView count = new TextView(this);
            count.setText("Public Story");
            count.setTextColor(android.graphics.Color.parseColor("#FFFC00"));
            count.setTextSize(9);
            item.addView(count);

            item.setOnClickListener(v -> {
                if (mapWebView != null) {
                    double hLat = lastUserLat + h.latOffset;
                    double hLng = lastUserLng + h.lngOffset;
                    mapWebView.loadUrl("javascript:map.panTo([" + hLat + ", " + hLng + "])");
                    playMapHotspotStories(h.name);
                }
            });
            container.addView(item);
        }
    }

    private void playMapHotspotStories(String placeName) {
        MockHotspot target = null;
        for (MockHotspot h : mapHotspots) {
            if (h.name.equals(placeName)) {
                target = h;
                break;
            }
        }
        if (target == null) return;

        activeStorySegments = new ArrayList<>();
        int count = 0;
        for (String url : target.storyUrls) {
            StoryItem item = new StoryItem(
                "hotspot_" + placeName + "_" + count,
                "public",
                placeName,
                url,
                true,
                System.currentTimeMillis() - 3600000 * 2,
                System.currentTimeMillis() + 3600000 * 22,
                "EVERYONE",
                null,
                "[]", "[]", "[]", "[]",
                150 + count * 12, 0, "", ""
            );
            activeStorySegments.add(item);
            count++;
        }

        runOnUiThread(() -> {
            View overlay = findViewById(R.id.story_viewer_overlay);
            if (overlay == null) return;
            overlay.setVisibility(View.VISIBLE);

            View closeBtn = findViewById(R.id.story_viewer_close);
            if (closeBtn != null) {
                closeBtn.setOnClickListener(v -> closeStoryViewer());
            }

            LinearLayout progressContainer = findViewById(R.id.story_viewer_progress_container);
            if (progressContainer != null) {
                progressContainer.removeAllViews();
                for (int i = 0; i < activeStorySegments.size(); i++) {
                    android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
                    lp.setMargins(6, 0, 6, 0);
                    pb.setLayoutParams(lp);
                    pb.setMax(100);
                    pb.setProgress(0);
                    pb.setProgressDrawable(ContextCompat.getDrawable(this, android.R.drawable.progress_horizontal));
                    pb.getProgressDrawable().setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
                    progressContainer.addView(pb);
                }
            }

            View leftTouch = findViewById(R.id.story_viewer_left_touch);
            View rightTouch = findViewById(R.id.story_viewer_right_touch);
            
            if (leftTouch != null) {
                leftTouch.setOnClickListener(v -> {
                    if (currentStorySegmentIndex > 0) {
                        playSegment(currentStorySegmentIndex - 1);
                    } else {
                        closeStoryViewer();
                    }
                });
            }
            if (rightTouch != null) {
                rightTouch.setOnClickListener(v -> {
                    if (currentStorySegmentIndex < activeStorySegments.size() - 1) {
                        playSegment(currentStorySegmentIndex + 1);
                    } else {
                        closeStoryViewer();
                    }
                });
            }

            LinearLayout reactionsBar = findViewById(R.id.story_viewer_reactions_bar);
            if (reactionsBar != null) reactionsBar.removeAllViews();
            android.widget.ImageButton replySend = findViewById(R.id.story_viewer_reply_send);
            if (replySend != null) replySend.setOnClickListener(null);
            
            playSegment(0);
        });
    }

    private final java.util.Map<Integer, List<String>> spotlightCommentsMap = new java.util.HashMap<>();

    public void openSpotlightComments(int position, TextView countText) {
        View overlay = findViewById(R.id.spotlight_comments_overlay);
        if (overlay == null) return;

        overlay.setVisibility(View.VISIBLE);

        View close = findViewById(R.id.spotlight_comments_close);
        if (close != null) close.setOnClickListener(v -> overlay.setVisibility(View.GONE));

        List<String> comments = spotlightCommentsMap.get(position);
        if (comments == null) {
            comments = new ArrayList<>();
            comments.add("This is awesome! 🔥");
            comments.add("Nice filter details. 😮");
            comments.add("Where was this shot? 📍");
            spotlightCommentsMap.put(position, comments);
        }

        final List<String> finalComments = comments;
        Runnable refreshComments = () -> {
            LinearLayout container = findViewById(R.id.spotlight_comments_container);
            if (container != null) {
                container.removeAllViews();
                for (String comment : finalComments) {
                    TextView tv = new TextView(this);
                    tv.setText("👻 User: " + comment);
                    tv.setTextColor(android.graphics.Color.WHITE);
                    tv.setTextSize(13);
                    tv.setPadding(0, 10, 0, 10);
                    container.addView(tv);
                }
            }
            if (countText != null) {
                countText.setText(finalComments.size() + "");
            }
        };

        refreshComments.run();

        android.widget.ImageButton submit = findViewById(R.id.spotlight_comment_submit_btn);
        android.widget.EditText input = findViewById(R.id.spotlight_comment_input_text);
        if (submit != null && input != null) {
            submit.setOnClickListener(v -> {
                String txt = input.getText().toString();
                if (!txt.trim().isEmpty()) {
                    finalComments.add(txt);
                    input.setText("");
                    refreshComments.run();
                }
            });
        }
    }

    public void openSpotlightShare(int position) {
        View overlay = findViewById(R.id.spotlight_share_overlay);
        if (overlay == null) return;

        overlay.setVisibility(View.VISIBLE);

        View close = findViewById(R.id.spotlight_share_close);
        if (close != null) close.setOnClickListener(v -> overlay.setVisibility(View.GONE));

        LinearLayout container = findViewById(R.id.spotlight_share_container);
        if (container != null) {
            container.removeAllViews();
            for (String friend : profileFriendsList) {
                TextView tv = new TextView(this);
                tv.setText("👦 Send to " + friend);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setTextSize(14);
                tv.setPadding(0, 16, 0, 16);
                tv.setBackground(ContextCompat.getDrawable(this, R.drawable.glass_rec_pill));
                tv.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2E2E3E")));
                
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 12);
                tv.setLayoutParams(lp);
                tv.setGravity(android.view.Gravity.CENTER_VERTICAL);
                tv.setPadding(24, 16, 24, 16);

                tv.setOnClickListener(v -> {
                    if (chatRepo != null) {
                        chatRepo.sendMessage("user", friend, "Shared a Spotlight Reel! 🎬\nCheck this out: " + spotlightItems.get(position).videoUrl, null, "text", null);
                    }
                    overlay.setVisibility(View.GONE);
                    showToast("Sent to " + friend + "! 📤");
                });
                container.addView(tv);
            }
        }
    }

    public void saveSpotlightVideo(String videoUrl) {
        showToast("Saving to Gallery...");
        cameraExecutor.execute(() -> {
            try {
                java.net.URL url = new java.net.URL(videoUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.connect();

                String name = "Spotlight_" + System.currentTimeMillis() + ".mp4";
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Pictures/SnapTake");
                }
                Uri targetUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
                if (targetUri != null) {
                    try (java.io.InputStream in = conn.getInputStream();
                         java.io.OutputStream out = getContentResolver().openOutputStream(targetUri)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                    runOnUiThread(() -> {
                        showNotification("Download Complete 🎬", "Spotlight Reel saved to Snaptake Memories!", "💾");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to download spotlight video", e);
                runOnUiThread(() -> showToast("Download failed."));
            }
        });
    }

    private class MapJavaScriptInterface {
        @android.webkit.JavascriptInterface
        public void onUserMarkerClicked() {
            runOnUiThread(() -> {
                showNotification("Snap Map 📍", "This is You! Customize your profile in Settings.", "👻");
            });
        }

        @android.webkit.JavascriptInterface
        public void onFriendClicked(String name) {
            runOnUiThread(() -> {
                showNotification("Snap Map 📍", "Tapped " + name + "! Opening Chat...", "💬");
                activeChatFriend = name;
                switchTab(2);
            });
        }

        @android.webkit.JavascriptInterface
        public void onHotspotClicked(String placeName) {
            runOnUiThread(() -> {
                showNotification("Snap Map 🔥", "Playing Public Stories from: " + placeName, "🎬");
                playMapHotspotStories(placeName);
            });
        }
    }

    private void startLocationTracking() {
        if (isLocationTracking) return;
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        lastUserLat = location.getLatitude();
                        lastUserLng = location.getLongitude();
                        
                        refreshMapUI();
                        
                        if (locationRepo == null) locationRepo = new LocationRepository();
                        if (!isGhostModeEnabled) {
                            locationRepo.updateLocation("currentUser", location);
                        }
                    }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(@NonNull String provider) {}
                    @Override public void onProviderDisabled(@NonNull String provider) {}
                };

                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, locationListener);
                }
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
                }
                isLocationTracking = true;
                
                Location lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                Location best = lastGps;
                if (lastNet != null && (best == null || lastNet.getAccuracy() < best.getAccuracy())) {
                    best = lastNet;
                }
                if (best != null) {
                    lastUserLat = best.getLatitude();
                    lastUserLng = best.getLongitude();
                    refreshMapUI();
                    
                    if (locationRepo == null) locationRepo = new LocationRepository();
                    if (!isGhostModeEnabled) {
                        locationRepo.updateLocation("currentUser", best);
                    }
                }
                
                if (locationRepo != null) {
                    locationRepo.listenForLocations(locations -> {
                        for (java.util.Map.Entry<String, LocationRepository.UserLocation> entry : locations.entrySet()) {
                            String userId = entry.getKey();
                            LocationRepository.UserLocation loc = entry.getValue();
                            if (!userId.equals("currentUser") && mapWebView != null) {
                                mapWebView.post(() -> mapWebView.loadUrl("javascript:updateFriendLocation('" + userId + "', " + loc.latitude + ", " + loc.longitude + ", '👦')"));
                            }
                        }
                    });
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "GPS tracking failed", e);
        }
    }

    private void stopLocationTracking() {
        if (!isLocationTracking) return;
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm != null && locationListener != null) {
                lm.removeUpdates(locationListener);
            }
            isLocationTracking = false;
        } catch (Exception e) {
            Log.e(TAG, "GPS stop failed", e);
        }
    }

    private void updateMapCoords(double lat, double lng) {
        lastUserLat = lat;
        lastUserLng = lng;
        refreshMapUI();
    }

    private void startSpotlightVideo(int index) {
        // Spotlight video playback is handled dynamically inside SpotlightAdapter
    }

    private void switchTab(int tabIndex) {
        Log.e(TAG, "switchTab: tabIndex=" + tabIndex);
        currentTab = tabIndex;
        View bottomNav = findViewById(R.id.bottom_nav_bar);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.VISIBLE);
        }
        // 1. Reset all capsules to inactive state (transparent background, 70% white tint, visible label)
        int inactiveColor = android.graphics.Color.parseColor("#B3FFFFFF"); // 70% White
        int activeColor = android.graphics.Color.parseColor("#FFFC00"); // Snapchat Yellow

        if (navCreateCapsule != null) navCreateCapsule.setBackgroundResource(0);
        if (navScanCapsule != null) navScanCapsule.setBackgroundResource(0);
        if (navCloseCapsule != null) navCloseCapsule.setBackgroundResource(0);
        if (navBrowseCapsule != null) navBrowseCapsule.setBackgroundResource(0);
        if (navExploreCapsule != null) navExploreCapsule.setBackgroundResource(0);

        if (navCreateLabel != null) {
            navCreateLabel.setVisibility(View.VISIBLE);
            navCreateLabel.setTextColor(inactiveColor);
        }
        if (navScanLabel != null) {
            navScanLabel.setVisibility(View.VISIBLE);
            navScanLabel.setTextColor(inactiveColor);
        }
        if (navCloseLabel != null) {
            navCloseLabel.setVisibility(View.VISIBLE);
            navCloseLabel.setTextColor(inactiveColor);
        }
        if (navBrowseLabel != null) {
            navBrowseLabel.setVisibility(View.VISIBLE);
            navBrowseLabel.setTextColor(inactiveColor);
        }
        if (navExploreLabel != null) {
            navExploreLabel.setVisibility(View.VISIBLE);
            navExploreLabel.setTextColor(inactiveColor);
        }

        if (navCreateIcon != null) navCreateIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
        if (navScanIcon != null) navScanIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
        if (navCloseIcon != null) {
            if (navCloseIcon instanceof ImageView) {
                ((ImageView) navCloseIcon).setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
            } else {
                navCloseIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
            }
        }
        if (navBrowseIcon != null) navBrowseIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
        if (navExploreIcon != null) navExploreIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor));

        // 2. Set active tab to premium highlighted state (yellow capsule highlight, yellow text, yellow icon)
        switch (tabIndex) {
            case 1:
                if (navCreateCapsule != null) navCreateCapsule.setBackgroundResource(R.drawable.snap_active_tab_highlight);
                if (navCreateLabel != null) navCreateLabel.setTextColor(activeColor);
                if (navCreateIcon != null) navCreateIcon.setImageTintList(android.content.res.ColorStateList.valueOf(activeColor));
                break;
            case 2:
                if (navScanCapsule != null) navScanCapsule.setBackgroundResource(R.drawable.snap_active_tab_highlight);
                if (navScanLabel != null) navScanLabel.setTextColor(activeColor);
                if (navScanIcon != null) navScanIcon.setImageTintList(android.content.res.ColorStateList.valueOf(activeColor));
                break;
            case 3:
                if (navCloseCapsule != null) navCloseCapsule.setBackgroundResource(R.drawable.snap_active_tab_highlight);
                if (navCloseLabel != null) navCloseLabel.setTextColor(activeColor);
                if (navCloseIcon != null) {
                    if (navCloseIcon instanceof ImageView) {
                        ((ImageView) navCloseIcon).setImageTintList(android.content.res.ColorStateList.valueOf(activeColor));
                    } else {
                        navCloseIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeColor));
                    }
                }
                break;
            case 4:
                if (navBrowseCapsule != null) navBrowseCapsule.setBackgroundResource(R.drawable.snap_active_tab_highlight);
                if (navBrowseLabel != null) navBrowseLabel.setTextColor(activeColor);
                if (navBrowseIcon != null) navBrowseIcon.setImageTintList(android.content.res.ColorStateList.valueOf(activeColor));
                break;
            case 5:
                if (navExploreCapsule != null) navExploreCapsule.setBackgroundResource(R.drawable.snap_active_tab_highlight);
                if (navExploreLabel != null) navExploreLabel.setTextColor(activeColor);
                if (navExploreIcon != null) navExploreIcon.setImageTintList(android.content.res.ColorStateList.valueOf(activeColor));
                break;
        }

        // Show/hide content layers
        if (cameraLayer != null) cameraLayer.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);
        if (mapLayer != null) mapLayer.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        if (chatLayer != null) chatLayer.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
        if (storiesLayer != null) storiesLayer.setVisibility(tabIndex == 4 ? View.VISIBLE : View.GONE);
        if (spotlightLayer != null) spotlightLayer.setVisibility(tabIndex == 5 ? View.VISIBLE : View.GONE);

        // Shutter button & lenses carousel: show only on camera tab
        updateShutterVisibility();
        if (lensesCarousel != null) {
            lensesCarousel.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);
        }

        // Feature activation per tab
        if (tabIndex == 1) {
            setupLocationMap();
        } else {
            stopLocationTracking();
        }

        if (tabIndex == 4) {
            setupStoriesSystem();
        }

        if (tabIndex == 5) {
            // Handled automatically by ViewPager2 page attachment in adapter
        } else {
            // Handled automatically by ViewPager2 page detachment in adapter
        }

        if (tabIndex == 3) {
            startCamera();
        } else {
            try {
                ProcessCameraProvider.getInstance(this).get().unbindAll();
            } catch (Exception e) {
                Log.e(TAG, "Unbind failed", e);
            }
        }
    }

    private void updateShutterVisibility() {
        if (captureContainer != null) {
            // Show standard white shutter button only on Camera tab (tab 3) AND when "None" (index 0) is active
            captureContainer.setVisibility((currentTab == 3 && currentLensIndex == 0) ? View.VISIBLE : View.GONE);
        }
    }

    private void setupChatSystem() {
        chatPanel = findViewById(R.id.chat_panel);
        chatMessagesContainer = findViewById(R.id.chat_messages_container);
        chatInput = findViewById(R.id.chat_input);
        chatScroll = findViewById(R.id.chat_scroll);

        View instantCameraBtn = findViewById(R.id.chat_btn_instant_camera);
        if (instantCameraBtn != null) {
            instantCameraBtn.setOnClickListener(v -> switchTab(3));
        }

        if (chatInput != null) {
            chatInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    boolean hasText = s.toString().trim().length() > 0;
                    View sendBtn = findViewById(R.id.chat_send_btn);
                    View micBtn = findViewById(R.id.chat_mic_btn);
                    View stickerBtn = findViewById(R.id.chat_sticker_btn);
                    View attachBtn = findViewById(R.id.chat_attach_btn);
                    
                    if (sendBtn != null) sendBtn.setVisibility(hasText ? View.VISIBLE : View.GONE);
                    if (micBtn != null) micBtn.setVisibility(hasText ? View.GONE : View.VISIBLE);
                    if (stickerBtn != null) stickerBtn.setVisibility(hasText ? View.GONE : View.VISIBLE);
                    if (attachBtn != null) attachBtn.setVisibility(hasText ? View.GONE : View.VISIBLE);
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        View backBtn = findViewById(R.id.chat_panel_back);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> {
                if (chatPanel != null) {
                    chatPanel.animate()
                            .translationX(chatPanel.getWidth())
                            .setDuration(300)
                            .withEndAction(() -> {
                                chatPanel.setVisibility(View.GONE);
                                View bottomNav = findViewById(R.id.bottom_nav_bar);
                                if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE);
                            })
                            .start();
                }
            });
        }

        View sendBtn = findViewById(R.id.chat_send_btn);
        if (sendBtn != null) {
            sendBtn.setOnClickListener(v -> sendMessage());
        }

        // Swappable identity for testing both sides
        com.google.android.material.button.MaterialButton switchIdentityBtn = findViewById(R.id.chat_btn_switch_identity);
        if (switchIdentityBtn != null) {
            switchIdentityBtn.setText("Acting: You");
            switchIdentityBtn.setOnClickListener(v -> {
                if ("currentUser".equals(currentTestingUserId)) {
                    currentTestingUserId = activeChatFriendId;
                    switchIdentityBtn.setText("Acting: " + activeChatFriend);
                    Toast.makeText(this, "Switched identity: Acting as " + activeChatFriend, Toast.LENGTH_SHORT).show();
                } else {
                    currentTestingUserId = "currentUser";
                    switchIdentityBtn.setText("Acting: You");
                    Toast.makeText(this, "Switched identity: Acting as You", Toast.LENGTH_SHORT).show();
                }
                if (activeChatFriendId != null && !activeChatFriendId.isEmpty()) {
                    openChatWithFriend(activeChatFriendId, activeChatFriend);
                }
            });
        }

        // Setup attachment picker, stickers picker, voice note recorder
        setupAttachmentHandling();
        setupVoiceRecording();
        setupGroupChatCreation();

        // Chat list population
        LinearLayout listContainer = findViewById(R.id.chat_list_container);
        if (listContainer != null) {
            listContainer.removeAllViews();
            List<ChatFriend> friends = new ArrayList<>();
            friends.add(new ChatFriend("my_ai", "My AI 👻", "Chat with My AI", "Just now", android.R.drawable.presence_online, false, 0));
            friends.add(new ChatFriend("alex", "Alex", "Sent", "2m ago", android.R.drawable.ic_menu_send, true, 145));
            friends.add(new ChatFriend("jessica", "Jessica", "New Chat", "10m ago", android.R.drawable.sym_action_email, false, 0));
            friends.add(new ChatFriend("sam", "Sam", "Opened", "1h ago", android.R.drawable.presence_away, true, 38));
            friends.add(new ChatFriend("sarah", "Sarah", "Received", "4h ago", android.R.drawable.presence_online, true, 73));
            friends.add(new ChatFriend("david", "David", "New Snap", "1d ago", android.R.drawable.sym_action_email, false, 0));

            for (ChatFriend friend : friends) {
                View row = getLayoutInflater().inflate(R.layout.item_chat_list_row, listContainer, false);
                
                // Bind Avatar
                View avatarContainer = row.findViewById(R.id.chat_row_avatar_container);
                TextView avatarText = row.findViewById(R.id.chat_row_avatar_text);
                if (avatarContainer != null && avatarText != null) {
                    String emoji = "👻";
                    String color = "#FFFC00";
                    if (friend.id.equals("my_ai")) {
                        emoji = "👻";
                        color = "#9B51E0";
                    } else if (friend.id.equals("alex")) {
                        emoji = "👦";
                        color = "#FF9500";
                    } else if (friend.id.equals("jessica")) {
                        emoji = "👧";
                        color = "#FF2D55";
                    } else if (friend.id.equals("sam")) {
                        emoji = "👨";
                        color = "#34C759";
                    } else if (friend.id.equals("sarah")) {
                        emoji = "👩";
                        color = "#007AFF";
                    } else if (friend.id.equals("david")) {
                        emoji = "👱";
                        color = "#AF52DE";
                    }
                    avatarText.setText(emoji);
                    avatarContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color)));
                }

                // Bind Name
                TextView nameView = row.findViewById(R.id.chat_row_name);
                if (nameView != null) {
                    nameView.setText(friend.name);
                }

                // Bind Status Subtext
                TextView statusView = row.findViewById(R.id.chat_row_status_text);
                if (statusView != null) {
                    statusView.setText(friend.statusText + " • " + friend.time);
                }

                // Bind Snapchat unread/read receipt icons programmatically
                ImageView statusIcon = row.findViewById(R.id.chat_row_status_icon);
                if (statusIcon != null) {
                    String statusText = friend.statusText.toLowerCase();
                    if (statusText.contains("sent") || statusText.contains("opened")) {
                        statusIcon.setImageResource(android.R.drawable.ic_menu_send);
                        statusIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#8E8E93")));
                    } else {
                        // Create a rounded solid square
                        android.graphics.drawable.GradientDrawable sq = new android.graphics.drawable.GradientDrawable();
                        sq.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                        sq.setCornerRadius(4 * getResources().getDisplayMetrics().density);
                        
                        String colorHex = "#00B6FF"; // blue for chats
                        if (statusText.contains("snap") || statusText.contains("received")) {
                            colorHex = "#FF2D55"; // red for snaps
                        } else if (statusText.contains("video")) {
                            colorHex = "#AF52DE"; // purple for video snaps
                        }
                        sq.setColor(android.graphics.Color.parseColor(colorHex));
                        statusIcon.setImageDrawable(sq);
                    }
                }

                // Bind Streaks
                View streakContainer = row.findViewById(R.id.chat_row_streak_container);
                TextView streakText = row.findViewById(R.id.chat_row_streak_text);
                if (streakContainer != null && streakText != null) {
                    if (friend.hasStreaks) {
                        streakText.setText("🔥 " + friend.streaksCount);
                        streakContainer.setVisibility(View.VISIBLE);
                    } else {
                        streakContainer.setVisibility(View.GONE);
                    }
                }

                // Quick camera trigger
                View cameraBtn = row.findViewById(R.id.chat_row_btn_camera);
                if (cameraBtn != null) {
                    cameraBtn.setOnClickListener(v -> switchTab(3));
                }

                // Separator View
                View sep = new View(this);
                sep.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                sep.setBackgroundColor(android.graphics.Color.parseColor("#15FFFFFF"));

                row.setOnClickListener(v -> openChatWithFriend(friend.id, friend.name));

                listContainer.addView(row);
                listContainer.addView(sep);
            }
        }

        // Conversation search UI wiring
        EditText searchInput = findViewById(R.id.chat_search_input);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s.toString().trim();
                    populateMessagesList(activeThreadMessages);
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        ImageButton searchClose = findViewById(R.id.chat_search_close);
        if (searchClose != null) {
            searchClose.setOnClickListener(v -> {
                searchQuery = null;
                if (searchInput != null) searchInput.setText("");
                View searchContainer = findViewById(R.id.chat_search_container);
                if (searchContainer != null) searchContainer.setVisibility(View.GONE);
                populateMessagesList(activeThreadMessages);
            });
        }



        // Pinned Banner close button
        ImageButton pinnedClose = findViewById(R.id.chat_pinned_close);
        View pinnedBanner = findViewById(R.id.chat_pinned_banner);
        if (pinnedClose != null && pinnedBanner != null) {
            pinnedClose.setOnClickListener(v -> pinnedBanner.setVisibility(View.GONE));
        }

        // Reply preview close button
        ImageView replyClose = findViewById(R.id.chat_reply_preview_close);
        if (replyClose != null) {
            replyClose.setOnClickListener(v -> clearReplyPreview());
        }
    }

    private String getMyAIResponse(String userMsg) {
        userMsg = userMsg.toLowerCase().trim();
        if (userMsg.contains("hello") || userMsg.contains("hi") || userMsg.contains("hey")) {
            return "Hey there! I'm SnapTake My AI 👻. How can I help you snap today?";
        } else if (userMsg.contains("name")) {
            return "My name is SnapTake My AI, your custom chatbot companion built into the app!";
        } else if (userMsg.contains("filter") || userMsg.contains("lens")) {
            return "Just swipe left or right directly on the viewfinder to cycle color filters like Vintage and Retro! Tap on faces to use Neon Devil or Angel lenses!";
        } else if (userMsg.contains("joke")) {
            return "Why did the camera stop working? It just couldn't focus! 📸";
        } else if (userMsg.contains("reels") || userMsg.contains("spotlight")) {
            return "Click the Spotlight tab at the bottom right. You can watch short video clips and swipe up/down!";
        } else if (userMsg.contains("location") || userMsg.contains("map")) {
            return "Tap the Map tab (far left) to load a dark street map centered right on your GPS location! 🗺️";
        } else {
            return "Cool! Let me know if you want to know about SnapTake filters, GPS maps, or custom AR face lenses! 👻";
        }
    }

    private List<ChatRepository.ChatMessage> activeThreadMessages = new ArrayList<>();

    private void openChatWithFriend(String friendName) {
        openChatWithFriend(friendName.toLowerCase().replace(" ", "_"), friendName);
    }

    private void openChatWithFriend(String friendId, String friendName) {
        activeChatFriend = friendName;
        activeChatFriendId = friendId;
        TextView title = findViewById(R.id.chat_panel_title);
        if (title != null) title.setText(friendName);

        // Configure Snapchat-style Bitmoji avatar dynamically
        View avatarContainer = findViewById(R.id.chat_avatar_container);
        TextView avatarText = findViewById(R.id.chat_avatar_text);
        if (avatarContainer != null && avatarText != null) {
            String emoji = "👻";
            String color = "#FFFC00"; // default Snapchat yellow
            if (friendId.equals("my_ai")) {
                emoji = "👻";
                color = "#9B51E0"; // purple for My AI
            } else if (friendId.equals("alex")) {
                emoji = "👦";
                color = "#FF9500";
            } else if (friendId.equals("jessica")) {
                emoji = "👧";
                color = "#FF2D55";
            } else if (friendId.equals("sam")) {
                emoji = "👨";
                color = "#34C759";
            } else if (friendId.equals("sarah")) {
                emoji = "👩";
                color = "#007AFF";
            } else if (friendId.equals("david")) {
                emoji = "👱";
                color = "#AF52DE";
            } else {
                emoji = "👥"; // group icon
                color = "#00B6FF";
            }
            avatarText.setText(emoji);
            avatarContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color)));
            avatarContainer.setOnClickListener(v -> {
                View searchContainer = findViewById(R.id.chat_search_container);
                if (searchContainer != null) {
                    searchContainer.setVisibility(searchContainer.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                }
            });
        }

        // Check if group chat session
        isGroupChat = friendId.contains("group") || friendName.contains(",") || friendName.contains("Group");

        if (chatRepo == null) chatRepo = new ChatRepository();
        
        // Typing indicator sync
        TextView typingIndicator = findViewById(R.id.chat_typing_indicator);
        if (typingIndicator != null) {
            chatRepo.listenToTypingStatus(friendId, new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    String typingTo = snapshot.getValue(String.class);
                    runOnUiThread(() -> {
                        if (currentTestingUserId.equals(typingTo)) {
                            typingIndicator.setText(friendName + " is typing...");
                            typingIndicator.setVisibility(View.VISIBLE);
                        } else {
                            typingIndicator.setVisibility(View.GONE);
                        }
                    });
                }
                @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
            });
        }

        // Live status watch dot
        if (chatRepo != null && !isGroupChat) {
            chatRepo.listenToOnlineStatus(friendId, new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    Boolean isOnline = snapshot.child("online").getValue(Boolean.class);
                    runOnUiThread(() -> {
                        if (title != null) {
                            title.setTextColor(isOnline != null && isOnline ? 
                                    android.graphics.Color.GREEN : android.graphics.Color.WHITE);
                        }
                    });
                }
                @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
            });
        }

        if (chatMessagesContainer != null) {
            chatMessagesContainer.removeAllViews();
            
            if (isGroupChat) {
                chatRepo.listenForGroupMessages(friendId, messages -> runOnUiThread(() -> populateMessagesList(messages)));
            } else {
                chatRepo.listenForMessages("currentUser", friendId, messages -> runOnUiThread(() -> populateMessagesList(messages)));
            }
        }

        // Wire input typing triggers
        if (chatInput != null) {
            chatInput.addTextChangedListener(new android.text.TextWatcher() {
                private final Handler typingHandler = new Handler(Looper.getMainLooper());
                private Runnable typingRunnable;

                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (typingRunnable != null) {
                        typingHandler.removeCallbacks(typingRunnable);
                    }
                    String targetUser = currentTestingUserId.equals("currentUser") ? activeChatFriendId : "currentUser";
                    chatRepo.setTypingStatus(currentTestingUserId, targetUser);

                    typingRunnable = () -> chatRepo.setTypingStatus(currentTestingUserId, "none");
                    typingHandler.postDelayed(typingRunnable, 2000);
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (chatPanel != null) {
            chatPanel.setVisibility(View.VISIBLE);
            View bottomNav = findViewById(R.id.bottom_nav_bar);
            if (bottomNav != null) bottomNav.setVisibility(View.GONE);

            chatPanel.setTranslationX(chatPanel.getWidth());
            chatPanel.animate()
                    .translationX(0)
                    .setDuration(300)
                    .start();
        }
    }

    private void populateMessagesList(List<ChatRepository.ChatMessage> messages) {
        activeThreadMessages = messages;
        if (chatMessagesContainer == null) return;
        chatMessagesContainer.removeAllViews();

        for (ChatRepository.ChatMessage msg : messages) {
            // Apply conversation keyword search filter
            if (searchQuery != null && !searchQuery.isEmpty()) {
                if (msg.message == null || !msg.message.toLowerCase().contains(searchQuery.toLowerCase())) {
                    continue;
                }
            }

            boolean isMe = msg.sender.equals(currentTestingUserId);
            View bubble = getLayoutInflater().inflate(
                    isMe ? R.layout.item_chat_bubble_sent : R.layout.item_chat_bubble_received, 
                    chatMessagesContainer, 
                    false
            );

            // Bind text
            TextView textVal = bubble.findViewById(R.id.chat_text);
            if (textVal != null) {
                if (msg.message != null && !msg.message.isEmpty()) {
                    textVal.setText(msg.message);
                    textVal.setVisibility(View.VISIBLE);
                } else {
                    textVal.setVisibility(View.GONE);
                }
            }

            // Bind sender label (Snapchat-style name tag)
            TextView senderLabel = bubble.findViewById(R.id.chat_sender);
            if (senderLabel != null) {
                senderLabel.setText(isMe ? "You" : getFriendlySenderName(msg.sender));
                senderLabel.setTextColor(isMe ? android.graphics.Color.parseColor("#00B6FF") : android.graphics.Color.parseColor("#FF00E5"));
                senderLabel.setVisibility(View.VISIBLE);
            }

            // Snapchat-style Saved Message Highlight
            View savedIndicator = bubble.findViewById(R.id.chat_saved_indicator);
            View bubbleMain = bubble.findViewById(R.id.chat_bubble_main);
            if (savedIndicator != null) {
                savedIndicator.setVisibility(msg.isPinned ? View.VISIBLE : View.GONE);
                savedIndicator.setBackgroundColor(isMe ? android.graphics.Color.parseColor("#00B6FF") : android.graphics.Color.parseColor("#FF00E5"));
            }
            if (bubbleMain != null) {
                if (msg.isPinned) {
                    bubbleMain.setBackgroundResource(R.drawable.glass_rec_pill);
                    bubbleMain.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2A2A38")));
                    bubbleMain.setPadding(20, 12, 20, 12);
                } else {
                    bubbleMain.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    bubbleMain.setPadding(0, 0, 0, 0);
                }
            }

            // Bind time
            TextView timeVal = bubble.findViewById(R.id.chat_time);
            if (timeVal != null) {
                java.util.Date date = new java.util.Date(msg.timestamp);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
                timeVal.setText(sdf.format(date));
            }

            // Bind ticks/status
            ImageView ticks = bubble.findViewById(R.id.chat_status_ticks);
            if (ticks != null) {
                if ("read".equals(msg.status)) {
                    ticks.setImageResource(android.R.drawable.checkbox_on_background);
                    ticks.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00F2FF")));
                } else if ("delivered".equals(msg.status)) {
                    ticks.setImageResource(android.R.drawable.checkbox_on_background);
                    ticks.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#80FFFFFF")));
                } else {
                    ticks.setImageResource(android.R.drawable.checkbox_off_background);
                    ticks.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#80FFFFFF")));
                }
            }

            // Bind images
            ImageView img = bubble.findViewById(R.id.chat_image);
            if (img != null) {
                if (("photo".equals(msg.mediaType) || "sticker".equals(msg.mediaType) || "gif".equals(msg.mediaType)) && msg.mediaUrl != null) {
                    img.setVisibility(View.VISIBLE);
                    if (msg.mediaUrl.startsWith("content://") || msg.mediaUrl.startsWith("file://")) {
                        img.setImageURI(Uri.parse(msg.mediaUrl));
                    } else {
                        img.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                    img.setOnClickListener(v -> showFullscreenImage(msg.mediaUrl));
                } else {
                    img.setVisibility(View.GONE);
                }
            }

            // Bind videos
            View videoContainer = bubble.findViewById(R.id.chat_video_container);
            ImageView videoThumb = bubble.findViewById(R.id.chat_video_thumbnail);
            if (videoContainer != null && videoThumb != null) {
                if ("video".equals(msg.mediaType) && msg.mediaUrl != null) {
                    videoContainer.setVisibility(View.VISIBLE);
                    videoThumb.setImageResource(android.R.drawable.ic_menu_slideshow);
                    videoContainer.setOnClickListener(v -> showFullscreenVideo(msg.mediaUrl));
                } else {
                    videoContainer.setVisibility(View.GONE);
                }
            }

            // Bind Voice Notes player
            View voiceContainer = bubble.findViewById(R.id.chat_voice_container);
            ImageButton voicePlay = bubble.findViewById(R.id.chat_voice_play_btn);
            ProgressBar voiceProgress = bubble.findViewById(R.id.chat_voice_progress);
            TextView voiceDuration = bubble.findViewById(R.id.chat_voice_duration);
            if (voiceContainer != null && voicePlay != null && voiceProgress != null && voiceDuration != null) {
                if ("voice".equals(msg.mediaType) && msg.mediaUrl != null) {
                    voiceContainer.setVisibility(View.VISIBLE);
                    voiceDuration.setText(msg.mediaDuration > 0 ? (msg.mediaDuration / 60) + ":" + String.format("%02d", msg.mediaDuration % 60) : "0:05");
                    voiceProgress.setProgress(0);
                    voicePlay.setOnClickListener(v -> playVoiceNote(msg.mediaUrl, voicePlay, voiceProgress, voiceDuration));
                } else {
                    voiceContainer.setVisibility(View.GONE);
                }
            }

            // Bind reply header
            View replyHeader = bubble.findViewById(R.id.chat_reply_container);
            TextView replySender = bubble.findViewById(R.id.chat_reply_sender);
            TextView replyText = bubble.findViewById(R.id.chat_reply_text);
            if (replyHeader != null && replySender != null && replyText != null) {
                if (msg.replyToId != null && !msg.replyToId.isEmpty()) {
                    replyHeader.setVisibility(View.VISIBLE);
                    String parentText = "Message deleted";
                    String parentSender = "Unknown";
                    for (ChatRepository.ChatMessage pm : messages) {
                        if (pm.id.equals(msg.replyToId)) {
                            parentText = pm.message != null && !pm.message.isEmpty() ? pm.message : "[" + pm.mediaType + " Attachment]";
                            parentSender = getFriendlySenderName(pm.sender);
                            break;
                        }
                    }
                    replySender.setText(parentSender);
                    replyText.setText(parentText);
                } else {
                    replyHeader.setVisibility(View.GONE);
                }
            }

            // Bind reactions
            LinearLayout reactionsLayout = bubble.findViewById(R.id.chat_reactions_layout);
            if (reactionsLayout != null) {
                if (msg.reactions != null && !msg.reactions.isEmpty()) {
                    reactionsLayout.setVisibility(View.VISIBLE);
                    reactionsLayout.removeAllViews();
                    for (java.util.Map.Entry<String, String> entry : msg.reactions.entrySet()) {
                        TextView emojiText = new TextView(this);
                        emojiText.setText(entry.getValue());
                        emojiText.setTextSize(12);
                        emojiText.setPadding(4, 0, 4, 0);
                        reactionsLayout.addView(emojiText);
                    }
                } else {
                    reactionsLayout.setVisibility(View.GONE);
                }
            }

            // Long Press Options Dialog
            bubble.setOnLongClickListener(v -> {
                showChatOptionsDialog(msg);
                return true;
            });

            // Mark delivered/read receipts:
            if (!isMe && !"read".equals(msg.status)) {
                chatRepo.updateMessageStatus(msg.id, "read");
            }

            // Display Pin references dynamically
            View pinnedBanner = findViewById(R.id.chat_pinned_banner);
            TextView pinnedText = findViewById(R.id.chat_pinned_text);
            if (pinnedBanner != null && pinnedText != null) {
                ChatRepository.ChatMessage pinnedMsg = null;
                for (ChatRepository.ChatMessage m : messages) {
                    if (m.isPinned) {
                        pinnedMsg = m;
                        break;
                    }
                }
                if (pinnedMsg != null) {
                    pinnedText.setText(pinnedMsg.message != null && !pinnedMsg.message.isEmpty() ? pinnedMsg.message : "[" + pinnedMsg.mediaType + "]");
                    pinnedBanner.setVisibility(View.VISIBLE);
                } else {
                    pinnedBanner.setVisibility(View.GONE);
                }
            }

            chatMessagesContainer.addView(bubble);
        }

        // Auto Scroll to bottom
        if (chatScroll != null) {
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private String getFriendlySenderName(String senderId) {
        if ("currentUser".equals(senderId)) return "You";
        if ("peerUser".equals(senderId) || "alex".equals(senderId) || "alexUser".equals(senderId)) return "Alex";
        if ("jessica".equals(senderId) || "jessicaUser".equals(senderId)) return "Jessica";
        if ("sam".equals(senderId) || "samUser".equals(senderId)) return "Sam";
        if ("sarah".equals(senderId) || "sarahUser".equals(senderId)) return "Sarah";
        if ("david".equals(senderId) || "davidUser".equals(senderId)) return "David";
        if ("my_ai".equals(senderId)) return "My AI 👻";
        return senderId;
    }

    private void addChatMessageToUI(String sender, String message, boolean isMe) {
        // Obsolete manually generated views: layout is handled dynamically by populateMessagesList
    }

    private void sendMessage() {
        if (chatInput == null || chatInput.getText().toString().trim().isEmpty()) return;
        String text = chatInput.getText().toString().trim();
        
        if (chatRepo == null) chatRepo = new ChatRepository();
        String senderId = currentTestingUserId;
        String receiverId = isGroupChat ? activeChatFriendId : (currentTestingUserId.equals("currentUser") ? activeChatFriendId : "currentUser");
        chatRepo.sendMessage(senderId, receiverId, text, null, "text", replyToMessageId);
        
        chatInput.setText("");
        clearReplyPreview();

        // Simulate reply if talking to My AI
        if (activeChatFriendId != null && activeChatFriendId.equals("my_ai")) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                String reply = getMyAIResponse(text);
                chatRepo.sendMessage("my_ai", "currentUser", reply, null, "text", null);
            }, 1500);
        }
    }

    // Voice Notes seekable Media playback
    private MediaPlayer voicePlayer;
    private Handler voiceHandler = new Handler(Looper.getMainLooper());
    private Runnable voiceUpdater;

    private void playVoiceNote(String filePath, ImageButton playBtn, ProgressBar progress, TextView durationText) {
        if (voicePlayer != null) {
            voicePlayer.release();
            voicePlayer = null;
        }
        if (voiceUpdater != null) {
            voiceHandler.removeCallbacks(voiceUpdater);
        }

        try {
            voicePlayer = new MediaPlayer();
            voicePlayer.setDataSource(filePath);
            voicePlayer.prepare();
            voicePlayer.start();
            playBtn.setImageResource(android.R.drawable.ic_media_pause);

            progress.setMax(voicePlayer.getDuration());
            voiceUpdater = new Runnable() {
                @Override
                public void run() {
                    if (voicePlayer != null && voicePlayer.isPlaying()) {
                        progress.setProgress(voicePlayer.getCurrentPosition());
                        int seconds = voicePlayer.getCurrentPosition() / 1000;
                        durationText.setText((seconds / 60) + ":" + String.format("%02d", seconds % 60));
                        voiceHandler.postDelayed(this, 100);
                    } else {
                        playBtn.setImageResource(android.R.drawable.ic_media_play);
                        progress.setProgress(0);
                    }
                }
            };
            voiceHandler.post(voiceUpdater);

            voicePlayer.setOnCompletionListener(mp -> {
                playBtn.setImageResource(android.R.drawable.ic_media_play);
                progress.setProgress(0);
                voiceHandler.removeCallbacks(voiceUpdater);
            });

        } catch (Exception e) {
            Toast.makeText(this, "Playback error", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Voice playback failed", e);
        }
    }

    // Voice Notes MicroRecorder
    private MediaRecorder voiceRecorder;
    private File voiceFile;
    private long voiceRecordStartTime;

    private void setupVoiceRecording() {
        ImageButton micBtn = findViewById(R.id.chat_mic_btn);
        if (micBtn == null) return;

        micBtn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 200);
                        return false;
                    }
                    startRecordingVoice();
                    micBtn.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
                    return true;

                case MotionEvent.ACTION_UP:
                    stopRecordingVoice();
                    micBtn.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
                    return true;
            }
            return false;
        });
    }

    private void startRecordingVoice() {
        try {
            voiceFile = File.createTempFile("voice_", ".3gp", getCacheDir());
            voiceRecorder = new MediaRecorder();
            voiceRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            voiceRecorder.setOutputFile(voiceFile.getAbsolutePath());
            voiceRecorder.prepare();
            voiceRecorder.start();
            voiceRecordStartTime = System.currentTimeMillis();
            Toast.makeText(this, "Recording voice note...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start voice recording", e);
            Toast.makeText(this, "Mic in use or initialization failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecordingVoice() {
        if (voiceRecorder == null) return;
        try {
            voiceRecorder.stop();
            voiceRecorder.release();
            voiceRecorder = null;

            long duration = (System.currentTimeMillis() - voiceRecordStartTime) / 1000;
            if (duration < 1) {
                Toast.makeText(this, "Voice note too short", Toast.LENGTH_SHORT).show();
                if (voiceFile != null && voiceFile.exists()) {
                    voiceFile.delete();
                }
                return;
            }

            if (chatRepo == null) chatRepo = new ChatRepository();
            String receiverId = isGroupChat ? activeChatFriend : "peerUser";
            chatRepo.sendMessage(currentTestingUserId, receiverId, "", voiceFile.getAbsolutePath(), "voice", replyToMessageId, duration);
            clearReplyPreview();

        } catch (Exception e) {
            Log.e(TAG, "Failed to stop voice recording", e);
        }
    }

    // Dialog option selector
    private void showChatOptionsDialog(ChatRepository.ChatMessage msg) {
        String[] options = {"Reply", "Pin Message", "Delete Message", "Forward Message", "React with 👍", "React with ❤️", "React with 😂", "React with 😮", "React with 😢", "React with 🙏"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Message Options");
        builder.setItems(options, (dialog, which) -> {
            String choice = options[which];
            if ("Reply".equals(choice)) {
                setupReplyState(msg);
            } else if ("Pin Message".equals(choice)) {
                chatRepo.pinMessage(msg.id, true);
                Toast.makeText(this, "Message pinned!", Toast.LENGTH_SHORT).show();
            } else if ("Delete Message".equals(choice)) {
                chatRepo.deleteMessage(msg.id);
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show();
            } else if ("Forward Message".equals(choice)) {
                showForwardMessageDialog(msg);
            } else {
                String emoji = choice.substring(choice.length() - 2).trim();
                chatRepo.toggleReaction(msg.id, currentTestingUserId, emoji);
            }
        });
        builder.show();
    }

    private void setupReplyState(ChatRepository.ChatMessage msg) {
        replyToMessageId = msg.id;
        replyToMessageText = msg.message != null && !msg.message.isEmpty() ? msg.message : "[" + msg.mediaType + " attachment]";
        
        RelativeLayout replyPreviewBar = findViewById(R.id.chat_reply_preview_bar);
        TextView replyPreviewTitle = findViewById(R.id.chat_reply_preview_title);
        TextView replyPreviewText = findViewById(R.id.chat_reply_preview_text);
        
        if (replyPreviewBar != null && replyPreviewTitle != null && replyPreviewText != null) {
            String senderName = msg.sender.equals(currentTestingUserId) ? "You" : "Alex";
            replyPreviewTitle.setText("Replying to " + senderName);
            replyPreviewText.setText(replyToMessageText);
            replyPreviewBar.setVisibility(View.VISIBLE);
        }
    }

    private void clearReplyPreview() {
        replyToMessageId = null;
        replyToMessageText = null;
        RelativeLayout replyPreviewBar = findViewById(R.id.chat_reply_preview_bar);
        if (replyPreviewBar != null) {
            replyPreviewBar.setVisibility(View.GONE);
        }
    }

    private void showForwardMessageDialog(ChatRepository.ChatMessage msg) {
        String[] friends = {"Jessica", "Sam", "Sarah", "David"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Forward Message to:");
        builder.setItems(friends, (dialog, which) -> {
            String target = friends[which];
            if (chatRepo == null) chatRepo = new ChatRepository();
            chatRepo.sendMessage(currentTestingUserId, target, msg.message, msg.mediaUrl, msg.mediaType, null);
            Toast.makeText(this, "Message forwarded to " + target, Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    // Setup media & stickers picker dialogue options
    private void setupAttachmentHandling() {
        ImageButton attachBtn = findViewById(R.id.chat_attach_btn);
        if (attachBtn != null) {
            attachBtn.setOnClickListener(v -> {
                String[] options = {"Photo Attachment (Camera Mock)", "Video Attachment (Camera Mock)", "Choose Image from Gallery", "Choose Video from Gallery"};
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Send Attachment");
                builder.setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        sendMockMediaMessage("photo", "https://picsum.photos/400/400");
                    } else if (which == 1) {
                        sendMockMediaMessage("video", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4");
                    } else if (which == 2) {
                        sendMockMediaMessage("photo", "https://picsum.photos/400/400?random=" + System.currentTimeMillis());
                    } else {
                        sendMockMediaMessage("video", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4");
                    }
                });
                builder.show();
            });
        }

        ImageButton stickerBtn = findViewById(R.id.chat_sticker_btn);
        if (stickerBtn != null) {
            stickerBtn.setOnClickListener(v -> {
                String[] stickers = {"👻 Cool Ghost Sticker", "⭐ Snap Star Sticker", "📸 Camera Lens Sticker", "🎬 Funny Movie GIF", "🐱 Dancing Cat GIF"};
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Send Stickers / GIFs");
                builder.setItems(stickers, (dialog, which) -> {
                    String selected = stickers[which];
                    if (selected.contains("GIF")) {
                        sendMockMediaMessage("gif", selected);
                    } else {
                        sendMockMediaMessage("sticker", selected);
                    }
                });
                builder.show();
            });
        }
    }

    private void sendMockMediaMessage(String type, String url) {
        if (chatRepo == null) chatRepo = new ChatRepository();
        String receiverId = isGroupChat ? activeChatFriend : "peerUser";
        String messageText = type.equals("sticker") || type.equals("gif") ? url : "";
        chatRepo.sendMessage(currentTestingUserId, receiverId, messageText, url, type, replyToMessageId);
        clearReplyPreview();
    }

    // Group chat creation dialogue popup
    private void setupGroupChatCreation() {
        ImageView createGroupBtn = findViewById(R.id.btn_create_group);
        if (createGroupBtn != null) {
            createGroupBtn.setOnClickListener(v -> {
                String[] friends = {"Alex", "Jessica", "Sam", "Sarah"};
                boolean[] checked = {false, false, false, false};
                
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Create Group Chat");
                builder.setMultiChoiceItems(friends, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                });
                builder.setPositiveButton("Create", (dialog, which) -> {
                    List<String> selectedMembers = new ArrayList<>();
                    selectedMembers.add("currentUser");
                    StringBuilder groupName = new StringBuilder("You");
                    for (int i = 0; i < friends.length; i++) {
                        if (checked[i]) {
                            selectedMembers.add(friends[i].toLowerCase() + "User");
                            groupName.append(", ").append(friends[i]);
                        }
                    }
                    groupName.append(" Group");
                    
                    if (selectedMembers.size() < 2) {
                        Toast.makeText(this, "Please select at least 1 friend to create a group", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (chatRepo == null) chatRepo = new ChatRepository();
                    String groupId = chatRepo.createGroupChat(groupName.toString(), selectedMembers);
                    Toast.makeText(this, "Created Group: " + groupName, Toast.LENGTH_SHORT).show();
                    
                    LinearLayout listContainer = findViewById(R.id.chat_list_container);
                    if (listContainer != null) {
                        appendFriendRow(listContainer, groupId, groupName.toString(), "New Group", "Just now", android.R.drawable.ic_menu_share);
                    }
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            });
        }
    }

    private void appendFriendRow(LinearLayout container, String id, String name, String status, String time, int iconRes) {
        View row = getLayoutInflater().inflate(android.R.layout.activity_list_item, container, false);
        TextView text1 = row.findViewById(android.R.id.text1);
        TextView text2 = row.findViewById(android.R.id.text2);
        ImageView icon = row.findViewById(android.R.id.icon);

        if (text1 != null) {
            text1.setText(name);
            text1.setTextColor(android.graphics.Color.WHITE);
            text1.setTextSize(16);
            text1.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        if (text2 != null) {
            text2.setText(status + " • " + time);
            text2.setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"));
            text2.setTextSize(12);
        }
        if (icon != null) {
            icon.setImageResource(iconRes);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFCC00")));
        }
        row.setPadding(32, 24, 32, 24);

        View sep = new View(this);
        sep.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        sep.setBackgroundColor(android.graphics.Color.parseColor("#15FFFFFF"));

        row.setOnClickListener(v -> openChatWithFriend(id, name));

        container.addView(row, 0); // Insert at top
        container.addView(sep, 1);
    }

    private void showFullscreenImage(String url) {
        View overlay = findViewById(R.id.story_viewer_overlay);
        ImageView img = findViewById(R.id.story_viewer_image);
        VideoView vid = findViewById(R.id.story_viewer_video);
        View closeBtn = findViewById(R.id.story_viewer_close);
        
        if (overlay == null || img == null || vid == null) return;
        overlay.setVisibility(View.VISIBLE);
        vid.setVisibility(View.GONE);
        img.setVisibility(View.VISIBLE);

        if (url.startsWith("content://") || url.startsWith("file://") || url.startsWith("http")) {
            img.setImageURI(Uri.parse(url));
        } else {
            img.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> overlay.setVisibility(View.GONE));
        }
    }

    private void showFullscreenVideo(String url) {
        View overlay = findViewById(R.id.story_viewer_overlay);
        ImageView img = findViewById(R.id.story_viewer_image);
        VideoView vid = findViewById(R.id.story_viewer_video);
        View closeBtn = findViewById(R.id.story_viewer_close);
        
        if (overlay == null || img == null || vid == null) return;
        overlay.setVisibility(View.VISIBLE);
        img.setVisibility(View.GONE);
        vid.setVisibility(View.VISIBLE);

        vid.setVideoPath(url);
        vid.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            vid.start();
        });

        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> {
                vid.stopPlayback();
                overlay.setVisibility(View.GONE);
            });
        }
    }

    private List<File> scanSavedSnaps() {
        List<File> list = new ArrayList<>();
        try {
            File picsDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_PICTURES), "SnapTake");
            if (picsDir.exists() && picsDir.isDirectory()) {
                File[] files = picsDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && (f.getName().endsWith(".jpg") || f.getName().endsWith(".jpeg") || f.getName().endsWith(".png"))) {
                            list.add(f);
                        }
                    }
                }
            }
            File vidsDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MOVIES), "SnapTake");
            if (vidsDir.exists() && vidsDir.isDirectory()) {
                File[] files = vidsDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().endsWith(".mp4")) {
                            list.add(f);
                        }
                    }
                }
            }
            // Sort newest first
            Collections.sort(list, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        } catch (Exception e) {
            Log.e(TAG, "Story scan failed", e);
        }
        return list;
    }

    private void seedMockStoriesIfEmpty() {
        if (storyDb == null) return;
        List<StoryItem> userStories = storyDb.getStoriesByUser("user");
        List<StoryItem> alexStories = storyDb.getStoriesByUser("Alex");
        if (alexStories.isEmpty() && userStories.isEmpty()) {
            Log.d(TAG, "Seeding mock friend stories in SQLite database...");
            long now = System.currentTimeMillis();
            long expires = now + (24 * 60 * 60 * 1000L); // 24 hours

            // 1. Alex Story (Video)
            storyDb.addStory(new StoryItem(
                    "alex_story_1",
                    "Alex",
                    "Alex",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    true,
                    now - 3600000L, // 1 hour ago
                    expires - 3600000L,
                    "EVERYONE",
                    "Summer Chill",
                    "[{\"emoji\":\"🔥\",\"scaleX\":1.5,\"scaleY\":1.5,\"translationX\":100.0,\"translationY\":-150.0,\"rotation\":15.0}]",
                    "[{\"text\":\"Camping vibes! ⛺\",\"color\":-1,\"scaleX\":1.2,\"scaleY\":1.2,\"translationX\":0.0,\"translationY\":100.0,\"rotation\":-5.0}]",
                    "[]", "[]",
                    14, 2, "Sam,Jessica,Sarah", "🔥,❤️"
            ));

            // 2. Jessica Story (Video)
            storyDb.addStory(new StoryItem(
                    "jessica_story_1",
                    "Jessica",
                    "Jessica",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    true,
                    now - 7200000L, // 2 hours ago
                    expires - 7200000L,
                    "EVERYONE",
                    "Lo-Fi Study",
                    "[{\"emoji\":\"✨\",\"scaleX\":1.8,\"scaleY\":1.8,\"translationX\":-120.0,\"translationY\":-200.0,\"rotation\":-10.0}]",
                    "[{\"text\":\"Road trip! 🚗💨\",\"color\":-16711681,\"scaleX\":1.3,\"scaleY\":1.3,\"translationX\":0.0,\"translationY\":250.0,\"rotation\":8.0}]",
                    "[]", "[]",
                    28, 4, "Alex,Sam,Sarah,David", "❤️,❤️,😂"
            ));

            // 3. Sam Story (Video)
            storyDb.addStory(new StoryItem(
                    "sam_story_1",
                    "Sam",
                    "Sam",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                    true,
                    now - 10800000L, // 3 hours ago
                    expires - 10800000L,
                    "EVERYONE",
                    "Dance Vibes",
                    "[{\"emoji\":\"🎉\",\"scaleX\":1.4,\"scaleY\":1.4,\"translationX\":80.0,\"translationY\":-80.0,\"rotation\":20.0}]",
                    "[{\"text\":\"Weekend vibes! 🕺\",\"color\":-256,\"scaleX\":1.1,\"scaleY\":1.1,\"translationX\":0.0,\"translationY\":50.0,\"rotation\":0.0}]",
                    "[]", "[]",
                    8, 0, "Alex,Jessica", "🔥"
            ));
        }
    }

    private void playStoryForUser(String userId, String displayName) {
        if (storyDb == null) return;
        activeStorySegments = storyDb.getStoriesByUser(userId);
        if (activeStorySegments.isEmpty()) {
            Toast.makeText(this, displayName + " has no active stories!", Toast.LENGTH_SHORT).show();
            return;
        }

        View overlay = findViewById(R.id.story_viewer_overlay);
        if (overlay == null) return;
        overlay.setVisibility(View.VISIBLE);

        // Configure close button
        View closeBtn = findViewById(R.id.story_viewer_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> closeStoryViewer());
        }

        // Configure Segmented Progress Bars
        LinearLayout progressContainer = findViewById(R.id.story_viewer_progress_container);
        if (progressContainer != null) {
            progressContainer.removeAllViews();
            for (int i = 0; i < activeStorySegments.size(); i++) {
                android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
                lp.setMargins(6, 0, 6, 0);
                pb.setLayoutParams(lp);
                pb.setMax(100);
                pb.setProgress(0);
                pb.setProgressDrawable(ContextCompat.getDrawable(this, android.R.drawable.progress_horizontal));
                pb.getProgressDrawable().setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
                progressContainer.addView(pb);
            }
        }

        // Configure Touch Navigation Zones
        View leftTouch = findViewById(R.id.story_viewer_left_touch);
        View rightTouch = findViewById(R.id.story_viewer_right_touch);
        
        if (leftTouch != null) {
            leftTouch.setOnClickListener(v -> {
                if (currentStorySegmentIndex > 0) {
                    playSegment(currentStorySegmentIndex - 1);
                } else {
                    closeStoryViewer();
                }
            });
        }
        if (rightTouch != null) {
            rightTouch.setOnClickListener(v -> {
                if (currentStorySegmentIndex < activeStorySegments.size() - 1) {
                    playSegment(currentStorySegmentIndex + 1);
                } else {
                    closeStoryViewer();
                }
            });
        }

        // Setup Reactions bar
        LinearLayout reactionsBar = findViewById(R.id.story_viewer_reactions_bar);
        if (reactionsBar != null) {
            reactionsBar.removeAllViews();
            String[] emojis = {"❤️", "😂", "🔥", "😮", "😢", "👍"};
            for (String emoji : emojis) {
                TextView emojiTv = new TextView(this);
                emojiTv.setText(emoji);
                emojiTv.setTextSize(28);
                emojiTv.setPadding(20, 0, 20, 0);
                emojiTv.setOnClickListener(v -> {
                    StoryItem currentStory = activeStorySegments.get(currentStorySegmentIndex);
                    storyDb.addReaction(currentStory.id, emoji);
                    Toast.makeText(this, "Sent reaction " + emoji, Toast.LENGTH_SHORT).show();
                });
                reactionsBar.addView(emojiTv);
            }
        }

        // Setup Reply box
        android.widget.ImageButton replySend = findViewById(R.id.story_viewer_reply_send);
        android.widget.EditText replyInput = findViewById(R.id.story_viewer_reply_input);
        if (replySend != null && replyInput != null) {
            replySend.setOnClickListener(v -> {
                String text = replyInput.getText().toString();
                if (!text.trim().isEmpty()) {
                    StoryItem currentStory = activeStorySegments.get(currentStorySegmentIndex);
                    storyDb.addReply(currentStory.id, "user", text);
                    
                    // Insert into Chat repository to make it genuinely functional!
                    // Assuming chatRepo has a method to add messages
                    if (chatRepo != null) {
                        chatRepo.sendMessage("user", currentStory.userId, text, null, "text", null);
                    }
                    
                    replyInput.setText("");
                    Toast.makeText(this, "Reply sent to " + displayName + "!", Toast.LENGTH_SHORT).show();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(replyInput.getWindowToken(), 0);
                }
            });
        }

        playSegment(0);
    }

    private void playSegment(int index) {
        if (index < 0 || index >= activeStorySegments.size()) return;
        currentStorySegmentIndex = index;
        
        StoryItem item = activeStorySegments.get(index);
        
        // 1. Reset progress handler
        storyProgressHandler.removeCallbacks(storyProgressRunnable);
        
        // Update Segmented Progress bars visual state
        LinearLayout progressContainer = findViewById(R.id.story_viewer_progress_container);
        if (progressContainer != null) {
            for (int i = 0; i < progressContainer.getChildCount(); i++) {
                android.widget.ProgressBar pb = (android.widget.ProgressBar) progressContainer.getChildAt(i);
                if (i < index) pb.setProgress(100);
                else if (i > index) pb.setProgress(0);
                else pb.setProgress(0);
            }
        }

        // 2. Bind Header details
        TextView usernameTv = findViewById(R.id.story_viewer_username);
        TextView timeTv = findViewById(R.id.story_viewer_time);
        if (usernameTv != null) usernameTv.setText(item.username);
        if (timeTv != null) {
            long diffMins = (System.currentTimeMillis() - item.timestamp) / 60000;
            if (diffMins < 60) timeTv.setText(diffMins + "m ago");
            else timeTv.setText((diffMins / 60) + "h ago");
        }

        // 3. Load Media
        ImageView img = findViewById(R.id.story_viewer_image);
        VideoView vid = findViewById(R.id.story_viewer_video);
        if (img == null || vid == null) return;

        if (item.isVideo) {
            img.setVisibility(View.GONE);
            vid.setVisibility(View.VISIBLE);
            vid.setVideoPath(item.mediaPath);
            vid.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                vid.start();
                
                // Track progress matching video duration
                int duration = mp.getDuration();
                startStoryProgress(duration > 0 ? duration : STORY_PHOTO_DURATION_MS);
            });
        } else {
            vid.setVisibility(View.GONE);
            vid.stopPlayback();
            img.setVisibility(View.VISIBLE);
            img.setImageURI(null);
            if (item.mediaPath.startsWith("content://") || item.mediaPath.startsWith("file://")) {
                img.setImageURI(Uri.parse(item.mediaPath));
            } else {
                img.setImageURI(Uri.fromFile(new java.io.File(item.mediaPath)));
            }
            startStoryProgress(STORY_PHOTO_DURATION_MS);
        }

        // 4. Render Canvas Drawings, Texts, and Stickers Overlays
        FrameLayout overlayContainer = findViewById(R.id.story_viewer_overlay_container);
        DoodleView doodleView = findViewById(R.id.story_viewer_doodle);
        if (overlayContainer != null) {
            StoryOverlayManager.deserializeOverlays(this, overlayContainer, item.textsJson, item.stickersJson, false);
            // Re-inflate DoodleView if we removed it
            if (doodleView == null) {
                doodleView = new DoodleView(this);
                doodleView.setId(R.id.story_viewer_doodle);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                doodleView.setLayoutParams(lp);
                overlayContainer.addView(doodleView);
            }
            doodleView.setEnabled(false);
            doodleView.setDrawingPathsFromJson(item.drawingsJson);
        }

        // 5. Play Background Music loop
        if (storyMusicPlayer != null) {
            storyMusicPlayer.release();
            storyMusicPlayer = null;
        }
        if (item.musicTitle != null) {
            String[] tracks = {"Snap Beats", "Summer Chill", "Dance Vibes", "Lo-Fi Study"};
            String[] urls = {
                "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
            };
            int trackIdx = 0;
            for (int k = 0; k < tracks.length; k++) {
                if (tracks[k].equalsIgnoreCase(item.musicTitle)) {
                    trackIdx = k;
                    break;
                }
            }
            storyMusicPlayer = new android.media.MediaPlayer();
            try {
                storyMusicPlayer.setDataSource(urls[trackIdx]);
                storyMusicPlayer.setLooping(true);
                storyMusicPlayer.prepareAsync();
                storyMusicPlayer.setOnPreparedListener(android.media.MediaPlayer::start);
            } catch (Exception e) {
                Log.e(TAG, "Error playing story music", e);
            }
        }

        // 6. Interaction & Analytics tracking
        View bottomPanel = findViewById(R.id.story_viewer_bottom_panel);
        View ownerMetrics = findViewById(R.id.story_viewer_owner_metrics);
        TextView metricsText = findViewById(R.id.story_viewer_metrics_text);

        if ("user".equalsIgnoreCase(item.userId)) {
            // Owner view: show analytics stats
            if (bottomPanel != null) bottomPanel.setVisibility(View.GONE);
            if (ownerMetrics != null) {
                ownerMetrics.setVisibility(View.VISIBLE);
                if (metricsText != null) {
                    metricsText.setText("👁️ " + item.viewCount + " views  •  📸 " + item.screenshotCount + " screenshots");
                    
                    // Show viewers list bottom sheet on click
                    ownerMetrics.setOnClickListener(v -> {
                        if (item.viewersCsv != null && !item.viewersCsv.isEmpty()) {
                            String[] viewers = item.viewersCsv.split(",");
                            android.widget.ListView listView = new android.widget.ListView(this);
                            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, viewers);
                            listView.setAdapter(adapter);
                            
                            com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
                            sheet.setContentView(listView);
                            sheet.show();
                        } else {
                            Toast.makeText(this, "No views yet!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        } else {
            // Friend view: show reply box and reactions, increment view count
            if (ownerMetrics != null) ownerMetrics.setVisibility(View.GONE);
            if (bottomPanel != null) bottomPanel.setVisibility(View.VISIBLE);
            
            storyDb.incrementView(item.id, "user");
        }
    }

    private void startStoryProgress(int durationMs) {
        storyProgressTick = 0;
        int interval = durationMs / 50; // Update progress bar 50 times over segment duration
        
        storyProgressRunnable = new Runnable() {
            @Override
            public void run() {
                storyProgressTick++;
                LinearLayout progressContainer = findViewById(R.id.story_viewer_progress_container);
                if (progressContainer != null && currentStorySegmentIndex < progressContainer.getChildCount()) {
                    android.widget.ProgressBar pb = (android.widget.ProgressBar) progressContainer.getChildAt(currentStorySegmentIndex);
                    if (pb != null) pb.setProgress(storyProgressTick * 2);
                }
                
                if (storyProgressTick >= 50) {
                    // Auto-advance
                    if (currentStorySegmentIndex < activeStorySegments.size() - 1) {
                        playSegment(currentStorySegmentIndex + 1);
                    } else {
                        closeStoryViewer();
                    }
                } else {
                    storyProgressHandler.postDelayed(this, interval);
                }
            }
        };
        storyProgressHandler.postDelayed(storyProgressRunnable, interval);
    }

    private void closeStoryViewer() {
        storyProgressHandler.removeCallbacks(storyProgressRunnable);
        
        // Stop video
        VideoView vid = findViewById(R.id.story_viewer_video);
        if (vid != null) vid.stopPlayback();

        // Stop music
        if (storyMusicPlayer != null) {
            storyMusicPlayer.stop();
            storyMusicPlayer.release();
            storyMusicPlayer = null;
        }

        View overlay = findViewById(R.id.story_viewer_overlay);
        if (overlay != null) overlay.setVisibility(View.GONE);
        
        setupStoriesSystem();
    }

    private void setupStoriesSystem() {
        seedMockStoriesIfEmpty();
        
        LinearLayout friendsContainer = findViewById(R.id.stories_friends_container);
        if (friendsContainer != null) {
            friendsContainer.removeAllViews();
            
            String[] friends = {"My Story", "Alex", "Jessica", "Sam", "Sarah", "David", "Emma"};
            for (String f : friends) {
                View item = getLayoutInflater().inflate(R.layout.item_story_friend, friendsContainer, false);
                
                View ring = item.findViewById(R.id.story_border_ring);
                View avatarBg = item.findViewById(R.id.story_avatar_bg);
                TextView avatarText = item.findViewById(R.id.story_avatar_text);
                TextView nameView = item.findViewById(R.id.story_friend_name);

                nameView.setText(f);
                
                String userId = f.equals("My Story") ? "user" : f;
                boolean hasStories = storyDb != null && !storyDb.getStoriesByUser(userId).isEmpty();
                
                // Configure avatar emojis and bg colors based on name
                String emoji = "👻";
                String color = "#FFFC00"; // default Snapchat yellow
                if (f.equals("My Story")) {
                    emoji = "👻";
                    color = "#FFFC00";
                } else if (f.equals("Alex")) {
                    emoji = "👦";
                    color = "#FF9500";
                } else if (f.equals("Jessica")) {
                    emoji = "👧";
                    color = "#FF2D55";
                } else if (f.equals("Sam")) {
                    emoji = "👨";
                    color = "#34C759";
                } else if (f.equals("Sarah")) {
                    emoji = "👩";
                    color = "#007AFF";
                } else if (f.equals("David")) {
                    emoji = "👱";
                    color = "#AF52DE";
                } else {
                    emoji = "👩‍🦰";
                    color = "#E0A0FF";
                }

                if (avatarText != null) avatarText.setText(emoji);
                if (avatarBg != null) {
                    avatarBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color)));
                }

                // Show gradient ring only if the friend has active stories
                if (ring != null) {
                    if (hasStories && !f.equals("My Story")) {
                        ring.setVisibility(View.VISIBLE);
                    } else {
                        // For user story or if no stories, hide gradient border ring
                        ring.setBackground(null);
                    }
                }

                item.setOnClickListener(v -> {
                    if (hasStories) {
                        if (ring != null) ring.setBackground(null); // Mark as read
                        playStoryForUser(userId, f);
                    } else {
                        if (f.equals("My Story")) {
                            Toast.makeText(this, "Capture a Snap and click Send to post your first story!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, f + " has no active stories.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                friendsContainer.addView(item);
            }
        }

        GridLayout discoverGrid = findViewById(R.id.stories_discover_grid);
        if (discoverGrid != null) {
            discoverGrid.removeAllViews();
            
            String[] titles = {"Daily Memes", "Travel Goals", "Tech Today", "Food Safari"};
            String[] descs = {"Laugh out loud", "Explore Maldives", "Future of AI", "Best street foods"};
            int[] colors = {
                android.graphics.Color.parseColor("#22FFFFFF"), 
                android.graphics.Color.parseColor("#2200F2FF"), 
                android.graphics.Color.parseColor("#226366F1"), 
                android.graphics.Color.parseColor("#22FFCC00")  
            };

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int cardWidth = (screenWidth - 48) / 2; 

            for (int i = 0; i < titles.length; i++) {
                View card = getLayoutInflater().inflate(R.layout.item_story_discover, discoverGrid, false);
                
                // Configure size
                GridLayout.LayoutParams params = (GridLayout.LayoutParams) card.getLayoutParams();
                params.width = cardWidth;
                params.height = (int) (cardWidth * 1.4f);
                params.setMargins(8, 8, 8, 8);
                card.setLayoutParams(params);

                // Set Card background tint
                View cardBg = card.findViewById(R.id.discover_card_bg);
                if (cardBg != null) {
                    cardBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[i]));
                }

                // Bind Text
                TextView titleView = card.findViewById(R.id.discover_title);
                TextView descView = card.findViewById(R.id.discover_subtitle);
                
                if (titleView != null) titleView.setText(titles[i]);
                if (descView != null) descView.setText(descs[i]);

                final String storyTitle = titles[i];
                boolean isSubbed = getPreferences(MODE_PRIVATE).getBoolean("sub_" + storyTitle, false);
                if (titleView != null) {
                    titleView.setText(isSubbed ? "⭐ " + storyTitle : storyTitle);
                }
                if (descView != null) descView.setText(descs[i]);

                card.setOnClickListener(v -> {
                    playDiscoverChannel(storyTitle);
                });

                discoverGrid.addView(card);
            }
        }
    }

    private final List<String> blockedUsersList = new ArrayList<>();
    private final List<String> pendingFriendRequests = new ArrayList<>();

    private Bitmap generateSnapcode(String username) {
        Bitmap bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(android.graphics.Color.parseColor("#FFFC00"));
        
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(android.graphics.Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        canvas.drawRoundRect(20, 20, 280, 280, 40, 40, paint);
        
        paint.setStyle(Paint.Style.FILL);
        int seed = username.hashCode();
        java.util.Random rand = new java.util.Random(seed);
        for (int x = 40; x <= 260; x += 15) {
            for (int y = 40; y <= 260; y += 15) {
                if (x > 100 && x < 200 && y > 100 && y < 200) continue;
                if (rand.nextBoolean()) {
                    canvas.drawCircle(x, y, 4f, paint);
                }
            }
        }
        
        android.graphics.drawable.Drawable logoDrawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_snaptake_logo_vector);
        if (logoDrawable != null) {
            logoDrawable.setBounds(105, 105, 195, 195);
            logoDrawable.draw(canvas);
        } else {
            paint.setTextSize(64);
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float yOffset = -(fm.ascent + fm.descent) / 2;
            canvas.drawText("👻", 150f, 150f + yOffset, paint);
        }
        
        return bmp;
    }

    private void setupProfileOverlay() {
        View container = findViewById(R.id.profile_overlay_container);
        View backBtn = findViewById(R.id.profile_btn_back);
        if (backBtn != null && container != null) {
            backBtn.setOnClickListener(v -> container.setVisibility(View.GONE));
        }

        // Initialize snapcode
        ImageView snapcodeImg = findViewById(R.id.profile_snapcode_image);
        View snapcodeCard = findViewById(R.id.profile_snapcode_card);
        if (snapcodeImg != null && snapcodeCard != null) {
            Bitmap sc = generateSnapcode("snaptaker");
            snapcodeImg.setImageBitmap(sc);
            snapcodeCard.setOnClickListener(v -> {
                SnapAlertHelper.showDialog(this, "Your Snapcode 👻", 
                    "Scan this code to add @snaptaker on Snaptake!", 
                    "Share Code", () -> showToast("Snapcode shared! 📲"), 
                    "Close", null);
            });
        }

        // Settings Gear Click
        View gearBtn = findViewById(R.id.profile_settings_btn);
        View settingsOverlay = findViewById(R.id.profile_settings_overlay);
        if (gearBtn != null && settingsOverlay != null) {
            gearBtn.setOnClickListener(v -> settingsOverlay.setVisibility(View.VISIBLE));
            
            View closeSettings = findViewById(R.id.profile_settings_close);
            if (closeSettings != null) closeSettings.setOnClickListener(v2 -> settingsOverlay.setVisibility(View.GONE));
            
            Button syncCloud = findViewById(R.id.settings_sync_cloud_btn);
            View syncLoading = findViewById(R.id.cloud_sync_loading_overlay);
            TextView cloudStatus = findViewById(R.id.memories_cloud_status);
            if (syncCloud != null && syncLoading != null) {
                syncCloud.setOnClickListener(v2 -> {
                    syncLoading.setVisibility(View.VISIBLE);
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        syncLoading.setVisibility(View.GONE);
                        settingsOverlay.setVisibility(View.GONE);
                        if (cloudStatus != null) {
                            cloudStatus.setText("☁️ Backed Up");
                            cloudStatus.setTextColor(android.graphics.Color.parseColor("#39FF14"));
                        }
                        showNotification("Cloud Backup Synced ☁️", "Successfully secured all snaps and stickers to vault.", "💾");
                    }, 2500);
                });
            }

            Button clearCache = findViewById(R.id.settings_clear_cache_btn);
            if (clearCache != null) clearCache.setOnClickListener(v2 -> {
                showToast("Media cache cleared! 🧹");
                settingsOverlay.setVisibility(View.GONE);
            });
            
            Button logout = findViewById(R.id.settings_logout_btn);
            if (logout != null) logout.setOnClickListener(v2 -> {
                showToast("Logged out successfully! 🚪");
                settingsOverlay.setVisibility(View.GONE);
                if (container != null) container.setVisibility(View.GONE);
            });
        }

        // My Story Row Click
        View myStoryRow = findViewById(R.id.profile_my_story_row);
        if (myStoryRow != null) {
            myStoryRow.setOnClickListener(v -> {
                if (storyDb != null) {
                    activeStorySegments = storyDb.getStoriesByUser("user");
                    if (activeStorySegments.isEmpty()) {
                        showToast("You have no active story segments! 📸");
                    } else {
                        runOnUiThread(() -> {
                            View overlay = findViewById(R.id.story_viewer_overlay);
                            if (overlay != null) {
                                overlay.setVisibility(View.VISIBLE);
                                
                                View closeBtn = findViewById(R.id.story_viewer_close);
                                if (closeBtn != null) {
                                    closeBtn.setOnClickListener(v2 -> closeStoryViewer());
                                }
                                
                                LinearLayout progressContainer = findViewById(R.id.story_viewer_progress_container);
                                if (progressContainer != null) {
                                    progressContainer.removeAllViews();
                                    for (int i = 0; i < activeStorySegments.size(); i++) {
                                        android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
                                        lp.setMargins(6, 0, 6, 0);
                                        pb.setLayoutParams(lp);
                                        pb.setMax(100);
                                        pb.setProgress(0);
                                        pb.getProgressDrawable().setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
                                        progressContainer.addView(pb);
                                    }
                                }

                                View leftTouch = findViewById(R.id.story_viewer_left_touch);
                                View rightTouch = findViewById(R.id.story_viewer_right_touch);
                                
                                if (leftTouch != null) {
                                    leftTouch.setOnClickListener(v2 -> {
                                        if (currentStorySegmentIndex > 0) {
                                            playSegment(currentStorySegmentIndex - 1);
                                        } else {
                                            closeStoryViewer();
                                        }
                                    });
                                }
                                if (rightTouch != null) {
                                    rightTouch.setOnClickListener(v2 -> {
                                        if (currentStorySegmentIndex < activeStorySegments.size() - 1) {
                                            playSegment(currentStorySegmentIndex + 1);
                                        } else {
                                            closeStoryViewer();
                                        }
                                    });
                                }

                                LinearLayout reactionsBar = findViewById(R.id.story_viewer_reactions_bar);
                                if (reactionsBar != null) reactionsBar.removeAllViews();
                                android.widget.ImageButton replySend = findViewById(R.id.story_viewer_reply_send);
                                if (replySend != null) replySend.setOnClickListener(null);
                                
                                playSegment(0);
                            }
                        });
                    }
                }
            });
        }

        // Refresh dynamic memories grid
        refreshProfileMemoriesGrid();

        // 1. Privacy Spinner
        android.widget.Spinner privacySpinner = findViewById(R.id.privacy_spinner);
        if (privacySpinner != null) {
            String[] privacyOptions = {"Everyone", "Friends Only", "My Eyes Only"};
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, privacyOptions);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            privacySpinner.setAdapter(adapter);
            
            if ("EVERYONE".equals(storyPrivacy)) privacySpinner.setSelection(0);
            else if ("FRIENDS".equals(storyPrivacy)) privacySpinner.setSelection(1);
            else privacySpinner.setSelection(2);

            privacySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position == 0) storyPrivacy = "EVERYONE";
                    else if (position == 1) storyPrivacy = "FRIENDS";
                    else storyPrivacy = "PRIVATE";
                    Toast.makeText(MainActivity.this, "Privacy updated: " + storyPrivacy, Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        // 2. Theme Toggle Switch
        android.widget.Switch themeSwitch = findViewById(R.id.theme_toggle_switch);
        if (themeSwitch != null && container != null) {
            themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    container.setBackgroundColor(android.graphics.Color.parseColor("#F20B0C10")); // Dark grey
                } else {
                    container.setBackgroundColor(android.graphics.Color.parseColor("#F2F2F4F7")); // Snapchat Light grey
                }
            });
        }

        // 3. Contacts Sync
        Button syncBtn = findViewById(R.id.profile_sync_contacts_btn);
        if (syncBtn != null) {
            syncBtn.setOnClickListener(v -> {
                SnapAlertHelper.showDialog(this, "Sync Contacts? 📞", 
                    "Snaptake will scan your address book to find friends nearby.", 
                    "Sync", () -> {
                        showToast("Syncing contacts...");
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            SnapAlertHelper.showDialog(this, "Contacts Found! 👥", 
                                "Found: 'John Doe 👦' and 'Alice Smith 👧' on Snaptake!", 
                                "Add John", () -> {
                                    if (!profileFriendsList.contains("John Doe")) {
                                        profileFriendsList.add("John Doe");
                                        refreshProfileFriendsList();
                                    }
                                    showToast("John Doe added!");
                                }, "Add Alice", () -> {
                                    if (!profileFriendsList.contains("Alice Smith")) {
                                        profileFriendsList.add("Alice Smith");
                                        refreshProfileFriendsList();
                                    }
                                    showToast("Alice Smith added!");
                                });
                        }, 1500);
                    }, "Cancel", null);
            });
        }

        // 4. Friend Requests
        if (pendingFriendRequests.isEmpty()) {
            pendingFriendRequests.add("Cody Ninja 👻");
            pendingFriendRequests.add("Adventure Girl 🎒");
        }
        refreshFriendRequestsUI();

        // 5. Add Friend Action
        Button addBtn = findViewById(R.id.friend_add_btn);
        EditText inputField = findViewById(R.id.friend_add_input);
        if (addBtn != null && inputField != null) {
            addBtn.setOnClickListener(v -> {
                String name = inputField.getText().toString().trim();
                if (!name.isEmpty() && !profileFriendsList.contains(name)) {
                    profileFriendsList.add(name);
                    inputField.setText("");
                    Toast.makeText(this, name + " added to Friends! 👥", Toast.LENGTH_SHORT).show();
                    refreshProfileFriendsList();
                }
            });
        }

        // Populate friends list initially
        if (profileFriendsList.isEmpty()) {
            profileFriendsList.add("Alex");
            profileFriendsList.add("Jessica");
            profileFriendsList.add("Sam");
            profileFriendsList.add("Sarah");
            profileFriendsList.add("David");
            profileFriendsList.add("Emma");
        }
        refreshProfileFriendsList();
    }

    private void refreshProfileMemoriesGrid() {
        GridLayout grid = findViewById(R.id.profile_memories_grid);
        if (grid == null) return;
        
        runOnUiThread(() -> grid.removeAllViews());
        cameraExecutor.execute(() -> {
            List<MediaItem> mediaList = getCapturedMedia();
            if (mediaList.isEmpty()) {
                runOnUiThread(() -> {
                    TextView tv = new TextView(this);
                    tv.setText("No captured memories yet! 📸");
                    tv.setTextColor(android.graphics.Color.GRAY);
                    tv.setTextSize(12);
                    grid.addView(tv);
                });
                return;
            }
            
            int limit = Math.min(mediaList.size(), 6);
            for (int i = 0; i < limit; i++) {
                final int index = i;
                MediaItem item = mediaList.get(index);
                Bitmap thumb = loadScaledBitmap(item.uri, 120);
                if (thumb != null) {
                    runOnUiThread(() -> {
                        ImageView iv = new ImageView(this);
                        iv.setImageBitmap(thumb);
                        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        
                        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                        lp.width = 160;
                        lp.height = 160;
                        lp.setMargins(6, 6, 6, 6);
                        iv.setLayoutParams(lp);
                        
                        iv.setOnClickListener(v -> {
                            galleryItems = mediaList;
                            openMemoriesFullscreenViewer(index);
                        });
                        grid.addView(iv);
                    });
                }
            }
        });
    }

    private void refreshFriendRequestsUI() {
        LinearLayout container = findViewById(R.id.profile_friend_requests_container);
        if (container == null) return;
        container.removeAllViews();

        for (String req : pendingFriendRequests) {
            RelativeLayout row = new RelativeLayout(this);
            row.setPadding(0, 12, 0, 12);

            TextView nameTv = new TextView(this);
            nameTv.setText(req);
            nameTv.setTextColor(android.graphics.Color.WHITE);
            nameTv.setTextSize(15);
            RelativeLayout.LayoutParams lpName = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpName.addRule(RelativeLayout.ALIGN_PARENT_START);
            lpName.addRule(RelativeLayout.CENTER_VERTICAL);
            row.addView(nameTv, lpName);

            LinearLayout btnLayout = new LinearLayout(this);
            btnLayout.setOrientation(LinearLayout.HORIZONTAL);
            RelativeLayout.LayoutParams lpBtns = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpBtns.addRule(RelativeLayout.ALIGN_PARENT_END);
            lpBtns.addRule(RelativeLayout.CENTER_VERTICAL);

            Button accept = new Button(this);
            accept.setText("Accept");
            accept.setTextSize(10);
            accept.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFFC00")));
            accept.setTextColor(android.graphics.Color.BLACK);
            accept.setOnClickListener(v -> {
                pendingFriendRequests.remove(req);
                String cleanName = req.replaceAll("[👻🎒\\s]", "");
                if (!profileFriendsList.contains(cleanName)) {
                    profileFriendsList.add(cleanName);
                }
                showToast("Accepted " + cleanName + "!");
                refreshFriendRequestsUI();
                refreshProfileFriendsList();
            });

            Button ignore = new Button(this);
            ignore.setText("Ignore");
            ignore.setTextSize(10);
            ignore.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33FFFFFF")));
            ignore.setTextColor(android.graphics.Color.WHITE);
            LinearLayout.LayoutParams lpIgnore = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpIgnore.setMarginStart(8);
            ignore.setLayoutParams(lpIgnore);
            ignore.setOnClickListener(v -> {
                pendingFriendRequests.remove(req);
                showToast("Ignored Request");
                refreshFriendRequestsUI();
            });

            btnLayout.addView(accept);
            btnLayout.addView(ignore);
            row.addView(btnLayout, lpBtns);

            container.addView(row);
        }
    }

    private void refreshProfileFriendsList() {
        LinearLayout listContainer = findViewById(R.id.profile_friends_list);
        if (listContainer != null) {
            listContainer.removeAllViews();
            for (String friend : profileFriendsList) {
                if (blockedUsersList.contains(friend)) continue;

                RelativeLayout row = new RelativeLayout(this);
                row.setPadding(0, 16, 0, 16);
                
                TextView nameTv = new TextView(this);
                String tag = "";
                if ("Alex".equals(friend)) tag = " (Best Friend 💛)";
                else if ("Jessica".equals(friend)) tag = " (Mutual Friend 😊)";
                
                nameTv.setText("👦 " + friend + tag);
                nameTv.setTextColor(android.graphics.Color.WHITE);
                nameTv.setTextSize(16);
                RelativeLayout.LayoutParams lpName = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpName.addRule(RelativeLayout.ALIGN_PARENT_START);
                lpName.addRule(RelativeLayout.CENTER_VERTICAL);
                row.addView(nameTv, lpName);

                // Add delete button (X)
                TextView deleteTv = new TextView(this);
                deleteTv.setText("❌");
                deleteTv.setPadding(16, 16, 16, 16);
                RelativeLayout.LayoutParams lpDel = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpDel.addRule(RelativeLayout.ALIGN_PARENT_END);
                lpDel.addRule(RelativeLayout.CENTER_VERTICAL);
                
                deleteTv.setOnClickListener(v -> {
                    SnapAlertHelper.showDialog(this, "Manage " + friend + "? 👥", 
                            "Do you want to remove this friend or block them completely?", 
                            "Remove Friend", () -> {
                                profileFriendsList.remove(friend);
                                Toast.makeText(this, friend + " removed.", Toast.LENGTH_SHORT).show();
                                refreshProfileFriendsList();
                            }, "Block User", () -> {
                                profileFriendsList.remove(friend);
                                blockedUsersList.add(friend);
                                Toast.makeText(this, friend + " blocked completely.", Toast.LENGTH_SHORT).show();
                                refreshProfileFriendsList();
                            });
                });
                row.addView(deleteTv, lpDel);

                listContainer.addView(row);
            }
        }
    }

    private void activateLensByName(String name) {
        int targetPos = -1;
        for (int i = 0; i < lensItems.size(); i++) {
            if (lensItems.get(i).name.equalsIgnoreCase(name)) {
                targetPos = i;
                break;
            }
        }
        if (targetPos != -1) {
            currentLensIndex = targetPos;
            androidx.recyclerview.widget.RecyclerView carousel = findViewById(R.id.lenses_carousel);
            if (carousel != null) {
                carousel.scrollToPosition(targetPos);
                androidx.recyclerview.widget.RecyclerView.Adapter adapter = carousel.getAdapter();
                if (adapter != null) adapter.notifyDataSetChanged();
            }
            updateShutterVisibility();
            FaceOverlayView overlay = findViewById(R.id.face_overlay);
            if (overlay != null) {
                overlay.setActiveLens(name);
            }
            Toast.makeText(this, "Activated " + name + " Lens! ✨", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSearchOverlay() {
        View container = findViewById(R.id.search_overlay_container);
        View backBtn = findViewById(R.id.search_btn_back);
        if (backBtn != null && container != null) {
            backBtn.setOnClickListener(v -> container.setVisibility(View.GONE));
        }

        android.widget.EditText searchInput = findViewById(R.id.search_input);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    refreshSearchResults(s.toString());
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
        
        refreshSearchResults("");
    }

    private void refreshSearchResults(String query) {
        GridLayout lensesGrid = findViewById(R.id.search_lenses_grid);
        LinearLayout friendsList = findViewById(R.id.search_friends_list);
        LinearLayout channelsList = findViewById(R.id.search_channels_list);
        View container = findViewById(R.id.search_overlay_container);

        // 1. Filter Lenses
        if (lensesGrid != null) {
            lensesGrid.removeAllViews();
            for (String lensName : lensesList) {
                if (query.isEmpty() || lensName.toLowerCase().contains(query.toLowerCase())) {
                    TextView tv = new TextView(this);
                    tv.setText("✨ " + lensName);
                    tv.setTextColor(android.graphics.Color.WHITE);
                    tv.setBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"));
                    tv.setPadding(24, 24, 24, 24);
                    tv.setGravity(android.view.Gravity.CENTER);
                    
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = 0;
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                    lp.setMargins(8, 8, 8, 8);
                    tv.setLayoutParams(lp);

                    tv.setOnClickListener(v -> {
                        activateLensByName(lensName);
                        if (container != null) container.setVisibility(View.GONE);
                    });
                    lensesGrid.addView(tv);
                }
            }
        }

        // 2. Filter Friends
        if (friendsList != null) {
            friendsList.removeAllViews();
            for (String friend : profileFriendsList) {
                if (blockedUsersList.contains(friend)) continue;
                if (query.isEmpty() || friend.toLowerCase().contains(query.toLowerCase())) {
                    RelativeLayout row = new RelativeLayout(this);
                    row.setPadding(12, 16, 12, 16);
                    row.setBackgroundColor(android.graphics.Color.parseColor("#1AFFFFFF"));

                    TextView nameTv = new TextView(this);
                    nameTv.setText("👦 " + friend);
                    nameTv.setTextColor(android.graphics.Color.WHITE);
                    nameTv.setTextSize(15);
                    RelativeLayout.LayoutParams lpName = new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lpName.addRule(RelativeLayout.ALIGN_PARENT_START);
                    lpName.addRule(RelativeLayout.CENTER_VERTICAL);
                    row.addView(nameTv, lpName);

                    Button chatBtn = new Button(this);
                    chatBtn.setText("Chat");
                    chatBtn.setTextSize(11f);
                    chatBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFFC00")));
                    chatBtn.setTextColor(android.graphics.Color.BLACK);
                    RelativeLayout.LayoutParams lpBtn = new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, 80);
                    lpBtn.addRule(RelativeLayout.ALIGN_PARENT_END);
                    lpBtn.addRule(RelativeLayout.CENTER_VERTICAL);
                    chatBtn.setOnClickListener(v -> {
                        Toast.makeText(this, "Opening chat with " + friend, Toast.LENGTH_SHORT).show();
                        if (container != null) container.setVisibility(View.GONE);
                        switchTab(2);
                    });
                    row.addView(chatBtn, lpBtn);

                    friendsList.addView(row);
                }
            }
        }

        // 3. Filter Channels
        if (channelsList != null) {
            channelsList.removeAllViews();
            String[] channels = {"Daily Memes", "Travel Goals", "Tech Today", "Food Safari"};
            for (String ch : channels) {
                if (query.isEmpty() || ch.toLowerCase().contains(query.toLowerCase())) {
                    RelativeLayout row = new RelativeLayout(this);
                    row.setPadding(12, 16, 12, 16);
                    row.setBackgroundColor(android.graphics.Color.parseColor("#15FFFFFF"));

                    TextView chTv = new TextView(this);
                    chTv.setText("📺 " + ch);
                    chTv.setTextColor(android.graphics.Color.WHITE);
                    chTv.setTextSize(15);
                    RelativeLayout.LayoutParams lpCh = new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lpCh.addRule(RelativeLayout.ALIGN_PARENT_START);
                    lpCh.addRule(RelativeLayout.CENTER_VERTICAL);
                    row.addView(chTv, lpCh);

                    Button playBtn = new Button(this);
                    playBtn.setText("Watch");
                    playBtn.setTextSize(11f);
                    playBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00F2FF")));
                    playBtn.setTextColor(android.graphics.Color.BLACK);
                    RelativeLayout.LayoutParams lpBtn = new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, 80);
                    lpBtn.addRule(RelativeLayout.ALIGN_PARENT_END);
                    lpBtn.addRule(RelativeLayout.CENTER_VERTICAL);
                    playBtn.setOnClickListener(v -> {
                        if (container != null) container.setVisibility(View.GONE);
                        playDiscoverChannel(ch);
                    });
                    row.addView(playBtn, lpBtn);

                    channelsList.addView(row);
                }
            }
        }
    }

    private void setupMoreToolsDrawer() {
        View drawer = findViewById(R.id.more_tools_drawer);
        ImageButton btnPlus = findViewById(R.id.btnPlus);
        if (btnPlus != null && drawer != null) {
            btnPlus.setOnClickListener(v -> drawer.setVisibility(View.VISIBLE));
            drawer.setOnClickListener(v -> drawer.setVisibility(View.GONE));
            
            View content = (View) drawer.findViewById(R.id.more_tools_title).getParent();
            if (content != null) {
                content.setOnClickListener(v -> {});
            }
        }

        // 1. Grid lines toggle
        android.widget.Switch gridSwitch = findViewById(R.id.tool_toggle_grid);
        ImageView gridHelper = findViewById(R.id.viewfinder_grid_helper);
        if (gridSwitch != null && gridHelper != null) {
            gridSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    drawViewfinderGrid(gridHelper);
                    gridHelper.setVisibility(View.VISIBLE);
                } else {
                    gridHelper.setVisibility(View.GONE);
                }
            });
        }

        // 2. Leveler toggle
        android.widget.Switch levelerSwitch = findViewById(R.id.tool_toggle_leveler);
        View levelContainer = findViewById(R.id.level_container);
        if (levelerSwitch != null && levelContainer != null) {
            levelContainer.setVisibility(View.GONE);
            levelerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                levelContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });
        }

        // 3. Night Mode Boost toggle
        android.widget.Switch nightSwitch = findViewById(R.id.tool_toggle_night);
        if (nightSwitch != null) {
            nightSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isNightBoostActive = isChecked;
                applySimulatedNightBoost(isChecked);
                Toast.makeText(this, isChecked ? "Night Exposure Boost: ON" : "Night Exposure Boost: OFF", Toast.LENGTH_SHORT).show();
            });
        }

        // 4. Aspect Ratio selection
        Button aspectBtn = findViewById(R.id.tool_btn_aspect);
        if (aspectBtn != null) {
            aspectBtn.setOnClickListener(v -> {
                selectedAspectMode = (selectedAspectMode + 1) % 3;
                String modeLabel = "16:9";
                if (selectedAspectMode == 1) modeLabel = "4:3";
                else if (selectedAspectMode == 2) modeLabel = "1:1";
                aspectBtn.setText(modeLabel);
                
                applyAspectRatio(selectedAspectMode);
            });
        }
    }

    private void drawViewfinderGrid(ImageView gridHelper) {
        gridHelper.post(() -> {
            int w = gridHelper.getWidth();
            int h = gridHelper.getHeight();
            if (w <= 0 || h <= 0) return;
            
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            
            Paint p = new Paint();
            p.setColor(android.graphics.Color.parseColor("#4DFFFFFF"));
            p.setStrokeWidth(3f);
            
            c.drawLine(w * 0.33f, 0, w * 0.33f, h, p);
            c.drawLine(w * 0.66f, 0, w * 0.66f, h, p);
            c.drawLine(0, h * 0.33f, w, h * 0.33f, p);
            c.drawLine(0, h * 0.66f, w, h * 0.66f, p);
            
            gridHelper.setImageBitmap(bmp);
        });
    }

    private void applySimulatedNightBoost(boolean active) {
        if (viewFinder != null) {
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            if (active) {
                matrix.set(new float[] {
                    1.4f, 0, 0, 0, 30f,
                    0, 1.4f, 0, 0, 30f,
                    0, 0, 1.4f, 0, 30f,
                    0, 0, 0, 1f, 0
                });
            }
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
            viewFinder.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
        }
    }

    private void applyAspectRatio(int mode) {
        if (viewFinder == null) return;
        
        ViewGroup.LayoutParams lp = viewFinder.getLayoutParams();
        int parentWidth = ((View) viewFinder.getParent()).getWidth();
        int parentHeight = ((View) viewFinder.getParent()).getHeight();
        
        if (mode == 0) {
            lp.width = parentWidth;
            lp.height = (int) (parentWidth * (16f / 9f));
            if (lp.height > parentHeight) {
                lp.height = parentHeight;
                lp.width = (int) (parentHeight * (9f / 16f));
            }
        } else if (mode == 1) {
            lp.width = parentWidth;
            lp.height = (int) (parentWidth * (4f / 3f));
            if (lp.height > parentHeight) {
                lp.height = parentHeight;
                lp.width = (int) (parentHeight * (3f / 4f));
            }
        } else {
            int size = Math.min(parentWidth, parentHeight);
            lp.width = size;
            lp.height = size;
        }
        
        viewFinder.setLayoutParams(lp);
        Toast.makeText(this, "Aspect Ratio adjusted! 📐", Toast.LENGTH_SHORT).show();
    }

    private void showPreCaptureMusicSelector() {
        String[] tracks = {"Snap Beats", "Summer Chill", "Dance Vibes", "Lo-Fi Study"};
        String[] urls = {
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
        };
        
        View overlay = findViewById(R.id.music_selector_bottom_sheet_overlay);
        View card = findViewById(R.id.music_sheet_card);
        if (overlay != null && card != null) {
            overlay.setVisibility(View.VISIBLE);
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(300).start();
            
            card.setTranslationY(1000f);
            card.animate()
                .translationY(0f)
                .setDuration(450)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                .start();
        }

        View dismissBg = findViewById(R.id.music_sheet_bg_dismiss);
        if (dismissBg != null) {
            dismissBg.setOnClickListener(v -> dismissMusicSelector());
        }

        View clearBtn = findViewById(R.id.music_sheet_clear_btn);
        if (clearBtn != null) {
            clearBtn.setOnClickListener(v -> {
                selectedMusicTrack = null;
                ImageButton btnMusic = findViewById(R.id.btnMusic);
                if (btnMusic != null) btnMusic.setBackgroundTintList(null);
                if (storyMusicPlayer != null) {
                    storyMusicPlayer.stop();
                    storyMusicPlayer.release();
                    storyMusicPlayer = null;
                }
                showToast("Music cleared");
                dismissMusicSelector();
            });
        }

        for (int i = 0; i < 4; i++) {
            final int index = i;
            int itemId = getResources().getIdentifier("music_item_" + i, "id", getPackageName());
            View item = findViewById(itemId);
            if (item != null) {
                // highlight state if currently selected
                if (tracks[index].equals(selectedMusicTrack)) {
                    item.setBackgroundColor(android.graphics.Color.parseColor("#44FFFC00"));
                } else {
                    item.setBackgroundColor(android.graphics.Color.parseColor("#2E2E3A"));
                }
                item.setOnClickListener(v -> {
                    selectedMusicTrack = tracks[index];
                    showToast("Music Selected: " + selectedMusicTrack + " 🎵");
                    
                    ImageButton btnMusic = findViewById(R.id.btnMusic);
                    if (btnMusic != null) {
                        btnMusic.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                android.graphics.Color.parseColor("#FFFC00")));
                    }
                    
                    if (storyMusicPlayer != null) {
                        storyMusicPlayer.release();
                    }
                    storyMusicPlayer = new android.media.MediaPlayer();
                    try {
                        storyMusicPlayer.setDataSource(urls[index]);
                        storyMusicPlayer.setLooping(true);
                        storyMusicPlayer.prepareAsync();
                        storyMusicPlayer.setOnPreparedListener(android.media.MediaPlayer::start);
                    } catch (Exception e) {
                        Log.e(TAG, "Error playing music", e);
                    }
                    dismissMusicSelector();
                });
            }
        }
    }

    private void dismissMusicSelector() {
        View overlay = findViewById(R.id.music_selector_bottom_sheet_overlay);
        View card = findViewById(R.id.music_sheet_card);
        if (overlay != null && card != null) {
            card.animate()
                .translationY(1000f)
                .setDuration(350)
                .withEndAction(() -> {
                    overlay.setVisibility(View.GONE);
                })
                .start();
            overlay.animate().alpha(0f).setDuration(300).start();
        }
    }

    private void enterScissorsCropMode() {
        ScissorsTraceView traceCanvas = findViewById(R.id.scissors_trace_canvas);
        ImageView previewImage = findViewById(R.id.post_capture_image);
        if (traceCanvas != null && previewImage != null && capturedUri != null) {
            traceCanvas.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Trace around any part of the image to cut a sticker! ✂️", Toast.LENGTH_LONG).show();
            
            cameraExecutor.execute(() -> {
                try {
                    Bitmap bmp;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        bmp = android.graphics.ImageDecoder.decodeBitmap(
                                android.graphics.ImageDecoder.createSource(getContentResolver(), capturedUri),
                                (decoder, info, source) -> decoder.setMutableRequired(true)
                        );
                    } else {
                        bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), capturedUri);
                        bmp = bmp.copy(Bitmap.Config.ARGB_8888, true);
                    }
                    
                    final Bitmap finalBmp = bmp;
                    runOnUiThread(() -> {
                        traceCanvas.setSourceBitmap(finalBmp);
                        traceCanvas.setCropListener(cropped -> {
                            addCroppedStickerToOverlay(cropped);
                            traceCanvas.setVisibility(View.GONE);
                        });
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load bitmap for crop", e);
                }
            });
        }
    }

    private void addCroppedStickerToOverlay(Bitmap sticker) {
        FrameLayout overlayContainer = findViewById(R.id.post_capture_overlay_container);
        if (overlayContainer != null) {
            ImageView stickerImg = new ImageView(this);
            stickerImg.setImageBitmap(sticker);
            stickerImg.setTag("sticker");
            
            int sizePx = (int) (160 * getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sizePx, sizePx);
            lp.gravity = android.view.Gravity.CENTER;
            stickerImg.setLayoutParams(lp);
            
            stickerImg.setOnTouchListener(new MultiTouchListener());
            overlayContainer.addView(stickerImg);
            
            Toast.makeText(this, "Sticker added! Drag to place. ✂️", Toast.LENGTH_SHORT).show();
        }
    }

    // Discover Playback State
    private String currentDiscoverPublisher = "";
    private int currentDiscoverSegmentIndex = 0;
    private final List<String> discoverSegments = new ArrayList<>();
    private final Handler discoverProgressHandler = new Handler(Looper.getMainLooper());
    private Runnable discoverProgressRunnable;
    private int discoverProgressTick = 0;
    
    private void playDiscoverChannel(String publisherName) {
        currentDiscoverPublisher = publisherName;
        currentDiscoverSegmentIndex = 0;
        
        discoverSegments.clear();
        if (publisherName.equals("Daily Memes")) {
            discoverSegments.add("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4");
            discoverSegments.add("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4");
        } else if (publisherName.equals("Travel Goals")) {
            discoverSegments.add("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4");
            discoverSegments.add("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4");
            discoverSegments.add("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4");
        } else if (publisherName.equals("Tech Today")) {
            discoverSegments.add("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4");
            discoverSegments.add("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4");
        } else {
            discoverSegments.add("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4");
            discoverSegments.add("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4");
            discoverSegments.add("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4");
        }
        
        View overlay = findViewById(R.id.discover_player_overlay);
        if (overlay == null) return;
        overlay.setVisibility(View.VISIBLE);

        View closeBtn = findViewById(R.id.discover_viewer_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> closeDiscoverViewer());
        }

        Button subBtn = findViewById(R.id.discover_viewer_subscribe_btn);
        if (subBtn != null) {
            boolean isSubbed = getPreferences(MODE_PRIVATE).getBoolean("sub_" + publisherName, false);
            subBtn.setText(isSubbed ? "Subscribed ✔️" : "Subscribe");
            subBtn.setOnClickListener(v -> toggleSubscription(publisherName, subBtn));
        }

        LinearLayout progressContainer = findViewById(R.id.discover_viewer_progress_container);
        if (progressContainer != null) {
            progressContainer.removeAllViews();
            for (int i = 0; i < discoverSegments.size(); i++) {
                android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
                lp.setMargins(6, 0, 6, 0);
                pb.setLayoutParams(lp);
                pb.setMax(100);
                pb.setProgress(0);
                pb.setProgressDrawable(ContextCompat.getDrawable(this, android.R.drawable.progress_horizontal));
                if (pb.getProgressDrawable() != null) {
                    pb.getProgressDrawable().setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
                }
                progressContainer.addView(pb);
            }
        }

        View leftTouch = findViewById(R.id.discover_viewer_left_touch);
        View rightTouch = findViewById(R.id.discover_viewer_right_touch);
        if (leftTouch != null) {
            leftTouch.setOnClickListener(v -> {
                if (currentDiscoverSegmentIndex > 0) {
                    playDiscoverSegment(currentDiscoverSegmentIndex - 1);
                } else {
                    closeDiscoverViewer();
                }
            });
        }
        if (rightTouch != null) {
            rightTouch.setOnClickListener(v -> {
                if (currentDiscoverSegmentIndex < discoverSegments.size() - 1) {
                    playDiscoverSegment(currentDiscoverSegmentIndex + 1);
                } else {
                    cycleDiscoverChannel(true);
                }
            });
        }

        overlay.setOnTouchListener(new View.OnTouchListener() {
            private float initialY = 0;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        float diffY = event.getY() - initialY;
                        if (Math.abs(diffY) > 150) {
                            cycleDiscoverChannel(diffY < 0);
                            return true;
                        }
                        break;
                }
                return false;
            }
        });

        playDiscoverSegment(0);
    }

    private void playDiscoverSegment(int index) {
        if (index < 0 || index >= discoverSegments.size()) return;
        currentDiscoverSegmentIndex = index;
        
        String url = discoverSegments.get(index);
        discoverProgressHandler.removeCallbacks(discoverProgressRunnable);

        TextView pubTv = findViewById(R.id.discover_viewer_publisher);
        TextView titleTv = findViewById(R.id.discover_viewer_title);
        if (pubTv != null) pubTv.setText(currentDiscoverPublisher);
        if (titleTv != null) titleTv.setText("Segment " + (index + 1) + " of " + discoverSegments.size());

        LinearLayout progressContainer = findViewById(R.id.discover_viewer_progress_container);
        if (progressContainer != null) {
            for (int i = 0; i < progressContainer.getChildCount(); i++) {
                android.widget.ProgressBar pb = (android.widget.ProgressBar) progressContainer.getChildAt(i);
                if (i < index) pb.setProgress(100);
                else if (i > index) pb.setProgress(0);
                else pb.setProgress(0);
            }
        }

        ImageView img = findViewById(R.id.discover_viewer_image);
        VideoView vid = findViewById(R.id.discover_viewer_video);
        if (img != null && vid != null) {
            img.setVisibility(View.GONE);
            vid.setVisibility(View.VISIBLE);
            vid.setVideoPath(url);
            vid.setOnPreparedListener(mp -> {
                vid.start();
                int dur = mp.getDuration();
                startDiscoverProgress(dur > 0 ? dur : 5000);
            });
        }
    }

    private void startDiscoverProgress(int durationMs) {
        discoverProgressTick = 0;
        int interval = durationMs / 50;
        discoverProgressRunnable = new Runnable() {
            @Override
            public void run() {
                discoverProgressTick++;
                LinearLayout progressContainer = findViewById(R.id.discover_viewer_progress_container);
                if (progressContainer != null && currentDiscoverSegmentIndex < progressContainer.getChildCount()) {
                    android.widget.ProgressBar pb = (android.widget.ProgressBar) progressContainer.getChildAt(currentDiscoverSegmentIndex);
                    if (pb != null) pb.setProgress(discoverProgressTick * 2);
                }
                
                if (discoverProgressTick >= 50) {
                    if (currentDiscoverSegmentIndex < discoverSegments.size() - 1) {
                        playDiscoverSegment(currentDiscoverSegmentIndex + 1);
                    } else {
                        cycleDiscoverChannel(true);
                    }
                } else {
                    discoverProgressHandler.postDelayed(this, interval);
                }
            }
        };
        discoverProgressHandler.postDelayed(discoverProgressRunnable, interval);
    }

    private void cycleDiscoverChannel(boolean forward) {
        String[] channels = {"Daily Memes", "Travel Goals", "Tech Today", "Food Safari"};
        int curIdx = 0;
        for (int i = 0; i < channels.length; i++) {
            if (channels[i].equalsIgnoreCase(currentDiscoverPublisher)) {
                curIdx = i;
                break;
            }
        }
        if (forward) {
            curIdx = (curIdx + 1) % channels.length;
        } else {
            curIdx = (curIdx - 1 + channels.length) % channels.length;
        }
        playDiscoverChannel(channels[curIdx]);
    }

    private void toggleSubscription(String publisherName, Button btn) {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        boolean isSubbed = prefs.getBoolean("sub_" + publisherName, false);
        isSubbed = !isSubbed;
        prefs.edit().putBoolean("sub_" + publisherName, isSubbed).apply();
        
        btn.setText(isSubbed ? "Subscribed ✔️" : "Subscribe");
        
        TextView animText = findViewById(R.id.discover_viewer_sub_anim);
        if (animText != null && isSubbed) {
            animText.setVisibility(View.VISIBLE);
            animText.setAlpha(1.0f);
            animText.setScaleX(1.0f);
            animText.setScaleY(1.0f);
            animText.animate().alpha(0.0f).scaleX(1.4f).scaleY(1.4f).setDuration(1200).withEndAction(() -> animText.setVisibility(View.GONE));
        }
        
        setupStoriesSystem();
    }

    private void closeDiscoverViewer() {
        discoverProgressHandler.removeCallbacks(discoverProgressRunnable);
        VideoView vid = findViewById(R.id.discover_viewer_video);
        if (vid != null) vid.stopPlayback();
        
        View overlay = findViewById(R.id.discover_player_overlay);
        if (overlay != null) overlay.setVisibility(View.GONE);
    }

    private void setupSpotlightSystem() {
        // Spotlight system is fully handled dynamically by ViewPager2 inside setupBottomNavigation()
    }

    private void loadSpotlightItem(int index) {
        // Spotlight items are loaded dynamically inside SpotlightAdapter
    }


    private void initializeUI() {
        ImageButton captureButton = findViewById(R.id.image_capture_button);
        ImageButton btnScissors = findViewById(R.id.btnScissors);
        ImageButton btnFrames = findViewById(R.id.btnFrames);
        ImageButton btnMusic = findViewById(R.id.btnMusic);
        ImageButton btnSnapTool = findViewById(R.id.btnSnapTool);
        ImageButton btnPlus = findViewById(R.id.btnPlus);

        if (btnScissors != null) {
            btnScissors.setOnClickListener(v -> {
                // If we have a captured preview active, enter Scissors Crop Trace Mode
                View postCapture = findViewById(R.id.post_capture_layer);
                if (postCapture != null && postCapture.getVisibility() == View.VISIBLE) {
                    enterScissorsCropMode();
                } else {
                    Toast.makeText(this, "Capture a snap first, then click Scissors to cut stickers! ✂️", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        if (btnFrames != null) {
            btnFrames.setOnClickListener(v -> {
                isMultiSnapActive = !isMultiSnapActive;
                if (isMultiSnapActive) {
                    btnFrames.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#00F2FF"))); // Neon Blue
                    Toast.makeText(this, "Multi-Snap Frames: ACTIVE 🎞️", Toast.LENGTH_SHORT).show();
                    
                    // Turn off burst if active
                    if (isBurstModeActive) {
                        isBurstModeActive = false;
                        if (btnSnapTool != null) btnSnapTool.setBackgroundTintList(null);
                    }
                } else {
                    btnFrames.setBackgroundTintList(null);
                    Toast.makeText(this, "Multi-Snap Frames: INACTIVE", Toast.LENGTH_SHORT).show();
                    
                    // Reset multi-snap lists
                    View container = findViewById(R.id.multi_snap_filmstrip_container);
                    if (container != null) container.setVisibility(View.GONE);
                    LinearLayout filmstrip = findViewById(R.id.multi_snap_filmstrip);
                    if (filmstrip != null) filmstrip.removeAllViews();
                    multiSnapCapturedUris.clear();
                }
            });
        }
        
        if (btnMusic != null) {
            btnMusic.setOnClickListener(v -> {
                showPreCaptureMusicSelector();
            });
        }
        
        if (btnSnapTool != null) {
            btnSnapTool.setOnClickListener(v -> {
                isBurstModeActive = !isBurstModeActive;
                if (isBurstModeActive) {
                    btnSnapTool.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#FFFC00"))); // Snapchat Yellow
                    Toast.makeText(this, "Burst Quick Snap: ACTIVE ⚡", Toast.LENGTH_SHORT).show();
                    
                    // Turn off multi-snap if active
                    if (isMultiSnapActive) {
                        isMultiSnapActive = false;
                        if (btnFrames != null) btnFrames.setBackgroundTintList(null);
                    }
                } else {
                    btnSnapTool.setBackgroundTintList(null);
                    Toast.makeText(this, "Burst Quick Snap: INACTIVE", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        if (btnPlus != null) {
            btnPlus.setOnClickListener(v -> {
                View drawer = findViewById(R.id.more_tools_drawer);
                if (drawer != null) drawer.setVisibility(View.VISIBLE);
            });
        }

        View profileBtn = findViewById(R.id.btn_profile);
        if (profileBtn != null) {
            profileBtn.setOnClickListener(v -> {
                View overlay = findViewById(R.id.profile_overlay_container);
                if (overlay != null) overlay.setVisibility(View.VISIBLE);
            });
        }

        View searchBtn = findViewById(R.id.btn_search);
        if (searchBtn != null) {
            searchBtn.setOnClickListener(v -> {
                View overlay = findViewById(R.id.search_overlay_container);
                if (overlay != null) overlay.setVisibility(View.VISIBLE);
            });
        }

        View addFriendBtn = findViewById(R.id.btn_add_friend);
        if (addFriendBtn != null) addFriendBtn.setOnClickListener(v -> {
            View overlay = findViewById(R.id.profile_overlay_container);
            if (overlay != null) overlay.setVisibility(View.VISIBLE);
        });
        
        SeekBar zoomSlider = findViewById(R.id.zoom_slider);
        setupZoomAndFocus(zoomSlider);
        setupShutterTouchGestures(captureButton);
        setupPostCaptureControls();
        setupMemoriesControls();
        
        // Setup new overlays
        setupProfileOverlay();
        setupSearchOverlay();
        setupMoreToolsDrawer();
        
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
                if (e1 == null || e2 == null) return false;
                
                float diffY = e1.getY() - e2.getY();
                float diffX = e1.getX() - e2.getX();
                
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > 150 && Math.abs(velocityX) > 150) {
                        cycleLiveFilter(diffX > 0);
                        return true;
                    }
                } else {
                    if (diffY > 150 && Math.abs(velocityY) > 150) {
                        openMemoriesDrawer();
                        return true;
                    }
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

    private void cycleLiveFilter(boolean forward) {
        int curIndex = 0;
        for (int i = 0; i < filtersList.length; i++) {
            if (filtersList[i].equals(currentFilter)) {
                curIndex = i;
                break;
            }
        }
        if (forward) {
            curIndex = (curIndex + 1) % filtersList.length;
        } else {
            curIndex = (curIndex - 1 + filtersList.length) % filtersList.length;
        }
        currentFilter = filtersList[curIndex];
        
        applyFilterEffects(currentFilter);
        
        if (filterNameIndicator != null) {
            filterNameIndicator.setVisibility(View.VISIBLE);
            filterNameIndicator.setText(currentFilter);
            filterNameIndicator.setAlpha(1.0f);
            filterNameIndicator.animate()
                    .alpha(0.0f)
                    .setDuration(1000)
                    .withEndAction(() -> filterNameIndicator.setVisibility(View.GONE))
                    .start();
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
            if (deepARManager != null) {
                deepARManager.switchEffect(lensName);
            }
        });
        lensesRecycler.setAdapter(adapter);
    }

    private String[] generateFiltersList() {
        return new String[] {
            "Original", "Noir", "Retro", "Cyberpunk", "Cold Glitch", "Sunset Warm", 
            "Forest Green", "Dramatic", "Matrix Green", "Ocean Blue", "Polaroid Faded", "Acid Neon",
            "Sepia Vintage", "Infrared", "Golden Hour", "Electric Violet", "Teal & Orange",
            "High Contrast", "Rosy Pink", "Cyber Green", "Bleach Bypass", "Cross Process",
            "Ice Cold", "Crimson Red", "Yellow Sunshine"
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
        } else if (filterName.equals("Sepia Vintage")) {
            matrix.set(new float[] {
                0.393f, 0.769f, 0.189f, 0, 0,
                0.349f, 0.686f, 0.168f, 0, 0,
                0.272f, 0.534f, 0.131f, 0, 0,
                0,      0,      0,      1, 0
            });
        } else if (filterName.equals("Infrared")) {
            matrix.set(new float[] {
                0f, 1f, 0f, 0, 0,
                1f, 0f, 0f, 0, 0,
                0f, 0f, 1f, 0, 0,
                0,  0,  0,  1, 0
            });
        } else if (filterName.equals("Golden Hour")) {
            matrix.set(new float[] {
                1.4f, 0.1f, 0f,   0, 10f,
                0.1f, 1.1f, 0f,   0, 5f,
                0f,   0f,   0.6f, 0, -10f,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Electric Violet")) {
            matrix.set(new float[] {
                0.8f, 0f,   0.4f, 0, 15f,
                0f,   0.6f, 0.2f, 0, 0f,
                0.3f, 0f,   1.4f, 0, 20f,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Teal & Orange")) {
            matrix.set(new float[] {
                1.15f, 0.05f, 0f,    0, 15f,
                0f,    0.95f, 0.05f, 0, 0f,
                -0.1f, 0.05f, 1.25f, 0, -10f,
                0,     0,     0,     1, 0
            });
        } else if (filterName.equals("High Contrast")) {
            matrix.set(new float[] {
                1.5f, 0f,   0f,   0, -40f,
                0f,   1.5f, 0f,   0, -40f,
                0f,   0f,   1.5f, 0, -40f,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Rosy Pink")) {
            matrix.set(new float[] {
                1.3f, 0.1f, 0.1f, 0, 20f,
                0.1f, 0.9f, 0.1f, 0, 10f,
                0.1f, 0.1f, 1.1f, 0, 20f,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Cyber Green")) {
            matrix.set(new float[] {
                0.5f, 0.3f, 0f,   0, 0,
                0.2f, 1.4f, 0.1f, 0, 20f,
                0f,   0.2f, 0.5f, 0, 0,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Bleach Bypass")) {
            matrix.set(new float[] {
                1.3f, 0f,   0f,   0, -10f,
                0f,   1.3f, 0f,   0, -10f,
                0f,   0f,   1.3f, 0, -10f,
                0,    0,    0,    1, 0
            });
            android.graphics.ColorMatrix sat = new android.graphics.ColorMatrix();
            sat.setSaturation(0.3f);
            matrix.postConcat(sat);
        } else if (filterName.equals("Cross Process")) {
            matrix.set(new float[] {
                1.1f, 0.1f, 0f,   0, 10f,
                0f,   1.2f, 0.1f, 0, 15f,
                0.1f, 0f,   0.9f, 0, -20f,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Ice Cold")) {
            matrix.set(new float[] {
                0.7f, 0.1f, 0.2f, 0, -10f,
                0.1f, 0.9f, 0.1f, 0, 0f,
                0f,   0.1f, 1.5f, 0, 30f,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Crimson Red")) {
            matrix.set(new float[] {
                1.6f, 0f,   0f,   0, 30f,
                0f,   0.6f, 0f,   0, -10f,
                0f,   0f,   0.6f, 0, -10f,
                0,    0,    0,    1, 0
            });
        } else if (filterName.equals("Yellow Sunshine")) {
            matrix.set(new float[] {
                1.3f, 0.2f, 0f,   0, 15f,
                0.1f, 1.3f, 0f,   0, 15f,
                0f,   0f,   0.5f, 0, -30f,
                0,    0,    0,    1, 0
            });
        }
        return matrix;
    }

    private static class LensItem {
        String name;
        String assetPath;
        int iconResId;
        LensItem(String name, String assetPath, int iconResId) {
            this.name = name;
            this.assetPath = assetPath;
            this.iconResId = iconResId;
        }
    }

    private final List<LensItem> lensItems = new ArrayList<>();
    private int currentLensIndex = 0;

    private void setupLensCarousel() {
        androidx.recyclerview.widget.RecyclerView carousel = findViewById(R.id.lenses_carousel);
        if (carousel == null) return;

        lensItems.clear();
        lensItems.add(new LensItem("None", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Dog", null, R.drawable.ic_tool_snap));
        lensItems.add(new LensItem("Glasses", null, R.drawable.ic_nav_scan));
        lensItems.add(new LensItem("Crown", null, R.drawable.ic_tool_plus));
        lensItems.add(new LensItem("Stache", null, R.drawable.ic_tool_scissors));
        lensItems.add(new LensItem("Neon Devil", null, R.drawable.ic_nav_close));
        lensItems.add(new LensItem("Angel Halo", null, R.drawable.ic_nav_browse));
        lensItems.add(new LensItem("Cyberpunk HUD", null, R.drawable.ic_grid));
        lensItems.add(new LensItem("Bunny", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Cat", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Flower Crown", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Beard", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Ghost", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Star Eyes", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Heart Eyes", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Fire Head", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Rainbow Mouth", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Alien", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Pirate", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Clown", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Superhero", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Vampire", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Wizard", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Space Helmet", null, R.drawable.ic_camera));
        lensItems.add(new LensItem("Butterfly", null, R.drawable.ic_camera));
        
        LensAdapter adapter = new LensAdapter();
        carousel.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        carousel.setAdapter(adapter);

        androidx.recyclerview.widget.SnapHelper snapHelper = new androidx.recyclerview.widget.LinearSnapHelper();
        snapHelper.attachToRecyclerView(carousel);

        carousel.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int newState) {
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(recyclerView.getLayoutManager());
                    if (centerView != null) {
                        int pos = recyclerView.getLayoutManager().getPosition(centerView);
                        if (pos != currentLensIndex && pos >= 0 && pos < lensItems.size()) {
                            currentLensIndex = pos;
                            adapter.notifyDataSetChanged();
                            updateShutterVisibility();
                            
                            // Set the active lens in FaceOverlayView for ML Kit canvas rendering!
                            FaceOverlayView overlay = findViewById(R.id.face_overlay);
                            if (overlay != null) {
                                overlay.setActiveLens(lensItems.get(pos).name);
                            }
                        }
                    }
                }
            }
        });
    }

    private class LensAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<LensAdapter.ViewHolder> {
        class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            View lensCircle;
            TextView emojiView;
            ViewHolder(View v) { 
                super(v); 
                lensCircle = v.findViewById(R.id.lens_circle); 
                emojiView = v.findViewById(R.id.lens_emoji); 
            }
        }
        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.lens_item, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LensItem item = lensItems.get(position);
            
            // Set emoji based on name (hide for "None" so standard shutter is exposed)
            String emoji = "👻";
            if (item.name.equals("None")) emoji = "";
            else if (item.name.equals("Dog")) emoji = "🐶";
            else if (item.name.equals("Glasses")) emoji = "🕶️";
            else if (item.name.equals("Crown")) emoji = "👑";
            else if (item.name.equals("Stache")) emoji = "🥸";
            else if (item.name.equals("Neon Devil")) emoji = "😈";
            else if (item.name.equals("Angel Halo")) emoji = "😇";
            else if (item.name.equals("Cyberpunk HUD")) emoji = "🤖";
            else if (item.name.equals("Bunny")) emoji = "🐰";
            else if (item.name.equals("Cat")) emoji = "🐱";
            else if (item.name.equals("Flower Crown")) emoji = "🌸";
            else if (item.name.equals("Beard")) emoji = "🧔";
            else if (item.name.equals("Ghost")) emoji = "👻";
            else if (item.name.equals("Star Eyes")) emoji = "🤩";
            else if (item.name.equals("Heart Eyes")) emoji = "😍";
            else if (item.name.equals("Fire Head")) emoji = "🔥";
            else if (item.name.equals("Rainbow Mouth")) emoji = "🌈";
            else if (item.name.equals("Alien")) emoji = "👽";
            else if (item.name.equals("Pirate")) emoji = "🏴‍☠️";
            else if (item.name.equals("Clown")) emoji = "🤡";
            else if (item.name.equals("Superhero")) emoji = "🦸";
            else if (item.name.equals("Vampire")) emoji = "🧛";
            else if (item.name.equals("Wizard")) emoji = "🧙";
            else if (item.name.equals("Space Helmet")) emoji = "🧑‍🚀";
            else if (item.name.equals("Butterfly")) emoji = "🦋";
            
            if (holder.emojiView != null) holder.emojiView.setText(emoji);

            // Configure selected / center focus animation
            if (holder.lensCircle != null) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                if (position == currentLensIndex) {
                    if (item.name.equals("None")) {
                        // Make transparent to show the shutter button underneath
                        gd.setColor(android.graphics.Color.TRANSPARENT);
                        gd.setStroke(0, android.graphics.Color.TRANSPARENT);
                    } else {
                        gd.setColor(android.graphics.Color.parseColor("#33FFFC00")); // Snapchat Yellow background tint
                        gd.setStroke(4, android.graphics.Color.parseColor("#FFFC00")); // Snapchat Yellow border
                    }
                    holder.itemView.setScaleX(1.2f);
                    holder.itemView.setScaleY(1.2f);
                } else {
                    gd.setColor(android.graphics.Color.parseColor("#4D000000")); // Dark background
                    gd.setStroke(2, android.graphics.Color.parseColor("#80FFFFFF")); // faint white border
                    holder.itemView.setScaleX(1.0f);
                    holder.itemView.setScaleY(1.0f);
                }
                holder.lensCircle.setBackground(gd);
            }

            // Click action
            holder.itemView.setOnClickListener(v -> {
                if (position == currentLensIndex) {
                    // Clicking the selected lens in the center acts as a shutter tap!
                    takePhoto();
                } else {
                    // Scroll to select lens
                    androidx.recyclerview.widget.RecyclerView carousel = findViewById(R.id.lenses_carousel);
                    if (carousel != null) {
                        carousel.smoothScrollToPosition(position);
                        currentLensIndex = position;
                        notifyDataSetChanged();
                        updateShutterVisibility();
                        FaceOverlayView overlay = findViewById(R.id.face_overlay);
                        if (overlay != null) {
                            overlay.setActiveLens(lensItems.get(position).name);
                        }
                    }
                }
            });

            // Long-click action (starts recording video on center lens)
            holder.itemView.setOnLongClickListener(v -> {
                if (position == currentLensIndex) {
                    startVideoRecordingForSnap();
                    return true;
                }
                return false;
            });
        }
        @Override public int getItemCount() { return lensItems.size(); }
    }

    private void setupPostCaptureControls() {
        View textBtn = findViewById(R.id.post_btn_text);
        View doodleBtn = findViewById(R.id.post_btn_doodle);
        View muteBtn = findViewById(R.id.post_btn_mute);
        View closeBtn = findViewById(R.id.post_btn_close);
        View saveBtn = findViewById(R.id.post_btn_save);
        View shareBtn = findViewById(R.id.post_btn_share);
        View sendStoryBtn = findViewById(R.id.post_btn_send_story);
        View stickersBtn = findViewById(R.id.post_btn_stickers);
        View musicBtn = findViewById(R.id.post_btn_music);
        
        DoodleView doodleView = findViewById(R.id.doodle_canvas);
        FrameLayout overlayContainer = findViewById(R.id.post_capture_overlay_container);

        // 1. Text Overlay Tool
        if (textBtn != null) {
            textBtn.setOnClickListener(v -> {
                EditText input = new EditText(this);
                input.setHint("Add some text...");
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Add Snap Text")
                        .setView(input)
                        .setPositiveButton("Done", (dialog, which) -> {
                            String txt = input.getText().toString();
                            if (!txt.trim().isEmpty()) {
                                TextView tv = new TextView(this);
                                tv.setText(txt);
                                tv.setTextColor(android.graphics.Color.WHITE);
                                tv.setTextSize(26);
                                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                                tv.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK);
                                
                                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                                lp.gravity = android.view.Gravity.CENTER;
                                tv.setLayoutParams(lp);
                                
                                tv.setOnTouchListener(new MultiTouchListener());
                                if (overlayContainer != null) {
                                    overlayContainer.addView(tv);
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // 2. Sticker Overlay Tool (BottomSheet)
        if (stickersBtn != null) {
            stickersBtn.setOnClickListener(v -> {
                String[] emojis = {"🔥", "❤️", "😂", "😎", "✨", "🎉", "👻", "🐱", "🐶", "🍕", "🎵", "🌈", "🚀", "🧁"};
                android.widget.GridView gridView = new android.widget.GridView(this);
                gridView.setNumColumns(4);
                gridView.setPadding(32, 32, 32, 32);
                gridView.setVerticalSpacing(24);
                gridView.setHorizontalSpacing(24);
                
                android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, emojis);
                gridView.setAdapter(adapter);
                
                com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
                sheet.setContentView(gridView);
                
                gridView.setOnItemClickListener((parent, view1, position, id) -> {
                    String emoji = emojis[position];
                    TextView tv = new TextView(this);
                    tv.setText(emoji);
                    tv.setTextSize(64);
                    tv.setTag("sticker");
                    
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = android.view.Gravity.CENTER;
                    tv.setLayoutParams(lp);
                    
                    tv.setOnTouchListener(new MultiTouchListener());
                    if (overlayContainer != null) {
                        overlayContainer.addView(tv);
                    }
                    sheet.dismiss();
                });
                sheet.show();
            });
        }

        // 3. Music Selector & Preview Tool
        if (musicBtn != null) {
            musicBtn.setOnClickListener(v -> {
                String[] tracks = {"Snap Beats", "Summer Chill", "Dance Vibes", "Lo-Fi Study"};
                String[] urls = {
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
                };
                
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Select Background Music")
                        .setItems(tracks, (dialog, which) -> {
                            selectedMusicTrack = tracks[which];
                            Toast.makeText(this, "Music Selected: " + selectedMusicTrack, Toast.LENGTH_SHORT).show();
                            
                            // Play audio loop
                            if (storyMusicPlayer != null) {
                                storyMusicPlayer.release();
                            }
                            storyMusicPlayer = new android.media.MediaPlayer();
                            try {
                                storyMusicPlayer.setDataSource(urls[which]);
                                storyMusicPlayer.setLooping(true);
                                storyMusicPlayer.prepareAsync();
                                storyMusicPlayer.setOnPreparedListener(android.media.MediaPlayer::start);
                            } catch (Exception e) {
                                Log.e(TAG, "Error playing music preview", e);
                            }
                            
                            // Add a visual floating music badge to the canvas
                            TextView tv = new TextView(this);
                            tv.setText("🎵 " + selectedMusicTrack);
                            tv.setTextColor(android.graphics.Color.BLACK);
                            tv.setBackgroundColor(android.graphics.Color.parseColor("#FFFC00")); // Yellow Snapchat tag
                            tv.setPadding(24, 12, 24, 12);
                            tv.setTextSize(16);
                            tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                            
                            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            lp.gravity = android.view.Gravity.CENTER;
                            tv.setLayoutParams(lp);
                            
                            tv.setOnTouchListener(new MultiTouchListener());
                            if (overlayContainer != null) {
                                overlayContainer.addView(tv);
                            }
                        })
                        .show();
            });
        }

        // 4. Drawing Brush Tool
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

        // 5. Close Preview
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> {
                findViewById(R.id.post_capture_layer).setVisibility(View.GONE);
                android.widget.VideoView vv = findViewById(R.id.post_capture_video);
                if (vv != null && vv.isPlaying()) vv.stopPlayback();
                
                // Stop music if playing
                if (storyMusicPlayer != null) {
                    storyMusicPlayer.stop();
                    storyMusicPlayer.release();
                    storyMusicPlayer = null;
                }
                
                // Reset overlays
                if (doodleView != null) {
                    doodleView.clearCanvas();
                    doodleView.setVisibility(View.GONE);
                }
                if (overlayContainer != null) {
                    overlayContainer.removeAllViews();
                }
                selectedMusicTrack = null;
                
                startCamera();
            });
        }

        // 6. Save Snap to Memories
        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                saveFinalizedSnap();
                // Clean up editor state
                if (overlayContainer != null) {
                    overlayContainer.removeAllViews();
                }
                if (storyMusicPlayer != null) {
                    storyMusicPlayer.stop();
                    storyMusicPlayer.release();
                    storyMusicPlayer = null;
                }
                selectedMusicTrack = null;
            });
        }

        // 7. Share Externally
        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> {
                if (capturedUri != null) {
                    shareMedia(capturedUri, capturedIsPhoto);
                }
            });
        }

        // 8. Upload to Story (SQLite insertion)
        if (sendStoryBtn != null) {
            sendStoryBtn.setOnClickListener(v -> {
                if (capturedUri == null) return;
                
                String path = capturedUri.toString();
                String storyId = java.util.UUID.randomUUID().toString();
                long now = System.currentTimeMillis();
                long expires = now + (24 * 60 * 60 * 1000L); // 24 hours
                
                // Serialize drawing paths
                String drawingsJson = "[]";
                if (doodleView != null && doodleView.getVisibility() == View.VISIBLE) {
                    drawingsJson = doodleView.getDrawingPathsJson();
                }
                
                // Serialize text & stickers
                String textsJson = "[]";
                String stickersJson = "[]";
                if (overlayContainer != null) {
                    textsJson = StoryOverlayManager.serializeTexts(overlayContainer);
                    stickersJson = StoryOverlayManager.serializeStickers(overlayContainer);
                }
                
                // Create StoryItem
                StoryItem item = new StoryItem(
                        storyId,
                        "user",          // current user
                        "My Story",
                        path,
                        !capturedIsPhoto, // isVideo
                        now,
                        expires,
                        storyPrivacy,
                        selectedMusicTrack,
                        stickersJson,
                        textsJson,
                        drawingsJson,
                        "[]", // mentions
                        0, 0, "", "" // views, screenshots, reactions
                );
                
                if (storyDb != null) {
                    storyDb.addStory(item);
                }
                
                // Stop music if playing
                if (storyMusicPlayer != null) {
                    storyMusicPlayer.stop();
                    storyMusicPlayer.release();
                    storyMusicPlayer = null;
                }
                
                Toast.makeText(this, "Posted to Story! 👻", Toast.LENGTH_SHORT).show();
                findViewById(R.id.post_capture_layer).setVisibility(View.GONE);
                
                // Reset views
                if (doodleView != null) {
                    doodleView.clearCanvas();
                    doodleView.setVisibility(View.GONE);
                }
                if (overlayContainer != null) {
                    overlayContainer.removeAllViews();
                }
                selectedMusicTrack = null;
                
                setupStoriesSystem();
                startCamera();
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

                // 3. Draw dynamic overlays (texts and stickers)
                FrameLayout overlayContainer = findViewById(R.id.post_capture_overlay_container);
                if (overlayContainer != null) {
                    float scaleRatioX = srcBitmap.getWidth() / (float) viewFinder.getWidth();
                    float scaleRatioY = srcBitmap.getHeight() / (float) viewFinder.getHeight();
                    
                    for (int i = 0; i < overlayContainer.getChildCount(); i++) {
                        View child = overlayContainer.getChildAt(i);
                        if (child instanceof TextView) {
                            TextView tv = (TextView) child;
                            android.graphics.Paint textPaint = new android.graphics.Paint();
                            textPaint.setAntiAlias(true);
                            
                            boolean isSticker = "sticker".equals(tv.getTag());
                            if (isSticker) {
                                textPaint.setTextSize(tv.getTextSize() * scaleRatioX * tv.getScaleX());
                            } else {
                                textPaint.setColor(tv.getCurrentTextColor());
                                textPaint.setTextSize(tv.getTextSize() * scaleRatioX * tv.getScaleX());
                                textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                                textPaint.setShadowLayer(4f * scaleRatioX, 2f * scaleRatioX, 2f * scaleRatioX, android.graphics.Color.BLACK);
                            }
                            
                            float relX = tv.getTranslationX() * scaleRatioX + (srcBitmap.getWidth() / 2f) - (tv.getWidth() * tv.getScaleX() * scaleRatioX / 2f);
                            float relY = tv.getTranslationY() * scaleRatioY + (srcBitmap.getHeight() / 2f) + (tv.getBaseline() * tv.getScaleY() * scaleRatioY / 2f);
                            
                            canvas.save();
                            canvas.rotate(tv.getRotation(), relX + (tv.getWidth() * tv.getScaleX() * scaleRatioX / 2f), relY - (tv.getBaseline() * tv.getScaleY() * scaleRatioY / 2f));
                            canvas.drawText(tv.getText().toString(), relX, relY, textPaint);
                            canvas.restore();
                        }
                    }
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
                    
                    // Reset doodle & overlay container
                    if (doodleView != null) {
                        doodleView.clearCanvas();
                        doodleView.setVisibility(View.GONE);
                    }
                    if (overlayContainer != null) {
                        overlayContainer.removeAllViews();
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
        postCaptureFilter = currentFilter;

        runOnUiThread(() -> {
            findViewById(R.id.post_capture_layer).setVisibility(View.VISIBLE);
            
            ImageView previewImg = findViewById(R.id.post_capture_image);
            android.widget.VideoView previewVid = findViewById(R.id.post_capture_video);
            View muteBtn = findViewById(R.id.post_btn_mute);

            // Apply carry-over live filter
            if (previewImg != null) {
                android.graphics.ColorMatrix matrix = getColorMatrixForFilter(postCaptureFilter);
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
                previewImg.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
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

    private String currentMemoriesTab = "SNAPS";
    private String memoriesSearchQuery = "";
    private final List<Uri> favoritedMemories = new ArrayList<>();
    private final List<Uri> archivedMemories = new ArrayList<>();
    private final List<Uri> privateMemories = new ArrayList<>();
    private String enteredPrivatePasscode = "";
    private boolean isPrivateUnlocked = false;

    private void setupMemoriesControls() {
        androidx.recyclerview.widget.RecyclerView grid = findViewById(R.id.memories_grid);
        if (grid != null) {
            grid.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        }

        View backBtn = findViewById(R.id.memories_btn_back);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> {
                findViewById(R.id.memories_drawer).setVisibility(View.GONE);
                isPrivateUnlocked = false; // Reset lock on exit
                loadLastSavedThumbnail();
            });
        }

        // Tab selection wiring
        TextView tabSnaps = findViewById(R.id.memories_tab_snaps);
        TextView tabRoll = findViewById(R.id.memories_tab_roll);
        TextView tabPrivate = findViewById(R.id.memories_tab_private);

        Runnable resetTabStyles = () -> {
            if (tabSnaps != null) {
                tabSnaps.setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"));
                tabSnaps.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                tabSnaps.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
            if (tabRoll != null) {
                tabRoll.setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"));
                tabRoll.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                tabRoll.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
            if (tabPrivate != null) {
                tabPrivate.setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"));
                tabPrivate.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                tabPrivate.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        };

        if (tabSnaps != null) {
            tabSnaps.setOnClickListener(v -> {
                currentMemoriesTab = "SNAPS";
                resetTabStyles.run();
                tabSnaps.setTextColor(android.graphics.Color.WHITE);
                tabSnaps.setBackgroundColor(android.graphics.Color.parseColor("#4DFFFFFF"));
                tabSnaps.setTypeface(null, android.graphics.Typeface.BOLD);
                openMemoriesDrawer();
            });
        }

        if (tabRoll != null) {
            tabRoll.setOnClickListener(v -> {
                currentMemoriesTab = "ROLL";
                resetTabStyles.run();
                tabRoll.setTextColor(android.graphics.Color.WHITE);
                tabRoll.setBackgroundColor(android.graphics.Color.parseColor("#4DFFFFFF"));
                tabRoll.setTypeface(null, android.graphics.Typeface.BOLD);
                openMemoriesDrawer();
            });
        }

        if (tabPrivate != null) {
            tabPrivate.setOnClickListener(v -> {
                currentMemoriesTab = "PRIVATE";
                resetTabStyles.run();
                tabPrivate.setTextColor(android.graphics.Color.WHITE);
                tabPrivate.setBackgroundColor(android.graphics.Color.parseColor("#4DFFFFFF"));
                tabPrivate.setTypeface(null, android.graphics.Typeface.BOLD);
                
                if (!isPrivateUnlocked) {
                    enteredPrivatePasscode = "";
                    updatePasscodeDots();
                    findViewById(R.id.memories_private_passcode_overlay).setVisibility(View.VISIBLE);
                } else {
                    openMemoriesDrawer();
                }
            });
        }

        // Passcode Keyboard Wiring
        View passcodeOverlay = findViewById(R.id.memories_private_passcode_overlay);
        View keyClose = findViewById(R.id.key_close);
        if (keyClose != null) {
            keyClose.setOnClickListener(v -> {
                if (passcodeOverlay != null) passcodeOverlay.setVisibility(View.GONE);
                if (tabSnaps != null) tabSnaps.performClick();
            });
        }

        View.OnClickListener numListener = v -> {
            Button b = (Button) v;
            if (enteredPrivatePasscode.length() < 4) {
                enteredPrivatePasscode += b.getText().toString();
                updatePasscodeDots();
                
                if (enteredPrivatePasscode.length() == 4) {
                    if ("1111".equals(enteredPrivatePasscode)) {
                        isPrivateUnlocked = true;
                        if (passcodeOverlay != null) passcodeOverlay.setVisibility(View.GONE);
                        openMemoriesDrawer();
                        showToast("Unlocked Private Folder! 🔒");
                    } else {
                        showToast("Incorrect Passcode! ❌");
                        enteredPrivatePasscode = "";
                        updatePasscodeDots();
                        
                        // Shake feedback
                        View layout = findViewById(R.id.memories_private_passcode_overlay);
                        if (layout != null) {
                            layout.animate().translationX(-15).setDuration(50).withEndAction(() -> {
                                layout.animate().translationX(15).setDuration(50).withEndAction(() -> {
                                    layout.animate().translationX(0).setDuration(50).start();
                                }).start();
                            }).start();
                        }
                    }
                }
            }
        };

        int[] numKeys = {R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4, R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9, R.id.key_0};
        for (int id : numKeys) {
            View k = findViewById(id);
            if (k != null) k.setOnClickListener(numListener);
        }

        View keyDel = findViewById(R.id.key_del);
        if (keyDel != null) {
            keyDel.setOnClickListener(v -> {
                if (!enteredPrivatePasscode.isEmpty()) {
                    enteredPrivatePasscode = enteredPrivatePasscode.substring(0, enteredPrivatePasscode.length() - 1);
                    updatePasscodeDots();
                }
            });
        }

        // Search text watcher
        android.widget.EditText searchInput = findViewById(R.id.memories_search_input);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    memoriesSearchQuery = s.toString().trim();
                    openMemoriesDrawer();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    private void updatePasscodeDots() {
        TextView d1 = findViewById(R.id.passcode_dot1);
        TextView d2 = findViewById(R.id.passcode_dot2);
        TextView d3 = findViewById(R.id.passcode_dot3);
        TextView d4 = findViewById(R.id.passcode_dot4);

        if (d1 != null) d1.setText(enteredPrivatePasscode.length() >= 1 ? "●" : "○");
        if (d2 != null) d2.setText(enteredPrivatePasscode.length() >= 2 ? "●" : "○");
        if (d3 != null) d3.setText(enteredPrivatePasscode.length() >= 3 ? "●" : "○");
        if (d4 != null) d4.setText(enteredPrivatePasscode.length() >= 4 ? "●" : "○");
    }

    private void openMemoriesDrawer() {
        cameraExecutor.execute(() -> {
            List<MediaItem> allMedia = getCapturedMedia();
            List<MediaItem> filtered = new ArrayList<>();
            
            for (MediaItem item : allMedia) {
                if (!memoriesSearchQuery.isEmpty()) {
                    String title = item.uri.toString().toLowerCase();
                    if (!title.contains(memoriesSearchQuery.toLowerCase())) {
                        continue;
                    }
                }
                
                if ("SNAPS".equals(currentMemoriesTab)) {
                    if (privateMemories.contains(item.uri) || archivedMemories.contains(item.uri)) {
                        continue;
                    }
                    filtered.add(item);
                } else if ("ROLL".equals(currentMemoriesTab)) {
                    filtered.add(item);
                } else if ("PRIVATE".equals(currentMemoriesTab)) {
                    if (privateMemories.contains(item.uri)) {
                        filtered.add(item);
                    }
                }
            }
            
            galleryItems = filtered;
            runOnUiThread(() -> {
                View drawer = findViewById(R.id.memories_drawer);
                if (drawer != null) {
                    drawer.setVisibility(View.VISIBLE);
                }
                
                androidx.recyclerview.widget.RecyclerView grid = findViewById(R.id.memories_grid);
                if (grid != null) {
                    memoriesAdapter = new MemoriesGridAdapter(this, galleryItems, position -> {
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
                openMemoriesDrawer();
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

        com.google.android.material.button.MaterialButton favBtn = findViewById(R.id.gallery_btn_fav);
        com.google.android.material.button.MaterialButton archiveBtn = findViewById(R.id.gallery_btn_archive);
        com.google.android.material.button.MaterialButton privateBtn = findViewById(R.id.gallery_btn_private);

        Runnable refreshViewerButtons = () -> {
            if (viewPager != null && posIsValid(viewPager.getCurrentItem())) {
                MediaItem item = galleryItems.get(viewPager.getCurrentItem());
                boolean isFav = favoritedMemories.contains(item.uri);
                if (favBtn != null) {
                    favBtn.setIcon(ContextCompat.getDrawable(this, isFav ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off));
                    favBtn.setIconTint(android.content.res.ColorStateList.valueOf(isFav ? android.graphics.Color.parseColor("#FFFC00") : android.graphics.Color.WHITE));
                }
                
                boolean isArchived = archivedMemories.contains(item.uri);
                if (archiveBtn != null) {
                    archiveBtn.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_save));
                    archiveBtn.setIconTint(android.content.res.ColorStateList.valueOf(isArchived ? android.graphics.Color.parseColor("#FFFC00") : android.graphics.Color.WHITE));
                }

                boolean isPrivate = privateMemories.contains(item.uri);
                if (privateBtn != null) {
                    privateBtn.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_secure));
                    privateBtn.setIconTint(android.content.res.ColorStateList.valueOf(isPrivate ? android.graphics.Color.parseColor("#FFFC00") : android.graphics.Color.WHITE));
                }
            }
        };

        if (viewPager != null) {
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    refreshViewerButtons.run();
                }
            });
        }

        if (favBtn != null) {
            favBtn.setOnClickListener(v -> {
                if (viewPager != null && posIsValid(viewPager.getCurrentItem())) {
                    MediaItem item = galleryItems.get(viewPager.getCurrentItem());
                    if (favoritedMemories.contains(item.uri)) {
                        favoritedMemories.remove(item.uri);
                        showToast("Removed from Favorites");
                    } else {
                        favoritedMemories.add(item.uri);
                        showToast("Added to Favorites! ⭐");
                    }
                    refreshViewerButtons.run();
                }
            });
        }

        if (archiveBtn != null) {
            archiveBtn.setOnClickListener(v -> {
                if (viewPager != null && posIsValid(viewPager.getCurrentItem())) {
                    MediaItem item = galleryItems.get(viewPager.getCurrentItem());
                    if (archivedMemories.contains(item.uri)) {
                        archivedMemories.remove(item.uri);
                        showToast("Restored from Archive");
                    } else {
                        archivedMemories.add(item.uri);
                        showToast("Moved to Archive! 📂");
                    }
                    if (viewer != null) viewer.setVisibility(View.GONE);
                    openMemoriesDrawer();
                }
            });
        }

        if (privateBtn != null) {
            privateBtn.setOnClickListener(v -> {
                if (viewPager != null && posIsValid(viewPager.getCurrentItem())) {
                    MediaItem item = galleryItems.get(viewPager.getCurrentItem());
                    if (privateMemories.contains(item.uri)) {
                        privateMemories.remove(item.uri);
                        showToast("Moved back to Snaps");
                    } else {
                        privateMemories.add(item.uri);
                        showToast("Moved to My Eyes Only! 🔒");
                    }
                    if (viewer != null) viewer.setVisibility(View.GONE);
                    openMemoriesDrawer();
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
        
        refreshViewerButtons.run();
    }

    private boolean posIsValid(int pos) {
        return pos >= 0 && pos < galleryItems.size();
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
            
            // Keep the shutter button fixed in the center (no gyro movement)
            View container = findViewById(R.id.capture_container);
            if (container != null) {
                container.setTranslationX(0);
                container.setTranslationY(0);
            }
            
            View scissors = findViewById(R.id.btnScissors);
            if (scissors != null) {
                scissors.setRotationX(pitch * 0.5f);
                scissors.setRotationY(-roll * 0.5f);
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
        if (chatRepo == null) chatRepo = new ChatRepository();
        chatRepo.setUserOnlineStatus("currentUser", true);
        chatRepo.setUserOnlineStatus("peerUser", true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        if (chatRepo == null) chatRepo = new ChatRepository();
        chatRepo.setUserOnlineStatus("currentUser", false);
        chatRepo.setUserOnlineStatus("peerUser", false);
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

    private void toggleFlash(ImageButton btn) {
        flashMode = (flashMode + 1) % 3;
        int icon = R.drawable.ic_flash_off;
        if (flashMode == ImageCapture.FLASH_MODE_ON) icon = R.drawable.ic_flash_on;
        else if (flashMode == ImageCapture.FLASH_MODE_AUTO) icon = R.drawable.ic_flash_auto;
        btn.setImageResource(icon);
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
        androidx.camera.core.SurfaceOrientedMeteringPointFactory factory = new androidx.camera.core.SurfaceOrientedMeteringPointFactory(viewFinder.getWidth(), viewFinder.getHeight());
        androidx.camera.core.MeteringPoint point = factory.createPoint(x, y);
        camera.getCameraControl().startFocusAndMetering(new androidx.camera.core.FocusMeteringAction.Builder(point).build());
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
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

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

                // ImageAnalysis use case for live ML Kit face stickers tracking (uses standard YUV_420_888)
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                boolean isFront = (lensFacing == CameraSelector.LENS_FACING_FRONT);
                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    try {
                        @androidx.camera.core.ExperimentalGetImage
                        android.media.Image mediaImage = imageProxy.getImage();
                        if (mediaImage != null) {
                            int orientation = imageProxy.getImageInfo().getRotationDegrees();
                            com.google.mlkit.vision.common.InputImage image = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, orientation);
                            
                            int width = imageProxy.getWidth();
                            int height = imageProxy.getHeight();

                            faceDetector.process(image)
                                    .addOnSuccessListener(faces -> {
                                        FaceOverlayView overlay = findViewById(R.id.face_overlay);
                                        if (overlay != null) {
                                            overlay.setFaces(faces, width, height, isFront);
                                            if (activeMode == CaptureMode.PHOTO && !faces.isEmpty() && isSmileShutterEnabled) {
                                                for (com.google.mlkit.vision.face.Face face : faces) {
                                                    if (face.getSmilingProbability() != null && face.getSmilingProbability() > 0.85f) {
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
                    } catch (Exception e) {
                        Log.e(TAG, "Image analysis error", e);
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
        if (isBurstModeActive) {
            Toast.makeText(this, "Firing Burst Mode Snap! ⚡", Toast.LENGTH_SHORT).show();
            new Handler(Looper.getMainLooper()).postDelayed(this::executePhotoCapture, 0);
            new Handler(Looper.getMainLooper()).postDelayed(this::executePhotoCapture, 400);
            new Handler(Looper.getMainLooper()).postDelayed(this::executePhotoCapture, 800);
        } else {
            if (timerSeconds > 0) {
                Toast.makeText(this, "Timer: " + timerSeconds + "s", Toast.LENGTH_SHORT).show();
                new Handler(Looper.getMainLooper()).postDelayed(this::executePhotoCapture, timerSeconds * 1000L);
            } else {
                executePhotoCapture();
            }
        }
    }

    private void executePhotoCapture() {
        // Selfie Soft Flash simulation
        if (lensFacing == CameraSelector.LENS_FACING_FRONT && flashMode == ImageCapture.FLASH_MODE_ON) {
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
        if (imageCapture == null) return;
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis());
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapTake");
        }
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cv
        ).build();
        imageCapture.takePicture(options, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                Uri savedUri = results.getSavedUri();
                if (savedUri != null) {
                    runOnUiThread(() -> {
                        if (isBurstModeActive || isMultiSnapActive) {
                            addMultiSnapFrame(savedUri);
                        } else {
                            launchPostCapturePreview(savedUri, true);
                        }
                    });
                }
            }
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed", exception);
            }
        });
    }

    private void addMultiSnapFrame(Uri uri) {
        multiSnapCapturedUris.add(uri);
        
        View container = findViewById(R.id.multi_snap_filmstrip_container);
        LinearLayout filmstrip = findViewById(R.id.multi_snap_filmstrip);
        if (container != null && filmstrip != null) {
            container.setVisibility(View.VISIBLE);
            
            // Create a small card for the thumbnail
            androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
            card.setRadius(12f);
            card.setCardElevation(4f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(120, 120);
            lp.setMargins(10, 0, 10, 0);
            card.setLayoutParams(lp);
            
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageURI(uri);
            card.addView(img);
            
            img.setOnClickListener(v -> {
                container.setVisibility(View.GONE);
                filmstrip.removeAllViews();
                multiSnapCapturedUris.clear();
                launchPostCapturePreview(uri, true);
            });
            
            filmstrip.addView(card);
            
            updateAssembleCollageButton();
        }
    }

    private void updateAssembleCollageButton() {
        LinearLayout filmstrip = findViewById(R.id.multi_snap_filmstrip);
        if (filmstrip == null) return;
        
        View oldBtn = filmstrip.findViewWithTag("btn_assemble_collage");
        if (oldBtn != null) filmstrip.removeView(oldBtn);
        
        if (multiSnapCapturedUris.size() >= 2) {
            Button btn = new Button(this);
            btn.setTag("btn_assemble_collage");
            btn.setText("Assemble\nCollage 🎞️");
            btn.setTextSize(10f);
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#00F2FF"))); // Neon Blue
            btn.setTextColor(android.graphics.Color.BLACK);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, 120);
            lp.setMargins(16, 0, 10, 0);
            btn.setLayoutParams(lp);
            
            btn.setOnClickListener(v -> {
                assembleCollageAndOpenEditor();
            });
            filmstrip.addView(btn);
        }
    }

    private Bitmap loadScaledBitmap(Uri uri, int maxDimension) {
        try {
            Bitmap bmp;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bmp = android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(getContentResolver(), uri),
                        (decoder, info, source) -> decoder.setMutableRequired(true)
                );
            } else {
                bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }
            if (bmp == null) return null;
            
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            int maxDim = Math.max(w, h);
            if (maxDim > maxDimension) {
                float scale = (float) maxDimension / maxDim;
                int targetW = Math.round(w * scale);
                int targetH = Math.round(h * scale);
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true);
                if (scaled != bmp) {
                    bmp.recycle();
                }
                return scaled;
            }
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "Error loading scaled bitmap", e);
            return null;
        }
    }

    private void assembleCollageAndOpenEditor() {
        if (multiSnapCapturedUris.size() < 2) return;
        
        Toast.makeText(this, "Stitching collage...", Toast.LENGTH_SHORT).show();
        
        cameraExecutor.execute(() -> {
            try {
                List<Bitmap> bitmaps = new ArrayList<>();
                for (Uri uri : multiSnapCapturedUris) {
                    Bitmap bmp = loadScaledBitmap(uri, 1080);
                    if (bmp != null) {
                        bitmaps.add(bmp);
                    }
                }
                
                if (bitmaps.isEmpty()) return;
                
                int count = bitmaps.size();
                int w = bitmaps.get(0).getWidth();
                int h = bitmaps.get(0).getHeight();
                
                Bitmap collage;
                if (count == 2) {
                    collage = Bitmap.createBitmap(w * 2, h, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(collage);
                    canvas.drawBitmap(bitmaps.get(0), 0, 0, null);
                    canvas.drawBitmap(bitmaps.get(1), w, 0, null);
                } else {
                    collage = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(collage);
                    canvas.drawBitmap(bitmaps.get(0), 0, 0, null);
                    canvas.drawBitmap(bitmaps.get(1), w, 0, null);
                    canvas.drawBitmap(bitmaps.get(2 % count), 0, h, null);
                    canvas.drawBitmap(bitmaps.get(3 % count), w, h, null);
                }
                
                for (Bitmap bmp : bitmaps) {
                    bmp.recycle();
                }
                
                Uri collageUri = saveBitmapToGallery(collage);
                
                runOnUiThread(() -> {
                    View container = findViewById(R.id.multi_snap_filmstrip_container);
                    if (container != null) container.setVisibility(View.GONE);
                    LinearLayout filmstrip = findViewById(R.id.multi_snap_filmstrip);
                    if (filmstrip != null) filmstrip.removeAllViews();
                    multiSnapCapturedUris.clear();
                    
                    if (collageUri != null) {
                        launchPostCapturePreview(collageUri, true);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Collage assembly failed", e);
            }
        });
    }

    public void showToast(String message) {
        SnapAlertHelper.showToast(this, message);
    }
    
    public void showNotification(String title, String body, String emoji) {
        SnapAlertHelper.showNotification(this, title, body, emoji);
    }

    private Uri saveBitmapToGallery(android.graphics.Bitmap bitmap) {
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis());
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapTake");
        }
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (uri != null) {
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save bitmap", e);
            }
        }
        return uri;
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

    private String currentVideoOutputPath;
    private boolean isRecordingVideo = false;

    private void startVideoRecordingFlow() {
        if (videoCapture == null) return;
        long timestamp = System.currentTimeMillis();
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(timestamp);
        
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SnapTake");
        }
        
        androidx.camera.video.MediaStoreOutputOptions options = new androidx.camera.video.MediaStoreOutputOptions.Builder(
                getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(cv).build();
        
        try {
            isRecordingVideo = true;
            activeRecording = videoCapture.getOutput()
                    .prepareRecording(this, options)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(this), recordEvent -> {
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
                            runOnUiThread(() -> {
                                ProgressBar pg = findViewById(R.id.record_progress);
                                if (pg != null) pg.setVisibility(View.GONE);
                                View timerCont = findViewById(R.id.recording_timer_container);
                                if (timerCont != null) timerCont.setVisibility(View.GONE);
                                stopRecordingTimer();
                                
                                androidx.camera.video.VideoRecordEvent.Finalize finalizeEvent = (androidx.camera.video.VideoRecordEvent.Finalize) recordEvent;
                                Uri savedUri = finalizeEvent.getOutputResults().getOutputUri();
                                if (savedUri != null) {
                                    launchPostCapturePreview(savedUri, false);
                                }
                            });
                        }
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Audio permission missing for recording", e);
        }
    }

    private Uri saveVideoToGallery(String filePath) {
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) return null;
        
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SnapTake");
        }
        
        Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
        if (uri != null) {
            try (java.io.InputStream in = new java.io.FileInputStream(file);
                 java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to save video", e);
            }
        }
        return uri;
    }

    private void stopVideoRecordingForSnap() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
            isRecordingVideo = false;
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
                Bitmap thumb = loadScaledBitmap(lastItem.uri, 120);
                runOnUiThread(() -> {
                    ImageView lastImage = findViewById(R.id.last_image_preview);
                    if (lastImage != null && thumb != null) {
                        lastImage.setImageBitmap(thumb);
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
            selectionArgs = new String[]{ "%SnapTake%" };
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
            vidSelectionArgs = new String[]{ "%SnapTake%" };
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
        
        // Expiration manager and media player cleanups
        StoryExpirationManager.getInstance(this).stopAutoCleanup();
        if (storyMusicPlayer != null) {
            storyMusicPlayer.release();
            storyMusicPlayer = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initializeExtensionsAndStartCamera();
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (rc == 100) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationTracking();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
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
            View view = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.lens_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String name = lenses[position];
            
            View lensCircle = holder.itemView.findViewById(R.id.lens_circle);
            TextView emojiView = holder.itemView.findViewById(R.id.lens_emoji);
            
            // Set emoji representing the lens type
            String emoji = "👻";
            if (name.equals("None")) emoji = "🚫";
            else if (name.equals("Dog")) emoji = "🐶";
            else if (name.equals("Glasses")) emoji = "🕶️";
            else if (name.equals("Crown")) emoji = "👑";
            else if (name.equals("Stache")) emoji = "🥸";
            else if (name.equals("Neon Devil")) emoji = "😈";
            else if (name.equals("Angel Halo")) emoji = "😇";
            else if (name.equals("Cyberpunk HUD")) emoji = "🤖";
            
            if (emojiView != null) emojiView.setText(emoji);

            // Set Border Stroke for Selected Lens
            if (lensCircle != null) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                if (name.equals(activeLens)) {
                    gd.setColor(android.graphics.Color.parseColor("#33FFFC00")); // semi-transparent Snapchat Yellow
                    gd.setStroke(4, android.graphics.Color.parseColor("#FFFC00")); // solid Snapchat Yellow
                } else {
                    gd.setColor(android.graphics.Color.parseColor("#4D000000")); // dark glass
                    gd.setStroke(2, android.graphics.Color.parseColor("#80FFFFFF")); // faint white
                }
                lensCircle.setBackground(gd);
            }

            holder.itemView.setOnClickListener(v -> {
                activeLens = name;
                notifyDataSetChanged();
                if (listener != null) listener.onLensClick(name);
            });
        }

        @Override
        public int getItemCount() { return lenses.length; }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ViewHolder(View view) { super(view); }
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
