package com.example.camera;

import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.os.Handler;
import android.os.Looper;

public class SnapAlertHelper {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Runnable toastRunnable;
    private static Runnable notificationRunnable;

    public static void showToast(MainActivity activity, String message) {
        if (activity == null) return;
        
        View toastContainer = activity.findViewById(R.id.snap_toast_container);
        TextView toastText = activity.findViewById(R.id.snap_toast_text);
        if (toastContainer == null || toastText == null) return;

        toastText.setText(message);
        
        mainHandler.removeCallbacks(toastRunnable);
        
        toastContainer.setVisibility(View.VISIBLE);
        toastContainer.setAlpha(0f);
        toastContainer.setScaleX(0.85f);
        toastContainer.setScaleY(0.85f);
        
        toastContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();

        toastRunnable = () -> toastContainer.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(200)
                .withEndAction(() -> toastContainer.setVisibility(View.GONE))
                .start();
                
        mainHandler.postDelayed(toastRunnable, 2500);
    }

    public static void showNotification(MainActivity activity, String title, String body, String emoji) {
        if (activity == null) return;

        View notifBar = activity.findViewById(R.id.snap_notification_bar);
        TextView titleTv = activity.findViewById(R.id.snap_notification_title);
        TextView bodyTv = activity.findViewById(R.id.snap_notification_body);
        TextView emojiTv = activity.findViewById(R.id.snap_notification_emoji);
        if (notifBar == null || titleTv == null || bodyTv == null) return;

        titleTv.setText(title);
        bodyTv.setText(body);
        if (emojiTv != null && emoji != null) {
            emojiTv.setText(emoji);
        }

        mainHandler.removeCallbacks(notificationRunnable);

        notifBar.setVisibility(View.VISIBLE);
        notifBar.setTranslationY(-300f);
        notifBar.setAlpha(0.8f);

        notifBar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(350)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        notificationRunnable = () -> notifBar.animate()
                .translationY(-300f)
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> notifBar.setVisibility(View.GONE))
                .start();

        mainHandler.postDelayed(notificationRunnable, 4000);
    }

    public static void showDialog(MainActivity activity, String title, String message, 
                                  String posText, Runnable onPosClick, 
                                  String negText, Runnable onNegClick) {
        if (activity == null) return;

        View dialogOverlay = activity.findViewById(R.id.snap_dialog_overlay);
        TextView titleTv = activity.findViewById(R.id.snap_dialog_title);
        TextView messageTv = activity.findViewById(R.id.snap_dialog_message);
        Button btnPos = activity.findViewById(R.id.snap_dialog_btn_pos);
        Button btnNeg = activity.findViewById(R.id.snap_dialog_btn_neg);
        
        if (dialogOverlay == null || titleTv == null || messageTv == null || btnPos == null || btnNeg == null) return;

        View dialogCard = (View) titleTv.getParent();

        titleTv.setText(title);
        messageTv.setText(message);
        
        btnPos.setText(posText != null ? posText : "Confirm");
        btnPos.setOnClickListener(v -> {
            dialogOverlay.setVisibility(View.GONE);
            if (onPosClick != null) onPosClick.run();
        });

        if (negText != null) {
            btnNeg.setVisibility(View.VISIBLE);
            btnNeg.setText(negText);
            btnNeg.setOnClickListener(v -> {
                dialogOverlay.setVisibility(View.GONE);
                if (onNegClick != null) onNegClick.run();
            });
        } else {
            btnNeg.setVisibility(View.GONE);
        }

        dialogOverlay.setVisibility(View.VISIBLE);
        dialogOverlay.setAlpha(0f);
        dialogOverlay.animate().alpha(1f).setDuration(200).start();

        if (dialogCard != null) {
            dialogCard.setScaleX(0.85f);
            dialogCard.setScaleY(0.85f);
            dialogCard.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();
        }
    }
}
