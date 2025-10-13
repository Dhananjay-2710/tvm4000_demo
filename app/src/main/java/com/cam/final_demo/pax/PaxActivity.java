package com.cam.final_demo.pax;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.cam.final_demo.R;
import com.pax.usbhost.UsbHost;
import com.pax.usbhost.exception.UsbHostException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PaxActivity extends AppCompatActivity {

    private static final String TAG = "PaxActivity";
    private static final String ACTION_USB_PERMISSION = "com.cam.final_demo.USB_PERMISSION";

    private UsbHost usbHost;
    private UsbManager usbManager;
    private PendingIntent permissionIntent;
    private BroadcastReceiver usbReceiver;

    private TextView tvLog, progressText;
    private Button btnScan, btnConnect, btnDisconnect, btnReconnect, btnExecute;
    private Spinner spinnerActions;
    private Future<?> saleFuture;
    private Future<?> balanceFuture;
    private FrameLayout progressOverlay;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pax);

        tvLog = findViewById(R.id.tv_pax_log);
        btnScan = findViewById(R.id.btn_scan);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        btnReconnect = findViewById(R.id.btn_reconnect);
        btnExecute = findViewById(R.id.btn_execute);
        spinnerActions = findViewById(R.id.spinner_actions);
        progressOverlay = findViewById(R.id.progressOverlay);
        progressText = findViewById(R.id.progressText);

        usbHost = new UsbHost(this);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);

        // Register broadcast for USB permission
        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        assert device != null;
                        appendLog("✅ Permission granted for " + device.getDeviceName());
                        actuallyConnect();
                    } else {
                        assert device != null;
                        appendLog("❌ Permission denied for " + device.getDeviceName());
                    }
                }
            }
        };
        registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED);

        // Dropdown actions
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"Sale JSON", "Check Balance", "Top Up", "Credit Debit"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerActions.setAdapter(adapter);

        btnScan.setOnClickListener(v -> scanDevices());
        btnConnect.setOnClickListener(v -> connectDevice());
        btnDisconnect.setOnClickListener(v -> disconnectDevice());
        btnReconnect.setOnClickListener(v -> reconnectDevice());
        btnExecute.setOnClickListener(v -> executeAction());
    }

//    private void scanDevices() {
//        var list = usbHost.getUsbDevList();
//        if (list.isEmpty()) {
//            appendLog("❌ No USB devices found");
//            return;
//        }
//        for (var devInfo : list) {
//            UsbDevice d = devInfo.getDevice();
//            appendLog("Found device: " + d.getDeviceName()
//                    + " VID=" + d.getVendorId()
//                    + " PID=" + d.getProductId()
//                    + " isPax=" + devInfo.isPaxDevice());
//        }
//    }

    private void scanDevices() {
        Map<String, UsbDevice> list = (Map<String, UsbDevice>) usbHost.getUsbDevList();
        if (list.isEmpty()) {
            appendLog("❌ No USB devices found");
            return;
        }
        for (Map.Entry<String, UsbDevice> entry : list.entrySet()) {
            UsbDevice d = entry.getValue();
            appendLog("Found device: " + d.getDeviceName()
                    + " VID=" + d.getVendorId()
                    + " PID=" + d.getProductId());
        }
    }


