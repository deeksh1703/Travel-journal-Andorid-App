package com.example.journal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.VH> {

    public interface Callback {
        void onEdit(Memory memory);
        void onDelete(Memory memory);
    }

    private final Context ctx;
    private final List<Memory> items;
    private final Callback cb;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public MemoryAdapter(Context ctx, List<Memory> items, Callback cb) {
        this.ctx = ctx;
        this.items = items;
        this.cb = cb;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_memory, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Memory m = items.get(position);

        // date
        if (m.date != null) {
            Timestamp t = m.date;
            try {
                holder.tvDate.setText(sdf.format(t.toDate()));
            } catch (Exception e) {
                holder.tvDate.setText("");
            }
        } else {
            holder.tvDate.setText("");
        }

        holder.tvDesc.setText(m.description != null ? m.description : "");

        // thumbnails: show up to 3
        holder.iv1.setVisibility(View.GONE);
        holder.iv2.setVisibility(View.GONE);
        holder.iv3.setVisibility(View.GONE);

        if (m.imageUrls != null && !m.imageUrls.isEmpty()) {
            if (m.imageUrls.size() > 0) {
                holder.iv1.setVisibility(View.VISIBLE);
                Glide.with(ctx)
                        .load(m.imageUrls.get(0))
                        .centerCrop()
                        .placeholder(R.drawable.placeholder)
                        .error(R.drawable.placeholder)
                        .into(holder.iv1);
            }
            if (m.imageUrls.size() > 1) {
                holder.iv2.setVisibility(View.VISIBLE);
                Glide.with(ctx)
                        .load(m.imageUrls.get(1))
                        .centerCrop()
                        .placeholder(R.drawable.placeholder)
                        .error(R.drawable.placeholder)
                        .into(holder.iv2);
            }
            if (m.imageUrls.size() > 2) {
                holder.iv3.setVisibility(View.VISIBLE);
                Glide.with(ctx)
                        .load(m.imageUrls.get(2))
                        .centerCrop()
                        .placeholder(R.drawable.placeholder)
                        .error(R.drawable.placeholder)
                        .into(holder.iv3);
            }
        }

        // safe callback calls
        holder.btnEdit.setOnClickListener(v -> {
            if (cb != null) cb.onEdit(m);
        });
        holder.btnDelete.setOnClickListener(v -> {
            if (cb != null) cb.onDelete(m);
        });

        // optional: click whole item
        holder.itemView.setOnClickListener(v -> {
            // e.g. open details — add callback if needed
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDate, tvDesc;
        ImageView iv1, iv2, iv3;
        MaterialButton btnEdit, btnDelete;

        VH(@NonNull View v) {
            super(v);
            tvDate = v.findViewById(R.id.tvDateItem);
            tvDesc = v.findViewById(R.id.tvDescriptionItem);
            iv1 = v.findViewById(R.id.ivThumb1);
            iv2 = v.findViewById(R.id.ivThumb2);
            iv3 = v.findViewById(R.id.ivThumb3);
            btnEdit = v.findViewById(R.id.btnEdit);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}
