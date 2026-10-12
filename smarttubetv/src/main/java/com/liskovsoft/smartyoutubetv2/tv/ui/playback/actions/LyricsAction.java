package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying song lyrics next to the video.
 */
public class LyricsAction extends TwoStateAction {
    public LyricsAction(Context context) {
        super(context, R.id.action_lyrics, R.drawable.action_lyrics);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.action_lyrics);
        labels[INDEX_ON] = context.getString(R.string.action_lyrics);
        setLabels(labels);
    }
}
