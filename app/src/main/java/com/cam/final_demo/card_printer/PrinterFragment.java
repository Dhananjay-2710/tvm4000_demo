package com.cam.final_demo.card_printer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.jetbrains.annotations.Nullable;

import com.cam.final_demo.R;
import com.cam.final_demo.usbdevicelist.UsbDeviceManager;
import com.evolis.sdk.CardFace;
import com.evolis.sdk.CleaningInfo;
import com.evolis.sdk.Connection;
import com.evolis.sdk.Device;
import com.evolis.sdk.Evolis;
import com.evolis.sdk.Feeder;
import com.evolis.sdk.InputTray;
import com.evolis.sdk.MagSession;
import com.evolis.sdk.OutputTray;
import com.evolis.sdk.PrintSession;
import com.evolis.sdk.PrinterInfo;
import com.evolis.sdk.ReturnCode;
import com.evolis.sdk.RibbonInfo;
import com.evolis.sdk.State;
import com.evolis.sdk.Status;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class PrinterFragment extends Fragment {
    private static final String TAG = "PrinterFragment";
    private static final int TARGET_VID = 3913;
    private ProgressBar progressBar;
    private final Object deviceIoLock = new Object();
    private TextView tvStatus, tvReadiness, tvPrinterName, tvPrinterModel, tvPrinterSN;
    private TextView tvRibbonDesc, tvRibbonType, tvRibbonRemaining, tvCleanCount, tvCleanUnderWarranty, tvCleanRegular, tvCleanAdvanced, progressMessage;
    private RadioGroup rgFeeder;
    private String name, email, pan, currentFeederImagePath;
    private Button btnRefresh, btnPrint, btnEject, btnRead;
    private Connection connection = null;
    private Device currentDevice;
    private UsbDeviceManager usbDeviceManager;
    private View loadingOverlay;

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_printer, container, false);

        Bundle args = getArguments();
        if (args != null) {
            name = args.getString("name");
            email = args.getString("email");
            pan = args.getString("pan");
            Log.d(TAG, "name=" + name + " email=" + email + " pan=" + pan);
            String userPhotoBase64 = args.getString("user_photo");
            if (userPhotoBase64 != null) {
                byte[] decodedBytes = android.util.Base64.decode(userPhotoBase64, Base64.DEFAULT);
                Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                ImageView preview = v.findViewById(R.id.ivUserPhoto);
                preview.setImageBitmap(bitmap);
            }
        }
