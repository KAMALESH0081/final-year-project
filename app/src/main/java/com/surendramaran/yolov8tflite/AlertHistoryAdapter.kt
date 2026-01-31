package com.surendramaran.yolov8tflite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlertHistoryAdapter(private val alertHistory: List<AlertRecord>) :
    RecyclerView.Adapter<AlertHistoryAdapter.AlertViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val alert = alertHistory[position]
        holder.bind(alert)
    }

    override fun getItemCount(): Int = alertHistory.size

    class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val classNameTextView: TextView = itemView.findViewById(R.id.tvClassName)
        private val confidenceTextView: TextView = itemView.findViewById(R.id.tvConfidence)
        private val speedTextView: TextView = itemView.findViewById(R.id.tvSpeed)
        private val timestampTextView: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(alert: AlertRecord) {
            classNameTextView.text = alert.className
            confidenceTextView.text = String.format("%.2f", alert.confidence)
            speedTextView.text = String.format("%.2f km/h", alert.speed)
            timestampTextView.text = alert.getFormattedTimestamp()
        }
    }
}