package com.cam.final_demo.usbdevicelist;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.hardware.usb.*;
import android.util.Log;
import java.util.*;

public class UsbDeviceManager {
    private static final String TAG = "UsbDeviceManager";
    private static final String ACTION_USB_PERMISSION = "com.cam.final_demo.USB_PERMISSION";
    private static final String USB_PERMISSION = "USB_PERMISSION";


    private final Context context;
    private final UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbInterface usbInterface;
    private UsbEndpoint epIn, epOut;
    private UsbDevice connectedDevice;
    private boolean isConnected = false;

    private final PendingIntent permissionIntent;
    private UsbEventListener eventListener;

    private int sendTimeout = 2000;
    private int recvTimeout = 2000;

    @SuppressLint("InlinedApi")
    public UsbDeviceManager(Context ctx) {
        this.context = ctx;
        this.usbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        this.permissionIntent = PendingIntent.getBroadcast(ctx, 0,
                new Intent(USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);

        IntentFilter filter = new IntentFilter(USB_PERMISSION);
        ctx.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    public void setUsbEventListener(UsbEventListener listener) {
        this.eventListener = listener;
    }

    public List<UsbDevice> scanDevices() {
        List<UsbDevice> devices = new ArrayList<>(usbManager.getDeviceList().values());

        if (devices.isEmpty()) {
            Log.i(TAG, "No USB devices detected.");
            return devices;
        }

        for (UsbDevice d : devices) {
            int vid = d.getVendorId();
            int pid = d.getProductId();
            Log.i(TAG, "Detected USB device: " + d.getDeviceName() +
                    " VID=" + vid + " PID=" + pid);

            // ✅ Auto-request permission if not already granted
            if (!usbManager.hasPermission(d)) {
                Log.i(TAG, "Requesting permission for: " + d.getDeviceName());
                usbManager.requestPermission(d, permissionIntent);
            } else {
                Log.i(TAG, "Already have permission for: " + d.getDeviceName());
            }
        }

        return devices;
    }

    public List<UsbDevice> findDevicesByVendor(int vendorId) {
        List<UsbDevice> results = new ArrayList<>();
        for (UsbDevice d : usbManager.getDeviceList().values()) {
            if (d.getVendorId() == vendorId) results.add(d);
        }
        return results;
    }

    public boolean hasPermission(UsbDevice d) {
        return usbManager.hasPermission(d);
    }

    public void requestPermission(UsbDevice d) {
        usbManager.requestPermission(d, permissionIntent);
    }

    public void connect(UsbDevice d) {
        if (!hasPermission(d)) {
            requestPermission(d);
            return;
        }
        connection = usbManager.openDevice(d);
        if (connection == null) {
            Log.e(TAG, "openDevice failed");
            return;
        }

        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface iface = d.getInterface(i);
            UsbEndpoint tmpIn = null, tmpOut = null;
            for (int j = 0; j < iface.getEndpointCount(); j++) {
                UsbEndpoint ep = iface.getEndpoint(j);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                        ep.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC ) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN) tmpIn = ep;
                    else tmpOut = ep;
                }
            }
            if (tmpIn != null && tmpOut != null) {
                if (connection.claimInterface(iface, true)) {
                    usbInterface = iface;
                    epIn = tmpIn;
                    epOut = tmpOut;
                    connectedDevice = d;
                    isConnected = true;
                    if (eventListener != null) eventListener.onConnected(d);
                    return;
                }
            }
        }
    }

    public void disconnect() {
        if (connection != null) {
            if (usbInterface != null) connection.releaseInterface(usbInterface);
            connection.close();
        }
        if (eventListener != null && connectedDevice != null) eventListener.onDisconnected(connectedDevice);
        connectedDevice = null;
        usbInterface = null;
        epIn = null;
        epOut = null;
        isConnected = false;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public int send(byte[] data) {
        if (!isConnected || epOut == null) return -1;
        return connection.bulkTransfer(epOut, data, data.length, sendTimeout);
    }

    public byte[] receive(int maxLen) {
        if (!isConnected || epIn == null) return null;
        byte[] buf = new byte[maxLen];
        int len = connection.bulkTransfer(epIn, buf, maxLen, recvTimeout);
        if (len > 0) {
            return Arrays.copyOf(buf, len);
        }
        return null;
    }

//    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context ctx, Intent intent) {
//            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
//                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
//                    if (device != null) {
//                        Log.d(TAG, "USB permission granted for " + device.getDeviceName());
//                        connect(device); // now claim endpoints & notify listener
//                    }
//                } else {
//                    if (eventListener != null) {
//                        eventListener.onError("Permission denied for " + device);
//                    }
//                }
//            }
//        }
//    };

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.i(TAG, "✅ Permission granted for: " + device.getDeviceName());
                            // You can now open connection
                            connect(device);
                        }
                    } else {
                        Log.w(TAG, "❌ Permission denied for: " + device.getDeviceName());
                    }
                }
            }
        }
    };

    public void teardown() {
        disconnect();
        try {
            context.unregisterReceiver(usbReceiver);
        } catch (Exception ignored) {}
    }

    public interface UsbEventListener {
        void onConnected(UsbDevice d);
        void onDisconnected(UsbDevice d);
        void onError(String msg);
    }

    public void logConnectedDevices() {
        Map<String, UsbDevice> deviceMap = usbManager.getDeviceList();
        if (deviceMap.isEmpty()) {
            Log.i(TAG, "No USB devices connected.");
            return;
        }

        Log.i(TAG, "=== USB Devices Connected ===");
        for (UsbDevice device : deviceMap.values()) {
            String info = "DeviceName=" + device.getDeviceName()
                    + " | VID=" + device.getVendorId()
                    + " | PID=" + device.getProductId()
                    + " | Class=" + device.getDeviceClass()
                    + " | SubClass=" + device.getDeviceSubclass()
                    + " | Interfaces=" + device.getInterfaceCount();
            Log.i(TAG, info);

            // log interfaces & endpoints
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface intf = device.getInterface(i);
                Log.i(TAG, "  Interface[" + i + "] Class=" + intf.getInterfaceClass()
                        + " SubClass=" + intf.getInterfaceSubclass()
                        + " Endpoints=" + intf.getEndpointCount());
                for (int e = 0; e < intf.getEndpointCount(); e++) {
                    UsbEndpoint ep = intf.getEndpoint(e);
                    Log.i(TAG, "    Endpoint[" + e + "] Dir=" +
                            (ep.getDirection() == UsbConstants.USB_DIR_IN ? "IN" : "OUT") +
                            " Type=" + ep.getType() +
                            " Addr=" + ep.getAddress() +
                            " MaxPktSize=" + ep.getMaxPacketSize());
                }
            }
        }
        Log.i(TAG, "=============================");
    }
}
