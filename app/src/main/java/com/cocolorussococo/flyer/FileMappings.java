package com.cocolorussococo.flyer;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import java.util.HashMap;
import java.util.Map;

public class FileMappings {
    public static final HashMap<String, Integer> mappings;

    static {
        mappings = new HashMap<>();
        // Common types
        mappings.put("audio/*", R.drawable.outline_audiotrack_24);
        mappings.put("image/*", R.drawable.outline_image_24);
        mappings.put("video/*", R.drawable.outline_video_file_24);
        mappings.put("text/*", R.drawable.outline_text_snippet_24);
        mappings.put("application/pdf", R.drawable.outline_pdf_24);

        // Defaults
        mappings.put("application/octet-stream", R.drawable.baseline_binary_24);

        // Executables
        mappings.put("application/x-msdos-program", R.drawable.outline_applications_24);
        mappings.put("application/vnd.android.package-archive", R.drawable.outline_applications_24);

        // Compressed archive
        mappings.put("application/x-bzip", R.drawable.outline_folder_zip_24);
        mappings.put("application/x-bzip2", R.drawable.outline_folder_zip_24);
        mappings.put("application/gzip", R.drawable.outline_folder_zip_24);
        mappings.put("application/vnd.rar", R.drawable.outline_folder_zip_24);
        mappings.put("application/x-tar", R.drawable.outline_folder_zip_24);
        mappings.put("application/zip", R.drawable.outline_folder_zip_24);
        mappings.put("application/x-7z-compressed", R.drawable.outline_folder_zip_24);
    }
    public static Drawable getIconFromUri(Context context, Uri uri) {
        String mime = context.getContentResolver().getType(uri);

        Integer found;
        // Specific search
        found = mappings.get(mime);
        // Generic search
        if (found == null) {
            found = mappings.get(mime.substring(0, mime.lastIndexOf('/') + 1).concat("*"));
        }
        // Default (binary file)
        if (found == null) {
            found = R.drawable.baseline_binary_24;
        }
        return AppCompatResources.getDrawable(context, found);
    }


    public static String getFilenameFromURI(@NonNull Context context, Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        String name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        cursor.close();

        return name;
    }
    public static long getSizeFromURI(@NonNull Context context, Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        long size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
        cursor.close();

        return size;
    }
}
