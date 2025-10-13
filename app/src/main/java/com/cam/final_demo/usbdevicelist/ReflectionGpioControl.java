package com.cam.final_demo.usbdevicelist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

public class ReflectionGpioControl {

    private static final String TAG = "ReflectionGpioControl";
    private static final String SERVICE_NAME = "zysj";

    private Object mSystemService;
    private Method mSetGpioMethod;
    private Method mGetGpioMethod;
    private boolean mIsInitialized = false;

    public ReflectionGpioControl(Context context) {
        initializeService(context);
    }

    /**
     * Initialize the GPIO service using reflection
     */
    @SuppressLint("WrongConstant")
    private void initializeService(Context context) {
        try {
            // Step 1: Get the system service (returns Object, not specific type)
            mSystemService = context.getSystemService(SERVICE_NAME);

            if (mSystemService == null) {
                Log.e(TAG, "ZYSJ system service not available");
                return;
            }

            // Step 2: Get the class of the service object
            Class<?> serviceClass = mSystemService.getClass();
            Log.i(TAG, "Service class: " + serviceClass.getName());

            // Step 3: Find the methods we need using reflection
            // Method signature: set_zysj_gpio_value(int pin, int value)
            mSetGpioMethod = serviceClass.getMethod("set_zysj_gpio_value",
                    int.class, int.class);

            // Method signature: get_zysj_gpio_value(int pin)
            mGetGpioMethod = serviceClass.getMethod("get_zysj_gpio_value",
                    int.class);

            mIsInitialized = true;
            Log.i(TAG, "GPIO service initialized successfully using reflection");

        } catch (NoSuchMethodException e) {
            Log.e(TAG, "GPIO methods not found: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error initializing GPIO service: " + e.getMessage());
        }
    }

    /**
     * Set GPIO pin value using reflection
     *
     * @param pin   GPIO pin number (1-4)
     * @param value 0 for LOW, 1 for HIGH
     * @return Result code from system service
     */
    public int setGpioPin(int pin, int value) {
        if (!mIsInitialized) {
            Log.e(TAG, "Service not initialized");
            return -1;
        }

        try {
            // Invoke the method: mSystemService.set_zysj_gpio_value(pin, value)
            Object result = mSetGpioMethod.invoke(mSystemService, pin, value);

            int returnValue = (Integer) result;
            Log.d(TAG, String.format("Set GPIO pin %d to %d, result: %d",
                    pin, value, returnValue));

            return returnValue;

        } catch (Exception e) {
            Log.e(TAG, "Error setting GPIO pin " + pin + ": " + e.getMessage());
            return -1;
        }
    }

    /**
     * Get GPIO pin value using reflection
     *
     * @param pin GPIO pin number (1-4)
     * @return GPIO value (0 or 1), -1 on error
     */
    public int getGpioPin(int pin) {
        if (!mIsInitialized) {
            Log.e(TAG, "Service not initialized");
            return -1;
        }

        try {
            // Invoke the method: mSystemService.get_zysj_gpio_value(pin)
            Object result = mGetGpioMethod.invoke(mSystemService, pin);

            int value = (Integer) result;
            Log.d(TAG, String.format("GPIO pin %d value: %d", pin, value));

            return value;

        } catch (Exception e) {
            Log.e(TAG, "Error reading GPIO pin " + pin + ": " + e.getMessage());
            return -1;
        }
    }

    /**
     * Set GPIO pin 2 to HIGH
     */
    public boolean setPin2High() {
        int result = setGpioPin(2, 1);
        return result == 0; // Assuming 0 means success
    }

    /**
     * Set GPIO pin 2 to LOW
     */
    public boolean setPin2Low() {
        int result = setGpioPin(2, 0);
        return result == 0; // Assuming 0 means success
    }

    /**
     * Toggle GPIO pin 2
     */
    public void togglePin2() {
        int currentValue = getGpioPin(2);
        if (currentValue >= 0) {
            int newValue = (currentValue == 1) ? 0 : 1;
            setGpioPin(2, newValue);
            Log.i(TAG, "Pin 2 toggled from " + currentValue + " to " + newValue);
        }
    }

    /**
     * Set all GPIO pins 1-4 to specific values
     */
    public void setAllPins(int pin1, int pin2, int pin3, int pin4) {
        Log.i(TAG, "Setting all pins: " + pin1 + "," + pin2 + "," + pin3 + "," + pin4);

        setGpioPin(1, pin1);
        setGpioPin(2, pin2);
        setGpioPin(3, pin3);
        setGpioPin(4, pin4);
    }

    /**
     * Read all GPIO pins 1-4
     */
    public int[] getAllPins() {
        int[] values = new int[4];

        for (int i = 0; i < 4; i++) {
            values[i] = getGpioPin(i + 1);
        }

        Log.i(TAG, String.format("All pins: [%d,%d,%d,%d]",
                values[0], values[1], values[2], values[3]));

        return values;
    }

    /**
     * Check if the service is available and initialized
     */
    public boolean isServiceAvailable() {
        return mIsInitialized;
    }

    /**
     * Get information about available methods (for debugging)
     */
    public void printServiceInfo() {
        if (mSystemService == null) {
            Log.i(TAG, "Service not available");
            return;
        }

        Class<?> serviceClass = mSystemService.getClass();
        Log.i(TAG, "Service class: " + serviceClass.getName());

        // Print all available methods
        Method[] methods = serviceClass.getMethods();
        Log.i(TAG, "Available methods:");
        for (Method method : methods) {
            // Only show methods that might be GPIO-related
            String methodName = method.getName();
            Log.i(TAG, "  " + method.toString());
            if (methodName.contains("gpio") || methodName.contains("zysj") ||
                    methodName.contains("set") || methodName.contains("get")) {
                Log.i(TAG, "  " + method.toString());
            }
        }
    }
}

/**
 * Usage Example
 */
/*
 * public class MainActivity extends Activity {
 * private ReflectionGpioControl gpioControl;
 *
 * @Override
 * protected void onCreate(Bundle savedInstanceState) {
 * super.onCreate(savedInstanceState);
 *
 * // Initialize GPIO control with reflection
 * gpioControl = new ReflectionGpioControl(this);
 *
 * // Check if service is available
 * if (gpioControl.isServiceAvailable()) {
 * Log.i("MainActivity", "GPIO service is available");
 *
 * // Print service information for debugging
 * gpioControl.printServiceInfo();
 *
 * // Use GPIO control
 * useGpioControl();
 * } else {
 * Log.e("MainActivity", "GPIO service not available");
 * }
 * }
 *
 * private void useGpioControl() {
 * // Set GPIO pin 2 to HIGH
 * gpioControl.setPin2High();
 *
 * // Read current value of pin 2
 * int pin2Value = gpioControl.getGpioPin(2);
 * Log.i("MainActivity", "Pin 2 value: " + pin2Value);
 *
 * // Toggle pin 2
 * gpioControl.togglePin2();
 *
 * // Set all pins to specific pattern
 * gpioControl.setAllPins(1, 0, 1, 0); // HIGH, LOW, HIGH, LOW
 *
 * // Read all pin values
 * int[] allValues = gpioControl.getAllPins();
 *
 * // Set individual pins
 * gpioControl.setGpioPin(1, 1); // Pin 1 HIGH
 * gpioControl.setGpioPin(2, 0); // Pin 2 LOW
 * gpioControl.setGpioPin(3, 1); // Pin 3 HIGH
 * gpioControl.setGpioPin(4, 0); // Pin 4 LOW
 * }
 * }
 */

