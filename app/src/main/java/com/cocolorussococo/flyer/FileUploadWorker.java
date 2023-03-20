package com.cocolorussococo.flyer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.Socket;

public class FileUploadWorker extends Worker {

    private final int port;
    private final String target;
    private final Uri file;
    private final Context context;
    private final int id;
    private final boolean hasNotificationPermissions;
    final NotificationCompat.Builder builder;
    final NotificationManagerCompat notificationManager;
    boolean hasExceededQuota;
    ListenableFuture<Void> l;
    final long startTime;
    final String filename;

    @SuppressLint("MissingPermission")
    private void sendNotification() {
        builder.setWhen(startTime);

        if ((hasExceededQuota || (l != null && !l.isDone())) && hasNotificationPermissions)
            notificationManager.notify(id, builder.build());
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                l = setForegroundAsync(new ForegroundInfo(id, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE));
            }
            else {
                l = setForegroundAsync(new ForegroundInfo(id, builder.build()));
            }
        } catch (IllegalStateException e) {
            hasExceededQuota = true;
        }
    }

    @SuppressLint("RestrictedApi")
    public FileUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        Data data = workerParams.getInputData();
        target = data.getString("targetHost");
        port = data.getInt("port", 0);
        file = Uri.parse(data.getString("file"));
        id = workerParams.getId().hashCode();

        this.context = context;
        hasNotificationPermissions = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;


        filename = FileMappings.getFilenameFromURI(context, file);

        notificationManager = NotificationManagerCompat.from(context);

        // Create notification
        builder = new NotificationCompat.Builder(context, String.valueOf(42069))
                .setSilent(true)
                .setSmallIcon(R.drawable.outline_file_upload_24)
                .setContentTitle(filename)
                .setContentText("0%")
                .setShowWhen(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setProgress(100, 0, false);

        // Display notification as soon as possible (call needed for Android >= 12)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        startTime = builder.getWhenIfShowing();
        sendNotification();
    }

    @SuppressLint("MissingPermission")
    @NonNull
    @Override
    public Result doWork() {
        try (Socket socket = new Socket(target, port)) {
            InputStream fileStream = context.getContentResolver().openInputStream(file);

            int bytes;
            socket.setSoTimeout(5000);
            socket.setSendBufferSize(1024 * 1024);
            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

            byte[] filenameStringBytes = filename.getBytes();
            byte[] mimetypeStringBytes = context.getContentResolver().getType(file).getBytes();

            // Write filename size
            dataOutputStream.writeByte(filenameStringBytes.length);
            // Write filename
            dataOutputStream.write(filenameStringBytes);
            // Write mimetype length
            dataOutputStream.writeByte(mimetypeStringBytes.length);
            // Write mimetype
            dataOutputStream.write(mimetypeStringBytes);

            final long total = FileMappings.getSizeFromURI(context, file);
            long written = 0;

            // Write content size
            dataOutputStream.writeLong(total);

            byte[] buffer = new byte[1024 * 1024];
            int prevPercentage = 0;
            long elapsedMillis = System.currentTimeMillis();

            while ((bytes = fileStream.read(buffer))
                    != -1) {
                dataOutputStream.write(buffer, 0, bytes);
                written += bytes;

                final int percentage = (int) ((float) written * 100f / total);

                // Do not issue a notification update if the percentage hasn't changed.
                if (percentage == prevPercentage) continue;
                prevPercentage = percentage;

                // Do not issue a notification update if we are exceeding the rate limit (Android
                // allows for 10 updates/sec, but we further reduce it down to 5 updates/sec).
                if (System.currentTimeMillis() - elapsedMillis < 200) continue;
                elapsedMillis = System.currentTimeMillis();

                builder
                        .setContentText(percentage + "%")
                        .setProgress(100, percentage, false);

                sendNotification();
            }
            fileStream.close();
            dataOutputStream.close();

            builder
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setContentText(context.getText(R.string.upload_complete));

            if (hasNotificationPermissions)
                notificationManager.notify(id + 1, builder.build());

            return Result.success();
        } catch (ConnectException e) {
            builder
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setContentText(context.getText(R.string.send_refused));

            if (hasNotificationPermissions)
                notificationManager.notify(id + 1, builder.build());
        } catch (IOException e) {
            builder
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setContentText(context.getText(R.string.shared_fail));

            if (hasNotificationPermissions)
                notificationManager.notify(id + 1, builder.build());

            e.printStackTrace();
        }

        return Result.failure();
    }
}