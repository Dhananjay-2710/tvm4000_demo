package com.cam.final_demo.usbdevicelist;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Small wrapper that uses your UsbDeviceManager + DeviceRegistry to check
 * that a set of required vendor IDs (VIDs) are connected.
 */
public class UsbChecker {
    private static final String TAG = "UsbChecker";

    private final UsbDeviceManager usbDeviceManager;
    private final Context ctx;

    public UsbChecker(Context ctx) {
        this.ctx = ctx;
        this.usbDeviceManager = new UsbDeviceManager(ctx);
    }

    /**
     * Scans devices (will request permissions if needed) and returns connected devices.
     */
    public List<UsbDevice> scanDevices() {
        List<UsbDevice> devices = usbDeviceManager.scanDevices();
        usbDeviceManager.logConnectedDevices();
        return devices;
    }

    /**
     * Check whether ALL required VIDs are present among connected devices.
     * @param requiredVids integer VIDs (decimal or 0xHEX)
     */
    public boolean areRequiredVidsPresent(int[] requiredVids) {
        if (requiredVids == null || requiredVids.length == 0) return true;

        List<UsbDevice> devices = scanDevices();
        if (devices.isEmpty()) return false;

        Set<Integer> present = new HashSet<>();
        for (UsbDevice d : devices) present.add(d.getVendorId());

        for (int vid : requiredVids) {
            if (!present.contains(vid)) {
                Log.i(TAG, String.format("Missing VID: 0x%04X", vid));
                return false;
            }
        }
        return true;
    }

    /**
     * Returns list of missing VIDs (empty if none missing)
     */
    public List<Integer> missingVids(int[] requiredVids) {
        List<Integer> missing = new ArrayList<>();
        if (requiredVids == null || requiredVids.length == 0) return missing;

        List<UsbDevice> devices = scanDevices();
        Set<Integer> present = new HashSet<>();
        for (UsbDevice d : devices) present.add(d.getVendorId());

        for (int vid : requiredVids) {
            if (!present.contains(vid)) missing.add(vid);
        }
        return missing;
    }

    /**
     * Builds a human-friendly list of connected devices using DeviceRegistry health checks.
     */
    public String buildConnectedDevicesSummary() {
        List<UsbDevice> devices = scanDevices();
        if (devices.isEmpty()) return "No USB devices detected.";

        StringBuilder sb = new StringBuilder();
        for (UsbDevice d : devices) {
            sb.append("Name: ").append(d.getDeviceName())
                    .append(" | VID=").append(String.format("0x%04X", d.getVendorId()))
                    .append(" | PID=").append(String.format("0x%04X", d.getProductId()))
                    .append("\n")
                    .append("    -> ").append(DeviceRegistry.checkHealth(d))
                    .append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Call when the outer activity is destroyed to release resources.
     */
    public void teardown() {
        usbDeviceManager.teardown();
    }
}

