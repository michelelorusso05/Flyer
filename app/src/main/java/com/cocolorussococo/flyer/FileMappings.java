package com.cocolorussococo.flyer;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import java.util.HashMap;

public class FileMappings {
    public static final HashMap<String, Integer> mappings;

    static {
        mappings = new HashMap<>();
        // Common types
        mappings.put("audio/*", R.drawable.outline_audiotrack_24);
        mappings.put("image/*", R.drawable.outline_image_24);
        mappings.put("video/*", R.drawable.outline_video_file_24);
        mappings.put("text/*", R.drawable.outline_text_snippet_24);
        mappings.put("model/*", R.drawable.outline_model);
        mappings.put("application/pdf", R.drawable.outline_pdf_24);

        // Defaults
        mappings.put("application/octet-stream", R.drawable.outline_binary_file);

        // Executables
        mappings.put("application/x-msdos-program", R.drawable.outline_applications_24);
        mappings.put("application/vnd.android.package-archive", R.drawable.outline_android_24);


        // Compressed archive
        putAll(new String[]{
                "application/x-bzip",
                "application/x-bzip2",
                "application/gzip",
                "application/vnd.rar",
                "application/x-tar",
                "application/zip",
                "application/x-7z-compressed"
        }, R.drawable.outline_folder_zip_24);

        // Word files
        putAll(new String[]{
                "application/msword",
                "application/vnd.ms-word.document.macroenabled.12",
                "application/vnd.ms-word.template.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.template"
        }, R.drawable.outline_word);

        // Powerpoint files
        putAll(new String[]{
                "application/powerpoint",
                "application/mspowerpoint",
                "application/vnd.ms-powerpoint",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
                "application/vnd.ms-powerpoint.template.macroenabled.12",
                "application/vnd.ms-powerpoint.addin.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
        }, R.drawable.outline_powerpoint);

        // Excel files
        putAll(new String[]{
                "application/excel",
                "application/x-excel",
                "application/x-msexcel",
                "application/vnd.ms-excel",
                "application/vnd.ms-excel.sheet.macroenabled.12",
                "application/vnd.ms-excel.sheet.binary.macroenabled.12",
                "application/vnd.ms-excel.template.macroenabled.12",
                "application/vnd.ms-excel.addin.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
                "image/vnd.xiff"
        }, R.drawable.outline_excel);

        // Assembly torture files
        mappings.put("text/x-assembly", R.drawable.drawable_asm);

        // C source files
        putAll(new String[] {
                "text/x-c",
                "text/x-chdr",
                "text/x-csrc"
        }, R.drawable.drawable_c);

        // C++ source files
        putAll(new String[] {
                "text/x-c++hdr",
                "text/x-c++src"
        }, R.drawable.drawable_cpp);

        // C# source files
        mappings.put("text/x-csharp", R.drawable.drawable_csharp);

        // CSS
        putAll(new String[] {
                "text/css",
                "text/x-sass",
                "text/x-scss"
        }, R.drawable.drawable_css);

        // D source files
        mappings.put("text/x-d", R.drawable.drawable_d);

        // Dart source files
        mappings.put("application/dart", R.drawable.drawable_dart);

        // F# source files
        mappings.put("text/x-fsharp", R.drawable.drawable_fsharp);

        // Go source files
        mappings.put("text/x-go", R.drawable.drawable_go);

        // HTML
        putAll(new String[] {
                "application/xhtml+xml",
                "text/html"
        }, R.drawable.drawable_html);

        // Java source files
        putAll(new String[] {
                "application/java-vm",
                "application/java-serialized-object",
                "application/x-httpd-java",
                "application/x-java",
                "application/x-java-vm",
                "application/java-archive",
                "text/x-java",
                "text/x-java-properties",
                "text/x-java-source"
        }, R.drawable.drawable_java);

        // JavaScript source files
        putAll(new String[] {
                "application/ecmascript",
                "application/node",
                "application/javascript",
                "application/x-javascript",
                "text/javascript"
        }, R.drawable.drawable_javascript);

        // JSON
        putAll(new String[] {
                "text/json",
                "application/json",
        }, R.drawable.drawable_json);

        // Kotlin source files
        mappings.put("text/x-kotlin", R.drawable.drawable_kotlin);

        // Lua source files
        mappings.put("text/x-lua", R.drawable.drawable_lua);

        // Markdown
        putAll(new String[] {
                "text/markdown",
                "text/x-web-markdown",
                "text/x-gfm",
                "text/x-markdown"
        }, R.drawable.drawable_markdown);

        // Perl source files
        putAll(new String[] {
                "text/x-perl",
                "application/x-perl"
        }, R.drawable.drawable_perl);

        // PHP source files
        putAll(new String[] {
                "application/x-httpd-php",
                "text/x-php"
        }, R.drawable.drawable_php);

        // Python source files
        putAll(new String[] {
                "application/x-python-bytecode",
                "text/x-python"
        }, R.drawable.drawable_python);

        // R source files
        mappings.put("text/x-rscr", R.drawable.drawable_r);

        // Ruby source files
        mappings.put("text/x-ruby", R.drawable.drawable_ruby);

        // Rust source files
        mappings.put("text/x-rustsrc", R.drawable.drawable_rust);

        // Swift source files
        mappings.put("text/x-swift", R.drawable.drawable_swift);

        // TypeScript source files
        mappings.put("application/typescript", R.drawable.drawable_typescript);

        // Generic XML
        putAll(new String[] {
                "text/xml",
                "application/xml"
        }, R.drawable.drawable_xml);
    }
    private static void putAll(String[] types, int icon) {
        assert mappings != null;
        for (String type : types)
            mappings.put(type, icon);
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
        // TODO: Custom failsafe extension mapping
        // Default (binary file)
        if (found == null) {
            found = R.drawable.outline_binary_file;
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