//        name = "Gilshan";

        // UI setup
        progressBar = v.findViewById(R.id.progress);
        loadingOverlay = v.findViewById(R.id.loadingOverlay);
        progressMessage = v.findViewById(R.id.progressMessage);
        tvStatus = v.findViewById(R.id.tvStatus);
        tvReadiness = v.findViewById(R.id.tvReadiness);
        tvPrinterName = v.findViewById(R.id.tvPrinterName);
        tvPrinterModel = v.findViewById(R.id.tvPrinterModel);
        tvPrinterSN = v.findViewById(R.id.tvPrinterSN);
        tvRibbonDesc = v.findViewById(R.id.tvRibbonDesc);
        tvRibbonType = v.findViewById(R.id.tvRibbonType);
        tvRibbonRemaining = v.findViewById(R.id.tvRibbonRemaining);
        tvCleanCount = v.findViewById(R.id.tvCleanCount);
        tvCleanUnderWarranty = v.findViewById(R.id.tvCleanUnderWarranty);
        tvCleanRegular = v.findViewById(R.id.tvCleanRegular);
        tvCleanAdvanced = v.findViewById(R.id.tvCleanAdvanced);

        rgFeeder = v.findViewById(R.id.rgFeeder);
        TextView tvFeederStatus = v.findViewById(R.id.tvFeederStatus);
        ImageView imgFeeder = v.findViewById(R.id.imgFeeder);

        ((RadioButton) v.findViewById(R.id.rbFeederD)).setChecked(true);
        tvFeederStatus.setText("Feeder selected: D");
        try {
            String imagePath = copyAssetToCacheNew("feeder_d.bmp");
            imgFeeder.setImageBitmap(BitmapFactory.decodeFile(imagePath));
        } catch (IOException e) {
            Log.e(TAG, "Failed to load default feeder image", e);
        }

        rgFeeder.setOnCheckedChangeListener((group, checkedId) -> {
            Feeder feeder = feederFromRadioId(checkedId);
            tvFeederStatus.setText("Setting feeder…");
            String assetName = "sample2.bmp";
            if (checkedId == R.id.rbFeederA) assetName = "feeder_a.bmp";
            else if (checkedId == R.id.rbFeederB) assetName = "feeder_b.bmp";
            else if (checkedId == R.id.rbFeederC) assetName = "feeder_c.bmp";
            else if (checkedId == R.id.rbFeederD) assetName = "feeder_d.bmp";

            final String finalAsset = assetName;
            final String nameToDraw = (name != null) ? name : "";

            new Thread(() -> {
                try {
                    // 1) copy asset
                    String originalPath = copyAssetToCacheNew(finalAsset);

                    // 2) decode
                    Bitmap original = BitmapFactory.decodeFile(originalPath);
                    if (original == null) {
                        try (InputStream is = requireContext().getAssets().open(finalAsset)) {
                            original = BitmapFactory.decodeStream(is);
                        }
                    }

                    // 3) draw name
                    Bitmap withText = addUserNameToBitmap(original, nameToDraw);

                    // 4) scale to card size
                    Bitmap scaled = Bitmap.createScaledBitmap(withText, 1012, 638, true);

                    // 5) save as BMP
                    String outFileName = "feeder_preview_" + System.currentTimeMillis() + ".bmp";
                    String savedPath = saveBitmapAsBmp(scaled, outFileName, requireContext());

                    requireActivity().runOnUiThread(() -> {
                        imgFeeder.setImageBitmap(BitmapFactory.decodeFile(savedPath));
                        currentFeederImagePath = savedPath;
                        tvFeederStatus.setText("Feeder selected: " + feeder.name());
                    });

                    // 6) Tell printer to switch feeder
                    setFeederForDevice(feeder, (ok, err) -> {
                        requireActivity().runOnUiThread(() -> {
                            if (ok) {
                                Toast.makeText(requireContext(),
                                        "Feeder set to " + feeder.name(),
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                tvFeederStatus.setText("Failed: " + (err != null ? err.getMessage() : "Unknown"));
                            }
                        });
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Feeder selection error", e);
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),
                                    "Feeder image error: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show()
                    );
                }
            }).start();
        });

        btnRefresh = v.findViewById(R.id.btnRefresh);
        btnPrint = v.findViewById(R.id.btnPrint);
        btnEject = v.findViewById(R.id.btnEject);
        btnRead = v.findViewById(R.id.btnRead);

        btnRefresh.setOnClickListener(v1 -> refreshPrinter());
        btnPrint.setOnClickListener(v1 -> printCard());
        btnEject.setOnClickListener(v1 -> ejectCard());
        btnRead.setOnClickListener(v1 -> readCard());

        connectPrinter();

        return v;
    }

    private void setLoading(boolean loading, String msg) {
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) {
                progressMessage.setText(msg);
            }
            tvStatus.setText(msg);
        });
    }

    private void connectPrinter() {
        Log.d(TAG, "Inside Connect Printer");
        setLoading(true, "Scanning for USB devices...");
        if (usbDeviceManager == null) {
            Log.d(TAG, "Init USB Device Manager");
            usbDeviceManager = new UsbDeviceManager(requireContext());
            usbDeviceManager.setUsbEventListener(new UsbDeviceManager.UsbEventListener() {
                @Override
                public void onConnected(UsbDevice d) {
                    Log.d(TAG, "USB device ready: " + d.getDeviceName());
                    setLoading(true, "Connecting to " + d.getProductName() + "...");

                    new Thread(() -> {
                        try {
                            Device[] sdkDevices = Evolis.getDevices();
                            if (sdkDevices == null || sdkDevices.length == 0) {
                                Log.w(TAG, "No Evolis printers found by SDK");
                                setLoading(false, "No Evolis printer found");
                                return;
                            }

                            int targetVID = d.getVendorId();
                            int targetPID = d.getProductId();

                            Device matchedDevice = null;
                            for (Device dev : sdkDevices) {
                                String uri = dev.getUri() != null ? dev.getUri().toLowerCase() : "";
                                String vidHex = String.format("%04x", targetVID).toLowerCase();
                                String pidHex = String.format("%04x", targetPID).toLowerCase();

                                Log.d(TAG, "SDK Device: " + dev.getDisplayName() + " uri=" + uri);

                                if (uri.contains(vidHex) && uri.contains(pidHex)) {
                                    matchedDevice = dev;
                                    break;
                                }
                            }

                            // fallback: pick first online device
                            if (matchedDevice == null) {
                                for (Device dev : sdkDevices) {
                                    if (dev.isOnline()) {
                                        matchedDevice = dev;
                                        Log.w(TAG, "Fallback: using first online SDK printer " + dev.getDisplayName());
                                        break;
                                    }
                                }
                            }

                            if (matchedDevice == null) {
                                Log.w(TAG, "No matching printer found for VID=" + targetVID + " PID=" + targetPID);
                                setLoading(false, "Unsupported USB device connected");
                                return;
                            }

                            currentDevice = matchedDevice;
                            connection = new Connection(currentDevice);
                            Log.i(TAG, "✅ Connected to Evolis printer: " + currentDevice.getDisplayName());

                            fetchPrinterDetails(connection);

                        } catch (Throwable t) {
                            Log.e(TAG, "Evolis connection failed", t);
                            setLoading(false, "Evolis connection failed: " + t.getMessage());
                        }
                    }).start();
                }

                @Override
                public void onDisconnected(UsbDevice d) {
                    setLoading(false, "Disconnected: " + d.getDeviceName());
                    if (connection != null) {
//                        try { connection.close(); } catch (Throwable ignored) {}
                        connection = null;
                        currentDevice = null;
                    }
                }

                @Override
                public void onError(String msg) {
                    setLoading(false, "USB Error: " + msg);
                }
            });
        }

        List<UsbDevice> devices = usbDeviceManager.scanDevices();
        if (!devices.isEmpty()) {
            Log.d(TAG, "Devices found: " + devices.size());

            UsbDevice targetPrinter = null;
            for (UsbDevice d : devices) {
                int vid = d.getVendorId();
                int pid = d.getProductId();
                Log.d(TAG, "Found device: " + d.getDeviceName() + " VID=" + vid + " PID=" + pid);

                // ✅ Check if VID matches your printer (3913)
                if (vid == 3913) {
                    targetPrinter = d;
                    Log.i(TAG, "🎯 Matched target printer: " + d.getDeviceName() +
                            " VID=" + vid + " PID=" + pid);
                    break;
                }
            }

            if (targetPrinter != null) {
                if (usbDeviceManager.hasPermission(targetPrinter)) {
                    usbDeviceManager.connect(targetPrinter); // go directly
                } else {
                    usbDeviceManager.requestPermission(targetPrinter); // will trigger broadcast → connect
                }
            } else {
                setLoading(false, "No matching printer found (VID=3913)");
                Log.w(TAG, "No matching printer found (VID=3913)");
            }

        } else {
            setLoading(false, "No USB devices found");
            Log.w(TAG, "No USB devices found");
        }
    }

    static class DeviceDetails {
        String displayName, name, uri, mark, model;
        boolean supervised, online;
        String printerName, printerModelName, printerSerial;
        Boolean duplex;
        String statusText;
        boolean feederEmpty, feederOpen, coverOpen;
        String majorState, minorState;
        boolean majorReady;
        String ribbonDesc, ribbonType;
        Integer ribbonRemaining, ribbonCapacity, ribbonProgress;
        Integer cleanCardsBeforeWarn, cleanRegularCount, cleanAdvancedCount;
        Boolean headUnderWarranty;
        Boolean canPrint;
        String readinessMsg;
    }

    @SuppressLint("SetTextI18n")
    private void fetchPrinterDetails(Connection connection) {
        setLoading(true, "🔄 Retrieving device details...");
        new Thread(() -> {
            try {
                DeviceDetails dd = new DeviceDetails();
                synchronized (deviceIoLock) {
                    // ---- PrinterInfo ----
                    PrinterInfo pi = connection.getInfo();
                    if (pi != null) {
                        dd.printerName = pi.getName();
                        dd.printerModelName = pi.getModelName();
                        dd.printerSerial = pi.getSerialNumber();
                        dd.duplex = pi.hasFlip();
                    }

                    // ---- Status ----
                    Status status = connection.getStatus();
                    if (status != null) {
                        dd.statusText = status.toString();
                        dd.feederEmpty = status.isOn(Status.Flag.WAR_FEEDER_EMPTY);
                        dd.feederOpen = status.isOn(Status.Flag.WAR_FEEDER_OPEN);
                        dd.coverOpen = status.isOn(Status.Flag.WAR_COVER_OPEN);
                    }

                    // ---- State ----
                    State state = connection.getState();
                    if (state != null) {
                        dd.majorState = String.valueOf(state.getMajorState());
                        dd.minorState = String.valueOf(state.getMinorState());
                        dd.majorReady = (state.getMajorState() == State.MajorState.READY);
                    }

                    // ---- Ribbon ----
                    RibbonInfo ri = connection.getRibbonInfo();
                    if (ri != null) {
                        dd.ribbonDesc = ri.getDescription();
                        dd.ribbonType = String.valueOf(ri.getType());
                        dd.ribbonRemaining = ri.getRemaining();
                        dd.ribbonCapacity = ri.getCapacity();
                        dd.ribbonProgress = ri.getProgress();
                    }

                    // ---- Cleaning ----
                    CleaningInfo ci = connection.getCleaningInfo();
                    if (ci != null) {
                        dd.cleanCardsBeforeWarn = ci.getCardCountBeforeWarning();
                        dd.headUnderWarranty = ci.isPrintHeadUnderWarranty();
                        dd.cleanRegularCount = ci.getRegularCleaningCount();
                        dd.cleanAdvancedCount = ci.getAdvancedCleaningCount();
                    }
                }

                // ---------- Readiness logic ----------
                // Feeder must NOT be empty
                boolean feederOk = !dd.feederEmpty;
                boolean feederOpenOk = !dd.feederOpen;
                boolean coverOk = !dd.coverOpen;
                boolean stateOk = dd.majorReady;
                dd.canPrint = feederOk && coverOk && stateOk && feederOpenOk;
                Log.d(TAG, "feederOpenOk=" + feederOpenOk + " feederOk=" + feederOk + " coverOk=" + coverOk + " stateOk=" + stateOk + " canPrint=" + dd.canPrint);
                if (!feederOk) {
                    dd.readinessMsg = "Feeder is empty.";
                } else if (!feederOpenOk) {
                    dd.readinessMsg = "Printer Feeder is open.";
                } else if (!coverOk) {
                    dd.readinessMsg = "Printer cover is open.";
                } else if (!stateOk) {
                    dd.readinessMsg = "Printer not ready (State: " + dd.majorState + ":" + dd.minorState + ")";
                } else {
                    dd.readinessMsg = "Ready to print.";
                }

                // ---- Update UI ----
                DeviceDetails finalDd = dd;
                requireActivity().runOnUiThread(() -> {
                    tvPrinterName.setText("Name: " + safe(finalDd.printerName));
                    tvPrinterModel.setText("Model: " + safe(finalDd.printerModelName));
                    tvPrinterSN.setText("Serial: " + safe(finalDd.printerSerial));

                    tvStatus.setText(finalDd.statusText != null ? finalDd.statusText : "-");
                    tvReadiness.setText(finalDd.readinessMsg);
                    tvReadiness.setBackgroundColor(getReadinessColor(finalDd));

                    if (finalDd.ribbonDesc != null) {
                        tvRibbonDesc.setText("Ribbon: " + finalDd.ribbonDesc);
                        tvRibbonType.setText("Type: " + finalDd.ribbonType);
                        tvRibbonRemaining.setText("Remaining: " +
                                finalDd.ribbonRemaining + "/" + finalDd.ribbonCapacity +
                                " (" + finalDd.ribbonProgress + "%)");
                    }

                    tvCleanCount.setText("Cards before warning: " + finalDd.cleanCardsBeforeWarn);
                    Boolean underWarranty = finalDd.headUnderWarranty;
                    tvCleanUnderWarranty.setText(
                            "Head warranty: " + (Boolean.TRUE.equals(underWarranty) ? "Yes" : "No")
                    );
//                    tvCleanUnderWarranty.setText("Head warranty: " + (finalDd.headUnderWarranty ? "Yes" : "No"));
                    tvCleanRegular.setText("Regular clean: " + finalDd.cleanRegularCount);
                    tvCleanAdvanced.setText("Advanced clean: " + finalDd.cleanAdvancedCount);

                    setLoading(false, "Printer details updated successfully");

                });

            } catch (Throwable t) {
                requireActivity().runOnUiThread(() ->
                        setLoading(false, "Unable to retrieve printer details: " + t.getMessage())
                );
            }
            requireActivity().runOnUiThread(() -> setLoading(false, ""));
        }).start();
    }

    private String safe(String... vals) {
        for (String v : vals) { if (v != null && !v.isEmpty()) return v; }
        return "-";
    }

    private void refreshPrinter() {
        setLoading(true, "🔄 Refreshing printer details...");

        if (usbDeviceManager == null) {
            usbDeviceManager = new UsbDeviceManager(requireContext());
            usbDeviceManager.setUsbEventListener(new UsbDeviceManager.UsbEventListener() {
                @Override
                public void onConnected(UsbDevice d) {
                    Log.d(TAG, "USB device connected: " + d.getDeviceName());
                }

                @Override
                public void onDisconnected(UsbDevice d) {
                    setLoading(false, "Disconnected: " + d.getDeviceName());
                    if (connection != null) {
                        try { connection.close(); } catch (Throwable ignored) {}
                        connection = null;
                        currentDevice = null;
                    }
                }

                @Override
                public void onError(String msg) {
                    setLoading(false, "USB Error: " + msg);
                }
            });
        }

        // 🔎 Find target printer
        UsbDevice target = null;
        List<UsbDevice> devices = usbDeviceManager.scanDevices();
        for (UsbDevice d : devices) {
            int vid = d.getVendorId();
            int pid = d.getProductId();
            Log.d(TAG, "Found device: " + d.getDeviceName() + " VID=" + vid + " PID=" + pid);

            if (vid == TARGET_VID) {
                target = d;
                break;
            }
        }

        if (target != null) {
            Log.i(TAG, "🎯 Target printer found: " + target.getDeviceName());

            if (usbDeviceManager.hasPermission(target)) {
                usbDeviceManager.connect(target);
            } else {
                usbDeviceManager.requestPermission(target); // connect will happen after broadcast
            }
        } else {
            setLoading(false, "Printer not connected (VID=" + TARGET_VID + ")");
            Log.w(TAG, "No matching printer found with VID=" + TARGET_VID);
        }
    }

    private void printCard() {
        setLoading(true, "🖨️ Starting card printing...");
        new Thread(() -> {
            String msg = "", cardReadmsg = "";
            try {
                Log.d(TAG, "=== Print Start ===");

                if (connection == null) {
                    throw new RuntimeException("No active printer connection");
                }

                // --- Status checks ---
                Status status = connection.getStatus();
                if (status == null)
                    throw new RuntimeException("getStatus() failed (code " + connection.getLastError() + ")");
                if (status.isOn(Status.Flag.WAR_FEEDER_EMPTY))
                    throw new IllegalStateException("⚠️ Feeder is empty.");
                if (status.isOn(Status.Flag.WAR_COVER_OPEN))
                    throw new IllegalStateException("⚠️ Printer cover is open.");

                // --- Printer ready state ---
                State st = connection.getState();
                if (st == null)
                    throw new RuntimeException("getState() failed (code " + connection.getLastError() + ")");
                if (st.getMajorState() != State.MajorState.READY)
                    throw new IllegalStateException("⚠️ Printer not READY (" + st.getMajorState() + "/" + st.getMinorState() + ")");

                // --- Apply feeder with retry ---
                int checkedId = rgFeeder.getCheckedRadioButtonId();
                Feeder feeder = feederFromRadioId(checkedId);
                Log.d(TAG, "Using feeder: " + feeder);
                if (!trySetFeederWithRetry(connection, feeder)) {
                    throw new RuntimeException("setFeeder failed, last error: " + connection.getLastError());
                }

                // --- Wait until printer READY after feeder selection ---
                int attempts = 0;
                do {
                    sleep(500);
                    st = connection.getState();
                    attempts++;
                } while (st != null && st.getMajorState() != State.MajorState.READY && attempts < 10);

                if (st == null || st.getMajorState() != State.MajorState.READY) {
                    throw new RuntimeException("Printer not READY after feeder switch (" +
                            (st != null ? st.getMajorState() : "null") + ")");
                }

                // --- Start session ---
                setLoading(true, "🖨️ Printing your card...");
                if (connection.isOpen()) {
                    PrintSession ps = new PrintSession(connection);

                    // Set card insertion mode :
                    connection.setInputTray(InputTray.FEEDER);

                    // Set card ejection mode :
                    connection.setOutputTray(OutputTray.STANDARD);

                    // Set card rejection mode (error cases) :
                    connection.setErrorTray(OutputTray.ERROR);

                    if (!ps.setAutoEject(false))
                        throw new RuntimeException("setAutoEject(false) failed");

                    // --- Select image ---
                    String imagePath = (currentFeederImagePath != null)
                            ? currentFeederImagePath
                            : requireContext().getCacheDir() + "/sample2.bmp";
                    if (!ps.setImage(CardFace.FRONT, imagePath))
                        throw new RuntimeException("Failed to set FRONT image");

                    // --- Print with retry ---
                    ReturnCode rc = tryPrintWithRetry(ps); // retry twice with delay

//                    if (rc == ReturnCode.PRINT_WAITCARDINSERT) {
//                        Log.d(TAG, "Waiting for card insertion...");
//                        if (!connection.insertCard()) {
//                            Log.d(TAG, "⚠️ Error during insertCard.");
//                            return;
//                        }
//                        Log.d(TAG, "Waiting printing done...");
//                        rc = ps.waitForPrintingDone();
//                    }
//                    if (rc == ReturnCode.PRINT_WAITCARDEJECT) {
//                        if (!connection.ejectCard()) {
//                            Log.d(TAG, "⚠️ Error during ejectCard.");
//                            return;
//                        }
//                        Log.d(TAG, "Waiting for card insertion...");
//                        rc = ps.waitForPrintingDone();
//                    }

                    if (rc != ReturnCode.OK) {
                        if (!connection.setBezelDelay(5)) {
                            Log.d(TAG, "⚠️ Error: setBezelDelay() failed with error: " + connection.getLastError());
                        }
                        throw new RuntimeException("Print failed (" + rc + ")");
                    }
                    setLoading(true, "✅ Card printing completed");

                    sleep(1000);

                    setLoading(true, "📖 Reading card data...");

                    // --- Read magstripe ---
                    cardReadmsg = tryReadMagstripe(connection);

                    // --- Eject card ---
                    setLoading(true, "⏏️ Ejecting card...");
                    if (!connection.ejectCard())
                        Log.w(TAG, "Card eject failed!");
                    else
                        Log.d(TAG, "Card ejected");

                    msg = "Card printed, ejected successfully and " + cardReadmsg;
                } else {
                    throw new RuntimeException("No active printer connection");
                }

            } catch (Throwable t) {
                msg = "❌ Card printing failed: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
                Log.e(TAG, "Print error", t);
            }

            final String finalMsg = msg;

            // Final user-facing instruction
            sleep(1000);
            setLoading(true, "🙏 Please collect your card. Thank you!");

            requireActivity().runOnUiThread(() -> setLoading(false, finalMsg));
        }).start();
    }

    private ReturnCode tryPrintWithRetry(PrintSession ps) {
        ReturnCode lastRc = ReturnCode.EUNDEFINED;

        for (int i = 0; i <= 2; i++) {
            ReturnCode rc = ps.print();
            lastRc = rc;

            if (rc == ReturnCode.OK) {
                return rc;
            }

            if (rc == ReturnCode.SESSION_EBUSY) {
                Log.w(TAG, "Printer busy (SESSION_EBUSY), waiting before retry...");
                sleep(1500 * 2);
                continue;
            }

            if (rc == ReturnCode.PRINTER_ENOCOM) {
                Log.w(TAG, "Printer offline (PRINTER_ENOCOM), retrying...");
                sleep(1500);
                continue;
            }

            Log.e(TAG, "Print failed permanently: " + rc);
            return rc;
        }

        return lastRc;
    }

    private String tryReadMagstripe(Connection co) {
        MagSession ms = new MagSession(co);
        ms.init();
        ReturnCode rcRead = ms.readTracks(true, true, false);
        if (rcRead == ReturnCode.OK)
            return "Read OK:\n- Track 1: " + ms.getData(0) + "\n- Track 2: " + ms.getData(1);
        rcRead = ms.readTracks(false, false, true);
        return (rcRead == ReturnCode.OK) ? "Read OK:\n- Track 3: " + ms.getData(2) : "Read failed: " + rcRead;
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private void ejectCard() {
        setLoading(true, "Ejecting...");
        new Thread(() -> {
            try {
                connection.ejectCard();
                setLoading(false, "Card Ejected");
            } catch (Throwable t) {
                setLoading(false, "Eject failed: " + t.getMessage());
            }
        }).start();
    }

    private void readCard() {
        setLoading(true, "🔄 Starting card reading...");
        new Thread(() -> {
            String msg = "";
            try {
                Log.d(TAG, "=== Card Read Start ===");
                if (connection == null) throw new RuntimeException("No active printer connection");

                // --- Status checks ---
                Status status = connection.getStatus();
                if (status == null)
                    throw new RuntimeException("getStatus() failed (code " + connection.getLastError() + ")");
                if (status.isOn(Status.Flag.WAR_FEEDER_EMPTY))
                    throw new IllegalStateException("⚠️ Feeder is empty.");
                if (status.isOn(Status.Flag.WAR_COVER_OPEN))
                    throw new IllegalStateException("⚠️ Printer cover is open.");

                // --- State must be READY ---
                State st = connection.getState();
                if (st == null)
                    throw new RuntimeException("getState() failed (code " + connection.getLastError() + ")");
                if (st.getMajorState() != State.MajorState.READY)
                    throw new IllegalStateException("⚠️ Printer not READY (" + st.getMajorState() + "/" + st.getMinorState() + ")");

                // --- Reading ---
                setLoading(true, "📖 Reading card data...");
                msg = tryReadMagstripeWithRetry(connection);

                setLoading(true, "✅ Card reading completed");
                sleep(1000);

                // --- Eject card ---
                setLoading(true, "⏏️ Ejecting card...");
                if (!connection.ejectCard())
                    Log.w(TAG, "Card eject failed!");
                else
                    Log.d(TAG, "Card ejected after read");

            } catch (Throwable t) {
                msg = "❌ Card reading failed: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
                Log.e(TAG, "Read error", t);
            }

            final String finalMsg = msg;

            // Final user-facing instruction
            sleep(1000);
            setLoading(true, "🙏 Please collect your card. Thank you!");

            requireActivity().runOnUiThread(() -> setLoading(false, finalMsg));
        }).start();
    }

    private String tryReadMagstripeWithRetry(Connection co) {
        String resultMsg = "";
        ReturnCode rc = ReturnCode.EUNDEFINED;

        for (int i = 0; i <= 2; i++) {
            MagSession ms = new MagSession(co);
            ms.init();

            rc = ms.readTracks(true, true, false);
            if (rc == ReturnCode.OK) {
                Log.d(TAG, "Card read success (Track 1 + 2)");
                resultMsg = "Read OK:\n- Track 1: " + ms.getData(0) + "\n- Track 2: " + ms.getData(1);
                return resultMsg;
            }

            if (rc == ReturnCode.SESSION_EBUSY) {
                Log.w(TAG, "SESSION_EBUSY: printer busy, retrying...");
                sleep(1000 * 2);
                continue;
            }

            if (rc == ReturnCode.PRINTER_ENOCOM) {
                Log.w(TAG, "PRINTER_ENOCOM: connection lost, retrying...");
                sleep(1000);
                continue;
            }

            // try Track 3 as fallback
            rc = ms.readTracks(false, false, true);
            if (rc == ReturnCode.OK) {
                Log.d(TAG, "Card read success (Track 3)");
                resultMsg = "Read OK:\n- Track 3: " + ms.getData(2);
                return resultMsg;
            }

            Log.w(TAG, "Read failed attempt " + (i + 1) + ": " + rc);
            sleep(1000);
        }

        return "Read failed: " + rc;
    }

    private Feeder feederFromRadioId(int id) {
        if (id == R.id.rbFeederA) return Feeder.A;
        if (id == R.id.rbFeederB) return Feeder.B;
        if (id == R.id.rbFeederC) return Feeder.C;
        if (id == R.id.rbFeederD) return Feeder.D;
        return Feeder.A;
    }

    private void setFeederForDevice(Feeder feeder, FeederCallback cb) {
        setLoading(true, "Feeder is Moving...");
        new Thread(() -> {
            boolean ok = false;
            Throwable err = null;

            try {
                if (connection == null) {
                    throw new RuntimeException("No active printer connection");
                }

                // --- Retry feeder setting ---
                ok = trySetFeederWithRetry(connection, feeder);
                if (!ok) {
                    err = new RuntimeException("setFeeder failed, last error code: " + connection.getLastError());
                } else {
                    // --- Wait until printer READY after feeder switch ---
                    State st;
                    int attempts = 0;
                    do {
                        sleep(500);
                        st = connection.getState();
                        attempts++;
                    } while (st != null && st.getMajorState() != State.MajorState.READY && attempts < 10);

                    if (st == null || st.getMajorState() != State.MajorState.READY) {
                        throw new RuntimeException("Printer not READY after feeder switch (" +
                                (st != null ? st.getMajorState() : "null") + ")");
                    }
                    refreshPrinter();
                }
            } catch (Throwable t) {
                err = t;
            }

            // --- Callback on UI thread ---
            Throwable finalErr = err;
            boolean finalOk = ok;
            requireActivity().runOnUiThread(() -> {
                cb.onResult(finalOk, finalErr);
                setLoading(false, "");
            });

        }).start();
    }

    private boolean trySetFeederWithRetry(Connection c, Feeder feeder) {
        boolean ok = false;
        for (int i = 0; i <= 3; i++) {
            ok = c.setFeeder(feeder);
            if (ok) return true;

            ReturnCode errCode = c.getLastError();
            Log.w(TAG, "setFeeder attempt " + (i+1) + " failed, error=" + errCode);

            if (errCode == ReturnCode.SESSION_EBUSY ||
                    errCode == ReturnCode.PRINTER_ENOCOM) {
                sleep(1000);
                continue;
            }
            break;
        }
        return ok;
    }

    interface FeederCallback {
        void onResult(boolean ok, Throwable error);
    }

    private String copyAssetToCacheNew(String assetName) throws IOException {
        File outFile = new File(getContext().getCacheDir(), assetName);
        try (InputStream in = getContext().getAssets().open(assetName);
             OutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        Log.d(TAG, "Image path: " + outFile.getAbsolutePath());
        return outFile.getAbsolutePath();
    }

    private Bitmap addUserNameToBitmap(Bitmap original, String userName) {
        if (original == null) return null;

        // Make mutable copy
        Bitmap mutable = original.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Style the text: auto-scale relative to image height
        float textSize = Math.max(18f, mutable.getHeight() * 0.06f);
        paint.setTextSize(textSize);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(2f, 1f, 1f, Color.WHITE);

        // compute vertical center for text
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        float x = 20f; // left padding
//        float y = (mutable.getHeight() / 2f) - ((fm.ascent + fm.descent) / 2f); // vertical center
        float y = mutable.getHeight() - 25f - fm.descent;

        // if username empty, optionally skip drawing
        if (userName != null && !userName.isEmpty()) {
            canvas.drawText(userName, x, y, paint);
        }

        return mutable;
    }

    public static String saveBitmapAsBmp(Bitmap bitmap, String fileName, Context context) throws IOException {
        File dir = new File(context.getCacheDir(), "printer");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            // Row padding: each row must be multiple of 4 bytes
            int rowBytes = (width * 3 + 3) & ~3;
            int imageSize = rowBytes * height;
            int fileSize = 54 + imageSize;

            // --- BMP Header ---
            byte[] header = new byte[54];
            header[0] = 'B';
            header[1] = 'M';
            header[2] = (byte) (fileSize);
            header[3] = (byte) (fileSize >> 8);
            header[4] = (byte) (fileSize >> 16);
            header[5] = (byte) (fileSize >> 24);
            header[10] = 54;  // offset
            header[14] = 40;  // DIB header size
            header[18] = (byte) (width);
            header[19] = (byte) (width >> 8);
            header[20] = (byte) (width >> 16);
            header[21] = (byte) (width >> 24);
            header[22] = (byte) (height);
            header[23] = (byte) (height >> 8);
            header[24] = (byte) (height >> 16);
            header[25] = (byte) (height >> 24);
            header[26] = 1;   // planes
            header[28] = 24;  // bits per pixel

            fos.write(header);

            // --- Pixel data (BGR order, bottom-up) ---
            byte[] row = new byte[rowBytes];
            for (int y = height - 1; y >= 0; y--) {
                int idx = 0;
                for (int x = 0; x < width; x++) {
                    int color = bitmap.getPixel(x, y);
                    row[idx++] = (byte) (color & 0xFF);        // Blue
                    row[idx++] = (byte) ((color >> 8) & 0xFF); // Green
                    row[idx++] = (byte) ((color >> 16) & 0xFF);// Red
                }
                while (idx < rowBytes) row[idx++] = 0;
                fos.write(row);
            }
        }
        return file.getAbsolutePath();
    }

    private int getReadinessColor(DeviceDetails d) {
        if (Boolean.TRUE.equals(d.canPrint)) {
            return 0xFF2E7D32; // green 600
        }
        if (Boolean.TRUE.equals(d.feederEmpty)) {
            return 0xFFB00020; // Material error red
        }
        if (Boolean.TRUE.equals(d.feederOpen)) {
            return 0xFFB00020; // Material error red
        }

        if (Boolean.TRUE.equals(d.coverOpen)) {
            return 0xFFB00020; // Material error red
        }
        if (!d.majorReady) {
            return 0xFFF57C00; // orange (not ready)
        }
        return 0xFF9E9E9E; // grey (unknown)
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (connection != null) {
            try { connection.close(); } catch (Throwable ignored) {}
            connection = null;
        }
    }
}
