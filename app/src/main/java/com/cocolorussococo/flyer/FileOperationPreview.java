package com.cocolorussococo.flyer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

public class FileOperationPreview {
    enum Mode {
        SENDER,
        RECEIVER
    }
    private final Activity context;

    private final View container;
    private final TextView filenameView;
    private final TextView statusView;
    private final ImageView iconView;
    private final ImageView fileIcon;
    private final CircularProgressIndicator progressBar;

    private boolean isSet = false;
    private final Mode mode;
    @SuppressLint("InflateParams")
    public FileOperationPreview(Activity ctx, Snackbar parent, Mode role) {
        context = ctx;

        container = context.getLayoutInflater().inflate(R.layout.operation_preview, null);
        parent.getView().setBackgroundColor(Color.TRANSPARENT);

        Snackbar.SnackbarLayout snackbarLayout = (Snackbar.SnackbarLayout) parent.getView();
        snackbarLayout.setPadding(0, 0, 0, 0);

        filenameView = container.findViewById(R.id.filenameView);
        statusView = container.findViewById(R.id.statusView);
        iconView = container.findViewById(R.id.statusIcon);
        fileIcon = container.findViewById(R.id.fileIcon);
        progressBar = container.findViewById(R.id.transferProgress);

        progressBar.setInterpolator(new DecelerateInterpolator());

        snackbarLayout.addView(container, 0);

        mode = role;

        switch (mode) {
            case SENDER:
                iconView.setImageResource(R.drawable.outline_file_upload_24);
                break;
            case RECEIVER:
                iconView.setImageResource(R.drawable.outline_file_download_24);
                break;
        }
    }

    public void setInfo(String _filename, String _mimetype, String _transmitter) {
        if (isSet) return;

        isSet = true;

        switch (mode) {
            case SENDER:
                statusView.setText(context.getString(R.string.sending_to, _transmitter));
                break;
            case RECEIVER:
                statusView.setText(context.getString(R.string.receiving_from, _transmitter));
                break;
        }

        filenameView.setText(_filename);
        if (_mimetype == null)
            fileIcon.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.round_text_fields_24));
        else
            fileIcon.setImageDrawable(FileMappings.getIconFromMimeType(context, _mimetype));
    }
    public boolean getSet() {
        return isSet;
    }

    public void updateProgress(int progress) {
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgressCompat(progress, true);
    }

    /**
     * Color and icon preset for a succeeded operation.
     * @param customMessage A resource string ID to be displayed in the status.
     */
    public void setSucceeded(@StringRes int customMessage) {
        progressBar.setProgressCompat(100, true);
        progressBar.setIndicatorColor(0xFF45CF40);

        iconView.setImageResource(R.drawable.round_check_24);
        iconView.setColorFilter(0xFF45CF40);
        statusView.setText(customMessage);
    }

    /**
     * Color and icon preset for a failed operation.
     */
    public void setFailed() {
        progressBar.setIndicatorColor(0xFFC92647);

        iconView.setImageResource(R.drawable.round_error_24);
        iconView.setColorFilter(0xFFC92647);
        statusView.setText(R.string.transfer_cancelled);
    }
    public void setUnsupported() {
        setFailed();

        filenameView.setText(R.string.not_supported_error);
        statusView.setText(R.string.update_warning);
    }

    public void setOnClick(View.OnClickListener onClick) {
        View clickableBackground = container.findViewById(R.id.clickableView);

        clickableBackground.setClickable(true);
        clickableBackground.setFocusable(true);
        clickableBackground.setOnClickListener(onClick);
    }
}
