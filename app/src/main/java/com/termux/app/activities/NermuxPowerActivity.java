package com.termux.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.app.NermuxHomeActivity;

public class NermuxPowerActivity extends AppCompatActivity {

    public static Intent newInstance(Context context, @Nullable String startDirectory) {
        return NermuxHomeActivity.newInstance(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(NermuxHomeActivity.newInstance(this));
        finish();
        overridePendingTransition(0, 0);
    }
}
