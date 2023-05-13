package com.cocolorussococo.flyer;

import static com.cocolorussococo.flyer.PacketUtils.ellipsize;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
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
import androidx.core.content.FileProvider;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    final Data.Builder progressData;
    PacketUtils.FlowType communicationType;

    @SuppressLint("MissingPermission")
    @Override
    public void onStopped() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    @SuppressLint({"MissingPermission", "RestrictedApi"})
    private void sendNotification() {
        // Do not send notifications for text transfer
        if (communicationType == PacketUtils.FlowType.TEXT) return;

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

        progressData = new Data.Builder();
        // sendNotification();
    }

    @SuppressLint({"MissingPermission", "RestrictedApi"})
    @NonNull
    @Override
    public Result doWork() {
        Uri toSave = null;
        try {
            socket.setSoTimeout(5000);
            socket.setReceiveBufferSize(1024 * 1024);
            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());

            int bytes;

            byte version = dataInputStream.readByte();
            byte typeOfContent = dataInputStream.readByte();

            if (version != PacketUtils.FLOW_PROTOCOL_VERSION) Log.w("Mismatching Flow version", "Shenanigans might happen");

            communicationType = PacketUtils.flowTypeFromInt(typeOfContent);

            if (communicationType.equals(PacketUtils.FlowType.NOT_SUPPORTED)) {
                Log.e("Unsupported content type", "Not implemented yet");
                socket.sendUrgentData(0x42);
                socket.close();
                return Result.failure(progressData.putInt("unsupportedCommunication", typeOfContent).build());
            }

            String transmitterName = readStringFromStream(dataInputStream);
            String filename = readStringFromStream(dataInputStream);

            progressData
                    .putString("transmitterName", transmitterName)
                    .putInt("transmissionType", typeOfContent);


            if (communicationType.equals(PacketUtils.FlowType.SINGLE_FILE)) {
                String mimeType = readStringFromStream(dataInputStream);

                progressData
                        .put("filename", filename)
                        .put("mimeType", mimeType);

                setProgressAsync(progressData.build());

                PendingIntent cancelIntent = WorkManager.getInstance(ctx).createCancelPendingIntent(getId());
                builder
                        .setContentTitle(ellipsize(filename, 25))
                        .addAction(0, ctx.getString(R.string.transfer_cancel), cancelIntent);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
                    builder.setSubText(ctx.getString(R.string.receiving_from, ellipsize(transmitterName, 20)));

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

                    progressData.putInt("percentage", percentage);

                    setProgressAsync(progressData.build());

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
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.setDataAndType(toSave, mimeType);

                PendingIntent pendingIntent = PendingIntent.getActivity(ctx, id, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                progressData.putString("fileURI", toSave.toString());

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
            }
            if (communicationType.equals(PacketUtils.FlowType.TEXT)) {
                progressData.putString("filename", filename);
            }

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
            return Result.failure(progressData.build());
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
            return Result.failure(progressData.build());
        }

        progressData
                .putInt("percentage", 100);

        return Result.success(progressData.build());
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static String readStringFromStream(DataInputStream stream) throws IOException {
        int l = stream.readInt();
        byte[] buf = new byte[l];
        stream.read(buf);

        return new String(buf);
    }
    private static @NonNull Pair<OutputStream, Uri> openOutputStreamForDownloadedFile(Context ctx, String filename, String mimetype) {
        try {
            Uri uri;
            OutputStream outputFile;

            // Android 9 and below
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                String s = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
                File f = new File(s, filename);
                uri = FileProvider.getUriForFile(ctx, "cocolorussococo.flyer.fileprovider", f);

                // uri = Uri.fromFile(f);
                //noinspection IOStreamConstructor
                outputFile = new FileOutputStream(f);
            }
            // Android 10 and above
            else {
                final ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                // If a mimetype is set to text/plain or application/octet-stream when it's actually known
                // (for example a .apk file with octet-stream) the file extension will be set wrong for multiple
                // copies of the files (e.g. file.apk -> file.apk (1)) making it unopenable without renaming.
                // To solve this, straight out avoid setting the mime type if it's a "default" mimetype.
                if (!mimetype.equals("text/plain") && !mimetype.equals("application/octet-stream"))
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);

                final Uri contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;

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
