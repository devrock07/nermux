package com.termux.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.media.AudioManager;

import com.termux.R;

import java.util.HashSet;
import java.util.Set;

public final class NermuxSoundEffects {

    private static final float DEFAULT_VOLUME = 0.72f;
    private static NermuxSoundEffects sInstance;

    private final SoundPool mSoundPool;
    private final ToneGenerator mFallbackTone;
    private final Set<Integer> mLoadedSounds = new HashSet<>();
    private final int mKeyboardSound;
    private final int mPanelSound;
    private final int mScanSound;
    private final int mGrantedSound;
    private final int mErrorSound;

    private NermuxSoundEffects(Context context) {
        Context appContext = context.getApplicationContext();
        AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        mSoundPool = new SoundPool.Builder()
            .setAudioAttributes(attributes)
            .setMaxStreams(4)
            .build();
        mSoundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0) mLoadedSounds.add(sampleId);
        });
        mFallbackTone = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
        mKeyboardSound = mSoundPool.load(appContext, R.raw.edex_keyboard, 1);
        mPanelSound = mSoundPool.load(appContext, R.raw.edex_panels, 1);
        mScanSound = mSoundPool.load(appContext, R.raw.edex_scan, 1);
        mGrantedSound = mSoundPool.load(appContext, R.raw.edex_granted, 1);
        mErrorSound = mSoundPool.load(appContext, R.raw.edex_error, 1);
    }

    public static synchronized NermuxSoundEffects get(Context context) {
        if (sInstance == null) sInstance = new NermuxSoundEffects(context);
        return sInstance;
    }

    public void tap() {
        play(mKeyboardSound, DEFAULT_VOLUME);
    }

    public void commandSubmit() {
        play(mScanSound, 0.66f);
    }

    public void boot() {
        play(mScanSound, 0.6f);
    }

    public void panel() {
        play(mPanelSound, 0.68f);
    }

    public void scan() {
        play(mScanSound, 0.54f);
    }

    public void success() {
        play(mGrantedSound, 0.7f);
    }

    public void error() {
        play(mErrorSound, 0.68f);
    }

    private void play(int soundId, float volume) {
        if (soundId == 0) {
            mFallbackTone.startTone(ToneGenerator.TONE_PROP_BEEP, 28);
            return;
        }
        if (!mLoadedSounds.contains(soundId)) {
            mFallbackTone.startTone(ToneGenerator.TONE_PROP_BEEP, 24);
            return;
        }
        mSoundPool.play(soundId, volume, volume, 1, 0, 1f);
    }
}
