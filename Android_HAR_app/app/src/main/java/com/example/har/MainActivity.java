package com.example.har;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Timer;
import java.util.TimerTask;



//Data is streamed into the main activity and the predictions are output to the UI
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG  = "";
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private float[][] results;
    private HARClassifier classifier;
    private TextView walkingTextView;
    private TextView runningTextView;
    private TextView lrbTextView;
    private TextView hrbTextView;

    // Load input test data files
    private static final String ax_file = "X_test_accx.txt";
    private static final String ay_file = "X_test_accy.txt";
    private static final String az_file = "X_test_accz.txt";
    private static final String ppg_file = "X_test_ppg.txt";
    private static final String label_file = "y_test.txt";

    private static float[][] ppg, ax, ay, az, y_test;

    private int i = 0;
    private int accuracy_count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        walkingTextView = findViewById(R.id.walkingTextView);
        runningTextView = findViewById(R.id.runningTextView);
        lrbTextView = findViewById(R.id.lrbTextView);
        hrbTextView = findViewById(R.id.hrbTextView);

        //Call another activity
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, MainActivity2.class));
            }
        });

        //Load required sensors, if used
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);//create instance
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

        //Load pre-collected Test Data into individual channels for easier handling; this is only done when using pre-collected data
        try{
            classifier = new HARClassifier(getApplicationContext()); //try/catch
        } catch (IOException e){
            Log.e("tfliteSupport", "Error reading model", e);
        }
        try {
            ppg = classifier.LoadTest(getApplicationContext(), ppg_file);
            ax = classifier.LoadTest(getApplicationContext(), ax_file);
            ay = classifier.LoadTest(getApplicationContext(), ay_file);
            az = classifier.LoadTest(getApplicationContext(), az_file);
            y_test = classifier.LoadTestlabel(getApplicationContext(), label_file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Stream pre-collected Test Data using a timer to simulate real-time data collection and inference
        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (i == 100) { //If last element of dataset has been input
                    Log.i(TAG,"Dataset is finished, interpreter is closed, timer is stopped");
                    classifier.interpreter.close(); //Close interpreter when finished
                    timer.cancel();
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (i < 100) {
                            long startTime = System.currentTimeMillis();
                            HARPrediction(i); //Make prediction on data input for single window
                            long endTime = System.currentTimeMillis();
                            System.out.println(i + " is " + (endTime - startTime));
                            i++;

                        }
                    }
                });
                //i++;
            }
        },0,8000); //Sets delay and period of timer to stream data in ms


    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void HARPrediction(int i){

        //Segment and Reshape Data into fixed window sizes
        float[][][] input_3d = new float[1][1600][4];

        for (int n = 0; n < 400; n++) {
            for(int k = 0; k < 4; k++){
            input_3d[0][n][k] = ppg[i][4*n+k];
            input_3d[0][n+400][k] = ax[i][4*n+k];
            input_3d[0][n+800][k] = ay[i][4*n+k];
            input_3d[0][n+1200][k] = az[i][4*n+k];
            }
        }

        //Make predictions on input data window in HAR Classifier
        results = classifier.predictions(input_3d);

        //Compare the accuracy
        int maxIndex = 0;
        for(int jj = 0;jj < 3;jj++){
            if(results[0][maxIndex] < results[0][jj+1]){
                maxIndex = jj + 1;
            }
            //System.out.print(i+","+results[0][jj]);
        } //return the max value index
        if(maxIndex == y_test[i][0]){
            accuracy_count = accuracy_count + 1;
        }

        System.out.println(i+","+maxIndex);

        //Output predictions to app UI
        walkingTextView.setText("Walking: \t" + round(results[0][0], 2));
        runningTextView.setText("Running: \t" + round(results[0][1], 2));
        lrbTextView.setText("Low Resistance Biking: \t" + round(results[0][2], 2));
        hrbTextView.setText("High Resistance Biking: \t" + round(results[0][3], 2));

        if(i==99){
            System.out.println("Accurate number is " + accuracy_count);
            //System.out.println("Accuracy is " + accuracy_count*1.0/100);
        }


    }

    //Rounds the output predictions to two decimal places
    private static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

}
