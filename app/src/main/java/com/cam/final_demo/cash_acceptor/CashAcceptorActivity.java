package com.cam.final_demo.cash_acceptor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cam.final_demo.MainActivity;
import com.cam.final_demo.R;
import com.cam.final_demo.database.DatabaseConstants;
import com.cam.final_demo.database.MyDatabaseHelper;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import device.itl.sspcoms.DeviceEvent;
import device.itl.sspcoms.ItlCurrency;
import device.itl.sspcoms.PayoutRoute;
import device.itl.sspcoms.SSPDevice;
import device.itl.sspcoms.SSPDeviceType;
import device.itl.sspcoms.SSPPayoutEvent;
import device.itl.sspcoms.SSPSystem;
import device.itl.sspcoms.SSPUpdate;

public class CashAcceptorActivity extends AppCompatActivity {
    private static final int insertedTobeAmount = 150;
    private static int insertedAmount;
    private static int countFiftyNotes = 0 ;
    private static int countHundredNotes = 0;
    private static int countTwoHundredNotes = 0;
    private static int countFiveHundredNotes = 0;

    private boolean isProcessRunning = false;
    private final Handler inactivityHandler = new Handler(Looper.getMainLooper());
    private Runnable inactivityRunnable;
    private static final long TIMEOUT_DURATION = 60_000; // 1 min

    // UI elements
    @SuppressLint("StaticFieldLeak")
    private static TextView tvLog;
    @SuppressLint("StaticFieldLeak")
    private static TextView countFifty;
    @SuppressLint("StaticFieldLeak")
    private static TextView countHundred;
    @SuppressLint("StaticFieldLeak")
    private static TextView countTwoHundred;
    @SuppressLint("StaticFieldLeak")
    private static TextView countFiveHundred;
    @SuppressLint("StaticFieldLeak")
    private static TextView insertedAmountText;
    @SuppressLint("StaticFieldLeak")
    private static TextView insertedSelectedAmountText;
    private static SwitchCompat switchEscrow;
    private static final int MY_PERMISSIONS_REQUEST_READ_STORAGE = 0;
    private final int CASH_ACCEPTOR_VENDOR_ID = 12345;
    private static String m_DeviceCountry = null;
    private static SSPDevice sspDevice = null;
    static MyDatabaseHelper dbHelper;
    static ProgressDialog progress;

    private static final String cardNumber = "5070123456789012";

    @SuppressLint("StaticFieldLeak")
    static CashAcceptorActivity cashActivity;

    @SuppressLint("StaticFieldLeak")
    private static CashAcceptorActivity instance = null;

    private static AlertDialog activeEventDialog;
    private static final Handler dialogHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        progress = new ProgressDialog(this);
        dbHelper = new MyDatabaseHelper(this);
        dbHelper.addUser("Dhananjay Gahiwade");

        for (String user : dbHelper.getAllUsers()) {
            Log.d("DB_USER", user);
        }

        DeviceManager.initialize(this);
        if (DeviceManager.openDevice(this)) {
            Log.d("Info", "USB connection detected!");
            DeviceManager.setupDevice();
        } else {
            Log.d("Info", "No USB connection detected!");
        }

        cashActivity = this;
        instance = this;

        TextView txtConnect = findViewById(R.id.txtConnection);

        /* ask for permission to storage read  */
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE)) {

                txtConnect.setText(R.string.this_app_requires_access_to_the_downloads_directory_in_order_to_load_download_files);
                txtConnect.setVisibility(View.VISIBLE);

                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_STORAGE);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_STORAGE);
            }
        }

        setContentView(R.layout.activity_cash);

        // 2. Init UI
        initUi();

        String currentTime = getCurrentTimestamp();

        String cardType = "NCMC";
        long id = dbHelper.insertTopUpDetails(
                cardNumber, cardType, currentTime,
                insertedTobeAmount, 0, 0, 0, 0,
                "Pending", currentTime, currentTime
        );

        //Shared Preference
        SharedPreferences prefs = getSharedPreferences("CashPrefs", MODE_PRIVATE);
        int lastNote = prefs.getInt("last_note", 0);
        String lastTime = prefs.getString("inserted_time", "N/A");

        Log.d("CashRecovery", "Last inserted note: ₹" + lastNote + " at " + lastTime);

    }

    private static String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private void initUi() {
        TextView txtConnection = findViewById(R.id.txtConnection);
        insertedAmountText = findViewById(R.id.insertedAmountText);
        insertedSelectedAmountText = findViewById(R.id.insertedSelectedAmount);
        countFifty = findViewById(R.id.countFifty);
        countHundred = findViewById(R.id.countHundred);
        countTwoHundred = findViewById(R.id.countTwoHundred);
        countFiveHundred = findViewById(R.id.countFiveHundred);

        SwitchCompat switchDevice = findViewById(R.id.switchDevice);
        switchDevice.setChecked(true);
        switchDevice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
            if (deviceCom != null) {
                deviceCom.SetDeviceEnable(isChecked);
            } else {
                Log.d("DeviceManager", "deviceCom is null. Call DeviceManager.initialize(context) first.");
            }
            Log.d("Info", "Device " + (isChecked ? "Enabled" : "Disabled"));
        });

        switchEscrow = findViewById(R.id.switchEscrow);
        switchEscrow.setChecked(true);
        switchEscrow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
            if (deviceCom != null) {
                deviceCom.SetEscrowMode(isChecked);
            } else {
                Log.d("DeviceManager", "deviceCom is null. Call DeviceManager.initialize(context) first.");
            }
            Log.d("Info", "Escrow " + (isChecked ? "Enabled" : "Disabled") + ".");
        });

        Button buttonDisconnect = findViewById(R.id.buttonDisconnect);
        buttonDisconnect.setOnClickListener(v -> {
            ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
            if (deviceCom != null) {
                deviceCom.EmptyPayout();
            } else {
                Log.d("Info", "deviceCom is null. Call DeviceManager.initialize(context) first.");
            }
            clearDisplay(cashActivity, CashAcceptorActivity.cashActivity);
        });

        // Debug views
