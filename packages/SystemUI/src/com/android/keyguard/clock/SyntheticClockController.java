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

import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Outline;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextClock;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.settingslib.Utils;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.TimeZone;

import static com.android.systemui.statusbar.phone
        .KeyguardClockPositionAlgorithm.CLOCK_USE_DEFAULT_Y;

/**
 * Plugin for the default clock face used only to provide a preview.
 */
public class SyntheticClockController implements ClockPlugin {

    /**
     * Image File Name.
     */
    private static final String CUSTOM_IMAGE = "synthetic_clock_image";

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

    /**
     * Text clock in preview view hierarchy.
     */
    private TextClock mClock;
    private TextClock mDate;
    private ImageView mImage;

    /**
     * Rounded Corners.
     */
    private ViewOutlineProvider mViewOutlineProvider;

    /**
     * Create a DefaultClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public SyntheticClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
    }

    private void createViews() {
        mView = (ClockLayout) mLayoutInflater
                .inflate(R.layout.digital_synthetic, null);
        mClock = mView.findViewById(R.id.clock);
        mDate = mView.findViewById(R.id.date);
        mImage = mView.findViewById(R.id.image);
        ColorExtractor.GradientColors colors;

        // Initialize state of plugin before generating preview.
        colors = mColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        int[] palette = colors.getColorPalette();
        if (palette == null) {
            colors = mColorExtractor.getColors(
                    WallpaperManager.FLAG_SYSTEM);
        }
        setColorPalette(colors.supportsDarkText(), colors.getColorPalette());

        onTimeTick();
    }

    private void saveImage(Context context, Uri imageUri) {
        try {
            final InputStream imageStream = context.getContentResolver().openInputStream(imageUri);
            File file = new File(context.getFilesDir(), CUSTOM_IMAGE);
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream output = new FileOutputStream(file);
            byte[] buffer = new byte[8 * 1024];
            int read;

            while ((read = imageStream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } catch (IOException e) {
            Log.e("Clock", "Save image failed " + " " + imageUri);
        }
    }

    private Bitmap loadImage(Context context) {
        try {
            Bitmap result = null;
            File file = new File(context.getFilesDir(), CUSTOM_IMAGE);
            if (file.exists()) {
                final Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath());
                result = image;
            }
            return result;
        } catch (Exception e) {
            Log.e("Clock", "Request image failed ", e);
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        mView = null;
        mClock = null;
        mDate = null;
        mImage = null;
    }

    @Override
    public String getName() {
        return "synthetic";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_synthetic);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.default_bold_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        // Use the normal clock view for the preview
        View previewView = getView();
        ColorExtractor.GradientColors colors;

        // Initialize state of plugin before generating preview.
        colors = mColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        int[] palette = colors.getColorPalette();
        if (palette == null) {
            colors = mColorExtractor.getColors(
                    WallpaperManager.FLAG_SYSTEM);
        }
        setColorPalette(colors.supportsDarkText(), colors.getColorPalette());
        setColorPalette(colors.supportsDarkText(), colors.getColorPalette());
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
        return CLOCK_USE_DEFAULT_Y;
    }

    @Override
    public void setStyle(Style style) {}

    @Override
    public void setTextColor(int color) {
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
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
        if (colorPalette == null || colorPalette.length == 0) {
            return;
        }
        final int accentColor = colorPalette[Math.max(0, colorPalette.length - 5)];
        GradientDrawable dateBg = (GradientDrawable) mDate.getBackground();
        dateBg.setColor(accentColor);
        dateBg.setStroke(0,Color.TRANSPARENT);
    }

    @Override
    public void onTimeTick() {
        Context context = mLayoutInflater.getContext();

        // Set format clock and date
        String clockFormat = Settings.System.getString(context.getContentResolver(),
              Settings.System.SYNTHETIC_CLOCK_FORMAT);
        String dateFormat = Settings.System.getString(context.getContentResolver(),
              Settings.System.SYNTHETIC_DATE_FORMAT);
        mClock.setFormat12Hour(clockFormat == "" ? "hh | mm | ss" : clockFormat);
        mClock.setFormat24Hour(clockFormat == "" ? "kk | mm | ss" : clockFormat);
        mDate.setFormat12Hour(dateFormat == "" ? "EEE" : dateFormat);
        mDate.setFormat24Hour(dateFormat == "" ? "EEE" : dateFormat);

        // Set src image
        String imageUri = Settings.System.getString(context.getContentResolver(),
              Settings.System.SYNTHETIC_CUSTOM_IMAGE);
        if (imageUri != null) {
            saveImage(context, Uri.parse(imageUri));
            Bitmap bm = loadImage(context);
            if (bm != null) {
                mImage.setImageBitmap(bm);
                mImage.setVisibility(View.VISIBLE);
                mImage.invalidateOutline();
            }
        } else {
            mImage.setVisibility(View.GONE);
        }

        // Create bounds with rounded corners
        mViewOutlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, mImage.getWidth(), mImage.getHeight(), 20);
            }
        };
        mImage.setOutlineProvider(mViewOutlineProvider);
        mImage.setClipToOutline(true);
        
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mView.setDarkAmount(darkAmount);
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {}

    @Override
    public boolean shouldShowStatusArea() {
        return false;
    }
}
