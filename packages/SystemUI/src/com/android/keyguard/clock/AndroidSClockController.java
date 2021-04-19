/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.keyguard.clock;

import android.animation.ValueAnimator;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.icu.text.DateFormat;
import android.icu.text.DisplayContext;
import android.text.Html;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.util.Converter;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import static com.android.systemui.statusbar.phone
        .KeyguardClockPositionAlgorithm.CLOCK_USE_DEFAULT_Y;

/**
 * Plugin for the default clock face used only to provide a preview.
 */
public class AndroidSClockController implements ClockPlugin {

    private final float mTextSizeNormal = 96f;
    private final float mTextSizeBig = 156f;
    private final float mDateTextSizeNormal = 24f;
    private boolean mHasVisibleNotification = false;
    private boolean mClockState = false;
    private float clockDividY = 6f;
    private float horizontalBias = 1f;

    /**
     * Resources used to get title and thumbnail.
     */
    private final Resources mResources;

    /**
     * LayoutInflater used to inflate custom clock views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Extracts accent color from wallpaper.
     */
    private final SysuiColorExtractor mColorExtractor;

    /**
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Root view of clock.
     */
    private ClockLayout mView;
    private ClockLayout mBigClockView;

    /**
     * Text clock in preview view hierarchy.
     */
    private TextClock mClock;
    private TextView mDate;
    private ConstraintLayout mContainer;
    private ConstraintLayout mContainerBig;
    private ConstraintSet mContainerSet = new ConstraintSet();
    private ConstraintSet mContainerSetBig = new ConstraintSet();

    private Context mContext;

    /**
     * Time and calendars to check the date
     */
    private final Calendar mTime = Calendar.getInstance(TimeZone.getDefault());

    /**
     * Create a DefaultClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public AndroidSClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        mResources = res;
        mLayoutInflater = inflater;
        mContext = mLayoutInflater.getContext();
        mColorExtractor = colorExtractor;
    }

    private void createViews() {
        mView = (ClockLayout) mLayoutInflater
                .inflate(R.layout.android_s_clock, null);
        final ClockLayout viewBig = (ClockLayout) mLayoutInflater
                .inflate(R.layout.android_s_big_clock, null);
        mClock = mView.findViewById(R.id.clock);
        mDate = mView.findViewById(R.id.date);
        mContainer = mView.findViewById(R.id.clock_view);
        mContainerBig = viewBig.findViewById(R.id.clock_view);
        mContainerSet.clone(mContainer);
        mContainerSetBig.clone(mContainerBig);
        mClock.setFormat12Hour("hh\nmm");
        mClock.setFormat24Hour("kk\nmm");
    }

    @Override
    public void onDestroyView() {
        mView = null;
        mClock = null;
        mDate = null;
        mContainer = null;
    }

    @Override
    public String getName() {
        return "android_s";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_android_s);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.samsung_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        View previewView = mLayoutInflater.inflate(R.layout.android_s_clock, null);
        TextClock previewClock = mView.findViewById(R.id.clock);
        previewClock.setFormat12Hour("hh\nmm");
        previewClock.setFormat24Hour("kk\nmm");
        onTimeTick();

        return mRenderer.createPreview(previewView, width, height);
    }

    @Override
    public View getView() {
        if (mView == null) {
            createViews();
        }
        return mView;
    }

    @Override
    public View getBigClockView() {
        return null;
    }

    @Override
    public int getPreferredY(int totalHeight) {
        return (int) (totalHeight / clockDividY);
    }

    @Override
    public void setStyle(Style style) {}

    @Override
    public void setTextColor(int color) {
        mClock.setTextColor(color);
    }

    @Override
    public void setTypeface(Typeface tf) {
        mClock.setTypeface(tf);
    }

    @Override
    public void setDateTypeface(Typeface tf) {
        mDate.setTypeface(tf);
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {}

    /**
     * Set whether or not the lock screen is showing notifications.
     */
    @Override
    public void setHasVisibleNotifications(boolean hasVisibleNotifications) {
        if (hasVisibleNotifications == mHasVisibleNotification) {
            return;
        }
        mHasVisibleNotification = hasVisibleNotifications;
        animate();
    }

    private void animate() {
        final float differenceSize = mTextSizeBig - mTextSizeNormal;
        if (!mHasVisibleNotification) {
            if (!mClockState) {
                mClock.animate()
                            .setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    mClock.setTextSize((float) Converter.dpToPx(mContext, (int) (mTextSizeNormal + (differenceSize * animation.getAnimatedFraction()))));
                                    /*
                                    horizontalBias = 0.5f + (0.5f * (1f - animation.getAnimatedFraction()));
                                    mContainerSet.setHorizontalBias(R.id.clock, horizontalBias);
                                    mContainerSet.applyTo(mContainer);
                                    */
                                    mClock.requestLayout();
                                }
                            })
                            .setDuration(550)
                            .withStartAction(() -> {
                                TransitionManager.beginDelayedTransition(mContainer,
                                new Fade().setDuration(550).addTarget(mContainer));
                                mContainerSetBig.applyTo(mContainer);
                            })
                            .withEndAction(() -> {
                                mClockState = true;
                            })
                            .start();
            }
        } else {
            if (mClockState) {
                mClock.animate()
                            .setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    mClock.setTextSize((float) Converter.dpToPx(mContext, (int) (mTextSizeNormal + (differenceSize * (1f - animation.getAnimatedFraction())))));
                                    /*
                                    horizontalBias = 0.5f + (0.5f * animation.getAnimatedFraction());
                                    mContainerSet.setHorizontalBias(R.id.clock, horizontalBias);
                                    mContainerSet.applyTo(mContainer);
                                    */
                                    mClock.requestLayout();
                                }
                            })
                            .setDuration(550)
                            .withStartAction(() -> {
                                TransitionManager.beginDelayedTransition(mContainer,
                                new Fade().setDuration(550).addTarget(mContainer));
                                mContainerSet.applyTo(mContainer);
                            })
                            .withEndAction(() -> {
                                mClockState = false;
                            })
                            .start();
            }
        }
    }

    @Override
    public void onTimeTick() {
        animate();

        DateFormat dateFormat = DateFormat.getInstanceForSkeleton("EEE", Locale.getDefault());
        dateFormat.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE);
        String day = dateFormat.format(mTime.getInstance().getTimeInMillis());
        String dayFinal = day.substring(0, 1).toUpperCase() + day.substring(1);

        dateFormat = DateFormat.getInstanceForSkeleton("MMM", Locale.getDefault());
        String month = dateFormat.format(mTime.getInstance().getTimeInMillis());
        String monthFinal = month.substring(0, 1).toUpperCase() + month.substring(1);

        dateFormat = DateFormat.getInstanceForSkeleton("d", Locale.getDefault());
        String dayNumber = dateFormat.format(mTime.getInstance().getTimeInMillis());

        mDate.setText(dayFinal + ", " + monthFinal + " " + dayNumber);
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mView.setDarkAmount(darkAmount);
        mDate.setTextSize(mDateTextSizeNormal + (8f * darkAmount));
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {}

    @Override
    public boolean shouldShowStatusArea() {
        return false;
    }
}
