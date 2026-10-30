package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.Gravity;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.lyrics.LrcLibClient;
import com.liskovsoft.smartyoutubetv2.common.misc.lyrics.Lyrics;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows time-synced song lyrics in a side panel while the video plays.
 */
public class LyricsController extends BasePlayerController {
    private static final String TAG = LyricsController.class.getSimpleName();
    private static final long TICK_MS = 100;
    // The panel takes the rest of the screen (see LyricsView)
    private static final int VIDEO_ZOOM_PERCENTS = 62;
    // Music videos often have an intro before the song starts, so the range goes well beyond a few seconds
    private static final long[] OFFSETS_MS = {-10_000, -5_000, -2_000, -1_000, -500, 0, 500, 1_000, 2_000, 5_000, 10_000};
    private static final String OFFSETS_PREFS = "lyrics_offsets";
    private Disposable mLyricsAction;
    private Lyrics mLyrics;
    private String mLyricsVideoId;
    // Video that was already looked up (or is being looked up). Prevents repeated queries for songs that aren't found.
    private String mLookupVideoId;
    private long mOffsetMs;
    private boolean mIsPanelShown;
    private final Runnable mTick = this::tick;

    @Override
    public void onNewVideo(Video item) {
        if (item != null && !TextUtils.equals(item.videoId, mLyricsVideoId)) {
            disposeActions();
            mLyrics = null;
            mLyricsVideoId = null;
            mLookupVideoId = null;
            mOffsetMs = loadOffset(item.videoId);
            hidePanel();
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        if (getPlayer() == null) {
            return;
        }

        getPlayer().setButtonState(R.id.action_lyrics, getPlayerTweaksData().isLyricsEnabled() ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);

        if (getPlayerTweaksData().isLyricsEnabled()) {
            loadLyrics(item, false);
        }
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        // The title may be unknown until the metadata arrives (e.g. a video opened from a link).
        // Wait for the duration too, it tells apart versions of a song. onVideoLoaded() covers the opposite order.
        if (getPlayerTweaksData().isLyricsEnabled() && getPlayer() != null && getPlayer().getDurationMs() > 0) {
            loadLyrics(getVideo(), false);
        }
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_lyrics) {
            enableLyrics(buttonState != PlayerUI.BUTTON_ON);
        }
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        if (buttonId != R.id.action_lyrics) {
            return;
        }

        AppDialogPresenter dialogPresenter = getAppDialogPresenter();
        String title = getContext().getString(R.string.lyrics_offset);

        if (mLyrics != null && mLyrics.getFirstLineStartMs() >= 0) {
            // The quickest fix for a video with an intro: press when the singing starts
            dialogPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.lyrics_sync_now), optionItem -> {
                if (getPlayer() != null && mLyrics != null) {
                    setOffset(getPlayer().getPositionMs() - mLyrics.getFirstLineStartMs());
                }
                dialogPresenter.closeDialog();
            }));
        }

        List<OptionItem> options = new ArrayList<>();
        boolean isCustomOffset = true;

        for (long offsetMs : OFFSETS_MS) {
            isCustomOffset &= offsetMs != mOffsetMs;
            options.add(UiOptionItem.from(getContext().getString(R.string.lyrics_offset_value, formatOffset(offsetMs)),
                    optionItem -> {
                        setOffset(offsetMs);
                        dialogPresenter.closeDialog();
                    },
                    mOffsetMs == offsetMs));
        }

        if (isCustomOffset) {
            // Set with "sync now": show it, so it's clear the lyrics are shifted
            long customOffset = mOffsetMs;
            options.add(UiOptionItem.from(getContext().getString(R.string.lyrics_offset_value, formatOffset(customOffset)),
                    optionItem -> dialogPresenter.closeDialog(), true));
        }

        dialogPresenter.appendRadioCategory(title, options);
        dialogPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.lyrics_hide), optionItem -> {
            enableLyrics(false);
            dialogPresenter.closeDialog();
        }));

        dialogPresenter.showDialog(title);
    }

    private static String formatOffset(long offsetMs) {
        return String.format(Locale.getDefault(), offsetMs > 0 ? "+%.1f" : "%.1f", offsetMs / 1_000f);
    }

    /**
     * Remembered per video, so the lyrics stay in sync next time
     */
    private void setOffset(long offsetMs) {
        mOffsetMs = offsetMs;

        Video video = getVideo();

        if (video == null || video.videoId == null) {
            return;
        }

        SharedPreferences prefs = getContext().getSharedPreferences(OFFSETS_PREFS, Context.MODE_PRIVATE);

        if (offsetMs == 0) {
            prefs.edit().remove(video.videoId).apply();
        } else {
            prefs.edit().putLong(video.videoId, offsetMs).apply();
        }
    }

    private long loadOffset(String videoId) {
        if (videoId == null || getContext() == null) {
            return 0;
        }

        return getContext().getSharedPreferences(OFFSETS_PREFS, Context.MODE_PRIVATE).getLong(videoId, 0);
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
        Utils.removeCallbacks(mTick);
    }

    @Override
    public void onFinish() {
        disposeActions();
        hidePanel();
    }

    private void enableLyrics(boolean enabled) {
        getPlayerTweaksData().setLyricsEnabled(enabled);

        if (getPlayer() != null) {
            getPlayer().setButtonState(R.id.action_lyrics, enabled ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }

        if (enabled) {
            loadLyrics(getVideo(), true);
        } else {
            disposeActions();
            hidePanel();
        }
    }

    /**
     * @param userAction show progress and "not found" messages only when the user asked for lyrics
     */
    private void loadLyrics(Video video, boolean userAction) {
        if (video == null || video.isLive || getPlayer() == null) {
            return;
        }

        if (mLyrics != null && TextUtils.equals(video.videoId, mLyricsVideoId)) {
            showPanel(mLyrics);
            return;
        }

        String title = video.getOriginalTitle();

        if (title == null || (!userAction && TextUtils.equals(video.videoId, mLookupVideoId))) {
            return;
        }

        disposeActions();
        mLookupVideoId = video.videoId;

        if (userAction) {
            MessageHelpers.showMessage(getContext(), R.string.lyrics_searching);
        }

        String channel = video.getAuthor();
        long durationMs = getPlayer().getDurationMs() > 0 ? getPlayer().getDurationMs() : video.getDurationMs();
        String videoId = video.videoId;

        mLyricsAction = RxHelper.execute(
                RxHelper.fromCallable(() -> LrcLibClient.find(title, channel, Math.max(durationMs, 0))),
                lyrics -> {
                    mLyrics = lyrics;
                    mLyricsVideoId = videoId;
                    if (getPlayerTweaksData().isLyricsEnabled()) {
                        showPanel(lyrics);
                    }
                },
                error -> {
                    Log.d(TAG, "No lyrics for %s: %s", title, error.getMessage());
                    if (userAction) {
                        MessageHelpers.showMessage(getContext(), R.string.lyrics_not_found);
                    }
                }
        );
    }

    private void showPanel(Lyrics lyrics) {
        if (getPlayer() == null) {
            return;
        }

        getPlayer().setLyrics(lyrics);

        // Make room for the panel: shrink the video and pin it to the left.
        // Applied every time because the engine resets the zoom when it's recreated.
        getPlayer().setZoomPercents(VIDEO_ZOOM_PERCENTS);
        getPlayer().setVideoGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        mIsPanelShown = true;

        Utils.removeCallbacks(mTick);
        tick();
    }

    private void hidePanel() {
        Utils.removeCallbacks(mTick);

        if (getPlayer() == null) {
            mIsPanelShown = false;
            return;
        }

        getPlayer().setLyrics(null);

        if (mIsPanelShown) {
            getPlayer().setZoomPercents(getPlayerData().getZoomPercents());
            getPlayer().setVideoGravity(Gravity.CENTER);
            mIsPanelShown = false;
        }
    }

    private void tick() {
        if (getPlayer() == null || !mIsPanelShown) {
            return;
        }

        // Positive offset delays the lyrics
        getPlayer().setLyricsPosition(getPlayer().getPositionMs() - mOffsetMs, getPlayer().getDurationMs());

        Utils.postDelayed(mTick, TICK_MS);
    }

    private void disposeActions() {
        RxHelper.disposeActions(mLyricsAction);
    }
}
