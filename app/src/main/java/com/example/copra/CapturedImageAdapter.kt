package com.example.copra

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CapturedImageAdapter(
    private var items: List<CapturedDetection>
) : RecyclerView.Adapter<CapturedImageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imgDetected)
        val label: TextView = view.findViewById(R.id.tvDetectedGrade)
        val icon: ImageView = view.findViewById(R.id.imgGradeIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_upload_detected, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.image.setImageBitmap(item.crop)

        val scorePercent = (item.score * 100).toInt().coerceIn(0, 100)
        holder.label.text = "${item.label} $scorePercent%"

        when {
            item.score >= 0.80f -> {
                holder.icon.setImageResource(R.drawable.check_circle)
                holder.icon.setColorFilter(Color.parseColor("#2E7D32"))
            }
            item.score >= 0.50f -> {
                holder.icon.setImageResource(R.drawable.warning__1_)
                holder.icon.setColorFilter(Color.parseColor("#F9A825"))
            }
            else -> {
                holder.icon.setImageResource(R.drawable.block__1_)
                holder.icon.setColorFilter(Color.parseColor("#C62828"))
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<CapturedDetection>) {
        items = newItems
        notifyDataSetChanged()
    }
}
