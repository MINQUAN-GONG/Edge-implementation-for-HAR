# Edge-implementation-for-HAR
This is a project regarding edge implementation for human activity recognition (HAR).
Built offline models (CNN and LSTM) is in 'Offline_models' folder. 
The convertion and interpreter codes can be directly seen in the main interfacne. 
Two quantised models using differnet quantisaion strategies (full integer with float fall back, and full integer only) are generated, namely 'tflite_model_quant_fullint', and 'tflite_model_quant_uint8' (which can be seen in 'TFlite_models' folder). 
The code of the deployment of these two models are in 'Android_HAR_app' folder. 
'e4link_har' folder contains the code of the final real-time CNN model calssifier application.
