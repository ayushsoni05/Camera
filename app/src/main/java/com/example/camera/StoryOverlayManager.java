package com.example.camera;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StoryOverlayManager {

    public static class OverlayText {
        public String text;
        public int color;
        public float scaleX;
        public float scaleY;
        public float translationX;
        public float translationY;
        public float rotation;
    }

    public static class OverlaySticker {
        public String emoji;
        public float scaleX;
        public float scaleY;
        public float translationX;
        public float translationY;
        public float rotation;
    }

    public static String serializeTexts(ViewGroup container) {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child instanceof TextView && !"sticker".equals(child.getTag())) {
                    TextView tv = (TextView) child;
                    JSONObject obj = new JSONObject();
                    obj.put("text", tv.getText().toString());
                    obj.put("color", tv.getCurrentTextColor());
                    obj.put("scaleX", (double) tv.getScaleX());
                    obj.put("scaleY", (double) tv.getScaleY());
                    obj.put("translationX", (double) tv.getTranslationX());
                    obj.put("translationY", (double) tv.getTranslationY());
                    obj.put("rotation", (double) tv.getRotation());
                    arr.put(obj);
                }
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    public static String serializeStickers(ViewGroup container) {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child instanceof TextView && "sticker".equals(child.getTag())) {
                    TextView tv = (TextView) child;
                    JSONObject obj = new JSONObject();
                    obj.put("emoji", tv.getText().toString());
                    obj.put("scaleX", (double) tv.getScaleX());
                    obj.put("scaleY", (double) tv.getScaleY());
                    obj.put("translationX", (double) tv.getTranslationX());
                    obj.put("translationY", (double) tv.getTranslationY());
                    obj.put("rotation", (double) tv.getRotation());
                    arr.put(obj);
                }
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    public static void deserializeOverlays(Context context, ViewGroup container, String textsJson, String stickersJson, boolean interactive) {
        container.removeAllViews();
        
        // 1. Recreate Texts
        if (textsJson != null && !textsJson.isEmpty() && !textsJson.equals("[]")) {
            try {
                JSONArray arr = new JSONArray(textsJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String text = obj.getString("text");
                    int color = obj.getInt("color");
                    float scaleX = (float) obj.getDouble("scaleX");
                    float scaleY = (float) obj.getDouble("scaleY");
                    float translationX = (float) obj.getDouble("translationX");
                    float translationY = (float) obj.getDouble("translationY");
                    float rotation = (float) obj.getDouble("rotation");

                    TextView tv = new TextView(context);
                    tv.setText(text);
                    tv.setTextColor(color);
                    tv.setTextSize(26);
                    tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    tv.setShadowLayer(4f, 2f, 2f, Color.BLACK);
                    
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = android.view.Gravity.CENTER;
                    tv.setLayoutParams(lp);

                    tv.setScaleX(scaleX);
                    tv.setScaleY(scaleY);
                    tv.setTranslationX(translationX);
                    tv.setTranslationY(translationY);
                    tv.setRotation(rotation);

                    if (interactive) {
                        tv.setOnTouchListener(new MultiTouchListener());
                    }
                    container.addView(tv);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. Recreate Stickers
        if (stickersJson != null && !stickersJson.isEmpty() && !stickersJson.equals("[]")) {
            try {
                JSONArray arr = new JSONArray(stickersJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String emoji = obj.getString("emoji");
                    float scaleX = (float) obj.getDouble("scaleX");
                    float scaleY = (float) obj.getDouble("scaleY");
                    float translationX = (float) obj.getDouble("translationX");
                    float translationY = (float) obj.getDouble("translationY");
                    float rotation = (float) obj.getDouble("rotation");

                    TextView tv = new TextView(context);
                    tv.setText(emoji);
                    tv.setTextSize(64);
                    tv.setTag("sticker");
                    
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = android.view.Gravity.CENTER;
                    tv.setLayoutParams(lp);

                    tv.setScaleX(scaleX);
                    tv.setScaleY(scaleY);
                    tv.setTranslationX(translationX);
                    tv.setTranslationY(translationY);
                    tv.setRotation(rotation);

                    if (interactive) {
                        tv.setOnTouchListener(new MultiTouchListener());
                    }
                    container.addView(tv);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
