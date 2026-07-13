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
        ImageView commentIcon;
        TextView commentCountText;
        ImageView shareIcon;
        ImageView saveIcon;
        TextView followBtn;
        ImageView musicIcon;

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
            
            commentIcon = itemView.findViewById(R.id.spotlight_comment_btn);
            commentCountText = itemView.findViewById(R.id.spotlight_comment_count);
            shareIcon = itemView.findViewById(R.id.spotlight_share_btn);
            saveIcon = itemView.findViewById(R.id.spotlight_save_btn);
            followBtn = itemView.findViewById(R.id.spotlight_follow_btn);
            musicIcon = itemView.findViewById(R.id.spotlight_music_icon);
        }

        void bind(MainActivity.SpotlightItem item, int position) {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(item.videoUrl));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setRepeatMode(ExoPlayer.REPEAT_MODE_ALL);

            if (creatorText != null) {
                creatorText.setText(item.creator);
                creatorText.setOnClickListener(v -> {
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).showNotification(item.creator + " 👤", "Subscribers: 125k\nClick Follow to subscribe!", "👻");
                    }
                });
            }
            if (captionText != null) captionText.setText(item.caption);
            if (musicText != null) musicText.setText(item.music);
            if (likesText != null) likesText.setText(item.likes);

            // Vinyl Rotation Animation
            if (musicIcon != null) {
                android.view.animation.RotateAnimation rotate = new android.view.animation.RotateAnimation(
                        0, 360,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
                );
                rotate.setDuration(4000);
                rotate.setRepeatCount(android.view.animation.Animation.INFINITE);
                rotate.setInterpolator(new android.view.animation.LinearInterpolator());
                musicIcon.startAnimation(rotate);
            }

            // Follow button toggling
            if (followBtn != null) {
                final boolean[] isFollowing = {false};
                followBtn.setOnClickListener(v -> {
                    isFollowing[0] = !isFollowing[0];
                    if (isFollowing[0]) {
                        followBtn.setText("Following");
                        followBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#807A7A7A")));
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).showToast("Subscribed to " + item.creator);
                        }
                    } else {
                        followBtn.setText("Follow");
                        followBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#40FFFFFF")));
                    }
                });
            }

            // Like Star Actions
            if (likeIcon != null) {
                android.content.SharedPreferences prefs = context.getSharedPreferences("snaptake_likes", Context.MODE_PRIVATE);
                final boolean[] isLiked = {prefs.getBoolean("liked_" + position, false)};
                
                likeIcon.setImageResource(isLiked[0] ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
                likeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                        isLiked[0] ? android.graphics.Color.parseColor("#FFFC00") : android.graphics.Color.WHITE));
                
                likeIcon.setOnClickListener(v -> {
                    isLiked[0] = !isLiked[0];
                    prefs.edit().putBoolean("liked_" + position, isLiked[0]).apply();
                    likeIcon.setImageResource(isLiked[0] ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
                    likeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                            isLiked[0] ? android.graphics.Color.parseColor("#FFFC00") : android.graphics.Color.WHITE));
                    if (likesText != null) {
                        likesText.setText(isLiked[0] ? "2.5k" : item.likes);
                    }
                    
                    // Heartbeat Overshoot Animation
                    likeIcon.setScaleX(0.7f);
                    likeIcon.setScaleY(0.7f);
                    likeIcon.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(250)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                            .start();
                });
            }

            // Comment Drawer Action
            if (commentIcon != null) {
                commentIcon.setOnClickListener(v -> {
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).openSpotlightComments(position, commentCountText);
                    }
                });
            }

            // Share Drawer Action
            if (shareIcon != null) {
                shareIcon.setOnClickListener(v -> {
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).openSpotlightShare(position);
                    }
                });
            }

            // Save Download Action
            if (saveIcon != null) {
                saveIcon.setOnClickListener(v -> {
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).saveSpotlightVideo(item.videoUrl);
                    }
                });
            }
        }
    }
}
