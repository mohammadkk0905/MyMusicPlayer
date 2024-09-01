package com.mohammadkk.mymusicplayer.utils;

import static android.os.Environment.getExternalStorageDirectory;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mohammadkk.mymusicplayer.models.Song;
import com.mohammadkk.mymusicplayer.providers.SortedCursor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public class FileUtils {
    public static final FileFilter AUDIO_FILE_FILTER = file -> {
        final boolean isAudio =  FileUtils.isAudioFile(file.getPath());
        return !file.isHidden() && (file.isDirectory() || isAudio);
    };

    @NonNull
    public static ArrayList<Pair<String, File>> listRoots() {
        ArrayList<Pair<String, File>> storageItems = new ArrayList<>();
        HashSet<String> paths = new HashSet<>();
        String defaultPath = getExternalStorageDirectory().getPath();
        String defaultPathState = Environment.getExternalStorageState();
        if (defaultPathState.equals(Environment.MEDIA_MOUNTED) || defaultPathState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            String mTitle = Environment.isExternalStorageRemovable() ? "SD Card" : "Internal Storage";
            File mFile = getExternalStorageDirectory();
            storageItems.add(new Pair<>(mTitle, mFile));
            paths.add(defaultPath);
        }

        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("vfat") || line.contains("/mnt")) {
                    StringTokenizer tokens = new StringTokenizer(line, " ");
                    tokens.nextToken();
                    String path = tokens.nextToken();
                    if (paths.contains(path)) continue;
                    if (line.contains("/dev/block/vold")) {
                        if (!line.contains("/mnt/secure") && !line.contains("/mnt/asec") && !line.contains("/mnt/obb") && !line.contains("/dev/mapper") && !line.contains("tmpfs")) {
                            if (!new File(path).isDirectory()) {
                                int index = path.lastIndexOf('/');
                                if (index != -1) {
                                    String newPath = "/storage/" + path.substring(index + 1);
                                    if (new File(newPath).isDirectory()) {
                                        path = newPath;
                                    }
                                }
                            }
                            paths.add(path);
                            try {
                                String mTitle = path.toLowerCase(Locale.ROOT).contains("sd") ? "SD Card" : "External Storage";
                                File mFile = new File(path);
                                storageItems.add(new Pair<>(mTitle, mFile));
                            } catch (Exception e) {
                                Log.w("FileUtils", "listRoots: " + e);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w("FileUtils", "listRoots: " + e);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Exception e) {
                    Log.w("FileUtils", "listRoots: " + e);
                }
            }
        }
        return storageItems;
    }
    public static String safeGetCanonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
    @Nullable
    private static String[] toPathArray(@Nullable List<File> files) {
        if (files != null) {
            String[] paths = new String[files.size()];
            for (int i = 0; i < files.size(); i++) {
                paths[i] = safeGetCanonicalPath(files.get(i));
            }
            return paths;
        }
        return null;
    }
    @NonNull
    public static List<File> listFilesDeep(@NonNull File directory, @Nullable FileFilter fileFilter) {
        List<File> files = new LinkedList<>();
        handleListFilesDeep(files, directory, fileFilter);
        return files;
    }
    private static void handleListFilesDeep(@NonNull Collection<File> files, @NonNull File dir, @Nullable FileFilter filter) {
        File[] found = dir.listFiles(filter);
        if (found != null) {
            for (File file : found) {
                if (file.isDirectory()) {
                    handleListFilesDeep(files, file, filter);
                } else {
                    files.add(file);
                }
            }
        }
    }
    public static boolean isAudioFile(@Nullable String path) {
        if (path == null) return false;
        int dotPos = path.lastIndexOf('.');
        if (dotPos == -1) return false;
        String[] extensions = new String[] {"mp3", "wav", "wma", "ogg", "m4a", "opus", "flac", "aac", "m4b"};
        String fileExtension = path.substring(dotPos + 1).toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (fileExtension.equals(extension)) {
                return true;
            }
        }
        return false;
    }
    @NonNull
    public static List<Song> matchFilesWithMediaStore(@NonNull Context context, @Nullable List<File> files) {
        return Libraries.fetchAllSongs(makeSongCursor(context, files));
    }
    @Nullable
    public static SortedCursor makeSongCursor(@NonNull final Context context, @Nullable final List<File> files) {
        String selection = null;
        String[] paths = null;
        if (files != null) {
            paths = toPathArray(files);
            final int filesSize = files.size();
            if (filesSize > 0 && filesSize < 999) {
                selection = "_data IN (" + makePlaceholders(files.size()) + ")";
            }
        }
        Cursor songCursor =  Libraries.makeSongCursor(context, selection, selection == null ? null : paths);
        return songCursor == null ? null : new SortedCursor(songCursor, paths, "_data");
    }
    private static String makePlaceholders(int len) {
        StringBuilder sb = new StringBuilder(len * 2 - 1);
        sb.append("?");
        for (int i = 1; i < len; i++) sb.append(",?");
        return sb.toString();
    }
}