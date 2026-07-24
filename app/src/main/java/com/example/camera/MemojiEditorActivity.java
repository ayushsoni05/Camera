package com.example.camera;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DatabaseReference;
import java.util.ArrayList;
import java.util.List;

public class MemojiEditorActivity extends AppCompatActivity {

    private AvatarView previewAvatar;
    private GridLayout optionsGrid;
    private AvatarState tempState;

    // Selections lists
    private final String[] skinColors = {"#f8d25c", "#edb98a", "#d2b48c", "#ffdbac"};
    private final String[] hairStyles = {"shortHair", "longHairCurly", "longHairStraight", "noHair", "hijab", "turban"};
    private final String[] hairColors = {"#2c1b18", "#b58143", "#724124", "#a55728", "#ecdcbf"};
    private final String[] eyeStyles = {"default", "happy", "hearts", "wink", "winkWacky", "side", "squint"};
    private final String[] eyebrowsStyles = {"default", "angry", "flatNatural", "raisedExcited"};
    private final String[] mouthStyles = {"default", "smile", "eating", "grimace", "sad", "serious", "tongue"};
    private final String[] accessoriesList = {"blank", "prescription01", "prescription02", "round", "sunglasses"};
    private final String[] facialHairList = {"blank", "beardLight", "beardMedium", "moustacheFancy"};

