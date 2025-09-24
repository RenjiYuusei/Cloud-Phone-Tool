package com.cloudphone.tool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ApkAdapter(
    private val data: List<ApkItem>,
    private val nonDeletableIds: Set<String>,
    private val isLoading: (String) -> Boolean,
    private val onInstall: (ApkItem) -> Unit,
    private val onDelete: (ApkItem) -> Unit
) : RecyclerView.Adapter<ApkAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.txt_name)
        val src: TextView = v.findViewById(R.id.txt_src)
        val version: TextView = v.findViewById(R.id.txt_version)
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
        holder.version.text = "Phiên bản: ${it.versionName ?: "(chưa rõ)"}${it.versionCode?.let { c -> " (code $c)" } ?: ""}"
        val loadingNow = isLoading(it.id)
        holder.progress.visibility = if (loadingNow) View.VISIBLE else View.GONE
        holder.btnInstall.isEnabled = !loadingNow
        holder.btnInstall.setOnClickListener { _ -> if (!loadingNow) onInstall(it) }
        val deletable = !nonDeletableIds.contains(it.id)
        holder.btnDelete.visibility = if (deletable) View.VISIBLE else View.GONE
        holder.btnDelete.setOnClickListener { _ -> if (deletable) onDelete(it) }
    }

    override fun getItemCount(): Int = data.size
}

