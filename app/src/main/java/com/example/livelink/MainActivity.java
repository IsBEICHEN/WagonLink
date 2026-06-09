package com.example.livelink;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.example.livelink.databinding.ActivityMainBinding;
import com.example.livelink.databinding.DialogChoicePanelBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity
        implements ChannelFragment.Host, SubscriptionFragment.Host, SettingsFragment.Host {

    private static final int FULLSCREEN_CONTROLS_TIMEOUT_MS = 5000;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();

    private ActivityMainBinding binding;
    private ExoPlayer player;
    private FloatingWindowController floatingWindowController;

    private ChannelFragment channelFragment;
    private SubscriptionFragment subscriptionFragment;
    private SettingsFragment settingsFragment;

    private boolean destroyed;
    private boolean isFullscreen;
    private boolean fullscreenControlsVisible;
    private boolean fullscreenLocked;
    private boolean floatingWindowEnabled;
    private boolean requestingOverlayPermission;
    private int playRequestToken;
    private String activeSubscriptionUrl = "";
    private String activeSubscriptionId = "";
    private String selectedChannelUrl = "";
    private String selectedChannelName = "";
    private String themeMode = AppPreferences.THEME_SYSTEM;

    private final Runnable enterFloatingWindowRunnable = this::enterFloatingWindowIfNeeded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        themeMode = AppPreferences.loadTheme(this);
        applyThemeMode();
        super.onCreate(savedInstanceState);

        floatingWindowEnabled = AppPreferences.loadFloatingWindow(this);
        if (floatingWindowEnabled && !canDrawOverlayWindow()) {
            floatingWindowEnabled = false;
            AppPreferences.saveFloatingWindow(this, false);
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        player = new ExoPlayer.Builder(this).build();
        attachPlayerListener();

        setupNavigation();

        if (savedInstanceState == null) {
            channelFragment = new ChannelFragment();
            subscriptionFragment = new SubscriptionFragment();
            settingsFragment = new SettingsFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, channelFragment, "channels")
                    .add(R.id.fragment_container, subscriptionFragment, "subscriptions")
                    .add(R.id.fragment_container, settingsFragment, "settings")
                    .hide(subscriptionFragment)
                    .hide(settingsFragment)
                    .commit();
        } else {
            channelFragment = (ChannelFragment) getSupportFragmentManager().findFragmentByTag("channels");
            subscriptionFragment = (SubscriptionFragment) getSupportFragmentManager().findFragmentByTag("subscriptions");
            settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentByTag("settings");
        }

        getSupportFragmentManager().executePendingTransactions();
        initializeAfterFragments();
    }

    private void initializeAfterFragments() {
        mainHandler.post(() -> {
            if (channelFragment != null && channelFragment.getPlayerView() != null) {
                channelFragment.getPlayerView().setPlayer(player);
                floatingWindowController = new FloatingWindowController(
                        this, mainHandler, player, channelFragment.getPlayerView(),
                        () -> { if (isFullscreen) setFullscreen(false); },
                        () -> { if (!destroyed && player != null && !isFullscreen) setFullscreen(true); },
                        this::updateKeepScreenOn);
            }

            String lastSubscriptionId = AppPreferences.loadLastSubscriptionId(this);
            reloadSubscriptions(lastSubscriptionId);
            if (!lastSubscriptionId.isEmpty() && !activeSubscriptionId.isEmpty()) {
                loadSubscription(false);
            } else if (!lastSubscriptionId.isEmpty()) {
                AppPreferences.saveLastSubscriptionId(this, "");
            }
        });
    }

    private void applyThemeMode() {
        if (AppPreferences.THEME_DARK.equals(themeMode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (AppPreferences.THEME_LIGHT.equals(themeMode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    private void setupNavigation() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            hideKeyboard();
            int id = item.getItemId();
            if (id == R.id.nav_channels) {
                showFragment(channelFragment);
            } else if (id == R.id.nav_subscriptions) {
                showFragment(subscriptionFragment);
                if (subscriptionFragment != null) subscriptionFragment.refreshList();
            } else if (id == R.id.nav_settings) {
                showFragment(settingsFragment);
            }
            return true;
        });
    }

    private void showFragment(Fragment target) {
        if (target == null) return;
        getSupportFragmentManager().beginTransaction()
                .hide(channelFragment)
                .hide(subscriptionFragment)
                .hide(settingsFragment)
                .show(target)
                .commit();
    }

    private void attachPlayerListener() {
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateKeepScreenOn();
                if (channelFragment == null) return;
                if (playbackState == Player.STATE_BUFFERING) {
                    channelFragment.showVideoLoading(getString(R.string.connecting_format, selectedChannelName));
                } else if (playbackState == Player.STATE_READY) {
                    channelFragment.hideVideoFeedback();
                    if (!selectedChannelName.isEmpty()) {
                        channelFragment.setStatusText(getString(R.string.playing_format, selectedChannelName));
                    }
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                updateKeepScreenOn();
                if (channelFragment == null) return;
                String message = error.getMessage() == null || error.getMessage().trim().isEmpty()
                        ? getString(R.string.connection_failed)
                        : error.getMessage();
                channelFragment.showVideoError(getString(R.string.play_failed_format, message));
                channelFragment.setStatusText(getString(R.string.play_failed_format, message));
            }
        });
    }

    // ---- ChannelFragment.Host ----

    @Override
    public void onSubscriptionPickerRequested() {
        if (subscriptions.isEmpty()) {
            if (channelFragment != null) {
                channelFragment.setStatusText(getString(R.string.add_subscription_first));
            }
            binding.bottomNav.setSelectedItemId(R.id.nav_subscriptions);
            return;
        }
        List<String> names = new ArrayList<>();
        for (Subscription s : subscriptions) names.add(s.name);
        showChoicePanel(getString(R.string.select_subscription), names, position -> {
            selectSubscription(subscriptions.get(position));
        });
    }

    @Override
    public void onGroupPickerRequested(List<String> groups) {
        showChoicePanel(getString(R.string.select_group), groups, position -> {
            if (channelFragment != null) {
                channelFragment.setSelectedGroup(groups.get(position));
            }
        });
    }

    @Override
    public void onRefreshRequested() {
        loadSubscription(true);
    }

    @Override
    public void onChannelSelected(Channel channel) {
        playChannel(channel);
    }

    @Override
    public void onFullscreenRequested(boolean enter) {
        setFullscreen(enter);
    }

    @Override
    public void onLockRequested() {
        if (!isFullscreen) return;
        fullscreenLocked = true;
        if (channelFragment != null) {
            channelFragment.hideVideoFeedback();
            PlayerView pv = channelFragment.getPlayerView();
            if (pv != null) { pv.hideController(); pv.setUseController(false); }
            View lockBtn = channelFragment.getLockButton();
            if (lockBtn != null) lockBtn.setVisibility(View.GONE);
        }
    }

    @Override
    public void onUnlockRequested() {
        if (!isFullscreen) return;
        fullscreenLocked = false;
        if (channelFragment != null) {
            PlayerView pv = channelFragment.getPlayerView();
            if (pv != null) {
                pv.setUseController(true);
                pv.showController();
            }
            View unlockBtn = channelFragment.getUnlockButton();
            if (unlockBtn != null) unlockBtn.setVisibility(View.GONE);
        }
    }

    @Override
    public void onControllerVisibilityChanged(boolean visible) {
        if (!isFullscreen || fullscreenLocked || channelFragment == null) return;
        fullscreenControlsVisible = visible;
        View lockBtn = channelFragment.getLockButton();
        View infoText = channelFragment.getFullscreenInfoText();
        if (lockBtn != null) lockBtn.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (infoText != null) infoText.setVisibility(visible && !selectedChannelName.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean isFullscreenLocked() {
        return fullscreenLocked;
    }

    @Override
    public boolean isFullscreen() {
        return isFullscreen;
    }

    @Override
    public String getActiveSourceName() {
        for (Subscription s : subscriptions) {
            if (s.id.equals(activeSubscriptionId)) return s.name;
        }
        return "";
    }

    @Override
    public String getSelectedChannelUrl() {
        return selectedChannelUrl;
    }

    // ---- SubscriptionFragment.Host ----

    @Override
    public void onLoadSubscription(int position) {
        if (position < 0 || position >= subscriptions.size()) return;
        selectSubscription(subscriptions.get(position));
        binding.bottomNav.setSelectedItemId(R.id.nav_channels);
    }

    @Override
    public void onRefreshSubscription(int position) {
        if (position < 0 || position >= subscriptions.size()) return;
        refreshSubscription(subscriptions.get(position));
    }

    @Override
    public void onEditSubscription(int position) {
        if (position < 0 || position >= subscriptions.size()) return;
        if (subscriptionFragment != null) {
            subscriptionFragment.showEditor(subscriptions.get(position));
        }
    }

    @Override
    public void onDeleteSubscription(String id) {
        Subscription deleted = null;
        for (Subscription s : subscriptions) {
            if (s.id.equals(id)) { deleted = s; break; }
        }
        if (deleted == null) return;

        SubscriptionStore.delete(this, subscriptions, deleted.id);
        ChannelCache.delete(this, deleted.id);

        if (activeSubscriptionId.equals(deleted.id)) {
            activeSubscriptionUrl = "";
            activeSubscriptionId = "";
            playRequestToken++;
            selectedChannelUrl = "";
            selectedChannelName = "";
            if (channelFragment != null) {
                channelFragment.hideVideoFeedback();
                channelFragment.setActiveSourceText(null);
                channelFragment.setChannels(new ArrayList<>());
                channelFragment.setStatusText(getString(R.string.no_channels));
            }
            AppPreferences.saveLastSubscriptionId(this, "");
        }

        reloadSubscriptions("");
        if (subscriptionFragment != null) {
            subscriptionFragment.setStatusText(getString(R.string.deleted_format, deleted.name));
            subscriptionFragment.refreshList();
        }
    }

    @Override
    public void onSaveSubscription(String id, String name, String url) {
        Subscription saved = SubscriptionStore.upsert(this, subscriptions, id, name, url);
        activeSubscriptionUrl = saved.url;
        activeSubscriptionId = saved.id;
        AppPreferences.saveLastSubscriptionId(this, saved.id);
        reloadSubscriptions(saved.id);

        if (channelFragment != null) {
            channelFragment.setActiveSourceText(saved.name);
            channelFragment.setSubscriptionPickerText(saved.name);
            channelFragment.setStatusText(getString(R.string.current_subscription_format, saved.name));
        }
        if (subscriptionFragment != null) {
            subscriptionFragment.setStatusText(getString(R.string.saved_format, saved.name));
            subscriptionFragment.refreshList();
        }

        binding.bottomNav.setSelectedItemId(R.id.nav_channels);
        loadSubscription(false);
    }

    @Override
    public void onShowSubscriptionActions(int position) {
        if (position < 0 || position >= subscriptions.size()) return;
        Subscription subscription = subscriptions.get(position);
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.load_subscription));
        actions.add(getString(R.string.refresh));
        actions.add(getString(R.string.edit));
        actions.add(getString(R.string.delete));
        showChoicePanel(subscription.name, actions, selected -> {
            if (selected == 0) {
                selectSubscription(subscription);
                binding.bottomNav.setSelectedItemId(R.id.nav_channels);
            } else if (selected == 1) {
                refreshSubscription(subscription);
            } else if (selected == 2) {
                if (subscriptionFragment != null) subscriptionFragment.showEditor(subscription);
            } else if (selected == 3) {
                onDeleteSubscription(subscription.id);
            }
        });
    }

    @Override
    public List<Subscription> getSubscriptions() {
        return subscriptions;
    }

    @Override
    public String getActiveSubscriptionId() {
        return activeSubscriptionId;
    }

    // ---- SettingsFragment.Host ----

    @Override
    public void onThemePickerRequested() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.theme_system));
        labels.add(getString(R.string.theme_light));
        labels.add(getString(R.string.theme_dark));
        showChoicePanel(getString(R.string.theme_mode), labels, position -> {
            if (position == 0) setThemeMode(AppPreferences.THEME_SYSTEM);
            else if (position == 1) setThemeMode(AppPreferences.THEME_LIGHT);
            else setThemeMode(AppPreferences.THEME_DARK);
        });
    }

    @Override
    public void onFloatingWindowToggled(boolean enabled) {
        setFloatingWindowEnabled(enabled, true);
    }

    @Override
    public boolean isFloatingWindowEnabled() {
        return floatingWindowEnabled;
    }

    @Override
    public boolean canDrawOverlayWindow() {
        return Settings.canDrawOverlays(this);
    }

    @Override
    public String getThemeMode() {
        return themeMode;
    }

    // ---- Core logic ----

    private void selectSubscription(Subscription subscription) {
        activeSubscriptionId = subscription.id;
        activeSubscriptionUrl = subscription.url;
        AppPreferences.saveLastSubscriptionId(this, subscription.id);
        if (channelFragment != null) {
            channelFragment.setSubscriptionPickerText(subscription.name);
            channelFragment.setActiveSourceText(subscription.name);
            channelFragment.setStatusText(getString(R.string.current_subscription_format, subscription.name));
        }
        if (subscriptionFragment != null) subscriptionFragment.refreshList();
        loadSubscription(false);
    }

    private void refreshSubscription(Subscription subscription) {
        activeSubscriptionId = subscription.id;
        activeSubscriptionUrl = subscription.url;
        AppPreferences.saveLastSubscriptionId(this, subscription.id);
        ChannelCache.delete(this, subscription.id);
        if (channelFragment != null) {
            channelFragment.setSubscriptionPickerText(subscription.name);
            channelFragment.setActiveSourceText(subscription.name);
            channelFragment.setStatusText(getString(R.string.loading_subscription));
        }
        if (subscriptionFragment != null) {
            subscriptionFragment.setStatusText(getString(R.string.loading_subscription));
            subscriptionFragment.refreshList();
        }
        loadSubscription(true);
    }

    private void reloadSubscriptions(String selectedId) {
        subscriptions.clear();
        subscriptions.addAll(SubscriptionStore.load(this));
        activeSubscriptionId = "";
        activeSubscriptionUrl = "";
        for (Subscription s : subscriptions) {
            if (s.id.equals(selectedId)) {
                activeSubscriptionId = s.id;
                activeSubscriptionUrl = s.url;
                if (channelFragment != null) {
                    channelFragment.setSubscriptionPickerText(s.name);
                    channelFragment.setActiveSourceText(s.name);
                }
                break;
            }
        }
        if (activeSubscriptionId.isEmpty() && channelFragment != null) {
            channelFragment.setSubscriptionPickerText(getString(R.string.select_subscription));
            channelFragment.setActiveSourceText(null);
        }
    }

    private boolean loadCachedSubscription(String url) {
        List<Channel> cached = ChannelCache.load(this, activeSubscriptionId, url);
        if (cached == null) return false;

        playRequestToken++;
        selectedChannelUrl = "";
        selectedChannelName = "";
        allChannels.clear();
        allChannels.addAll(cached);

        if (channelFragment != null) {
            channelFragment.hideVideoFeedback();
            channelFragment.updatePlayerInfoText(getString(R.string.select_channel_to_play));
            channelFragment.setChannels(allChannels);
            channelFragment.setStatusText(allChannels.isEmpty()
                    ? getString(R.string.no_channels)
                    : getString(R.string.channel_count_format, allChannels.size()));
        }
        return true;
    }

    private void loadSubscription(boolean forceRefresh) {
        String url = activeSubscriptionUrl.trim();
        if (url.isEmpty()) {
            if (channelFragment != null) {
                channelFragment.setStatusText(getString(R.string.add_subscription_first));
            }
            return;
        }
        if (!forceRefresh && loadCachedSubscription(url)) return;

        hideKeyboard();
        if (channelFragment != null) {
            channelFragment.setLoading(true);
            channelFragment.setStatusText(getString(R.string.loading_subscription));
        }

        playRequestToken++;
        selectedChannelUrl = "";
        selectedChannelName = "";

        final String cacheId = activeSubscriptionId;
        final String cacheUrl = url;
        ioExecutor.execute(() -> {
            try {
                SubscriptionClient.Result result = SubscriptionClient.load(url);
                mainHandler.post(() -> {
                    if (destroyed) return;
                    allChannels.clear();
                    allChannels.addAll(result.channels);
                    ChannelCache.save(this, cacheId, cacheUrl, result.channels);
                    if (channelFragment != null) {
                        channelFragment.setLoading(false);
                        channelFragment.setChannels(allChannels);
                        channelFragment.setStatusText(allChannels.isEmpty()
                                ? getString(R.string.no_playable_channels)
                                : getString(R.string.parsed_format, allChannels.size()));
                    }
                    if (forceRefresh && subscriptionFragment != null) {
                        subscriptionFragment.setStatusText(allChannels.isEmpty()
                                ? getString(R.string.no_playable_channels)
                                : getString(R.string.parsed_format, allChannels.size()));
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (destroyed) return;
                    String message = e.getMessage() == null ? getString(R.string.load_failed) : e.getMessage();
                    if (channelFragment != null) {
                        channelFragment.setLoading(false);
                        channelFragment.setStatusText(message);
                    }
                    if (forceRefresh && subscriptionFragment != null) {
                        subscriptionFragment.setStatusText(message);
                    }
                });
            }
        });
    }

    private void playChannel(Channel channel) {
        if (channel.url.isEmpty()) {
            if (channelFragment != null) {
                channelFragment.setStatusText(getString(R.string.channel_url_empty));
            }
            return;
        }
        try {
            selectedChannelUrl = channel.url;
            selectedChannelName = channel.name;
            if (channelFragment != null) {
                channelFragment.setSelectedChannelUrl(channel.url);
                channelFragment.updatePlayerInfoText(channel.name);
                channelFragment.showVideoLoading(getString(R.string.connecting_format, channel.name));
            }

            int requestToken = ++playRequestToken;
            mainHandler.postDelayed(() -> {
                if (requestToken == playRequestToken && player != null
                        && player.getPlaybackState() == Player.STATE_BUFFERING) {
                    if (channelFragment != null) {
                        channelFragment.showVideoError(getString(R.string.connection_timeout));
                        channelFragment.setStatusText(getString(R.string.connection_timeout));
                    }
                }
            }, 15000);

            MediaItem mediaItem = buildMediaItem(channel.url);
            player.setMediaSource(buildMediaSourceFactory(channel).createMediaSource(mediaItem));
            player.prepare();
            player.play();
            updateKeepScreenOn();
            if (channelFragment != null) {
                channelFragment.setStatusText(getString(R.string.playing_format, channel.name));
            }
        } catch (Exception e) {
            if (channelFragment != null) {
                channelFragment.setStatusText(getString(R.string.play_failed_format, e.getMessage()));
            }
        }
    }

    private DefaultMediaSourceFactory buildMediaSourceFactory(Channel channel) {
        String userAgent = channel.userAgent.isEmpty()
                ? "WagonLink/" + BuildConfig.VERSION_NAME + " Android"
                : channel.userAgent;
        DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(userAgent);
        Map<String, String> headers = new HashMap<>();
        if (!channel.referer.isEmpty()) headers.put("Referer", channel.referer);
        if (!headers.isEmpty()) factory.setDefaultRequestProperties(headers);
        return new DefaultMediaSourceFactory(this).setDataSourceFactory(factory);
    }

    private MediaItem buildMediaItem(String url) {
        Uri uri = Uri.parse(url);
        String lower = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        if (lower.endsWith(".mpd") || lower.contains(".mpd?")) {
            builder.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if (lower.endsWith(".m3u8") || lower.contains(".m3u8?")
                || lower.contains("m3u8") || lower.contains(".php")
                || lower.contains("live.catvod.com/?id=")) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        return builder.build();
    }

    // ---- Fullscreen ----

    private void setFullscreen(boolean fullscreen) {
        isFullscreen = fullscreen;
        fullscreenControlsVisible = false;
        fullscreenLocked = false;

        if (channelFragment == null) return;

        channelFragment.updateFullscreenButtonIcon(fullscreen);

        View topBar = channelFragment.getTopBar();
        View controls = channelFragment.getChannelControls();
        View list = channelFragment.getChannelList();
        View emptyText = channelFragment.getEmptyStateText();
        View playerContainer = channelFragment.getPlayerContainer();

        if (topBar != null) topBar.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (controls != null) controls.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (list != null) list.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (emptyText != null) emptyText.setVisibility(fullscreen ? View.GONE : emptyText.getVisibility());

        binding.bottomNav.setVisibility(fullscreen ? View.GONE : View.VISIBLE);

        if (fullscreen && playerContainer != null) {
            ViewGroup parent = (ViewGroup) playerContainer.getParent();
            if (parent != null) parent.removeView(playerContainer);
            binding.playerFullscreenContainer.addView(playerContainer,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            playerContainer.setPadding(0, 0, 0, 0);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) playerContainer.getLayoutParams();
            lp.setMargins(0, 0, 0, 0);
            playerContainer.setLayoutParams(lp);
            binding.playerFullscreenContainer.setVisibility(View.VISIBLE);
            binding.fragmentContainer.setVisibility(View.GONE);
        } else if (!fullscreen && playerContainer != null) {
            binding.playerFullscreenContainer.removeView(playerContainer);
            binding.playerFullscreenContainer.setVisibility(View.GONE);
            binding.fragmentContainer.setVisibility(View.VISIBLE);
            View fragmentRoot = channelFragment.getView();
            if (fragmentRoot instanceof ViewGroup) {
                ViewGroup fragmentLayout = (ViewGroup) fragmentRoot;
                android.widget.LinearLayout.LayoutParams plp = new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(180));
                plp.setMargins(dp(12), 0, dp(12), dp(8));
                fragmentLayout.addView(playerContainer, 1, plp);
            }
        }

        updateKeepScreenOn();

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
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        applyFullscreenControlState();
    }

    private void applyFullscreenControlState() {
        if (channelFragment == null) return;
        View lockBtn = channelFragment.getLockButton();
        View unlockBtn = channelFragment.getUnlockButton();
        PlayerView pv = channelFragment.getPlayerView();

        if (!isFullscreen) {
            fullscreenControlsVisible = false;
            fullscreenLocked = false;
            if (lockBtn != null) lockBtn.setVisibility(View.GONE);
            if (unlockBtn != null) unlockBtn.setVisibility(View.GONE);
            if (pv != null) {
                pv.setUseController(true);
                pv.setControllerAutoShow(true);
                pv.setControllerShowTimeoutMs(FULLSCREEN_CONTROLS_TIMEOUT_MS);
            }
            return;
        }

        if (lockBtn != null) lockBtn.setVisibility(View.GONE);
        if (unlockBtn != null) unlockBtn.setVisibility(View.GONE);

        if (pv != null) {
            pv.setUseController(!fullscreenLocked);
            pv.setControllerAutoShow(false);
            pv.setControllerShowTimeoutMs(FULLSCREEN_CONTROLS_TIMEOUT_MS);
            if (!fullscreenLocked) {
                pv.hideController();
            }
        }
    }

    // ---- Theme ----

    private void setThemeMode(String mode) {
        themeMode = mode;
        AppPreferences.saveTheme(this, mode);
        applyThemeMode();
        recreate();
    }

    // ---- Floating window ----

    private void setFloatingWindowEnabled(boolean enabled, boolean fromUser) {
        if (enabled && !canDrawOverlayWindow()) {
            floatingWindowEnabled = false;
            AppPreferences.saveFloatingWindow(this, false);
            if (settingsFragment != null) settingsFragment.updateFloatingWindowSwitch(false);
            requestingOverlayPermission = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        floatingWindowEnabled = enabled;
        AppPreferences.saveFloatingWindow(this, enabled);
        if (settingsFragment != null) settingsFragment.updateFloatingWindowSwitch(enabled);
        if (!enabled && floatingWindowController != null) {
            floatingWindowController.hide(false);
        }
    }

    private boolean shouldEnterFloatingWindow() {
        return floatingWindowEnabled && !requestingOverlayPermission
                && canDrawOverlayWindow() && floatingWindowController != null
                && !floatingWindowController.isShowing() && isPlaybackActive();
    }

    private boolean enterFloatingWindowIfNeeded() {
        if (!shouldEnterFloatingWindow()) return false;
        floatingWindowController.show();
        return floatingWindowController.isShowing();
    }

    private void scheduleFloatingWindowEntry() {
        mainHandler.removeCallbacks(enterFloatingWindowRunnable);
        if (shouldEnterFloatingWindow()) {
            mainHandler.postDelayed(enterFloatingWindowRunnable, 220);
        }
    }

    // ---- Choice panel ----

    private void showChoicePanel(String title, List<String> items, PopupSelection selection) {
        hideKeyboard();
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogChoicePanelBinding panelBinding = DialogChoicePanelBinding.inflate(getLayoutInflater());
        dialog.setContentView(panelBinding.getRoot());

        panelBinding.choiceTitle.setText(title);
        RecyclerView list = panelBinding.choiceList;
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new ChoiceAdapter(items, position -> {
            selection.onSelect(position);
            dialog.dismiss();
        }));
        dialog.show();
    }

    // ---- Utility ----

    private boolean isPlaybackActive() {
        return player != null && player.getPlayWhenReady()
                && (player.getPlaybackState() == Player.STATE_READY
                || player.getPlaybackState() == Player.STATE_BUFFERING);
    }

    private void updateKeepScreenOn() {
        boolean keepOn = isFullscreen || isPlaybackActive();
        Window window = getWindow();
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (channelFragment != null && channelFragment.getPlayerView() != null) {
            channelFragment.getPlayerView().setKeepScreenOn(keepOn);
        }
        if (floatingWindowController != null) {
            floatingWindowController.setKeepScreenOn(keepOn);
        }
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---- Lifecycle ----

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isFullscreen) {
            setFullscreen(true);
        }
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
            if (settingsFragment != null) settingsFragment.updateFloatingWindowSwitch(granted);
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
        if (floatingWindowController != null) floatingWindowController.hide(true);
        if (player != null) { player.release(); player = null; }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            setFullscreen(false);
            return;
        }
        Fragment visible = null;
        for (Fragment f : new Fragment[]{subscriptionFragment, settingsFragment}) {
            if (f != null && f.isVisible()) { visible = f; break; }
        }
        if (visible != null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_channels);
            return;
        }
        if (shouldEnterFloatingWindow()) {
            moveTaskToBack(true);
            scheduleFloatingWindowEntry();
            return;
        }
        super.onBackPressed();
    }

    // ---- Inner adapter for choice panel ----

    private static final class ChoiceAdapter extends RecyclerView.Adapter<ChoiceAdapter.VH> {
        private final List<String> items;
        private final PopupSelection selection;

        ChoiceAdapter(List<String> items, PopupSelection selection) {
            this.items = items;
            this.selection = selection;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(15);
            tv.setPadding(dp(parent, 16), dp(parent, 14), dp(parent, 16), dp(parent, 14));
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            android.util.TypedValue outValue = new android.util.TypedValue();
            parent.getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true);
            tv.setBackgroundResource(outValue.resourceId);

            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.textView.setText(items.get(position));

            android.util.TypedValue typedValue = new android.util.TypedValue();
            holder.textView.getContext().getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorOnSurface, typedValue, true);
            holder.textView.setTextColor(holder.textView.getContext().getColor(typedValue.resourceId));

            holder.textView.setOnClickListener(v -> selection.onSelect(holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private static int dp(View view, int value) {
            return Math.round(value * view.getResources().getDisplayMetrics().density);
        }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView textView;
            VH(TextView tv) { super(tv); this.textView = tv; }
        }
    }
}
