package com.cocolorussococo.flyer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.futures.SettableFuture;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.text.DecimalFormat;

public class FileDownloadWorker extends Worker {
    final Context ctx;
    final boolean hasNotificationPermissions;
    final int id;
    final NotificationCompat.Builder builder;
    final NotificationManagerCompat notificationManager;
    boolean hasExceededQuota;
    ListenableFuture<Void> l;
    final long startTime;
    final Socket socket;

    @SuppressLint("MissingPermission")
    @Override
    public void onStopped() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    @SuppressLint({"MissingPermission", "RestrictedApi"})
    private void sendNotification() {
        Log.d("Notification", "Updated");
        builder.setWhen(startTime);

        if (hasExceededQuota && hasNotificationPermissions)
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
    public FileDownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        ctx = context;

        id = workerParams.getId().hashCode();
        hasNotificationPermissions = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

        notificationManager = NotificationManagerCompat.from(ctx);


        // Create notification
        builder = new NotificationCompat.Builder(ctx, String.valueOf(42069))
                .setSilent(true)
                .setSmallIcon(R.drawable.outline_file_download_24)
                .setShowWhen(true)
                .setContentText("0% · 0 MB/s")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setProgress(100, 0, false);

        // Display notification as soon as possible (call needed for Android >= 12)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        startTime = builder.getWhenIfShowing();
        socket = DownloadActivity.consumeSocket();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("MissingPermission")
    @NonNull
    @Override
    public Result doWork() {
        Uri toSave = null;
        try {
            socket.setSoTimeout(5000);
            socket.setReceiveBufferSize(1024 * 1024);
            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());

            int bytes;

            int filenameLength = dataInputStream.readByte();
            byte[] filenameBuffer = new byte[filenameLength];
            dataInputStream.read(filenameBuffer);

            int mimetypeLength = dataInputStream.readByte();
            byte[] mimetypeBuffer = new byte[mimetypeLength];
            dataInputStream.read(mimetypeBuffer);

            String filename = new String(filenameBuffer);
            String mimeType = new String(mimetypeBuffer);

            PendingIntent cancelIntent = WorkManager.getInstance(ctx).createCancelPendingIntent(getId());
            builder
                    .setContentTitle(filename)
                    .addAction(0, ctx.getString(R.string.transfer_cancel), cancelIntent);
            sendNotification();

            Pair<OutputStream, Uri> pair = openOutputStreamForDownloadedFile(ctx, filename, mimeType);
            toSave = pair.second;
            if (pair.first == null) throw new RuntimeException("Something went wrong when opening output file.");
            OutputStream fileOutputStream = pair.first;

            long size = dataInputStream.readLong();
            long total = size;

            byte[] buffer = new byte[1024 * 1024];
            int prevPercentage = 0;
            long speedMillis = System.currentTimeMillis();
            long elapsedMillis = System.currentTimeMillis();
            long bytesConsumedInTime = 0;
            boolean shouldUpdateNotification = false;

            final DecimalFormat decimalFormat = new DecimalFormat("0.00");
            String speed = "0 MB/s";

            while (size > 0) {
                bytes = dataInputStream.read(buffer, 0, (int) Math.min(buffer.length, size));
                if (bytes == -1) throw new InterruptedException("Socket has been closed by remote host.");

                bytesConsumedInTime += bytes;

                fileOutputStream.write(buffer, 0, bytes);
                size -= bytes;

                // Progress logic check
                final long progress = total - size;
                final int percentage = (int) ((float) progress * 100f / total);
                // Do not issue a notification update if the percentage hasn't changed.
                if (percentage != prevPercentage) shouldUpdateNotification = true;
                prevPercentage = percentage;

                // Speed update (at least once a second) check
                long curTime = System.currentTimeMillis();
                if (curTime - speedMillis >= 1000) {
                    shouldUpdateNotification = true;
                    speedMillis = curTime;

                    speed = decimalFormat.format((float) bytesConsumedInTime / 1000000f).concat(" MB/s");
                    bytesConsumedInTime = 0;
                }

                // Do not issue a notification update if we are exceeding the rate limit (Android
                // allows for 10 updates/sec, but we further reduce it down to 2 updates/sec).
                if (System.currentTimeMillis() - elapsedMillis < 500) continue;
                elapsedMillis = System.currentTimeMillis();

                if (shouldUpdateNotification) {
                    builder
                            .setContentText(percentage + "% · " + speed)
                            .setProgress(100, percentage, false);

                    sendNotification();
                    shouldUpdateNotification = false;
                }
            }

            dataInputStream.close();
            fileOutputStream.close();

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.setDataAndType(toSave, mimeType);

            Intent chooser = Intent.createChooser(intent, ctx.getText(R.string.file_chooser_label));
            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, id, chooser,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            builder
                    .setOngoing(false)
                    .setProgress(0,0, false)
                    .setContentText(ctx.getText(R.string.download_complete))
                    .setAutoCancel(true)
                    .clearActions()
                    .setContentIntent(pendingIntent);

            sendNotification();

            if (hasNotificationPermissions)
                notificationManager.notify(id + 1, builder.build());
        } catch (InterruptedException | SocketException e) {
            e.printStackTrace();
            builder
                    .setOngoing(false)
                    .setProgress(0,0, false)
                    .setContentText(ctx.getText(R.string.transfer_cancelled))
                    .clearActions();

            // ConnectionReset, ConnectionAbort and NetworkUnreachable fall into the "An error occourred" category
            if (e.getMessage() != null &&
                    (e.getMessage().contains("ENETUNREACH") || e.getMessage().contains("abort")))
                builder.setContentText(ctx.getText(R.string.shared_fail));

            sendNotification();

            if (hasNotificationPermissions)
                notificationManager.notify(id + 1, builder.build());

            onDownloadFailed(toSave);
            return Result.failure();
        } catch (IOException e) {
            e.printStackTrace();
            builder
                    .setOngoing(false)
                    .setProgress(0,0, false)
                    .setContentText(ctx.getText(R.string.shared_fail))
                    .clearActions();

            sendNotification();

            if (hasNotificationPermissions)
                notificationManager.notify(id + 1, builder.build());

            onDownloadFailed(toSave);
            return Result.failure();
        }
        return Result.success();
    }

