package com.example.har;

import androidx.appcompat.app.AppCompatActivity;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Timer;
import java.util.TimerTask;


//Data is streamed into the main activity and the predictions are output to the UI
public class MainActivity2 extends AppCompatActivity implements SensorEventListener {

    private static final String TAG  = "";
    //private SensorManager mSensorManager;
    private byte[][] results;
    private float[][] results_new = new float[1][4];
    private HARClassifier_int classifier;

    private TextView walkingTextView_uint8;
    private TextView runningTextView_uint8;
    private TextView lrbTextView_uint8;
    private TextView hrbTextView_uint8;

    private static final String ax_file = "X_test_accx_uint8.txt";
    private static final String ay_file = "X_test_accy_uint8.txt";
    private static final String az_file = "X_test_accz_uint8.txt";
    private static final String ppg_file = "X_test_ppg_uint8.txt";
    private static final String label_file = "y_test_uint8.txt";

    private static float[][] ppg, ax, ay, az, y_test;

    private int i = 0;
    private int accuracy_count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        walkingTextView_uint8 = findViewById(R.id.walkingTextView1);
        runningTextView_uint8 = findViewById(R.id.runningTextView1);
        lrbTextView_uint8 = findViewById(R.id.lrbTextView1);
        hrbTextView_uint8 = findViewById(R.id.hrbTextView1);


        //Load pre-collected Test Data into individual channels for easier handling; this is only done when using pre-collected data
        try{
            classifier = new HARClassifier_int(getApplicationContext()); //try/catch
        } catch (IOException e){
            Log.e("tfliteSupport", "Error reading model", e);
        }
        try {
            ppg = classifier.LoadTest_int(getApplicationContext(), ppg_file);
            ax = classifier.LoadTest_int(getApplicationContext(), ax_file);
            ay = classifier.LoadTest_int(getApplicationContext(), ay_file);
            az = classifier.LoadTest_int(getApplicationContext(), az_file);
            y_test = classifier.LoadTestlabel_int(getApplicationContext(), label_file);
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

                            HARPrediction_int(i); //Make prediction on data input for single window
                            i++;

                        }
                    }
                });
                //i++;
            }
        },0,8000); //Sets delay and period of timer to stream data in ms (one window of data is 8s, suggest to decrease for faster testing)


    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override  //this is only called when this i value changes
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void HARPrediction_int(int i){

        //Segment and Reshape Data into fixed window sizes
        byte[][][] input_3d = new byte[1][1600][4];

        for (int n = 0; n < 400; n++) {
            for(int k = 0; k < 4; k++){
                input_3d[0][n][k] = (byte) ppg[i][4*n+k];
                input_3d[0][n+400][k] = (byte) ax[i][4*n+k];
                input_3d[0][n+800][k] = (byte) ay[i][4*n+k];
                input_3d[0][n+1200][k] = (byte) az[i][4*n+k];
            }
        }

        //Make predictions on input data window in HAR Classifier
        results = classifier.predictions_int(input_3d);

        for(int kk = 0;kk<4;kk++){
            results_new[0][kk] = results[0][kk] & 0xff;
        }

        //Compare the accuracy
        int maxIndex = 0;
        for(int jj = 0;jj < 3;jj++){
            if(results_new[0][maxIndex] < results_new[0][jj+1]){
                maxIndex = jj + 1;
            }
        } //return the max value index
        if(maxIndex == y_test[i][0]){
            accuracy_count = accuracy_count + 1;
        }

        System.out.println(i+","+maxIndex);

        //Output predictions to app UI
        walkingTextView_uint8.setText("Walking: \t" + round(results_new[0][0]/255, 2));
        runningTextView_uint8.setText("Running: \t" + round(results_new[0][1]/255, 2));
        lrbTextView_uint8.setText("Low Resistance Biking: \t" + round(results_new[0][2]/255, 2));
        hrbTextView_uint8.setText("High Resistance Biking: \t" + round(results_new[0][3]/255, 2));

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
