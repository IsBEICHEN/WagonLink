package com.example.livelink;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AbsListView;
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
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int COLOR_PRIMARY = 0xFF2F7CFF;
    private static final int COLOR_DANGER = 0xFFE85D75;
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
    private View channelTopBar;
    private FrameLayout playerContainer;
    private PlayerView playerView;
    private View playerFeedbackView;
    private ProgressBar videoProgressBar;
    private TextView playerStatusText;
    private TextView fullscreenInfoText;
    private Button fullscreenButton;
    private LinearLayout channelControls;
    private ScrollView channelScrollView;
    private LinearLayout channelListContent;
    private TextView channelSubscriptionPicker;
    private TextView groupPicker;
    private EditText searchInput;
    private Button refreshButton;
    private TextView channelTabButton;
    private TextView subscriptionTabButton;
    private TextView statusText;
    private TextView channelCountText;
    private TextView activeSourceText;
    private TextView emptyStateText;
    private ProgressBar progressBar;
    private ListView subscriptionListView;
    private ArrayAdapter<String> subscriptionListAdapter;
    private EditText subscriptionNameInput;
    private EditText subscriptionUrlInput;
    private Button saveSubscriptionButton;
    private Button deleteSubscriptionButton;
    private TextView themePicker;
    private Switch glassEffectSwitch;
    private TextView subscriptionStatusText;
    private boolean destroyed;
    private boolean isFullscreen;
    private boolean tabLayoutReady;
    private int playRequestToken;
    private String activeSubscriptionUrl = "";
    private String activeSubscriptionId = "";
    private String selectedChannelUrl = "";
    private String selectedChannelName = "";
    private String selectedGroup = "全部";
    private String editingSubscriptionId = "";
    private String themeMode = AppPreferences.THEME_SYSTEM;
    private boolean glassEffectEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themeMode = AppPreferences.loadTheme(this);
        glassEffectEnabled = true;
        AppPreferences.saveGlassEffect(this, true);
        resolveThemeColors();
        configureWindow();
        player = new ExoPlayer.Builder(this).build();
        attachPlayerListener();
        setContentView(buildContentView());
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
        contentHost.addView(channelPage, fillFrame());
        contentHost.addView(subscriptionPage, fillFrame());

        rootLayout.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        rootLayout.addView(buildBottomTabs(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(84)));

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
        bar.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42));
        params.setMargins(dp(10), 0, dp(10), dp(8));
        bar.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText("WagonLink");
        title.setTextColor(colorText);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);

        activeSourceText = new TextView(this);
        activeSourceText.setText("未选择订阅");
        activeSourceText.setTextColor(colorMuted);
        activeSourceText.setTextSize(12);
        activeSourceText.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        activeSourceText.setSingleLine(true);

        bar.addView(title, weighted(0, 42));
        bar.addView(activeSourceText, weighted(0, 42));
        return bar;
    }

    private View buildPlayer() {
        playerContainer = new FrameLayout(this);
        playerContainer.setBackground(roundRect(Color.BLACK, 20));
        playerContainer.setOnClickListener(view -> hideKeyboard());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(184));
        params.setMargins(dp(10), dp(4), dp(10), dp(8));
        playerContainer.setLayoutParams(params);

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setBackgroundColor(Color.BLACK);
        playerContainer.addView(playerView, fillFrame());

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
        fullscreenInfoText.setTextSize(13);
        fullscreenInfoText.setTypeface(Typeface.DEFAULT_BOLD);
        fullscreenInfoText.setSingleLine(true);
        fullscreenInfoText.setEllipsize(TextUtils.TruncateAt.END);
        fullscreenInfoText.setPadding(dp(12), 0, dp(12), 0);
        fullscreenInfoText.setBackground(roundRect(0xAA111827, 16));
        fullscreenInfoText.setVisibility(View.GONE);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36),
                Gravity.START | Gravity.TOP);
        infoParams.setMargins(dp(10), dp(10), dp(100), 0);
        playerContainer.addView(fullscreenInfoText, infoParams);

        fullscreenButton = pillButton("全屏", 0xCC111827, Color.WHITE);
        fullscreenButton.setTextSize(13);
        fullscreenButton.setOnClickListener(view -> setFullscreen(!isFullscreen));
        FrameLayout.LayoutParams fullscreenParams = new FrameLayout.LayoutParams(
                dp(78),
                dp(40),
                Gravity.END | Gravity.BOTTOM);
        fullscreenParams.setMargins(0, 0, dp(10), dp(10));
        playerContainer.addView(fullscreenButton, fullscreenParams);
        return playerContainer;
    }

    private View buildChannelControls() {
        channelControls = new LinearLayout(this);
        channelControls.setOrientation(LinearLayout.VERTICAL);
        channelControls.setPadding(dp(12), dp(12), dp(12), dp(8));
        channelControls.setBackground(roundRectWithStroke(colorCard, 20));
        channelControls.setElevation(glassEffectEnabled ? dp(8) : 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(10), 0, dp(10), dp(8));
        channelControls.setLayoutParams(params);

        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setGravity(Gravity.CENTER_VERTICAL);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);

        channelSubscriptionPicker = picker("选择订阅源");
        channelSubscriptionPicker.setOnClickListener(view -> showSubscriptionPicker());

        refreshButton = pillButton("刷新", COLOR_PRIMARY, Color.WHITE);
        refreshButton.setOnClickListener(view -> loadSubscription(true));

        sourceRow.addView(channelSubscriptionPicker, weighted(0, 52));
        sourceRow.addView(space(dp(10), 1));
        sourceRow.addView(refreshButton, new LinearLayout.LayoutParams(dp(84), dp(52)));
        channelControls.addView(sourceRow);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, dp(10), 0, 0);

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

        filterRow.addView(groupPicker, new LinearLayout.LayoutParams(dp(136), dp(52)));
        filterRow.addView(space(dp(10), 1));
        filterRow.addView(searchInput, weighted(0, 52));
        channelControls.addView(filterRow);

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);

        channelCountText = smallText("暂无频道");
        statusText = smallText("在底部订阅页添加订阅源");
        channelCountText.setGravity(Gravity.CENTER_VERTICAL);
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setPadding(0, dp(8), 0, 0);
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
        emptyStateText.setText("暂无频道\n先到底部订阅页添加订阅源");
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
        page.setPadding(dp(24), dp(30), dp(24), dp(10));

        TextView title = new TextView(this);
        title.setText("订阅");
        title.setTextColor(colorText);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(title);

        TextView subtitle = smallText("保存多个订阅源，在频道页快速切换。");
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(2), 0, dp(10));
        page.addView(subtitle, subtitleParams);

        LinearLayout appearance = new LinearLayout(this);
        appearance.setOrientation(LinearLayout.VERTICAL);
        appearance.setPadding(dp(12), dp(10), dp(12), dp(10));
        appearance.setBackground(roundRectWithStroke(colorCard, 20));
        appearance.setElevation(glassEffectEnabled ? dp(8) : 0);
        LinearLayout.LayoutParams appearanceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        appearanceParams.setMargins(0, 0, 0, dp(10));

        TextView appearanceTitle = new TextView(this);
        appearanceTitle.setText("外观");
        appearanceTitle.setTextColor(colorText);
        appearanceTitle.setTextSize(16);
        appearanceTitle.setTypeface(Typeface.DEFAULT_BOLD);
        themePicker = picker(themeLabel(themeMode));
        themePicker.setOnClickListener(view -> showThemePicker());
        appearance.addView(appearanceTitle);
        appearance.addView(themePicker, topMarginParams(dp(46), dp(8)));
        TextView versionText = smallText("当前版本: " + BuildConfig.VERSION_NAME);
        versionText.setGravity(Gravity.CENTER_VERTICAL);
        versionText.setPadding(dp(12), 0, dp(12), 0);
        versionText.setBackground(roundRectWithStroke(colorField, 14));
        appearance.addView(versionText, topMarginParams(dp(44), dp(8)));
        page.addView(appearance, appearanceParams);

        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        editor.setBackground(roundRectWithStroke(colorCard, 20));
        editor.setElevation(glassEffectEnabled ? dp(8) : 0);

        subscriptionNameInput = field("订阅名称，可不填");
        subscriptionUrlInput = field("订阅链接");
        subscriptionUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        editor.addView(subscriptionNameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)));
        editor.addView(subscriptionUrlInput, topMarginParams(dp(46), dp(8)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, dp(10), 0, 0);

        saveSubscriptionButton = pillButton("保存并加载", COLOR_PRIMARY, Color.WHITE);
        saveSubscriptionButton.setOnClickListener(view -> saveEditedSubscription());
        deleteSubscriptionButton = pillButton("删除", 0xFF2A2030, COLOR_DANGER);
        deleteSubscriptionButton.setOnClickListener(view -> deleteEditedSubscription());

        actionRow.addView(saveSubscriptionButton, weighted(0, 44));
        actionRow.addView(space(dp(8), 1));
        actionRow.addView(deleteSubscriptionButton, weighted(0, 44));
        editor.addView(actionRow);

        subscriptionStatusText = smallText("点下面列表可编辑订阅；清空表单可新增。");
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(8), 0, 0);
        editor.addView(subscriptionStatusText, statusParams);

        page.addView(editor);

        LinearLayout listHeader = new LinearLayout(this);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);
        listHeader.setOrientation(LinearLayout.HORIZONTAL);
        listHeader.setPadding(0, dp(12), 0, dp(6));
        TextView listTitle = new TextView(this);
        listTitle.setText("已保存订阅");
        listTitle.setTextColor(colorText);
        listTitle.setTextSize(16);
        listTitle.setTypeface(Typeface.DEFAULT_BOLD);
        Button newButton = pillButton("新增", colorField, colorText);
        newButton.setOnClickListener(view -> clearSubscriptionEditor());
        listHeader.addView(listTitle, weighted(0, 36));
        listHeader.addView(newButton, new LinearLayout.LayoutParams(dp(76), dp(38)));
        page.addView(listHeader);

        subscriptionListAdapter = new SubscriptionListAdapter(this);
        subscriptionListView = new ListView(this);
        subscriptionListView.setAdapter(subscriptionListAdapter);
        subscriptionListView.setDivider(null);
        subscriptionListView.setDividerHeight(0);
        subscriptionListView.setCacheColorHint(Color.TRANSPARENT);
        subscriptionListView.setBackgroundColor(colorAppBg);
        subscriptionListView.setClipToPadding(false);
        subscriptionListView.setPadding(0, 0, 0, dp(8));
        subscriptionListView.setOnItemClickListener((parent, view, position, id) -> editSubscription(position));
        page.addView(subscriptionListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));

        return page;
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
        FrameLayout host = new FrameLayout(this);
        host.setPadding(dp(18), dp(8), dp(18), dp(14));
        host.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(8), dp(6), dp(8), dp(6));
        tabs.setBackground(glassEffectEnabled
                ? roundRectWithStroke(colorBottomBar, 30)
                : roundRectWithStroke(colorBottomBar, 30));
        tabs.setElevation(glassEffectEnabled ? dp(14) : dp(6));

        channelTabButton = tabButton("频道");
        channelTabButton.setOnClickListener(view -> showTab(true));
        subscriptionTabButton = tabButton("订阅");
        subscriptionTabButton.setOnClickListener(view -> showTab(false));

        tabs.addView(channelTabButton, weighted(0, 48));
        tabs.addView(space(dp(10), 1));
        tabs.addView(subscriptionTabButton, weighted(0, 48));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(62),
                Gravity.CENTER);
        host.addView(tabs, params);
        return host;
    }

    private void showTab(boolean channels) {
        hideKeyboard();
        updateTabButtons(channels);
        View target = channels ? channelPage : subscriptionPage;
        View outgoing = channels ? subscriptionPage : channelPage;
        if (!tabLayoutReady) {
            channelPage.setVisibility(channels ? View.VISIBLE : View.GONE);
            subscriptionPage.setVisibility(channels ? View.GONE : View.VISIBLE);
            target.setAlpha(1f);
            target.setTranslationY(0f);
            outgoing.setAlpha(1f);
            outgoing.setTranslationY(0f);
            tabLayoutReady = true;
            return;
        }
        if (target.getVisibility() == View.VISIBLE) {
            return;
        }
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

    private void updateTabButtons(boolean channels) {
        styleTabButton(channelTabButton, channels);
        styleTabButton(subscriptionTabButton, !channels);
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
            subscriptionListAdapter.add("暂无订阅，点上方保存一个。");
        } else {
            for (Subscription subscription : subscriptions) {
                subscriptionListAdapter.add(subscription.name + "\n" + subscription.url);
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
        editingSubscriptionId = subscription.id;
        statusText.setText("当前订阅: " + subscription.name);
        loadSubscription();
    }

    private void editSubscription(int position) {
        if (subscriptions.isEmpty() || position < 0 || position >= subscriptions.size()) {
            clearSubscriptionEditor();
            return;
        }
        Subscription subscription = subscriptions.get(position);
        editingSubscriptionId = subscription.id;
        subscriptionNameInput.setText(subscription.name);
        subscriptionUrlInput.setText(subscription.url);
        subscriptionStatusText.setText("正在编辑: " + subscription.name);
    }

    private void clearSubscriptionEditor() {
        editingSubscriptionId = "";
        subscriptionNameInput.setText("");
        subscriptionUrlInput.setText("");
        subscriptionStatusText.setText("新建订阅");
    }

    private void saveEditedSubscription() {
        hideKeyboard();
        String url = subscriptionUrlInput.getText().toString().trim();
        if (url.isEmpty()) {
            subscriptionStatusText.setText("请输入订阅链接");
            return;
        }
        Subscription saved = SubscriptionStore.upsert(
                this,
                subscriptions,
                subscriptionNameInput.getText().toString(),
                url);
        activeSubscriptionUrl = saved.url;
        activeSubscriptionId = saved.id;
        editingSubscriptionId = saved.id;
        AppPreferences.saveLastSubscriptionId(this, saved.id);
        updateActiveSourceLabel(saved.name);
        reloadSubscriptions(saved.id);
        subscriptionNameInput.setText(saved.name);
        subscriptionUrlInput.setText(saved.url);
        subscriptionStatusText.setText("已保存并切换到: " + saved.name);
        statusText.setText("当前订阅: " + saved.name);
        showTab(true);
        loadSubscription();
    }

    private void deleteEditedSubscription() {
        hideKeyboard();
        if (editingSubscriptionId.isEmpty()) {
            subscriptionStatusText.setText("请选择要删除的订阅");
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
            subscriptionStatusText.setText("订阅不存在");
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
        clearSubscriptionEditor();
        reloadSubscriptions("");
        subscriptionStatusText.setText("已删除: " + deleted.name);
    }

    private void showSubscriptionPicker() {
        if (subscriptions.isEmpty()) {
            statusText.setText("请先到底部订阅页添加订阅源");
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
        ThemedListAdapter adapter = new ThemedListAdapter(this);
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
        ThemedListAdapter adapter = new ThemedListAdapter(this);
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
        int maxListHeight = Math.max(dp(180), screenHeight - dp(220));
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
        popup.showAtLocation(rootLayout, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(76));
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
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
            updateKeepScreenOn();
            statusText.setText("正在播放: " + channel.name);
        } catch (Exception exception) {
            statusText.setText("播放失败: " + exception.getMessage());
        }
    }

    private MediaItem buildMediaItem(String url) {
        Uri uri = Uri.parse(url);
        String lower = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        if (lower.endsWith(".mpd")) {
            builder.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if (lower.endsWith(".m3u8")
                || lower.contains("live.catvod.com/?id=")) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        return builder.build();
    }

    private void showVideoLoading(String message) {
        if (playerFeedbackView == null || videoProgressBar == null || playerStatusText == null) {
            return;
        }
        videoProgressBar.setVisibility(View.VISIBLE);
        playerStatusText.setText(message == null || message.trim().isEmpty() ? "正在连接..." : message);
        playerFeedbackView.setVisibility(View.VISIBLE);
    }

    private void showVideoError(String message) {
        if (playerFeedbackView == null || videoProgressBar == null || playerStatusText == null) {
            return;
        }
        videoProgressBar.setVisibility(View.GONE);
        playerStatusText.setText(message == null || message.trim().isEmpty() ? "播放失败" : message);
        playerFeedbackView.setVisibility(View.VISIBLE);
    }

    private void hideVideoFeedback() {
        if (playerFeedbackView != null) {
            playerFeedbackView.setVisibility(View.GONE);
        }
    }

    private void updatePlayerInfoOverlay() {
        if (fullscreenInfoText == null) {
            return;
        }
        if (!isFullscreen || selectedChannelName.isEmpty()) {
            fullscreenInfoText.setVisibility(View.GONE);
            return;
        }
        String source = activeSourceText == null ? "" : activeSourceText.getText().toString().trim();
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
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        boolean selected = channel.url.equals(selectedChannelUrl);
        card.setBackground(selected
                ? roundRectWithStroke(0x1A2F7CFF, 16, COLOR_PRIMARY, 2)
                : roundRectWithStroke(colorCard, 16));
        card.setOnClickListener(view -> playChannel(channel));

        TextView title = new TextView(this);
        title.setText(channel.name);
        title.setTextColor(selected ? COLOR_PRIMARY : colorText);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);

        TextView detail = new TextView(this);
        detail.setText(selected ? "正在播放 | " + channel.group : channel.group);
        detail.setTextColor(selected ? COLOR_PRIMARY : colorMuted);
        detail.setTextSize(13);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);

        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(24)));
        card.addView(detail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(20)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(70));
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
        saveSubscriptionButton.setEnabled(!loading);
        deleteSubscriptionButton.setEnabled(!loading);
    }

    private void setFullscreen(boolean fullscreen) {
        isFullscreen = fullscreen;
        fullscreenButton.setText(fullscreen ? "退出" : "全屏");
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
        params.height = fullscreen ? ViewGroup.LayoutParams.MATCH_PARENT : dp(184);
        params.setMargins(
                fullscreen ? 0 : dp(10),
                fullscreen ? 0 : dp(4),
                fullscreen ? 0 : dp(10),
                fullscreen ? 0 : dp(8));
        playerContainer.setLayoutParams(params);

        Window window = getWindow();
        if (fullscreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            configureWindow();
        }
    }

    private void updateKeepScreenOn() {
        boolean playingOrBuffering = player != null
                && player.getPlayWhenReady()
                && (player.getPlaybackState() == Player.STATE_READY
                || player.getPlaybackState() == Player.STATE_BUFFERING);
        boolean keepOn = isFullscreen || playingOrBuffering;
        Window window = getWindow();
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (playerView != null) {
                playerView.setKeepScreenOn(true);
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (playerView != null) {
                playerView.setKeepScreenOn(false);
            }
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
        field.setMinHeight(dp(52));
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
        view.setMinHeight(dp(52));
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setBackground(roundRectWithStroke(colorField, 14));
        return view;
    }

    private TextView tabButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setMinHeight(dp(48));
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
    protected void onDestroy() {
        destroyed = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        if (subscriptionPage.getVisibility() == View.VISIBLE) {
            showTab(true);
            return;
        }
        super.onBackPressed();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence sequence, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence sequence, int start, int before, int count) {
        }
    }

    private interface PopupSelection {
        void onSelect(int position);
    }

    private final class ThemedListAdapter extends ArrayAdapter<String> {
        ThemedListAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_1, new ArrayList<>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView instanceof TextView) {
                view = (TextView) convertView;
            } else {
                view = new TextView(getContext());
                view.setTextSize(14);
                view.setGravity(Gravity.CENTER_VERTICAL);
                view.setPadding(dp(getContext(), 14), 0, dp(getContext(), 14), 0);
            }
            view.setText(getItem(position));
            view.setTextColor(colorText);
            view.setBackground(roundRect(colorCard, 0));
            view.setSingleLine(true);
            view.setEllipsize(TextUtils.TruncateAt.END);
            view.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(getContext(), 54)));
            return view;
        }

        private static int dp(Context context, int value) {
            float density = context.getResources().getDisplayMetrics().density;
            return Math.round(value * density);
        }
    }

    private final class SubscriptionListAdapter extends ArrayAdapter<String> {
        SubscriptionListAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_1, new ArrayList<>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            LinearLayout container;
            if (convertView instanceof LinearLayout
                    && ((LinearLayout) convertView).getChildCount() > 0
                    && ((LinearLayout) convertView).getChildAt(0) instanceof TextView) {
                container = (LinearLayout) convertView;
                view = (TextView) container.getChildAt(0);
            } else {
                container = new LinearLayout(getContext());
                container.setOrientation(LinearLayout.VERTICAL);
                container.setPadding(0, dp(getContext(), 4), 0, dp(getContext(), 4));
                container.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                view = new TextView(getContext());
                view.setTextSize(14);
                view.setPadding(dp(getContext(), 14), dp(getContext(), 12), dp(getContext(), 14), dp(getContext(), 12));
                container.addView(view, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            view.setText(getItem(position));
            view.setTextColor(colorText);
            view.setBackground(roundRectWithStroke(colorCard, 16));
            view.setSingleLine(false);
            return container;
        }

        private static int dp(Context context, int value) {
            float density = context.getResources().getDisplayMetrics().density;
            return Math.round(value * density);
        }
    }
}
