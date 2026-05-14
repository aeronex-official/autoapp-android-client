package com.autoapp.store.ui.myapps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.autoapp.store.databinding.ItemInstalledAppBinding

class InstalledAppAdapter(
    private val pm: PackageManager,
    private val onClick: (ApplicationInfo) -> Unit
) : ListAdapter<ApplicationInfo, InstalledAppAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInstalledAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemInstalledAppBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(app: ApplicationInfo) {
            binding.tvAppName.text = app.loadLabel(pm)
            binding.tvPackage.text = app.packageName
            binding.ivIcon.setImageDrawable(app.loadIcon(pm))
            binding.root.setOnClickListener { onClick(app) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ApplicationInfo>() {
        override fun areItemsTheSame(oldItem: ApplicationInfo, newItem: ApplicationInfo) =
            oldItem.packageName == newItem.packageName
        override fun areContentsTheSame(oldItem: ApplicationInfo, newItem: ApplicationInfo) =
            oldItem.packageName == newItem.packageName
    }
}
