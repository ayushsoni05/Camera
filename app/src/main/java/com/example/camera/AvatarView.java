package com.example.camera;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

public class AvatarView extends AppCompatImageView {

    public AvatarView(Context context) {
        super(context);
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAvatarState(String skinColor, String hairStyle, String hairColor, String outfitColor, String expression) {
        AvatarState state = new AvatarState(skinColor, hairStyle, hairColor, outfitColor, expression);
        setAvatarState(state);
    }

    public void setAvatarState(AvatarState state) {
        if (state == null) return;
        
        // Generate premium DiceBear PNG URL
        String url = AvatarSvgGenerator.generateUrl(state, "png");
        
        // Load with Glide and crop to a beautiful circle
        Glide.with(getContext())
             .load(url)
             .transform(new CircleCrop())
             .placeholder(android.R.drawable.ic_menu_gallery)
             .into(this);
    }
}