//    private void connectDevice() {
//        var list = usbHost.getUsbDevList();
//        if (list.isEmpty()) {
//            appendLog("❌ No PAX device found");
//            return;
//        }
//        UsbDevice d = list.get(0).getDevice();
//
//        if (!usbManager.hasPermission(d)) {
//            appendLog("⚠️ No permission for " + d.getDeviceName() + " → requesting...");
//            usbManager.requestPermission(d, permissionIntent);
//        } else {
//            actuallyConnect();
//        }
//    }
//
//    private void actuallyConnect() {
//        executorService.submit(() -> {
//            try {
//                var list = usbHost.getUsbDevList();
//                if (!list.isEmpty()) {
//                    for (int i = 0; i < list.get(0).getInterfaceList().size(); i++) {
//                        if (list.get(0).getInterfaceList().get(i).getInterfaceClass() == 255) {
//                            usbHost.setUsbDevTobeConnected(list.get(0), i);
//                            appendLog("✅ Using vendor-specific interface " + i);
//                            break;
//                        }
//                    }
//                }
//
//                usbHost.setConnectTimeout(5000);
//                usbHost.connect();
//                usbHost.setSendTimeout(5000);
//                usbHost.setRecvTimeout(20000);
//
//                runOnUiThread(() -> appendLog("✅ Connected to PAX successfully"));
//            } catch (UsbHostException e) {
//                runOnUiThread(() -> appendLog("❌ Connect failed: " + e.getErrCode()));
//            }
//        });
//    }

    private void connectDevice() {
        List<UsbHost.UsbDevInfo> list = usbHost.getUsbDevList();  // 👈 replace with actual type
        if (list.isEmpty()) {
            appendLog("❌ No PAX device found");
            return;
        }
        UsbDevice d = list.get(0).getDevice();

        if (!usbManager.hasPermission(d)) {
            appendLog("⚠️ No permission for " + d.getDeviceName() + " → requesting...");
            usbManager.requestPermission(d, permissionIntent);
        } else {
            actuallyConnect();
        }
    }

    private void actuallyConnect() {
        executorService.submit(() -> {
            try {
                List<UsbHost.UsbDevInfo> list = usbHost.getUsbDevList();  // 👈 replace with actual type
                if (!list.isEmpty()) {
                    for (int i = 0; i < list.get(0).getInterfaceList().size(); i++) {
                        if (list.get(0).getInterfaceList().get(i).getInterfaceClass() == 255) {
                            usbHost.setUsbDevTobeConnected(list.get(0), i);
                            appendLog("✅ Using vendor-specific interface " + i);
                            break;
                        }
                    }
                }

                usbHost.setConnectTimeout(5000);
                usbHost.connect();
                usbHost.setSendTimeout(5000);
                usbHost.setRecvTimeout(20000);

                runOnUiThread(() -> appendLog("✅ Connected to PAX successfully"));
            } catch (UsbHostException e) {
                runOnUiThread(() -> appendLog("❌ Connect failed: " + e.getErrCode()));
            }
        });
    }

    private void disconnectDevice() {
        usbHost.disconnect();
        appendLog("🔌 Disconnected from PAX");
    }

    private void reconnectDevice() {
        disconnectDevice();
        connectDevice();
    }

    private void executeAction() {
        String action = (String) spinnerActions.getSelectedItem();
        if ("Send JSON".equals(action)) {
            sendJson();
        } else if ("Sale JSON".equals(action)) {
            saleJson();
        } else if ("Check Balance".equals(action)){
            checkBalanceJson();
        } else if ("Top Up".equals(action)){
            topUpJson();
        } else if ("Credit Debit".equals(action)){
            creditDebitJson();
        }
    }

    @SuppressLint("SetTextI18n")
    private void checkBalanceJson() {
        if (balanceFuture != null && !balanceFuture.isDone()) {
            appendLog("⚠️ Previous Balance Enquiry task still running, cancelling...");
            balanceFuture.cancel(true);
        }

        Log.d(TAG, "Check Balance Card : " + timeStamp());

        balanceFuture = executorService.submit(() -> {
            try {
                Log.d(TAG, "Balance Future inside exectuor: " + timeStamp());
                runOnUiThread(this::showLoading);
                runOnUiThread(() -> progressText.setText("Click on the PAX Down side (Checking Balance)..."));

                // Unique Operator Order ID
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(new Date());
                int randomDigits = new Random().nextInt(9000) + 1000;
                String operatorOrderId = timestamp + randomDigits;

                Log.d(TAG, "Before Send Payload : " + timeStamp());
                // Build JSON payload for Balance Enquiry
                String jsonPayload = "{"
                        + "\"DATA\": {"
                        + "\"TRAN_TYPE\": \"BALANCE_ENQ\","
                        + "\"OPERATOR_ORDER_ID\": \"" + operatorOrderId + "\","
                        + "\"PRINT_FLAG\": \"1\","
                        + "\"STATION_ID\": \"1234\","
                        + "\"GATENO\": \"TOM12\","
                        + "\"STATION_NAME\": \"Rajiv Chowk\","
                        + "\"SHIFT_NO\": \"1\","
                        + "\"IS_OFFLINE\": \"0\""
                        + "}"
                        + "}";

                usbHost.send(jsonPayload.getBytes(StandardCharsets.UTF_8));

                Log.d(TAG, "After Send Payload : " + timeStamp());

                runOnUiThread(() -> appendLog("✅ Sent Balance Enquiry JSON: " + jsonPayload));

                Log.d(TAG, "Waiting for response Start : " + timeStamp());

                String resp = recvResponse();

                Log.d(TAG, "Response Received : " + timeStamp());

                runOnUiThread(() -> {
                    hideLoading(); // ✅ hide loader once done
                    if (resp != null) {
                        appendLog("📥 Balance response: " + resp);

                        try {
                            // Parse JSON
                            JSONObject root = new JSONObject(resp);
                            JSONObject responseObj = root.getJSONObject("RESPONSE");

                            String tranType = responseObj.optString("TRAN_TYPE", "");
                            String status = responseObj.optString("STATUS", "");
                            String uid = responseObj.optString("UID", "");
                            String balance = responseObj.optString("BALANCE", "");
                            String message = responseObj.optString("MESSAGE", "");

                            // Nicely formatted display
                            String formatted = "Transaction: " + tranType + "\n"
                                    + "Status: " + status + "\n"
                                    + "UID: " + uid + "\n"
                                    + "Balance: " + balance + "\n"
                                    + "Message: " + message;

                            showResponseDialog("Balance Response", formatted);

                        } catch (JSONException e) {
                            // Fallback if parsing fails
                            showResponseDialog("Balance Response", resp);
                        }

                    } else {
                        appendLog("⚠️ No balance response within 60s");
                        showResponseDialog("Timeout", "⚠️ No balance response within 60s");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideLoading();
                    appendLog("❌ Balance JSON failed: " + e.getMessage());
                });
            }

            Log.d(TAG, "Show Response on the Screen : " + timeStamp());
        });
    }

    @SuppressLint("SetTextI18n")
    private void topUpJson() {
        if (saleFuture != null && !saleFuture.isDone()) {
            appendLog("⚠️ Previous Top-Up task still running, cancelling...");
            saleFuture.cancel(true);
        }

        // Step 1: Ask user for amount
        runOnUiThread(() -> {
            final EditText input = new EditText(PaxActivity.this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setHint("Enter Top-up Amount");

            new AlertDialog.Builder(PaxActivity.this)
                    .setTitle("Top-Up")
                    .setMessage("Enter the amount to Top-up:")
                    .setView(input)
                    .setPositiveButton("Submit", (dialog, which) -> {
                        String userAmount = input.getText().toString().trim();

                        if (userAmount.isEmpty()) {
                            Toast.makeText(PaxActivity.this, "Amount is required", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Step 2: Run task in background
                        saleFuture = executorService.submit(() -> {
                            try {
                                runOnUiThread(PaxActivity.this::showLoading);
                                runOnUiThread(() -> progressText.setText("Click on the PAX Down side (Processing Top-Up)..."));

                                // Unique Operator Order ID
                                String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(new Date());
                                int randomDigits = new Random().nextInt(9000) + 1000;
                                String operatorOrderId = timestamp + randomDigits;

                                // Build JSON payload for Top-Up
                                String jsonPayload = "{"
                                        + "\"DATA\": {"
                                        + "\"AMOUNT\": \"" + userAmount + "\","
                                        + "\"TRAN_TYPE\": \"TOPUP\","
                                        + "\"OPERATOR_ORDER_ID\": \"" + operatorOrderId + "\","
                                        + "\"PRINT_FLAG\": \"1\","
                                        + "\"STATION_ID\": \"1234\","
                                        + "\"GATENO\": \"TOM12\","
                                        + "\"STATION_NAME\": \"Rajiv Chowk\","
                                        + "\"SHIFT_NO\": \"1\","
                                        + "\"IS_OFFLINE\": \"0\""
                                        + "}"
                                        + "}";

                                usbHost.send(jsonPayload.getBytes(StandardCharsets.UTF_8));
                                runOnUiThread(() -> appendLog("✅ Sent Top-Up JSON: " + jsonPayload));

                                // Step 3: Wait for response
                                String resp = recvResponse();

                                runOnUiThread(() -> {
                                    hideLoading(); // ✅ hide loader once done
                                    if (resp != null) {
                                        appendLog("📥 Top-Up response: " + resp);

                                        try {
                                            // Parse JSON
                                            JSONObject root = new JSONObject(resp);
                                            JSONObject responseObj = root.getJSONObject("RESPONSE");

                                            String tranType = responseObj.optString("TRAN_TYPE", "");
                                            String status = responseObj.optString("STATUS", "");
                                            String uid = responseObj.optString("UID", "");
                                            String balance = responseObj.optString("BALANCE", "");
                                            String message = responseObj.optString("MESSAGE", "");

                                            // Nicely formatted display
                                            String formatted = "Transaction: " + tranType + "\n"
                                                    + "Status: " + status + "\n"
                                                    + "UID: " + uid + "\n"
                                                    + "Updated Balance: " + balance + "\n"
                                                    + "Message: " + message;

                                            showResponseDialog("Top-Up Response", formatted);

                                        } catch (JSONException e) {
                                            showResponseDialog("Top-Up Response", resp);
                                        }

                                    } else {
                                        appendLog("⚠️ No Top-Up response within 60s");
                                        showResponseDialog("Timeout", "⚠️ No Top-Up response within 60s");
                                    }
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> {
                                    hideLoading();
                                    appendLog("❌ Top-Up JSON failed: " + e.getMessage());
                                });
                            }
                        });
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }


    @SuppressLint("SetTextI18n")
    private void creditDebitJson() {
        if (saleFuture != null && !saleFuture.isDone()) {
            appendLog("⚠️ Previous Top-Up task still running, cancelling...");
            saleFuture.cancel(true);
        }

        // Step 1: Ask user for amount
        runOnUiThread(() -> {
            final EditText input = new EditText(PaxActivity.this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setHint("Enter Top-up Amount");

            new AlertDialog.Builder(PaxActivity.this)
                    .setTitle("Credit/Debit Transaction")
                    .setMessage("Enter the amount to Top-up:")
                    .setView(input)
                    .setPositiveButton("Submit", (dialog, which) -> {
                        String userAmount = input.getText().toString().trim();

                        if (userAmount.isEmpty()) {
                            Toast.makeText(PaxActivity.this, "Amount is required", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Step 2: Run task in background
                        saleFuture = executorService.submit(() -> {
                            try {
                                runOnUiThread(PaxActivity.this::showLoading);
                                runOnUiThread(() -> progressText.setText("Click on the PAX Down side (Processing Credit/Debit)..."));

                                // Unique Operator Order ID
                                String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(new Date());
                                int randomDigits = new Random().nextInt(9000) + 1000;
                                String operatorOrderId = timestamp + randomDigits;

                                // Build JSON payload for Top-Up
                                String jsonPayload = "{"
                                        + "\"DATA\": {"
                                        + "\"AMOUNT\": \"" + userAmount + "\","
                                        + "\"TRAN_TYPE\": \"CREDIT_DEBIT\","
                                        + "\"OPERATOR_ORDER_ID\": \"" + operatorOrderId + "\","
                                        + "\"PRINT_FLAG\": \"1\","
                                        + "\"STATION_ID\": \"1234\","
                                        + "\"GATENO\": \"TOM12\","
                                        + "\"STATION_NAME\": \"Rajiv Chowk\","
                                        + "\"SHIFT_NO\": \"1\","
                                        + "\"IS_OFFLINE\": \"0\""
                                        + "}"
                                        + "}";

                                usbHost.send(jsonPayload.getBytes(StandardCharsets.UTF_8));
                                runOnUiThread(() -> appendLog("✅ Sent Top-Up JSON: " + jsonPayload));

                                // Step 3: Wait for response
                                String resp = recvResponse();

                                runOnUiThread(() -> {
                                    hideLoading(); // ✅ hide loader once done
                                    if (resp != null) {
                                        appendLog("📥 Credit Debit response: " + resp);

                                        try {
                                            // Parse JSON
                                            JSONObject root = new JSONObject(resp);
                                            JSONObject responseObj = root.getJSONObject("RESPONSE");

                                            String tranType = responseObj.optString("TRAN_TYPE", "");
                                            String status = responseObj.optString("STATUS", "");
                                            String uid = responseObj.optString("UID", "");
                                            String balance = responseObj.optString("BALANCE", "");
                                            String message = responseObj.optString("MESSAGE", "");

                                            // Nicely formatted display
                                            String formatted = "Transaction: " + tranType + "\n"
                                                    + "Status: " + status + "\n"
                                                    + "UID: " + uid + "\n"
                                                    + "Updated Balance: " + balance + "\n"
                                                    + "Message: " + message;

                                            showResponseDialog("Credit Debit Response", formatted);

                                        } catch (JSONException e) {
                                            showResponseDialog("Credit Debit Response", resp);
                                        }
                                    } else {
                                        appendLog("⚠️ No Credit Debit response within 60s");
                                        showResponseDialog("Timeout", "⚠️ No Top-Up response within 60s");
                                    }
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> {
                                    hideLoading();
                                    appendLog("❌ Credit Debit JSON failed: " + e.getMessage());
                                });
                            }
                        });
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }

    private void sendJson() {
        executorService.submit(() -> {
            try {
                String jsonPayload = "{"
                        + "\"DATA\": {"
                        + "\"AMOUNT\": \"11.11\","
                        + "\"TRAN_TYPE\": \"SALE\","
                        + "\"OPERATOR_ORDER_ID\": \"110924554444555445163823\","
                        + "\"PRINT_FLAG\": \"1\","
                        + "\"STATION_ID\": \"1234\","
                        + "\"GATENO\": \"TOM12\","
                        + "\"STATION_NAME\": \"Rajiv Chowk\","
                        + "\"SHIFT_NO\": \"1\","
                        + "\"IS_OFFLINE\": \"0\""
                        + "}"
                        + "}";

                usbHost.send(jsonPayload.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> appendLog("✅ Sent JSON: " + jsonPayload));

                String resp = recvResponse();
                if (resp != null) {
                    runOnUiThread(() -> {
                        appendLog("📥 Final response: " + resp);
                        showResponseDialog("Generic Response", resp);
                    });
                } else {
                    runOnUiThread(() -> {
                        appendLog("⚠️ No response within 60s");
                        showResponseDialog("Timeout", "⚠️ No response within 60s");
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> appendLog("❌ Send failed: " + e.getMessage()));
            }
        });
    }

    private void saleJson() {
        if (saleFuture != null && !saleFuture.isDone()) {
            appendLog("⚠️ Previous Sale task still running, cancelling...");
            saleFuture.cancel(true);
        }

        saleFuture = executorService.submit(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(new Date());
                int randomDigits = new Random().nextInt(9000) + 1000;
                String operatorOrderId = timestamp + randomDigits;

                double randomAmount = 10 + (490 * new Random().nextDouble());
                String amountStr = String.format(Locale.US, "%.2f", randomAmount);

                String jsonPayload = "{"
                        + "\"DATA\": {"
                        + "\"AMOUNT\": \"" + amountStr + "\","
                        + "\"TRAN_TYPE\": \"SALE\","
                        + "\"OPERATOR_ORDER_ID\": \"" + operatorOrderId + "\","
                        + "\"PRINT_FLAG\": \"1\","
                        + "\"STATION_ID\": \"1234\","
                        + "\"GATENO\": \"TOM12\","
                        + "\"STATION_NAME\": \"Rajiv Chowk\","
                        + "\"SHIFT_NO\": \"1\","
                        + "\"IS_OFFLINE\": \"0\""
                        + "}"
                        + "}";

                usbHost.send(jsonPayload.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> appendLog("✅ Sent Sale JSON: " + jsonPayload));

                String resp = recvResponse();
                if (resp != null) {
                    runOnUiThread(() -> {
                        appendLog("📥 Final response: " + resp);
                        showResponseDialog("Sale Response", resp);
                    });
                } else {
                    runOnUiThread(() -> {
                        appendLog("⚠️ No response within 60s");
                        showResponseDialog("Timeout", "⚠️ No response within 60s");
                    });
                }


            } catch (Exception e) {
                runOnUiThread(() -> appendLog("❌ Sale JSON failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Receive response from PAX with retry loop
     *
     * @return String response or null if none
     */
    private String recvResponse() {
        Log.d(TAG, "Inside Response : " + timeStamp());
        long start = System.currentTimeMillis();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try {
            usbHost.setRecvTimeout(800); // balanced timeout
        } catch (Exception e) {
            appendLog("⚠️ Failed to set recv timeout: " + e.getMessage());
        }

        int openBraces = 0;
        int closeBraces = 0;

        Log.d(TAG, "Before While : " + timeStamp());

        while (System.currentTimeMillis() - start < 60_000) {
            try {
                byte[] chunk = usbHost.recv(256);

                Log.d(TAG, "Chunk Recvied : " + timeStamp());

                if (chunk != null && chunk.length > 0) {
                    buffer.write(chunk);
                    String partial = buffer.toString(StandardCharsets.UTF_8.name());

                    // Count braces to detect JSON completeness
                    openBraces = partial.length() - partial.replace("{", "").length();
                    closeBraces = partial.length() - partial.replace("}", "").length();

                    appendLog("📥 RX Chunk (" + chunk.length + " bytes)");
                    Log.d(TAG, "📥 Data so far: " + partial);

                    if (openBraces > 0 && openBraces == closeBraces) {
                        try {
                            // Validate full JSON
                            new JSONObject(partial);
                            appendLog("✅ Full JSON received" + timeStamp());
                            return partial;
                        } catch (JSONException e) {
                            // If braces match but JSON invalid, continue reading
                        }
                    }
                }

                Log.d(TAG, "After Recv Data : " + timeStamp());

            } catch (Exception ignored) {
                // continue reading even on timeout
            }
        }

        // Timeout fallback
        byte[] finalData = buffer.toByteArray();
        if (finalData.length > 0) {
            String finalResp = new String(finalData, StandardCharsets.UTF_8);
            appendLog("⚠️ Timeout reached, partial data: " + finalResp);
            return finalResp;
        }

        appendLog("⚠️ No response received within 60s");
        return null;
    }

    private void showLoading() {
        runOnUiThread(() -> {
            progressOverlay.setVisibility(View.VISIBLE);
            btnExecute.setEnabled(false);
            spinnerActions.setEnabled(false);
            btnScan.setEnabled(false);
            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(false);
            btnReconnect.setEnabled(false);
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> {
            progressOverlay.setVisibility(View.GONE);
            btnExecute.setEnabled(true);
            spinnerActions.setEnabled(true);
            btnScan.setEnabled(true);
            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(true);
            btnReconnect.setEnabled(true);
        });
    }

    private void showResponseDialog(String title, String message) {
        runOnUiThread(() -> {
            new androidx.appcompat.app.AlertDialog.Builder(PaxActivity.this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private void appendLog(String msg) {
        runOnUiThread(() -> tvLog.append(msg + "\n"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        usbHost.disconnect();
        unregisterReceiver(usbReceiver);
        executorService.shutdownNow();
    }


    private String timeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}