    private enum Tab { SKIN, HAIR_STYLE, HAIR_COLOR, EYES, EYEBROWS, MOUTH, ACCESSORIES, FACIAL_HAIR }
    private Tab activeTab = Tab.SKIN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.memoji_editor);

        previewAvatar = findViewById(R.id.memoji_preview_avatar);
        optionsGrid = findViewById(R.id.options_grid);

        // Load selections
        SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        String skin = prefs.getString("avatar_skin", "#FFE0BD");
        String top = prefs.getString("avatar_hair_style", "short");
        String hairColor = prefs.getString("avatar_hair_color", "#090806");
        String outfitColor = prefs.getString("avatar_outfit", "#6366F1");
        String expr = prefs.getString("avatar_expr", "happy");
        String eyebrows = prefs.getString("avatar_eyebrows", "default");
        String mouth = prefs.getString("avatar_mouth", "default");
        String accs = prefs.getString("avatar_accessories", "blank");
        String beard = prefs.getString("avatar_facial_hair", "blank");

        tempState = new AvatarState(skin, top, hairColor, outfitColor, expr, eyebrows, mouth, accs, beard);
        if (previewAvatar != null) {
            previewAvatar.setAvatarState(tempState);
        }

        // Configure Category Tabs
        setupTabButtons();

        // Populate default skin options
        populateOptions();

        // Configure Save/Cancel Actions
        View backBtn = findViewById(R.id.editor_btn_back);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        View cancelBtn = findViewById(R.id.editor_btn_cancel);
        if (cancelBtn != null) cancelBtn.setOnClickListener(v -> finish());

        View saveBtn = findViewById(R.id.editor_btn_save);
        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                saveMemoji();
                finish();
            });
        }
    }

    private void setupTabButtons() {
        MaterialButton tabSkin = findViewById(R.id.tab_skin);
        MaterialButton tabHair = findViewById(R.id.tab_hair);
        MaterialButton tabHairColor = findViewById(R.id.tab_hair_color);
        MaterialButton tabEyes = findViewById(R.id.tab_eyes);
        MaterialButton tabEyebrows = findViewById(R.id.tab_eyebrows);
        MaterialButton tabMouth = findViewById(R.id.tab_mouth);
        MaterialButton tabAccs = findViewById(R.id.tab_accessories);
        MaterialButton tabBeard = findViewById(R.id.tab_facial_hair);

        List<MaterialButton> tabs = new ArrayList<>();
        if (tabSkin != null) tabs.add(tabSkin);
        if (tabHair != null) tabs.add(tabHair);
        if (tabHairColor != null) tabs.add(tabHairColor);
        if (tabEyes != null) tabs.add(tabEyes);
        if (tabEyebrows != null) tabs.add(tabEyebrows);
        if (tabMouth != null) tabs.add(tabMouth);
        if (tabAccs != null) tabs.add(tabAccs);
        if (tabBeard != null) tabs.add(tabBeard);

        for (MaterialButton tab : tabs) {
            tab.setOnClickListener(v -> {
                // Set color highlights
                for (MaterialButton t : tabs) {
                    t.setTextColor(Color.WHITE);
                }
                tab.setTextColor(Color.parseColor("#FFFC00")); // Highlight Snap Yellow

                if (tab == tabSkin) activeTab = Tab.SKIN;
                else if (tab == tabHair) activeTab = Tab.HAIR_STYLE;
                else if (tab == tabHairColor) activeTab = Tab.HAIR_COLOR;
                else if (tab == tabEyes) activeTab = Tab.EYES;
                else if (tab == tabEyebrows) activeTab = Tab.EYEBROWS;
                else if (tab == tabMouth) activeTab = Tab.MOUTH;
                else if (tab == tabAccs) activeTab = Tab.ACCESSORIES;
                else if (tab == tabBeard) activeTab = Tab.FACIAL_HAIR;

                populateOptions();
            });
        }
    }

    private void populateOptions() {
        if (optionsGrid == null) return;
        optionsGrid.removeAllViews();

        String[] currentOptions = {};
        switch (activeTab) {
            case SKIN: currentOptions = skinColors; break;
            case HAIR_STYLE: currentOptions = hairStyles; break;
            case HAIR_COLOR: currentOptions = hairColors; break;
            case EYES: currentOptions = eyeStyles; break;
            case EYEBROWS: currentOptions = eyebrowsStyles; break;
            case MOUTH: currentOptions = mouthStyles; break;
            case ACCESSORIES: currentOptions = accessoriesList; break;
            case FACIAL_HAIR: currentOptions = facialHairList; break;
        }

        for (String opt : currentOptions) {
            MaterialButton btn = new MaterialButton(this);
            btn.setTextSize(11);
            btn.setCornerRadius(16);
            btn.setPadding(8, 8, 8, 8);
            
            // Visual grid spacing
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(10, 10, 10, 10);
            btn.setLayoutParams(params);

            if (activeTab == Tab.SKIN || activeTab == Tab.HAIR_COLOR) {
                // Color selection button
                btn.setText("");
                btn.setBackgroundColor(Color.parseColor(opt));
            } else {
                // Name selection button
                btn.setText(opt);
                btn.setTextColor(Color.WHITE);
                btn.setBackgroundColor(Color.parseColor("#40FFFFFF"));
            }

            btn.setOnClickListener(v -> {
                switch (activeTab) {
                    case SKIN: tempState.skinColor = opt; break;
                    case HAIR_STYLE: tempState.hairStyle = opt; break;
                    case HAIR_COLOR: tempState.hairColor = opt; break;
                    case EYES: tempState.expression = opt; break;
                    case EYEBROWS: tempState.eyebrowsStyle = opt; break;
                    case MOUTH: tempState.mouthStyle = opt; break;
                    case ACCESSORIES: tempState.accessories = opt; break;
                    case FACIAL_HAIR: tempState.facialHair = opt; break;
                }
                // Live preview refresh
                if (previewAvatar != null) {
                    previewAvatar.setAvatarState(tempState);
                }
            });

            optionsGrid.addView(btn);
        }
    }

    private void saveMemoji() {
        SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        prefs.edit()
            .putString("avatar_skin", tempState.skinColor)
            .putString("avatar_hair_style", tempState.hairStyle)
            .putString("avatar_hair_color", tempState.hairColor)
            .putString("avatar_expr", tempState.expression)
            .putString("avatar_eyebrows", tempState.eyebrowsStyle)
            .putString("avatar_mouth", tempState.mouthStyle)
            .putString("avatar_accessories", tempState.accessories)
            .putString("avatar_facial_hair", tempState.facialHair)
            .apply();

        // Update online Firebase references for user's map marker
        String userId = prefs.getString("userId", "currentUser");
        DatabaseReference ref = FirebaseSafeHelper.getDatabaseReference();
        if (ref != null) {
            ref.child("users").child(userId).child("avatar").setValue(tempState)
                .addOnSuccessListener(aVoid -> Toast.makeText(MemojiEditorActivity.this, "Memoji Synced Online! 🌐", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(MemojiEditorActivity.this, "Local Memoji saved.", Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(this, "Local Memoji saved! 🎨", Toast.LENGTH_SHORT).show();
        }
    }
}
