package com.example.camera;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SpotlightAdapter extends RecyclerView.Adapter<SpotlightAdapter.VideoViewHolder> {

    private final List<MainActivity.SpotlightItem> items;
    private final Context context;

    public SpotlightAdapter(Context context, List<MainActivity.SpotlightItem> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_spotlight_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        holder.bind(items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull VideoViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        holder.player.play();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull VideoViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        holder.player.pause();
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;
        ExoPlayer player;
        TextView creatorText;
        TextView captionText;
        TextView musicText;
        TextView likesText;
        ImageView likeIcon;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.player_view);
            player = new ExoPlayer.Builder(context).build();
            playerView.setPlayer(player);

            creatorText = itemView.findViewById(R.id.spotlight_creator);
            captionText = itemView.findViewById(R.id.spotlight_caption);
            musicText = itemView.findViewById(R.id.spotlight_music);
            likesText = itemView.findViewById(R.id.spotlight_likes);
            likeIcon = itemView.findViewById(R.id.spotlight_like_icon);
        }

        void bind(MainActivity.SpotlightItem item, int position) {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(item.videoUrl));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setRepeatMode(ExoPlayer.REPEAT_MODE_ALL);

            if (creatorText != null) creatorText.setText(item.creator);
            if (captionText != null) captionText.setText(item.caption);
            if (musicText != null) musicText.setText(item.music);
            if (likesText != null) likesText.setText(item.likes);

            // Liked State click handler local to the adapter page
            if (likeIcon != null) {
                android.content.SharedPreferences prefs = context.getSharedPreferences("snaptake_likes", Context.MODE_PRIVATE);
                final boolean[] isLiked = {prefs.getBoolean("liked_" + position, false)};
                
                // Update UI state initially
                likeIcon.setImageResource(isLiked[0] ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
                likeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                        isLiked[0] ? android.graphics.Color.parseColor("#FFCC00") : android.graphics.Color.WHITE));
                
                likeIcon.setOnClickListener(v -> {
                    isLiked[0] = !isLiked[0];
                    prefs.edit().putBoolean("liked_" + position, isLiked[0]).apply();
                    likeIcon.setImageResource(isLiked[0] ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
                    likeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                            isLiked[0] ? android.graphics.Color.parseColor("#FFCC00") : android.graphics.Color.WHITE));
                    if (likesText != null) {
                        likesText.setText(isLiked[0] ? "2.5k" : item.likes);
                    }
                });
            }
        }
    }
}
