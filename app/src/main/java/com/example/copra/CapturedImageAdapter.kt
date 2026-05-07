package com.example.copra

import android.graphics.Color
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class CapturedImageAdapter(
    private var items: List<CapturedDetection>,
    initialSelectedKeys: Set<String> = emptySet(),
    private val onSelectionChanged: ((Set<String>) -> Unit)? = null
) : RecyclerView.Adapter<CapturedImageAdapter.ViewHolder>() {

    private val selectedItemKeys = LinkedHashSet(initialSelectedKeys)

    companion object {
        fun selectionKeyForRect(rect: RectF): String {
            return listOf(rect.left, rect.top, rect.right, rect.bottom)
                .joinToString("|") { coordinate -> "%.2f".format(java.util.Locale.US, coordinate) }
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val image: ImageView = view.findViewById(R.id.imgDetected)
        val label: TextView = view.findViewById(R.id.tvDetectedGrade)
        val model: TextView = view.findViewById(R.id.tvDetectedModel)
        val latency: TextView = view.findViewById(R.id.tvDetectedLatency)
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
        val itemKey = item.selectionKey()
        val isSelected = selectedItemKeys.contains(itemKey)
        holder.card.strokeWidth = if (isSelected) 3 else 1
        holder.card.strokeColor = if (isSelected) {
            Color.parseColor("#AE7049")
        } else {
            Color.parseColor("#E0E0E0")
        }

        when (item.classificationStatus) {
            ClassificationStatus.PENDING -> {
                holder.label.text = "Classifying..."
                holder.latency.text = "Latency: waiting..."
                holder.icon.setImageResource(R.drawable.manage_search)
                holder.icon.setColorFilter(Color.parseColor("#757575"))
            }

            ClassificationStatus.FAILED -> {
                holder.label.text = "Classification failed"
                holder.latency.text = "Latency: unavailable"
                holder.icon.setImageResource(R.drawable.block__1_)
                holder.icon.setColorFilter(Color.parseColor("#757575"))
            }

            ClassificationStatus.READY -> {
                val grade = item.classificationLabel ?: "Unknown"
                val confidence = item.classificationConfidence ?: 0f
                val scorePercent = (confidence * 100).toInt().coerceIn(0, 100)
                holder.label.text = "$grade $scorePercent%"
                holder.latency.text = formatLatency(item.classificationMs)

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

        holder.model.text = item.classificationModelName ?: "Model not recorded"
        holder.itemView.setOnClickListener {
            val changed = if (selectedItemKeys.contains(itemKey)) {
                selectedItemKeys.remove(itemKey)
                true
            } else {
                selectedItemKeys.add(itemKey)
                true
            }

            if (changed) {
                notifyItemChanged(position)
                onSelectionChanged?.invoke(selectedItemKeys.toSet())
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<CapturedDetection>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun clearSelection() {
        if (selectedItemKeys.isEmpty()) return
        selectedItemKeys.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(emptySet())
    }

    fun hasSelection(): Boolean = selectedItemKeys.isNotEmpty()

    private fun CapturedDetection.selectionKey(): String = selectionKeyForRect(sourceRect)

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

    private fun formatLatency(value: Long?): String {
        return if (value == null || value <= 0L) {
            "Latency: unavailable"
        } else {
            "Latency: ${value} ms"
        }
    }
}
