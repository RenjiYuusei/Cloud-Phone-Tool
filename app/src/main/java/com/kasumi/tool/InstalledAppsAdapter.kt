package com.kasumi.tool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class InstalledAppsAdapter(
    private val data: List<InstalledAppItem>,
    private val onOpen: (InstalledAppItem) -> Unit,
    private val onInfo: (InstalledAppItem) -> Unit,
    private val onUninstall: (InstalledAppItem) -> Unit
) : RecyclerView.Adapter<InstalledAppsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.img_icon)
        val name: TextView = v.findViewById(R.id.txt_app_name)
        val pkg: TextView = v.findViewById(R.id.txt_package)
        val version: TextView = v.findViewById(R.id.txt_version)
        val btnOpen: MaterialButton = v.findViewById(R.id.btn_open)
        val btnInfo: MaterialButton = v.findViewById(R.id.btn_info)
        val btnUninstall: MaterialButton = v.findViewById(R.id.btn_uninstall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_installed_app, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = data[position]
        holder.icon.setImageDrawable(it.icon)
        holder.name.text = it.appName
        holder.pkg.text = it.packageName
        holder.version.text = "Phiên bản: ${it.versionName ?: "?"}${it.versionCode?.let { c -> " (code $c)" } ?: ""}"
        holder.btnOpen.setOnClickListener { _ -> onOpen(it) }
        holder.btnInfo.setOnClickListener { _ -> onInfo(it) }
        holder.btnUninstall.setOnClickListener { _ -> onUninstall(it) }
    }
}