//        tvLog = findViewById(R.id.tv_cash_log);
//        btnScan = findViewById(R.id.btn_cash_scan);
//        btnConnect = findViewById(R.id.btn_cash_connect);
//        btnDisconnect = findViewById(R.id.btn_cash_disconnect);
//        btnReconnect = findViewById(R.id.btn_cash_reconnect);
    }

    @SuppressLint("SetTextI18n")
    public static void showAlertDialogForInsertAmount(Context context, double insertedFoundAmount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_note_inserted, null);
        builder.setView(dialogView);
        TextView textInsertedAmount = dialogView.findViewById(R.id.textMessage);
        textInsertedAmount.setText("Inserted amount: ₹" + insertedFoundAmount);

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawableResource(R.drawable.dialog_background);
        dialog.show();

        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnContinue = dialogView.findViewById(R.id.btnContinue);

        View.OnClickListener updateCashLogic = v -> {
            ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
            Log.d("Info", "Inserted amount: " + insertedFoundAmount + " / " + insertedTobeAmount + ".");
            if ((insertedAmount + insertedFoundAmount) > insertedTobeAmount) {
                Log.d("Info", "Extra amount inserted from if");
                showEventDialog(CashAcceptorActivity.cashActivity, "Insert Extra Amount", "Please Collect your Extra Amount", false, 3000);
                deviceCom.SetEscrowAction(SSPSystem.BillAction.Reject);
            } else if (insertedAmount < insertedTobeAmount) {

                if (insertedFoundAmount == 50) {
                    countFiftyNotes++;
                    ContentValues values = new ContentValues();
                    values.put(DatabaseConstants.COLUMN_FIFTY_NOTE, countFiftyNotes);
                    int rows = dbHelper.updateTopUpDetails(
                            values,
                            "CardNumber = ?",
                            new String[]{cardNumber}
                    );
                    if (rows > 0) {
                        Log.d("DB_UPDATE", "Update successful. Rows affected: " + rows);
                        Toast.makeText(context, "Top-up record updated successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w("DB_UPDATE", "No rows updated. Possibly no matching record.");
                        Toast.makeText(context, "No matching record found to update.", Toast.LENGTH_SHORT).show();
                    }

                    //Shared Preference
                    SharedPreferences prefs = context.getSharedPreferences("CashPrefs", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("last_note", (int) insertedFoundAmount);
                    editor.putString("inserted_time", getCurrentTimestamp());
                    editor.apply();

                } else if (insertedFoundAmount == 100) {
                    countHundredNotes++;
                    ContentValues values = new ContentValues();
                    values.put(DatabaseConstants.COLUMN_HUNDRED_NOTE, countHundredNotes);
                    int rows = dbHelper.updateTopUpDetails(
                            values,
                            "CardNumber = ?",
                            new String[]{cardNumber}
                    );
                    if (rows > 0) {
                        Log.d("DB_UPDATE", "Update successful. Rows affected: " + rows);
                        Toast.makeText(context, "Top-up record updated successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w("DB_UPDATE", "No rows updated. Possibly no matching record.");
                        Toast.makeText(context, "No matching record found to update.", Toast.LENGTH_SHORT).show();
                    }

                    //Shared Preference
                    SharedPreferences prefs = context.getSharedPreferences("CashPrefs", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("last_note", (int) insertedFoundAmount);
                    editor.putString("inserted_time", getCurrentTimestamp());
                    editor.apply();

                } else if (insertedFoundAmount == 200) {
                    countTwoHundredNotes++;
                    ContentValues values = new ContentValues();
                    values.put(DatabaseConstants.COLUMN_TWO_HUNDRED_NOTE, countTwoHundredNotes);
                    int rows = dbHelper.updateTopUpDetails(
                            values,
                            "CardNumber = ?",
                            new String[]{cardNumber}
                    );
                    if (rows > 0) {
                        Log.d("DB_UPDATE", "Update successful. Rows affected: " + rows);
                        Toast.makeText(context, "Top-up record updated successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w("DB_UPDATE", "No rows updated. Possibly no matching record.");
                        Toast.makeText(context, "No matching record found to update.", Toast.LENGTH_SHORT).show();
                    }
                    //Shared Preference
                    SharedPreferences prefs = context.getSharedPreferences("CashPrefs", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("last_note", (int) insertedFoundAmount);
                    editor.putString("inserted_time", getCurrentTimestamp());
                    editor.apply();

                } else if (insertedFoundAmount == 500) {
                    countFiveHundredNotes++;
                    ContentValues values = new ContentValues();
                    values.put(DatabaseConstants.COLUMN_FIVE_HUNDRED_NOTE, countFiveHundredNotes);
                    int rows = dbHelper.updateTopUpDetails(
                            values,
                            "CardNumber = ?",
                            new String[]{cardNumber}
                    );
                    if (rows > 0) {
                        Log.d("DB_UPDATE", "Update successful. Rows affected: " + rows);
                        Toast.makeText(context, "Top-up record updated successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w("DB_UPDATE", "No rows updated. Possibly no matching record.");
                        Toast.makeText(context, "No matching record found to update.", Toast.LENGTH_SHORT).show();
                    }
                    //Shared Preference
                    SharedPreferences prefs = context.getSharedPreferences("CashPrefs", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("last_note", (int) insertedFoundAmount);
                    editor.putString("inserted_time", getCurrentTimestamp());
                    editor.apply();
                }

                insertedAmount += (int) insertedFoundAmount;

                updateInsertedDisplay(CashAcceptorActivity.cashActivity, context);

                if (deviceCom != null) {
                    deviceCom.SetEscrowAction(SSPSystem.BillAction.Accept);
                } else {
                    Log.d("DeviceManager", "deviceCom is null. Call DeviceManager.initialize(context) first.");
                }
//                if (insertedAmount == insertedTobeAmount) {
//                    Log.d("Device", "Target reached. Now disable device.");
//                    if (deviceCom != null) {
////                        deviceCom.SetDeviceEnable(false);
//                    } else {
//                        Log.d("DeviceManager", "deviceCom is null. Call DeviceManager.initialize(context) first.");
//                    }
//                }
                Log.d("Info", "Note Accepted from else");
            } else {
                Log.d("Info", "Note Rejected from else");
                showEventDialog(CashAcceptorActivity.cashActivity, "Target amount already inserted", "Please Collect your Extra Amount", false, 3000);
                deviceCom.SetEscrowAction(SSPSystem.BillAction.Reject);
                Log.d("Info", "Extra amount inserted from else");
            }
            Log.d("Info", "Inserted amount: " + insertedAmount + " / " + insertedTobeAmount + ".");

            dialog.dismiss();
        };

        btnCancel.setOnClickListener(updateCashLogic);
        btnContinue.setOnClickListener(updateCashLogic);
    }

    @SuppressLint("SetTextI18n")
    private static void updateInsertedDisplay(CashAcceptorActivity cashActivity, Context context) {
        insertedAmountText.setText("Inserted amount: ₹" + insertedAmount + " / ₹" + insertedTobeAmount);
        insertedSelectedAmountText.setText("Please insert ₹" + insertedTobeAmount + " in cash.");
        countFifty.setText(countFiftyNotes + "x");
        countHundred.setText(countHundredNotes + "x");
        countTwoHundred.setText(countTwoHundredNotes + "x");
        countFiveHundred.setText(countFiveHundredNotes + "x");

        Button buttonPay = cashActivity.findViewById(R.id.buttonPay);
        Button buttonFloat = cashActivity.findViewById(R.id.buttonFloat);
        Button buttonEmpty = cashActivity.findViewById(R.id.buttonEmpty);

        Button buttonCancel = cashActivity.findViewById(R.id.buttonCancel);
        Button buttonConfirm = cashActivity.findViewById(R.id.buttonConfirm);

        if (insertedAmount == 0) {
            buttonPay.setVisibility(View.GONE);
            buttonFloat.setVisibility(View.GONE);
            buttonEmpty.setVisibility(View.GONE);
            buttonCancel.setVisibility(View.GONE);
            buttonConfirm.setVisibility(View.GONE);
        } else if (insertedAmount < insertedTobeAmount) {
            Log.d("Info", "ssp Device stored Payout Value : " + sspDevice.storedPayoutValue);
            if (sspDevice.storedPayoutValue > 0){
                buttonPay.setVisibility(View.VISIBLE);
                buttonFloat.setVisibility(View.VISIBLE);
                buttonEmpty.setVisibility(View.VISIBLE);
            } else {
                buttonPay.setVisibility(View.GONE);
                buttonFloat.setVisibility(View.GONE);
                buttonEmpty.setVisibility(View.GONE);
            }
            // Add listeners once, or wrap with flag
            if (!buttonPay.hasOnClickListeners()) {
                buttonPay.setOnClickListener(v -> {
                    ItlCurrency curpay = new ItlCurrency();
                    curpay.country = m_DeviceCountry;
                    curpay.value = insertedAmount * 100;
                    Log.d("Info", "Payout " + m_DeviceCountry + " " + insertedAmount);
                    ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
                    if (deviceCom != null) {
                        deviceCom.PayoutAmount(curpay);
                    } else {
                        Log.d("Info", "deviceCom is null. Call DeviceManager.initialize(context) first.");
                    }
                    clearDisplay(cashActivity, context);
                    Log.d("Info", "Payout " + m_DeviceCountry + " " + insertedAmount);
                });
            };
            if (!buttonFloat.hasOnClickListeners()) {
                buttonFloat.setOnClickListener(v -> {
                    clearDisplay(cashActivity, context);
                    Log.d("Info", "Float");
                });
            };
            if (!buttonEmpty.hasOnClickListeners()) {
                buttonEmpty.setOnClickListener(v -> {
                    ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
                    if (deviceCom != null) {
                        deviceCom.EmptyPayout();
                    }
                    clearDisplay(cashActivity, context);
                    Log.d("Info", "Empty");
                });
            };

        } else if (insertedAmount == insertedTobeAmount) {
            buttonPay.setVisibility(View.INVISIBLE);
            buttonFloat.setVisibility(View.INVISIBLE);
            buttonEmpty.setVisibility(View.INVISIBLE);
            buttonCancel.setVisibility(View.VISIBLE);
            buttonConfirm.setVisibility(View.VISIBLE);

            buttonCancel.setOnClickListener(v -> {
                Log.d("Info", "Cancel");
                ItlCurrency curpay = new ItlCurrency();
                curpay.country = m_DeviceCountry;
                curpay.value = insertedAmount * 100;
                Log.d("Info", "Payout " + m_DeviceCountry + " " + insertedAmount);
                ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
                if (deviceCom != null) {
                    deviceCom.PayoutAmount(curpay);
                } else {
                    Log.d("Info", "deviceCom is null. Call DeviceManager.initialize(context) first.");
                }
                clearDisplay(cashActivity, context);
            });
            buttonConfirm.setOnClickListener(v -> {
                Log.d("Info", "Confirm");
                ContentValues values = new ContentValues();
                values.put(DatabaseConstants.COLUMN_TOPUP_STATUS, "Success");
                int rows = dbHelper.updateTopUpDetails(
                        values,
                        "CardNumber = ?",
                        new String[]{cardNumber}
                );
                if (rows > 0) {
                    Log.d("DB_UPDATE", "Update successful. Rows affected: " + rows);
                    Toast.makeText(context, "Top-up record updated successfully!", Toast.LENGTH_SHORT).show();
                } else {
                    Log.w("DB_UPDATE", "No rows updated. Possibly no matching record.");
                    Toast.makeText(context, "No matching record found to update.", Toast.LENGTH_SHORT).show();
                }
                clearDisplay(cashActivity, context);
                ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
                if (deviceCom != null) {
                    deviceCom.EmptyPayout();
                }
            });
        }
    }

    public static void DeviceDisconnected(SSPDevice dev) {
        Log.d("Info" , "Device Disconnected");
    }

    @SuppressLint("SetTextI18n")
    public static void DisplaySetUp(SSPDevice dev) {
        Log.d("Info" , "Display SetUp called");

        m_DeviceCountry = dev.shortDatasetVersion;
        sspDevice = dev;

        if (dev.type != SSPDeviceType.SmartPayout) {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(CashAcceptorActivity.getInstance());
            // 2. Chain together various setter methods to set the dialog characteristics
            builder.setMessage("Connected device is not SMART Payout (" + dev.type.toString() + ")")
                    .setTitle("SSP");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getInstance().finish();
                }
            });

            // 3. Get the AlertDialog from create()
            androidx.appcompat.app.AlertDialog dialog = builder.create();

            // 4. Show the dialog
            dialog.show();// show error
            return;
        }

        LinearLayout container = CashAcceptorActivity.cashActivity.findViewById(R.id.containerCurrency);
        container.removeAllViews(); // Optional: clear if reusing

        if (sspDevice != null && sspDevice.currency != null && !sspDevice.currency.isEmpty()) {
            for (int i = 0; i < sspDevice.currency.size(); i++) {
                ItlCurrency itlCurrency = sspDevice.currency.get(i);

                // Create row TextView
                TextView currencyRow = new TextView(CashAcceptorActivity.cashActivity);
                currencyRow.setText(itlCurrency.country + " | ₹" + itlCurrency.realvalue + " | " + itlCurrency.route);
                currencyRow.setPadding(16, 8, 16, 8);
                currencyRow.setTextSize(16);
                currencyRow.setTextColor(ContextCompat.getColor(CashAcceptorActivity.cashActivity, R.color.currencyText));

                // Set OnClickListener to show confirmation dialog
                currencyRow.setOnClickListener(v -> {
                    showChangeRouteDialog(itlCurrency, currencyRow);
                });
                container.addView(currencyRow);
            }
        } else {
            Log.e("CurrencyDebug", "sspDevice or sspDevice.currency is null or empty");
            TextView noData = new TextView(CashAcceptorActivity.cashActivity);
            noData.setText("No currency data available");
            noData.setPadding(16, 8, 16, 8);
            noData.setTextColor(ContextCompat.getColor(CashAcceptorActivity.cashActivity, R.color.no_data_text));
            container.addView(noData);
        }
    }

    @SuppressLint("SetTextI18n")
    private static void showChangeRouteDialog(ItlCurrency currency, TextView rowTextView) {
        Context context = rowTextView.getContext();
        new AlertDialog.Builder(context)
                .setTitle("Change Route")
                .setMessage("Current route: " + currency.route + "\nDo you want to toggle the route?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Toggle route logic
                    PayoutRoute newRoute;
                    if (currency.route == PayoutRoute.PayoutStore) {
                        newRoute = PayoutRoute.Cashbox;
                    } else {
                        newRoute = PayoutRoute.PayoutStore;
                    }

                    ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
                    if (deviceCom != null) {
                        deviceCom.SetPayoutRoute(currency, newRoute);
                    }
                    currency.route = newRoute;
                    // Reflect the change in the UI
                    rowTextView.setText(currency.country + " | ₹" + currency.realvalue + " | " + currency.route);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static CashAcceptorActivity getInstance() {
        return instance;
    }

    public static void DisplayEvents(DeviceEvent ev){
        Log.d("cashevents" , ev.event+"");
        switch (ev.event) {
            case CommunicationsFailure:
                Log.d("info" , "CommunicationsFailure");
                break;
            case Ready:
                showEventDialog(CashAcceptorActivity.cashActivity, "Ready", "Device is Ready to accept the notes", false,2000);
                Log.d("info" , "Ready");
                break;
            case BillRead:
                Log.d("info" , "Bill Read");
                showEventDialog(CashAcceptorActivity.cashActivity, "Read", "Reading Inserted Note", true,2000);
                break;
            case BillEscrow:
                Log.d("info" , "BillEscrow");
                Log.d("Info", ev.currency + " " + String.valueOf((int) ev.value) + ".00");
                showEventDialog(CashAcceptorActivity.cashActivity, "Escrowing", "Device is Esc rowing the Payment", true,2000);
                if (switchEscrow.isChecked()) {
                    showAlertDialogForInsertAmount(CashAcceptorActivity.cashActivity, ev.value);
//                    bttnAccept.setVisibility(View.VISIBLE);
//                    bttnReject.setVisibility(View.VISIBLE);
                }
                break;
            case BillStacked:
                Log.d("info" , "BillStacked");
                showEventDialog(CashAcceptorActivity.cashActivity, "Note get stacked", "Device is Stacking the note", true,2000);
                break;
            case BillReject:
                showEventDialog(CashAcceptorActivity.cashActivity, "Note Rejected", "Please check your note and insert proper note or Checked wither you try to insert extra amount", false,3000);
                Log.d("info" , "Bill Reject");
                if (switchEscrow.isChecked()) {
//                    bttnAccept.setVisibility(View.INVISIBLE);
//                    bttnReject.setVisibility(View.INVISIBLE);
                }
                break;
            case BillJammed:
                Log.d("info" , "Bill jammed");
                break;
            case BillFraud:
                Log.d("info" , "Bill Fraud");
                break;
            case BillCredit:
                Log.d("info" , "Bill Credit");
                showEventDialog(CashAcceptorActivity.cashActivity, "Bill Credit", "Bill Credit", true,2000);
                break;
            case Full:
                Log.d("info" , "Bill Cash box Full");
                break;
            case Initialising:
                Log.d("info" , "Initialising");
                showEventDialog(CashAcceptorActivity.cashActivity, "Initialising", "Initialising", true,2000);
                break;
            case Disabled:
                showEventDialog(CashAcceptorActivity.cashActivity, "Disabled", "Disabled", true,2000);
                Log.d("info" , "Disabled");
                break;
            case SoftwareError:
                Log.d("info" , "Software error");
                break;
            case AllDisabled:
                Log.d("info" , "All channels disabled");
                break;
            case CashboxRemoved:
                showEventDialog(CashAcceptorActivity.cashActivity, "Cash Box Removed", "Cash Box Removed", true,2000);
                Log.d("info" , "Cash Box Removed");
                break;
            case CashboxReplaced:
                showEventDialog(CashAcceptorActivity.cashActivity, "Cash Box Replaced", "Cash Box Replaced", true,2000);
                Log.d("info" , "Cash Box Replaced");
                break;
            case NotePathOpen:
                Log.d("info" , "Note Path Open");
                break;
            case BarCodeTicketEscrow:
                Log.d("info" , "Barcode ticket escrow");
                if (switchEscrow.isChecked()) {
                    showAlertDialogForInsertAmount(CashAcceptorActivity.cashActivity, ev.value);
//                    bttnAccept.setVisibility(View.VISIBLE);
//                    bttnReject.setVisibility(View.VISIBLE);
                }
                break;
            case BarCodeTicketStacked:
                Log.d("info" , "Barcode ticket stacked");
                break;
            case BillStoredInPayout:
                showEventDialog(CashAcceptorActivity.cashActivity, "Bill Stored In Payout", "Bill Stored In Payout", true,2000);
                Log.d("Info", "Bill Stored In Payout");
                break;
            case PayoutOutOfService:
                showEventDialog(CashAcceptorActivity.cashActivity, "Payout Out Of Service", "Payout Out Of Service", true,2000);
                Log.d("Info", "Payout out of service");
                break;
            case Dispensing:
                showEventDialog(CashAcceptorActivity.cashActivity, "Dispensing", "Dispensing", true,2000);
                Log.d("Info", "Payout dispensing");
                break;
            case Dispensed:
                showEventDialog(CashAcceptorActivity.cashActivity, "Dispensed", "Dispensed", true,2000);
                Log.d("Info", "Payout dispensed");
                break;
            case Emptying:
                showEventDialog(CashAcceptorActivity.cashActivity, "Emptying", "Emptying", true,2000);
                Log.d("Info", "Payout emptying");
                break;
            case Emptied:
                showEventDialog(CashAcceptorActivity.cashActivity, "Emptied", "Emptied", true,2000);
                Log.d("Info", "Payout emptied");
                break;
            case SmartEmptying:
                Log.d("Info", "Payout emptying");
                break;
            case SmartEmptied:
                Log.d("Info", "Payout emptied");
                break;
            case BillTransferedToStacker:
                Log.d("Info", "Bill Transferred ToStacker");
                showEventDialog(CashAcceptorActivity.cashActivity, "Bill Transferred ToStacker", "Bill Transferred ToStacker", true,2000);
                break;
            case BillHeldInBezel:
                break;
            case BillInStoreAtReset:
                break;
            case BillInStackerAtReset:
                break;
            case BillDispensedAtReset:
                break;
            case NoteFloatRemoved:
                Log.d("Info", "NF removed");
                break;
            case NoteFloatAttached:
                Log.d("Info", "NF attached");
                break;
            case DeviceFull:
                Log.d("Info", "Device Full");
                break;
            case RefillBillCredit:
                break;
        }
    }

//    public static void showEventDialog(Context context, String header, String subHeader, boolean showProgress, int delayMillis) {
//        Log.d("Info", "Attempting to show event dialog.");
//
//        // Check if the context is a valid Activity
//        if (context instanceof Activity && !((Activity) context).isFinishing() && !((Activity) context).isDestroyed()) {
//            // --- If Activity is VALID, show the Dialog (Normal Path) ---
//            showDialogInternal(context, header, subHeader, showProgress, delayMillis);
//        } else {
//            // --- If Activity is INVALID, show a Toast instead (Fallback Path) ---
//            Log.w("DialogHelper", "Activity not available. Showing Toast as a fallback.");
//            String toastMessage = header + "\n" + subHeader;
//            // Use getApplicationContext() to ensure the Toast can always be shown
//            Toast.makeText(context.getApplicationContext(), toastMessage, Toast.LENGTH_LONG).show();
//        }
//    }
//
//    private static void showDialogInternal(Context context, String header, String subHeader, boolean showProgress, int delayMillis) {
//        AlertDialog.Builder builder = new AlertDialog.Builder(context);
//        LayoutInflater inflater = LayoutInflater.from(context);
//        View dialogView = inflater.inflate(R.layout.dialog_event_popup, null);
//        builder.setView(dialogView);
//
//        TextView tvHeader = dialogView.findViewById(R.id.tvHeader);
//        TextView tvSubHeader = dialogView.findViewById(R.id.tvSubHeader);
//        ProgressBar progressBar = dialogView.findViewById(R.id.eventProgress);
//
//        tvHeader.setText(header);
//        tvSubHeader.setText(subHeader);
//        progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
//
//        AlertDialog eventDialog = builder.create();
//        eventDialog.setCancelable(false);
//        if (eventDialog.getWindow() != null) {
//            eventDialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
//        }
//
//        eventDialog.show();
//
//        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//            if (eventDialog.isShowing()) {
//                eventDialog.dismiss();
//            }
//        }, delayMillis);
//    }

    public static void showEventDialog(Context context, String header, String subHeader,
                                       boolean showProgress, int delayMillis) {
        Log.d("Info", "Attempting to show event dialog.");

        if (!(context instanceof Activity)) {
            // Not an Activity → fallback to Toast
            Toast.makeText(context.getApplicationContext(), header + "\n" + subHeader, Toast.LENGTH_LONG).show();
            return;
        }

        Activity activity = (Activity) context;

        // Guard: don’t show if finishing/destroyed
        if (activity.isFinishing() || activity.isDestroyed()) {
            Log.w("DialogHelper", "Activity not available. Showing Toast as fallback.");
            Toast.makeText(activity.getApplicationContext(), header + "\n" + subHeader, Toast.LENGTH_LONG).show();
            return;
        }

        // If an old dialog is still showing, dismiss it first
        if (activeEventDialog != null && activeEventDialog.isShowing()) {
            activeEventDialog.dismiss();
            activeEventDialog = null;
        }

        // Build dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_event_popup, null);
        builder.setView(dialogView);

        TextView tvHeader = dialogView.findViewById(R.id.tvHeader);
        TextView tvSubHeader = dialogView.findViewById(R.id.tvSubHeader);
        ProgressBar progressBar = dialogView.findViewById(R.id.eventProgress);

        tvHeader.setText(header);
        tvSubHeader.setText(subHeader);
        progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);

        activeEventDialog = builder.create();
        activeEventDialog.setCancelable(false);

        if (activeEventDialog.getWindow() != null) {
            activeEventDialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }

        activeEventDialog.show();

        // Auto-dismiss after delay
        dialogHandler.postDelayed(() -> {
            if (activeEventDialog != null && activeEventDialog.isShowing()
                    && !activity.isFinishing() && !activity.isDestroyed()) {
                activeEventDialog.dismiss();
                activeEventDialog = null;
            }
        }, delayMillis);
    }

    /** Call this in onDestroy() to prevent leaks */
    public static void dismissEventDialog() {
        dialogHandler.removeCallbacksAndMessages(null);
        if (activeEventDialog != null && activeEventDialog.isShowing()) {
            activeEventDialog.dismiss();
        }
        activeEventDialog = null;
    }

    @SuppressLint("DefaultLocale")
    public static void DisplayPayoutEvents(SSPPayoutEvent ev) {
        Log.d("cashevents payout" , ev.event+"");
        String pd = null;

        switch (ev.event) {
            case CashPaidOut:
                pd = "Paying " + ev.country + " " + String.format("%.2f", ev.realvalue) + " of " +
                        " " + ev.country + " " + String.format("%.2f", ev.realvalueRequested);
                showEventDialog(CashAcceptorActivity.cashActivity, "Payout Amount Invalid", pd, true,2000);
//                txtPayoutStatus.setText(pd);
//                DisplayChannels();
                break;
            case CashStoreInPayout:
                showEventDialog(CashAcceptorActivity.cashActivity, "Cash Store In Payout", "Cash Store In Payout", true,2000);
//                DisplayChannels();
                break;
            case CashLevelsChanged:
                showEventDialog(CashAcceptorActivity.cashActivity, "Cash Levels Changed", "Cash Levels Changed", true,2000);
//                DisplayChannels();
                break;
            case PayoutStarted:
                showEventDialog(CashAcceptorActivity.cashActivity, "Cash Levels Changed", "Cash Levels Changed", true,2000);
                pd = "Request " + ev.country + " " + String.format("%.2f", ev.realvalue) + " of " +
                        " " + ev.country + " " + String.format("%.2f", ev.realvalueRequested);
                showEventDialog(CashAcceptorActivity.cashActivity, "Payout Started", pd, true,2000);
//                txtPayoutStatus.setText(pd);
//                lPayoutControl.setVisibility(View.INVISIBLE);
                break;
            case PayoutEnded:
                pd = "Paid " + ev.country + " " + String.format("%.2f", ev.realvalue) + " of " +
                        " " + ev.country + " " + String.format("%.2f", ev.realvalueRequested);
                showEventDialog(CashAcceptorActivity.cashActivity, "Payout Ended", pd, true,2000);
//                txtPayoutStatus.setText(pd);
//                lPayoutControl.setVisibility(View.VISIBLE);
                break;
            case PayinStarted:
                showEventDialog(CashAcceptorActivity.cashActivity, "Pay in Started", "Pay in Started", true,2000);
                break;
            case PayinEnded:
                showEventDialog(CashAcceptorActivity.cashActivity, "Pay in Ended", "Pay in Ended", true,2000);
                break;
            case EmptyStarted:
                showEventDialog(CashAcceptorActivity.cashActivity, "Empty started", "Empty started", true,2000);
                Log.d("Info", "Empty started");
                break;
            case EmptyEnded:
                showEventDialog(CashAcceptorActivity.cashActivity, "Empty ended", "Empty ended", true,2000);
                Log.d("Info", "Empty ended");
                break;
            case PayoutConfigurationFail:
                //TODO handle config failures
                break;
            case PayoutAmountInvalid:
                showEventDialog(CashAcceptorActivity.cashActivity, "Payout Amount Invalid", "Payout Amount Invalid", true,2000);
                Log.d("Info", "Payout Amount Invalid");
                break;
            case PayoutRequestFail:
                //TODO handle this
                break;

            case RouteChanged:
                break;

            case PayoutDeviceNotConnected:
                showEventDialog(CashAcceptorActivity.cashActivity, "Payout device not connected", "Payout device not connected", true,2000);
                Log.d("Info", "Payout device not connected");
                break;

            case PayoutDeviceEmpty:
                Log.d("Info", "Payout device empty");
                showEventDialog(CashAcceptorActivity.cashActivity, "Payout device empty", "Payout device empty", true,2000);
                break;

            case PayoutDeviceDisabled:
                showEventDialog(CashAcceptorActivity.cashActivity, "Payout Device Disabled", "Payout Device Disabled", true,2000);
                Log.d("Info", "Payout device disabled");
                break;
        }
    }

    public static void UpdateFileDownload(SSPUpdate sspUpdate) {
        Log.d("UpdateFileDownload", "UpdateFileDownload Inside UpdateFileDownload");
        switch (sspUpdate.UpdateStatus) {
            case dwnInitialise:
                progress.setMessage("Downloading Ram");
                progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progress.setIndeterminate(false);
                progress.setProgress(0);
                progress.setMax(sspUpdate.numberOfRamBlocks);
                progress.setCanceledOnTouchOutside(false);
                progress.show();
                break;
            case dwnRamCode:
                progress.setProgress(sspUpdate.blockIndex);
                break;
            case dwnMainCode:
                progress.setMessage("Downloading flash");
                progress.setMax(sspUpdate.numberOfBlocks);
                progress.setProgress(sspUpdate.blockIndex);
                break;
            case dwnComplete:
                progress.dismiss();
                break;
            case dwnError:
                progress.dismiss();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("onActivityResult", "Inside onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 123 && resultCode == RESULT_OK) {
            Log.d("onActivityResult", "Inside If");
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();

            path += "/";
            String flname = "";
            Log.d("onActivityResult", "File Name : " + flname);
            if (data.hasExtra("filename")) {
                Log.d("onActivityResult", "Inside nested if file name has data");
                flname = data.getStringExtra("filename");
                path += flname;

            } else {
                Log.d("Info", "No file data");
//                txtDevice.setText(R.string.no_file_data_error);
                return;
            }

            SSPUpdate sspUpdate = DeviceManager.getSSPUpdate();
            sspUpdate = new SSPUpdate(flname);

            try {
                final File up = new File(path);

                sspUpdate.fileData = new byte[(int) up.length()];
                DataInputStream dis = new DataInputStream(new FileInputStream(up));
                dis.readFully(sspUpdate.fileData);
                dis.close();

                sspUpdate.SetFileData();
                clearDisplay(cashActivity, CashAcceptorActivity.cashActivity);
                ItlCurrency currency = new ItlCurrency();
                PayoutRoute newRoute;
                if (currency.route == PayoutRoute.PayoutStore) {
                    newRoute = PayoutRoute.Cashbox;
                } else {
                    newRoute = PayoutRoute.PayoutStore;
                }
                ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
                if (deviceCom != null) {
                    deviceCom.SetPayoutRoute(currency, newRoute);
                }
                deviceCom.SetSSPDownload(sspUpdate);
            } catch (IOException e) {
                e.printStackTrace();
                //   txtEvents.append(R.string.unable_to_load + "\r\n");
            }
        }
    }
    public static void clearDisplay(CashAcceptorActivity cashActivity, Context context) {
        insertedAmount = 0;
        countFiftyNotes = 0;
        countHundredNotes = 0;
        countTwoHundredNotes = 0;
        countFiveHundredNotes = 0;
        updateInsertedDisplay(cashActivity, context);
    }

    /**
     * Append a line to the debug log TextView and auto-scroll.
     */
    private void appendLog(String message) {
        if (tvLog == null) return;

        // Timestamp prefix (optional)
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());

        // Append message
        tvLog.append("[" + timestamp + "] " + message + "\n");

        // Auto-scroll to bottom
        final int scrollAmount = tvLog.getLayout() == null ? 0 :
                tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight();

        if (scrollAmount > 0) {
            tvLog.scrollTo(0, scrollAmount);
        } else {
            tvLog.scrollTo(0, 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopInactivityTimer();
        isProcessRunning = false;
        dismissEventDialog();
    }

    private void startInactivityTimer() {
        Log.d("Info" , "Inactivity timer expired.");
        inactivityRunnable = () -> {
            if (!isProcessRunning) {
                isProcessRunning = true;
                // New Thread
                new Thread(() -> {
                    Log.d("Info" , "Inserted Amount : " + insertedAmount);
                    ItlCurrency curpay = new ItlCurrency();
                    curpay.country = m_DeviceCountry;
                    curpay.value = insertedAmount * 100;
                    Log.d("Info", "Payout " + m_DeviceCountry + " " + insertedAmount);

                    ITLDeviceCom deviceCom = DeviceManager.getDeviceCom();
                    if (deviceCom != null) {
                        Log.d("Info" , "Current Paying Amount : " + curpay.value);
//                        deviceCom.SetEscrowAction(SSPSystem.BillAction.Reject);
                        if (DeviceManager.getFTDevice() == null || !DeviceManager.getFTDevice().isOpen()) {
                            Log.e("FTDI", "Device is null or closed. Can't send command.");
//
                        } else {
                            Log.d("Info", "Device is open. Sending command.");
                            if(curpay.value > 0) {
                                deviceCom.PayoutAmount(curpay);
                            } else {
                                Log.d("Info", "Payout amount is 0. Not sending command.");
                            }
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } else {
                        Log.d("Info", "deviceCom is null. Call DeviceManager.initialize(context) first.");
                    }

                    // Close the device after payout
//                    DeviceManager.closeDevice();
                }).start();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent = new Intent(CashAcceptorActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    isProcessRunning = false;
                }, 10000);

//                finish();
            }
        };
        inactivityHandler.postDelayed(inactivityRunnable, TIMEOUT_DURATION);
    }

    private void stopInactivityTimer() {
        if (inactivityRunnable != null) {
            inactivityHandler.removeCallbacks(inactivityRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DeviceManager.getFTDevice() == null || !DeviceManager.getFTDevice().isOpen()) {
            DeviceManager.initialize(this);
            if (DeviceManager.openDevice(this)) {
                DeviceManager.setupDevice();
                Log.d("Info", "Device reinitialized in onResume");
            }
        }
        stopInactivityTimer();
        startInactivityTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopInactivityTimer();
        isProcessRunning = false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN ||
                ev.getAction() == MotionEvent.ACTION_MOVE) {
            stopInactivityTimer();
            startInactivityTimer();
        }  // restart on any user touch
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        stopInactivityTimer();  // Clear old timer
        startInactivityTimer(); // Restart fresh
    }
}