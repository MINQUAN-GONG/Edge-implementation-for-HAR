package com.empatica.har;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.LinearLayout;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.EmpaticaDevice;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_ENABLE_BT = 1;

    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 1;

    private static final String EMPATICA_API_KEY = ""; // TODO insert your API Key here

    private EmpaDeviceManager deviceManager = null;

    private float[][] results;
    private static List<Float> ax, ay, az, ppg; //ensure loaded data are float
    private final int N_samples = 1600;

    private HARClassifier classifier;

    private TextView walkingTextView;

    private TextView runningTextView;

    private TextView lrbTextView;

    private TextView hrbTextView;

    private TextView accel_xLabel;

    private TextView accel_yLabel;

    private TextView accel_zLabel;

    private TextView bvpLabel;


    private TextView batteryLabel;

    private TextView statusLabel;

    private TextView deviceNameLabel;

    private LinearLayout dataCnt;

    public long startTime;

    public long ppgstartTime;

    public long starttingTime;

    int count = 0;

    int index_acc = 0;

    int index_ppg = 0;


    @Override //Initiliaze
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);


        // Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);

        dataCnt = (LinearLayout) findViewById(R.id.dataArea);

        accel_xLabel = (TextView) findViewById(R.id.accel_x);

        accel_yLabel = (TextView) findViewById(R.id.accel_y);

        accel_zLabel = (TextView) findViewById(R.id.accel_z);

        bvpLabel = (TextView) findViewById(R.id.bvp);

        batteryLabel = (TextView) findViewById(R.id.battery);

        deviceNameLabel = (TextView) findViewById(R.id.deviceName);

        walkingTextView = findViewById(R.id.walkingTextView);

        runningTextView = findViewById(R.id.runningTextView);

        lrbTextView = findViewById(R.id.lrbTextView);

        hrbTextView = findViewById(R.id.hrbTextView);

        ax = new ArrayList<>();  //dynamic array
        ay = new ArrayList<>();
        az = new ArrayList<>();
        ppg = new ArrayList<>();

        //Load pre-collected Test Data into individual channels for easier handling; this is only done when using pre-collected data
        try{
            classifier = new HARClassifier(getApplicationContext()); //try/catch
        } catch (IOException e){
            Log.e("tfliteSupport", "Error reading model", e);
        }

        final Button disconnectButton = findViewById(R.id.disconnectButton);

        disconnectButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (deviceManager != null) {

                    deviceManager.disconnect();  //按键关机
                }
            }
        });

        initEmpaticaDeviceManager(); //init
        starttingTime = System.currentTimeMillis();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_ACCESS_COARSE_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted, yay!
                    initEmpaticaDeviceManager();
                } else {
                    // Permission denied, boo!
                    final boolean needRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION);
                    new AlertDialog.Builder(this)
                            .setTitle("Permission required")
                            .setMessage("Without this permission bluetooth low energy devices cannot be found, allow it in order to connect to the device.")
                            .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // try again
                                    if (needRationale) {
                                        // the "never ask again" flash is not set, try again with permission request
                                        initEmpaticaDeviceManager();
                                    } else {
                                        // the "never ask again" flag is set so the permission requests is disabled, try open app settings to enable the permission
                                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                                        intent.setData(uri);
                                        startActivity(intent);
                                    }
                                }
                            })
                            .setNegativeButton("Exit application", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // without permission exit is the only way
                                    finish();
                                }
                            })
                            .show();
                }
                break;
        }
    }

    private void initEmpaticaDeviceManager() {
        // Android 6 (API level 23) now require ACCESS_COARSE_LOCATION permission to use BLE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
        } else {
            //if not insert APIkey:
            if (TextUtils.isEmpty(EMPATICA_API_KEY)) {
                new AlertDialog.Builder(this)
                        .setTitle("Warning")
                        .setMessage("Please insert your API KEY")
                        .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // without permission exit is the only way
                                finish();
                            }
                        })
                        .show();
                return;
            }

            // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
            deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);

            // Initialize the Device Manager using your API key. You need to have Internet access at this point.
            deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);
        }
    }

    @Override //on pause
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceManager != null) {
            deviceManager.cleanUp();
        }
    }

    @Override //stop
    protected void onStop() {
        super.onStop();
        if (deviceManager != null) {
            deviceManager.stopScanning();
        }
    }

    @Override
    public void didDiscoverDevice(EmpaticaDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {

        Log.i(TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed);

        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed + " - ConnectionNotAllowedException", e);
            }
        }
    }

    @Override
    public void didFailedScanning(int errorCode) {

        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                Log.e(TAG,"Scan failed: a BLE scan with the same settings is already started by the app");
                break;
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                Log.e(TAG,"Scan failed: app cannot be registered");
                break;
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                Log.e(TAG,"Scan failed: power optimized scan feature is not supported");
                break;
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                Log.e(TAG,"Scan failed: internal error");
                break;
            default:
                Log.e(TAG,"Scan failed with unknown error (errorCode=" + errorCode + ")");
                break;
        }
    }

    @Override
    public void didRequestEnableBluetooth() {
        // Request the user to enable Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public void bluetoothStateChanged() {
        // E4link detected a bluetooth adapter change
        // Check bluetooth adapter and update your UI accordingly.
        boolean isBluetoothOn = BluetoothAdapter.getDefaultAdapter().isEnabled();
        Log.i(TAG, "Bluetooth State Changed: " + isBluetoothOn);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user chose not to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // You should deal with this
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {

        didUpdateOnWristStatus(status);
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        // Update the UI
        updateLabel(statusLabel, status.name());

        // The device manager is ready for use
        if (status == EmpaStatus.READY) {
            updateLabel(statusLabel, status.name() + " - Turn on your device");
            // Start scanning
            deviceManager.startScanning();
            // The device manager has established a connection

            hide();

        } else if (status == EmpaStatus.CONNECTED) {

            show();
            // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {

            updateLabel(deviceNameLabel, "");

            hide();
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        if (ax.size() == 0) {
            startTime = System.currentTimeMillis();
            //System.out.println("Communication latency is " + (startTime - starttingTime));
            ax.add((float)x);
            ay.add((float)y);
            az.add((float)z);
            index_acc = 0;
        }
        updateLabel(accel_xLabel, "" + x);
        updateLabel(accel_yLabel, "" + y);
        updateLabel(accel_zLabel, "" + z);

        //synchronisation
        if ((ax.size() < N_samples) && (ax.size() > 0) && (index_acc != 255)) {
            if (((index_acc+1)%4) != 0){
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+1.0*(x-ax.get(6*index_acc+index_acc/4))/6.0));
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+2.0*(x-ax.get(6*index_acc+index_acc/4))/6.0));
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+3.0*(x-ax.get(6*index_acc+index_acc/4))/6.0));
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+4.0*(x-ax.get(6*index_acc+index_acc/4))/6.0));
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+5.0*(x-ax.get(6*index_acc+index_acc/4))/6.0));
                ax.add((float)x);

                ay.add((float)(ay.get(6*index_acc+index_acc/4)+1.0*(y-ay.get(6*index_acc+index_acc/4))/6.0));
                ay.add((float)(ay.get(6*index_acc+index_acc/4)+2.0*(y-ay.get(6*index_acc+index_acc/4))/6.0));
                ay.add((float)(ay.get(6*index_acc+index_acc/4)+3.0*(y-ay.get(6*index_acc+index_acc/4))/6.0));
                ay.add((float)(ay.get(6*index_acc+index_acc/4)+4.0*(y-ay.get(6*index_acc+index_acc/4))/6.0));
                ay.add((float)(ay.get(6*index_acc+index_acc/4)+5.0*(y-ay.get(6*index_acc+index_acc/4))/6.0));
                ay.add((float)y);

                az.add((float)(az.get(6*index_acc+index_acc/4)+1.0*(z-az.get(6*index_acc+index_acc/4))/6.0));
                az.add((float)(az.get(6*index_acc+index_acc/4)+2.0*(z-az.get(6*index_acc+index_acc/4))/6.0));
                az.add((float)(az.get(6*index_acc+index_acc/4)+3.0*(z-az.get(6*index_acc+index_acc/4))/6.0));
                az.add((float)(az.get(6*index_acc+index_acc/4)+4.0*(z-az.get(6*index_acc+index_acc/4))/6.0));
                az.add((float)(az.get(6*index_acc+index_acc/4)+5.0*(z-az.get(6*index_acc+index_acc/4))/6.0));
                az.add((float)z);
            }
            else{
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+1.0*(x-ax.get(6*index_acc+index_acc/4))/7.0));
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+2.0*(x-ax.get(6*index_acc+index_acc/4))/7.0));
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+3.0*(x-ax.get(6*index_acc+index_acc/4))/7.0));
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+4.0*(x-ax.get(6*index_acc+index_acc/4))/7.0));
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+5.0*(x-ax.get(6*index_acc+index_acc/4))/7.0));
                ax.add((float)(ax.get(6*index_acc+index_acc/4)+6.0*(x-ax.get(6*index_acc+index_acc/4))/7.0));
                ax.add((float)x);

                ay.add((float)(ay.get(6*index_acc+index_acc/4)+1.0*(y-ay.get(6*index_acc+index_acc/4))/7.0));
                ay.add((float)(ay.get(6*index_acc+index_acc/4)+2.0*(y-ay.get(6*index_acc+index_acc/4))/7.0));
                ay.add((float)(ay.get(6*index_acc+index_acc/4)+3.0*(y-ay.get(6*index_acc+index_acc/4))/7.0));
                ay.add((float)(ay.get(6*index_acc+index_acc/4)+4.0*(y-ay.get(6*index_acc+index_acc/4))/7.0));
                ay.add((float)(ay.get(6*index_acc+index_acc/4)+5.0*(y-ay.get(6*index_acc+index_acc/4))/7.0));
                ay.add((float)(ay.get(6*index_acc+index_acc/4)+6.0*(y-ay.get(6*index_acc+index_acc/4))/7.0));
                ay.add((float)y);

                az.add((float)(az.get(6*index_acc+index_acc/4)+1.0*(z-az.get(6*index_acc+index_acc/4))/7.0));
                az.add((float)(az.get(6*index_acc+index_acc/4)+2.0*(z-az.get(6*index_acc+index_acc/4))/7.0));
                az.add((float)(az.get(6*index_acc+index_acc/4)+3.0*(z-az.get(6*index_acc+index_acc/4))/7.0));
                az.add((float)(az.get(6*index_acc+index_acc/4)+4.0*(z-az.get(6*index_acc+index_acc/4))/7.0));
                az.add((float)(az.get(6*index_acc+index_acc/4)+5.0*(z-az.get(6*index_acc+index_acc/4))/7.0));
                az.add((float)(az.get(6*index_acc+index_acc/4)+6.0*(z-az.get(6*index_acc+index_acc/4))/7.0));
                az.add((float)z);
            }
            index_acc = index_acc + 1;
        }
        if (index_acc == 255){
            for(int kk = 0; kk < 6; kk++){
            ax.add((float)x);
            ay.add((float)y);
            az.add((float)z);
            }
            index_acc = index_acc + 1;
        }
        if (ax.size() == N_samples){
            long endTime = System.currentTimeMillis();
            long dt = endTime - startTime;
            System.out.println("Time to collect N_samples is " + dt);
        }
        HARPrediction();
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        updateLabel(bvpLabel, "" + bvp);
        if (ppg.size() == 0) {
            ppgstartTime = System.currentTimeMillis();
            //System.out.println("PPG Start time of count" + count + " is " + ppgstartTime);
            ppg.add((float)bvp);
            index_ppg = 0;
        }
        if((ppg.size() > 0) && (ppg.size() < N_samples) && (index_ppg) != 511){
            if (((index_ppg+1)%8) != 0){
                ppg.add((float)(ppg.get(3*index_ppg+index_ppg/8)+1.0*(bvp-ppg.get(3*index_ppg+index_ppg/8))/3.0));
                ppg.add((float)(ppg.get(3*index_ppg+index_ppg/8)+2.0*(bvp-ppg.get(3*index_ppg+index_ppg/8))/3.0));
                ppg.add((float)bvp);
            }
            else{
                ppg.add((float)(ppg.get(3*index_ppg+index_ppg/8)+1.0*(bvp-ppg.get(3*index_ppg+index_ppg/8))/4.0));
                ppg.add((float)(ppg.get(3*index_ppg+index_ppg/8)+2.0*(bvp-ppg.get(3*index_ppg+index_ppg/8))/4.0));
                ppg.add((float)(ppg.get(3*index_ppg+index_ppg/8)+3.0*(bvp-ppg.get(3*index_ppg+index_ppg/8))/4.0));
                ppg.add((float)bvp);
            }
            index_ppg = index_ppg + 1;
        }
        if (index_ppg == 511){
            for(int qq = 0; qq < 3; qq++){
                ppg.add((float)bvp);
            }
            index_ppg = index_ppg + 1;
        }
        if(ppg.size() == N_samples){
            long ppgendTime = System.currentTimeMillis();
            long ppgdt = ppgendTime - ppgstartTime;
            System.out.println("PPGTime to collect N_samples is " + ppgdt);
        }
        HARPrediction();
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        updateLabel(batteryLabel, String.format("%.0f %%", battery * 100));
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        //updateLabel(edaLabel, "" + gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        //updateLabel(ibiLabel, "" + ibi);
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        //updateLabel(temperatureLabel, "" + temp);
    }

    // Update a label with some text, making sure this is run in the UI thread
    private void updateLabel(final TextView label, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }

    @Override
    public void didReceiveTag(double timestamp) {

    }

    @Override
    public void didEstablishConnection() {
        show();
    }

    @Override
    public void didUpdateOnWristStatus(@EmpaSensorStatus final int status) {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                if (status == EmpaSensorStatus.ON_WRIST) {

                    ((TextView) findViewById(R.id.wrist_status_label)).setText("ON WRIST");
                }
                else {

                    ((TextView) findViewById(R.id.wrist_status_label)).setText("NOT ON WRIST");
                }
            }
        });
    }

    void show() {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                dataCnt.setVisibility(View.VISIBLE);
            }
        });
    }

    void hide() {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                dataCnt.setVisibility(View.INVISIBLE);
            }
        });
    }

    private void HARPrediction(){
        if(ax.size() == N_samples && ay.size() == N_samples && az.size() == N_samples && ppg.size() == N_samples) {
            System.out.println("N_sample is satisfied");
            //Load parameters for MinMax scaling of each input channel
            float ax_max = 127f; //8 bits
            float ax_min = -127f;
            float ax_delt = ax_max - ax_min;
            float ay_max = 127f;
            float ay_min = -127f;
            float ay_delt = ay_max - ay_min;
            float az_max = 127f;
            float az_min = -127f;
            float az_delt = az_max - az_min;
            float ppg_max = 700f;
            float ppg_min = -700f;
            float ppg_delt = ppg_max - ppg_min;

            //Segment and Reshape Data into fixed window sizes
            float[][][] input_3d = new float[1][1600][4];

            for (int n = 0; n < 400; n++) {
                for (int k = 0; k < 4; k++) {
                    input_3d[0][n][k] = 2 * ((ppg.get(4 * n + k) - ppg_min) / ppg_delt) - 1;
                    input_3d[0][n + 400][k] = 2 * ((ax.get(4 * n + k) - ax_min) / ax_delt) - 1;
                    input_3d[0][n + 800][k] = 2 * ((ay.get(4 * n + k) - ay_min) / ay_delt) - 1;
                    input_3d[0][n + 1200][k] = 2 * ((az.get(4 * n + k) - az_min) / az_delt) - 1;
                }
            }

            //System.out.println("Start prediction");
            //Make predictions on input data window in HAR Classifier
            results = classifier.predictions(input_3d);
            //System.out.println("Prediction finished");
            //Output predictions to app UI
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    walkingTextView.setText("Walking: \t" + round(results[0][0], 2));
                    runningTextView.setText("Running: \t" + round(results[0][1], 2));
                    lrbTextView.setText("Low Resistance Biking: \t" + round(results[0][2], 2));
                    hrbTextView.setText("High Resistance Biking: \t" + round(results[0][3], 2));
                }
            });

            ax.clear();
            ay.clear();
            az.clear();
            ppg.clear();
            System.out.println("Count" + count + "finished");
            long end = System.currentTimeMillis();
            System.out.println("Pridiction " + count + " time is " + (end-startTime));
            count = count + 1;
            //index_acc = 0;
            //index_ppg = 0;
        }

    }

    //Rounds the output predictions to two decimal places
    private static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

}
