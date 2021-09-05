package com.example.har;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

//This classifier performs HAR inference on the data input into the Main Activity
public class HARClassifier {
    //Load instance of interpreter from TFlite Android support library
    protected Interpreter interpreter; //create a specific object interpreter
    //private static final String Model_Name = "tflite_model_quant_fullint.tflite"; //Load model for testing (float-32 or unit-8)
    private static final String Model_Name = "tflite_model.tflite";
    public int[] output_shape;
    public int output_index = 0;

    //Load Model into Interpreter
    public HARClassifier(Context context) throws IOException {
        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, Model_Name);
        Interpreter.Options tfliteOptions = new Interpreter.Options();
        interpreter = new Interpreter(tfliteModel, tfliteOptions);

    }

    public float[][] predictions(float[][][] input_data){
        float[][] results = new float[1][4]; //Output shape of results
        interpreter.run(input_data,results); //Run Interpreter on data input from Main Activity
        output_shape = interpreter.getOutputTensor(output_index).shape(); //Output tensor shape
        return results; //Return prediction results
    }

    public float[][] LoadTest(Context context, String file) throws IOException {
        //Read input data to form 2D array
        BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(file)));
        int rows = 100; //7000
        int columns = 1600;

        String[] line;
        float[][] myArray = new float[rows][columns];

        for (int i = 0; i < rows; i++) {
            line = reader.readLine().trim().split("\\s+");
            if (line == null) {
                reader.close();
            }
            for (int j = 0; j < columns; j++) {
                float k = Float.parseFloat(line[j]);
                myArray[i][j] = k;
            }

        }

        return myArray;
    }
    public float[][] LoadTestlabel(Context context, String file) throws IOException {
        //Read input data to form 2D array
        BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(file)));
        int rows = 100; //8526
        int columns = 1;

        String[] line;
        float[][] myArray = new float[rows][columns];

        for (int i = 0; i < rows; i++) {
            line = reader.readLine().trim().split("\\s+");
            if (line == null) {
                reader.close();
            }
            for (int j = 0; j < columns; j++) {
                float k = Float.parseFloat(line[j]);
                myArray[i][j] = k;
            }

        }
        return myArray;
    }
}
