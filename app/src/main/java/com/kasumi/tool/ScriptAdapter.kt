package com.kasumi.tool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.io.File

class ScriptAdapter(
    private val data: List<ScriptItem>,
    private val getAutoFile: (ScriptItem) -> File,
    private val getManualFile: (ScriptItem) -> File,
    private val onExecute: (ScriptItem) -> Unit,
    private val onDownload: (ScriptItem) -> Unit,
    private val onDelete: (ScriptItem) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.txt_script_name)
        val gameName: TextView = v.findViewById(R.id.txt_game_name)
        val btnExecute: MaterialButton = v.findViewById(R.id.btn_execute)
        val btnDownload: MaterialButton = v.findViewById(R.id.btn_download)
        val btnDelete: MaterialButton = v.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]
        holder.name.text = item.name
        holder.gameName.text = "Game: ${item.gameName}"

        // Check if script is downloaded in either folder
        val autoFile = getAutoFile(item)
        val manualFile = getManualFile(item)
        val isDownloaded = autoFile.exists() || manualFile.exists()
        
        // Update button states
        holder.btnExecute.isEnabled = isDownloaded
        holder.btnDownload.visibility = if (isDownloaded) View.GONE else View.VISIBLE
        holder.btnDelete.visibility = if (isDownloaded) View.VISIBLE else View.GONE
        
        holder.btnExecute.setOnClickListener { onExecute(item) }
        holder.btnDownload.setOnClickListener { onDownload(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }
}
