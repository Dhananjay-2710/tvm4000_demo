package com.cam.final_demo.usbdevicelist;
import android.util.Log;

public class AlarmController {
    private static final String TAG = "AlarmController";
    private final GpioDeviceManager gpioManager;
    private final int sensorPin;
    private final int buzzerPin;
    private final int armPin;

    private Thread monitoringThread;
    private volatile boolean isRunning = false;

    public AlarmController(GpioDeviceManager manager, int sensorPin, int buzzerPin, int armPin) {
        this.gpioManager = manager;
        this.sensorPin = sensorPin;
        this.buzzerPin = buzzerPin;
        this.armPin = armPin;
    }

    public void start() {
        if (isRunning) return; // Already running
        isRunning = true;

        // Arm the system
        gpioManager.writeGpio(armPin, true);

        monitoringThread = new Thread(() -> {
            Log.d(TAG, "Alarm monitoring started.");
            while (isRunning) {
                String doorStatus = gpioManager.readGpio(sensorPin);

                if ("1".equals(doorStatus)) { // Assuming "1" means door is open
                    // Door is open, sound the alarm
                    gpioManager.writeGpio(buzzerPin, true);
                } else {
                    // Door is closed, silence the alarm
                    gpioManager.writeGpio(buzzerPin, false);
                }

                try {
                    Thread.sleep(100); // Check 10 times per second
                } catch (InterruptedException e) {
                    isRunning = false; // Stop if interrupted
                }
            }
            Log.d(TAG, "Alarm monitoring stopped.");

            // Disarm and turn off buzzer when stopped
            gpioManager.writeGpio(armPin, false);
            gpioManager.writeGpio(buzzerPin, false);
        });
        monitoringThread.start();
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        if (monitoringThread != null) {
            monitoringThread.interrupt();
        }
    }
}
