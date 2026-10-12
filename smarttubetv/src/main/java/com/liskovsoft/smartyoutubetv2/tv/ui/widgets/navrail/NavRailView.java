package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.navrail;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Narrow column of section icons shown while the sidebar is collapsed.<br/>
 * It isn't focusable: moving left from the content opens the full sidebar as before.
 */
public class NavRailView extends FrameLayout {
    private ScrollView mScroll;
    private LinearLayout mItemsContainer;
    private final List<ImageView> mIcons = new ArrayList<>();
    private final List<View> mIndicators = new ArrayList<>();
    private final List<Boolean> mTintable = new ArrayList<>();
    private int mSelected = -1;
    private boolean mScrollPending;

    public static class Item {
        public final int iconResId;
        @Nullable public final String iconUrl;

        public Item(int iconResId, @Nullable String iconUrl) {
            this.iconResId = iconResId;
            this.iconUrl = iconUrl;
        }
    }

    public NavRailView(Context context) {
        super(context);
        init();
    }

    public NavRailView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.nav_rail_view, this);
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.nav_background));
        setFocusable(false);
        mScroll = findViewById(R.id.nav_rail_scroll);
        mItemsContainer = findViewById(R.id.nav_rail_items);
    }

    public void setItems(List<Item> items) {
        mItemsContainer.removeAllViews();
        mIcons.clear();
        mIndicators.clear();
        mTintable.clear();
        mSelected = -1;

        int slotHeight = dp(44);
        int iconSize = dp(24);

        for (Item item : items) {
            FrameLayout slot = new FrameLayout(getContext());

            View indicator = new View(getContext());
            indicator.setBackgroundResource(R.drawable.nav_rail_indicator);
            indicator.setVisibility(View.INVISIBLE);
            slot.addView(indicator, new LayoutParams(dp(3), dp(20), Gravity.START | Gravity.CENTER_VERTICAL));

            ImageView icon = new ImageView(getContext());
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            slot.addView(icon, new LayoutParams(iconSize, iconSize, Gravity.CENTER));

            if (item.iconUrl != null) {
                Glide.with(getContext()).load(item.iconUrl).apply(ViewUtil.glideOptions().circleCrop()).into(icon);
            } else if (item.iconResId > 0) {
                icon.setImageDrawable(ContextCompat.getDrawable(getContext(), item.iconResId));
            }

            mItemsContainer.addView(slot, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, slotHeight));
            mIcons.add(icon);
            mIndicators.add(indicator);
            mTintable.add(item.iconUrl == null);
        }

        applyColors();
    }

    public void setSelected(int index) {
        if (index == mSelected) {
            return;
        }

        mSelected = index;
        applyColors();

        if (mItemsContainer.getHeight() > 0) {
            scrollToSelected(true);
        } else {
            mScrollPending = true; // not laid out yet
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mScrollPending = true; // the visible part changed, keep the current section in view
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mScrollPending && mItemsContainer.getHeight() > 0) {
            mScrollPending = false;
            scrollToSelected(false);
        }
    }

    private void scrollToSelected(boolean smooth) {
        if (mSelected < 0 || mSelected >= mItemsContainer.getChildCount()) {
            return;
        }

        // Keep the current section in the upper third
        int target = Math.max(0, mItemsContainer.getChildAt(mSelected).getTop() - mScroll.getHeight() / 3);

        if (smooth) {
            mScroll.smoothScrollTo(0, target);
        } else {
            mScroll.scrollTo(0, target);
        }
    }

    private void applyColors() {
        ColorStateList selected = ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.white));
        ColorStateList normal = ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.nav_rail_icon));

        for (int i = 0; i < mIcons.size(); i++) {
            boolean isSelected = i == mSelected;
            ImageView icon = mIcons.get(i);
            ImageViewCompat.setImageTintList(icon, mTintable.get(i) ? (isSelected ? selected : normal) : null);
            icon.setAlpha(!mTintable.get(i) && !isSelected ? 0.6f : 1f);
            mIndicators.get(i).setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
