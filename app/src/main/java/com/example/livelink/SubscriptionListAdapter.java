package com.example.livelink;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.livelink.databinding.ItemSubscriptionBinding;

import java.util.ArrayList;
import java.util.List;

public final class SubscriptionListAdapter extends RecyclerView.Adapter<SubscriptionListAdapter.ViewHolder> {

    public interface OnSubscriptionClickListener {
        void onClick(int position);
    }

    public interface OnSubscriptionLongClickListener {
        void onLongClick(int position);
    }

    private final List<Subscription> subscriptions = new ArrayList<>();
    private String activeId = "";
    private OnSubscriptionClickListener clickListener;
    private OnSubscriptionLongClickListener longClickListener;

    public void setOnClickListener(OnSubscriptionClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnLongClickListener(OnSubscriptionLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setSubscriptions(List<Subscription> newSubscriptions) {
        subscriptions.clear();
        subscriptions.addAll(newSubscriptions);
        notifyDataSetChanged();
    }

    public void setActiveId(String id) {
        String oldId = this.activeId;
        this.activeId = id == null ? "" : id;
        if (!oldId.equals(this.activeId)) {
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSubscriptionBinding binding = ItemSubscriptionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Subscription subscription = subscriptions.get(position);
        boolean selected = subscription.id.equals(activeId);

        holder.binding.subscriptionName.setText(subscription.name);
        holder.binding.subscriptionUrl.setText(subscription.url);

        if (selected) {
            holder.binding.subscriptionCard.setStrokeColor(
                    holder.itemView.getContext().getColor(R.color.primary));
            holder.binding.subscriptionCard.setStrokeWidth(dp(holder.itemView, 2));
            holder.binding.activeIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.binding.subscriptionCard.setStrokeColor(
                    resolveAttrColor(holder.itemView, com.google.android.material.R.attr.colorOutlineVariant));
            holder.binding.subscriptionCard.setStrokeWidth(dp(holder.itemView, 1));
            holder.binding.activeIndicator.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onClick(holder.getAdapterPosition());
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(holder.getAdapterPosition());
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return subscriptions.size();
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
        final ItemSubscriptionBinding binding;

        ViewHolder(ItemSubscriptionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
