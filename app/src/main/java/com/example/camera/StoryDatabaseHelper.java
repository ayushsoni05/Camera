package com.example.camera;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class StoryDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "StoryDatabaseHelper";
    private static final String DATABASE_NAME = "stories.db";
    private static final int DATABASE_VERSION = 2;

    // Table names
    private static final String TABLE_STORIES = "stories";
    private static final String TABLE_REPLIES = "story_replies";

    // Common columns
    private static final String KEY_ID = "id";
    private static final String KEY_TIMESTAMP = "timestamp";

    // Stories Columns
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_MEDIA_PATH = "mediaPath";
    private static final String KEY_IS_VIDEO = "isVideo";
    private static final String KEY_EXPIRES_AT = "expiresAt";
    private static final String KEY_PRIVACY = "privacy";
    private static final String KEY_MUSIC_TITLE = "musicTitle";
    private static final String KEY_STICKERS_JSON = "stickersJson";
    private static final String KEY_TEXTS_JSON = "textsJson";
    private static final String KEY_DRAWINGS_JSON = "drawingsJson";
    private static final String KEY_MENTIONS_JSON = "mentionsJson";
    private static final String KEY_VIEW_COUNT = "viewCount";
    private static final String KEY_SCREENSHOT_COUNT = "screenshotCount";
    private static final String KEY_VIEWERS_CSV = "viewersCsv";
    private static final String KEY_REACTIONS_CSV = "reactionsCsv";

    // Replies Columns
    private static final String KEY_REPLY_ID = "replyId";
    private static final String KEY_STORY_ID = "storyId";
    private static final String KEY_REPLIER = "replierName";
    private static final String KEY_REPLY_TEXT = "replyText";

    public StoryDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_STORIES_TABLE = "CREATE TABLE " + TABLE_STORIES + "("
                + KEY_ID + " TEXT PRIMARY KEY,"
                + KEY_USER_ID + " TEXT,"
                + KEY_USERNAME + " TEXT,"
                + KEY_MEDIA_PATH + " TEXT,"
                + KEY_IS_VIDEO + " INTEGER,"
                + KEY_TIMESTAMP + " INTEGER,"
                + KEY_EXPIRES_AT + " INTEGER,"
                + KEY_PRIVACY + " TEXT,"
                + KEY_MUSIC_TITLE + " TEXT,"
                + KEY_STICKERS_JSON + " TEXT,"
                + KEY_TEXTS_JSON + " TEXT,"
                + KEY_DRAWINGS_JSON + " TEXT,"
                + KEY_MENTIONS_JSON + " TEXT,"
                + KEY_VIEW_COUNT + " INTEGER DEFAULT 0,"
                + KEY_SCREENSHOT_COUNT + " INTEGER DEFAULT 0,"
                + KEY_VIEWERS_CSV + " TEXT DEFAULT '',"
                + KEY_REACTIONS_CSV + " TEXT DEFAULT ''"
                + ")";
        db.execSQL(CREATE_STORIES_TABLE);

        String CREATE_REPLIES_TABLE = "CREATE TABLE " + TABLE_REPLIES + "("
                + KEY_REPLY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_STORY_ID + " TEXT,"
                + KEY_REPLIER + " TEXT,"
                + KEY_REPLY_TEXT + " TEXT,"
                + KEY_TIMESTAMP + " INTEGER"
                + ")";
        db.execSQL(CREATE_REPLIES_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STORIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REPLIES);
        onCreate(db);
    }

    public synchronized void addStory(StoryItem item) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_ID, item.id);
        values.put(KEY_USER_ID, item.userId);
        values.put(KEY_USERNAME, item.username);
        values.put(KEY_MEDIA_PATH, item.mediaPath);
        values.put(KEY_IS_VIDEO, item.isVideo ? 1 : 0);
        values.put(KEY_TIMESTAMP, item.timestamp);
        values.put(KEY_EXPIRES_AT, item.expiresAt);
        values.put(KEY_PRIVACY, item.privacy);
        values.put(KEY_MUSIC_TITLE, item.musicTitle);
        values.put(KEY_STICKERS_JSON, item.stickersJson);
        values.put(KEY_TEXTS_JSON, item.textsJson);
        values.put(KEY_DRAWINGS_JSON, item.drawingsJson);
        values.put(KEY_MENTIONS_JSON, item.mentionsJson);
        values.put(KEY_VIEW_COUNT, item.viewCount);
        values.put(KEY_SCREENSHOT_COUNT, item.screenshotCount);
        values.put(KEY_VIEWERS_CSV, item.viewersCsv);
        values.put(KEY_REACTIONS_CSV, item.reactionsCsv);

        db.insertWithOnConflict(TABLE_STORIES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized List<StoryItem> getActiveStories(String forUserId) {
        List<StoryItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        long now = System.currentTimeMillis();
        
        String query = "SELECT * FROM " + TABLE_STORIES + " WHERE " + KEY_EXPIRES_AT + " > ? ORDER BY " + KEY_TIMESTAMP + " ASC";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(now)});

        try {
            if (cursor.moveToFirst()) {
                do {
                    StoryItem item = cursorToStoryItem(cursor);
                    list.add(item);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public synchronized List<StoryItem> getStoriesByUser(String userId) {
        List<StoryItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        long now = System.currentTimeMillis();

        String query = "SELECT * FROM " + TABLE_STORIES + " WHERE " + KEY_USER_ID + " = ? AND " + KEY_EXPIRES_AT + " > ? ORDER BY " + KEY_TIMESTAMP + " ASC";
        Cursor cursor = db.rawQuery(query, new String[]{userId, String.valueOf(now)});

        try {
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursorToStoryItem(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public synchronized void deleteExpiredStories() {
        SQLiteDatabase db = this.getWritableDatabase();
        long now = System.currentTimeMillis();
        int deleted = db.delete(TABLE_STORIES, KEY_EXPIRES_AT + " <= ?", new String[]{String.valueOf(now)});
        if (deleted > 0) {
            Log.d(TAG, "Deleted " + deleted + " expired stories.");
        }
    }

    public synchronized void incrementView(String storyId, String viewerUsername) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            String query = "SELECT " + KEY_VIEW_COUNT + ", " + KEY_VIEWERS_CSV + " FROM " + TABLE_STORIES + " WHERE " + KEY_ID + " = ?";
            Cursor cursor = db.rawQuery(query, new String[]{storyId});
            if (cursor.moveToFirst()) {
                int viewCount = cursor.getInt(0);
                String csv = cursor.getString(1);
                cursor.close();

                boolean alreadyViewed = false;
                if (csv != null && !csv.isEmpty()) {
                    String[] viewers = csv.split(",");
                    for (String v : viewers) {
                        if (v.trim().equalsIgnoreCase(viewerUsername)) {
                            alreadyViewed = true;
                            break;
                        }
                    }
                }

                ContentValues values = new ContentValues();
                if (!alreadyViewed) {
                    viewCount++;
                    String newCsv = (csv == null || csv.isEmpty()) ? viewerUsername : csv + "," + viewerUsername;
                    values.put(KEY_VIEW_COUNT, viewCount);
                    values.put(KEY_VIEWERS_CSV, newCsv);
                } else {
                    values.put(KEY_VIEW_COUNT, viewCount); // don't increment
                }

                db.update(TABLE_STORIES, values, KEY_ID + " = ?", new String[]{storyId});
            } else {
                cursor.close();
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error incrementing view", e);
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void incrementScreenshot(String storyId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_STORIES + " SET " + KEY_SCREENSHOT_COUNT + " = " + KEY_SCREENSHOT_COUNT + " + 1 WHERE " + KEY_ID + " = '" + storyId + "'");
    }

    public synchronized void addReaction(String storyId, String emoji) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            String query = "SELECT " + KEY_REACTIONS_CSV + " FROM " + TABLE_STORIES + " WHERE " + KEY_ID + " = ?";
            Cursor cursor = db.rawQuery(query, new String[]{storyId});
            if (cursor.moveToFirst()) {
                String csv = cursor.getString(0);
                cursor.close();

                String newCsv = (csv == null || csv.isEmpty()) ? emoji : csv + "," + emoji;
                ContentValues values = new ContentValues();
                values.put(KEY_REACTIONS_CSV, newCsv);

                db.update(TABLE_STORIES, values, KEY_ID + " = ?", new String[]{storyId});
            } else {
                cursor.close();
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error adding reaction", e);
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void addReply(String storyId, String replier, String replyText) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_STORY_ID, storyId);
        values.put(KEY_REPLIER, replier);
        values.put(KEY_REPLY_TEXT, replyText);
        values.put(KEY_TIMESTAMP, System.currentTimeMillis());
        db.insert(TABLE_REPLIES, null, values);
    }

    public synchronized List<StoryReply> getReplies(String storyId) {
        List<StoryReply> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_REPLIES + " WHERE " + KEY_STORY_ID + " = ? ORDER BY " + KEY_TIMESTAMP + " ASC", new String[]{storyId});
        try {
            if (cursor.moveToFirst()) {
                do {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_REPLY_ID));
                    String sId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_STORY_ID));
                    String replier = cursor.getString(cursor.getColumnIndexOrThrow(KEY_REPLIER));
                    String text = cursor.getString(cursor.getColumnIndexOrThrow(KEY_REPLY_TEXT));
                    long ts = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP));
                    list.add(new StoryReply(id, sId, replier, text, ts));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    private StoryItem cursorToStoryItem(Cursor cursor) {
        StoryItem item = new StoryItem();
        item.id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID));
        item.userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID));
        item.username = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USERNAME));
        item.mediaPath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_MEDIA_PATH));
        item.isVideo = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_VIDEO)) == 1;
        item.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP));
        item.expiresAt = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_EXPIRES_AT));
        item.privacy = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PRIVACY));
        item.musicTitle = cursor.getString(cursor.getColumnIndexOrThrow(KEY_MUSIC_TITLE));
        item.stickersJson = cursor.getString(cursor.getColumnIndexOrThrow(KEY_STICKERS_JSON));
        item.textsJson = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TEXTS_JSON));
        item.drawingsJson = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DRAWINGS_JSON));
        item.mentionsJson = cursor.getString(cursor.getColumnIndexOrThrow(KEY_MENTIONS_JSON));
        item.viewCount = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_VIEW_COUNT));
        item.screenshotCount = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_SCREENSHOT_COUNT));
        item.viewersCsv = cursor.getString(cursor.getColumnIndexOrThrow(KEY_VIEWERS_CSV));
        item.reactionsCsv = cursor.getString(cursor.getColumnIndexOrThrow(KEY_REACTIONS_CSV));
        return item;
    }

    public static class StoryReply {
        public int replyId;
        public String storyId;
        public String replier;
        public String replyText;
        public long timestamp;

        public StoryReply(int replyId, String storyId, String replier, String replyText, long timestamp) {
            this.replyId = replyId;
            this.storyId = storyId;
            this.replier = replier;
            this.replyText = replyText;
            this.timestamp = timestamp;
        }
    }
}
