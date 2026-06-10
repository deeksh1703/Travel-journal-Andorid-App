package com.example.journal;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import android.widget.Button;
import android.widget.EditText;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Clean, simplified CreateMemoryActivity:
 * - pick images via SAF (OpenMultipleDocuments)
 * - upload to Firebase Storage and store download URLs in Firestore
 * - supports edit mode via EXTRA_MEMORY_ID (loads existing imageUrls into adapter)
 */
public class CreateMemoryActivity extends AppCompatActivity {
    private static final String TAG = "CreateMemoryActivity";
    public static final String EXTRA_MEMORY_ID = "EXTRA_MEMORY_ID";

    // UI
    private Button btnPickImages, btnSave;
    private TextView tvDate;
    private EditText etDescription;
    private RecyclerView rvImagePreview;
    private ImagePreviewAdapter adapter;

    // State
    private final List<Uri> selectedUris = new ArrayList<>();     // newly chosen local URIs
    private final List<String> existingImageUrls = new ArrayList<>(); // for edit mode

    // simple progress UI
    private AlertDialog progressDialog;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private StorageReference storageRoot;

    // ActivityResult launcher for OpenMultipleDocuments
    private ActivityResultLauncher<String[]> pickImagesLauncher;

    // edit mode
    private boolean isEditMode = false;
    private String editingMemoryId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_memory);

        // Firebase init
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storageRoot = FirebaseStorage.getInstance().getReference();

        // UI init
        btnPickImages = findViewById(R.id.btnAddPhotos);
        btnSave = findViewById(R.id.btnSave);
        tvDate = findViewById(R.id.tvDate);
        etDescription = findViewById(R.id.etDescription);
        rvImagePreview = findViewById(R.id.rvImagePreview);

        adapter = new ImagePreviewAdapter(this, selectedUris);
        adapter.setExistingImageUrls(existingImageUrls);
        rvImagePreview.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvImagePreview.setAdapter(adapter);

        progressDialog = buildProgressDialog();

        // default date = today
        Calendar c = Calendar.getInstance();
        setDateText(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));

        tvDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new android.app.DatePickerDialog(CreateMemoryActivity.this,
                    (view, year, month, dayOfMonth) -> setDateText(year, month, dayOfMonth),
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        // register SAF multiple docs picker
        pickImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris == null || uris.isEmpty()) {
                        Log.d(TAG, "No images selected");
                        return;
                    }
                    int before = selectedUris.size();
                    selectedUris.clear();
                    selectedUris.addAll(uris);
                    for (Uri u : uris) {
                        try {
                            getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ex) {
                            Log.w(TAG, "persist permission failed for " + u, ex);
                        }
                    }
                    if (before == 0) adapter.notifyItemRangeInserted(0, selectedUris.size());
                    else adapter.notifyDataSetChanged();
                });

        btnPickImages.setOnClickListener(v -> pickImages());
        btnSave.setOnClickListener(v -> saveMemory());

        // check edit mode
        Intent it = getIntent();
        if (it != null && it.hasExtra(EXTRA_MEMORY_ID)) {
            isEditMode = true;
            editingMemoryId = it.getStringExtra(EXTRA_MEMORY_ID);
            loadMemoryForEdit(editingMemoryId);
        }
    }

    private AlertDialog buildProgressDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        ProgressBar pb = new ProgressBar(this);
        TextView tv = new TextView(this);
        tv.setText("Working...");
        container.addView(pb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.setView(container);
        b.setCancelable(false);
        return b.create();
    }

    private void pickImages() {
        String[] mime = new String[]{"image/*"};
        pickImagesLauncher.launch(mime);
    }

    private void setDateText(int year, int month, int dayOfMonth) {
        String s = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
        tvDate.setText(s);
    }

    private void loadMemoryForEdit(String memoryId) {
        progressDialog.show();

        db.collection("memories").document(memoryId)
                .get()
                .addOnSuccessListener(doc -> {
                    progressDialog.dismiss();
                    if (doc == null || !doc.exists()) {
                        Toast.makeText(this, "Memory not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    Object desc = doc.get("description");
                    if (desc != null) etDescription.setText(desc.toString());

                    Object dateObj = doc.get("date");
                    if (dateObj instanceof Timestamp) {
                        Date d = ((Timestamp) dateObj).toDate();
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(d);
                        setDateText(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                    }

                    existingImageUrls.clear();
                    Object iv = doc.get("imageUrls");
                    if (iv instanceof List) {
                        List<?> list = (List<?>) iv;
                        for (Object o : list) if (o instanceof String) existingImageUrls.add((String) o);
                    }

                    adapter.setExistingImageUrls(existingImageUrls);
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Log.e(TAG, "loadMemoryForEdit failed", e);
                    Toast.makeText(this, "Failed to load memory: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Upload newly selected images (if any), collect download URLs, and write Firestore doc.
     */
    private void saveMemory() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show();
            return;
        }

        String desc = etDescription.getText().toString().trim();
        if (desc.isEmpty()) {
            etDescription.setError("Description required");
            etDescription.requestFocus();
            return;
        }

        String dateStr = tvDate.getText().toString();
        String[] parts = dateStr.split("-");
        int y = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]) - 1;
        int d = Integer.parseInt(parts[2]);
        Calendar cal = Calendar.getInstance();
        cal.set(y, m, d, 0, 0, 0);
        Date chosenDate = cal.getTime();
        final Timestamp dateTimestamp = new Timestamp(chosenDate);

        final String memoryId = isEditMode ? editingMemoryId : db.collection("memories").document().getId();
        final String uid = user.getUid();
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            String email = user.getEmail();
            displayName = (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : "Traveler";
        }
        final String finalDisplayName = displayName;

        // If no images selected -> write doc directly
        if (selectedUris.isEmpty()) {
            List<String> finalImageUrls = new ArrayList<>(existingImageUrls);
            Map<String, Object> doc = new HashMap<>();
            doc.put("ownerUid", uid);
            doc.put("ownerName", finalDisplayName);
            doc.put("description", desc);
            doc.put("date", dateTimestamp);
            doc.put("imageUrls", finalImageUrls);
            doc.put("createdAt", FieldValue.serverTimestamp());
            doc.put("uploading", false);

            db.collection("memories").document(memoryId)
                    .set(doc)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Memory saved", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "firestore set failed", e);
                        Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
            return;
        }

        // Upload images
        progressDialog.show();

        List<Task<Uri>> urlTasks = new ArrayList<>();
        for (Uri localUri : new ArrayList<>(selectedUris)) {
            final Uri uploadUri = localUri;

            try (InputStream in = getContentResolver().openInputStream(uploadUri)) {
                if (in == null) {
                    Log.e(TAG, "Cannot open InputStream for URI: " + uploadUri);
                    Toast.makeText(this, "Cannot read selected image, please re-select it.", Toast.LENGTH_LONG).show();
                    continue; // skip this URI
                }
            } catch (Exception ex) {
                Log.e(TAG, "openInputStream failed for URI: " + uploadUri, ex);
                Toast.makeText(this, "Cannot read selected image, please re-select it.", Toast.LENGTH_LONG).show();
                continue; // skip this URI
            }


            final String extension = getFileExtension(uploadUri);
            final String filename = "img_" + UUID.randomUUID().toString() + "." + extension;
            final StorageReference imgRef = storageRoot.child("users").child(uid).child("memories").child(memoryId).child(filename);

            UploadTask putTask = imgRef.putFile(uploadUri);

            putTask.addOnFailureListener(e -> {
                Log.e(TAG, "putFile failed for " + uploadUri + " -> " + imgRef.getPath(), e);
                runOnUiThread(() -> Toast.makeText(CreateMemoryActivity.this, "Upload failed for a file: " + e.getMessage(), Toast.LENGTH_LONG).show());
            });

            putTask.addOnProgressListener(snapshot -> {
                long transferred = snapshot.getBytesTransferred();
                long total = snapshot.getTotalByteCount();
                int p = (total > 0) ? (int) ((transferred * 100) / total) : 0;
                try { progressDialog.setTitle("Uploading " + p + "%"); } catch (Exception ignored) {}
            });

            Task<Uri> urlTask = putTask.continueWithTask((Continuation<UploadTask.TaskSnapshot, Task<Uri>>) task -> {
                if (!task.isSuccessful()) {
                    Exception ex = task.getException();
                    Log.e(TAG, "Upload task not successful for " + uploadUri, ex);
                    throw ex;
                }
                return imgRef.getDownloadUrl();
            });
            urlTasks.add(urlTask);
        }

        Tasks.whenAllSuccess(urlTasks)
                .addOnSuccessListener(results -> {
                    List<String> downloadUrls = new ArrayList<>();
                    for (Object o : results) {
                        if (o instanceof Uri) downloadUrls.add(((Uri) o).toString());
                    }

                    List<String> finalImageUrls = new ArrayList<>(existingImageUrls);
                    finalImageUrls.addAll(downloadUrls);

                    Map<String, Object> doc = new HashMap<>();
                    doc.put("ownerUid", uid);
                    doc.put("ownerName", finalDisplayName);
                    doc.put("description", desc);
                    doc.put("date", dateTimestamp);
                    doc.put("imageUrls", finalImageUrls);
                    doc.put("createdAt", FieldValue.serverTimestamp());
                    doc.put("uploading", false);

                    db.collection("memories").document(memoryId)
                            .set(doc)
                            .addOnSuccessListener(aVoid -> {
                                progressDialog.dismiss();
                                Toast.makeText(CreateMemoryActivity.this, "Memory saved with images", Toast.LENGTH_SHORT).show();
                                selectedUris.clear();
                                adapter.notifyDataSetChanged();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                progressDialog.dismiss();
                                Log.e(TAG, "firestore set failed after uploads", e);
                                Toast.makeText(CreateMemoryActivity.this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Log.e(TAG, "one of the upload tasks failed", e);
                    Toast.makeText(CreateMemoryActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // get extension from content resolver mime type
    private String getFileExtension(@NonNull Uri uri) {
        String ext = "jpg";
        try {
            ContentResolver cr = getContentResolver();
            String type = cr.getType(uri);
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            if (type != null) {
                String e = mime.getExtensionFromMimeType(type);
                if (e != null) ext = e;
            } else {
                String path = uri.getPath();
                if (path != null && path.contains(".")) {
                    String p = path.substring(path.lastIndexOf('.') + 1);
                    if (p.length() <= 8) ext = p;
                }
            }
        } catch (Exception ignored) {}
        return ext;
    }
}
