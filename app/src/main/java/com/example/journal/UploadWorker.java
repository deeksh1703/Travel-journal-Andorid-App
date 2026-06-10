package com.example.journal;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Robust UploadWorker:
 * - uploads filePaths[] to storage
 * - merges newly uploaded URLs with existing (minus removed)
 * - deletes removed storage objects (best-effort)
 * - updates Firestore doc.imageUrls and uploading=false
 */
public class UploadWorker extends Worker {
    private static final String TAG = "UploadWorker";

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String memoryId = getInputData().getString("memoryId");
        String[] filePaths = getInputData().getStringArray("filePaths");
        String ownerUid = getInputData().getString("ownerUid");
        String[] removedArr = getInputData().getStringArray("removedUrls");

        if (memoryId == null) {
            Log.e(TAG, "Missing memoryId (input data)");
            return Result.failure();
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseStorage storage = FirebaseStorage.getInstance();

        try {
            // 1) Read existing imageUrls from Firestore
            List<String> existingUrls = new ArrayList<>();
            DocumentSnapshot snap = Tasks.await(db.collection("memories").document(memoryId).get());
            if (snap != null && snap.exists()) {
                Object iv = snap.get("imageUrls");
                if (iv instanceof List) {
                    for (Object o : (List<?>) iv) {
                        if (o instanceof String) existingUrls.add((String) o);
                    }
                }
            } else {
                Log.w(TAG, "Firestore doc not found or empty for memoryId=" + memoryId);
            }

            // Build set of URLs to remove (if provided)
            HashSet<String> removedSet = new HashSet<>();
            if (removedArr != null) {
                for (String r : removedArr) if (r != null) removedSet.add(r);
            }

            // 2) Upload each provided temp file and collect download URLs
            List<String> uploadedUrls = new ArrayList<>();
            if (filePaths != null && filePaths.length > 0) {
                for (String p : filePaths) {
                    if (p == null) continue;
                    File f = new File(p);
                    if (!f.exists()) {
                        Log.w(TAG, "Temp file missing: " + p);
                        continue;
                    }
                    Uri fileUri = Uri.fromFile(f);
                    String fileName = f.getName();

                    StorageReference ref;
                    if (ownerUid != null && !ownerUid.isEmpty()) {
                        ref = storage.getReference().child("users").child(ownerUid)
                                .child("memories").child(memoryId).child(fileName);
                    } else {
                        ref = storage.getReference().child("memories").child(memoryId).child(fileName);
                    }

                    Log.d(TAG, "Starting upload for tmp file: " + p + " -> storagePath=" + ref.getPath());

                    // Use continueWithTask to chain upload -> getDownloadUrl, then await the result
                    UploadTask uploadTask = ref.putFile(fileUri);

                    // Wait for upload completion (Tasks.await blocks here inside Worker)
                    Tasks.await(uploadTask);

                    if (!uploadTask.isSuccessful()) {
                        Exception ex = uploadTask.getException();
                        Log.e(TAG, "Upload failed for " + p + " (putFile failed)", ex);
                        // If upload failed due to transient reasons, ask WorkManager to retry
                        return Result.retry();
                    }

                    // Now get the download URL (await)
                    try {
                        Uri downloadUri = Tasks.await(ref.getDownloadUrl());
                        if (downloadUri != null) {
                            uploadedUrls.add(downloadUri.toString());
                            Log.d(TAG, "Uploaded " + p + " -> " + downloadUri.toString());
                        } else {
                            Log.e(TAG, "getDownloadUrl returned null for " + ref.getPath());
                            return Result.retry();
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "Failed to getDownloadUrl for " + ref.getPath(), ex);
                        // treat as retryable
                        return Result.retry();
                    }
                }
            } else {
                Log.d(TAG, "No filePaths to upload");
            }

            // 3) Compute final imageUrls: existing (minus removed) + newly uploaded
            List<String> finalUrls = new ArrayList<>();
            for (String u : existingUrls) {
                if (!removedSet.contains(u)) finalUrls.add(u);
            }
            finalUrls.addAll(uploadedUrls);

            // 4) Delete storage objects for removed URLs (best-effort)
            if (!removedSet.isEmpty()) {
                for (String url : removedSet) {
                    try {
                        StorageReference r = storage.getReferenceFromUrl(url);
                        Tasks.await(r.delete());
                        Log.d(TAG, "Deleted removed storage object: " + url);
                    } catch (IllegalArgumentException iae) {
                        // getReferenceFromUrl throws if URL is invalid format; log and continue
                        Log.w(TAG, "Invalid URL passed to getReferenceFromUrl: " + url, iae);
                    } catch (Exception ex) {
                        Log.w(TAG, "Failed to delete removed url: " + url, ex);
                        // do not block the update because of delete failures
                    }
                }
            }

            // 5) Update Firestore doc (set imageUrls and clear uploading flag)
            Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("imageUrls", finalUrls);
            updates.put("uploading", false);
            updates.put("updatedAt", FieldValue.serverTimestamp());

            Tasks.await(db.collection("memories").document(memoryId).update(updates));
            Log.d(TAG, "Firestore doc updated for memoryId=" + memoryId);

            // 6) Cleanup temp files (best-effort)
            if (filePaths != null) {
                for (String p : filePaths) {
                    try {
                        if (p == null) continue;
                        File f = new File(p);
                        if (f.exists()) {
                            boolean ok = f.delete();
                            Log.d(TAG, "Deleted tmp " + p + " -> " + ok);
                        }
                    } catch (Exception ex) {
                        Log.w(TAG, "Failed delete tmp " + p, ex);
                    }
                }
            }

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Worker failed for memoryId=" + memoryId, e);
            // treat as retryable so WorkManager can re-run on transient errors (network etc)
            return Result.retry();
        }
    }
}
