package com.retro.subsonic;

import android.content.Context;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class CacheManager {

    private static final String FOLDER_NAME = "subsonic_music_cache";

    public static File getCacheFolder(Context context) {
        File baseDir = context.getExternalCacheDir();
        if (baseDir == null) {
            baseDir = context.getCacheDir();
        }
        File folder = new File(baseDir, FOLDER_NAME);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static File getSongFile(Context context, String songId) {
        return new File(getCacheFolder(context), songId + ".mp3");
    }

    public static File getTempFile(Context context, String songId) {
        return new File(getCacheFolder(context), songId + ".tmp");
    }

    public static boolean isSongCached(Context context, String songId) {
        File file = getSongFile(context, songId);
        return file.exists() && file.length() > 1024; // 大于 1KB 视为有效缓存
    }

    public static long getUsedCacheBytes(Context context) {
        File folder = getCacheFolder(context);
        File[] files = folder.listFiles();
        if (files == null) return 0;
        long total = 0;
        for (File f : files) {
            if (f.isFile()) {
                total += f.length();
            }
        }
        return total;
    }

    public static void clearAllCache(Context context) {
        File folder = getCacheFolder(context);
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files) {
            f.delete();
        }
    }

    // LRU 淘汰机制：超出上限时删除最久未访问的歌曲
    public static void trimCache(Context context, long maxBytes, String currentPlayingSongId) {
        File folder = getCacheFolder(context);
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) return;

        long currentSize = 0;
        for (File f : files) {
            if (f.isFile()) currentSize += f.length();
        }

        if (currentSize <= maxBytes) return;

        // 按最后修改时间升序排列（最老的排前面）
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
            }
        });

        String safeFileName = currentPlayingSongId + ".mp3";
        for (File f : files) {
            if (currentSize <= maxBytes) break;
            if (f.isFile() && !f.getName().equals(safeFileName)) {
                long len = f.length();
                if (f.delete()) {
                    currentSize -= len;
                }
            }
        }
    }
}
