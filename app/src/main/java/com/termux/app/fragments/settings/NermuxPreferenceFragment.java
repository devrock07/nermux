package com.termux.app.fragments.settings;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

public abstract class NermuxPreferenceFragment extends PreferenceFragmentCompat {

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.setBackgroundResource(R.drawable.nermux_settings_screen_background);

        RecyclerView listView = getListView();
        listView.setBackgroundColor(Color.TRANSPARENT);
        listView.setClipToPadding(false);
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listView.setPadding(dp(8), dp(10), dp(8), dp(24));
        listView.setItemAnimator(null);
        listView.setAlpha(0f);
        listView.setTranslationY(dp(8));
        listView.animate().alpha(1f).translationY(0f).setDuration(160).start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
