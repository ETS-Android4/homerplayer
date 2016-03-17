package com.studio4plus.homerplayer.service;

import android.net.Uri;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.FileDataSource;
import com.google.common.base.Preconditions;

import java.io.File;
import java.util.Iterator;
import java.util.List;

public class Player {

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 128;

    private final Allocator exoAllocator;
    private final ExoPlayer exoPlayer;

    public Player() {
        exoPlayer = ExoPlayer.Factory.newInstance(1);
        exoAllocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);
    }

    public PlaybackController createPlayback() {
        return new PlaybackControllerImpl();
    }

    public DurationQueryController createDurationQuery(List<File> files) {
        return new DurationQueryControllerImpl(files);
    }

    private void prepareAudioFile(File file, long startPositionMs) {
        Uri fileUri = Uri.fromFile(file);

        DataSource dataSource = new FileDataSource();
        SampleSource source = new ExtractorSampleSource(
                fileUri, dataSource, exoAllocator, BUFFER_SEGMENT_SIZE * BUFFER_SEGMENT_COUNT);
        MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(
                source, MediaCodecSelector.DEFAULT);
        exoPlayer.seekTo(startPositionMs);
        exoPlayer.prepare(audioRenderer);
    }

    private class PlaybackControllerImpl
            extends SimpleExoPlayerListener implements PlaybackController {

        private File currentFile;
        private Observer observer;

        private PlaybackControllerImpl() {
            exoPlayer.setPlayWhenReady(true);  // Call before setting the listener.
            exoPlayer.addListener(this);
        }

        @Override
        public void setObserver(Observer observer) {
            this.observer = observer;
        }

        @Override
        public void start(File currentFile, long startPositionMs) {
            Preconditions.checkNotNull(observer);
            this.currentFile = currentFile;
            prepareAudioFile(currentFile, startPositionMs);
        }

        public void stop() {
            exoPlayer.stop();
            observer.onPlaybackStopped(exoPlayer.getCurrentPosition());
        }

        @Override
        public void release() {
            exoPlayer.stop();
        }

        @Override
        public long getCurrentPosition() {
            return exoPlayer.getCurrentPosition();
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch(playbackState) {
                case ExoPlayer.STATE_READY:
                    observer.onPlaybackStarted();
                    observer.onDuration(currentFile, exoPlayer.getDuration());
                    break;
                case ExoPlayer.STATE_ENDED:
                    observer.onPlaybackEnded();
                    break;
                case ExoPlayer.STATE_IDLE:
                    exoPlayer.release();
                    exoPlayer.removeListener(this);
                    observer.onPlayerReleased();
                    break;
            }
        }
    }

    private class DurationQueryControllerImpl
            extends SimpleExoPlayerListener implements DurationQueryController {

        private final Iterator<File> iterator;
        private File currentFile;
        private Observer observer;
        private boolean releaseOnIdle = false;

        private DurationQueryControllerImpl(List<File> files) {
            Preconditions.checkArgument(!files.isEmpty());
            this.iterator = files.iterator();
        }

        @Override
        public void start(Observer observer) {
            this.observer = observer;
            exoPlayer.setPlayWhenReady(false);  // Call before setting the listener.
            exoPlayer.addListener(this);
            processNextFile();
        }

        @Override
        public void stop() {
            releaseOnIdle = true;
            exoPlayer.stop();
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch(playbackState) {
                case ExoPlayer.STATE_READY:
                    observer.onDuration(currentFile, exoPlayer.getDuration());
                    boolean hasNext = processNextFile();
                    if (!hasNext)
                        exoPlayer.stop();
                    break;
                case ExoPlayer.STATE_IDLE:
                    exoPlayer.removeListener(this);
                    if (releaseOnIdle) {
                        exoPlayer.release();
                        observer.onPlayerReleased();
                    } else {
                        observer.onFinished();
                    }
                    break;
            }
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            releaseOnIdle = true;
        }

        private boolean processNextFile() {
            boolean hasNext = iterator.hasNext();
            if (hasNext) {
                currentFile = iterator.next();
                prepareAudioFile(currentFile, 0);
            }
            return hasNext;
        }
    }
}
