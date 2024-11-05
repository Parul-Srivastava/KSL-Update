package com.example.khasisignlanguage;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.DownloadType;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_SELECT = 2;
    private Bitmap selectedImage;
    private Interpreter tflite;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();

        Log.d(TAG, "Firebase initialized.");

        Button buttonSelect = findViewById(R.id.buttonSelect);
        Button buttonCapture = findViewById(R.id.buttonCapture);
        Button buttonPredict = findViewById(R.id.buttonPredict);
        Button buttonLetterLookup = findViewById(R.id.buttonLetterLookup);
        Button buttonrtp = findViewById(R.id.buttonRealTimePrediction);

        buttonSelect.setOnClickListener(v -> selectImageFromGallery());
        buttonCapture.setOnClickListener(v -> captureImageFromCamera());
        buttonPredict.setOnClickListener(v -> predictSignLanguage());
        buttonLetterLookup.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LetterLookupActivity.class);
            startActivity(intent);
        });
        buttonrtp.setOnClickListener(v -> rtp());

        // Request camera and storage permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }

        // Download the model from Firebase
        CustomModelDownloadConditions conditions = new CustomModelDownloadConditions.Builder()
                .requireWifi()
                .build();
        FirebaseModelDownloader.getInstance()
                .getModel("model", DownloadType.LOCAL_MODEL, conditions)
                .addOnSuccessListener(model -> {
                    Log.d(TAG, "Model downloaded successfully!");
                    // Use the downloaded model to create an interpreter
                    File modelFile = model.getFile();
                    if (modelFile != null) {
                        try {
                            tflite = new Interpreter(modelFile);
                            Log.d(TAG, "Interpreter initialized.");
                        } catch (Exception e) {
                            Log.e(TAG, "Error initializing interpreter: " + e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "Model file is null");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Model download failed: " + e.getMessage()));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE && data != null) {
                Bundle extras = data.getExtras();
                selectedImage = (Bitmap) Objects.requireNonNull(extras).get("data");
            } else if (requestCode == REQUEST_IMAGE_SELECT && data != null) {
                Uri imageUri = data.getData();
                try {
                    selectedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void selectImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_IMAGE_SELECT);
    }

    private void captureImageFromCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void predictSignLanguage() {
        if (selectedImage != null) {
            if (tflite == null) {
                Log.e(TAG, "Model is not initialized");
                Toast.makeText(this, "Model is not downloaded yet", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // Preprocess the image
                Bitmap resizedImage = Bitmap.createScaledBitmap(selectedImage, 224, 224, true);
                ByteBuffer byteBuffer = convertBitmapToByteBuffer(resizedImage);

                // Create input tensor
                TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
                inputFeature0.loadBuffer(byteBuffer);

                // Create output tensor
                TensorBuffer outputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 3}, DataType.FLOAT32);

                // Run inference
                tflite.run(inputFeature0.getBuffer(), outputFeature0.getBuffer().rewind());

                // Get the result
                float[] outputArray = outputFeature0.getFloatArray();
                int predictedIndex = getMaxIndex(outputArray);
                String[] labels = {"A", "D", "E"};
                String predictedLabel = labels[predictedIndex];

                // Display result
                TextView textViewResult = findViewById(R.id.textViewResult);
                textViewResult.setText("Result: " + predictedLabel);

                Log.d(TAG, "Prediction result: " + predictedLabel);

            } catch (Exception e) {
                Log.e(TAG, "Error during model inference: " + e.getMessage());
                e.printStackTrace();
                Toast.makeText(this, "Error during prediction: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Please select or capture an image first", Toast.LENGTH_SHORT).show();
        }
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[224 * 224];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < 224; ++i) {
            for (int j = 0; j < 224; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f); // Red
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);  // Green
                byteBuffer.putFloat((val & 0xFF) / 255.0f);         // Blue
            }
        }

        // Ensure byteBuffer is flipped to be read by TensorFlow Lite
        byteBuffer.rewind();

        Log.d(TAG, "ByteBuffer created with size: " + byteBuffer.remaining());

        return byteBuffer;
    }

    private int getMaxIndex(float[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private void uploadImage() {
        // Convert Bitmap to Uri
        Uri fileUri = getImageUri(selectedImage);

        if (fileUri != null) {
            StorageReference fileRef = storageRef.child("images/" + fileUri.getLastPathSegment());

            fileRef.putFile(fileUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        // Handle success
                        Log.d(TAG, "Image uploaded successfully");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Image upload failed: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private Uri getImageUri(Bitmap bitmap) {
        // Creating a file within the cache directory to store the image
        File file = new File(getCacheDir(), "image.jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create image file: " + e.getMessage());
            return null;
        }

        return Uri.fromFile(file);
    }

    private void rtp() {
        Intent intent = new Intent(MainActivity.this, RealTimePredictionActivity.class);
        startActivity(intent);
    }

}