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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class FileImageUtils {
    private static final String TAG = "FileUtils";

    private FileImageUtils() {
        // This class is not supposed to be instantiated
    }

    public static void saveImage(Context context, Uri imageUri, String customNameImage) {
        try {
            final InputStream imageStream = context.getContentResolver().openInputStream(imageUri);
            File file = new File(context.getFilesDir(), customNameImage);
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

    public static Bitmap loadImage(Context context, String customNameImage) {
        try {
            Bitmap result = null;
            File file = new File(context.getFilesDir(), customNameImage);
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
}