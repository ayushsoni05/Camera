package com.example.camera;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class QueueRepository {

    private static final String PREFS_NAME = "OfflineSnapQueue";
    private static final String KEY_QUEUE = "snap_queue";
    private final SharedPreferences prefs;

    public static class OfflineSnap {
        public String snapId;
        public String recipientName;
        public String filePath;
        public long timestamp;

        public OfflineSnap(String snapId, String recipientName, String filePath, long timestamp) {
            this.snapId = snapId;
            this.recipientName = recipientName;
            this.filePath = filePath;
            this.timestamp = timestamp;
        }
    }

    public QueueRepository(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void enqueueSnap(String recipient, String path) {
        List<OfflineSnap> current = getQueue();
        String id = "snap_" + System.currentTimeMillis();
        current.add(new OfflineSnap(id, recipient, path, System.currentTimeMillis()));
        saveQueue(current);
    }

    public synchronized List<OfflineSnap> getQueue() {
        List<OfflineSnap> list = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_QUEUE, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new OfflineSnap(
                    obj.getString("snapId"),
                    obj.getString("recipientName"),
                    obj.getString("filePath"),
                    obj.getLong("timestamp")
                ));
            }
        } catch (Exception e) {
            // Ignore malformed JSON
        }
        return list;
    }

    public synchronized void clearQueue() {
        prefs.edit().remove(KEY_QUEUE).apply();
    }

    public synchronized void removeSnap(String snapId) {
        List<OfflineSnap> current = getQueue();
        List<OfflineSnap> updated = new ArrayList<>();
        for (OfflineSnap s : current) {
            if (!s.snapId.equals(snapId)) {
                updated.add(s);
            }
        }
        saveQueue(updated);
    }

    private void saveQueue(List<OfflineSnap> list) {
        try {
            JSONArray arr = new JSONArray();
            for (OfflineSnap s : list) {
                JSONObject obj = new JSONObject();
                obj.put("snapId", s.snapId);
                obj.put("recipientName", s.recipientName);
                obj.put("filePath", s.filePath);
                obj.put("timestamp", s.timestamp);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_QUEUE, arr.toString()).apply();
        } catch (Exception e) {
            // Ignore format errors
        }
    }
}
