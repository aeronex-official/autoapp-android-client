package com.autoapp.store.ui.apps

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.autoapp.store.data.model.AppItem
import com.autoapp.store.databinding.ItemAppBinding
import com.bumptech.glide.Glide

class AppAdapter(private val onClick: (AppItem) -> Unit) :
    ListAdapter<AppItem, AppAdapter.AppViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppItem) {
            binding.tvAppName.text = app.name
            binding.tvCategory.text = app.category
            binding.root.setOnClickListener { onClick(app) }

            if (!app.iconUrl.isNullOrEmpty()) {
                Glide.with(binding.root)
                    .load(app.iconUrl)
                    .placeholder(android.R.drawable.sym_def_app_icon)
                    .into(binding.ivIcon)
            } else {
                binding.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem) = oldItem == newItem
    }
}
