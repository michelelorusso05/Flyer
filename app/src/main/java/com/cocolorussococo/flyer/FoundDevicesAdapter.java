package com.cocolorussococo.flyer;

import android.app.Activity;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class FoundDevicesAdapter extends RecyclerView.Adapter<FoundDevicesAdapter.ViewHolder> {
    private final ArrayList<Host> foundHosts;
    private final Activity context;
    private Timer cleanupTimer;
    private boolean isScheduled;
    private final onDeviceSelectedCallback callback;

    private final static int[] DEVICE_TYPES = {
            R.drawable.outline_smartphone_24,
            R.drawable.outline_tablet_24,
            R.drawable.baseline_windows_24,
            R.drawable.round_question_mark_24
    };

    public interface onDeviceSelectedCallback {
        void run(Host selected);
    }

    public FoundDevicesAdapter(Activity ctx, onDeviceSelectedCallback _callback) {
        context = ctx;
        foundHosts = new ArrayList<>();
        cleanupTimer = new Timer();

        callback = _callback;

        restart();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.target_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final int pos = holder.getAdapterPosition();
        Host host = foundHosts.get(pos);
        holder.deviceName.setText(host.getName());
        holder.deviceIcon.setImageResource(DEVICE_TYPES[host.getDeviceType().ordinal()]);
        holder.clickableLayout.setOnClickListener((v) -> callback.run(foundHosts.get(pos)));
    }
    @Override
    public int getItemCount() {
        return foundHosts.size();
    }
    public void addDevice(Host found) {
        int index = foundHosts.indexOf(found);
        // New host
        if (index == -1) {
            foundHosts.add(found);
            notifyItemInserted(foundHosts.size() - 1);
        }
        else {
            foundHosts.get(index).updatePort(found.getPort());
            foundHosts.get(index).setLastUpdated();
        }
    }
    public void forgetDevice(Host toForget) {
        int index = foundHosts.indexOf(toForget);
        if (index == -1) return;

        foundHosts.remove(index);
        notifyItemRemoved(index);
    }
    public boolean hasAlreadyBeenDiscovered(Host host) {
        return foundHosts.contains(host);
    }
    public void forgetDevices() {
        notifyItemRangeRemoved(0, foundHosts.size());
        foundHosts.clear();
    }
    public void cleanup() {
        if (!isScheduled) return;

        cleanupTimer.cancel();
        isScheduled = false;
    }
    public void restart() {
        if (isScheduled) return;
        isScheduled = true;

        cleanupTimer = new Timer();
        cleanupTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                for (int i = foundHosts.size() - 1; i >= 0; i--) {
                    Host h = foundHosts.get(i);
                    if (System.currentTimeMillis() - h.getLastUpdated() > 4000)
                        context.runOnUiThread(() -> forgetDevice(h));
                }
            }
        }, 0, 4000);
    }
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView deviceName;
        private final ImageView deviceIcon;
        private final View clickableLayout;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.deviceName);
            deviceIcon = itemView.findViewById(R.id.deviceType);
            clickableLayout = itemView.findViewById(R.id.clickable_layout);
        }
    }
}
