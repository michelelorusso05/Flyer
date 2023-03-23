package com.cocolorussococo.flyer;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ReceiveTileService extends TileService {
    // Called when the user taps on your tile in an active or inactive state.
    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(getApplicationContext(), DownloadActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // TODO: When Android 14 releases, update with the new method
        startActivityAndCollapse(intent);
    }
}
