package com.example.livelink;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.livelink.databinding.ItemChannelBinding;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public final class ChannelListAdapter extends RecyclerView.Adapter<ChannelListAdapter.ViewHolder> {

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    private final List<Channel> channels = new ArrayList<>();
    private String selectedUrl = "";
    private OnChannelClickListener listener;

    public void setOnChannelClickListener(OnChannelClickListener listener) {
        this.listener = listener;
    }

    public void setChannels(List<Channel> newChannels) {
        channels.clear();
        channels.addAll(newChannels);
        notifyDataSetChanged();
    }

    public void setSelectedUrl(String url) {
        String oldUrl = this.selectedUrl;
        this.selectedUrl = url == null ? "" : url;
        if (!oldUrl.equals(this.selectedUrl)) {
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChannelBinding binding = ItemChannelBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel channel = channels.get(position);
        boolean selected = channel.url.equals(selectedUrl);

        holder.binding.channelName.setText(channel.name);

        if (selected) {
            holder.binding.channelGroup.setText(
                    holder.itemView.getContext().getString(R.string.playing_group_format, channel.group));
        } else {
            holder.binding.channelGroup.setText(channel.group);
        }

        MaterialCardView card = holder.binding.channelCard;
        if (selected) {
            card.setStrokeColor(card.getContext().getColor(R.color.primary));
            card.setStrokeWidth(dp(card, 2));
            card.setCardBackgroundColor(card.getContext().getColor(R.color.channel_selected_bg));
        } else {
            card.setStrokeColor(card.getContext().getColor(R.color.outline_variant));
            card.setStrokeWidth(dp(card, 1));
            card.setCardBackgroundColor(com.google.android.material.R.attr.colorSurface);
            card.setCardBackgroundColor(resolveAttrColor(card, com.google.android.material.R.attr.colorSurface));
        }

        holder.binding.channelName.setTextColor(selected
                ? card.getContext().getColor(R.color.primary)
                : resolveAttrColor(card, com.google.android.material.R.attr.colorOnSurface));
        holder.binding.channelGroup.setTextColor(selected
                ? card.getContext().getColor(R.color.primary)
                : resolveAttrColor(card, com.google.android.material.R.attr.colorOnSurfaceVariant));

        holder.binding.playButton.setText(selected ? R.string.playing : R.string.play);
        if (selected) {
            holder.binding.playButton.setBackgroundTintList(
                    ColorStateList.valueOf(card.getContext().getColor(R.color.primary)));
            holder.binding.playButton.setTextColor(card.getContext().getColor(R.color.on_primary));
        } else {
            holder.binding.playButton.setBackgroundTintList(
                    ColorStateList.valueOf(card.getContext().getColor(R.color.channel_play_btn_bg)));
            holder.binding.playButton.setTextColor(card.getContext().getColor(R.color.primary));
        }

        View.OnClickListener clickListener = v -> {
            if (listener != null) {
                listener.onChannelClick(channel);
            }
        };
        holder.itemView.setOnClickListener(clickListener);
        holder.binding.playButton.setOnClickListener(clickListener);
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static int resolveAttrColor(View view, int attr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        view.getContext().getTheme().resolveAttribute(attr, typedValue, true);
        return view.getContext().getColor(typedValue.resourceId);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ItemChannelBinding binding;

        ViewHolder(ItemChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
