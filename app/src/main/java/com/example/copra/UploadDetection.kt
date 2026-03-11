package com.example.copra

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DetectedAdapter(
    private var items: List<String>
) : RecyclerView.Adapter<DetectedAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgDetected: ImageView = view.findViewById(R.id.imgDetected)
        val tvDetectedGrade: TextView = view.findViewById(R.id.tvDetectedGrade)
        val imgGradeIcon: ImageView = view.findViewById(R.id.imgGradeIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_upload_detected, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val grade = items[position]

        holder.tvDetectedGrade.text = grade

        when (grade) {

            "Grade I" -> {
                holder.imgGradeIcon.setImageResource(R.drawable.check_circle)
                holder.imgGradeIcon.setColorFilter(Color.parseColor("#2E7D32"))
            }

            "Grade II" -> {
                holder.imgGradeIcon.setImageResource(R.drawable.warning__1_)
                holder.imgGradeIcon.setColorFilter(Color.parseColor("#F9A825"))
            }

            "Grade III" -> {
                holder.imgGradeIcon.setImageResource(R.drawable.block__1_)
                holder.imgGradeIcon.setColorFilter(Color.parseColor("#C62828"))
            }
        }
    }

    fun updateData(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }
}