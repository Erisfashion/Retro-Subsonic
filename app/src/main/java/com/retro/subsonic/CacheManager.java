package com.retro.subsonic;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Comparator;

public class CacheManager {

    private static final String FOLDER_NAME = "subsonic_music_cache";

    public static File getCacheFolder(Context context) {
        File baseDir = null;
        try {
            baseDir = context.getExternalCacheDir();
        } catch (Throwable ignored) {}

        if (baseDir == null || (!baseDir.exists() && !baseDir.mkdirs())) {
            baseDir = context.getCacheDir();
        }

        File folder = new File(baseDir, FOLDER_NAME);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static File getSongFile(Context context, String songId) {
        return new File(getCacheFolder(context), sanitizeFileName(songId) + ".mp3");
    }

    public static File getTempFile(Context context, String songId) {
        return new File(getCacheFolder(context), sanitizeFileName(songId) + ".tmp");
    }

    private static String sanitizeFileName(String id) {
        return id.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    // 核心：严格校验音频文件魔数，拒绝把 JSON、XML 报错文本误认作音乐缓存
    public static boolean isValidAudioFile(File file) {
        if (file == null || !file.exists() || file.length() < 32 * 1024) {
            return false;
        }
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] header = new byte[12];
            int read = fis.read(header);
            if (read < 4) return false;

            // 1. MP3 (包含 ID3v2 标签或 MP3 帧同步字 0xFFEx / 0xFFFx)
            if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') return true;
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0) return true;

            // 2. FLAC
            if (header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C') return true;

            // 3. OGG Vorbis
            if (header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S') return true;

            // 4. WAV (RIFF)
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F') return true;

            // 5. M4A / AAC (ftyp)
            if (read >= 8 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') return true;
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xF6) == 0xF0) return true;

            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (fis != null) try { fis.close(); } catch (Exception ignored) {}
        }
    }

    public static boolean isSongCached(Context context, String songId) {
        File file = getSongFile(context, songId);
        if (file.exists()) {
            if (isValidAudioFile(file)) {
                return true;
            } else {
                // 若被污染为非音频文件，立即自动物理粉碎，防止卡死
                file.delete();
            }
        }
        return false;
    }

    public static long getUsedCacheBytes(Context context) {
        File folder = getCacheFolder(context);
        File[] files = folder.listFiles();
        if (files == null) return 0;
        long total = 0;
        for (File f : files) {
            if (f.isFile()) total += f.length();
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

    public static void trimCache(Context context, long maxBytes, String currentPlayingSongId) {
        File folder = getCacheFolder(context);
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) return;

        long currentSize = 0;
        for (File f : files) {
            if (f.isFile()) currentSize += f.length();
        }

        if (currentSize <= maxBytes) return;

        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
            }
        });

        String safeFileName = sanitizeFileName(currentPlayingSongId) + ".mp3";
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
