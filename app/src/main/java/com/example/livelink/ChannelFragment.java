package com.example.livelink;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.livelink.databinding.FragmentChannelBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChannelFragment extends Fragment {

    public interface Host {
        void onSubscriptionPickerRequested();
        void onGroupPickerRequested(List<String> groups);
        void onRefreshRequested();
        void onChannelSelected(Channel channel);
        void onFullscreenRequested(boolean enter);
        void onLockRequested();
        void onUnlockRequested();
        void onControllerVisibilityChanged(boolean visible);
        boolean isFullscreenLocked();
        boolean isFullscreen();
        String getActiveSourceName();
        String getSelectedChannelUrl();
    }

    private FragmentChannelBinding binding;
    private ChannelListAdapter adapter;
    private Host host;
    private ImageButton fullscreenButton;
    private ImageButton minimalFullscreenButton;

    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Channel> filteredChannels = new ArrayList<>();
    private String selectedGroup = "全部";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        host = (Host) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChannelBinding.inflate(inflater, container, false);
        setupViews();
        return binding.getRoot();
    }

    private void setupViews() {
        adapter = new ChannelListAdapter();
        adapter.setOnChannelClickListener(channel -> {
            if (host != null) {
                host.onChannelSelected(channel);
            }
        });
        binding.channelList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.channelList.setAdapter(adapter);

        binding.subscriptionPicker.setOnClickListener(v -> {
            if (host != null) host.onSubscriptionPickerRequested();
        });

        binding.refreshButton.setOnClickListener(v -> {
            if (host != null) host.onRefreshRequested();
        });

        binding.groupPicker.setOnClickListener(v -> {
            if (host != null) {
                List<String> groups = PlaylistParser.groupsFor(allChannels);
                if (groups.isEmpty()) groups.add("全部");
                host.onGroupPickerRequested(groups);
            }
        });

        binding.searchInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                applyFilters();
            }
        });

        bindFullscreenControls();
        binding.playerView.post(this::bindFullscreenControls);
        updateFullscreenButtonIcon(false);

        binding.lockButton.setImageDrawable(UiIcons.lock(false, dp(26), 0xFFFFFFFF));
        binding.lockButton.setOnClickListener(v -> {
            if (host != null) host.onLockRequested();
        });
        binding.unlockButton.setImageDrawable(UiIcons.lock(true, dp(26), 0xFFFFFFFF));
        binding.unlockButton.setOnClickListener(v -> {
            if (host != null) host.onUnlockRequested();
        });

        binding.playerView.setControllerVisibilityListener(
                (androidx.media3.ui.PlayerView.ControllerVisibilityListener) visibility -> {
                    if (host != null) {
                        host.onControllerVisibilityChanged(visibility == View.VISIBLE);
                    }
                });

        binding.playerContainer.setOnClickListener(v -> {
            if (host != null && host.isFullscreen() && host.isFullscreenLocked()) {
                binding.unlockButton.setVisibility(
                        binding.unlockButton.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        updateEmptyState();
    }

    private void bindFullscreenControls() {
        if (binding == null) return;
        fullscreenButton = binding.playerView.findViewById(androidx.media3.ui.R.id.exo_fullscreen);
        minimalFullscreenButton = binding.playerView.findViewById(androidx.media3.ui.R.id.exo_minimal_fullscreen);
        View.OnClickListener fullscreenClickListener = v -> {
            if (host != null) host.onFullscreenRequested(!host.isFullscreen());
        };
        if (fullscreenButton != null) {
            fullscreenButton.setVisibility(View.VISIBLE);
            fullscreenButton.setOnClickListener(fullscreenClickListener);
        }
        if (minimalFullscreenButton != null) {
            minimalFullscreenButton.setVisibility(View.VISIBLE);
            minimalFullscreenButton.setOnClickListener(fullscreenClickListener);
        }
        updateFullscreenButtonIcon(host != null && host.isFullscreen());
    }

    public void setChannels(List<Channel> channels) {
        allChannels.clear();
        allChannels.addAll(channels);
        selectedGroup = "全部";
        if (binding != null) {
            binding.groupPicker.setText(selectedGroup);
        }
        applyFilters();
    }

    public void setSelectedGroup(String group) {
        this.selectedGroup = group;
        if (binding != null) {
            binding.groupPicker.setText(group);
        }
        applyFilters();
    }

    public void setSelectedChannelUrl(String url) {
        if (adapter != null) {
            adapter.setSelectedUrl(url);
        }
    }

    public void setSubscriptionPickerText(String text) {
        if (binding != null) {
            binding.subscriptionPicker.setText(text);
        }
    }

    public void setActiveSourceText(String text) {
        if (binding != null) {
            binding.activeSourceText.setText(text == null || text.trim().isEmpty()
                    ? getString(R.string.no_subscription_selected) : text.trim());
        }
    }

    public void setStatusText(String text) {
        if (binding != null) {
            binding.channelCountText.setText(text);
        }
    }

    public void setLoading(boolean loading) {
        if (binding != null) {
            binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.refreshButton.setEnabled(!loading);
        }
    }

    public void showVideoLoading(String message) {
        if (binding == null) return;
        binding.videoProgressBar.setVisibility(View.VISIBLE);
        binding.playerStatusText.setText(message);
        binding.playerFeedback.setVisibility(View.VISIBLE);
    }

    public void showVideoError(String message) {
        if (binding == null) return;
        binding.videoProgressBar.setVisibility(View.GONE);
        binding.playerStatusText.setText(message);
        binding.playerFeedback.setVisibility(View.VISIBLE);
    }

    public void hideVideoFeedback() {
        if (binding != null) {
            binding.playerFeedback.setVisibility(View.GONE);
        }
    }

    public void updatePlayerInfoText(String text) {
        if (binding != null) {
            binding.playerInfoText.setText(text);
        }
    }

    public View getPlayerContainer() {
        return binding != null ? binding.playerContainer : null;
    }

    public androidx.media3.ui.PlayerView getPlayerView() {
        return binding != null ? binding.playerView : null;
    }

    public View getTopBar() {
        return binding != null ? binding.topBar : null;
    }

    public View getChannelControls() {
        return binding != null ? binding.channelControls : null;
    }

    public View getChannelList() {
        return binding != null ? binding.channelList : null;
    }

    public View getEmptyStateText() {
        return binding != null ? binding.emptyStateText : null;
    }

    public View getLockButton() {
        return binding != null ? binding.lockButton : null;
    }

    public View getUnlockButton() {
        return binding != null ? binding.unlockButton : null;
    }

    public View getFullscreenInfoText() {
        return binding != null ? binding.fullscreenInfoText : null;
    }

    public void updateFullscreenButtonIcon(boolean isFullscreen) {
        if (binding == null) return;
        int icon = isFullscreen
                ? androidx.media3.ui.R.drawable.exo_styled_controls_fullscreen_exit
                : androidx.media3.ui.R.drawable.exo_styled_controls_fullscreen_enter;
        String description = getString(isFullscreen ? R.string.exit_fullscreen : R.string.fullscreen);
        updateFullscreenButton(fullscreenButton, icon, description);
        updateFullscreenButton(minimalFullscreenButton, icon, description);
    }

    private void updateFullscreenButton(ImageButton button, int icon, String description) {
        if (button == null) return;
        button.setImageResource(icon);
        button.setContentDescription(description);
    }

    private void applyFilters() {
        if (binding == null) return;
        String query = binding.searchInput.getText() == null ? ""
                : binding.searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);

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

        adapter.setChannels(filteredChannels);
        if (host != null) {
            adapter.setSelectedUrl(host.getSelectedChannelUrl());
        }
        updateEmptyState();

        if (!allChannels.isEmpty()) {
            binding.channelCountText.setText(
                    getString(R.string.channel_filter_count_format,
                            filteredChannels.size(), allChannels.size()));
        }
    }

    private void updateEmptyState() {
        if (binding == null) return;
        boolean empty = filteredChannels.isEmpty();
        binding.emptyStateText.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.channelList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        fullscreenButton = null;
        minimalFullscreenButton = null;
        binding = null;
    }
}
