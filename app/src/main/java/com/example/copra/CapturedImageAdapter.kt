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

        when (item.classificationStatus) {
            ClassificationStatus.PENDING -> {
                holder.label.text = "Classifying..."
                holder.icon.setImageResource(R.drawable.manage_search)
                holder.icon.setColorFilter(Color.parseColor("#757575"))
            }

            ClassificationStatus.FAILED -> {
                holder.label.text = "Classification failed"
                holder.icon.setImageResource(R.drawable.block__1_)
                holder.icon.setColorFilter(Color.parseColor("#757575"))
            }

            ClassificationStatus.READY -> {
                val grade = item.classificationLabel ?: "Unknown"
                val confidence = item.classificationConfidence ?: 0f
                val scorePercent = (confidence * 100).toInt().coerceIn(0, 100)
                holder.label.text = "$grade $scorePercent%"

                when (gradeBucket(grade)) {
                    1 -> {
                        holder.icon.setImageResource(R.drawable.check_circle)
                        holder.icon.setColorFilter(Color.parseColor("#2E7D32"))
                    }

                    2 -> {
                        holder.icon.setImageResource(R.drawable.warning__1_)
                        holder.icon.setColorFilter(Color.parseColor("#F9A825"))
                    }

                    3 -> {
                        holder.icon.setImageResource(R.drawable.block__1_)
                        holder.icon.setColorFilter(Color.parseColor("#C62828"))
                    }

                    else -> {
                        holder.icon.setImageResource(R.drawable.manage_search)
                        holder.icon.setColorFilter(Color.parseColor("#455A64"))
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<CapturedDetection>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun gradeBucket(label: String): Int {
        val normalized = label.trim().lowercase()
        val compact = normalized.replace(Regex("[^a-z0-9]"), "")
        return when {
            normalized.contains("grade iii") || compact.contains("gradeiii") ||
                compact.contains("gradec") || compact == "c" -> 3

            normalized.contains("grade ii") || compact.contains("gradeii") ||
                compact.contains("gradeb") || compact == "b" -> 2

            normalized.contains("grade i") || compact.contains("gradei") ||
                compact.contains("gradea") || compact == "a" -> 1

            else -> 0
        }
    }
}
