package com.example.camera;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PresetRepository {

    private static final String PREFS_NAME = "LensPresets";
    private final SharedPreferences prefs;

    public static class LensPreset {
        public String name;
        public float exposure;  // -2.0 to 2.0
        public float contrast;  // 0.5 to 2.0
        public float tint;      // -1.0 to 1.0
        public float vignette;  // 0.0 to 1.0

        public LensPreset(String name, float exposure, float contrast, float tint, float vignette) {
            this.name = name;
            this.exposure = exposure;
            this.contrast = contrast;
            this.tint = tint;
            this.vignette = vignette;
        }
    }

    public PresetRepository(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Pre-populate with default artistic presets
        if (getAllPresets().isEmpty()) {
            savePreset(new LensPreset("CineGold", 0.2f, 1.3f, 0.4f, 0.5f));
            savePreset(new LensPreset("NoirDark", -0.5f, 1.6f, -0.8f, 0.7f));
            savePreset(new LensPreset("AquaVivid", 0.1f, 1.1f, -0.5f, 0.2f));
        }
    }

    public void savePreset(LensPreset preset) {
        SharedPreferences.Editor editor = prefs.edit();
        String val = preset.exposure + "," + preset.contrast + "," + preset.tint + "," + preset.vignette;
        editor.putString(preset.name, val);
        editor.apply();
    }

    public List<LensPreset> getAllPresets() {
        List<LensPreset> list = new ArrayList<>();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String name = entry.getKey();
            String val = entry.getValue().toString();
            String[] parts = val.split(",");
            if (parts.length == 4) {
                try {
                    float exp = Float.parseFloat(parts[0]);
                    float con = Float.parseFloat(parts[1]);
                    float tint = Float.parseFloat(parts[2]);
                    float vig = Float.parseFloat(parts[3]);
                    list.add(new LensPreset(name, exp, con, tint, vig));
                } catch (NumberFormatException e) {
                    // Ignore malformed entries
                }
            }
        }
        return list;
    }

    public LensPreset getPreset(String name) {
        String val = prefs.getString(name, null);
        if (val == null) return null;
        String[] parts = val.split(",");
        if (parts.length == 4) {
            try {
                return new LensPreset(name,
                    Float.parseFloat(parts[0]),
                    Float.parseFloat(parts[1]),
                    Float.parseFloat(parts[2]),
                    Float.parseFloat(parts[3])
                );
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
