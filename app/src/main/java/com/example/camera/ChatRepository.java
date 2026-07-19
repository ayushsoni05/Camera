package com.example.camera;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChatRepository {

    public interface ChatCallback {
        void onMessagesUpdated(List<ChatMessage> messages);
    }

    public static class ChatMessage {
        public String id;
        public String sender;
        public String receiver;
        public String message;
        public String mediaUrl;
        public String mediaType; // "text", "photo", "video", "voice", "sticker", "gif"
        public String status; // "sent", "delivered", "read"
        public String replyToId;
        public long timestamp;
        public java.util.Map<String, String> reactions; // userId -> emoji
        public long mediaDuration; // voice notes duration in seconds
        public boolean isPinned;

        public ChatMessage() {}

        public ChatMessage(String id, String sender, String receiver, String message, String mediaUrl, String mediaType, String replyToId, long timestamp) {
            this.id = id;
            this.sender = sender;
            this.receiver = receiver;
            this.message = message;
            this.mediaUrl = mediaUrl;
            this.mediaType = mediaType;
            this.status = "sent";
            this.replyToId = replyToId;
            this.timestamp = timestamp;
            this.reactions = new java.util.HashMap<>();
            this.mediaDuration = 0;
            this.isPinned = false;
        }
    }

    private final DatabaseReference messagesRef;
    private ValueEventListener listener;
    
    // Local memory backup to ensure offline-first support and immediate rendering
    private final List<ChatMessage> localBackupList = new ArrayList<>();
    private ChatCallback activeCallback;
    private String activeCurrentUserId;
    private String activeFriendId;

    public ChatRepository() {
        FirebaseDatabase database = FirebaseSafeHelper.getDatabase();
        messagesRef = database != null ? database.getReference("messages") : null;
    }

    public void sendMessage(String sender, String receiver, String text, String mediaUrl, String mediaType, String replyToId, long mediaDuration) {
        String key = messagesRef != null ? messagesRef.push().getKey() : null;
        if (key == null) {
            key = "local_" + System.currentTimeMillis();
        }
        ChatMessage msg = new ChatMessage(key, sender, receiver, text, mediaUrl, mediaType, replyToId, System.currentTimeMillis());
        msg.mediaDuration = mediaDuration;
        
        // Save to local backup list instantly for offline/connection-delay support
        localBackupList.add(msg);
        triggerLocalCallback();

        // Push to Firebase in the background
        if (messagesRef != null && !key.startsWith("local_")) {
            messagesRef.child(key).setValue(msg)
                    .addOnFailureListener(e -> Log.e("ChatRepository", "Failed to send message", e));
        }
    }

    public void sendMessage(String sender, String receiver, String text, String mediaUrl, String mediaType, String replyToId) {
        sendMessage(sender, receiver, text, mediaUrl, mediaType, replyToId, 0);
    }

    public void updateMessageStatus(String messageId, String status) {
        messagesRef.child(messageId).child("status").setValue(status);
    }

    public void deleteMessage(String messageId) {
        // Firebase update
        messagesRef.child(messageId).removeValue();
        
        // Local update
        for (int i = 0; i < localBackupList.size(); i++) {
            if (localBackupList.get(i).id.equals(messageId)) {
                localBackupList.remove(i);
                break;
            }
        }
        triggerLocalCallback();
    }

    public void editMessage(String messageId, String newText) {
        messagesRef.child(messageId).child("message").setValue(newText);
    }

    public void toggleReaction(String messageId, String userId, String emoji) {
        // Local update
        for (int i = 0; i < localBackupList.size(); i++) {
            if (localBackupList.get(i).id.equals(messageId)) {
                ChatMessage msg = localBackupList.get(i);
                if (msg.reactions == null) {
                    msg.reactions = new java.util.HashMap<>();
                }
                if (emoji.equals(msg.reactions.get(userId))) {
                    msg.reactions.remove(userId);
                    messagesRef.child(messageId).child("reactions").child(userId).removeValue();
                } else {
                    msg.reactions.put(userId, emoji);
                    messagesRef.child(messageId).child("reactions").child(userId).setValue(emoji);
                }
                localBackupList.set(i, msg);
                break;
            }
        }
        triggerLocalCallback();
    }

    public void pinMessage(String messageId, boolean isPinned) {
        messagesRef.child(messageId).child("isPinned").setValue(isPinned);
        
        // Local update
        for (int i = 0; i < localBackupList.size(); i++) {
            if (localBackupList.get(i).id.equals(messageId)) {
                ChatMessage msg = localBackupList.get(i);
                msg.isPinned = isPinned;
                localBackupList.set(i, msg);
                break;
            }
        }
        triggerLocalCallback();
    }

    public void setTypingStatus(String userId, String friendId) {
        DatabaseReference ref = FirebaseSafeHelper.getDatabaseReference();
        if (ref != null) {
            ref.child("users").child(userId).child("typingTo").setValue(friendId);
        }
    }

    public void listenToTypingStatus(String friendId, ValueEventListener listener) {
        DatabaseReference ref = FirebaseSafeHelper.getDatabaseReference();
        if (ref != null) {
            ref.child("users").child(friendId).child("typingTo").addValueEventListener(listener);
        }
    }

    public void setUserOnlineStatus(String userId, boolean isOnline) {
        DatabaseReference ref = FirebaseSafeHelper.getDatabaseReference();
        if (ref != null) {
            ref.child("users").child(userId).child("online").setValue(isOnline);
            if (!isOnline) {
                ref.child("users").child(userId).child("lastSeen").setValue(System.currentTimeMillis());
            }
        }
    }

    public void listenToOnlineStatus(String friendId, ValueEventListener listener) {
        DatabaseReference ref = FirebaseSafeHelper.getDatabaseReference();
        if (ref != null) {
            ref.child("users").child(friendId).addValueEventListener(listener);
        }
    }

    public String createGroupChat(String groupName, List<String> memberIds) {
        DatabaseReference ref = FirebaseSafeHelper.getDatabaseReference();
        if (ref == null) return "local_" + System.currentTimeMillis();
        DatabaseReference groupsRef = ref.child("groups");
        String key = groupsRef.push().getKey();
        if (key != null) {
            java.util.Map<String, Object> groupData = new java.util.HashMap<>();
            groupData.put("id", key);
            groupData.put("name", groupName);
            java.util.Map<String, Boolean> members = new java.util.HashMap<>();
            for (String memberId : memberIds) {
                members.put(memberId, true);
            }
            groupData.put("members", members);
            groupsRef.child(key).setValue(groupData);
            return key;
        }
        return null;
    }

    public void listenForMessages(String currentUserId, String friendId, ChatCallback callback) {
        this.activeCallback = callback;
        this.activeCurrentUserId = currentUserId;
        this.activeFriendId = friendId;

        // Trigger local callback immediately with existing in-memory messages
        triggerLocalCallback();

        if (listener != null) {
            messagesRef.removeEventListener(listener);
        }
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ChatMessage> dbList = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ChatMessage msg = child.getValue(ChatMessage.class);
                    if (msg != null && msg.sender != null && msg.receiver != null) {
                        if (((msg.sender.equals(currentUserId) && msg.receiver.equals(friendId)) || 
                             (msg.sender.equals(friendId) && msg.receiver.equals(currentUserId)))) {
                            dbList.add(msg);
                            if (msg.id != null && msg.receiver.equals(currentUserId) && !"read".equals(msg.status)) {
                                updateMessageStatus(msg.id, "read");
                            }
                        }
                    }
                }
                
                // Update local backup list with database items (avoid duplicates)
                for (ChatMessage dbMsg : dbList) {
                    boolean exists = false;
                    for (int i = 0; i < localBackupList.size(); i++) {
                        ChatMessage localMsg = localBackupList.get(i);
                        if (localMsg != null && localMsg.id != null && dbMsg.id != null) {
                            if (localMsg.id.equals(dbMsg.id) || 
                                (localMsg.id.startsWith("local_") && 
                                 localMsg.message != null && dbMsg.message != null &&
                                 localMsg.message.equals(dbMsg.message) && 
                                 localMsg.sender != null && dbMsg.sender != null &&
                                 localMsg.sender.equals(dbMsg.sender))) {
                                localBackupList.set(i, dbMsg);
                                exists = true;
                                break;
                            }
                        }
                    }
                    if (!exists) {
                        localBackupList.add(dbMsg);
                    }
                }
                
                triggerLocalCallback();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ChatRepository", "Listen cancelled", error.toException());
            }
        };
        messagesRef.addValueEventListener(listener);
    }

    public void listenForGroupMessages(String groupId, ChatCallback callback) {
        this.activeCallback = callback;
        this.activeCurrentUserId = null;
        this.activeFriendId = groupId;
        
        triggerLocalCallback();

        if (listener != null) {
            messagesRef.removeEventListener(listener);
        }
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ChatMessage> dbList = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ChatMessage msg = child.getValue(ChatMessage.class);
                    if (msg != null && groupId.equals(msg.receiver)) {
                        dbList.add(msg);
                    }
                }
                
                // Update local list
                for (ChatMessage dbMsg : dbList) {
                    boolean exists = false;
                    for (int i = 0; i < localBackupList.size(); i++) {
                        if (localBackupList.get(i).id.equals(dbMsg.id) || 
                            (localBackupList.get(i).id.startsWith("local_") && 
                             localBackupList.get(i).message.equals(dbMsg.message) && 
                             localBackupList.get(i).sender.equals(dbMsg.sender))) {
                            localBackupList.set(i, dbMsg);
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        localBackupList.add(dbMsg);
                    }
                }
                
                triggerLocalCallback();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ChatRepository", "Listen cancelled", error.toException());
            }
        };
        messagesRef.addValueEventListener(listener);
    }

    private void triggerLocalCallback() {
        if (activeCallback == null) return;
        List<ChatMessage> list = new ArrayList<>();
        
        if (activeCurrentUserId == null) {
            // Group chat
            for (ChatMessage msg : localBackupList) {
                if (activeFriendId.equals(msg.receiver)) {
                    list.add(msg);
                }
            }
        } else {
            // 1-to-1 chat
            for (ChatMessage msg : localBackupList) {
                if (((msg.sender.equals(activeCurrentUserId) && msg.receiver.equals(activeFriendId)) || 
                     (msg.sender.equals(activeFriendId) && msg.receiver.equals(activeCurrentUserId)))) {
                    list.add(msg);
                }
            }
        }
        
        // Sort by timestamp
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            list.sort((m1, m2) -> Long.compare(m1.timestamp, m2.timestamp));
        }
        
        activeCallback.onMessagesUpdated(list);
    }

    public void stopListening() {
        if (listener != null) {
            messagesRef.removeEventListener(listener);
        }
        activeCallback = null;
    }
}
