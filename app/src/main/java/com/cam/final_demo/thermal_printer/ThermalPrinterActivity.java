package com.cam.final_demo.thermal_printer;

import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import print.Print;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.print.PrintHelper;

import com.cam.final_demo.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThermalPrinterActivity extends AppCompatActivity {
    private static final String TAG = "ThermalPrinterActivity";
    private ImageView ivTicket;

    private static final int TARGET_VID = 8401;
    private Button btnPrintTicket;
    private Bitmap ticketBitmap;
    private boolean printerAvailable = false;


    private static final int PRINT_THREE_INCH = 576;
    private static final int BITMAP_BLACKW =0;

    private static final int PRINT_SUCCEED = 1;
    private static final int PRINT_FAILURE = 0;

    private static final String ENG_LANG = String.valueOf(1);
    private static final String LANGUAGE = ENG_LANG;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    PrinterTVM2000 thermalPrinter = new PrinterTVM2000();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thermal_printer);

        UsbDevice printerDevice = findThermalPrinterDevice();
        UsbManager usbManager = (UsbManager) getSystemService(USB_SERVICE);

        if (printerDevice == null) {
            Toast.makeText(this, "No Thermal Printer found (VID 8401)", Toast.LENGTH_LONG).show();
            return;
        }

        if (!usbManager.hasPermission(printerDevice)) {
            PendingIntent permissionIntent =
                    PendingIntent.getBroadcast(this, 0, new Intent("USB_PERMISSION"), PendingIntent.FLAG_IMMUTABLE);

            IntentFilter filter = new IntentFilter("USB_PERMISSION");
            registerReceiver(usbReceiver, filter);

            usbManager.requestPermission(printerDevice, permissionIntent);
        } else {
            // we already have permission, init immediately
            initializeThermalPrinter();
        }


        ivTicket = findViewById(R.id.ivTicket);
        btnPrintTicket = findViewById(R.id.btnPrintTicket);

        // disable print until we check printer
        btnPrintTicket.setEnabled(false);

        // 1) Load the image from assets
        loadAssetImage("ticketimg.jpeg");

        // 2) Background initialize the TVM printer (non-blocking UI)
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Checking printer...");
        pd.setCancelable(false);
        pd.show();

        bg.submit(() -> {
            try {
                boolean ok = thermalPrinter.initUsbDevicesNew(getApplicationContext());
                printerAvailable = ok;
                Log.i(TAG, "Printer init result: " + ok);
            } catch (Exception e) {
                Log.e(TAG, "Printer init error", e);
                printerAvailable = false;
            }

            // update UI on main thread
            runOnUiThread(() -> {
                pd.dismiss();
                if (printerAvailable) {
                    Toast.makeText(this, "Printer detected and ready", Toast.LENGTH_SHORT).show();
                    btnPrintTicket.setEnabled(true);
                } else {
                    Toast.makeText(this, "Printer not detected. You can use System Print as fallback.", Toast.LENGTH_LONG).show();
                    btnPrintTicket.setEnabled(true); // allow fallback printing
                }
            });
        });

        // 3) Print button click — use TVM printer if available, otherwise fallback
        btnPrintTicket.setOnClickListener(v -> {
            if (ticketBitmap == null) {
                Toast.makeText(this, "No image loaded", Toast.LENGTH_SHORT).show();
                return;
            }

            btnPrintTicket.setEnabled(false);
            Toast.makeText(this, "Printing...", Toast.LENGTH_SHORT).show();

            bg.submit(() -> {
                try {
                    if (printerAvailable) {
                        printImage(ticketBitmap, 2, PRINT_THREE_INCH, false, BITMAP_BLACKW, false);

                        try {
                            Log.d(TAG, "Language : " + LANGUAGE );
                            MediaPlayer mp = MediaPlayer.create(this, LANGUAGE.equals(ENG_LANG)?R.raw.eng_fold_msg:R.raw.kanna_fold_msg);
                            Log.d(TAG, "MediaPlayer : " + mp);
                            mp.start();
                            Log.d(TAG, "MediaPlayer Started : " + mp.getDuration());

                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mp.stop();
                                    mp.release();
                                }
                            }, mp.getDuration());
                        }catch(Exception e){
                            Log.d(TAG, "Error : " + e);
//                            Logger.error(e);
                        }

                    } else {
                        // Fallback: system print dialogue using PrintHelper
                        runOnUiThread(() -> printWithPrintHelper(ticketBitmap));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Print error", e);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                } finally {
                    runOnUiThread(() -> btnPrintTicket.setEnabled(true));
                }
            });
        });
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("USB_PERMISSION".equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.i(TAG, "USB Permission granted for → " + device.getDeviceName());
                    initializeThermalPrinter();
                } else {
                    Toast.makeText(context, "USB Permission DENIED", Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    private void initializeThermalPrinter() {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Initializing Thermal Printer...");
        pd.setCancelable(false);
        pd.show();

        bg.submit(() -> {
            boolean ok = false;
            try {
                ok = thermalPrinter.initUsbDevicesNew(getApplicationContext());
            } catch (Exception e) {
                Log.e(TAG, "Init failed", e);
            }

            boolean finalOk = ok;
            runOnUiThread(() -> {
                pd.dismiss();
                printerAvailable = finalOk;

                if (finalOk) {
                    Toast.makeText(this, "Printer READY", Toast.LENGTH_SHORT).show();
                    btnPrintTicket.setEnabled(true);
                } else {
                    Toast.makeText(this, "Thermal Printer init FAILED", Toast.LENGTH_LONG).show();
                }
            });
        });
    }


    public void printImage(final Bitmap bitmap, final int light, final int size, final boolean isRotate, final int sype, final boolean isLzo) {

        Log.d(TAG, "Inside printImage start");
        if (bitmap != null) {
            Log.d(TAG, "Initial Bitmap size: " + bitmap.getWidth() + " x " + bitmap.getHeight());
        } else {
            Log.d(TAG, "Initial Bitmap is null");
        }
        Log.d(TAG, "Parameters -> light: " + light
                + ", size: " + size
                + ", isRotate: " + isRotate
                + ", sype: " + sype
                + ", isLzo: " + isLzo);

        // Submit printing job to executorService
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Inside the executorService.execute block");

                    try {
                        if (thermalPrinter != null) {
                            thermalPrinter.BeforePrintAction();
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "BeforePrintAction threw", t);
                    }

                    // Prepare bitmap for printing
                    Bitmap bitmapPrint = bitmap;
                    if (bitmapPrint == null) {
                        Log.e(TAG, "bitmap is null, aborting print");
                        Log.e(TAG, "Printing Error " + PRINT_FAILURE);
                        return;
                    }

                    // Rotate if requested
                    if (isRotate) {
                        try {
                            bitmapPrint = thermalPrinter.Tobitmap90(bitmapPrint);
                        } catch (Throwable t) {
                            Log.w(TAG, "Rotation failed", t);
                        }
                    }

                    // Resize if requested
                    if (size != 0) {
                        try {
                            int newHeight = thermalPrinter.getHeight(size, bitmapPrint.getWidth(), bitmapPrint.getHeight());
                            bitmapPrint = thermalPrinter.Tobitmap(bitmapPrint, size, newHeight);
                        } catch (Throwable t) {
                            Log.w(TAG, "Resize failed", t);
                        }
                    }

                    // Do the actual print call, catching exceptions from the print lib
                    int printResult;
                    try {
                        Log.d(TAG, "Inside try block for Print");
                        if (!isLzo) {
                            printResult = Print.PrintBitmap(bitmapPrint, sype, light);
                        } else {
                            printResult = Print.PrintBitmapLZO(bitmapPrint, sype, light);
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Inside catch block for Print", e);
                        printResult = -1;
                        Log.d(TAG, "Print Result : " + printResult);
                        e.printStackTrace();
                    }

                    Log.d(TAG, "Print Result : " + printResult);

                    // Notify handler about print status (if you use handler)
                    try {
                        if (printResult >= 0) {
                            Log.d(TAG, "Print Success " + PRINT_SUCCEED);
                            handler.sendEmptyMessage(PRINT_SUCCEED);
                        } else {
                            Log.d(TAG, "Print Failure " + PRINT_FAILURE);
                            handler.sendEmptyMessage(PRINT_FAILURE);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Handler send failed", t);
                    }

                    // Recycle bitmaps safely (guard against double recycle)
                    try {
                        if (!bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Original bitmap recycle failed", t);
                    }

                    try {
                        if (!bitmapPrint.isRecycled()) {
                            bitmapPrint.recycle();
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "bitmapPrint recycle failed", t);
                    }

                    // Feed and cut paper (wrap in try/catch to avoid crashing)
                    try {
                        Print.PrintAndFeed(5);
                    } catch (Throwable t) {
                        Log.w(TAG, "PrintAndFeed failed", t);
                    }

                    try {
                        // Print.PARTIAL_CUT_FEED might be a byte/short - cast to int for the API
                        Print.CutPaper((int) Print.PARTIAL_CUT_FEED, 15);
                    } catch (Throwable t) {
                        Log.w(TAG, "CutPaper failed", t);
                    }

                    // After print action
                    try {
                        if (thermalPrinter != null) {
                            thermalPrinter.AfterPrintAction();
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "AfterPrintAction threw", t);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error in printing: " + e.getMessage(), e);
                    try {
                        Log.d(TAG, "Print Failure " + PRINT_FAILURE);
                        handler.sendEmptyMessage(PRINT_FAILURE);
                    } catch (Throwable t) {
                        Log.w(TAG, "Handler send failed in exception block", t);
                    }
                }
            }
        });
        Log.d(TAG, "Inside print image end");
    }

    private void loadAssetImage(String assetName) {
        try (InputStream is = getAssets().open(assetName)) {
            ticketBitmap = BitmapFactory.decodeStream(is);
            ivTicket.setImageBitmap(ticketBitmap);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load asset: " + assetName, Toast.LENGTH_LONG).show();
        }
    }

    // Fallback PrintHelper
    private void printWithPrintHelper(Bitmap bitmap) {
        try {
            PrintHelper printHelper = new PrintHelper(this);
            printHelper.setScaleMode(PrintHelper.SCALE_MODE_FIT);
            printHelper.printBitmap("Ticket Print", bitmap);
            Toast.makeText(this, "System print dialog opened.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "System print failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private UsbDevice findThermalPrinterDevice() {
        UsbManager usbManager = (UsbManager) getSystemService(USB_SERVICE);
        if (usbManager == null) return null;

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

        for (UsbDevice device : deviceList.values()) {
            int vid = device.getVendorId();
            int pid = device.getProductId();
            Log.i(TAG, "Found USB Device → VID=" + vid + " PID=" + pid);

            if (vid == TARGET_VID) {  // 8401
                Log.i(TAG, "🎯 Thermal printer matched → " + device.getDeviceName());
                return device;
            }
        }

        return null;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
    }
}
