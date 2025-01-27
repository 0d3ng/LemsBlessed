package com.sinaungoding.lemsblessed;

import static com.welie.blessed.BluetoothBytesParser.bytes2String;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;
import com.welie.blessed.ConnectionPriority;
import com.welie.blessed.GattStatus;
import com.welie.blessed.HciStatus;
import com.welie.blessed.PhyOptions;
import com.welie.blessed.PhyType;
import com.welie.blessed.ScanFailure;
import com.welie.blessed.ScanMode;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;

import timber.log.Timber;

public class BluetoothHandler {

    private static BluetoothHandler instance = null;
    public BluetoothCentralManager central;
    private final Context context;
    private final Handler handler = new Handler();

    private static final UUID BTVS_SERVICE_UUID = UUID.fromString("f9cc1523-4e0a-49e5-8cf3-0007e819ea1e");
    private static final UUID BTVS_CHARACTERISTIC_UUID = UUID.fromString("f9cc152a-4e0a-49e5-8cf3-0007e819ea1e");

    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {
        @Override
        public void onServicesDiscovered(@NotNull BluetoothPeripheral peripheral) {
            // Request a higher MTU, iOS always asks for 185
//            peripheral.requestMtu(50);

            // Request a new connection priority
            peripheral.requestConnectionPriority(ConnectionPriority.HIGH);

            boolean setPreferredPhy = peripheral.setPreferredPhy(PhyType.LE_2M, PhyType.LE_2M, PhyOptions.S2);
            Timber.d("setPreferredPhy %s", setPreferredPhy);

            // Read manufacturer and model number from the Device Information Service
//            boolean result = peripheral.readCharacteristic(BTVS_SERVICE_UUID, BTVS_SERVICE_UUID);
//            Timber.d("readCharacteristic: %s", result);
//
//            result = peripheral.readPhy();
//            Timber.d("readPhy: %s", result);
//
//            // Turn on notifications for Current Time Service and write it if possible
//            BluetoothGattCharacteristic currentTimeCharacteristic = peripheral.getCharacteristic(BTVS_SERVICE_UUID, BTVS_SERVICE_UUID);
//            if (currentTimeCharacteristic != null) {
//                peripheral.setNotify(currentTimeCharacteristic, true);
//            } else
//                Timber.d("currentTimeCharacteristic NULL");
//
//            // Try to turn on notifications for other characteristics
//            result = peripheral.readCharacteristic(BTVS_SERVICE_UUID, BTVS_SERVICE_UUID);
//            Timber.d("readCharacteristic: %s", result);
//            result = peripheral.setNotify(BTVS_SERVICE_UUID, BTVS_SERVICE_UUID, true);
//            Timber.d("setNotify: %s", result);
            BluetoothGattCharacteristic characteristic = null;
            for (BluetoothGattService service : peripheral.getServices()) {
                Timber.d("service with uuid: %s", service.getUuid().toString());
                for (BluetoothGattCharacteristic gattCharacteristic : service.getCharacteristics()) {
                    if ((BTVS_SERVICE_UUID.toString().equals(service.getUuid().toString())) &&
                            (BTVS_CHARACTERISTIC_UUID.toString().equals(gattCharacteristic.getUuid().toString()))) {
                        Timber.d("Found service");
                        characteristic = gattCharacteristic;
                        break;
                    }
                }
            }
            if (characteristic != null) {
                peripheral.setNotify(characteristic, true);
                String value = characteristic.getStringValue(0);
                Timber.d("New value: %s", value);
            }
        }

        @Override
        public void onNotificationStateUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                final boolean isNotifying = peripheral.isNotifying(characteristic);
                Timber.i("SUCCESS: Notify set to '%s' for %s", isNotifying, characteristic.getUuid());
            } else {
                Timber.e("ERROR: Changing notification state failed for %s (%s)", characteristic.getUuid(), status);
            }
        }

        @Override
        public void onCharacteristicWrite(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                Timber.i("SUCCESS: Writing <%s> to <%s>", bytes2String(value), characteristic.getUuid());
            } else {
                Timber.i("ERROR: Failed writing <%s> to <%s> (%s)", bytes2String(value), characteristic.getUuid(), status);
            }
        }

        @Override
        public void onCharacteristicUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] data, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status != GattStatus.SUCCESS) return;

            UUID characteristicUUID = characteristic.getUuid();
//            BluetoothBytesParser parser = new BluetoothBytesParser(value);
//            Timber.d("Uuid: %s value: %s", characteristicUUID.toString(), parser.getStringValue(0));
            Timber.d("Uuid: %s value: %s", characteristicUUID.toString(), byteArrayToHex(data));
            int co2 = ByteBuffer.wrap(new byte[]{data[5], data[6]}).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
