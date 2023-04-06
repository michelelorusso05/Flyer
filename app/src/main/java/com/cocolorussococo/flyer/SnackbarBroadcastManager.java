package com.cocolorussococo.flyer;

import java.util.UUID;

public class SnackbarBroadcastManager {
    UUID current;

    public SnackbarBroadcastManager() {}

    public void enqueue(UUID toEnqueue) {
        current = toEnqueue;
    }
    public boolean canShow(UUID toShow) {
        return (current == null || toShow == current);
    }
    public void yield(UUID owner) {
        if (current == owner)
            current = null;
    }
}
