package com.example.vid.ble_test;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.UUID;

public class BLEService extends Service {

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private final static String TAG = BLEService.class.getSimpleName();
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    //Nastavimo filtre
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";


    public BLEService() {
    }

    public class LocalBinder extends Binder {
        BLEService getService() {
            return BLEService.this;
        }
    }

    //Ustvarimo objekt IBinder
    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public boolean onUnbind(Intent intent) {
        //Zapremo povezavo z GATT
        close();
        return super.onUnbind(intent);
    }

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    //Ta metoda prebere vrednost iz senzorja. Kliče se v metodi getTemperature, za njo pa se izvede callback,
    //kjer imamo nadzor nad prejetim podatkom
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }


    //Ta metoda samo preverja podporo BLE na telefonu
    public boolean initialize() {
        //Preverimo, če obstajata BluetoothManager in BluetoothAdapter -> zaradi verzije androida!
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    //Ta metoda izvede povezavo z GATT serverjem
    public boolean connect(final String address) {
        //Preverimo, če BluetoothAdapter ni inicializiran ali nima podanega naslova
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Preverimo, če je povezava s serverjem že vzpostavljena
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        //Preverimo, če naprave ne najde
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        //Metoda za povezavo z GATT strežnikom. Autoconnect je nastavljen na false
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);

        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }


    //Callback od metode za povezavo z GATT. Tu imamo dostop do rezultatov povezave -> odkriti servisi, itd.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {


        //Tukaj service pošlje sporočilo Obrazcu (preko broadcast). Sporoči ali je povezava z GATT
        //vzpostavljena, ali prekinjena
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        //Tu imao nadzor nad servici, ki tečejo na BLE napravi
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                getTemperature(gatt);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        //Tu imamo nadzor nad prejeto meritvijo iz senzorja
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                System.out.println("dobi "+characteristic.toString());
            }
        }

        //Tu imamo nadzor nad prejeto meritvijo, če se ta spremeni. Ali to rabimo?
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            System.out.println("update");
        }
    };


    //Obe BoradcastUpdate metodi, ki ju uporablja zgornji callback. Prva samo pošlje intent, druga pa še
    //konkretne zajete podatke
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        //Ta del iz objekta characteristic pridobi HEX string in ga pošlje preko broadcasta
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
        }

        sendBroadcast(intent);
    }

    //Glavna metoda, s pomočjo katere povemo BLE napravi, kater senzor bi radi brali
    public void getTemperature(BluetoothGatt bluetoothGatt) {
        //Tu se definirajo naslovi iz datasheeta
        UUID temperatureServiceUuid = UUID.fromString("f000aa10-0451-4000-b000-000000000000");
        UUID temperatureConfigUuid = UUID.fromString("f000aa12-0451-4000-b000-000000000000");
        UUID temperatureReadUuid = UUID.fromString("F000AA11-0451-4000-b000-000000000000");

        //Ustvarimo senzor na podlagi naslova iz datasheeta
        BluetoothGattService temperatureService = bluetoothGatt.getService(temperatureServiceUuid);

        //Ustvarimo karakteristiko na podlagi naslova iz datasheeta. To je karakteristika, kamor
        //bomo poslali 1
        BluetoothGattCharacteristic config = temperatureService.getCharacteristic(temperatureConfigUuid);

        //Karakteristika, iz katere bo priletel podatek
        BluetoothGattCharacteristic temperatureRead = temperatureService.getCharacteristic(temperatureReadUuid);

        //pošiljanje 1
        config.setValue(new byte[]{1}); //NB: the config value is different for the Gyroscope
        bluetoothGatt.writeCharacteristic(config);

        System.out.println("Config: "+config.toString());

        //Takoj za pošiljanjem 1 kličemo metodo za branje karakteristike
        readCharacteristic(temperatureRead);
    }


}
