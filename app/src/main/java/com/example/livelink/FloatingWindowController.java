package com.example.livelink;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

final class FloatingWindowController {
    private final Activity activity;
    private final Handler mainHandler;
    private final ExoPlayer player;
    private final PlayerView pagePlayerView;
    private final Runnable beforeShow;
    private final Runnable onReturnToFullscreen;
    private final Runnable onAttachmentChanged;

    private WindowManager windowManager;
    private View floatingWindowView;
    private PlayerView floatingPlayerView;
    private WindowManager.LayoutParams windowParams;

    FloatingWindowController(
            Activity activity,
            Handler mainHandler,
            ExoPlayer player,
            PlayerView pagePlayerView,
            Runnable beforeShow,
            Runnable onReturnToFullscreen,
            Runnable onAttachmentChanged) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.player = player;
        this.pagePlayerView = pagePlayerView;
        this.beforeShow = beforeShow;
        this.onReturnToFullscreen = onReturnToFullscreen;
        this.onAttachmentChanged = onAttachmentChanged;
    }

    boolean isShowing() {
        return floatingWindowView != null;
    }

    void show() {
        if (isShowing() || player == null || !Settings.canDrawOverlays(activity)) {
            return;
        }
        if (beforeShow != null) {
            beforeShow.run();
        }
        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }

        int displayWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int displayHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int defaultWidth = Math.min(displayWidth - dp(36), dp(360));
        int defaultHeight = Math.round(defaultWidth * 9f / 16f) + dp(40);
        int width = clamp(AppPreferences.loadFloatingWidth(activity, defaultWidth), dp(220), displayWidth - dp(24));
        int height = clamp(AppPreferences.loadFloatingHeight(activity, defaultHeight), dp(160), displayHeight - dp(80));
        int x = clamp(AppPreferences.loadFloatingX(activity, dp(12)), 0, Math.max(0, displayWidth - width));
        int y = clamp(AppPreferences.loadFloatingY(activity, dp(88)), 0, Math.max(0, displayHeight - height));

        windowParams = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.START | Gravity.TOP;
        windowParams.x = x;
        windowParams.y = y;

        floatingWindowView = buildView();
        attachPlayer();
        windowManager.addView(floatingWindowView, windowParams);
    }

    void hide(boolean releaseOnly) {
        if (!isShowing()) {
            return;
        }
        saveBounds();
        if (!releaseOnly) {
            restorePlayerToPage();
        } else if (floatingPlayerView != null) {
            floatingPlayerView.setPlayer(null);
        }
        if (windowManager != null) {
            try {
                windowManager.removeView(floatingWindowView);
            } catch (IllegalArgumentException ignored) {
            }
        }
        floatingWindowView = null;
        floatingPlayerView = null;
        windowParams = null;
    }

    void setKeepScreenOn(boolean keepOn) {
        if (floatingPlayerView != null) {
            floatingPlayerView.setKeepScreenOn(keepOn);
        }
    }

    private View buildView() {
        FrameLayout panel = new FrameLayout(activity);
        panel.setBackground(rounded(Color.BLACK, 18));

        floatingPlayerView = new PlayerView(activity);
        floatingPlayerView.setUseController(false);
        floatingPlayerView.setControllerAutoShow(false);
        floatingPlayerView.setBackgroundColor(Color.BLACK);
        panel.addView(floatingPlayerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View touchLayer = new View(activity);
        touchLayer.setBackgroundColor(Color.TRANSPARENT);
        touchLayer.setOnTouchListener(new TouchListener());
        panel.addView(touchLayer, fillFrame());

        Button closeButton = iconButton(UiIcons.close(dp(16), Color.WHITE));
        closeButton.setContentDescription("关闭悬浮窗");
        closeButton.setOnClickListener(view -> hide(false));
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                dp(32),
                dp(32),
                Gravity.END | Gravity.TOP);
        closeParams.setMargins(0, dp(8), dp(8), 0);
        panel.addView(closeButton, closeParams);
        closeButton.bringToFront();
        return panel;
    }

    private void attachPlayer() {
        if (floatingPlayerView == null || player == null) {
            return;
        }
        pagePlayerView.setPlayer(null);
        floatingPlayerView.setPlayer(player);
        if (onAttachmentChanged != null) {
            onAttachmentChanged.run();
        }
    }

    private void restorePlayerToPage() {
        if (floatingPlayerView != null) {
            floatingPlayerView.setPlayer(null);
        }
        if (player != null) {
            pagePlayerView.setPlayer(player);
        }
        if (onAttachmentChanged != null) {
            onAttachmentChanged.run();
        }
    }

    private void returnToFullscreen() {
        hide(false);
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        mainHandler.postDelayed(onReturnToFullscreen, 220);
    }

    private void saveBounds() {
        if (windowParams == null) {
            return;
        }
        AppPreferences.saveFloatingBounds(
                activity,
                windowParams.x,
                windowParams.y,
                windowParams.width,
                windowParams.height);
    }

    private void move(int startX, int startY, int deltaX, int deltaY) {
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        windowParams.x = clamp(startX + deltaX, 0, Math.max(0, screenWidth - windowParams.width));
        windowParams.y = clamp(startY + deltaY, 0, Math.max(0, screenHeight - windowParams.height));
        windowManager.updateViewLayout(floatingWindowView, windowParams);
    }

    private void resize(int startWidth, int startHeight, int deltaX, int deltaY) {
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int maxWidth = Math.max(dp(220), screenWidth - windowParams.x);
        int maxHeight = Math.max(dp(160), screenHeight - windowParams.y);
        windowParams.width = clamp(startWidth + deltaX, dp(220), maxWidth);
        windowParams.height = clamp(startHeight + deltaY, dp(160), maxHeight);
        windowManager.updateViewLayout(floatingWindowView, windowParams);
    }

    private Button iconButton(Drawable icon) {
        Button button = new Button(activity);
        button.setText("");
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(null);
        button.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
        return button;
    }

    private FrameLayout.LayoutParams fillFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private final class TouchListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private int startWidth;
        private int startHeight;
        private float downRawX;
        private float downRawY;
        private boolean resizing;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (windowParams == null || windowManager == null || floatingWindowView == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = windowParams.x;
                    startY = windowParams.y;
                    startWidth = windowParams.width;
                    startHeight = windowParams.height;
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    resizing = event.getX() >= Math.max(0, view.getWidth() - dp(56))
                            && event.getY() >= Math.max(0, view.getHeight() - dp(56));
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int deltaX = Math.round(event.getRawX() - downRawX);
                    int deltaY = Math.round(event.getRawY() - downRawY);
                    moved = moved || Math.abs(deltaX) > dp(6) || Math.abs(deltaY) > dp(6);
                    if (resizing) {
                        resize(startWidth, startHeight, deltaX, deltaY);
                    } else {
                        move(startX, startY, deltaX, deltaY);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    saveBounds();
                    if (!moved && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        returnToFullscreen();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
