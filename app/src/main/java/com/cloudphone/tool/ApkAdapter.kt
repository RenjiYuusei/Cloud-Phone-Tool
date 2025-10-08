package com.cloudphone.tool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ApkAdapter(
    private val data: List<ApkItem>,
    private val nonDeletableIds: Set<String>,
    private val isLoading: (String) -> Boolean,
    private val getCachedFile: (ApkItem) -> java.io.File?,
    private val onInstall: (ApkItem) -> Unit,
    private val onDelete: (ApkItem) -> Unit
) : RecyclerView.Adapter<ApkAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.img_app_icon)
        val name: TextView = v.findViewById(R.id.txt_name)
        val src: TextView = v.findViewById(R.id.txt_src)
        val version: TextView = v.findViewById(R.id.txt_version)
        val badgeCached: TextView = v.findViewById(R.id.badge_cached)
        val fileSize: TextView = v.findViewById(R.id.txt_file_size)
        val btnInstall: Button = v.findViewById(R.id.btn_install)
        val btnDelete: ImageButton = v.findViewById(R.id.btn_delete)
        val progress: ProgressBar = v.findViewById(R.id.progress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_apk, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = data[position]
        holder.name.text = it.name
        // Ẩn hiển thị link tải/nguồn để tránh rối UI
        holder.src.visibility = View.GONE
        
        // Hiển thị icon từ URL
        if (!it.iconUrl.isNullOrEmpty()) {
            Glide.with(holder.icon.context)
                .load(it.iconUrl)
                .placeholder(R.drawable.ic_apps)
                .error(R.drawable.ic_apps)
                .circleCrop()
                .into(holder.icon)
        } else {
            // Hiển thị icon mặc định nếu không có iconUrl
            Glide.with(holder.icon.context)
                .load(R.drawable.ic_apps)
                .circleCrop()
                .into(holder.icon)
        }
        
        holder.version.text = "Phiên bản: ${it.versionName ?: "(chưa rõ)"}${it.versionCode?.let { c -> " (code $c)" } ?: ""}"
        
        // Hiển thị badge và file size nếu đã cache
        val cachedFile = getCachedFile(it)
        if (cachedFile != null && cachedFile.exists()) {
            holder.badgeCached.visibility = View.VISIBLE
            holder.fileSize.visibility = View.VISIBLE
            holder.fileSize.text = formatFileSize(cachedFile.length())
        } else {
            holder.badgeCached.visibility = View.GONE
            holder.fileSize.visibility = View.GONE
        }
        
        val loadingNow = isLoading(it.id)
        holder.progress.visibility = if (loadingNow) View.VISIBLE else View.GONE
        holder.btnInstall.isEnabled = !loadingNow
        holder.btnInstall.setOnClickListener { _ -> if (!loadingNow) onInstall(it) }
        val deletable = !nonDeletableIds.contains(it.id)
        holder.btnDelete.visibility = if (deletable) View.VISIBLE else View.GONE
        holder.btnDelete.setOnClickListener { _ -> if (deletable) onDelete(it) }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    override fun getItemCount(): Int = data.size
}

