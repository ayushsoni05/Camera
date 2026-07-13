package com.example.camera;

import android.content.Context;

public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;
    
    private final MainActivity activity;
    private final String message;
    
    private Toast(MainActivity activity, String message) {
        this.activity = activity;
        this.message = message;
    }
    
    public static Toast makeText(Context context, CharSequence text, int duration) {
        MainActivity act = null;
        if (context instanceof MainActivity) {
            act = (MainActivity) context;
        }
        return new Toast(act, text != null ? text.toString() : "");
    }
    
    public void show() {
        if (activity != null) {
            SnapAlertHelper.showToast(activity, message);
        }
    }
}
