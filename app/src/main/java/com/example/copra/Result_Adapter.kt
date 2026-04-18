package com.example.copra

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ResultAdapter(
    private val list: MutableList<ResultModel>,
    private val currentPage: Int,
    private val totalPages: Int,
    private val onItemClick: (ResultModel) -> Unit,
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
            val bitmap = decodeThumbnail(item.imagePath)
            if (bitmap != null) {
                holder.image.setImageBitmap(bitmap)
            } else {
                holder.image.setImageResource(R.drawable.sample_image_20)
            }

            holder.status.text = item.status
            holder.confidence.text = item.gradeSummary
            holder.date.text = item.date
            holder.gradeLabel.text = item.sourceLabel

            when (dominantGrade(item)) {
                1 -> holder.gradeIcon.setImageResource(R.drawable.check_circle)
                2 -> holder.gradeIcon.setImageResource(R.drawable.warning__1_)
                3 -> holder.gradeIcon.setImageResource(R.drawable.block__1_)
                else -> holder.gradeIcon.setImageResource(R.drawable.manage_search)
            }

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        if (holder is FooterViewHolder) {
            holder.etPageNumber.setText((currentPage + 1).toString())
            holder.tvTotalPages.text = "of $totalPages"

            holder.btnPrev.isEnabled = currentPage > 0
            holder.btnNext.isEnabled = currentPage < totalPages - 1

            holder.btnPrev.setOnClickListener {
                if (currentPage > 0) onPrevClick()
            }

            holder.btnNext.setOnClickListener {
                if (currentPage < totalPages - 1) onNextClick()
            }

            holder.etPageNumber.setOnEditorActionListener { _, _, _ ->
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

    private fun dominantGrade(item: ResultModel): Int {
        return when {
            item.grade1Count >= item.grade2Count &&
                item.grade1Count >= item.grade3Count &&
                item.grade1Count > 0 -> 1

            item.grade2Count >= item.grade1Count &&
                item.grade2Count >= item.grade3Count &&
                item.grade2Count > 0 -> 2

            item.grade3Count > 0 -> 3
            else -> 0
        }
    }

    private fun decodeThumbnail(path: String) = runCatching {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = 4
        options.inJustDecodeBounds = false
        BitmapFactory.decodeFile(path, options)
    }.getOrNull()
}
