package com.example.camera;

import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class LocationRepository {

    public interface LocationCallback {
        void onLocationsUpdated(Map<String, UserLocation> locations);
    }

    public static class UserLocation {
        public double latitude;
        public double longitude;
        public long timestamp;

        public UserLocation() {}

        public UserLocation(double latitude, double longitude, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }
    }

    private final DatabaseReference locationsRef;
    private ValueEventListener listener;

    public LocationRepository() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        locationsRef = database.getReference("locations");
    }

    public void updateLocation(String userId, Location location) {
        UserLocation loc = new UserLocation(location.getLatitude(), location.getLongitude(), System.currentTimeMillis());
        locationsRef.child(userId).setValue(loc)
                .addOnFailureListener(e -> Log.e("LocationRepository", "Failed to update location", e));
    }

    public void listenForLocations(LocationCallback callback) {
        if (listener != null) {
            locationsRef.removeEventListener(listener);
        }
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, UserLocation> locations = new HashMap<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    UserLocation loc = child.getValue(UserLocation.class);
                    if (loc != null) {
                        locations.put(child.getKey(), loc);
                    }
                }
                callback.onLocationsUpdated(locations);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("LocationRepository", "Listen cancelled", error.toException());
            }
        };
        locationsRef.addValueEventListener(listener);
    }

    public void stopListening() {
        if (listener != null) {
            locationsRef.removeEventListener(listener);
        }
    }
}
