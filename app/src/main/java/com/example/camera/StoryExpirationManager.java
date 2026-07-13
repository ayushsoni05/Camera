package com.example.camera;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StoryExpirationManager {

    private static final String TAG = "StoryExpirationManager";
    private static StoryExpirationManager instance;
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private StoryDatabaseHelper dbHelper;
    private ExpirationListener listener;
    
    public interface ExpirationListener {
        void onStoriesExpired();
    }

    private StoryExpirationManager(Context context) {
        dbHelper = new StoryDatabaseHelper(context.getApplicationContext());
    }

    public static synchronized StoryExpirationManager getInstance(Context context) {
        if (instance == null) {
            instance = new StoryExpirationManager(context);
        }
        return instance;
    }

    public void setListener(ExpirationListener listener) {
        this.listener = listener;
    }

    public void startAutoCleanup() {
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    dbHelper.deleteExpiredStories();
                    
                    // Notify on main thread to refresh stories UI
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onStoriesExpired();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error in auto-cleanup thread", e);
                }
            }
        }, 5, 10, TimeUnit.SECONDS); // Check every 10 seconds
    }

    public void stopAutoCleanup() {
        scheduler.shutdown();
    }
}
