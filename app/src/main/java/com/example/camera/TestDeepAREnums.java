package com.example.camera;
import ai.deepar.ar.DeepARImageFormat;
import android.util.Log;
public class TestDeepAREnums {
    public static void printEnums() {
        for (DeepARImageFormat f : DeepARImageFormat.values()) {
            Log.d("DeepAREnums", "Format: " + f.name());
        }
    }
}
