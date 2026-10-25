/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.model;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.FileAsset;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.PartnerProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Represents a wallpaper from the "partner customization" APK installed on the system.
 */
public class LegacyPartnerWallpaperInfo extends WallpaperInfo {
    public static final Creator<LegacyPartnerWallpaperInfo> CREATOR =
            new Creator<LegacyPartnerWallpaperInfo>() {
                @Override
                public LegacyPartnerWallpaperInfo createFromParcel(Parcel in) {
                    return new LegacyPartnerWallpaperInfo(in);
                }

                @Override
                public LegacyPartnerWallpaperInfo[] newArray(int size) {
                    return new LegacyPartnerWallpaperInfo[size];
                }
            };
    private String mThumbName;
    private String mFullName;
    private File mSystemLegacyDir;
    private boolean mFetchedSystemLegacyDir;
    private FileAsset mAsset;
    private FileAsset mThumbAsset;

    public LegacyPartnerWallpaperInfo(String thumbName, String fullName) {
        mThumbName = thumbName;
        mFullName = fullName;
    }

    private LegacyPartnerWallpaperInfo(Parcel in) {
        super(in);
        mThumbName = in.readString();
        mFullName = in.readString();
    }

    /**
     * @param ctx
     * @return All legacy partner and read-only system wallpapers found on the device.
     */
    public static List<WallpaperInfo> getAll(Context ctx) {
        PartnerProvider partnerProvider = InjectorProvider.getInjector().getPartnerProvider(ctx);

        List<WallpaperInfo> wallpaperInfos = new ArrayList<>();
        List<File> searchDirs = new ArrayList<>();

        // Add legacy partner directory if present.
        File legacyDir = partnerProvider.getLegacyWallpaperDirectory();
        if (legacyDir != null && legacyDir.isDirectory()) {
            searchDirs.add(legacyDir);
        }

        // Add read-only system and product wallpaper directories.
        File[] fallbackDirs = new File[] {
            new File("/product/media/wallpapers"),
            new File("/system/media/wallpapers"),
            new File("/system_ext/media/wallpapers")
        };
        for (File dir : fallbackDirs) {
            if (dir.exists() && dir.isDirectory() && !searchDirs.contains(dir)) {
                searchDirs.add(dir);
            }
        }

        for (File systemDir : searchDirs) {
            File[] files = systemDir.listFiles();
            if (files == null) {
                continue;
            }

            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                String name = file.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg")
                        && !lower.endsWith(".png") && !lower.endsWith(".webp")) {
                    continue;
                }
                if (lower.contains("_small.")) {
                    continue;
                }

                int dotPos = name.lastIndexOf('.');
                String dotExt = (dotPos > -1) ? name.substring(dotPos) : "";
                String baseName = (dotPos > -1) ? name.substring(0, dotPos) : name;
                File smallFile = new File(systemDir, baseName + "_small" + dotExt);
                String thumbPath = smallFile.exists() ? smallFile.getAbsolutePath() : file.getAbsolutePath();

                wallpaperInfos.add(new LegacyPartnerWallpaperInfo(thumbPath, file.getAbsolutePath()));
            }
        }

        return wallpaperInfos;
    }

    /**
     * Gets (and caches) the system legacy directory. May return null if no such directory is found
     * (which is the case for newer devices).
     */
    private File getSystemLegacyDir(Context context) {
        if (!mFetchedSystemLegacyDir) {
            PartnerProvider partnerProvider = InjectorProvider.getInjector().getPartnerProvider(context);
            mSystemLegacyDir = partnerProvider.getLegacyWallpaperDirectory();
            mFetchedSystemLegacyDir = true;
        }

        return mSystemLegacyDir;
    }

    public Drawable getThumbnail(Context context) {
        File thumbnail;
        if (mThumbName != null && mThumbName.startsWith("/")) {
            thumbnail = new File(mThumbName);
        } else {
            final File systemDir = getSystemLegacyDir(context);
            thumbnail = (systemDir == null) ? null : new File(systemDir, mThumbName);
        }
        if (thumbnail == null || !thumbnail.exists()) {
            return null;
        }

        Bitmap thumbBitmap = BitmapFactory.decodeFile(thumbnail.getAbsolutePath());
        return (thumbBitmap != null) ? new BitmapDrawable(context.getResources(), thumbBitmap) : null;
    }

    @Override
    public String getWallpaperId() {
        return mFullName;
    }

    @Override
    public String getTitle(Context context) {
        if (mFullName == null) {
            return null;
        }
        String fileName = new File(mFullName).getName();
        int dotPos = fileName.lastIndexOf('.');
        if (dotPos > 0) {
            fileName = fileName.substring(0, dotPos);
        }
        String[] words = fileName.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1));
            }
        }
        return (sb.length() > 0) ? sb.toString() : fileName;
    }

    @Override
    public List<String> getAttributions(Context context) {
        return Arrays.asList(context.getResources().getString(R.string.on_device_wallpaper_title));
    }

    @Override
    public Asset getAsset(Context context) {
        if (mAsset == null) {
            File fullSizeImage;
            if (mFullName != null && mFullName.startsWith("/")) {
                fullSizeImage = new File(mFullName);
            } else {
                final File systemDir = getSystemLegacyDir(context);
                fullSizeImage = (systemDir == null) ? null : new File(systemDir, mFullName);
            }
            mAsset = new FileAsset(fullSizeImage);
        }
        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        if (mThumbAsset == null) {
            File thumbnail;
            if (mThumbName != null && mThumbName.startsWith("/")) {
                thumbnail = new File(mThumbName);
            } else {
                final File systemDir = getSystemLegacyDir(context);
                thumbnail = (systemDir == null) ? null : new File(systemDir, mThumbName);
            }
            mThumbAsset = new FileAsset(thumbnail);
        }
        return mThumbAsset;
    }

    @Override
    public String getCollectionId(Context context) {
        return context.getString(R.string.on_device_wallpaper_collection_id);
    }

    @Override
    public void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
                            int requestCode, boolean isAssetIdPresent) {
        srcActivity.startActivityForResult(factory.newIntent(srcActivity, this,
                isAssetIdPresent, false), requestCode);
    }

    @Override
    public void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
            int requestCode, boolean isAssetIdPresent,
            boolean shouldRefreshCategory) {
        srcActivity.startActivityForResult(factory.newIntent(srcActivity, this,
                isAssetIdPresent, shouldRefreshCategory), requestCode);
    }

    @Override
    @BackupPermission
    public int getBackupPermission() {
        return BACKUP_NOT_ALLOWED;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeString(mThumbName);
        parcel.writeString(mFullName);
    }

}
