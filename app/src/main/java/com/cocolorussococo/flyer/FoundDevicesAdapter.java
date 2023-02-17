package com.cocolorussococo.flyer;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class FoundDevicesAdapter extends RecyclerView.Adapter<FoundDevicesAdapter.ViewHolder> {
    private final ArrayList<Host> foundHosts;
    private final Context context;
    private Uri fileToSend;

    private final static int[] DEVICE_TYPES = {
            R.drawable.outline_smartphone_24,
            R.drawable.outline_tablet_24,
            R.drawable.baseline_windows_24
    };

    public FoundDevicesAdapter(Context ctx) {
        context = ctx;
        foundHosts = new ArrayList<>();
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
        Host host = foundHosts.get(position);
        holder.deviceName.setText(host.getName());
        holder.deviceIcon.setImageResource(DEVICE_TYPES[host.getType()]);
        holder.clickableLayout.setOnClickListener((v) -> {
            if (fileToSend == null) throw new IllegalStateException("By the time the user will be able to click buttons, Uri should have already been set.");
            UploadActivity.connect(context, fileToSend, foundHosts.get(position).getIp(), foundHosts.get(position).getPort());
        });
    }
    @Override
    public int getItemCount() {
        return foundHosts.size();
    }
    public void addDevice(Host found) {
        foundHosts.add(found);
        notifyItemInserted(foundHosts.size() - 1);
    }
    public void setFileToSend(Uri uri) {
        fileToSend = uri;
    }
    public boolean hasAlreadyBeenDiscovered(Host host) {
        return foundHosts.contains(host);
    }
    public void forgetDevices() {
        notifyItemRangeRemoved(0, foundHosts.size());
        foundHosts.clear();
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

        public TextView getDeviceName() {
            return deviceName;
        }

        public ImageView getDeviceIcon() {
            return deviceIcon;
        }

        public View getClickableLayout() {
            return clickableLayout;
        }
    }
}
