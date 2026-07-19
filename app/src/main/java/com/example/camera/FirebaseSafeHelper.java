package com.example.camera;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;

public class FirebaseSafeHelper {
    private static final String TAG = "FirebaseSafeHelper";
    private static final String DEFAULT_DATABASE_URL = "https://snaptake-5a82f-default-rtdb.firebaseio.com";

    public static FirebaseDatabase getDatabase() {
        try {
            // First try default initialization
            return FirebaseDatabase.getInstance();
        } catch (Exception e) {
            try {
                // Fallback to default RTDB URL if google-services.json is missing database_url
                return FirebaseDatabase.getInstance(DEFAULT_DATABASE_URL);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to initialize FirebaseDatabase", ex);
                return null;
            }
        }
    }

    public static DatabaseReference getDatabaseReference() {
        FirebaseDatabase db = getDatabase();
        return db != null ? db.getReference() : null;
    }

    public static FirebaseAuth getAuth() {
        try {
            return FirebaseAuth.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize FirebaseAuth", e);
            return null;
        }
    }
}
