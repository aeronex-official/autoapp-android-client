package com.autoapp.store.ui.apps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.autoapp.store.data.model.AppItem
import com.autoapp.store.databinding.ItemAppBinding
import com.bumptech.glide.Glide

/**
 * 下载进度状态，由 AppsFragment 通过 LocalBroadcast 驱动更新。
 */
data class DownloadState(
    val percent: Int,      // 0-100，-1 表示 indeterminate（连接中）
    val label: String,     // 显示文字，例如 "12.3 MB / 45.6 MB  (27%)"
    val isDone: Boolean = false,
    val isError: Boolean = false,
    val errorMsg: String = ""
)

class AppAdapter(private val onClick: (AppItem) -> Unit) :
    ListAdapter<AppItem, AppAdapter.AppViewHolder>(DiffCallback()) {

    // appId -> 当前下载状态；不在 map 中 = 空闲
    private val downloadStates = mutableMapOf<String, DownloadState>()

    // ── 供 AppsFragment 调用的状态更新方法 ─────────────────────────

    fun setDownloading(appId: String, percent: Int, label: String) {
        downloadStates[appId] = DownloadState(percent, label)
        notifyItemByAppId(appId)
    }

    fun setSuccess(appId: String) {
        downloadStates.remove(appId)
        notifyItemByAppId(appId)
    }

    fun setError(appId: String, message: String) {
        downloadStates[appId] = DownloadState(0, message, isError = true, errorMsg = message)
        notifyItemByAppId(appId)
        // 3 秒后自动清除错误状态
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            downloadStates.remove(appId)
            notifyItemByAppId(appId)
        }, 3000)
    }

    private fun notifyItemByAppId(appId: String) {
        val pos = currentList.indexOfFirst { it.id == appId }
        if (pos >= 0) notifyItemChanged(pos, "progress")  // payload 避免整个 item 闪烁
    }

    // ── RecyclerView 标准方法 ────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position), downloadStates[getItem(position).id])
    }

    // payload 更新：只刷新进度条部分，不重绘整个 item（避免图标闪烁）
    override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains("progress")) {
            holder.updateProgress(downloadStates[getItem(position).id])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class AppViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppItem, state: DownloadState?) {
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

            updateProgress(state)
        }

        fun updateProgress(state: DownloadState?) {
            if (state == null) {
                // 空闲状态：隐藏进度条和文字
                binding.tvDownloadStatus.visibility = View.GONE
                binding.downloadProgress.visibility = View.GONE
                binding.root.alpha = 1f
                return
            }

            // 显示状态文字
            binding.tvDownloadStatus.visibility = View.VISIBLE
            binding.tvDownloadStatus.text = state.label

            when {
                state.isError -> {
                    // 失败：显示红色文字，隐藏进度条
                    binding.tvDownloadStatus.setTextColor(0xFFFF6B6B.toInt())
                    binding.downloadProgress.visibility = View.GONE
                    binding.root.alpha = 1f
                }
                state.isDone -> {
                    // 完成：绿色文字
                    binding.tvDownloadStatus.setTextColor(0xFF66BB6A.toInt())
                    binding.downloadProgress.visibility = View.GONE
                    binding.root.alpha = 1f
                }
                else -> {
                    // 下载中：蓝色文字 + 进度条
                    binding.tvDownloadStatus.setTextColor(0xFF7EB3FF.toInt())
                    binding.downloadProgress.visibility = View.VISIBLE
                    binding.root.alpha = 0.85f  // 轻微变暗表示"忙碌"

                    if (state.percent < 0) {
                        // indeterminate（连接中/获取文件大小中）
                        binding.downloadProgress.isIndeterminate = true
                    } else {
                        binding.downloadProgress.isIndeterminate = false
                        binding.downloadProgress.progress = state.percent
                    }
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem) =
            oldItem == newItem
    }
}
