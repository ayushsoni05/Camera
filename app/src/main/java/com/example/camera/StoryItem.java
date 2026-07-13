package com.example.camera;

public class StoryItem {
    public String id;
    public String userId;       // "user" or friend name (e.g. "Alex")
    public String username;     // Display name
    public String mediaPath;    // Local path to photo or video file
    public boolean isVideo;
    public long timestamp;      // Post time in milliseconds
    public long expiresAt;      // Expiration time in milliseconds
    public String privacy;      // "EVERYONE", "FRIENDS"
    public String musicTitle;   // Music track name, if any
    
    // Serialized overlays
    public String stickersJson; // Emojis / stickers (positions, scales)
    public String textsJson;    // Text annotations
    public String drawingsJson; // Drawing path coordinates
    public String mentionsJson; // Mentioned usernames
    
    // Analytics & Interactions
    public int viewCount;
    public int screenshotCount;
    public String viewersCsv;   // Comma separated viewer usernames
    public String reactionsCsv; // Comma separated emoji reactions

    public StoryItem() {}

    public StoryItem(String id, String userId, String username, String mediaPath, boolean isVideo,
                     long timestamp, long expiresAt, String privacy, String musicTitle,
                     String stickersJson, String textsJson, String drawingsJson, String mentionsJson,
                     int viewCount, int screenshotCount, String viewersCsv, String reactionsCsv) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.mediaPath = mediaPath;
        this.isVideo = isVideo;
        this.timestamp = timestamp;
        this.expiresAt = expiresAt;
        this.privacy = privacy;
        this.musicTitle = musicTitle;
        this.stickersJson = stickersJson;
        this.textsJson = textsJson;
        this.drawingsJson = drawingsJson;
        this.mentionsJson = mentionsJson;
        this.viewCount = viewCount;
        this.screenshotCount = screenshotCount;
        this.viewersCsv = viewersCsv;
        this.reactionsCsv = reactionsCsv;
    }
}
