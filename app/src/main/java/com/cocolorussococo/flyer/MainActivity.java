package com.cocolorussococo.flyer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                SharedPreferences.Editor e = this.getSharedPreferences("com.cocolorussococo.flyer", Context.MODE_PRIVATE).edit();
                e.putBoolean("refusedNotifications", !isGranted);
                e.apply();
            });
    ActivityResultLauncher<String> requestStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) startActivity(new Intent(this, DownloadActivity.class));
            });


    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(String.valueOf(42069), name, importance);
            channel.setDescription("Progress notification");
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button upload = findViewById(R.id.uploadButton);
        upload.setOnClickListener((v) -> {
            startActivity(new Intent(this, UploadActivity.class));
        });

        Button download = findViewById(R.id.downloadButton);
        download.setOnClickListener((v) -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (!(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
                    new MaterialAlertDialogBuilder(this)
                            .setIcon(R.drawable.outline_folder_open_24)
                            .setTitle(R.string.storage_dialog_title)
                            .setMessage(R.string.storage_dialog_desc)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                            .show();
                    return;
                }
            }
            startActivity(new Intent(this, DownloadActivity.class));
        });

        createNotificationChannel();

        // Handle notification permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            boolean disableNotifications = this.getSharedPreferences("com.cocolorussococo.flyer", Context.MODE_PRIVATE).getBoolean("refusedNotifications", false);

            if (!disableNotifications)
            {
                new MaterialAlertDialogBuilder(this)
                        .setIcon(R.drawable.outline_notifications_24)
                        .setTitle(R.string.notification_dialog_title)
                        .setMessage(R.string.notification_dialog_desc)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                        .show();
            }
        }
    }

    private void writeToFile() {
        try {
            OutputStream outputFile;
            // Android 9 and below
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (!(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
                    new MaterialAlertDialogBuilder(this)
                            .setIcon(R.drawable.outline_folder_open_24)
                            .setTitle(R.string.storage_dialog_title)
                            .setMessage(R.string.storage_dialog_desc)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                            .show();
                    return;
                }
                String s = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
                File f = new File(s, "Ciao.txt");
                outputFile = new FileOutputStream(f);
            }
            // Android 10 and above
            else {
                final ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, "Ciao.txt");
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");

                final Uri contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri uri = getContentResolver().insert(contentUri, values);

                outputFile = getContentResolver().openOutputStream(uri);
            }

            outputFile.write("Ciao".getBytes());

            outputFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}