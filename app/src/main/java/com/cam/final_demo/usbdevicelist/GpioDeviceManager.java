package com.cam.final_demo.usbdevicelist;

import android.util.Log;
import java.io.*;
import java.util.*;

public class GpioDeviceManager {

    private static final String TAG = "GpioManager";
    private static final String GPIO_BASE_PATH = "/sys/class/gpio/";

    // Represents one gpiochip block from /sys/class/gpio
    public static class GpioChipInfoDetails {
        public final String name;
        public final int base;
        public final int ngpio;
        public final String label;

        public GpioChipInfoDetails(String name, int base, int ngpio, String label) {
            this.name = name;
            this.base = base;
            this.ngpio = ngpio;
            this.label = label;
        }
    }

    // Represents one individual GPIO pin
    public static class GpioPinInfo {
        public final int number;
        public final String chipLabel;
        public final String value; // "0", "1", or "N/A"

        public GpioPinInfo(int number, String chipLabel, String value) {
            this.number = number;
            this.chipLabel = chipLabel;
            this.value = value;
        }
    }

    /**
     * Executes a shell command with superuser (root) privileges.
     * @param command The command to execute.
     * @return true if the command exited with 0, false otherwise.
     */
    private boolean executeAsRoot(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            try (DataOutputStream os = new DataOutputStream(process.getOutputStream())) {
                os.writeBytes(command + "\n");
                os.flush();
                os.writeBytes("exit\n");
                os.flush();
                return process.waitFor() == 0;
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Root command failed: " + command, e);
            return false;
        }
    }

    /**
     * Lists all available gpiochip controllers. This does not require root.
     */
    public List<GpioChipInfoDetails> listAllGpioChips() {
        List<GpioChipInfoDetails> chips = new ArrayList<>();
        File baseDir = new File(GPIO_BASE_PATH);
        File[] files = baseDir.listFiles((dir, name) -> name.startsWith("gpiochip"));

        if (files == null) return chips;

        for (File f : files) {
            try {
                int base = Integer.parseInt(readFile(new File(f, "base")));
                int ngpio = Integer.parseInt(readFile(new File(f, "ngpio")));
                String label = readFile(new File(f, "label"));
                chips.add(new GpioChipInfoDetails(f.getName(), base, ngpio, label));
            } catch (Exception e) {
                Log.w(TAG, "Could not read info for " + f.getName(), e);
            }
        }
        return chips;
    }

    /**
     * Exports a GPIO pin to make it available for use. Requires root.
     */
    public boolean exportGpio(int gpioNumber) {
        File gpioPath = new File(GPIO_BASE_PATH + "gpio" + gpioNumber);
        if (gpioPath.exists()) {
            return true; // Already exported
        }
        return executeAsRoot("echo " + gpioNumber + " > " + GPIO_BASE_PATH + "export");
    }

    /**
     * Unexports a GPIO pin to release it. Requires root.
     */
    public boolean unexportGpio(int gpioNumber) {
        File gpioPath = new File(GPIO_BASE_PATH + "gpio" + gpioNumber);
        if (!gpioPath.exists()) {
            return true; // Already unexported
        }
        return executeAsRoot("echo " + gpioNumber + " > " + GPIO_BASE_PATH + "unexport");
    }

    /**
     * Sets the direction of a GPIO pin. Requires root.
     * @param direction Must be "in" or "out".
     */
    public boolean setDirection(int gpioNumber, String direction) {
        if (!"in".equals(direction) && !"out".equals(direction)) {
            Log.e(TAG, "Invalid GPIO direction: " + direction);
            return false;
        }
        return executeAsRoot("echo " + direction + " > " + GPIO_BASE_PATH + "gpio" + gpioNumber + "/direction");
    }

    /**
     * Writes a value to a GPIO pin. Requires root.
     * This method automatically handles exporting and setting direction.
     * @param high true for high (1), false for low (0).
     */
    public boolean writeGpio(int gpioNumber, boolean high) {
        if (!exportGpio(gpioNumber)) {
            Log.e(TAG, "Failed to export GPIO " + gpioNumber + " before writing.");
            return false;
        }

        // CRITICAL: Set direction to "out" before writing.
        if (!setDirection(gpioNumber, "out")) {
            Log.e(TAG, "Failed to set direction for GPIO " + gpioNumber);
            return false;
        }

        String value = high ? "1" : "0";
        return executeAsRoot("echo " + value + " > " + GPIO_BASE_PATH + "gpio" + gpioNumber + "/value");
    }

    /**
     * Reads the value of a given GPIO pin. Does not require root if permissions are correct.
     */
    public String readGpio(int gpioNumber) {
        File gpioFile = new File(GPIO_BASE_PATH + "gpio" + gpioNumber + "/value");
        return readFile(gpioFile);
    }

    /**
     * Scans all pins from all chips, tries to export & read them.
     */
    public List<GpioPinInfo> listAllPins() {
        List<GpioPinInfo> pins = new ArrayList<>();
        List<GpioChipInfoDetails> chips = listAllGpioChips();

        for (GpioChipInfoDetails chip : chips) {
            for (int i = chip.base; i < chip.base + chip.ngpio; i++) {
                String val = "N/A";
                // Only try to read if the pin directory already exists or can be created
                if (new File(GPIO_BASE_PATH + "gpio" + i).exists() || exportGpio(i)) {
                    val = readGpio(i);
                }
                pins.add(new GpioPinInfo(i, chip.label, val != null ? val : "N/A"));
            }
        }
        return pins;
    }

    /**
     * Utility method to read the first line from a file.
     */
    private String readFile(File f) {
        if (f == null || !f.exists()) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            return br.readLine().trim();
        } catch (IOException e) {
            return null;
        }
    }
}