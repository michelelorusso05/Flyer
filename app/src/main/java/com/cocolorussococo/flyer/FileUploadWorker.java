package com.cocolorussococo.flyer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class FileUploadWorker extends Worker {

    private static final int port = 10469;
    private final String target;
    private final Uri file;
    private final Context context;
    private final int id;
    private final boolean hasNotificationPermissions;

    public FileUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        Data data = workerParams.getInputData();
        target = data.getString("targetHost");
        file = Uri.parse(data.getString("file"));
        id = workerParams.getId().hashCode();

        this.context = context;
        hasNotificationPermissions = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    @NonNull
    @Override
    public Result doWork() {
        String filename = file.getPath().substring(file.getPath().lastIndexOf('/') + 1);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // Create notification
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, String.valueOf(42069))
                .setSilent(true)
                .setSmallIcon(R.drawable.outline_file_upload_24)
                .setContentTitle(filename)
                .setContentText("0%")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setProgress(100, 0, false);
        //.addAction(R.drawable.ic_baseline_stop_24, context.getString(R.string.stopDownload), stopDownload);

        if (hasNotificationPermissions)
            notificationManager.notify(id, builder.build());

        try (Socket socket = new Socket(target, port)) {
            InputStream fileStream = context.getContentResolver().openInputStream(file);

            int bytes;
            socket.setSendBufferSize(1024 * 1024);
            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

            byte[] filenameStringBytes = filename.getBytes();

            // Write filename size
            dataOutputStream.writeByte(filenameStringBytes.length);

            // Write filename
            dataOutputStream.write(filenameStringBytes);

            final long total = getSizeFromURI(context, file);

            // Write content size
            dataOutputStream.writeLong(total);

            final int startSize = dataOutputStream.size();

            byte[] buffer = new byte[1024 * 1024];
            while ((bytes = fileStream.read(buffer))
                    != -1) {
                dataOutputStream.write(buffer, 0, bytes);
                dataOutputStream.flush();

                final int progress = dataOutputStream.size() - startSize;
                final int percentage = (int) ((float) progress * 100f / total);
                builder
                        .setContentText(percentage + "%")
                        .setProgress(100, percentage, false);
                notificationManager.notify(id, builder.build());
            }
            fileStream.close();
            dataOutputStream.close();

            builder
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setContentText(context.getText(R.string.upload_complete));

            notificationManager.notify(id, builder.build());

            return Result.success();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Result.failure();
    }

    private static long getSizeFromURI(@NonNull Context context, Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        long size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
        cursor.close();

        return size;
    }
}