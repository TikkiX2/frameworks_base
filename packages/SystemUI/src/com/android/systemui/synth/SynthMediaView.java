/*
* Copyright (C) 2020 SynthOS
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
package com.android.systemui.synth;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.internal.util.ImageUtils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.MediaArtworkProcessor;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.media.MediaDataManager;
import com.android.systemui.media.MediaData;
import com.android.systemui.media.MediaAction;

import java.util.List;

public class SynthMediaView extends FrameLayout implements MediaDataManager.Listener {
    private static final boolean DEBUG = true;
    private static final String TAG = "SynthMediaView";

    // Button IDs for QS controls
    static final int[] ACTION_IDS = {
            R.id.action0,
            R.id.action1,
            R.id.action2,
            R.id.action3,
            R.id.action4
    };

    Context mContext;
    private NotificationMediaManager mMediaManager;
    private MediaDataManager mMediaDataManager;
    private MediaArtworkProcessor mMediaArtworkProcessor;

    boolean mState = true;
    boolean mButtonsVisibility = true;

    private TextView mTitle;
    private TextView mArtist;
    private ImageView mArtwork;

    private View mBackground;

    private TextView mAppName;
    private ImageView mAppIcon;

    private MediaSession.Token mToken;
    private MediaController mController;
    private int mBackgroundColor;
    private int mAlbumArtRadius = 20;
    // This will provide the corners for the album art.
    private ViewOutlineProvider mArtworkOutlineProvider;
    private ViewOutlineProvider mBackgroundOutlineProvider;

    private boolean mBlur;
    private float mBlurRadius;

    private Runnable mRunnable;

    public SynthMediaView(Context context) {
        this(context, null);
    }

    public SynthMediaView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SynthMediaView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SynthMediaView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
        mMediaManager = Dependency.get(NotificationMediaManager.class);
        mMediaArtworkProcessor = mMediaManager.getMediaArtworkProcessor();
        mMediaDataManager = mMediaManager.getMediaDataManager();
        mMediaDataManager.addListener(this);

        if (DEBUG) Log.d(TAG, "New Instance");
    }

    @Override
    public void onMediaDataLoaded(String key, String oldKey, MediaData data) {
        if (mState) {
            bind(data);
        }
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

        settingViews();
        MediaSession.Token token = data.getToken();
        mBackgroundColor = data.getBackgroundColor();
        if (mToken == null || !mToken.equals(token)) {
            mToken = token;
        }

        if (mToken != null) {
            mController = new MediaController(mContext, mToken);
        } else {
            mController = null;
        }

        // Background
        mBackground.setBackgroundTintList(
                ColorStateList.valueOf(mBackgroundColor));
        mBackgroundOutlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, mBackground.getWidth(), mBackground.getHeight(), mAlbumArtRadius);
            }
        };
        mBackground.setClipToOutline(true);
        mBackground.setOutlineProvider(mBackgroundOutlineProvider);

        // Artwork
        boolean hasArtwork = data.getArtwork() != null;
        if (hasArtwork) {
            Drawable artwork = ImageUtils.resize(mContext, data.getArtwork().loadDrawable(mContext), mArtwork.getWidth());
            Bitmap artworkBlur = mMediaArtworkProcessor.processArtwork(mContext, ImageUtils.buildScaledBitmap(artwork, artwork.getIntrinsicWidth(), artwork.getIntrinsicHeight()), mBlurRadius);
            if (mBlur) mArtwork.setImageBitmap(artworkBlur); else mArtwork.setImageDrawable(artwork);
            mArtworkOutlineProvider = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, mArtwork.getWidth(), mArtwork.getHeight(), mAlbumArtRadius);
                }
            };
            mArtwork.setClipToOutline(true);
            mArtwork.setOutlineProvider(mArtworkOutlineProvider);
        }

        // App icon
        if (data.getAppIcon() != null) {
            mAppIcon.setImageDrawable(data.getAppIcon());
        } else {
            Drawable iconDrawable = mContext.getDrawable(R.drawable.ic_music_note);
            mAppIcon.setImageDrawable(iconDrawable);
        }

        // Song name
        mTitle.setText(data.getSong());

        // App title
        mAppName.setText(data.getApp());

        // Artist name
        mArtist.setText(data.getArtist());

        // Actions

        // Media controls
        int i = 0;
        List<MediaAction> actionIcons = data.getActions();
        for (; i < actionIcons.size() && i < ACTION_IDS.length; i++) {
            int actionId = ACTION_IDS[i];
            final ImageButton button = (ImageButton) findViewById(actionId);
            MediaAction mediaAction = actionIcons.get(i);
            button.setImageDrawable(mediaAction.getDrawable());
            button.setContentDescription(mediaAction.getContentDescription());
            Runnable action = mediaAction.getAction();

            if (action == null) {
                button.setEnabled(false);
                button.setVisibility(View.GONE);
            } else {
                button.setEnabled(true);
                button.setOnClickListener(v -> {
                    action.run();
                    if (mRunnable != null) {
                        mRunnable.run();
                    }
                });
                button.setVisibility(mButtonsVisibility ? View.VISIBLE : View.GONE);
            }
        }

        // Hide any unused buttons
        for (; i < ACTION_IDS.length; i++) {
            int actionId = ACTION_IDS[i];
            final ImageButton button = (ImageButton) findViewById(actionId);
            button.setVisibility(View.GONE);
        }
    }

    public void settingViews() {
        mTitle = (TextView) findViewById(R.id.title);
        mArtist = (TextView) findViewById(R.id.artist);
        mArtwork = (ImageView) findViewById(R.id.artwork);
        mAppName = (TextView) findViewById(R.id.app_name);
        mAppIcon = (ImageView) findViewById(R.id.app_icon);
        mBackground = findViewById(R.id.background);
    }

    public void setState(boolean value) {
        if (mState != value) mState = value;
        setVisible(value);
    }

    public void setVisible(boolean value) {
        setVisibility(value ? View.VISIBLE : View.GONE);
    }

    public void setTitleFont(int fontstyle) {
        setFont(mTitle, fontstyle);
    }

    public void setArtistFont(int fontstyle) {
        setFont(mArtist, fontstyle);
    }

    public void setArtworkBlur(boolean blur, float blurRadius) {
        mBlur = blur;
        mBlurRadius = blurRadius;
    }

    public void setAppNameFont(int fontstyle) {
        setFont(mAppName, fontstyle);
    }

    public void setRunnable(Runnable run) {
        mRunnable = run;
    }

    private void setFont(TextView view, int fontstyle) {
    	if (view != null) {
    		switch (fontstyle) {
    			case 0:
    			default:
    				view.setTypeface(Typeface.create(mContext.getResources().getString(R.string.clock_sysfont_headline_medium), Typeface.NORMAL));
    				break;
    			case 1:
    				view.setTypeface(Typeface.create(mContext.getResources().getString(R.string.clock_sysfont_body_medium), Typeface.NORMAL));
    				break;
    			case 2:
    				view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    				break;
    			case 3:
    				view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    				break;
    			case 4:
    				view.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
    				break;
    			case 5:
    				view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
    				break;
    			case 6:
    				view.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    				break;
    			case 7:
    				view.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
    				break;
    			case 8:
    				view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
    				break;
    			case 9:
    				view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
    				break;
    			case 10:
    				view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
    				break;
    			case 11:
    				view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
    				break;
    			case 12:
    				view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    				break;
    			case 13:
    				view.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
    				break;
                case 14:
                    view.setTypeface(Typeface.create("abelreg", Typeface.NORMAL));
                    break;
                case 15:
                    view.setTypeface(Typeface.create("adventpro", Typeface.NORMAL));
                    break;
                case 16:
                    view.setTypeface(Typeface.create("alien-league", Typeface.NORMAL));
                    break;
                case 17:
                    view.setTypeface(Typeface.create("bignoodle-italic", Typeface.NORMAL));
                    break;
                case 18:
                    view.setTypeface(Typeface.create("biko", Typeface.NORMAL));
                    break;
                case 19:
                    view.setTypeface(Typeface.create("blern", Typeface.NORMAL));
                    break;
                case 20:
                    view.setTypeface(Typeface.create("cherryswash", Typeface.NORMAL));
                    break;
                case 21:
                    view.setTypeface(Typeface.create("codystar", Typeface.NORMAL));
                    break;
                case 22:
                    view.setTypeface(Typeface.create("ginora-sans", Typeface.NORMAL));
                    break;
                case 23:
                    view.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
                    break;
                case 24:
                    view.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
                    break;
                case 25:
                    view.setTypeface(Typeface.create("inkferno", Typeface.NORMAL));
                    break;
                case 26:
                    view.setTypeface(Typeface.create("jura-reg", Typeface.NORMAL));
                    break;
                case 27:
                    view.setTypeface(Typeface.create("kellyslab", Typeface.NORMAL));
                    break;
                case 28:
                    view.setTypeface(Typeface.create("metropolis1920", Typeface.NORMAL));
                    break;
                case 29:
                    view.setTypeface(Typeface.create("neonneon", Typeface.NORMAL));
                    break;
                case 30:
                    view.setTypeface(Typeface.create("pompiere", Typeface.NORMAL));
                    break;
                case 31:
                    view.setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
                    break;
                case 32:
                    view.setTypeface(Typeface.create("riviera", Typeface.NORMAL));
                    break;
                case 33:
                    view.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
                    break;
                case 34:
                    view.setTypeface(Typeface.create("sedgwick-ave", Typeface.NORMAL));
                    break;
                case 35:
                    view.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
                    break;
                case 36:
                    view.setTypeface(Typeface.create("themeable-clock", Typeface.NORMAL));
                    break;
                case 37:
                    view.setTypeface(Typeface.create("unionfont", Typeface.NORMAL));
                    break;
                case 38:
                    view.setTypeface(Typeface.create("vibur", Typeface.NORMAL));
                    break;
                case 39:
                    view.setTypeface(Typeface.create("voltaire", Typeface.NORMAL));
                    break;
    		}
    	}
    }

}