//            Timber.d("Co2: %d PM2.5: %f Temp: %f Humidity: %f", co2, pm25, temp, humidity);
            Timber.d("Co2: %d", co2);
        }

        private String byteArrayToHex(byte[] bytes) {
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                hexString.append(String.format("%02X ", b));
            }
            return hexString.toString().trim();
        }

        private int bytesToUnsignedInt16(byte[] bytes, int offset) {
            return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
        }

        private float bytesToFloat(byte[] bytes, int offset) {
            int intBits = ((bytes[offset] & 0xFF) << 24) |
                    ((bytes[offset + 1] & 0xFF) << 16) |
                    ((bytes[offset + 2] & 0xFF) << 8) |
                    (bytes[offset + 3] & 0xFF);
            return Float.intBitsToFloat(intBits);
        }

        @Override
        public void onMtuChanged(@NotNull BluetoothPeripheral peripheral, int mtu, @NotNull GattStatus status) {
            Timber.i("new MTU set: %d", mtu);
        }

        private void sendMeasurement(@NotNull Intent intent, @NotNull BluetoothPeripheral peripheral) {
            Timber.d("sendMeasurement: %s", peripheral.getAddress());
        }

        private void writeContourClock(@NotNull BluetoothPeripheral peripheral) {
            Calendar calendar = Calendar.getInstance();
            int offsetInMinutes = calendar.getTimeZone().getRawOffset() / 60000;
            int dstSavingsInMinutes = calendar.getTimeZone().getDSTSavings() / 60000;
            calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
            BluetoothBytesParser parser = new BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN);
            Timber.d("writeContourClock: %s", peripheral.getAddress());
        }

        private void writeGetAllGlucoseMeasurements(@NotNull BluetoothPeripheral peripheral) {
            byte OP_CODE_REPORT_STORED_RECORDS = 1;
            byte OPERATOR_ALL_RECORDS = 1;
            final byte[] command = new byte[]{OP_CODE_REPORT_STORED_RECORDS, OPERATOR_ALL_RECORDS};
            Timber.d("writeGetAllGlucoseMeasurements: %s", peripheral.getAddress());
        }
    };

    // Callback for central
    private final BluetoothCentralManagerCallback bluetoothCentralManagerCallback = new BluetoothCentralManagerCallback() {

        @Override
        public void onConnectedPeripheral(@NotNull BluetoothPeripheral peripheral) {
            Timber.i("connected to '%s'", peripheral.getName());
        }

        @Override
        public void onConnectionFailed(@NotNull BluetoothPeripheral peripheral, final @NotNull HciStatus status) {
            Timber.e("connection '%s' failed with status %s", peripheral.getName(), status);
        }

        @Override
        public void onDisconnectedPeripheral(@NotNull final BluetoothPeripheral peripheral, final @NotNull HciStatus status) {
            Timber.i("disconnected '%s' with status %s", peripheral.getName(), status);

            // Reconnect to this device when it becomes available again
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    central.autoConnectPeripheral(peripheral, peripheralCallback);
                }
            }, 5000);
        }

        @Override
        public void onDiscoveredPeripheral(@NotNull BluetoothPeripheral peripheral, @NotNull ScanResult scanResult) {
            Timber.i("Found peripheral '%s'", peripheral.getName());
            central.stopScan();
            central.connectPeripheral(peripheral, peripheralCallback);

        }

        @Override
        public void onBluetoothAdapterStateChanged(int state) {
            Timber.i("bluetooth adapter changed state to %d", state);
            if (state == BluetoothAdapter.STATE_ON) {
                // Bluetooth is on now, start scanning again
                // Scan for peripherals with a certain service UUIDs
                central.startPairingPopupHack();
                startScan();
            }
        }

        @Override
        public void onScanFailed(@NotNull ScanFailure scanFailure) {
            Timber.i("scanning failed with error %s", scanFailure);
        }
    };

    public static synchronized BluetoothHandler getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothHandler(context.getApplicationContext());
        }
        return instance;
    }

    private BluetoothHandler(Context context) {
        this.context = context;

        // Plant a tree
        Timber.plant(new Timber.DebugTree());

        // Create BluetoothCentral
        central = new BluetoothCentralManager(context, bluetoothCentralManagerCallback, new Handler());

        // Scan for peripherals with a certain service UUIDs
        central.startPairingPopupHack();
        startScan();
    }

    private void startScan() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                central.setScanMode(ScanMode.LOW_LATENCY);
                central.scanForPeripheralsWithNames(new String[]{"EVS-4E7B"});
            }
        }, 1000);
    }
}
