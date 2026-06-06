package com.example.livelink;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

final class ThemedListAdapter extends ArrayAdapter<String> {
    private final int textColor;
    private final int backgroundColor;

    ThemedListAdapter(Context context, int textColor, int backgroundColor) {
        super(context, android.R.layout.simple_list_item_1, new ArrayList<>());
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
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
        view.setTextColor(textColor);
        view.setBackground(roundRect(backgroundColor));
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(getContext(), 54)));
        return view;
    }

    private static GradientDrawable roundRect(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
