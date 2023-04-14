package com.cocolorussococo.flyer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
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
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

public class FileUploadWorker extends Worker {
    private final int port;
    private final String target;
    private final Uri file;
    private final Context ctx;
    private final int id;
    private final boolean hasNotificationPermissions;
    final NotificationCompat.Builder builder;
    final NotificationManagerCompat notificationManager;
    boolean hasExceededQuota;
    ListenableFuture<Void> l;
    final long startTime;
    final String filename;
    Socket socket;
    final Data.Builder progressData;

    @Override
    public void onStopped() {
        try {
            if (socket != null)
                socket.close();
        } catch (IOException ignored) {}
    }

    @SuppressLint("MissingPermission")
    private void sendNotification() {
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
    public FileUploadWorker(@NonNull Context ctx, @NonNull WorkerParameters workerParams) {
        super(ctx, workerParams);

        Data data = workerParams.getInputData();
        target = data.getString("targetHost");
        port = data.getInt("port", 0);
        file = Uri.parse(data.getString("file"));
        id = workerParams.getId().hashCode();

        String receiverName = data.getString("hostname");

        this.ctx = ctx;
        hasNotificationPermissions = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) ||
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;


        filename = FileMappings.getFilenameFromURI(ctx, file);

        notificationManager = NotificationManagerCompat.from(ctx);

        // Create notification
        builder = new NotificationCompat.Builder(ctx, String.valueOf(42069))
                .setSilent(true)
                .setSmallIcon(R.drawable.outline_file_upload_24)
                .setContentTitle(filename)
                .setSubText(ctx.getString(R.string.sending_to, receiverName))
                .setContentText("0%")
                .setShowWhen(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, 0, false);

        // Display notification as soon as possible (call needed for Android >= 12)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        startTime = builder.getWhenIfShowing();
        sendNotification();

        progressData = new Data.Builder();
    }

    @SuppressLint("MissingPermission")
    @NonNull
    @Override
    public Result doWork() {
        try {
            socket = new Socket(target, port);
            // Reliability and throughput
            socket.setTrafficClass(0x04 | 0x08);
            InputStream fileStream = ctx.getContentResolver().openInputStream(file);

            int bytes;
            socket.setSoTimeout(5000);

            socket.setSendBufferSize(1024 * 1024);
            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

            PendingIntent cancelIntent = WorkManager.getInstance(ctx).createCancelPendingIntent(getId());
            builder
                    .setContentTitle(filename)
                    .addAction(0, ctx.getString(R.string.transfer_cancel), cancelIntent);

            // Write version
            dataOutputStream.writeByte(PacketUtils.FLOW_PROTOCOL_VERSION);
            // Write data type (0 for normal content)
            dataOutputStream.writeByte(0x00);

            // Write hostname
            writeStringToStream(dataOutputStream, Host.getHostname(ctx));
            // Write filename
            writeStringToStream(dataOutputStream, filename);
            // Write mimetype
            writeStringToStream(dataOutputStream, ctx.getContentResolver().getType(file));

            progressData
                    .putBoolean("started", true);

            setProgressAsync(progressData.build());

            final long total = FileMappings.getSizeFromURI(ctx, file);
            long written = 0;

            // Write content size
            dataOutputStream.writeLong(total);

            byte[] buffer = new byte[1024 * 1024];
            int prevPercentage = 0;
            long speedMillis = System.currentTimeMillis();
            long elapsedMillis = System.currentTimeMillis();
            long bytesConsumedInTime = 0;
            boolean shouldUpdateNotification = false;

            final DecimalFormat decimalFormat = new DecimalFormat("0.00");
            String speed = "0 MB/s";

            // Sending data on a socket doesn't implement a timeout, so we create one from scratch
            Watchdog watchdog = new Watchdog(5000, () -> {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            });

            while (true) {
                bytes = fileStream.read(buffer);
                if (bytes == -1) break;

                bytesConsumedInTime += bytes;

                dataOutputStream.write(buffer, 0, bytes);
                written += bytes;

                watchdog.resetTimer();

                final int percentage = (int) ((float) written * 100f / total);

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
            fileStream.close();
            dataOutputStream.close();

            watchdog.done();

            builder
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setContentText(ctx.getText(R.string.upload_complete))
                    .clearActions();

            if (hasNotificationPermissions)
                notificationManager.notify(id + 1, builder.build());

            progressData.putInt("percentage", 100);
            return Result.success(progressData.build());

        } catch (ConnectException e) {
            builder
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setContentText(ctx.getText(R.string.send_refused))
                    .clearActions();

            if (hasNotificationPermissions)
                notificationManager.notify(id + 1, builder.build());
        } catch (IOException e) {
            builder
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setContentText(ctx.getText(R.string.shared_fail))
                    .clearActions();

            // BrokenPipe and ConnectionClosed fall into the "Transfer cancelled" category
            if (e.getMessage() != null &&
                    (e.getMessage().contains("Broken pipe") || e.getMessage().contains("closed") || e.getMessage().contains("reset")))
                builder.setContentText(ctx.getText(R.string.transfer_cancelled));

            if (hasNotificationPermissions)
                notificationManager.notify(id + 1, builder.build());

            e.printStackTrace();
        }

        return Result.failure(progressData.build());
    }

    private static void writeStringToStream(DataOutputStream stream, String toWrite) throws IOException {
        byte[] buf = toWrite.getBytes();

        stream.writeByte(buf.length);
        stream.write(buf);
    }

    private static class Watchdog {
        private final Runnable runOnFail;
        private final int timeout;
        private Timer timer;

        public Watchdog(int timeout, Runnable runOnFail) {
            this.timeout = timeout;
            this.runOnFail = runOnFail;

            resetTimer();
        }
        public void resetTimer() {
            if (timer != null) timer.cancel();
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    runOnFail.run();
                }
            }, timeout);
        }
        public void done() {
            if (timer != null) timer.cancel();
        }
    }
}