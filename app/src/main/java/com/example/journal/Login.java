package com.example.journal;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Login extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnSignup;
    private ProgressDialog progressDialog;
    private FirebaseAuth mAuth;
    private static final String TAG = "FB_TEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // ensure this layout exists

        // init views (IDs must match your activity_login.xml)
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnSignup = findViewById(R.id.btnSignup);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Signing in...");
        progressDialog.setCancelable(false);

        mAuth = FirebaseAuth.getInstance();

        // Quick Firebase init log (safe here)
        FirebaseUser user = mAuth.getCurrentUser();
        Log.d(TAG, "Firebase initialized. Current user: " + (user != null ? user.getEmail() : "null"));

        // If user already signed in, go to Home (MainActivity)
        if (user != null) {
            goToHome();
            return;
        }

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnSignup.setOnClickListener(v -> startActivity(new Intent(Login.this, SignupActivity.class)));
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String pwd = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError("Email required");
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            etEmail.requestFocus();
            return;
        }
        if (pwd.isEmpty() || pwd.length() < 6) {
            etPassword.setError("Password >= 6 chars required");
            etPassword.requestFocus();
            return;
        }

        progressDialog.show();
        mAuth.signInWithEmailAndPassword(email, pwd)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        // signed in -> go to Home
                        goToHome();
                    } else {
                        Toast.makeText(Login.this, "Authentication failed: " + (task.getException() != null ? task.getException().getMessage() : "Unknown"), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToHome() {
        Intent i = new Intent(Login.this, MainActivity.class); // MainActivity = your home screen
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}
