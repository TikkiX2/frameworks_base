/*
* Copyright (C) 2021 SynthOS
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.synth.smartmedia;

import android.animation.ValueAnimator;
import android.animation.TimeInterpolator;
import android.app.PendingIntent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.settingslib.Utils;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.internal.util.Converter;
import com.android.internal.util.ImageUtils;
import com.android.systemui.Dependency;
import com.android.keyguard.KeyguardSliceView.KeyguardSliceTextView;
import com.android.systemui.R;
import com.android.systemui.statusbar.MediaArtworkProcessor;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.media.MediaDataManager;
import com.android.systemui.plugins.SmartMediaPlugin;
import com.android.systemui.media.MediaData;
import com.android.systemui.media.MediaAction;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.util.List;

public class TextSmartMedia implements SmartMediaPlugin, MediaDataManager.Listener {
    private static final boolean DEBUG = true;
    private static final String TAG = "SynthMediaView";
    private static final int SMART_MEDIA_ANIMATION_DURATION = 500;

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

    Context mContext;
    private NotificationMediaManager mMediaManager;
    private MediaDataManager mMediaDataManager;
    private MediaArtworkProcessor mMediaArtworkProcessor;
    private MediaSession.Token mToken;
    private MediaController mController;
    private MediaData mMediaData;

    boolean mState = false;

    private boolean mHasVisibleNotification;

    /**
     * Root view of clock.
     */
    private View mView;

    private ConstraintLayout mContainer;
    private ConstraintLayout mContainerBig;
    private ConstraintSet mContainerSet = new ConstraintSet();
    private ConstraintSet mContainerSetBig = new ConstraintSet();

    // Views
    private TextView mTitle;
    private KeyguardSliceTextView mArtist;
    private View mPreviousAction;
    private View mPlayPauseAction;
    private View mNextAction;

    /**
     *
     * Smart Auto Functions
     * 
     */
    private Runnable mRunnable;

    /**
     * Create a DefaultClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public TextSmartMedia(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor, Context context) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
        mContext = context;
        mMediaManager = Dependency.get(NotificationMediaManager.class);
        mMediaArtworkProcessor = mMediaManager.getMediaArtworkProcessor();
        mMediaDataManager = mMediaManager.getMediaDataManager();
        mMediaDataManager.addListener(this);
    }

    public void createViews() {
        mView = mLayoutInflater.inflate(R.layout.text_media_view, null);

        mTitle = (TextView) mView.findViewById(R.id.title);
        mArtist = (KeyguardSliceTextView) mView.findViewById(R.id.artist);
        mPreviousAction = mView.findViewById(R.id.previous);
        mPlayPauseAction = mView.findViewById(R.id.play_pause);
        mNextAction = mView.findViewById(R.id.next);

        mContainer = mView.findViewById(R.id.container);
        mContainerSet.clone(mContainer);
    }

    @Override
    public void onDestroyView() {
        mView = null;
        mTitle = null;
        mArtist = null;
        mPreviousAction = null;
        mPlayPauseAction = null;
        mNextAction = null;
        mMediaDataManager.removeListener(this);
    }

    @Override
    public void onMediaDataLoaded(String key, String oldKey, MediaData data) {
        mMediaData = data;
        bind(mMediaData);
    }

    /**
    * Is empty cause is unnecessary.
    * @param key key of notification removed.
    */
    @Override
    public void onMediaDataRemoved(String key) {
    }

    /**
     * Bind this view based on the data given
     */
    public void bind(@NonNull MediaData data) {
        if (mView == null) return;

        MediaSession.Token token = data.getToken();
        if (mToken == null || !mToken.equals(token)) {
            mToken = token;
        }

        if (mToken != null) {
            mController = new MediaController(mContext, mToken);
        } else {
            mController = null;
        }

        // Song name
        mTitle.setText(data.getSong());

        // Artist name
        mArtist.setText(data.getArtist());
        Drawable iconDrawable = null;
        if (data.getAppIcon() != null) {
            iconDrawable = data.getAppIcon();
        } else {
            iconDrawable = mContext.getDrawable(R.drawable.ic_music_note);
        }
        final int iconSize = Converter.dpToPx(mContext, 20);
        final int width = (int) (iconDrawable.getIntrinsicWidth()
                / (float) iconDrawable.getIntrinsicHeight() * (iconSize));
        iconDrawable.setBounds(0, 0, Math.max(width, 1), (iconSize));
        mArtist.setCompoundDrawables(iconDrawable, null, null, null);

        // Actions
        mPreviousAction.setOnClickListener(v -> {
            mController.getTransportControls().skipToPrevious();
        });
        mPlayPauseAction.setOnClickListener(v -> {
            if (mMediaManager.isNowPlaying()) {
                mController.getTransportControls().pause();
            } else {
                mController.getTransportControls().play();
            }
        });
        mNextAction.setOnClickListener(v -> {
            mController.getTransportControls().skipToNext();
        });
    }

    @Override
    public void setState(boolean state) {
        if (mState != state) mState = state;
        if (mState && mMediaData != null) {
            bind(mMediaData);
        }
    }

    @Override
    public void setArtworkBlur(boolean blur, float blurRadius) {
    }

    @Override
    public String getName() {
        return "text_media";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.text_smart_media);
    }

    @Override
    public Bitmap getThumbnail() {
        return null;
    }

    @Override
    public Bitmap getPreview(int width, int height) {
        return null;
    }

    @Override
    public View getMediaView() {
        if (mView == null) {
            createViews();
        }
        return mView;
    }

    @Override
    public void setHasVisibleNotifications(boolean hasVisibleNotifications) {
        if (hasVisibleNotifications == mHasVisibleNotification) {
            return;
        }
        mHasVisibleNotification = hasVisibleNotifications;
    }

    @Override
    public void setTitleTypeface(Typeface tf) {
        mTitle.setTypeface(tf);
    }

    @Override
    public void setArtistTypeface(Typeface tf) {
        mArtist.setTypeface(tf);
    }
    
    @Override
    public void setAppTypeface(Typeface tf) {
    }

    @Override
    public void setDarkAmount(float darkAmount) {}

    @Override
    public void setOnClickRunnable(Runnable run) {
        mRunnable = run;
    }

}
