/**
 * Copyright (C) 2021 SynthOS
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
 *
 * QS Custom blur system
 */

package com.android.systemui.synth;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.internal.util.ImageUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.MediaArtworkProcessor;
import com.android.systemui.statusbar.phone.NotificationPanelViewController;

public class QSCustomBlur {

    private NotificationPanelViewController mNotificationPanelViewController;
    private NotificationMediaManager mMediaManager;
    private Context mContext;
    private ImageView mCustomBlurView;

    public QSCustomBlur(
            Context context, 
            ImageView customBlurView,
            NotificationMediaManager notificationMediaManager,
            NotificationPanelViewController notificationPanelViewController) {
        mContext = context;
        mCustomBlurView = customBlurView;
        mMediaManager = notificationMediaManager;
        mNotificationPanelViewController = notificationPanelViewController;
    }

    public void updateView(boolean isKeyguard) {
        float customBlurPosition = mNotificationPanelViewController.getExpandedFraction() * (float) mCustomBlurView.getMeasuredHeight();
        if (!isKeyguard && showCustomBlur()) {
            setTypeBackground();
            mCustomBlurView.setVisibility(View.VISIBLE);
            mCustomBlurView.setAlpha(mNotificationPanelViewController.getExpandedFraction());
            // mCustomBlurView.setTop((int) (mCustomBlurView.getMeasuredHeight() - customBlurPosition) - ((int) (mCustomBlurView.getMeasuredHeight() - customBlurPosition) * 2));
        } else {
            mCustomBlurView.setVisibility(View.GONE);
        }
    }

    private void setColorAccentBlur() {
        MediaArtworkProcessor artworkProcessor = mMediaManager.getMediaArtworkProcessor();
        Bitmap bm = tintBitmap(getBitmap(R.drawable.qs_bg_blur), getAccentColor());
        Bitmap bmBlur = artworkProcessor.processArtwork(mContext, bm, (int) ((float) getBlurRadius() * mNotificationPanelViewController.getExpandedFraction()));
        mCustomBlurView.setImageBitmap(bmBlur);
    }
    
    private Bitmap tintBitmap(Bitmap bitmap, int color) {
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(color, Mode.SRC_IN));
        Bitmap bitmapResult = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapResult);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return bitmapResult;
    }
    
    private Bitmap getBitmap(int drawableRes) {
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        Drawable drawable = mContext.getResources().getDrawable(drawableRes);
        Canvas canvas = new Canvas();
        Bitmap bitmap = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }
    
    private void setTypeBackground() {
        MediaArtworkProcessor artworkProcessor = mMediaManager.getMediaArtworkProcessor();
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
        WallpaperInfo info = wallpaperManager.getWallpaperInfo(UserHandle.USER_CURRENT);
        String customNameImage = "qs_custom_blur_image";
        String imageUri = Settings.System.getString(mContext.getContentResolver(),
            Settings.System.QS_CUSTOM_BLUR_IMAGE);

        switch(getTypeBlur()) {
            case 0: // Color Accent
                setColorAccentBlur();
                break;
            case 1: // Custom Color
                mCustomBlurView.setImageBitmap(null);
                Bitmap bm = tintBitmap(getBitmap(R.drawable.rounded), getCustomColor());
                Bitmap bmBlur = artworkProcessor.processArtwork(mContext, bm, (int) ((float) getBlurRadius() * mNotificationPanelViewController.getExpandedFraction()));
                mCustomBlurView.setImageBitmap(bmBlur);
                break;
            case 2: // Custom Image
                if (imageUri != null) {
                    FileImageUtils.saveImage(mContext, Uri.parse(imageUri), customNameImage);
                    Bitmap bitmap = FileImageUtils.loadImage(mContext, customNameImage);
                    if (bitmap != null) {
                        Bitmap bitmapBlur = artworkProcessor.processArtwork(mContext, bitmap, (int) ((float) getBlurRadius() * mNotificationPanelViewController.getExpandedFraction()));
                        mCustomBlurView.setImageBitmap(bitmapBlur);
                        mCustomBlurView.invalidateOutline();
                    }
                } else {
                    setColorAccentBlur();
                }
                break;
            case 3: // Wallpaper
                if (info == null) {
                    Drawable wallpaper = wallpaperManager.getDrawable();
                    Bitmap wallpaperBlur = artworkProcessor.processArtwork(mContext, ImageUtils.buildScaledBitmap(wallpaper, wallpaper.getIntrinsicWidth(), wallpaper.getIntrinsicHeight()), (int) ((float) getBlurRadius() * mNotificationPanelViewController.getExpandedFraction()));  
                    mCustomBlurView.setImageBitmap(wallpaperBlur);
                } else {
                    setColorAccentBlur();
                }
                break;
        }
    }

    private boolean showCustomBlur() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_CUSTOM_BLUR, 0, UserHandle.USER_CURRENT) != 0;
    }

    private int getTypeBlur() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_CUSTOM_BLUR_TYPE, 0, UserHandle.USER_CURRENT);
    }

    private int getCustomColor() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_CUSTOM_BLUR_COLOR, getAccentColor(), UserHandle.USER_CURRENT);
    }

    private int getBlurRadius() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_CUSTOM_BLUR_RADIUS, 25, UserHandle.USER_CURRENT);
    }

    int getAccentColor() {
        final TypedValue value = new TypedValue();
        mContext.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true);
        return value.data;
    }
}