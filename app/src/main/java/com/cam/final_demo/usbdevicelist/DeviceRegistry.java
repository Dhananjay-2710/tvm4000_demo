package com.cam.final_demo.usbdevicelist;

import android.hardware.usb.UsbDevice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * DeviceRegistry: keeps mapping of known VIDs -> health checker and friendly name.
 * Add or update entries in the static block below.
 */
public class DeviceRegistry {

    private static final String TAG = "DeviceRegistry";

    public interface HealthChecker {
        String check(UsbDevice d);
    }

    // existing health checker registry (VID -> checker)
    private static final Map<Integer, HealthChecker> registry = new HashMap<>();

    // NEW: friendly names for known VIDs (VID -> short friendly name)
    private static final Map<Integer, String> friendlyNames = new HashMap<>();

    static {
        // Register health checkers (existing)
        register(8401, d -> "Thermal Printer detected (basic OK)");
        register(12216, d -> "PAX terminal detected (basic OK)");
        register(3913, d -> "Card Printer detected (basic OK)");
        register(8746, d -> "Monitor Screen detected (basic OK)");
        register(1027, d -> "Cash Acceptor detected (basic OK)");
        register(6428, d -> "Cash Acceptor detected (basic OK)");
        register(13030, d -> "USB Camera detected (basic OK)");
        register(11279, d -> "Fingerprint detected (basic OK)");

        // Populate friendly names (SHORT NAMES)
        friendlyNames.put(8401, "Thermal Printer");
        friendlyNames.put(12216, "PAX Terminal");
        friendlyNames.put(3913, "Card Printer");
        friendlyNames.put(8746, "Monitor Screen");
        friendlyNames.put(1027, "Cash Acceptor");
        friendlyNames.put(13030, "USB Camera");
        friendlyNames.put(11279, "Fingerprint");
    }

    public static void register(int vid, HealthChecker checker) {
        registry.put(vid, checker);
    }

    public static String checkHealth(UsbDevice d) {
        HealthChecker checker = registry.get(d.getVendorId());
        if (checker != null) {
            return checker.check(d);
        }
        return "Unknown device (VID=" + d.getVendorId() + ")";
    }

    /**
     * NEW: return the friendly short name for a VID, or a default if unknown.
     */
    public static String getFriendlyName(int vid) {
        String name = friendlyNames.get(vid);
        if (name != null) return name;
        // fall back to generic label
        return "VID " + String.format("0x%04X", vid);
    }

    /**
     * NEW: Format a list/array of required VIDs into a human readable string with names:
     * e.g. "Thermal Printer (0x20D1), Card Printer (0x0F49)"
     */
    public static String formatVidsWithNames(int[] vids) {
        if (vids == null || vids.length == 0) return "(none)";
        StringJoiner sj = new StringJoiner(", ");
        for (int vid : vids) {
            sj.add(getFriendlyName(vid) + " (" + String.format("0x%04X", vid) + ")");
        }
        return sj.toString();
    }

    /**
     * NEW: Format missing VIDs (List<Integer>) into friendly strings:
     * e.g. "Card Printer (0x0F49), USB Camera (0x32FE)"
     */
    public static String formatMissingVids(List<Integer> missing) {
        if (missing == null || missing.isEmpty()) return "(none)";
        StringJoiner sj = new StringJoiner(", ");
        for (Integer vid : missing) {
            sj.add(getFriendlyName(vid) + " (" + String.format("0x%04X", vid) + ")");
        }
        return sj.toString();
    }
}
