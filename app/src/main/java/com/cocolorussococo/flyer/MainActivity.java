package com.cocolorussococo.flyer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;

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
        upload.setOnClickListener((v) -> launchActivityChecked(UploadActivity.class));

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
            launchActivityChecked(DownloadActivity.class);
        });

        findViewById(R.id.dozeWarningClickableLayout).setOnClickListener((v) -> {
            // Direct mode, violates Google Play Policies
            // startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            //      Uri.parse("package:com.cocolorussococo.flyer")));

            new MaterialAlertDialogBuilder(this)
                    //.setIcon(R.drawable.outline_notifications_24)
                    .setTitle(R.string.power_optimization_title)
                    .setMessage(R.string.power_optimization_desc)
                    .setPositiveButton(R.string.take_me_to_settings, (dialog, which) ->
                            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {})
                    .show();
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

    @Override
    protected void onResume() {
        super.onResume();
        boolean allowInDozeMode = getSystemService(PowerManager.class)
                .isIgnoringBatteryOptimizations("com.cocolorussococo.flyer");
        findViewById(R.id.dozeModeWarning).setVisibility(allowInDozeMode ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_debugmenu) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Informazioni di debug")
                    .setMessage("Build del 06/04/2023\n\nVersione protocollo Discovery: 1.0\nVersione protocollo Flow: 1.0")
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void launchActivityChecked(Class<?> cls) {
        if (hasValidInterfaces()) {
            startActivity(new Intent(this, cls));
        }
        else {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.outline_wifi_24)
                    .setTitle(R.string.no_interfaces_available_dialog_title)
                    .setMessage(R.string.no_interfaces_available_dialog_desc)
                    // Open WiFi settings
                    .setPositiveButton(R.string.open_wifi_settings, (dialog, which) -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)))
                    // Open Router&Tethering settings
                    .setNegativeButton(R.string.open_hotspot_settings, (dialog, which) -> {
                        Intent tetherSettings = new Intent();
                        tetherSettings.setClassName("com.android.settings", "com.android.settings.TetherSettings");

                        startActivity(tetherSettings);
                    })
                    .setNeutralButton(android.R.string.ok, (dialog, which) -> {})
                    .show();
        }
    }
    private boolean hasValidInterfaces() {
        try {
            return Host.getActiveInterfaces().size() > 0;
        } catch (SocketException e) {
            return false;
        }
    }
}