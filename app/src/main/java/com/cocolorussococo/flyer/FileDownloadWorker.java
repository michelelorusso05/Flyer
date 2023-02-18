package com.cocolorussococo.flyer;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class FileDownloadWorker extends Worker {
    final Context ctx;

    public FileDownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        ctx = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        try (Socket socket = DownloadActivity.consumeSocket()) {
            OutputStream stream = ctx.getContentResolver().openOutputStream(Uri.parse(Environment.DIRECTORY_DOWNLOADS + File.separator + "Ciao.txt"));
            stream.write("Ciao".getBytes());
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Result.success();
    }
}
