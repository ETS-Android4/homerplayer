package com.studio4plus.homerplayer.ui.settings;

import static com.studio4plus.homerplayer.util.CollectionUtils.map;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.Preference;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.studio4plus.homerplayer.BuildConfig;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.concurrency.SimpleFuture;
import com.studio4plus.homerplayer.model.AudioBookManager;
import com.studio4plus.homerplayer.util.LifecycleAwareRunnable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

public class MainSettingsFragment extends BaseSettingsFragment {

    private static final String FILE_PROVIDER_AUTHORITY = "com.studio4plus.homerplayer.shared";

    private static final String KEY_RESET_ALL_BOOK_PROGRESS = "reset_all_book_progress_preference";
    private static final String KEY_AUDIOBOOKS_FOLDER = "audiobooks_folder_preference";
    private static final String KEY_FAQ = "faq_preference";
    private static final String KEY_SHARE_LOGS = "share_logs_preference";
    private static final String KEY_VERSION = "version_preference";

    private static final String FAQ_URL = "https://goo.gl/1RVxFW";

    @Inject public AudiobooksFolderManager folderManager;
    @Inject public AudioBookManager audioBookManager;
    @Inject public GlobalSettings globalSettings;
    @Inject public ShareLogs shareLogs;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        HomerPlayerApplication.getComponent(requireActivity()).inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);

        ConfirmDialogPreference preferenceResetProgress =
                getPreference(KEY_RESET_ALL_BOOK_PROGRESS);
        preferenceResetProgress.setOnConfirmListener(() -> {
            audioBookManager.resetAllBookProgress();
            Toast.makeText(
                    getActivity(),
                    R.string.pref_reset_all_book_progress_done,
                    Toast.LENGTH_SHORT).show();
        });
        setupAudiobooksFolder();
        setupFaq();
        setupShareLogs();
        updateVersionSummary();
    }

    @Override
    public void onStart() {
        super.onStart();
        updateKioskModeSummary();
        updateAudiobooksFolderSummary();
    }

    @Override
    protected int getTitle() {
        return R.string.settings_title;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch(key) {
            case GlobalSettings.KEY_SIMPLE_KIOSK_MODE:
            case GlobalSettings.KEY_KIOSK_MODE:
                updateKioskModeSummary();
                break;
            case GlobalSettings.KEY_AUDIOBOOKS_FOLDERS:
                updateAudiobooksFolderSummary();
        }
    }

    private void updateVersionSummary() {
        Preference preference = getPreference(KEY_VERSION);
        String versionString = String.format(
                Locale.US, "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
        preference.setSummary(versionString);
    }

    private void updateKioskModeSummary() {
        Preference kioskModeScreen = getPreference(GlobalSettings.KEY_KIOSK_MODE_SCREEN);

        int summaryStringId = R.string.pref_kiosk_mode_screen_summary_disabled;
        if (globalSettings.isFullKioskModeEnabled())
            summaryStringId = R.string.pref_kiosk_mode_screen_summary_full;
        else if (globalSettings.isSimpleKioskModeEnabled())
            summaryStringId = R.string.pref_kiosk_mode_screen_summary_simple;
        kioskModeScreen.setSummary(summaryStringId);
    }

    private void setupAudiobooksFolder() {
        Preference preference = getPreference(KEY_AUDIOBOOKS_FOLDER);
        preference.setOnPreferenceClickListener(ignore -> {
            startActivity(new Intent(requireContext(), SettingsFoldersActivity.class));
            return true;
        });
        updateAudiobooksFolderSummary();
    }

    private void updateAudiobooksFolderSummary() {
        Preference preference = getPreference(KEY_AUDIOBOOKS_FOLDER);
        preference.setVisible(!globalSettings.legacyFileAccessMode());
        Set<String> folderUriStrings = globalSettings.audiobooksFolders();
        if (folderUriStrings.isEmpty()) {
            preference.setSummary(R.string.pref_folder_audiobooks_summery_empty);
            new BlinkPrefSummary(mainHandler, preference);
        } else {
            List<DocumentFile> folders = folderManager.getFolders();
            List<String> folderNames = map(folders, DocumentFile::getName);
            Collections.sort(folderNames);
            String summary = TextUtils.join(", ", folderNames);
            preference.setSummary(summary);
        }
    }

    private void setupFaq() {
        Preference preference = getPreference(KEY_FAQ);
        preference.setSummary(getString(R.string.pref_help_faq_summary, FAQ_URL));
        preference.setOnPreferenceClickListener(preference1 -> {
            openUrl(FAQ_URL);
            return true;
        });
    }

    private void setupShareLogs() {
        Preference preference = getPreference(KEY_SHARE_LOGS);
        preference.setOnPreferenceClickListener(pref -> {
            SimpleFuture<File> shareFuture = shareLogs.shareLogs();
            shareFuture.addListener(new SimpleFuture.Listener<File>() {
                @Override
                public void onResult(@NonNull File result) {
                    Activity activity = getActivity();
                    if (isResumed() && activity != null) {
                        Uri shareUri = FileProvider.getUriForFile(activity, FILE_PROVIDER_AUTHORITY, result);
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_STREAM, shareUri);
                        try {
                            activity.startActivity(Intent.createChooser(intent, null));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(requireContext(), "Error: " + e.getMessage() , Toast.LENGTH_LONG).show();
                        }
                    }
                }

                @Override
                public void onException(@NonNull Throwable t) {
                    Activity activity = getActivity();
                    if (activity != null) {
                        Toast.makeText(activity, t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });

            return true;
        });
    }

    private static class BlinkPrefSummary extends LifecycleAwareRunnable {

        private final Preference preference;
        private final CharSequence summary;
        private int count = 6;

        private static final int DELAY_MS = 500;

        public BlinkPrefSummary(@NonNull Handler handler, @NonNull Preference preference) {
            super(handler);
            this.preference = preference;
            this.summary = preference.getSummary();
            handler.postDelayed(this, DELAY_MS);
        }

        @Override
        public void run() {
            preference.setSummary((count % 2 == 0) ? " " : summary);
            if (--count > 0)
                handler.postDelayed(this, DELAY_MS);
        }
    }
}
