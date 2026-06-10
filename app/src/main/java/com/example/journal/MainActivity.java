package com.example.journal;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.example.journal.R;


import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private TextView tvGreeting;
    private Button btnLogout, btnCreateMemory, btnViewMemories;
    private FirebaseAuth mAuth;

//    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvGreeting = findViewById(R.id.tvGreeting);
        btnLogout = findViewById(R.id.tvLogout);
        btnCreateMemory = findViewById(R.id.btnCreateMemory);
        btnViewMemories = findViewById(R.id.btnViewMemories);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        // If no user signed in, go back to Login
        if (user == null) {
            redirectToLogin();
            return;
        }

        // Set greeting: prefer displayName, otherwise use email prefix
        String display = user.getDisplayName();
        if (display == null || display.trim().isEmpty()) {
            String email = user.getEmail();
            if (email != null && email.contains("@")) {
                display = email.substring(0, email.indexOf('@'));
            } else {
                display = "Traveler";
            }
        }
        tvGreeting.setText("Hello, " + display);

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            redirectToLogin();
        });

        btnCreateMemory.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, CreateMemoryActivity.class);
            startActivity(i);
        });

        btnViewMemories.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, ViewMemoriesActivity.class);
            startActivity(i);
        });
    }

    private void redirectToLogin() {
        Intent i = new Intent(MainActivity.this, Login.class); // or LoginActivity if you renamed it
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}
