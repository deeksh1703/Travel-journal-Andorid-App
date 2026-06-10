package com.example.journal;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class ImagePreviewAdapter extends RecyclerView.Adapter<ImagePreviewAdapter.VH> {
    private final Context ctx;
    private final List<Uri> items;
    private List<String> existingUrls = new ArrayList<>();
    private List<Uri> localUris;

    public void setExistingImageUrls(List<String> urls) {
        this.existingUrls = new ArrayList<>(urls);
    }

    public void setLocalUris(List<Uri> uris) {
        this.localUris = uris;
    }

    public ImagePreviewAdapter(Context ctx, List<Uri> items) {
        this.ctx = ctx;
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_image_preview, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        if (position < existingUrls.size()) {
            // Existing Firebase image URLs
            String imageUrl = existingUrls.get(position);
            Glide.with(ctx)
                    .load(imageUrl)
                    .centerCrop()
                    .into(holder.img);
        } else {
            // Local image URIs (not yet uploaded)
            Uri uri = items.get(position - existingUrls.size());
            Glide.with(ctx)
                    .load(uri)
                    .centerCrop()
                    .into(holder.img);
        }
    }

    @Override
    public int getItemCount() {
        return existingUrls.size() + items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;

        VH(@NonNull View v) {
            super(v);
            img = v.findViewById(R.id.ivThumb);
        }
    }
}
