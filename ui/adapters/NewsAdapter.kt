package com.fitnesslemon.app.ui.adapters

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.News
import com.fitnesslemon.app.databinding.ItemNewsBinding

class NewsAdapter(private val news: List<News>) : RecyclerView.Adapter<NewsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(news[position])
    }

    override fun getItemCount() = news.size

    class ViewHolder(private val binding: ItemNewsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: News) {
            binding.apply {
                tvTitle.text = item.title ?: "Без названия"

                // ✅ ИСПРАВЛЕНО: используем excerpt или shortDescription
                val excerptText = item.excerpt ?: item.shortDescription ?: ""
                tvExcerpt.text = excerptText

                tvDate.text = item.formattedDate ?: ""

                // Блок медиа
                val hasImages = !item.images.isNullOrEmpty()
                val hasVideos = !item.videos.isNullOrEmpty()
                layoutMedia.visibility = if (hasImages || hasVideos) View.VISIBLE else View.GONE

                if (hasImages) {
                    ivFirstImage.visibility = View.VISIBLE
                    Glide.with(root.context)
                        .load(item.images!![0])
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(ivFirstImage)
                    ivFirstImage.setOnClickListener {
                        showFullScreenImage(root.context, item.images[0])
                    }
                } else {
                    ivFirstImage.visibility = View.GONE
                }

                if (hasVideos) {
                    ivVideoIcon.visibility = View.VISIBLE
                    ivVideoIcon.setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.videos!![0]))
                        intent.setDataAndType(Uri.parse(item.videos[0]), "video/*")
                        root.context.startActivity(intent)
                    }
                } else {
                    ivVideoIcon.visibility = View.GONE
                }
            }
        }

        private fun showFullScreenImage(context: Context, imageUrl: String) {
            val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(R.layout.dialog_full_image)
            val imageView = dialog.findViewById<ImageView>(R.id.imageView)
            Glide.with(context).load(imageUrl).into(imageView)
            dialog.show()
        }
    }
}