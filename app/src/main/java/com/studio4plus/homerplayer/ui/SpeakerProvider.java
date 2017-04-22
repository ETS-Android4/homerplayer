package com.studio4plus.homerplayer.ui;

import android.support.annotation.NonNull;

import com.studio4plus.homerplayer.util.SimpleFuture;

public interface SpeakerProvider {
    @NonNull SimpleFuture<Speaker> obtainTts();
}
