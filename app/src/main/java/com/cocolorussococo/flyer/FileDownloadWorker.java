package com.cocolorussococo.flyer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class FileDownloadWorker extends Worker {
    final Context ctx;
    final boolean hasNotificationPermissions;
    final int id;

    public FileDownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        ctx = context;

        id = workerParams.getId().hashCode();
        hasNotificationPermissions = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    @NonNull
    @Override
    public Result doWork() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ctx);

        // Create notification
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, String.valueOf(42069))
                .setSilent(true)
                .setSmallIcon(R.drawable.outline_file_download_24)
                .setContentText("0%")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(100, 0, false);

        try (Socket socket = DownloadActivity.consumeSocket();) {
            socket.setSoTimeout(5000);
            socket.setReceiveBufferSize(1024 * 1024);
            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());

            int bytes = 0;

            int filenameLength = dataInputStream.readByte();
            byte[] filenameBuffer = new byte[filenameLength];
            dataInputStream.read(filenameBuffer);

            int mimetypeLength = dataInputStream.readByte();
            byte[] mimetypeBuffer = new byte[mimetypeLength];
            dataInputStream.read(mimetypeBuffer);

            String filename = new String(filenameBuffer);
            String mimeType = new String(mimetypeBuffer);

            System.out.println(filenameLength + " " + filename);
            System.out.println(mimetypeLength + " " + mimeType);

            builder.setContentTitle(filename);
            if (hasNotificationPermissions) {
                notificationManager.notify(id, builder.build());
            }

            Pair<OutputStream, Uri> pair = openOutputStreamForDownloadedFile(ctx, filename, mimeType);
            if (pair.first == null) throw new RuntimeException("Something went wrong when opening output file.");
            OutputStream fileOutputStream = pair.first;


            long size = dataInputStream.readLong();
            long total = size;

            System.out.println(size);

            byte[] buffer = new byte[1024 * 1024];
            int prevPercentage = 0;
            long elapsedMillis = System.currentTimeMillis();
            while (size > 0
                    && (bytes = dataInputStream.read(
                    buffer, 0,
                    (int)Math.min(buffer.length, size)))
                    != -1) {
                fileOutputStream.write(buffer, 0, bytes);
                size -= bytes;

                final long progress = total - size;
                final int percentage = (int) ((float) progress * 100f / total);
                // Do not issue a notification update if the percentage hasn't changed.
                if (percentage == prevPercentage) continue;
                // Do not issue a notification update if we are exceeding the rate limit (Android
                // allows for 10 updates/sec, but we further reduce it down to 5 updates/sec).
                if (System.currentTimeMillis() - elapsedMillis < 200) continue;
                elapsedMillis = System.currentTimeMillis();
                builder
                        .setContentText(percentage + "%")
                        .setProgress(100, percentage, false);
                if (hasNotificationPermissions) {
                    notificationManager.notify(id, builder.build());
                }
            }

            dataInputStream.close();
            fileOutputStream.close();

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.setDataAndType(pair.second, mimeType);

            Intent chooser = Intent.createChooser(intent, ctx.getText(R.string.file_chooser_label));
            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, id, chooser,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            builder
                    .setOngoing(false)
                    .setProgress(0,0, false)
                    .setContentText(ctx.getText(R.string.download_complete))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            if (hasNotificationPermissions) {
                // Cancel the last notification, to force the next (final) one to be showed.
                notificationManager.cancel(id);
                notificationManager.notify(id, builder.build());
            }

        } catch (IOException e) {
            builder
                    .setOngoing(false)
                    .setProgress(0,0, false)
                    .setContentText(ctx.getText(R.string.shared_fail));

            if (hasNotificationPermissions) {
                // Cancel the last notification, to force the next (final) one to be showed.
                notificationManager.cancel(id);
                notificationManager.notify(id, builder.build());
            }

            e.printStackTrace();
            return Result.failure();
        }
        return Result.success();
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
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);

                final Uri contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                uri = ctx.getContentResolver().insert(contentUri, values);

                outputFile = ctx.getContentResolver().openOutputStream(uri);
            };

            return new Pair<>(outputFile, uri);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Pair<>(null, null);
    }
}
