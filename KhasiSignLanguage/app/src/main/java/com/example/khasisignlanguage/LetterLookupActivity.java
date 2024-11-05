package com.example.khasisignlanguage;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LetterLookupActivity extends AppCompatActivity {

    private EditText editTextLetter;
    private ImageView imageViewLetter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_letter_lookup);

        // Set up window insets handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI elements
        editTextLetter = findViewById(R.id.editTextLetter);
        imageViewLetter = findViewById(R.id.imageViewLetter);
        Button buttonShow = findViewById(R.id.buttonShow);

        // Set onClickListener for the Show button
        buttonShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String letter = editTextLetter.getText().toString().trim().toLowerCase();
                letter= letter.toUpperCase();

                // Determine which image to display based on the entered letter
                switch (letter) {
                    case "A":
                        imageViewLetter.setImageResource(R.drawable.letter_a); // Replace with your actual image resource
                        break;
                    case "D":
                        imageViewLetter.setImageResource(R.drawable.letter_d);
                        break;
                    // Add cases for other letters as needed
                    case "E":
                        imageViewLetter.setImageResource(R.drawable.letter_e);
                        break;
                    default:
                        imageViewLetter.setImageResource(android.R.color.transparent); // Clear the image if no match
                        break;
                }
            }
        });
    }
}