package com.example.livelink;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class ChannelAdapter extends BaseAdapter {
    private final Context context;
    private final List<Channel> channels = new ArrayList<>();
    private int cardColor = Color.rgb(21, 27, 46);
    private int titleColor = Color.rgb(245, 247, 251);
    private int detailColor = Color.rgb(139, 151, 172);

    ChannelAdapter(Context context) {
        this.context = context;
    }

    void setThemeColors(int cardColor, int titleColor, int detailColor) {
        this.cardColor = cardColor;
        this.titleColor = titleColor;
        this.detailColor = detailColor;
        notifyDataSetChanged();
    }

    void submit(List<Channel> nextChannels) {
        channels.clear();
        channels.addAll(nextChannels);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return channels.size();
    }

    @Override
    public Channel getItem(int position) {
        return channels.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            int horizontal = dp(14);
            int vertical = dp(10);
            row.setPadding(horizontal, vertical, horizontal, vertical);
            GradientDrawable background = new GradientDrawable();
            background.setColor(cardColor);
            background.setCornerRadius(dp(16));
            row.setBackground(background);

            TextView title = new TextView(context);
            title.setTextSize(16);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);

            TextView detail = new TextView(context);
            detail.setTextSize(12);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);

            row.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(24)));
            row.addView(detail, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(20)));

            holder = new Holder(title, detail);
            row.setTag(holder);
            convertView = row;
        } else {
            holder = (Holder) convertView.getTag();
        }

        Channel channel = channels.get(position);
        holder.title.setTextColor(titleColor);
        holder.detail.setTextColor(detailColor);
        holder.title.setText(channel.name);
        holder.detail.setText(channel.group + "  |  " + channel.url);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(66));
        params.setMargins(dp(10), dp(4), dp(10), dp(4));
        convertView.setLayoutParams(params);
        return convertView;
    }

    private int dp(int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static final class Holder {
        final TextView title;
        final TextView detail;

        Holder(TextView title, TextView detail) {
            this.title = title;
            this.detail = detail;
        }
    }
}
