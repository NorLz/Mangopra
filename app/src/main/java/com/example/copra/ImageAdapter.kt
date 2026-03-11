package com.example.copra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

class ImageAdapter(private val images: List<Int>) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imgItem)
        val gradeText: TextView = view.findViewById(R.id.tvGrade)
        val gradeIcon: ImageView = view.findViewById(R.id.imgGradeIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun getItemCount() = images.size

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {

        holder.image.setImageResource(images[position])

        // Random Grade
        val grades = listOf("Grade I", "Grade II", "Grade III")
        val randomGrade = grades[Random.nextInt(grades.size)]

        holder.gradeText.text = randomGrade

        when (randomGrade) {
            "Grade I" -> {
                holder.gradeIcon.setImageResource(R.drawable.check_circle)
            }
            "Grade II" -> {
                holder.gradeIcon.setImageResource(R.drawable.warning__1_)
            }
            "Grade III" -> {
                holder.gradeIcon.setImageResource(R.drawable.block__1_)
            }
        }
    }
}