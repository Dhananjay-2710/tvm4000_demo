package com.cam.final_demo.cash_acceptor;

import android.content.Context;
import android.util.Log;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import device.itl.sspcoms.SSPDevice;
import device.itl.sspcoms.SSPUpdate;

public class DeviceManager {
    private static final String TAG = "DeviceManager";

    private static D2xxManager ftD2xx = null;
    private static FT_Device ftDev = null;
    private static ITLDeviceCom deviceCom;
    private static boolean isInitialized = false;
    private static SSPUpdate sspUpdate = null;

    public static void initialize(Context context) {
        Log.d("Info" , "Initializing DeviceManager Start");
        if (!isInitialized) {
            try {
                ftD2xx = D2xxManager.getInstance(context);
                deviceCom = new ITLDeviceCom();
                isInitialized = true;
            } catch (D2xxManager.D2xxException ex) {
                Log.d("DeviceManager", "FTDI Init Error: " + ex.toString());
            }
        }
        Log.d("Info" , "Initializing DeviceManager End");
    }

    public static boolean openDevice(Context context) {
        Log.d("Info" , "Open device Start");
        if (ftD2xx == null) return false;

        int devCount = ftD2xx.createDeviceInfoList(context);
        if (devCount <= 0) return false;

        D2xxManager.FtDeviceInfoListNode[] deviceList = new D2xxManager.FtDeviceInfoListNode[devCount];
        ftD2xx.getDeviceInfoList(devCount, deviceList);

        if (ftDev == null) {
            ftDev = ftD2xx.openByIndex(context, 0);
        } else {
            synchronized (ftDev) {
                ftDev = ftD2xx.openByIndex(context, 0);
            }
        }

        if (ftDev != null && ftDev.isOpen()) {
            configureDevice(9600, (byte) 8, (byte) 2, (byte) 0, (byte) 0);
            ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
            ftDev.restartInTask();
            return true;
        }
        Log.d("Info" , "open Device End ");
        return false;
    }

    public static void configureDevice(int baud, byte dataBits, byte stopBits, byte parity, byte flowControl) {
        if (!ftDev.isOpen()) return;
        // configure our port
        // reset to UART mode for 232 devices
        ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);
        ftDev.setBaudRate(baud);

        switch (dataBits) {
            case 7:
                dataBits = D2xxManager.FT_DATA_BITS_7;
                break;
            case 8:
            default:
                dataBits = D2xxManager.FT_DATA_BITS_8;
                break;
        }

        switch (stopBits) {
            case 2:
                stopBits = D2xxManager.FT_STOP_BITS_2;
                break;
            default:
                stopBits = D2xxManager.FT_STOP_BITS_1;
                break;
        }

        switch (parity) {
            case 0:
                break;
            case 1:
                parity = D2xxManager.FT_PARITY_ODD;
                break;
            case 2:
                parity = D2xxManager.FT_PARITY_EVEN;
                break;
            case 3:
                parity = D2xxManager.FT_PARITY_MARK;
                break;
            case 4:
                parity = D2xxManager.FT_PARITY_SPACE;
                break;
            default:
                parity = D2xxManager.FT_PARITY_NONE;
                break;
        }

        ftDev.setDataCharacteristics(dataBits, stopBits, parity);

        short flowCtrlSetting;
        switch (flowControl) {
            case 1:
                flowCtrlSetting = D2xxManager.FT_FLOW_RTS_CTS;
                break;
            case 2:
                flowCtrlSetting = D2xxManager.FT_FLOW_DTR_DSR;
                break;
            case 3:
                flowCtrlSetting = D2xxManager.FT_FLOW_XON_XOFF;
                break;
            default:
                flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
                break;
        }
        ftDev.setFlowControl(flowCtrlSetting, (byte) 0x0b, (byte) 0x0d);
    }

    public static void setupDevice() {
        if (deviceCom != null && ftDev != null && ftDev.isOpen()) {
            deviceCom.setup(ftDev, 0, true, true, 0x0123456701234567L);
            deviceCom.start();
        }
    }

    public static void closeDevice() {
        isInitialized = false;
        if (deviceCom != null) {
            deviceCom.Stop();
        }
        if (ftDev != null) {
            ftDev.close();
        }
    }

    public static ITLDeviceCom getDeviceCom() {
        return deviceCom;
    }

    public static SSPUpdate getSSPUpdate() {
        return sspUpdate;
    }

    public static FT_Device getFTDevice() {
        return ftDev;
    }

    public static boolean isConnected() {
        return ftDev != null && ftDev.isOpen();
    }
}