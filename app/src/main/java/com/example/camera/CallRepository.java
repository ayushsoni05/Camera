package com.example.camera;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;

public class CallRepository {

    private static final String TAG = "CallRepository";

    public static class CallSession {
        public String callId;
        public String callerId;
        public String callerName;
        public String receiverId;
        public String receiverName;
        public String callType; // "AUDIO" or "VIDEO"
        public String status;   // "OFFERED", "RINGING", "CONNECTED", "DECLINED", "ENDED", "MISSED"
        public long timestamp;
        public long durationSeconds;

        public CallSession() {}

        public CallSession(String callId, String callerId, String callerName, String receiverId, String receiverName, String callType, String status, long timestamp) {
            this.callId = callId;
            this.callerId = callerId;
            this.callerName = callerName;
            this.receiverId = receiverId;
            this.receiverName = receiverName;
            this.callType = callType;
            this.status = status;
            this.timestamp = timestamp;
            this.durationSeconds = 0;
        }
    }

    public interface CallCallback {
        void onCallUpdated(CallSession session);
    }

    public interface IncomingCallListener {
        void onIncomingCall(CallSession session);
    }

    private final DatabaseReference callsRef;
    private static final Map<String, CallSession> localCallBackup = new HashMap<>();

    public CallRepository() {
        callsRef = FirebaseSafeHelper.getDatabaseReference() != null ? FirebaseSafeHelper.getDatabaseReference().child("calls") : null;
    }

    public String initiateCall(String callerId, String callerName, String receiverId, String receiverName, String callType) {
        String callId = callsRef != null ? callsRef.push().getKey() : null;
        if (callId == null) {
            callId = "call_local_" + System.currentTimeMillis();
        }

        CallSession session = new CallSession(callId, callerId, callerName, receiverId, receiverName, callType, "OFFERED", System.currentTimeMillis());
        localCallBackup.put(callId, session);

        if (callsRef != null && !callId.startsWith("call_local_")) {
            callsRef.child(callId).setValue(session)
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to initiate call on Firebase", e));
        }
        return callId;
    }

    public void updateCallStatus(String callId, String status, long durationSeconds) {
        CallSession session = localCallBackup.get(callId);
        if (session != null) {
            session.status = status;
            session.durationSeconds = durationSeconds;
            localCallBackup.put(callId, session);
        }

        if (callsRef != null && callId != null && !callId.startsWith("call_local_")) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", status);
            updates.put("durationSeconds", durationSeconds);
            callsRef.child(callId).updateChildren(updates);
        }
    }

    public void acceptCall(String callId) {
        updateCallStatus(callId, "CONNECTED", 0);
    }

    public void declineCall(String callId) {
        updateCallStatus(callId, "DECLINED", 0);
    }

    public void endCall(String callId, long durationSeconds) {
        updateCallStatus(callId, "ENDED", durationSeconds);
    }

    public void listenForCall(String callId, CallCallback callback) {
        if (callId == null || callback == null) return;

        if (callsRef != null && !callId.startsWith("call_local_")) {
            callsRef.child(callId).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    CallSession session = snapshot.getValue(CallSession.class);
                    if (session != null) {
                        localCallBackup.put(callId, session);
                        callback.onCallUpdated(session);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Call listener cancelled: " + error.getMessage());
                }
            });
        } else {
            CallSession session = localCallBackup.get(callId);
            if (session != null) {
                callback.onCallUpdated(session);
            }
        }
    }

    public void listenForIncomingCalls(String currentUserId, IncomingCallListener listener) {
        if (currentUserId == null || listener == null || callsRef == null) return;

        callsRef.orderByChild("receiverId").equalTo(currentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    CallSession session = child.getValue(CallSession.class);
                    if (session != null && "OFFERED".equals(session.status)) {
                        listener.onIncomingCall(session);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Incoming calls listener error: " + error.getMessage());
            }
        });
    }
}
