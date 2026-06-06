package com.example.livelink;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int COLOR_PRIMARY = 0xFF2F7CFF;
    private static final int COLOR_DANGER = 0xFFE85D75;
    private static final int FULLSCREEN_CONTROLS_TIMEOUT_MS = 5000;
    private int colorAppBg;
    private int colorCard;
    private int colorField;
    private int colorBottomBar;
    private int colorText;
    private int colorMuted;
    private int colorBorder;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Channel> filteredChannels = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();

    private ExoPlayer player;
    private LinearLayout rootLayout;
    private FrameLayout contentHost;
    private LinearLayout channelPage;
    private LinearLayout subscriptionPage;
    private LinearLayout settingsPage;
    private View channelTopBar;
    private FrameLayout playerContainer;
    private PlayerView playerView;
    private View playerFeedbackView;
    private ProgressBar videoProgressBar;
    private TextView playerStatusText;
    private TextView fullscreenInfoText;
    private TextView playerInfoText;
    private Button fullscreenButton;
    private Button lockButton;
    private Button unlockButton;
    private LinearLayout channelControls;
    private ScrollView channelScrollView;
    private LinearLayout channelListContent;
    private TextView channelSubscriptionPicker;
    private TextView groupPicker;
    private EditText searchInput;
    private Button refreshButton;
    private TextView channelTabButton;
    private TextView subscriptionTabButton;
    private TextView settingsTabButton;
    private TextView statusText;
    private TextView channelCountText;
    private TextView activeSourceText;
    private TextView emptyStateText;
    private FrameLayout bottomTabsHost;
    private ProgressBar progressBar;
    private ListView subscriptionListView;
    private SubscriptionListAdapter subscriptionListAdapter;
    private EditText subscriptionNameInput;
    private EditText subscriptionUrlInput;
    private Button saveSubscriptionButton;
    private Button deleteSubscriptionButton;
    private TextView themePicker;
    private Switch glassEffectSwitch;
    private Switch floatingWindowSwitch;
    private TextView subscriptionStatusText;
    private PopupWindow subscriptionEditorPopup;
    private FloatingWindowController floatingWindowController;
    private boolean destroyed;
    private boolean isFullscreen;
    private boolean fullscreenControlsVisible;
    private boolean fullscreenLocked;
    private boolean videoFeedbackRequested;
    private boolean videoFeedbackLoading;
    private boolean tabLayoutReady;
    private boolean floatingWindowEnabled;
    private boolean requestingOverlayPermission;
    private int currentTabIndex;
    private int playRequestToken;
    private String activeSubscriptionUrl = "";
    private String activeSubscriptionId = "";
    private String selectedChannelUrl = "";
    private String selectedChannelName = "";
    private String selectedGroup = "全部";
    private String editingSubscriptionId = "";
    private String themeMode = AppPreferences.THEME_SYSTEM;
    private boolean glassEffectEnabled;
    private final Runnable hideFullscreenControlsRunnable = () -> {
        if (isFullscreen) {
            setFullscreenControlsVisible(false);
        }
    };
    private final Runnable enterFloatingWindowRunnable = this::enterFloatingWindowIfNeeded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themeMode = AppPreferences.loadTheme(this);
        glassEffectEnabled = true;
        floatingWindowEnabled = AppPreferences.loadFloatingWindow(this);
        if (floatingWindowEnabled && !canDrawOverlayWindow()) {
            floatingWindowEnabled = false;
            AppPreferences.saveFloatingWindow(this, false);
        }
        AppPreferences.saveGlassEffect(this, true);
        resolveThemeColors();
        configureWindow();
        player = new ExoPlayer.Builder(this).build();
        attachPlayerListener();
        setContentView(buildContentView());
        floatingWindowController = new FloatingWindowController(
                this,
                mainHandler,
                player,
                playerView,
                () -> {
                    if (isFullscreen) {
                        setFullscreen(false);
                    }
                },
                () -> {
                    if (!destroyed && player != null && !isFullscreen) {
                        setFullscreen(true);
                    }
                },
                this::updateKeepScreenOn);
        String lastSubscriptionId = AppPreferences.loadLastSubscriptionId(this);
        reloadSubscriptions(lastSubscriptionId);
        if (!lastSubscriptionId.isEmpty() && !activeSubscriptionId.isEmpty()) {
            loadSubscription();
        } else if (!lastSubscriptionId.isEmpty()) {
            AppPreferences.saveLastSubscriptionId(this, "");
        }
        showTab(true);
    }

    private void attachPlayerListener() {
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateKeepScreenOn();
                if (playbackState == Player.STATE_BUFFERING) {
                    showVideoLoading("正在连接: " + selectedChannelName);
                } else if (playbackState == Player.STATE_READY) {
                    hideVideoFeedback();
                    if (statusText != null && !selectedChannelName.isEmpty()) {
                        statusText.setText("正在播放: " + selectedChannelName);
                    }
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                updateKeepScreenOn();
                String message = error.getMessage() == null || error.getMessage().trim().isEmpty()
                        ? "频道连接失败"
                        : error.getMessage();
                showVideoError("播放失败: " + message);
                if (statusText != null) {
                    statusText.setText("播放失败: " + message);
                }
            }
        });
    }

    private void resolveThemeColors() {
        boolean dark = shouldUseDarkTheme();
        if (dark) {
            colorAppBg = 0xFF0A1020;
            colorCard = 0xFF151B2E;
            colorField = 0xFF202842;
            colorBottomBar = 0xFF080D1A;
            colorText = 0xFFF5F7FB;
            colorMuted = 0xFF8B97AC;
            colorBorder = 0xFF26324D;
            if (glassEffectEnabled) {
                colorAppBg = 0xCC0A1020;
                colorCard = 0xB31B2438;
                colorField = 0x8030405E;
                colorBottomBar = 0xBF0B1120;
                colorBorder = 0x55FFFFFF;
            }
        } else {
            colorAppBg = 0xFFEFF3FA;
            colorCard = 0xFFFFFFFF;
            colorField = 0xFFFFFFFF;
            colorBottomBar = 0xFFFFFFFF;
            colorText = 0xFF101828;
            colorMuted = 0xFF667085;
            colorBorder = 0xFFC6D0E0;
            if (glassEffectEnabled) {
                colorAppBg = 0xBFEFF3FA;
                colorCard = 0xCCFFFFFF;
                colorField = 0xAAFFFFFF;
                colorBottomBar = 0xCCFFFFFF;
                colorBorder = 0xAAFFFFFF;
            }
        }
    }

    private boolean shouldUseDarkTheme() {
        if (AppPreferences.THEME_DARK.equals(themeMode)) {
            return true;
        }
        if (AppPreferences.THEME_LIGHT.equals(themeMode)) {
            return false;
        }
        int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(glassEffectEnabled ? Color.TRANSPARENT : colorAppBg);
        window.setNavigationBarColor(glassEffectEnabled ? Color.TRANSPARENT : colorAppBg);
        applyWindowBlurSafely(window, glassEffectEnabled);
        window.getDecorView().setSystemUiVisibility(shouldUseDarkTheme() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void applyWindowBlurSafely(Window window, boolean enabled) {
        WindowManager.LayoutParams params = window.getAttributes();
        if (enabled) {
            try {
                window.setBackgroundBlurRadius(dp(36));
                params.setBlurBehindRadius(dp(28));
                window.setAttributes(params);
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            } catch (Throwable ignored) {
                disableWindowBlur(window, params);
            }
        } else {
            disableWindowBlur(window, params);
        }
    }

    private void disableWindowBlur(Window window, WindowManager.LayoutParams params) {
        try {
            window.setBackgroundBlurRadius(0);
            params.setBlurBehindRadius(0);
            window.setAttributes(params);
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        } catch (Throwable ignored) {
        }
    }

    private View buildContentView() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(colorAppBg);

        contentHost = new FrameLayout(this);
        channelPage = buildChannelPage();
        subscriptionPage = buildSubscriptionPage();
        settingsPage = buildSettingsPage();
        contentHost.addView(channelPage, fillFrame());
        contentHost.addView(subscriptionPage, fillFrame());
        contentHost.addView(settingsPage, fillFrame());

        rootLayout.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        rootLayout.addView(buildBottomTabs(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(96)));

        return rootLayout;
    }

    private LinearLayout buildChannelPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(colorAppBg);
        page.setPadding(0, dp(28), 0, 0);

        channelTopBar = buildChannelTopBar();
        page.addView(channelTopBar);
        page.addView(buildPlayer());
        page.addView(buildChannelControls());
        page.addView(buildChannelList(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        return page;
    }

    private View buildChannelTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44));
        params.setMargins(0, 0, 0, dp(6));
        bar.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText("WagonLink");
        title.setTextColor(colorText);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);

        activeSourceText = new TextView(this);
        activeSourceText.setText("未选择订阅");
        activeSourceText.setTextColor(colorMuted);
        activeSourceText.setTextSize(12);
        activeSourceText.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        activeSourceText.setSingleLine(true);
        activeSourceText.setEllipsize(TextUtils.TruncateAt.END);
        activeSourceText.setPadding(dp(10), 0, 0, 0);

        bar.addView(title, weighted(0, 42));
        bar.addView(activeSourceText, new LinearLayout.LayoutParams(dp(138), dp(42)));
        return bar;
    }

    private View buildPlayer() {
        playerContainer = new FrameLayout(this);
        playerContainer.setBackground(roundRect(Color.BLACK, 20));
        playerContainer.setOnClickListener(view -> handlePlayerSurfaceTap());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(166));
        params.setMargins(dp(10), dp(4), dp(10), dp(8));
        playerContainer.setLayoutParams(params);

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerShowTimeoutMs(FULLSCREEN_CONTROLS_TIMEOUT_MS);
        playerView.setOnClickListener(view -> handlePlayerSurfaceTap());
        playerView.setBackgroundColor(Color.BLACK);
        playerContainer.addView(playerView, fillFrame());

        playerInfoText = new TextView(this);
        playerInfoText.setText("选择频道后开始播放");
        playerInfoText.setTextColor(Color.WHITE);
        playerInfoText.setTextSize(13);
        playerInfoText.setTypeface(Typeface.DEFAULT_BOLD);
        playerInfoText.setSingleLine(true);
        playerInfoText.setEllipsize(TextUtils.TruncateAt.END);
        playerInfoText.setPadding(dp(4), 0, dp(4), 0);
        playerInfoText.setShadowLayer(dp(2), 0, dp(1), 0xCC000000);
        playerInfoText.setBackground(null);
        FrameLayout.LayoutParams playerInfoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36),
                Gravity.START | Gravity.TOP);
        playerInfoParams.setMargins(dp(10), dp(10), dp(100), 0);
        playerContainer.addView(playerInfoText, playerInfoParams);

        LinearLayout feedback = new LinearLayout(this);
        feedback.setOrientation(LinearLayout.VERTICAL);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(18), dp(12), dp(18), dp(12));
        feedback.setBackground(roundRect(0xAA111827, 18));
        feedback.setVisibility(View.GONE);
        playerFeedbackView = feedback;

        videoProgressBar = new ProgressBar(this);
        videoProgressBar.setIndeterminate(true);
        feedback.addView(videoProgressBar, new LinearLayout.LayoutParams(dp(42), dp(42)));

        playerStatusText = new TextView(this);
        playerStatusText.setTextColor(Color.WHITE);
        playerStatusText.setTextSize(13);
        playerStatusText.setGravity(Gravity.CENTER);
        playerStatusText.setMaxLines(2);
        playerStatusText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams playerStatusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        playerStatusParams.setMargins(0, dp(8), 0, 0);
        feedback.addView(playerStatusText, playerStatusParams);
        FrameLayout.LayoutParams feedbackParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        playerContainer.addView(feedback, feedbackParams);

        fullscreenInfoText = new TextView(this);
        fullscreenInfoText.setTextColor(Color.WHITE);
        fullscreenInfoText.setTextSize(12);
        fullscreenInfoText.setTypeface(Typeface.DEFAULT_BOLD);
        fullscreenInfoText.setSingleLine(true);
        fullscreenInfoText.setEllipsize(TextUtils.TruncateAt.END);
        fullscreenInfoText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        fullscreenInfoText.setPadding(dp(4), 0, dp(4), 0);
        fullscreenInfoText.setShadowLayer(dp(2), 0, dp(1), 0xCC000000);
        fullscreenInfoText.setBackground(null);
        fullscreenInfoText.setVisibility(View.GONE);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36),
                Gravity.START | Gravity.TOP);
        infoParams.setMargins(dp(10), dp(10), dp(100), 0);
        playerContainer.addView(fullscreenInfoText, infoParams);

        fullscreenButton = iconButton(UiIcons.fullscreen(false, dp(18), Color.WHITE));
        fullscreenButton.setContentDescription("全屏");
        fullscreenButton.setOnClickListener(view -> setFullscreen(!isFullscreen));
        FrameLayout.LayoutParams fullscreenParams = new FrameLayout.LayoutParams(
                dp(36),
                dp(36),
                Gravity.END | Gravity.BOTTOM);
        fullscreenParams.setMargins(0, 0, dp(8), dp(8));
        playerContainer.addView(fullscreenButton, fullscreenParams);

        lockButton = lockIconButton(false);
        lockButton.setVisibility(View.GONE);
        lockButton.setOnClickListener(view -> lockFullscreenControls());
        FrameLayout.LayoutParams lockParams = new FrameLayout.LayoutParams(
                dp(44),
                dp(44),
                Gravity.START | Gravity.CENTER_VERTICAL);
        lockParams.setMargins(dp(12), 0, 0, 0);
        playerContainer.addView(lockButton, lockParams);

        unlockButton = lockIconButton(true);
        unlockButton.setVisibility(View.GONE);
        unlockButton.setOnClickListener(view -> unlockFullscreenControls());
        FrameLayout.LayoutParams unlockParams = new FrameLayout.LayoutParams(
                dp(44),
                dp(44),
                Gravity.START | Gravity.CENTER_VERTICAL);
        unlockParams.setMargins(dp(16), 0, 0, 0);
        playerContainer.addView(unlockButton, unlockParams);
        return playerContainer;
    }

    private View buildChannelControls() {
        channelControls = new LinearLayout(this);
        channelControls.setOrientation(LinearLayout.VERTICAL);
        channelControls.setPadding(dp(10), 0, dp(10), dp(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(2));
        channelControls.setLayoutParams(params);

        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setGravity(Gravity.CENTER_VERTICAL);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);

        channelSubscriptionPicker = picker("选择订阅源");
        channelSubscriptionPicker.setOnClickListener(view -> showSubscriptionPicker());

        refreshButton = pillButton("刷新", COLOR_PRIMARY, Color.WHITE);
        refreshButton.setOnClickListener(view -> loadSubscription(true));

        sourceRow.addView(channelSubscriptionPicker, weighted(0, 46));
        sourceRow.addView(space(dp(8), 1));
        sourceRow.addView(refreshButton, new LinearLayout.LayoutParams(dp(78), dp(46)));
        channelControls.addView(sourceRow);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(dp(10), dp(8), dp(10), dp(8));
        filterRow.setBackground(roundRectWithStroke(colorCard, 18));
        filterRow.setElevation(glassEffectEnabled ? dp(6) : 0);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        filterParams.setMargins(0, dp(8), 0, 0);

        groupPicker = picker("全部");
        groupPicker.setOnClickListener(view -> showGroupPicker());
        resetGroups();

        searchInput = field("搜索频道");
        searchInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                applyFilters();
            }
        });

        filterRow.addView(groupPicker, new LinearLayout.LayoutParams(dp(128), dp(44)));
        filterRow.addView(space(dp(8), 1));
        filterRow.addView(searchInput, weighted(0, 44));
        channelControls.addView(filterRow, filterParams);

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);

        channelCountText = smallText("暂无频道");
        statusText = smallText("在订阅页添加订阅源");
        channelCountText.setGravity(Gravity.CENTER_VERTICAL);
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setPadding(dp(4), dp(6), dp(4), 0);
        infoRow.addView(progressBar, new LinearLayout.LayoutParams(dp(22), dp(22)));
        infoRow.addView(channelCountText, weighted(0, 24));
        channelControls.addView(infoRow);
        return channelControls;
    }

    private View buildChannelList() {
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(colorAppBg);

        channelScrollView = new ScrollView(this);
        channelScrollView.setBackgroundColor(colorAppBg);
        channelScrollView.setFillViewport(true);
        channelScrollView.setClipToPadding(false);
        channelScrollView.setPadding(0, 0, 0, dp(82));

        channelListContent = new LinearLayout(this);
        channelListContent.setOrientation(LinearLayout.VERTICAL);
        channelListContent.setPadding(0, dp(4), 0, dp(10));
        channelScrollView.addView(channelListContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyStateText = new TextView(this);
        emptyStateText.setText("暂无频道\n请在订阅页添加订阅源或刷新当前订阅");
        emptyStateText.setTextColor(colorMuted);
        emptyStateText.setTextSize(15);
        emptyStateText.setGravity(Gravity.CENTER);
        emptyStateText.setLineSpacing(dp(4), 1);
        container.addView(channelScrollView, fillFrame());
        container.addView(emptyStateText, fillFrame());
        renderChannelList();
        return container;
    }

    private LinearLayout buildSubscriptionPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(colorAppBg);
        page.setPadding(dp(18), dp(28), dp(18), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("订阅");
        title.setTextColor(colorText);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, weighted(0, 40));

        Button addButton = iconButton(UiIcons.plus(dp(20), COLOR_PRIMARY));
        addButton.setContentDescription("新增订阅");
        addButton.setOnClickListener(view -> clearSubscriptionEditor());
        header.addView(addButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        page.addView(header);

        subscriptionStatusText = smallText("点按加载订阅，长按可编辑或删除。右上角 + 可新增。");
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(6), 0, dp(10));
        page.addView(subscriptionStatusText, subtitleParams);

        subscriptionListAdapter = new SubscriptionListAdapter(
                this,
                subscriptions,
                () -> activeSubscriptionId,
                COLOR_PRIMARY,
                () -> colorText,
                () -> colorCard);
        subscriptionListView = new ListView(this);
        subscriptionListView.setAdapter(subscriptionListAdapter);
        subscriptionListView.setDivider(null);
        subscriptionListView.setDividerHeight(0);
        subscriptionListView.setCacheColorHint(Color.TRANSPARENT);
        subscriptionListView.setBackgroundColor(colorAppBg);
        subscriptionListView.setClipToPadding(false);
        subscriptionListView.setPadding(0, 0, 0, dp(8));
        subscriptionListView.setOnItemClickListener((parent, view, position, id) -> loadSubscriptionAt(position));
        subscriptionListView.setOnItemLongClickListener((parent, view, position, id) -> {
            showSubscriptionActions(position);
            return true;
        });
        page.addView(subscriptionListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));

        return page;
    }

    private LinearLayout buildSettingsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(colorAppBg);
        page.setPadding(dp(18), dp(28), dp(18), dp(10));

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextColor(colorText);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(title);

        TextView subtitle = smallText("外观、版本和基础偏好设置。");
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(4), 0, dp(12));
        page.addView(subtitle, subtitleParams);

        LinearLayout appearance = new LinearLayout(this);
        appearance.setOrientation(LinearLayout.VERTICAL);
        appearance.setPadding(dp(14), dp(12), dp(14), dp(12));
        appearance.setBackground(roundRectWithStroke(colorCard, 18));
        appearance.setElevation(glassEffectEnabled ? dp(4) : 0);

        TextView appearanceTitle = new TextView(this);
        appearanceTitle.setText("外观");
        appearanceTitle.setTextColor(colorText);
        appearanceTitle.setTextSize(16);
        appearanceTitle.setTypeface(Typeface.DEFAULT_BOLD);
        appearance.addView(appearanceTitle);

        themePicker = picker("主题模式  " + themeLabel(themeMode));
        themePicker.setOnClickListener(view -> showThemePicker());
        appearance.addView(themePicker, topMarginParams(dp(44), dp(8)));

        TextView versionText = smallText("当前版本: " + BuildConfig.VERSION_NAME);
        versionText.setGravity(Gravity.CENTER_VERTICAL);
        versionText.setPadding(dp(12), 0, dp(12), 0);
        versionText.setBackground(roundRectWithStroke(colorField, 14));
        appearance.addView(versionText, topMarginParams(dp(44), dp(8)));

        page.addView(appearance, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout playback = new LinearLayout(this);
        playback.setOrientation(LinearLayout.VERTICAL);
        playback.setPadding(dp(14), dp(12), dp(14), dp(12));
        playback.setBackground(roundRectWithStroke(colorCard, 18));
        playback.setElevation(glassEffectEnabled ? dp(4) : 0);

        TextView playbackTitle = new TextView(this);
        playbackTitle.setText("播放");
        playbackTitle.setTextColor(colorText);
        playbackTitle.setTextSize(16);
        playbackTitle.setTypeface(Typeface.DEFAULT_BOLD);
        playback.addView(playbackTitle);

        playback.addView(buildFloatingWindowRow(), topMarginParams(dp(52), dp(8)));

        LinearLayout.LayoutParams playbackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        playbackParams.setMargins(0, dp(12), 0, 0);
        page.addView(playback, playbackParams);
        return page;
    }

    private View buildFloatingWindowRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(10), 0);
        row.setBackground(roundRectWithStroke(colorField, 14));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText("返回时自动进入悬浮窗");
        label.setTextColor(colorText);
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setSingleLine(true);

        TextView detail = smallText("开启后，播放中返回桌面会自动显示小窗");
        detail.setSingleLine(true);

        copy.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(24)));
        copy.addView(detail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(20)));

        floatingWindowSwitch = new Switch(this);
        floatingWindowSwitch.setText("");
        floatingWindowSwitch.setChecked(floatingWindowEnabled && canDrawOverlayWindow());
        floatingWindowSwitch.setOnCheckedChangeListener((buttonView, checked) -> setFloatingWindowEnabled(checked, true));

        row.addView(copy, weighted(0, 48));
        row.addView(floatingWindowSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View buildGlassEffectRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(10), 0);
        row.setBackground(roundRectWithStroke(colorField, 14));

        TextView label = new TextView(this);
        label.setText("毛玻璃特效");
        label.setTextColor(colorText);
        label.setTextSize(14);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setSingleLine(true);

        glassEffectSwitch = new Switch(this);
        glassEffectSwitch.setText("");
        glassEffectSwitch.setChecked(glassEffectEnabled);
        glassEffectSwitch.setOnCheckedChangeListener((buttonView, checked) -> setGlassEffectEnabled(checked));

        row.addView(label, weighted(0, 48));
        row.addView(glassEffectSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View buildBottomTabs() {
        bottomTabsHost = new FrameLayout(this);
        bottomTabsHost.setPadding(dp(16), dp(6), dp(16), dp(28));
        bottomTabsHost.setBackgroundColor(Color.TRANSPARENT);
        bottomTabsHost.setOnApplyWindowInsetsListener((view, insets) -> {
            int navigationBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            int bottomPadding = Math.max(dp(28), navigationBottom + dp(12));
            view.setPadding(dp(16), dp(6), dp(16), bottomPadding);
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = dp(58) + dp(6) + bottomPadding;
                view.setLayoutParams(layoutParams);
            }
            return insets;
        });

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(6), dp(5), dp(6), dp(5));
        tabs.setBackground(glassEffectEnabled
                ? roundRectWithStroke(colorBottomBar, 30)
                : roundRectWithStroke(colorBottomBar, 30));
        tabs.setElevation(glassEffectEnabled ? dp(14) : dp(6));

        channelTabButton = tabButton("频道");
        channelTabButton.setOnClickListener(view -> showTab(0));
        subscriptionTabButton = tabButton("订阅");
        subscriptionTabButton.setOnClickListener(view -> showTab(1));
        settingsTabButton = tabButton("设置");
        settingsTabButton.setOnClickListener(view -> showTab(2));

        tabs.addView(channelTabButton, weighted(0, 48));
        tabs.addView(space(dp(6), 1));
        tabs.addView(subscriptionTabButton, weighted(0, 48));
        tabs.addView(space(dp(6), 1));
        tabs.addView(settingsTabButton, weighted(0, 48));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottomTabsHost.addView(tabs, params);
        return bottomTabsHost;
    }

    private void showTab(boolean channels) {
        showTab(channels ? 0 : 1);
    }

    private void showTab(int tabIndex) {
        hideKeyboard();
        updateTabButtons(tabIndex);
        View target = pageForTab(tabIndex);
        View outgoing = pageForTab(currentTabIndex);
        if (!tabLayoutReady) {
            channelPage.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
            subscriptionPage.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
            settingsPage.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
            target.setAlpha(1f);
            target.setTranslationY(0f);
            currentTabIndex = tabIndex;
            tabLayoutReady = true;
            return;
        }
        if (target.getVisibility() == View.VISIBLE) {
            currentTabIndex = tabIndex;
            return;
        }
        currentTabIndex = tabIndex;
        target.setVisibility(View.VISIBLE);
        target.setAlpha(0f);
        target.setTranslationY(dp(10));
        target.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        outgoing.animate()
                .alpha(0f)
                .translationY(-dp(6))
                .setDuration(160)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setAlpha(1f);
                    outgoing.setTranslationY(0f);
                })
                .start();
    }

    private View pageForTab(int tabIndex) {
        if (tabIndex == 1) {
            return subscriptionPage;
        }
        if (tabIndex == 2) {
            return settingsPage;
        }
        return channelPage;
    }
    private void updateTabButtons(int tabIndex) {
        styleTabButton(channelTabButton, tabIndex == 0);
        styleTabButton(subscriptionTabButton, tabIndex == 1);
        styleTabButton(settingsTabButton, tabIndex == 2);
    }

    private void styleTabButton(TextView button, boolean selected) {
        button.setBackground(selected
                ? roundRectWithStroke(COLOR_PRIMARY, 24, 0x66FFFFFF, glassEffectEnabled ? 2 : 1)
                : roundRect(glassEffectEnabled ? colorField : Color.TRANSPARENT, 24));
        button.setTextColor(selected ? Color.WHITE : colorText);
        button.animate()
                .scaleX(selected ? 1.02f : 1f)
                .scaleY(selected ? 1.02f : 1f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void reloadSubscriptions(String selectedId) {
        subscriptions.clear();
        subscriptions.addAll(SubscriptionStore.load(this));
        refreshSubscriptionSpinner(selectedId);
        refreshSubscriptionList();
    }

    private void refreshSubscriptionSpinner(String selectedId) {
        if (channelSubscriptionPicker == null) {
            return;
        }
        activeSubscriptionId = "";
        activeSubscriptionUrl = "";
        channelSubscriptionPicker.setText("选择订阅源");
        updateActiveSourceLabel(null);
        for (int i = 0; i < subscriptions.size(); i++) {
            Subscription subscription = subscriptions.get(i);
            if (subscription.id.equals(selectedId)) {
                activeSubscriptionId = subscription.id;
                activeSubscriptionUrl = subscription.url;
                channelSubscriptionPicker.setText(subscription.name);
                updateActiveSourceLabel(subscription.name);
                break;
            }
        }
    }

    private void refreshSubscriptionList() {
        if (subscriptionListAdapter == null) {
            return;
        }
        subscriptionListAdapter.clear();
        if (subscriptions.isEmpty()) {
            subscriptionListAdapter.add("暂无订阅，点击左上角 + 新增。");
        } else {
            for (Subscription subscription : subscriptions) {
                subscriptionListAdapter.add(subscription.name);
            }
        }
        subscriptionListAdapter.notifyDataSetChanged();
    }

    private void updateActiveSourceLabel(String name) {
        if (activeSourceText == null) {
            return;
        }
        if (name == null || name.trim().isEmpty()) {
            activeSourceText.setText("未选择订阅");
        } else {
            activeSourceText.setText(name.trim());
        }
        updatePlayerInfoOverlay();
    }

    private void selectChannelSubscription(Subscription subscription) {
        activeSubscriptionId = subscription.id;
        activeSubscriptionUrl = subscription.url;
        AppPreferences.saveLastSubscriptionId(this, subscription.id);
        channelSubscriptionPicker.setText(subscription.name);
        updateActiveSourceLabel(subscription.name);
        refreshSubscriptionList();
        editingSubscriptionId = subscription.id;
        statusText.setText("当前订阅: " + subscription.name);
        loadSubscription();
    }

    private void loadSubscriptionAt(int position) {
        if (subscriptions.isEmpty() || position < 0 || position >= subscriptions.size()) {
            clearSubscriptionEditor();
            return;
        }
        selectChannelSubscription(subscriptions.get(position));
        showTab(true);
    }

    private void showSubscriptionActions(int position) {
        if (subscriptions.isEmpty() || position < 0 || position >= subscriptions.size()) {
            clearSubscriptionEditor();
            return;
        }
        Subscription subscription = subscriptions.get(position);
        List<String> actions = new ArrayList<>();
        actions.add("加载订阅");
        actions.add("编辑");
        actions.add("删除");
        showChoicePanel(subscription.name, actions, selected -> {
            if (selected == 0) {
                selectChannelSubscription(subscription);
                showTab(true);
            } else if (selected == 1) {
                editingSubscriptionId = subscription.id;
                showSubscriptionEditor(subscription);
            } else if (selected == 2) {
                editingSubscriptionId = subscription.id;
                deleteEditedSubscription();
            }
        });
    }

    private void editSubscription(int position) {
        if (subscriptions.isEmpty() || position < 0 || position >= subscriptions.size()) {
            clearSubscriptionEditor();
            return;
        }
        Subscription subscription = subscriptions.get(position);
        editingSubscriptionId = subscription.id;
        showSubscriptionEditor(subscription);
    }

    private void clearSubscriptionEditor() {
        editingSubscriptionId = "";
        showSubscriptionEditor(null);
    }

    private void showSubscriptionEditor(Subscription subscription) {
        if (subscriptionEditorPopup != null) {
            subscriptionEditorPopup.dismiss();
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(roundRectWithStroke(colorCard, 22));

        TextView title = new TextView(this);
        title.setText(subscription == null ? "新增订阅" : "编辑订阅");
        title.setTextColor(colorText);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(32)));

        subscriptionNameInput = field("订阅名称，可不填");
        subscriptionUrlInput = field("订阅链接");
        subscriptionUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (subscription != null) {
            subscriptionNameInput.setText(subscription.name);
            subscriptionUrlInput.setText(subscription.url);
        }
        panel.addView(subscriptionNameInput, topMarginParams(dp(46), dp(10)));
        panel.addView(subscriptionUrlInput, topMarginParams(dp(46), dp(8)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, dp(10), 0, 0);

        saveSubscriptionButton = pillButton("保存并加载", COLOR_PRIMARY, Color.WHITE);
        saveSubscriptionButton.setOnClickListener(view -> saveEditedSubscription());
        deleteSubscriptionButton = pillButton("删除", 0xFF2A2030, COLOR_DANGER);
        deleteSubscriptionButton.setVisibility(subscription == null ? View.GONE : View.VISIBLE);
        deleteSubscriptionButton.setOnClickListener(view -> deleteEditedSubscription());

        actionRow.addView(saveSubscriptionButton, weighted(0, 44));
        actionRow.addView(space(dp(8), 1));
        actionRow.addView(deleteSubscriptionButton, weighted(0, 44));
        panel.addView(actionRow);

        subscriptionEditorPopup = new PopupWindow(
                panel,
                Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(520)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        subscriptionEditorPopup.setBackgroundDrawable(roundRectWithStroke(colorCard, 22));
        subscriptionEditorPopup.setOutsideTouchable(true);
        subscriptionEditorPopup.setElevation(dp(14));
        subscriptionEditorPopup.showAtLocation(rootLayout, Gravity.CENTER, 0, 0);
        subscriptionUrlInput.requestFocus();
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            subscriptionUrlInput.postDelayed(() -> manager.showSoftInput(subscriptionUrlInput, InputMethodManager.SHOW_IMPLICIT), 180);
        }
    }
    private void saveEditedSubscription() {
        hideKeyboard();
        if (subscriptionUrlInput == null || subscriptionNameInput == null) {
            return;
        }
        String url = subscriptionUrlInput.getText().toString().trim();
        if (url.isEmpty()) {
            if (subscriptionStatusText != null) {
                subscriptionStatusText.setText("请输入订阅链接");
            }
            return;
        }
        Subscription saved = SubscriptionStore.upsert(
                this,
                subscriptions,
                editingSubscriptionId,
                subscriptionNameInput.getText().toString(),
                url);
        activeSubscriptionUrl = saved.url;
        activeSubscriptionId = saved.id;
        editingSubscriptionId = saved.id;
        AppPreferences.saveLastSubscriptionId(this, saved.id);
        updateActiveSourceLabel(saved.name);
        reloadSubscriptions(saved.id);
        if (subscriptionStatusText != null) {
            subscriptionStatusText.setText("已保存并切换到 " + saved.name);
        }
        statusText.setText("当前订阅: " + saved.name);
        if (subscriptionEditorPopup != null) {
            subscriptionEditorPopup.dismiss();
        }
        showTab(true);
        loadSubscription();
    }

    private void deleteEditedSubscription() {
        hideKeyboard();
        if (editingSubscriptionId.isEmpty()) {
            if (subscriptionStatusText != null) {
                subscriptionStatusText.setText("请选择要删除的订阅");
            }
            return;
        }
        Subscription deleted = null;
        for (Subscription subscription : subscriptions) {
            if (subscription.id.equals(editingSubscriptionId)) {
                deleted = subscription;
                break;
            }
        }
        if (deleted == null) {
            if (subscriptionStatusText != null) {
                subscriptionStatusText.setText("订阅不存在");
            }
            return;
        }
        SubscriptionStore.delete(this, subscriptions, deleted.id);
        ChannelCache.delete(this, deleted.id);
        if (activeSubscriptionId.equals(deleted.id)) {
            activeSubscriptionUrl = "";
            activeSubscriptionId = "";
            playRequestToken++;
            selectedChannelUrl = "";
            selectedChannelName = "";
            hideVideoFeedback();
            AppPreferences.saveLastSubscriptionId(this, "");
            updateActiveSourceLabel(null);
            allChannels.clear();
            filteredChannels.clear();
            renderChannelList();
            channelCountText.setText("暂无频道");
        }
        editingSubscriptionId = "";
        reloadSubscriptions("");
        if (subscriptionStatusText != null) {
            subscriptionStatusText.setText("已删除 " + deleted.name);
        }
        if (subscriptionEditorPopup != null) {
            subscriptionEditorPopup.dismiss();
        }
    }
    private void showSubscriptionPicker() {
        if (subscriptions.isEmpty()) {
            statusText.setText("请先在订阅页添加或选择订阅源");
            showTab(false);
            return;
        }
        List<String> names = new ArrayList<>();
        for (Subscription subscription : subscriptions) {
            names.add(subscription.name);
        }
        showPopupList(channelSubscriptionPicker, names, position -> selectChannelSubscription(subscriptions.get(position)));
    }

    private void showGroupPicker() {
        List<String> groups = PlaylistParser.groupsFor(allChannels);
        if (groups.isEmpty()) {
            groups.add("全部");
        }
        showChoicePanel("选择分类", groups, position -> {
            selectedGroup = groups.get(position);
            groupPicker.setText(selectedGroup);
            applyFilters();
        });
    }

    private void showThemePicker() {
        List<String> labels = new ArrayList<>();
        labels.add("跟随系统");
        labels.add("日间模式");
        labels.add("夜间模式");
        showPopupList(themePicker, labels, position -> {
            if (position == 0) {
                setThemeMode(AppPreferences.THEME_SYSTEM);
            } else if (position == 1) {
                setThemeMode(AppPreferences.THEME_LIGHT);
            } else {
                setThemeMode(AppPreferences.THEME_DARK);
            }
        });
    }

    private void setThemeMode(String mode) {
        themeMode = mode;
        AppPreferences.saveTheme(this, mode);
        recreate();
    }

    private void setGlassEffectEnabled(boolean enabled) {
        if (glassEffectEnabled == enabled) {
            return;
        }
        glassEffectEnabled = enabled;
        AppPreferences.saveGlassEffect(this, enabled);
        recreate();
    }

    private String themeLabel(String mode) {
        if (AppPreferences.THEME_LIGHT.equals(mode)) {
            return "日间模式";
        }
        if (AppPreferences.THEME_DARK.equals(mode)) {
            return "夜间模式";
        }
        return "跟随系统";
    }

    private void showPopupList(View anchor, List<String> items, PopupSelection selection) {
        ListView listView = new ListView(this);
        ThemedListAdapter adapter = new ThemedListAdapter(this, colorText, colorCard);
        adapter.addAll(items);
        listView.setAdapter(adapter);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setBackground(roundRect(colorCard, 18));

        int popupHeight = Math.min(dp(280), Math.max(dp(56), items.size() * dp(52)));
        PopupWindow popup = new PopupWindow(
                listView,
                anchor.getWidth(),
                popupHeight,
                true);
        popup.setBackgroundDrawable(roundRect(colorCard, 18));
        popup.setOutsideTouchable(true);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selection.onSelect(position);
            popup.dismiss();
        });
        popup.showAsDropDown(anchor, 0, dp(6));
    }

    private void showChoicePanel(String titleText, List<String> items, PopupSelection selection) {
        hideKeyboard();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        panel.setBackground(roundRectWithStroke(colorCard, 22));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(colorText);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38)));

        ListView listView = new ListView(this);
        ThemedListAdapter adapter = new ThemedListAdapter(this, colorText, colorCard);
        adapter.addAll(items);
        listView.setAdapter(adapter);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setBackgroundColor(colorCard);
        listView.setClipToPadding(false);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int popupWidth = Math.max(dp(240), screenWidth - dp(24));
        popupWidth = Math.min(popupWidth, screenWidth - dp(8));
        int bottomOffset = bottomTabsHost == null || bottomTabsHost.getHeight() == 0
                ? dp(92)
                : bottomTabsHost.getHeight() + dp(8);
        int maxListHeight = Math.max(dp(180), screenHeight - bottomOffset - dp(128));
        int listHeight = Math.min(maxListHeight, Math.max(dp(56), items.size() * dp(54)));
        panel.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                listHeight));

        PopupWindow popup = new PopupWindow(
                panel,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(roundRectWithStroke(colorCard, 22));
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(true);
        popup.setElevation(dp(12));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selection.onSelect(position);
            popup.dismiss();
        });
        popup.showAtLocation(rootLayout, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, bottomOffset);
    }

    private boolean loadCachedSubscription(String url) {
        List<Channel> cachedChannels = ChannelCache.load(this, activeSubscriptionId, url);
        if (cachedChannels == null) {
            return false;
        }
        playRequestToken++;
        selectedChannelUrl = "";
        selectedChannelName = "";
        hideVideoFeedback();
        updatePlayerInfoOverlay();
        allChannels.clear();
        allChannels.addAll(cachedChannels);
        updateGroups();
        applyFilters();
        statusText.setText("已从缓存载入频道");
        channelCountText.setText(allChannels.isEmpty() ? "暂无频道" : allChannels.size() + " 个频道");
        return true;
    }

    private void loadSubscription() {
        loadSubscription(false);
    }

    private void loadSubscription(boolean forceRefresh) {
        String url = activeSubscriptionUrl.trim();
        if (url.isEmpty()) {
            statusText.setText("请先在订阅页添加或选择订阅源");
            channelCountText.setText("暂无频道");
            return;
        }
        if (!forceRefresh && loadCachedSubscription(url)) {
            return;
        }
        hideKeyboard();
        setLoading(true);
        playRequestToken++;
        selectedChannelUrl = "";
        selectedChannelName = "";
        hideVideoFeedback();
        updatePlayerInfoOverlay();
        statusText.setText("正在加载订阅...");
        channelCountText.setText("正在解析频道");

        final String cacheSubscriptionId = activeSubscriptionId;
        final String cacheUrl = url;
        ioExecutor.execute(() -> {
            try {
                SubscriptionClient.Result result = SubscriptionClient.load(url);
                mainHandler.post(() -> {
                    if (destroyed) {
                        return;
                    }
                    allChannels.clear();
                    allChannels.addAll(result.channels);
                    ChannelCache.save(this, cacheSubscriptionId, cacheUrl, result.channels);
                    setLoading(false);
                    updateGroups();
                    applyFilters();
                    if (allChannels.isEmpty()) {
                        statusText.setText("没有解析到可播放频道");
                        channelCountText.setText("暂无频道");
                    } else {
                        statusText.setText("已解析 " + allChannels.size() + " 个频道");
                        channelCountText.setText(allChannels.size() + " 个频道");
                    }
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    if (destroyed) {
                        return;
                    }
                    setLoading(false);
                    String message = exception.getMessage() == null ? "加载失败" : exception.getMessage();
                    statusText.setText(message);
                    channelCountText.setText("加载失败");
                });
            }
        });
    }

    private void playChannel(Channel channel) {
        if (channel.url.isEmpty()) {
            statusText.setText("频道地址为空");
            return;
        }
        try {
            selectedChannelUrl = channel.url;
            selectedChannelName = channel.name;
            renderChannelList();
            updatePlayerInfoOverlay();
            showVideoLoading("正在连接: " + channel.name);
            int requestToken = ++playRequestToken;
            mainHandler.postDelayed(() -> {
                if (requestToken == playRequestToken
                        && player != null
                        && player.getPlaybackState() == Player.STATE_BUFFERING) {
                    showVideoError("连接超时，请换一个频道或稍后再试");
                    if (statusText != null) {
                        statusText.setText("连接超时: " + selectedChannelName);
                    }
                }
            }, 15000);
            MediaItem mediaItem = buildMediaItem(channel.url);
            player.setMediaSource(buildMediaSourceFactory(channel).createMediaSource(mediaItem));
            player.prepare();
            player.play();
            updateKeepScreenOn();
            statusText.setText("正在播放: " + channel.name);
        } catch (Exception exception) {
            statusText.setText("播放失败: " + exception.getMessage());
        }
    }

    private DefaultMediaSourceFactory buildMediaSourceFactory(Channel channel) {
        String userAgent = channel.userAgent.isEmpty()
                ? "WagonLink/" + BuildConfig.VERSION_NAME + " Android"
                : channel.userAgent;
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(userAgent);
        Map<String, String> headers = new HashMap<>();
        if (!channel.referer.isEmpty()) {
            headers.put("Referer", channel.referer);
        }
        if (!headers.isEmpty()) {
            httpFactory.setDefaultRequestProperties(headers);
        }
        return new DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory);
    }

    private MediaItem buildMediaItem(String url) {
        Uri uri = Uri.parse(url);
        String lower = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        if (lower.endsWith(".mpd") || lower.contains(".mpd?")) {
            builder.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if (lower.endsWith(".m3u8")
                || lower.contains(".m3u8?")
                || lower.contains("m3u8")
                || lower.contains(".php")
                || lower.contains("live.catvod.com/?id=")) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        return builder.build();
    }

    private void showVideoLoading(String message) {
        if (playerFeedbackView == null || videoProgressBar == null || playerStatusText == null) {
            return;
        }
        videoFeedbackRequested = true;
        videoFeedbackLoading = true;
        videoProgressBar.setVisibility(View.VISIBLE);
        playerStatusText.setText(message == null || message.trim().isEmpty() ? "正在连接..." : message);
        updateVideoFeedbackVisibility();
    }

    private void showVideoError(String message) {
        if (playerFeedbackView == null || videoProgressBar == null || playerStatusText == null) {
            return;
        }
        videoFeedbackRequested = true;
        videoFeedbackLoading = false;
        videoProgressBar.setVisibility(View.GONE);
        playerStatusText.setText(message == null || message.trim().isEmpty() ? "播放失败" : message);
        updateVideoFeedbackVisibility();
    }

    private void hideVideoFeedback() {
        videoFeedbackRequested = false;
        videoFeedbackLoading = false;
        if (playerFeedbackView != null) {
            playerFeedbackView.setVisibility(View.GONE);
        }
    }

    private void updateVideoFeedbackVisibility() {
        if (playerFeedbackView == null || videoProgressBar == null) {
            return;
        }
        videoProgressBar.setVisibility(videoFeedbackLoading ? View.VISIBLE : View.GONE);
        boolean visible = videoFeedbackRequested
                && (!isFullscreen || (fullscreenControlsVisible && !fullscreenLocked));
        playerFeedbackView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void updatePlayerInfoOverlay() {
        if (fullscreenInfoText == null) {
            return;
        }
        String source = activeSourceText == null ? "" : activeSourceText.getText().toString().trim();
        String normalInfo;
        if (!selectedChannelName.isEmpty()) {
            normalInfo = selectedChannelName;
        } else if (source.isEmpty() || "未选择订阅".equals(source)) {
            normalInfo = "选择频道后开始播放";
        } else {
            normalInfo = source;
        }
        if (playerInfoText != null) {
            playerInfoText.setText(normalInfo);
            playerInfoText.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        }
        if (!isFullscreen
                || selectedChannelName.isEmpty()
                || !fullscreenControlsVisible
                || fullscreenLocked) {
            fullscreenInfoText.setVisibility(View.GONE);
            return;
        }
        String info = source.isEmpty() ? selectedChannelName : source + "  |  " + selectedChannelName;
        fullscreenInfoText.setText(info);
        fullscreenInfoText.setVisibility(View.VISIBLE);
    }

    private void applyFilters() {
        if (channelListContent == null) {
            return;
        }
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);

        filteredChannels.clear();
        for (Channel channel : allChannels) {
            boolean matchesGroup = "全部".equals(selectedGroup) || channel.group.equals(selectedGroup);
            boolean matchesQuery = query.isEmpty()
                    || channel.name.toLowerCase(Locale.ROOT).contains(query)
                    || channel.group.toLowerCase(Locale.ROOT).contains(query)
                    || channel.url.toLowerCase(Locale.ROOT).contains(query);
            if (matchesGroup && matchesQuery) {
                filteredChannels.add(channel);
            }
        }
        renderChannelList();
        if (!allChannels.isEmpty()) {
            channelCountText.setText(filteredChannels.size() + " / " + allChannels.size() + " 个频道");
        }
    }

    private void renderChannelList() {
        if (channelListContent == null || emptyStateText == null) {
            return;
        }
        channelListContent.removeAllViews();
        emptyStateText.setVisibility(filteredChannels.isEmpty() && !isFullscreen ? View.VISIBLE : View.GONE);
        channelScrollView.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        for (Channel channel : filteredChannels) {
            channelListContent.addView(channelCard(channel));
        }
    }

    private View channelCard(Channel channel) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(8), dp(10), dp(8));
        boolean selected = channel.url.equals(selectedChannelUrl);
        card.setBackground(selected
                ? roundRectWithStroke(0x262F7CFF, 18, COLOR_PRIMARY, 2)
                : roundRectWithStroke(colorCard, 18));
        card.setOnClickListener(view -> playChannel(channel));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);
        textColumn.setPadding(dp(4), 0, dp(8), 0);

        TextView title = new TextView(this);
        title.setText(channel.name);
        title.setTextColor(selected ? COLOR_PRIMARY : colorText);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);

        TextView detail = new TextView(this);
        detail.setText(selected ? "正在播放 | " + channel.group : channel.group);
        detail.setTextColor(selected ? COLOR_PRIMARY : colorMuted);
        detail.setTextSize(12);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);

        textColumn.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(23)));
        textColumn.addView(detail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(19)));
        card.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1));

        TextView state = new TextView(this);
        state.setText(selected ? "播放中" : "播放");
        state.setTextColor(selected ? Color.WHITE : COLOR_PRIMARY);
        state.setTextSize(12);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        state.setGravity(Gravity.CENTER);
        state.setSingleLine(true);
        state.setBackground(selected
                ? roundRect(COLOR_PRIMARY, 16)
                : roundRectWithStroke(0xFFEAF1FF, 16, COLOR_PRIMARY, 1));
        card.addView(state, new LinearLayout.LayoutParams(dp(58), dp(34)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(66));
        params.setMargins(dp(10), dp(4), dp(10), dp(4));
        card.setLayoutParams(params);
        return card;
    }

    private void updateGroups() {
        selectedGroup = "全部";
        groupPicker.setText(selectedGroup);
    }

    private void resetGroups() {
        selectedGroup = "全部";
        if (groupPicker != null) {
            groupPicker.setText(selectedGroup);
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!loading);
        if (saveSubscriptionButton != null) {
            saveSubscriptionButton.setEnabled(!loading);
        }
        if (deleteSubscriptionButton != null) {
            deleteSubscriptionButton.setEnabled(!loading);
        }
    }

    private void handlePlayerSurfaceTap() {
        hideKeyboard();
        if (!isFullscreen) {
            return;
        }
        if (fullscreenLocked) {
            setFullscreenControlsVisible(true);
            return;
        }
        setFullscreenControlsVisible(!fullscreenControlsVisible);
    }

    private void setFullscreenControlsVisible(boolean visible) {
        if (!isFullscreen) {
            return;
        }
        fullscreenControlsVisible = visible;
        applyFullscreenControlState();
        mainHandler.removeCallbacks(hideFullscreenControlsRunnable);
        if (visible) {
            mainHandler.postDelayed(hideFullscreenControlsRunnable, FULLSCREEN_CONTROLS_TIMEOUT_MS);
        }
    }

    private void lockFullscreenControls() {
        if (!isFullscreen) {
            return;
        }
        fullscreenLocked = true;
        fullscreenControlsVisible = true;
        hideVideoFeedback();
        applyFullscreenControlState();
        mainHandler.removeCallbacks(hideFullscreenControlsRunnable);
        mainHandler.postDelayed(hideFullscreenControlsRunnable, FULLSCREEN_CONTROLS_TIMEOUT_MS);
    }

    private void unlockFullscreenControls() {
        if (!isFullscreen) {
            return;
        }
        fullscreenLocked = false;
        fullscreenControlsVisible = true;
        applyFullscreenControlState();
        mainHandler.removeCallbacks(hideFullscreenControlsRunnable);
        mainHandler.postDelayed(hideFullscreenControlsRunnable, FULLSCREEN_CONTROLS_TIMEOUT_MS);
    }

    private void applyFullscreenControlState() {
        if (playerView == null || fullscreenButton == null) {
            return;
        }
        if (!isFullscreen) {
            fullscreenControlsVisible = false;
            fullscreenLocked = false;
            fullscreenButton.setVisibility(View.VISIBLE);
            if (lockButton != null) {
                lockButton.setVisibility(View.GONE);
            }
            if (unlockButton != null) {
                unlockButton.setVisibility(View.GONE);
            }
            playerView.setUseController(true);
            playerView.setControllerAutoShow(true);
            applyFullscreenSystemUiVisibility();
            updatePlayerInfoOverlay();
            updateVideoFeedbackVisibility();
            return;
        }

        playerView.setControllerAutoShow(false);
        applyFullscreenSystemUiVisibility();
        updateFullscreenOverlayLayout(true);
        if (fullscreenLocked) {
            fullscreenButton.setVisibility(View.GONE);
            fullscreenInfoText.setVisibility(View.GONE);
            if (lockButton != null) {
                lockButton.setVisibility(View.GONE);
            }
            if (unlockButton != null) {
                unlockButton.setVisibility(fullscreenControlsVisible ? View.VISIBLE : View.GONE);
                unlockButton.bringToFront();
            }
            playerView.hideController();
            playerView.setUseController(false);
            updatePlayerInfoOverlay();
            updateVideoFeedbackVisibility();
            return;
        }

        fullscreenButton.setVisibility(fullscreenControlsVisible ? View.VISIBLE : View.GONE);
        fullscreenButton.bringToFront();
        if (lockButton != null) {
            lockButton.setVisibility(fullscreenControlsVisible ? View.VISIBLE : View.GONE);
            lockButton.bringToFront();
        }
        if (unlockButton != null) {
            unlockButton.setVisibility(View.GONE);
        }
        if (fullscreenControlsVisible) {
            playerView.setUseController(true);
            playerView.showController();
        } else {
            playerView.hideController();
            playerView.setUseController(false);
        }
        updatePlayerInfoOverlay();
        updateVideoFeedbackVisibility();
    }

    private void applyFullscreenSystemUiVisibility() {
        Window window = getWindow();
        View decorView = window.getDecorView();
        if (!isFullscreen) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            configureWindow();
            return;
        }

        boolean showSystemBars = fullscreenControlsVisible && !fullscreenLocked;
        if (showSystemBars) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void setFullscreen(boolean fullscreen) {
        isFullscreen = fullscreen;
        fullscreenControlsVisible = false;
        fullscreenLocked = false;
        mainHandler.removeCallbacks(hideFullscreenControlsRunnable);
        updateFullscreenButtonIcon(fullscreen);
        channelTopBar.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        channelControls.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        channelScrollView.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        emptyStateText.setVisibility(fullscreen || !filteredChannels.isEmpty() ? View.GONE : View.VISIBLE);
        channelPage.setPadding(0, fullscreen ? 0 : dp(28), 0, 0);
        contentHost.setPadding(0, 0, 0, 0);
        rootLayout.getChildAt(1).setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        updatePlayerInfoOverlay();
        updateKeepScreenOn();

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) playerContainer.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = fullscreen ? ViewGroup.LayoutParams.MATCH_PARENT : dp(166);
        params.setMargins(
                fullscreen ? 0 : dp(10),
                fullscreen ? 0 : dp(4),
                fullscreen ? 0 : dp(10),
                fullscreen ? 0 : dp(8));
        playerContainer.setLayoutParams(params);
        updateFullscreenOverlayLayout(fullscreen);

        Window window = getWindow();
        if (fullscreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            applyFullscreenSystemUiVisibility();
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            configureWindow();
        }
        applyFullscreenControlState();
        playerContainer.post(() -> updateFullscreenOverlayLayout(isFullscreen));
        if (fullscreen) {
            playerContainer.postDelayed(() -> {
                if (isFullscreen && !fullscreenLocked) {
                    setFullscreenControlsVisible(false);
                }
            }, 250);
        }
    }

    private void updateFullscreenOverlayLayout(boolean fullscreen) {
        if (playerContainer == null || fullscreenButton == null || fullscreenInfoText == null) {
            return;
        }
        int top;
        if (fullscreen && fullscreenControlsVisible && !fullscreenLocked) {
            top = Math.max(statusBarInsetTop(), dp(28)) + dp(8);
        } else {
            top = fullscreen ? dp(10) : dp(10);
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        FrameLayout.LayoutParams buttonParams = (FrameLayout.LayoutParams) fullscreenButton.getLayoutParams();
        buttonParams.gravity = Gravity.END | Gravity.BOTTOM;
        buttonParams.width = fullscreen ? dp(40) : dp(36);
        buttonParams.height = fullscreen ? dp(40) : dp(36);
        buttonParams.setMargins(0, 0, fullscreen ? dp(72) : dp(8), fullscreen ? 0 : dp(8));
        fullscreenButton.setLayoutParams(buttonParams);

        if (lockButton != null) {
            FrameLayout.LayoutParams lockParams = (FrameLayout.LayoutParams) lockButton.getLayoutParams();
            lockParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            lockParams.width = dp(44);
            lockParams.height = dp(44);
            lockParams.setMargins(dp(16), 0, 0, 0);
            lockButton.setLayoutParams(lockParams);
        }

        if (unlockButton != null) {
            FrameLayout.LayoutParams unlockParams = (FrameLayout.LayoutParams) unlockButton.getLayoutParams();
            unlockParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            unlockParams.width = dp(44);
            unlockParams.height = dp(44);
            unlockParams.setMargins(dp(16), 0, 0, 0);
            unlockButton.setLayoutParams(unlockParams);
        }

        FrameLayout.LayoutParams infoParams = (FrameLayout.LayoutParams) fullscreenInfoText.getLayoutParams();
        infoParams.gravity = Gravity.START | Gravity.TOP;
        infoParams.width = fullscreen ? Math.min(dp(360), Math.max(dp(180), screenWidth - dp(320))) : ViewGroup.LayoutParams.WRAP_CONTENT;
        infoParams.height = fullscreen ? dp(36) : dp(36);
        infoParams.setMargins(dp(16), top, fullscreen ? dp(260) : dp(110), 0);
        fullscreenInfoText.setLayoutParams(infoParams);

        if (playerInfoText != null) {
            FrameLayout.LayoutParams normalInfoParams = (FrameLayout.LayoutParams) playerInfoText.getLayoutParams();
            normalInfoParams.gravity = Gravity.START | Gravity.TOP;
            normalInfoParams.setMargins(dp(12), dp(10), dp(56), 0);
            playerInfoText.setLayoutParams(normalInfoParams);
        }
    }

    private int statusBarInsetTop() {
        WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
        if (insets == null) {
            return dp(24);
        }
        return insets.getInsets(WindowInsets.Type.statusBars()).top;
    }

    private void updateKeepScreenOn() {
        boolean playingOrBuffering = isPlaybackActive();
        boolean keepOn = isFullscreen || playingOrBuffering;
        Window window = getWindow();
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (playerView != null) {
                playerView.setKeepScreenOn(true);
            }
            if (floatingWindowController != null) {
                floatingWindowController.setKeepScreenOn(true);
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (playerView != null) {
                playerView.setKeepScreenOn(false);
            }
            if (floatingWindowController != null) {
                floatingWindowController.setKeepScreenOn(false);
            }
        }
    }

    private boolean isPlaybackActive() {
        return player != null
                && player.getPlayWhenReady()
                && (player.getPlaybackState() == Player.STATE_READY
                || player.getPlaybackState() == Player.STATE_BUFFERING);
    }

    private boolean canDrawOverlayWindow() {
        return Settings.canDrawOverlays(this);
    }

    private void setFloatingWindowEnabled(boolean enabled, boolean fromUser) {
        if (enabled && !canDrawOverlayWindow()) {
            floatingWindowEnabled = false;
            AppPreferences.saveFloatingWindow(this, false);
            updateFloatingWindowSwitch(false);
            requestingOverlayPermission = true;
            if (statusText != null) {
                statusText.setText("请允许 WagonLink 显示在其他应用上层");
            }
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        floatingWindowEnabled = enabled;
        AppPreferences.saveFloatingWindow(this, enabled);
        updateFloatingWindowSwitch(enabled);
        if (!enabled) {
            if (floatingWindowController != null) {
                floatingWindowController.hide(false);
            }
        } else if (fromUser && statusText != null) {
            statusText.setText("已开启悬浮窗，播放时返回桌面会自动进入小窗");
        }
    }

    private void updateFloatingWindowSwitch(boolean checked) {
        if (floatingWindowSwitch == null || floatingWindowSwitch.isChecked() == checked) {
            return;
        }
        floatingWindowSwitch.setOnCheckedChangeListener(null);
        floatingWindowSwitch.setChecked(checked);
        floatingWindowSwitch.setOnCheckedChangeListener((buttonView, nextChecked) -> setFloatingWindowEnabled(nextChecked, true));
    }

    private boolean shouldEnterFloatingWindow() {
        return floatingWindowEnabled
                && !requestingOverlayPermission
                && canDrawOverlayWindow()
                && floatingWindowController != null
                && !floatingWindowController.isShowing()
                && isPlaybackActive();
    }

    private boolean enterFloatingWindowIfNeeded() {
        if (!shouldEnterFloatingWindow()) {
            return false;
        }
        floatingWindowController.show();
        return floatingWindowController.isShowing();
    }

    private void scheduleFloatingWindowEntry() {
        mainHandler.removeCallbacks(enterFloatingWindowRunnable);
        if (shouldEnterFloatingWindow()) {
            mainHandler.postDelayed(enterFloatingWindowRunnable, 220);
        }
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setHintTextColor(colorMuted);
        field.setTextColor(colorText);
        field.setTextSize(14);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setMinHeight(dp(44));
        field.setPadding(dp(12), 0, dp(12), 0);
        field.setBackground(roundRectWithStroke(colorField, 14));
        return field;
    }

    private TextView picker(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(colorText);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setMinHeight(dp(44));
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setBackground(roundRectWithStroke(colorField, 14));
        return view;
    }

    private TextView tabButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setMinHeight(dp(46));
        return view;
    }

    private Button pillButton(String text, int backgroundColor, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(textColor);
        button.setBackground(roundRect(backgroundColor, 18));
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private Button iconButton(Drawable icon) {
        Button button = new Button(this);
        button.setText("");
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(null);
        button.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
        return button;
    }

    private Button lockIconButton(boolean locked) {
        Button button = iconButton(UiIcons.lock(locked, dp(26), Color.WHITE));
        button.setContentDescription(locked ? "解除锁定" : "锁定");
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private void updateFullscreenButtonIcon(boolean fullscreen) {
        if (fullscreenButton == null) {
            return;
        }
        fullscreenButton.setCompoundDrawablesWithIntrinsicBounds(
                null,
                UiIcons.fullscreen(fullscreen, dp(fullscreen ? 22 : 18), Color.WHITE),
                null,
                null);
        fullscreenButton.setContentDescription(fullscreen ? "退出全屏" : "全屏");
    }

    private TextView smallText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(colorMuted);
        view.setTextSize(12);
        view.setSingleLine(true);
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        if (glassEffectEnabled && color == colorCard) {
            drawable.setOrientation(GradientDrawable.Orientation.TL_BR);
            drawable.setColors(shouldUseDarkTheme()
                    ? new int[]{0xCC26314A, 0x99101729}
                    : new int[]{0xF2FFFFFF, 0x99EAF1FF});
        } else if (glassEffectEnabled && color == colorField) {
            drawable.setOrientation(GradientDrawable.Orientation.TL_BR);
            drawable.setColors(shouldUseDarkTheme()
                    ? new int[]{0x99364765, 0x66212A40}
                    : new int[]{0xE6FFFFFF, 0x88F4F8FF});
        } else {
            drawable.setColor(color);
        }
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundRectWithStroke(int color, int radiusDp) {
        GradientDrawable drawable = roundRect(color, radiusDp);
        drawable.setStroke(dp(glassEffectEnabled ? 2 : 1), colorBorder);
        return drawable;
    }

    private GradientDrawable roundRectWithStroke(int color, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = roundRect(color, radiusDp);
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private FrameLayout.LayoutParams fillFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams weighted(int width, int height) {
        return new LinearLayout.LayoutParams(width, dp(height), 1);
    }

    private LinearLayout.LayoutParams topMarginParams(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height);
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private View space(int width, int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return view;
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view == null) {
            return;
        }
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isFullscreen) {
            resolveThemeColors();
            applyFullscreenSystemUiVisibility();
            setFullscreen(true);
            return;
        }
        if (AppPreferences.THEME_SYSTEM.equals(themeMode) && !isPlaybackActive()) {
            recreate();
            return;
        }
        resolveThemeColors();
        configureWindow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainHandler.removeCallbacks(enterFloatingWindowRunnable);
        if (requestingOverlayPermission) {
            requestingOverlayPermission = false;
            boolean granted = canDrawOverlayWindow();
            floatingWindowEnabled = granted;
            AppPreferences.saveFloatingWindow(this, granted);
            updateFloatingWindowSwitch(granted);
            if (statusText != null) {
                statusText.setText(granted
                        ? "已开启悬浮窗，播放时返回桌面会自动进入小窗"
                        : "未获得悬浮窗权限");
            }
            return;
        }
        if (floatingWindowController != null && floatingWindowController.isShowing()) {
            floatingWindowController.hide(false);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        scheduleFloatingWindowEntry();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (floatingWindowController != null) {
            floatingWindowController.hide(true);
        }
        if (player != null) {
            player.release();
            player = null;
        }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            setFullscreen(false);
            return;
        }
        if (subscriptionPage.getVisibility() == View.VISIBLE || settingsPage.getVisibility() == View.VISIBLE) {
            showTab(true);
            return;
        }
        if (shouldEnterFloatingWindow()) {
            moveTaskToBack(true);
            scheduleFloatingWindowEntry();
            return;
        }
        super.onBackPressed();
    }

}
