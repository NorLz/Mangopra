package com.example.copra

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import android.widget.Button
import androidx.core.graphics.toColorInt
import android.widget.EditText

class ResultAdapter(
    private val list: MutableList<ResultModel>,
    private val currentPage: Int,
    private val totalPages: Int,
    private val onPageClick: (Int) -> Unit,
    private val onPrevClick: () -> Unit,
    private val onNextClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_FOOTER = 1
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.cardImage)
        val status: TextView = view.findViewById(R.id.cardStatus)
        val confidence: TextView = view.findViewById(R.id.cardConfidence)
        val date: TextView = view.findViewById(R.id.cardDate)
        val gradeLabel: TextView = view.findViewById(R.id.iconTopStart)
        val gradeIcon: ImageView = view.findViewById(R.id.iconTopEnd)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val btnPrev: Button = view.findViewById(R.id.btnPrev)
        val btnNext: Button = view.findViewById(R.id.btnNext)
        val etPageNumber: EditText = view.findViewById(R.id.etPageNumber)
        val tvTotalPages: TextView = view.findViewById(R.id.tvTotalPages)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == list.size) TYPE_FOOTER else TYPE_ITEM
    }

    override fun getItemCount(): Int = list.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ITEM) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_result_card, parent, false)
            ItemViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pagination_foooter, parent, false)
            FooterViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder) {
            val item = list[position]
            holder.image.setImageResource(item.imageRes)
            holder.confidence.text = item.confidence
            holder.date.text = item.date
            holder.gradeLabel.text = "Grade ${item.grade}"

            when (item.grade) {
                "I" -> {
                    holder.status.text = "Good Quality"
                    holder.gradeIcon.setImageResource(R.drawable.check_circle)
                }
                "II" -> {
                    holder.status.text = "Fair Quality"
                    holder.gradeIcon.setImageResource(R.drawable.warning__1_)
                }
                "III" -> {
                    holder.status.text = "Poor Quality"
                    holder.gradeIcon.setImageResource(R.drawable.block__1_)
                }
            }
        }

        if (holder is FooterViewHolder) {

            // Set current page
            holder.etPageNumber.setText((currentPage + 1).toString())
            holder.tvTotalPages.text = "of $totalPages"

            // Enable/disable buttons
            holder.btnPrev.isEnabled = currentPage > 0
            holder.btnNext.isEnabled = currentPage < totalPages - 1


            // Prev / Next logic
            holder.btnPrev.setOnClickListener {
                if (currentPage > 0) onPrevClick()
            }

            holder.btnNext.setOnClickListener {
                if (currentPage < totalPages - 1) onNextClick()
            }

            // EditText page input
            holder.etPageNumber.setOnEditorActionListener { v, actionId, event ->
                val input = holder.etPageNumber.text.toString().toIntOrNull()
                if (input != null && input in 1..totalPages) {
                    onPageClick(input - 1)
                } else {
                    holder.etPageNumber.setText((currentPage + 1).toString())
                }
                true
            }
        }
    }

}