package com.example.vid.ble_test;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class Obrazec extends AppCompatActivity {

    TextView textView2;
    BluetoothDevice device;
    private String mDeviceAddress;
    private BLEService mBluetoothLeService;
    private final static String TAG = Obrazec.class.getSimpleName();
    private boolean mConnected = false;

    TextView t;
    EditText vpis;
    EditText comment_polje;
    String sessionId = "";
    String sugar_level;
    String comment;
    String context;

    //Spremenljivke za senzor korakov
    private SensorManager mSensorManager;
    private Sensor mStepSensor;
    private TextView mTextView;


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

            if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) {
                //Izpis prejete meritve iz senzorja. Meritev je potrebno še razbrati
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

        //View elementi
        textView2 = (TextView) findViewById(R.id.textView2);
        device = getIntent().getExtras().getParcelable("device");
        textView2.setText("Ime naprave: " + device.getName());
        vpis = (EditText) findViewById(R.id.vpis);
        comment_polje = (EditText) findViewById(R.id.comment);
        mTextView = (TextView) findViewById(R.id.text_step);

        mDeviceAddress = device.getAddress();
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        //Kličemo BLEService
        Intent gattServiceIntent = new Intent(this, BLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        //registerReciever metoda prejme podatke od BLEService.
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        //Vklop senzorja korakov
        mSensorManager.registerListener(mSensorEventListener, mStepSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //ugasne reciever
        unregisterReceiver(mGattUpdateReceiver);

        //Izklop senzorja korakov
        mSensorManager.unregisterListener(mSensorEventListener);
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

    //Izvede se ob pritisku na gumb Pošlji. Pošlje podatke v ThinkEHR
    public void Send(View v) {

        t = (TextView)findViewById(R.id.t);
        sugar_level = vpis.getText().toString();
        comment = comment_polje.getText().toString();

        //Spremenljivke za čas
        Calendar c = Calendar.getInstance();
        int minutes = c.get(Calendar.MINUTE);
        int hours = c.get(Calendar.HOUR_OF_DAY);
        int day = c.get(Calendar.DAY_OF_MONTH);
        int month = c.get(Calendar.MONTH);
        int year = c.get(Calendar.YEAR);

        //Sestavimo format časa
        final String time = year+"-"+month+"-"+day+"T"+hours+":"+minutes+"Z";

        //2bbc32eb-ba55-43e6-bcd4-7e3ce9e4627e Ivan Cankar
        String patientIdEhr = "2bbc32eb-ba55-43e6-bcd4-7e3ce9e4627e";

        //Ustvarimo RequestQueue -> Vrsta, kamor bomo dodajali posamezne requeste.
        RequestQueue queue = Volley.newRequestQueue(this);

        //Prvi URL, kamor se pošlje geslo in username. Ta vrne sessionID
        String url ="https://rest.ehrscape.com/rest/v1/session?username=ltfe&password=ltfe54321";

        //Drugi URL, vključen je ID pacienta
        String url2 ="https://rest.ehrscape.com/rest/v1/composition?ehrId=" + patientIdEhr +

                "&templateId=Blood_Glucose_LTFE&format=FLAT&committer=";

        /* Request tipa StringRequest.. izgleda tako:
        StringRequest(metoda, URL, Response.Listener, Response.ErrorListener)
        Oba Listenerja override-amo, v njima določimo izvedbo akcije ob prejemu responsa

         */

        //PRVI REQUEST. V URL-ju pošljemo geslo in username, kot odgovor pa dobimo SessionID.
        //Head in Body requesta sta prazna
        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.POST, url, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {

                        //opstring se uporabi, ker v primeru napacnega keya vrne null string.
                        sessionId = response.optString("sessionId", null);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        t.setText("Prišlo je do napake med identifikacijo na strežniku!");
                    }
                });

        //Dodamo request v queue
        queue.add(jsObjRequest);

        //Zgeneriramo HashMap, kjer so vpisani naslovi do posameznih arhetipov in njihove konkkretne vrednosti za vpis
        //Tole se bo vpisalo v body drugega requesta
        Map<String,String> params = new HashMap<String, String>();
        params.put("ctx/language","en");
        params.put("ctx/territory", "SI");
        params.put("blood_glucose_test/blood_glucose_test_result:0/any_event:0/datetime_result_issued", time);
        params.put("blood_glucose_test/blood_glucose_test_result:0/any_event:0/result_group:0/result:0/glucose_result|magnitude", sugar_level);
        params.put("blood_glucose_test/blood_glucose_test_result:0/any_event:0/result_group:0/result:0/glucose_result|unit","mg/dl");
        params.put("blood_glucose_test/blood_glucose_test_result:0/any_event:0/result_group:0/result:0/result_comment:0",comment);
        params.put("blood_glucose_test/blood_glucose_test_result:0/any_event:0/result_group:0/result:0/result_context|code",context);
        params.put("blood_glucose_test/blood_glucose_test_result:0/device:0/device_name","VPD 2in1 Smart");
        params.put("blood_glucose_test/blood_glucose_test_result:0/device:0/type", "PG101");

        //DRUGI REQUEST. V glavi pošljemo sessionID, v body-ju pa podatke za vpis. Oboje je v formatu JSON
        JsonObjectRequest jsObjRequest2 = new JsonObjectRequest
                (Request.Method.POST, url2, new JSONObject(params), new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        t.setText("Vpis je bil uspešno poslan!");
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        t.setText("Prišlo je do napake med prenosom podatkov!");
                    }
                }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                //Glava (head)
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Content-Type", "application/json; charset=utf-8");
                headers.put("Ehr-Session", sessionId);
                return headers;
            }
        };
        queue.add(jsObjRequest2);
    }


    public void onRadioButtonClicked(View view) {
        //Preverimo, če je gumb pritisnjen
        boolean checked = ((RadioButton) view).isChecked();

        //Preverimo, kateri gumb je bil  pritisnjen
        switch(view.getId()) {
            case R.id.radio_predZajtrkom:
                if (checked)
                    context =  "at0.0.27";
                break;
            case R.id.radio_poZajtrku:
                if (checked)
                    context =  "at0.0.28";
                break;
            case R.id.radio_predKosilom:
                if (checked)
                    context =  "at0.0.29";
                break;
            case R.id.radio_poKosilu:
                if (checked)
                    context =  "at0.0.30";
                break;
            case R.id.radio_predVecerjo:
                if (checked)
                    context =  "at0.0.31";
                break;
            case R.id.radio_poVecerji:
                if (checked)
                    context =  "at0.0.32";
                break;
            case R.id.radio_predSpanjem:
                if (checked)
                    context =  "at0.0.33";
                break;
        }
    }

    //Listener za senzor korakov
    private SensorEventListener mSensorEventListener = new SensorEventListener() {

        private float mStepOffset;

        //Če se spremeni natančnost senzorja
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        //Če se spremeni vrednost senzorja
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mStepOffset == 0) {
                mStepOffset = event.values[0];
            }
            mTextView.setText("Prehodili ste že "+Float.toString(event.values[0] - mStepOffset)+" korakov. Čestitke!");
        }
    };

}
