package com.example.camera;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;

public class CapsuleRepository {

    private static final String TAG = "CapsuleRepository";

    public static class TimeCapsule {
        public String capsuleId;
        public String creatorName;
        public double latitude;
        public double longitude;
        public String message;
        public long timestamp;
        public int durationHours;

        public TimeCapsule() {}

        public TimeCapsule(String capsuleId, String creatorName, double latitude, double longitude, String message, long timestamp, int durationHours) {
            this.capsuleId = capsuleId;
            this.creatorName = creatorName;
            this.latitude = latitude;
            this.longitude = longitude;
            this.message = message;
            this.timestamp = timestamp;
            this.durationHours = durationHours;
        }

        public boolean isExpired() {
            long durationMillis = (long) durationHours * 60 * 60 * 1000;
            return System.currentTimeMillis() > (timestamp + durationMillis);
        }
    }

    public interface CapsuleCallback {
        void onCapsulesUpdated(Map<String, TimeCapsule> capsules);
    }

    private final DatabaseReference capsulesRef;
    private static final Map<String, TimeCapsule> localCapsulesBackup = new HashMap<>();

    public CapsuleRepository() {
        capsulesRef = FirebaseSafeHelper.getDatabaseReference() != null ? FirebaseSafeHelper.getDatabaseReference().child("capsules") : null;
    }

    public void postCapsule(String creatorName, double lat, double lng, String message, int durationHours) {
        String key = capsulesRef != null ? capsulesRef.push().getKey() : null;
        if (key == null) {
            key = "capsule_local_" + System.currentTimeMillis();
        }

        TimeCapsule capsule = new TimeCapsule(key, creatorName, lat, lng, message, System.currentTimeMillis(), durationHours);
        localCapsulesBackup.put(key, capsule);

        if (capsulesRef != null && !key.startsWith("capsule_local_")) {
            capsulesRef.child(key).setValue(capsule)
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to post Time Capsule to Firebase", e));
        }
    }

    public void listenForCapsules(CapsuleCallback callback) {
        if (callback == null) return;

        if (capsulesRef != null) {
            capsulesRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Map<String, TimeCapsule> capsules = new HashMap<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        TimeCapsule cap = child.getValue(TimeCapsule.class);
                        if (cap != null) {
                            if (!cap.isExpired()) {
                                capsules.put(child.getKey(), cap);
                            } else {
                                // Auto clean expired node
                                child.getRef().removeValue();
                            }
                        }
                    }
                    callback.onCapsulesUpdated(capsules);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Capsule listener cancelled", error.toException());
                }
            });
        } else {
            // Offline local callback fallback
            Map<String, TimeCapsule> activeLocal = new HashMap<>();
            for (Map.Entry<String, TimeCapsule> entry : localCapsulesBackup.entrySet()) {
                if (!entry.getValue().isExpired()) {
                    activeLocal.put(entry.getKey(), entry.getValue());
                }
            }
            callback.onCapsulesUpdated(activeLocal);
        }
    }
}
