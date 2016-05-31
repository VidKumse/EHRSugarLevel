package com.example.vid.ble_test;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.TextView;

public class Obrazec extends AppCompatActivity {

    TextView textView2;
    BluetoothDevice device;
    private String mDeviceAddress;
    private BLEService mBluetoothLeService;
    private final static String TAG = Obrazec.class.getSimpleName();
    private boolean mConnected = false;


    //metoda za kontrolo življenjskega cikla servisa
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BLEService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Ni useplo inicializirati bluetootha");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    //ta del pridobi podatke od broadcastrecieverja, torej podatke o servisih
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            //System.out.println("bbbbbbbb"+action);

            if (BLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //System.out.println("bbbbbbb sevices "+mBluetoothLeService.getServiceTemperature());
                System.out.println("bbbbbbb sevices ");
            } else if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) {
                System.out.println("bbbbbbb data "+intent.getStringExtra(BLEService.EXTRA_DATA));
                textView2.setText(""+intent.getStringExtra(BLEService.EXTRA_DATA));
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_obrazec);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        textView2 = (TextView) findViewById(R.id.textView2);
        device = getIntent().getExtras().getParcelable("device");
        textView2.setText("Ime naprave: " + device.getName());

        mDeviceAddress = device.getAddress();

        //Kličemo BLEService
        Intent gattServiceIntent = new Intent(this, BLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        //registerReciever metoda prejme podatke od BLEService.
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        //ugasne reciever
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Ugasne BLEService
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    //Zgenerira filtre, ki jih podamo v registerReciever
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


}
