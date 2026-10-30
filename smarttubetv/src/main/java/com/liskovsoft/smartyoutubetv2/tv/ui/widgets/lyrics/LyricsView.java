package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.lyrics;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.misc.lyrics.Lyrics;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Side panel that shows song lyrics and follows the playback position.<br/>
 * Synced lyrics highlight the current line and fill it karaoke style. Plain lyrics scroll along with the song.
 */
public class LyricsView extends LinearLayout {
    // Share of the screen width used until the video edge is known. The video is shrunk to the rest (see LyricsController).
    private static final float WIDTH_RATIO = 0.38f;
    // Below this share the video edge is considered not resized yet
    private static final float MIN_WIDTH_RATIO = 0.25f;
    // Where the current line sits, from the top of the scroll area
    private static final float CURRENT_LINE_POSITION = 0.35f;
    private static final float CURRENT_LINE_SP = 28;
    private static final float LINE_SP = 21;
    private static final float PLAIN_LINE_SP = 19;
    private static final String EMPTY_LINE = "♪";
    // Longer than this to the next line means an instrumental part, not a slowly sung line
    private static final long MAX_SUNG_LINE_MS = 7_000;
    private TextView mTitle;
    private TextView mArtist;
    private TextView mStatus;
    private ScrollView mScroll;
    private LinearLayout mLinesContainer;
    private final List<TextView> mLineViews = new ArrayList<>();
    private Lyrics mLyrics;
    private int mCurrentIndex = Integer.MIN_VALUE;
    // Karaoke fill of the current line. One span moved along the text: no new objects and no relayout per tick.
    private ForegroundColorSpan mSungSpan;
    private int mSungChars;
    private int mColorCurrent;
    private int mColorSung;
    private int mColorUpcoming;
    private int mColorPast;
    private int mColorPlain;
    private int mVideoRight;
    private final int mPlainScrollStepPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());

    public LyricsView(Context context) {
        super(context);
        init();
    }

    public LyricsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LyricsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Context context = getContext();
        inflate(context, R.layout.lyrics_view, this);
        setOrientation(VERTICAL);
        setBackgroundColor(ContextCompat.getColor(context, R.color.lyrics_panel_bg));

        mTitle = findViewById(R.id.lyrics_title);
        mArtist = findViewById(R.id.lyrics_artist);
        mStatus = findViewById(R.id.lyrics_status);
        mScroll = findViewById(R.id.lyrics_scroll);
        mLinesContainer = findViewById(R.id.lyrics_lines);

        mColorCurrent = ContextCompat.getColor(context, R.color.white);
        mColorSung = ContextCompat.getColor(context, R.color.lyrics_accent);
        mColorUpcoming = ContextCompat.getColor(context, R.color.lyrics_upcoming);
        mColorPast = ContextCompat.getColor(context, R.color.lyrics_past);
        mColorPlain = ContextCompat.getColor(context, R.color.lyrics_plain);
        mSungSpan = new ForegroundColorSpan(mColorSung);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        // Start right where the video ends, so there's no gap on screens that aren't 16:9
        int width = parentWidth - mVideoRight;
        if (mVideoRight <= 0 || width < parentWidth * MIN_WIDTH_RATIO) {
            width = (int) (parentWidth * WIDTH_RATIO);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    /**
     * Right edge of the video, in pixels from the left of the screen.
     */
    public void setVideoRight(int videoRight) {
        if (mVideoRight != videoRight) {
            mVideoRight = videoRight;
            requestLayout();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Let the first and the last lines reach the highlight position
        mScroll.post(() -> {
            int scrollHeight = mScroll.getHeight();
            mLinesContainer.setPadding(0, (int) (scrollHeight * CURRENT_LINE_POSITION), 0, (int) (scrollHeight * (1 - CURRENT_LINE_POSITION)));
        });
    }

    /**
     * Pass null to hide the panel.
     */
    public void setLyrics(@Nullable Lyrics lyrics) {
        if (lyrics == mLyrics) {
            return;
        }

        mLyrics = lyrics;
        mCurrentIndex = Integer.MIN_VALUE;
        mLineViews.clear();
        mLinesContainer.removeAllViews();
        mScroll.scrollTo(0, 0);

        if (lyrics == null) {
            setVisibility(View.GONE);
            return;
        }

        mTitle.setText(lyrics.getTrackName());
        mArtist.setText(lyrics.getArtistName());
        mStatus.setText(lyrics.isSynced() ? R.string.lyrics_synced : R.string.lyrics_unsynced);

        Typeface typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        int lineGap = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, lyrics.isSynced() ? 14 : 6, getResources().getDisplayMetrics());

        for (Lyrics.Line line : lyrics.getLines()) {
            TextView view = new TextView(getContext());
            view.setText(line.text.isEmpty() ? (lyrics.isSynced() ? EMPTY_LINE : " ") : line.text);
            view.setTypeface(typeface);
            view.setGravity(Gravity.START);
            view.setPadding(0, lineGap / 2, 0, lineGap / 2);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, lyrics.isSynced() ? LINE_SP : PLAIN_LINE_SP);
            view.setTextColor(lyrics.isSynced() ? mColorUpcoming : mColorPlain);
            mLinesContainer.addView(view, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            mLineViews.add(view);
        }

        setVisibility(View.VISIBLE);
    }

    public boolean hasLyrics() {
        return mLyrics != null;
    }

    public void setPosition(long positionMs, long durationMs) {
        if (mLyrics == null || mLineViews.isEmpty()) {
            return;
        }

        if (mLyrics.isSynced()) {
            updateSynced(positionMs);
        } else if (durationMs > 0) {
            // No timing info: scroll proportionally to the playback progress
            int range = mLinesContainer.getHeight() - mScroll.getHeight();
            if (range > 0) {
                int target = (int) (range * Math.min(1f, positionMs / (float) durationMs));
                // Move in small steps, not on every tick: each smooth scroll restarts an animation
                if (Math.abs(target - mScroll.getScrollY()) >= mPlainScrollStepPx) {
                    mScroll.smoothScrollTo(0, target);
                }
            }
        }
    }

    private void updateSynced(long positionMs) {
        int index = mLyrics.findLineIndex(positionMs);

        if (index != mCurrentIndex) {
            int previous = mCurrentIndex;
            mCurrentIndex = index;

            if (previous == Integer.MIN_VALUE) {
                // First update: style every line
                for (int i = 0; i < mLineViews.size(); i++) {
                    styleLine(i);
                }
            } else {
                if (previous >= 0) {
                    mLineViews.get(previous).setText(lineText(previous)); // drop the karaoke fill
                }
                // Only the lines between the old and the new position change (usually just two)
                for (int i = Math.max(0, Math.min(previous, index)); i <= Math.max(previous, index); i++) {
                    styleLine(i);
                }
            }

            if (index >= 0) {
                mLineViews.get(index).setText(lineText(index), TextView.BufferType.SPANNABLE);
                mSungChars = -1;
            }

            scrollToLine(index);
        }

        if (index >= 0) {
            fillLine(index, positionMs);
        }
    }

    private void styleLine(int i) {
        TextView view = mLineViews.get(i);
        view.setTextColor(i < mCurrentIndex ? mColorPast : i == mCurrentIndex ? mColorCurrent : mColorUpcoming);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, i == mCurrentIndex ? CURRENT_LINE_SP : LINE_SP);
    }

    private String lineText(int i) {
        String text = mLyrics.getLines().get(i).text;
        return text.isEmpty() ? EMPTY_LINE : text;
    }

    /**
     * LRC has no word timing, so the fill is spread evenly over the time the line is sung.
     */
    private void fillLine(int index, long positionMs) {
        List<Lyrics.Line> lines = mLyrics.getLines();
        Lyrics.Line line = lines.get(index);

        if (line.text.isEmpty()) {
            return;
        }

        long nextStartMs = index + 1 < lines.size() ? lines.get(index + 1).startMs : line.startMs + 5_000;
        // Singers fill most of the time until the next line, with a short breath at the end.
        // A long gap means an instrumental part follows: then don't stretch the fill over it.
        long gapMs = nextStartMs - line.startMs;
        long lineDurationMs = gapMs <= MAX_SUNG_LINE_MS ? gapMs * 85 / 100 : Math.min(gapMs, line.text.length() * 120L + 1_500);
        float progress = lineDurationMs > 0 ? Math.min(1f, (positionMs - line.startMs) / (float) lineDurationMs) : 1f;
        int sungChars = Math.max(0, Math.round(line.text.length() * progress));

        if (sungChars == mSungChars) {
            return; // nothing to redraw
        }

        mSungChars = sungChars;
        CharSequence text = mLineViews.get(index).getText();

        if (!(text instanceof Spannable)) {
            return;
        }

        // A color span doesn't change the text size: the view is just redrawn, not laid out again
        if (sungChars == 0) {
            ((Spannable) text).removeSpan(mSungSpan); // empty exclusive spans aren't allowed
        } else {
            ((Spannable) text).setSpan(mSungSpan, 0, sungChars, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void scrollToLine(int index) {
        // Wait for the text size change to be laid out
        mScroll.post(() -> {
            if (index < 0 || index >= mLineViews.size()) {
                mScroll.smoothScrollTo(0, 0);
                return;
            }

            TextView view = mLineViews.get(index);
            int target = view.getTop() - (int) (mScroll.getHeight() * CURRENT_LINE_POSITION);
            mScroll.smoothScrollTo(0, Math.max(0, target));
        });
    }
}
