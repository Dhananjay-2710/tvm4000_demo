package com.cam.final_demo.usbdevicelist;

import android.annotation.SuppressLint;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.TypedValue;

import com.cam.final_demo.R;

import java.util.List;

public class DeviceListActivity extends AppCompatActivity {

    private static final String TAG = "DeviceListActivity";
    private UsbDeviceManager usbDeviceManager;
    private LinearLayout deviceContainer, gpioContainer, gpioChipContainer;
    private TextView tvLog;

    private static final int GPIO_DOOR_SENSOR = 121;  // The pin that changes when door opens
    private static final int GPIO_BUZZER_CONTROL = 122; // The pin that makes noise
    private static final int GPIO_ALARM_ENABLE = 120;

    private AlarmController alarmController;

//    private ReflectionGpioControl gpioControl;

    private ReflectionGpioControl gpioControl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        gpioControl = new ReflectionGpioControl(this);
        if (gpioControl.isServiceAvailable()) {
//            boolean exit =false;
//            do{
//                Log.i(TAG, "Pin 0 value: " + gpioControl.getGpioPin(0));
//                Log.i(TAG, "Pin 1 value: " + gpioControl.getGpioPin(1));
//                Log.i(TAG, "Pin 2 value: " + gpioControl.getGpioPin(2));
//                Log.i(TAG, "Pin 3 value: " + gpioControl.getGpioPin(3));
//                Log.i(TAG, "Pin 4 value: " + gpioControl.getGpioPin(4));
//                Log.i(TAG, "Pin 5 value: " + gpioControl.getGpioPin(5));
//                Log.i(TAG, "Pin 6 value: " + gpioControl.getGpioPin(6));
//                Log.i(TAG, "Pin 7 value: " + gpioControl.getGpioPin(7));
//                Log.i(TAG, "Pin 8 value: " + gpioControl.getGpioPin(8));
//                Log.i(TAG, "Pin 9 value: " + gpioControl.getGpioPin(9));
//                Log.i(TAG, "Pin 10 value: " + gpioControl.getGpioPin(10));
//                Log.i(TAG, "Pin 11 value: " + gpioControl.getGpioPin(11));
//
//                try {
//                    Thread.sleep(5000);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//
//            } while (!exit);

            Log.i(TAG, "GPIO service is available");
            gpioControl.printServiceInfo();
            // Helper function to bind switch with actual GPIO pin
            setupGpioSwitch(R.id.gpio1, 1);
            setupGpioSwitch(R.id.gpio2, 2);
            setupGpioSwitch(R.id.gpio3, 3);
            setupGpioSwitch(R.id.gpio4, 4);
//            setupGpioSwitch(R.id.gpio5, 5);
//            setupGpioSwitch(R.id.gpio6, 6);
//            setupGpioSwitch(R.id.gpio7, 7);
//            setupGpioSwitch(R.id.gpio8, 8);
//            setupGpioSwitch(R.id.gpio9, 9);
//            setupGpioSwitch(R.id.gpio10, 10);
//            setupGpioSwitch(R.id.gpio11, 11);
        } else {
            Log.e(TAG, "GPIO service not available");
        }

        gpioContainer = findViewById(R.id.gpio_container);
//        gpioChipContainer = findViewById(R.id.gpio_chip_container);
        deviceContainer = findViewById(R.id.device_container);
        Button btnRefresh = findViewById(R.id.btnRefreshGpio);

        GpioDeviceManager gpioManager = new GpioDeviceManager();
        alarmController = new AlarmController(gpioManager, GPIO_DOOR_SENSOR, GPIO_BUZZER_CONTROL, GPIO_ALARM_ENABLE);

        // Set a listener for the refresh button
        btnRefresh.setOnClickListener(v -> {
            Log.d("GPIO_DISCOVERY", "Refreshing GPIO list...");
            showGpioPins();
        });
        tvLog = findViewById(R.id.tv_log);


        usbDeviceManager = new UsbDeviceManager(this);
        usbDeviceManager.setUsbEventListener(new UsbDeviceManager.UsbEventListener() {
            @Override
            public void onConnected(UsbDevice d) {
                appendLog("Connected: " + formatDevice(d));
            }

            @Override
            public void onDisconnected(UsbDevice d) {
                appendLog("Disconnected: " + formatDevice(d));
            }

            @Override
            public void onError(String msg) {
                appendLog("Error: " + msg);
            }
        });

