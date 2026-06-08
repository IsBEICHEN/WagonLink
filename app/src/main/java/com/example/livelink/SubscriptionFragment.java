package com.example.livelink;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.livelink.databinding.DialogSubscriptionEditorBinding;
import com.example.livelink.databinding.FragmentSubscriptionBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

public final class SubscriptionFragment extends Fragment {

    public interface Host {
        void onLoadSubscription(int position);
        void onEditSubscription(int position);
        void onDeleteSubscription(String id);
        void onSaveSubscription(String id, String name, String url);
        void onShowSubscriptionActions(int position);
        List<Subscription> getSubscriptions();
        String getActiveSubscriptionId();
    }

    private FragmentSubscriptionBinding binding;
    private SubscriptionListAdapter adapter;
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
        binding = FragmentSubscriptionBinding.inflate(inflater, container, false);
        setupViews();
        return binding.getRoot();
    }

    private void setupViews() {
        adapter = new SubscriptionListAdapter();
        adapter.setOnClickListener(position -> {
            if (host != null) host.onLoadSubscription(position);
        });
        adapter.setOnLongClickListener(position -> {
            if (host != null) host.onShowSubscriptionActions(position);
        });

        binding.subscriptionList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.subscriptionList.setAdapter(adapter);

        binding.addSubscriptionButton.setOnClickListener(v -> showEditor(null));
    }

    public void refreshList() {
        if (host == null || adapter == null) return;
        List<Subscription> subs = host.getSubscriptions();
        adapter.setSubscriptions(subs);
        adapter.setActiveId(host.getActiveSubscriptionId());
    }

    public void setStatusText(String text) {
        if (binding != null) {
            binding.subscriptionStatusText.setText(text);
        }
    }

    public void showEditor(@Nullable Subscription subscription) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        DialogSubscriptionEditorBinding editorBinding =
                DialogSubscriptionEditorBinding.inflate(getLayoutInflater());
        dialog.setContentView(editorBinding.getRoot());

        boolean isNew = subscription == null;
        editorBinding.editorTitle.setText(isNew ? R.string.add_subscription : R.string.edit_subscription);
        editorBinding.deleteButton.setVisibility(isNew ? View.GONE : View.VISIBLE);

        if (subscription != null) {
            editorBinding.subscriptionNameInput.setText(subscription.name);
            editorBinding.subscriptionUrlInput.setText(subscription.url);
        }

        editorBinding.saveButton.setOnClickListener(v -> {
            String url = editorBinding.subscriptionUrlInput.getText() == null ? ""
                    : editorBinding.subscriptionUrlInput.getText().toString().trim();
            if (url.isEmpty()) {
                setStatusText(getString(R.string.enter_url));
                return;
            }
            String name = editorBinding.subscriptionNameInput.getText() == null ? ""
                    : editorBinding.subscriptionNameInput.getText().toString();
            String id = isNew ? "" : subscription.id;
            hideKeyboard(editorBinding.subscriptionUrlInput);
            dialog.dismiss();
            if (host != null) host.onSaveSubscription(id, name, url);
        });

        editorBinding.deleteButton.setOnClickListener(v -> {
            hideKeyboard(editorBinding.subscriptionUrlInput);
            dialog.dismiss();
            if (host != null && subscription != null) {
                host.onDeleteSubscription(subscription.id);
            }
        });

        dialog.show();
        editorBinding.subscriptionUrlInput.requestFocus();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
