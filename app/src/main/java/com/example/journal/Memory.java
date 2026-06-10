package com.example.journal;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Firestore-friendly model for a memory.
 * - Public no-arg constructor required by Firestore.
 * - Use @Exclude for runtime-only fields that shouldn't be stored.
 */
public class Memory {

    // Not stored directly in the document (use doc.getId() to set it)
    @Exclude
    public String id;

    public String ownerUid;
    public String ownerName;
    public String description;
    public Timestamp date;            // user-specified date (nullable)
    public List<String> imageUrls;    // list of image download URLs (nullable)
    public Timestamp createdAt;       // when document was created in Firestore

    // runtime-only UI state (do not persist); marked @Exclude so Firestore ignores it
    @Exclude
    public Boolean uploading = false;

    // Required no-arg constructor for Firestore
    public Memory() { }

    // Convenience constructor for creating new Memory before upload
    public Memory(String ownerUid, String ownerName, String description,
                  Timestamp date, List<String> imageUrls, Timestamp createdAt) {
        this.ownerUid = ownerUid;
        this.ownerName = ownerName;
        this.description = description;
        this.date = date;
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.createdAt = createdAt;
        this.uploading = false;
    }

    // Getters and setters (Firestore can use fields directly but getters are handy)
    public String getId() { return id; }
    @Exclude
    public void setId(String id) { this.id = id; } // excluded because id is not stored inside doc itself

    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getDate() { return date; }
    public void setDate(Timestamp date) { this.date = date; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Exclude
    public Boolean isUploading() { return uploading; }
    @Exclude
    public void setUploading(Boolean uploading) { this.uploading = uploading; }
}
