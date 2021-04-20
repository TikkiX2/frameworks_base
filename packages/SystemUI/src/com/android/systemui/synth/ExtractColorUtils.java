/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.systemui.synth;

import android.util.Log;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.util.LayoutDirection;

import androidx.annotation.VisibleForTesting;
import androidx.palette.graphics.Palette;

import com.android.internal.util.ContrastColorUtil;
import com.android.internal.util.ImageUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.notification.MediaNotificationProcessor;

import java.util.List;

public final class ExtractColorUtils {
    private static final String TAG = "ExtractColorUtils";

    private ExtractColorUtils() {
        // This class is not supposed to be instantiated
    }

    public static int[] extractColor(Drawable image) {
        return extractColor(image != null ? ImageUtils.buildScaledBitmap(image, image.getIntrinsicWidth(), image.getIntrinsicHeight()) : null);
    }

    public static int extractBackgroundColor(Drawable image) {
        int[] colors = extractColor(image);
        return colors[0];
    }

    public static int extractForegroundColor(Drawable image) {
        int[] colors = extractColor(image);
        return colors[1];
    }

    public static int[] extractColor(Bitmap image) {
        if (image == null) return new int[] {0, 0};

        Palette.Builder paletteBuilder = MediaNotificationProcessor.generateArtworkPaletteBuilder(image);
        Palette palette = paletteBuilder.generate();
        Palette.Swatch backgroundSwatch = MediaNotificationProcessor.findBackgroundSwatch(palette);
        int backgroundColor = backgroundSwatch.getRgb();
        paletteBuilder.setRegion((int) (image.getWidth() * 0.4f), 0,
                image.getWidth(),
                image.getHeight());
        palette = paletteBuilder.generate();
        int foregroundColor = MediaNotificationProcessor.selectForegroundColor(backgroundColor, palette);
        return new int[] {backgroundColor, foregroundColor};
    }

    public static int extractBackgroundColor(Bitmap image) {
        int[] colors = extractColor(image);
        return colors[0];
    }

    public static int extractForegroundColor(Bitmap image) {
        int[] colors = extractColor(image);
        return colors[1];
    }
}