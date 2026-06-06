package com.example.livelink;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class SubscriptionListAdapter extends ArrayAdapter<String> {
    interface StringProvider {
        String get();
    }

    interface ColorProvider {
        int get();
    }

    private final List<Subscription> subscriptions;
    private final StringProvider activeIdProvider;
    private final int primaryColor;
    private final ColorProvider textColorProvider;
    private final ColorProvider cardColorProvider;

    SubscriptionListAdapter(
            Context context,
            List<Subscription> subscriptions,
            StringProvider activeIdProvider,
            int primaryColor,
            ColorProvider textColorProvider,
            ColorProvider cardColorProvider) {
        super(context, android.R.layout.simple_list_item_1, new ArrayList<>());
        this.subscriptions = subscriptions;
        this.activeIdProvider = activeIdProvider;
        this.primaryColor = primaryColor;
        this.textColorProvider = textColorProvider;
        this.cardColorProvider = cardColorProvider;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout container;
        TextView label;
        if (convertView instanceof LinearLayout
                && ((LinearLayout) convertView).getChildCount() > 0
                && ((LinearLayout) convertView).getChildAt(0) instanceof TextView) {
            container = (LinearLayout) convertView;
            label = (TextView) container.getChildAt(0);
        } else {
            container = new LinearLayout(getContext());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(0, dp(4), 0, dp(4));
            container.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            label = new TextView(getContext());
            label.setTextSize(14);
            label.setPadding(dp(14), dp(12), dp(14), dp(12));
            label.setSingleLine(false);
            container.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        boolean selected = isSelected(position);
        label.setText(getItem(position));
        label.setTextColor(selected ? primaryColor : textColorProvider.get());
        label.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        label.setBackground(selected
                ? roundRectWithStroke(0x262F7CFF, 18, primaryColor, 2)
                : roundRect(cardColorProvider.get(), 18));
        return container;
    }

    private boolean isSelected(int position) {
        String activeId = activeIdProvider.get();
        return activeId != null
                && position >= 0
                && position < subscriptions.size()
                && activeId.equals(subscriptions.get(position).id);
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundRectWithStroke(int color, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = roundRect(color, radiusDp);
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
