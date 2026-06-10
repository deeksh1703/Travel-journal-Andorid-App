package com.example.journal;

//import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

//import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
//import java.util.Map;

public class ViewMemoriesActivity extends AppCompatActivity implements MemoryAdapter.Callback {

    private static final String TAG = "ViewMemories";
    private RecyclerView rv;
    private MemoryAdapter adapter;
    private List<Memory> items = new ArrayList<>();

    public Boolean uploading;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_memories);

        rv = findViewById(R.id.rvMemories);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MemoryAdapter(this, items, this);
        rv.setAdapter(adapter);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadMemories();
    }

    private void loadMemories() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String uid = user.getUid();

        db.collection("memories")
                .whereEqualTo("ownerUid", uid)
                .orderBy("date", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "listen failed", error);
                        return;
                    }
                    items.clear();
                    if (snapshot != null) {
                        for (var d : snapshot.getDocuments()) {
                            Memory m = d.toObject(Memory.class);
                            if (m == null) continue;
                            m.id = d.getId();
                            Boolean uploading = d.getBoolean("uploading");
                            m.uploading = uploading != null ? uploading : false;
                            items.add(m);
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }


    // Edit -> open CreateMemoryActivity in EDIT mode. We'll pass the memory id.
    @Override
    public void onEdit(Memory memory) {
        // You need to implement edit handling inside CreateMemoryActivity:
        // - When launched with EXTRA_MEMORY_ID, load the doc and prefill UI,
        // - Allow changing description/date/images (upload new ones, delete removed ones),
        // - Update Firestore doc instead of creating new.
        Intent i = new Intent(this, CreateMemoryActivity.class);
        i.putExtra("EXTRA_MEMORY_ID", memory.id);
        startActivity(i);
    }

    // Delete -> confirm, then delete storage files and doc
    @Override
    public void onDelete(Memory memory) {
        new AlertDialog.Builder(this)
                .setTitle("Delete memory")
                .setMessage("Are you sure you want to delete this memory?")
                .setPositiveButton("Delete", (dialog, which) -> performDelete(memory))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performDelete(Memory memory) {
        if (memory == null || memory.id == null) return;
        // delete files from Storage if present
        List<String> urls = memory.imageUrls;
        if (urls == null || urls.isEmpty()) {
            // just delete doc
            db.collection("memories").document(memory.id).delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                        loadMemories();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            return;
        }

        // If images present, delete each storage object by reference.
        // We assume urls are full download URLs. We can remove by getting reference from URL.
        List<com.google.android.gms.tasks.Task<Void>> deleteTasks = new ArrayList<>();
        for (String url : urls) {
            try {
                StorageReference ref = FirebaseStorage.getInstance().getReferenceFromUrl(url);
                deleteTasks.add(ref.delete());
            } catch (Exception ex) {
                Log.w(TAG, "Failed creating ref for url: " + url, ex);
            }
        }
        // When all deletes finish, delete doc
        com.google.android.gms.tasks.Tasks.whenAll(deleteTasks)
                .addOnSuccessListener(v -> {
                    db.collection("memories").document(memory.id).delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Memory deleted", Toast.LENGTH_SHORT).show();
                                loadMemories();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete images: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // refresh in case user edited something
//        loadMemories();

    }
}
