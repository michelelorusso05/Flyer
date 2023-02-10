package com.cocolorussococo.flyer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {

    ActivityResultLauncher<String> requestPermissionLauncher;


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
            // notificationManager.cancelAll();
        }
        requestPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    SharedPreferences.Editor e = this.getSharedPreferences("com.cocolorussococo.flyer", Context.MODE_PRIVATE).edit();
                    e.putBoolean("refusedNotifications", !isGranted);
                    e.apply();
                });
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
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                        .show();
            }
        }
    }
}