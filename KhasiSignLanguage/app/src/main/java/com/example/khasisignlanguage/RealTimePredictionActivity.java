package com.example.khasisignlanguage;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RealTimePredictionActivity extends Activity implements TextureView.SurfaceTextureListener {

    private TextureView textureView;
    private Camera camera;
    private int currentCameraId;
    private int frontCameraId;
    private int backCameraId;
    private Interpreter tflite;
    private TextView predictionTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_real_time_prediction);  // Ensure the correct layout is set

        textureView = findViewById(R.id.cameraView);
        textureView.setSurfaceTextureListener(this);
        predictionTextView = findViewById(R.id.predictionTextView);

        frontCameraId = findFrontCamera();
        backCameraId = findBackCamera();
        currentCameraId = backCameraId; // Start with the back camera

        // Load the TensorFlow Lite model
        try {
            tflite = new Interpreter(Utils.loadModelFile(this, "model.tflite"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Switch Camera Button
        Button switchCameraButton = findViewById(R.id.switchCameraButton);
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });

        // Request camera permissions
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
    }

    private int findFrontCamera() {
        int cameraId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    private int findBackCamera() {
        int cameraId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    private void switchCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }

        // Switch camera
        if (currentCameraId == backCameraId) {
            currentCameraId = frontCameraId;
        } else {
            currentCameraId = backCameraId;
        }

        openCamera(currentCameraId);
    }

    private void openCamera(int cameraId) {
        try {
            camera = Camera.open(cameraId);
            setCameraDisplayOrientation(this, cameraId, camera);
            camera.setPreviewTexture(textureView.getSurfaceTexture());
            camera.startPreview();
            camera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    Bitmap bitmap = convertByteArrayToBitmap(data, camera);
                    new PredictSignLanguageTask().execute(bitmap);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera(currentCameraId);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    // Convert byte[] to Bitmap
    private Bitmap convertByteArrayToBitmap(byte[] data, Camera camera) {
        // Your implementation to convert the camera data into a Bitmap
        return null; // Implement this method
    }

    // AsyncTask for running prediction
    private class PredictSignLanguageTask extends AsyncTask<Bitmap, Void, String> {
        @Override
        protected String doInBackground(Bitmap... bitmaps) {
            Bitmap bitmap = bitmaps[0];
            return predictSignLanguage(bitmap);
        }

        @Override
        protected void onPostExecute(String prediction) {
            predictionTextView.setText(prediction);
        }
    }

    private String predictSignLanguage(Bitmap bitmap) {
        // Preprocess the bitmap to match the model input dimensions
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[224 * 224];
        resizedBitmap.getPixels(pixels, 0, 224, 0, 0, 224, 224);

        for (int pixel : pixels) {
            byteBuffer.putFloat((pixel >> 16 & 0xFF) / 255.0f); // Red
            byteBuffer.putFloat((pixel >> 8 & 0xFF) / 255.0f);  // Green
            byteBuffer.putFloat((pixel & 0xFF) / 255.0f);       // Blue
        }

        // Run the model inference
        float[][] output = new float[1][3]; // Assume 10 possible sign classes
        tflite.run(byteBuffer, output);

        // Find the class with the highest confidence
        int predictedClass = -1;
        float highestConfidence = -1;
        for (int i = 0; i < output[0].length; i++) {
            if (output[0][i] > highestConfidence) {
                highestConfidence = output[0][i];
                predictedClass = i;
            }
        }

        // Map predictedClass to sign language label (adjust based on your model)
        String[] signLabels = {"A", "D", "E"};
        return signLabels[predictedClass];
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (textureView.isAvailable()) {
                openCamera(currentCameraId);
            }
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
        }
    }
}
