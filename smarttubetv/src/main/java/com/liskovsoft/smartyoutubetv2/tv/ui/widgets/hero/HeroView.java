package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.hero;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/**
 * Featured area on top of the browse rows. Shows a large thumbnail and the info of the focused video.
 */
public class HeroView extends FrameLayout {
    // Wait for the focus to settle before loading a big image
    private static final long UPDATE_DELAY_MS = 250;
    // Share of the width covered by the thumbnail, it's pinned to the right
    private static final float BACKDROP_WIDTH_RATIO = 0.68f;
    private ImageView mBackdrop;
    private TextView mTitle;
    private TextView mSubtitle;
    private Video mPendingVideo;
    private String mShownVideoId;
    private final Runnable mUpdate = this::applyPendingVideo;

    public HeroView(Context context) {
        super(context);
        init();
    }

    public HeroView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.hero_view, this);
        setFocusable(false);
        mBackdrop = findViewById(R.id.hero_backdrop);
        mTitle = findViewById(R.id.hero_title);
        mSubtitle = findViewById(R.id.hero_subtitle);
    }

    /**
     * Fades the thumbnail into the page background, so the rows below and the text on the left stay readable.
     */
    public void setPageBackgroundColor(int color) {
        setBackgroundColor(color);
        int transparent = color & 0x00FFFFFF;
        findViewById(R.id.hero_scrim_start).setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {color, color & 0x99FFFFFF, transparent, transparent}));
        findViewById(R.id.hero_scrim_bottom).setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {transparent, color}));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int backdropWidth = (int) (w * BACKDROP_WIDTH_RATIO);
        // The start scrim covers the thumbnail only, so its edge blends into the page
        for (View view : new View[] {mBackdrop, findViewById(R.id.hero_scrim_start)}) {
            LayoutParams params = (LayoutParams) view.getLayoutParams();
            params.width = backdropWidth;
            view.setLayoutParams(params);
        }
    }

    public void setVideo(@Nullable Video video) {
        if (video == null || video.videoId != null && video.videoId.equals(mShownVideoId)) {
            return;
        }

        mPendingVideo = video;
        removeCallbacks(mUpdate);
        postDelayed(mUpdate, UPDATE_DELAY_MS);
    }

    private void applyPendingVideo() {
        Video video = mPendingVideo;

        if (video == null) {
            return;
        }

        mShownVideoId = video.videoId;
        mTitle.setText(video.getTitle());
        CharSequence subtitle = video.getSecondTitle();
        mSubtitle.setText(subtitle);
        mSubtitle.setVisibility(subtitle != null && subtitle.length() > 0 ? View.VISIBLE : View.GONE);

        String cardUrl = video.getCardImageUrl();
        // The card thumbnail is too small to fill the hero. Try the 720p one first.
        String bigUrl = video.videoId != null ? "https://i.ytimg.com/vi/" + video.videoId + "/hq720.jpg" : cardUrl;

        Glide.with(getContext())
                .load(bigUrl)
                .apply(ViewUtil.glideOptions())
                .error(Glide.with(getContext()).load(cardUrl).apply(ViewUtil.glideOptions()))
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(mBackdrop);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mUpdate);
        super.onDetachedFromWindow();
    }
}
