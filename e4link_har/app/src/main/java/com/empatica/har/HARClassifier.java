package com.empatica.har;

import android.content.Context;

import java.io.IOException;
import java.nio.MappedByteBuffer;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

//This classifier performs HAR inference on the data input into the Main Activity
public class HARClassifier {
    //Load instance of interpreter from TFlite Android support library
    protected Interpreter interpreter; //create a specific object interpreter
    private static final String Model_Name = "tflite_model_quant_fullint.tflite"; //Load model for testing float-32
    //private static final String Model_Name = "tflite_model.tflite"; //Load model for testing float-32
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
        int[] input_ten = interpreter.getInputTensor(output_index).shape(); //Input tensor shape

        return results; //Return prediction results
    }

}