    private void onDownloadFailed(@Nullable Uri corruptedFile) {
        if (corruptedFile == null) return;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!new File(corruptedFile.getPath()).delete())
                Log.w("Deletion error", "Unable to delete " + corruptedFile.getPath());
        }
        else {
            ctx.getContentResolver().delete(corruptedFile, null, null);
        }
    }

    private static @NonNull Pair<OutputStream, Uri> openOutputStreamForDownloadedFile(Context ctx, String filename, String mimetype) {
        try {
            Uri uri;
            OutputStream outputFile;
            // Android 9 and below
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                String s = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
                File f = new File(s, filename);
                uri = Uri.fromFile(f);
                //noinspection IOStreamConstructor
                outputFile = new FileOutputStream(f);
            }
            // Android 10 and above
            else {
                final ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                // MediaStore likes to override a file's extension when it's set to text/plain,
                // so we'll not put a MimeType entry for text files
                if (!mimetype.equals("text/plain"))
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);

                final Uri contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;

                Cursor c = ctx.getContentResolver().query(
                        contentUri,
                        new String[] { MediaStore.Downloads._ID },
                        MediaStore.Downloads.DISPLAY_NAME + " LIKE ?",
                        new String[]{ filename },
                        null
                );

                if (c != null) {
                    Log.w("Duplicate file", "File exists");
                    c.close();
                }

                uri = ctx.getContentResolver().insert(contentUri, values);
                outputFile = ctx.getContentResolver().openOutputStream(uri);
            }

            return new Pair<>(outputFile, uri);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Pair<>(null, null);
    }
}
