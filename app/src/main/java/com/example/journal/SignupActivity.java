package com.example.journal;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SignupActivity extends AppCompatActivity {

    private EditText etEmail, etPassword, etConfirm;
    private Button btnCreate;
    private ProgressDialog progressDialog;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirm = findViewById(R.id.etConfirm);
        btnCreate = findViewById(R.id.btnCreate);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Creating account...");
        progressDialog.setCancelable(false);

        mAuth = FirebaseAuth.getInstance();

        btnCreate.setOnClickListener(v -> createAccount());
    }

    private void createAccount() {
        String email = etEmail.getText().toString().trim();
        String pwd = etPassword.getText().toString().trim();
        String confirm = etConfirm.getText().toString().trim();

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
        if (pwd.length() < 6) {
            etPassword.setError("Password must be >= 6 characters");
            etPassword.requestFocus();
            return;
        }
        if (!pwd.equals(confirm)) {
            etConfirm.setError("Passwords do not match");
            etConfirm.requestFocus();
            return;
        }

        progressDialog.show();
        mAuth.createUserWithEmailAndPassword(email, pwd)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        // account created - go to MainActivity
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(SignupActivity.this, "Account created: " + (user != null ? user.getEmail() : ""), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SignupActivity.this, Login.class));
                        finish();
                    } else {
                        Toast.makeText(SignupActivity.this, "Signup failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
