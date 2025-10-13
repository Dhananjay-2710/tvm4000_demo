package com.cam.final_demo;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.cam.final_demo.card_printer.CardPrinterActivity;
import com.cam.final_demo.cash_acceptor.CashAcceptorActivity;
import com.cam.final_demo.databinding.ActivityMainBinding;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.cam.final_demo.pax.PaxActivity;
import com.cam.final_demo.thermal_printer.ThermalPrinterActivity;
import com.cam.final_demo.usbdevicelist.DeviceListActivity;
import com.cam.final_demo.usbdevicelist.DeviceRegistry;
import com.cam.final_demo.usbdevicelist.UsbChecker;
import com.evolis.sdk.android.CLibrary;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private UsbChecker usbChecker;

    public static final String EXTRA_DEVICE_TYPE = "EXTRA_DEVICE_TYPE";

    private final int[] VIDS_FOR_PAX = new int[]{12216};
    private final int[] VIDS_FOR_CARD_PRINTER = new int[]{3913, 13030};
    private final int[] VIDS_FOR_CASH_ACCEPTOR = new int[]{1027};
    private final int[] VIDS_FOR_THERMAL_PRINTER = new int[]{8401};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CLibrary.setContext(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        usbChecker = new UsbChecker(this);

        setSupportActionBar(binding.includeToolbar.toolbar);

        binding.btnPax.setOnClickListener(v ->
                guardedStartActivity(VIDS_FOR_PAX, new Intent(this, PaxActivity.class))
        );

        binding.btnCardPrinter.setOnClickListener(v ->
                guardedStartActivity(VIDS_FOR_CARD_PRINTER, new Intent(this, CardPrinterActivity.class))
        );

        binding.btnCashAcceptor.setOnClickListener(v ->
                guardedStartActivity(VIDS_FOR_CASH_ACCEPTOR, new Intent(this, CashAcceptorActivity.class))
        );

        binding.btnThermalPrinter.setOnClickListener(v ->
                guardedStartActivity(VIDS_FOR_THERMAL_PRINTER, new Intent(this, ThermalPrinterActivity.class))
        );

//        binding.btnPax.setOnClickListener(v -> startActivity(new Intent(this, PaxActivity.class)));
//        binding.btnCardPrinter.setOnClickListener(v -> startActivity(new Intent(this, CardPrinterActivity.class)));
//        binding.btnCashAcceptor.setOnClickListener(v -> startActivity(new Intent(this, CashAcceptorActivity.class)));
//        binding.btnThermalPrinter.setOnClickListener(v -> startActivity(new Intent(this, ThermalPrinterActivity.class)));
        binding.btnDeviceList.setOnClickListener(v -> startActivity(new Intent(this, DeviceListActivity.class)));
    }

    /**
     * Check required VIDs then run the intent if check passes. Shows dialog when missing.
     */
//    private void guardedStartActivity(int[] requiredVids, Intent intentToStart) {
//        boolean ok = usbChecker.areRequiredVidsPresent(requiredVids);
//        if (ok) {
//            startActivity(intentToStart);
//            return;
//        }
//
//        List<Integer> missing = usbChecker.missingVids(requiredVids);
//        String missingHex = buildMissingHex(missing);
//        String connectedSummary = usbChecker.buildConnectedDevicesSummary();
//
//        AlertDialog.Builder b = new AlertDialog.Builder(this)
//                .setTitle("Missing USB device(s)")
//                .setMessage("Required device vendor ID(s) not found: " + missingHex +
//                        "\n\nConnected devices:\n" + connectedSummary +
//                        "\n\nPlease plug in the required device(s) and press Retry.")
//                .setPositiveButton("Retry", (dialog, which) -> {
//                    // re-check
//                    guardedStartActivity(requiredVids, intentToStart);
//                })
//                .setNeutralButton("Show Devices", (dialog, which) -> {
//                    // Show a concise device list dialog (same summary)
//                    new AlertDialog.Builder(this)
//                            .setTitle("Connected devices")
//                            .setMessage(connectedSummary)
//                            .setPositiveButton("OK", null)
//                            .show();
//                })
//                .setNegativeButton("Cancel", (dialog, which) -> {
//                    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
//                });
//
//        b.setCancelable(true);
//        b.show();
//    }
    private void guardedStartActivity(int[] requiredVids, Intent intentToStart) {
        boolean ok = usbChecker.areRequiredVidsPresent(requiredVids);
        if (ok) {
            startActivity(intentToStart);
            return;
        }

        List<Integer> missing = usbChecker.missingVids(requiredVids);
        // Friendly string of required devices (names + hex)
        String requiredList = DeviceRegistry.formatVidsWithNames(requiredVids);
        // Friendly string of missing devices (names + hex)
        String missingList = DeviceRegistry.formatMissingVids(missing);

        String connectedSummary = usbChecker.buildConnectedDevicesSummary();

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Missing USB device(s)")
                .setMessage("Required device(s): " + requiredList +
                        "\n\nMissing: " + missingList +
                        "\n\nConnected devices:\n" + connectedSummary +
                        "\n\nPlease plug in the required device(s) and press Retry.")
                .setPositiveButton("Retry", (dialog, which) -> {
                    // re-check
                    guardedStartActivity(requiredVids, intentToStart);
                })
                .setNeutralButton("Show Devices", (dialog, which) -> {
                    // Show a concise device list dialog (same summary)
                    new AlertDialog.Builder(this)
                            .setTitle("Connected devices")
                            .setMessage(connectedSummary)
                            .setPositiveButton("OK", null)
                            .show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
                });

        b.setCancelable(true);
        b.show();
    }


    private String buildMissingHex(List<Integer> missing) {
        if (missing == null || missing.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("0x%04X", missing.get(i)));
        }
        return sb.toString();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbChecker != null) usbChecker.teardown();
        binding = null;
    }
}
