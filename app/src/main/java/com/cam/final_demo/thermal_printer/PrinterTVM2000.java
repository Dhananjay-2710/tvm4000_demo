package com.cam.final_demo.thermal_printer;
import static android.content.Context.USB_SERVICE;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;
import java.util.HashMap;
import java.util.Iterator;
import print.Print;


public class PrinterTVM2000 {

    private static UsbManager mUsbManager;
    private static UsbDevice device;
    private static PendingIntent mPermissionIntent = null;
    public static Print prnDeviceNew = null;
    private static final byte PARTIAL_CUT_FEED = 66;

    public boolean initUsbDevicesNew(Context thisCon) {

        mUsbManager = (UsbManager) thisCon.getSystemService(USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        boolean havePrinter = false;

        while (deviceIterator.hasNext()) {
            device = deviceIterator.next();
            int count = device.getInterfaceCount();

            for (int i = 0; i < count; i++) {
                Log.e("Printer", "inside device search loop");
                UsbInterface intf = device.getInterface(i);

                if (intf.getInterfaceClass() == 7) {
                    Log.d("Printer", "Interface printing");

                    havePrinter = true;

                    try {
                        if (Print.PortOpen(thisCon, device) != 0) {
                            havePrinter = false;
                            Log.d("Printer", "Device Not connected");
                        } else {
                            havePrinter = true;
                            Log.d("Printer", "Device connected");

                        }
                    } catch (Exception e) {
                        Log.e("Printer", e.toString());
                    }

                    if (mPermissionIntent != null) {
                        Log.d("PRINT_TAG", "vendorID--" + device.getVendorId() + " ProductId--" + device.getProductId());
                        mUsbManager.requestPermission(device, mPermissionIntent);
                    } else {
                        havePrinter = true;


                        try {
                            if (Print.PortOpen(thisCon, device) != 0) {
                                Log.d("Printer", "Device Not connected");
                                havePrinter = false;

                            } else {
                                havePrinter = true;
                                prnDeviceNew  =new Print();
                                Log.d("Printer", "Device connected");
                            }
                        } catch (Exception e) {
                            Log.e("Printer", e.toString());
                        }
                    }
                }
            }
        }

        if (!havePrinter) {
            Log.d("Printer", "Printer Not connected");
            prnDeviceNew = null;
        } else {
            try {
                Print.Initialize();
            } catch (Exception e) {
                e.printStackTrace();
            }
            prnDeviceNew = new Print();
        }

        return havePrinter;
    }

    public static void BeforePrintAction() {
        try {
            Print.LanguageEncode = "gb2312";
        } catch (Exception e) {
            Log.e("Print", "PublicAction --> BeforePrintAction " + e.getMessage(), e);
        }
    }

    public void AfterPrintAction() {
        // Empty in original Kotlin
    }

    public static Bitmap Tobitmap90(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.setRotate(90f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Bitmap Tobitmap(Bitmap bitmap, int width, int height) {
        Bitmap target = Bitmap.createBitmap(width, height, bitmap.getConfig());
        Canvas canvas = new Canvas(target);
        canvas.drawBitmap(bitmap, null, new Rect(0, 0, target.getWidth(), target.getHeight()), null);
        return target;
    }

    // width：目标宽度，pageWidthPoint：初始宽度，pageHeightPoint：初始高度
    public static int getHeight(int width, int pageWidthPoint, int pageHeightPoint) {
        double bili = (double) width / pageWidthPoint;
        return (int) (pageHeightPoint * bili);
    }
}
