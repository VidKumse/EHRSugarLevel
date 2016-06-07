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

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BLEService extends Service {

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private final static String TAG = BLEService.class.getSimpleName();
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    public BluetoothGattCharacteristic temperatureRead;

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

    //Ta metoda kliče pisanje enable flaga v senzor. Ko se izvede, se pokliče callback metoda onCharacteristicRead()
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeCharacteristic(characteristic);
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
                System.out.println("dobi "+characteristic.getValue());

            }
        }

        //Tu imamo nadzor nad prejeto meritvijo, če se ta spremeni. Ali to rabimo?
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            System.out.println("update");
        }

        //Metoda se izvede takoj za pošiljanjem enable flaga v senzor. Ta metoda nato po delayu kliče branje temperature
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //Pred metodo za branje temperature se izvede timer, ki počaka 1 sekundo, saj meritev traja
            new Timer().schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            readCharacteristic(temperatureRead);
                        }
                    },
                    1000
            );
        }
    };


    //Obe BoradcastUpdate metodi, ki ju uporablja zgornji callback. Prva samo pošlje intent, druga pa še
    //konkretne zajete podatke
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    //Ta metoda pošlje podatek o temperaturi Activityu Obrazec.java
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);


        //Klic metode za pretvorbo prejetega podatka v temperaturo
        double ambientTemperature = extractAmbientTemperature(characteristic);
        double targetTemperature = extractTargetTemperature(characteristic, ambientTemperature);

        //Zaokroževanje temperatur
        int ambientTemperatureINT = (int) Math.round(ambientTemperature);
        int targetTemperatureINT = (int) Math.round(targetTemperature);

        //S pomočjo broadcast intenta pošljemo podatek Activityu Obrazec.java
        intent.putExtra(EXTRA_DATA, String.valueOf(ambientTemperatureINT));
        sendBroadcast(intent);
    }

    //Glavna metoda, s pomočjo katere povemo BLE napravi, kater senzor bi radi brali
    public void getTemperature(BluetoothGatt bluetoothGatt) {
        //Tu se definirajo naslovi iz datasheeta
        UUID temperatureServiceUuid = UUID.fromString("f000aa00-0451-4000-b000-000000000000");
        UUID temperatureConfigUuid = UUID.fromString("f000aa02-0451-4000-b000-000000000000");
        UUID temperatureReadUuid = UUID.fromString("F000AA01-0451-4000-b000-000000000000");

        //Ustvarimo senzor na podlagi naslova iz datasheeta
        BluetoothGattService temperatureService = bluetoothGatt.getService(temperatureServiceUuid);

        //Ustvarimo karakteristiko na podlagi naslova iz datasheeta. To je karakteristika, kamor
        //bomo poslali enable flag
        BluetoothGattCharacteristic config = temperatureService.getCharacteristic(temperatureConfigUuid);

        //Karakteristika, iz katere bo priletel podatek
        temperatureRead = temperatureService.getCharacteristic(temperatureReadUuid);

        //pošiljanje enable flaga -> logična 1
        config.setValue(new byte[]{0x01});

        writeCharacteristic(config);
    }

    //Dve metodi za pretvorbo perejetega podatka v temperaturo. Iz dokumentacije senzorja
    private double extractAmbientTemperature(BluetoothGattCharacteristic c) {
        int offset = 2;
        return shortUnsignedAtOffset(c, offset) / 128.0;
    }

    private static Integer shortUnsignedAtOffset(BluetoothGattCharacteristic c, int offset) {
        Integer lowerByte = c.getIntValue(c.FORMAT_UINT8, offset);
        Integer upperByte = c.getIntValue(c.FORMAT_UINT8, offset + 1);

        return (upperByte << 8) + lowerByte;
    }

    private double extractTargetTemperature(BluetoothGattCharacteristic c, double ambient) {
        Integer twoByteValue = shortSignedAtOffset(c, 0);

        double Vobj2 = twoByteValue.doubleValue();
        Vobj2 *= 0.00000015625;

        double Tdie = ambient + 273.15;

        double S0 = 5.593E-14;	// Calibration factor
        double a1 = 1.75E-3;
        double a2 = -1.678E-5;
        double b0 = -2.94E-5;
        double b1 = -5.7E-7;
        double b2 = 4.63E-9;
        double c2 = 13.4;
        double Tref = 298.15;
        double S = S0*(1+a1*(Tdie - Tref)+a2*Math.pow((Tdie - Tref),2));
        double Vos = b0 + b1*(Tdie - Tref) + b2*Math.pow((Tdie - Tref),2);
        double fObj = (Vobj2 - Vos) + c2*Math.pow((Vobj2 - Vos),2);
        double tObj = Math.pow(Math.pow(Tdie,4) + (fObj/S),.25);

        return tObj - 273.15;
    }

    private static Integer shortSignedAtOffset(BluetoothGattCharacteristic c, int offset) {
        Integer lowerByte = c.getIntValue(c.FORMAT_UINT8, offset);
        Integer upperByte = c.getIntValue(c.FORMAT_SINT8, offset + 1); // Note: interpret MSB as signed.

        return (upperByte << 8) + lowerByte;
    }

}