        showGpioPins();
        scanAndShowDevices();
    }

    private void setupGpioSwitch(int switchId, int pin) {
        SwitchCompat gpioSwitch = findViewById(switchId);

        // Initialize switch to reflect actual hardware state
        int currentValue = gpioControl.getGpioPin(pin);

        if (pin == 1 && currentValue == 0) {
            gpioControl.setGpioPin(1, 1);
            currentValue = gpioControl.getGpioPin(1);
        }

        gpioSwitch.setChecked(currentValue == 1);
        Log.d(TAG, "GPIO pin " + pin + " value: " + currentValue);

        // When user toggles switch
        gpioSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            gpioControl.setGpioPin(pin, isChecked ? 1 : 0);

            // Re-read actual hardware state
            int newValue = gpioControl.getGpioPin(pin);
            Log.d(TAG, "Set GPIO pin " + pin + " → Resulting value: " + newValue);

            // Force switch to follow real hardware
            gpioSwitch.setChecked(newValue == 1);
        });
    }

    @SuppressLint("SetTextI18n")
    private void showGpioPins() {
        GpioDeviceManager gpioManager = new GpioDeviceManager();
        List<GpioDeviceManager.GpioPinInfo> pins = gpioManager.listAllPins();

        gpioContainer.removeAllViews();

        for (GpioDeviceManager.GpioPinInfo pin : pins) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8, 8, 8, 8);

            TextView tv = new TextView(this);
            tv.setText("GPIO " + pin.number + " (" + pin.chipLabel + ") → " + pin.value);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button toggleBtn = new Button(this);
            Log.d("GPIO", "Value: " + pin.value);
            toggleBtn.setText(pin.value.equals("1") ? "Set LOW" : "Set HIGH");

            toggleBtn.setOnClickListener(v -> {
                boolean newVal = pin.value.equals("0");
                Log.d("GPIO", "Set Value" + pin.number + " to " + newVal);
                boolean ok = gpioManager.writeGpio(pin.number, newVal);
                if (ok) {
                    String newValue = newVal ? "1" : "0";
                    tv.setText("GPIO " + pin.number + " (" + pin.chipLabel + ") → " + newValue);
                    toggleBtn.setText(newVal ? "Set LOW" : "Set HIGH");
                    appendLog("GPIO " + pin.number + " set to " + (newVal ? "HIGH" : "LOW"));
                } else {
                    appendLog("Failed to set GPIO " + pin.number);
                }
            });

            row.addView(tv);
            row.addView(toggleBtn);
            gpioContainer.addView(row);
        }
    }

    @SuppressLint("SetTextI18n")
    private void scanAndShowDevices() {
        deviceContainer.removeAllViews();
        List<UsbDevice> devices = usbDeviceManager.scanDevices();
        if (devices == null || devices.isEmpty()) {
            appendLog("No USB devices found");
            TextView tv = new TextView(this);
            tv.setText("No USB devices found");
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            deviceContainer.addView(tv);
            return;
        }

        for (UsbDevice d : devices) {
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams wrapParams =
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wrapParams.setMargins(0, dp(6), 0, dp(6));
            wrapper.setLayoutParams(wrapParams);
            wrapper.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

            // Device name + VID/PID
            TextView t1 = new TextView(this);
            t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            t1.setText("Device: " + safeStr(d.getDeviceName()));

            TextView t2 = new TextView(this);
            t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
//            t2.setText("VID=0x" + String.format("%04X", d.getVendorId()) + "   PID=0x" + String.format("%04X", d.getProductId()));
            t2.setText("VID = " + d.getVendorId() + " \n PID = " + d.getProductId() + " \n Product Name = " + d.getProductName());

            // Health status (updated async)
            TextView t3 = new TextView(this);
            t3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t3.setText("Health: Checking...");

            // Connect button
            Button connectBtn = new Button(this);
            connectBtn.setText("Connect");
            connectBtn.setOnClickListener(v -> {
                appendLog("Connecting to " + d.getDeviceName());
                usbDeviceManager.connect(d);
                usbDeviceManager.logConnectedDevices();
                checkDeviceHealth(d, t3);
            });

            wrapper.addView(t1);
            wrapper.addView(t2);
            wrapper.addView(t3);
            wrapper.addView(connectBtn);
            deviceContainer.addView(wrapper);
            checkDeviceHealth(d, t3);
        }
    }

    /**
     * Runs the health-checker from DeviceRegistry on a background thread and writes the result
     * into the provided statusView (on UI thread).
     */
    @SuppressLint("SetTextI18n")
    private void checkDeviceHealth(UsbDevice device, TextView statusView) {
        // show checking immediately (UI thread)
        runOnUiThread(() -> statusView.setText("Health: Checking..."));

        new Thread(() -> {
            String health;
            try {
                health = DeviceRegistry.checkHealth(device);
            } catch (Throwable t) {
                health = "Health-check error: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            }
            final String finalHealth = health;
            runOnUiThread(() -> statusView.setText("Health: " + finalHealth));
        }).start();
    }

    private String formatDevice(UsbDevice d) {
        if (d == null) return "Unknown device";
        return "Device: " + d.getDeviceName() +
                " VID=" + d.getVendorId() +
                " PID=" + d.getProductId() +
                " Product Name=" + d.getProductName();
//        return "Device: " + d.getDeviceName() +
//                " VID=0x" + String.format("%04X", d.getVendorId()) +
//                " PID=0x" + String.format("%04X", d.getProductId());
    }

    private void appendLog(String msg) {
        tvLog.append(msg + "\n");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbDeviceManager != null) usbDeviceManager.teardown();
        if (alarmController != null) {
            alarmController.stop();
        }
    }

    // small helpers
    private String safeStr(String s) { return s == null ? "-" : s; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
