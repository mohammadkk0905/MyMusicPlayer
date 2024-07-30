package com.mohammadkk.mymusicplayer.utils;

import static android.os.Environment.getExternalStorageDirectory;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mohammadkk.mymusicplayer.models.FileItem;

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
    @NonNull
    public static List<FileItem> listFilesDeep(@NonNull Context context, @NonNull Uri treeUri) {
        List<FileItem> files = new LinkedList<>();
        handleListFilesDeep(context, files, treeUri);
        return files;
    }
    private static void handleListFilesDeep(@NonNull Context context, @NonNull Collection<FileItem> files, @NonNull Uri treeUri) {
        List<FileItem> found = listFiles(context, treeUri);
        for (FileItem file : found) {
            if (file.isDirectory()) {
                handleListFilesDeep(context, files, file.getContentUri());
            } else if (isAudioFile(file.getFilename())) {
                files.add(file);
            }
        }
    }
    private static List<FileItem> listFiles(Context context, Uri treeUri) {
        final ContentResolver resolver = context.getContentResolver();
        final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                DocumentsContract.getDocumentId(treeUri));
        final String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        final List<FileItem> results = new ArrayList<>();
        Cursor c = null;
        try {
            c = resolver.query(childrenUri, projection, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    final String documentId = c.getString(0);
                    final Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                    final String name = c.getString(1);
                    final String mimetype = c.getString(2);
                    final boolean isDirectory = mimetype.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    final long modified =  c.getLong(3);
                    results.add(new FileItem(name, isDirectory, modified, docUri));
                }
            }
        } catch (Exception e) {
            Log.w("FileUtils", "Failed query: " + e);
        } finally {
            closeQuietly(c);
        }
        return results;
    }
    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
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
    public static boolean deleteSingle(@NonNull Context context, Uri treeUri) {
        try {
            return DocumentsContract.deleteDocument(context.getContentResolver(), treeUri);
        } catch (Exception e) {
            return false;
        }
    }
}