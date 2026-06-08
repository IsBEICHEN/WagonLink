package com.example.livelink;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.livelink.databinding.FragmentSettingsBinding;

public final class SettingsFragment extends Fragment {

    public interface Host {
        void onThemePickerRequested();
        void onFloatingWindowToggled(boolean enabled);
        boolean isFloatingWindowEnabled();
        boolean canDrawOverlayWindow();
        String getThemeMode();
    }

    private FragmentSettingsBinding binding;
    private Host host;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        host = (Host) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        setupViews();
        return binding.getRoot();
    }

    private void setupViews() {
        updateThemeButton();

        binding.versionText.setText(getString(R.string.version_format, BuildConfig.VERSION_NAME));

        binding.themePicker.setOnClickListener(v -> {
            if (host != null) host.onThemePickerRequested();
        });

        boolean checked = host != null && host.isFloatingWindowEnabled() && host.canDrawOverlayWindow();
        binding.floatingWindowSwitch.setChecked(checked);
        binding.floatingWindowSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (host != null) host.onFloatingWindowToggled(isChecked);
        });
    }

    public void updateThemeButton() {
        if (binding == null || host == null) return;
        String mode = host.getThemeMode();
        String label;
        if (AppPreferences.THEME_LIGHT.equals(mode)) {
            label = getString(R.string.theme_light);
        } else if (AppPreferences.THEME_DARK.equals(mode)) {
            label = getString(R.string.theme_dark);
        } else {
            label = getString(R.string.theme_system);
        }
        binding.themePicker.setText(getString(R.string.theme_mode) + "  " + label);
    }

    public void updateFloatingWindowSwitch(boolean checked) {
        if (binding == null) return;
        binding.floatingWindowSwitch.setOnCheckedChangeListener(null);
        binding.floatingWindowSwitch.setChecked(checked);
        binding.floatingWindowSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (host != null) host.onFloatingWindowToggled(isChecked);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
