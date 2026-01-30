package com.surendramaran.yolov8tflite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ConfidenceThresholdAdapter(
    private val confidenceThresholds: MutableMap<String, Float>
) : RecyclerView.Adapter<ConfidenceThresholdAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val className: TextView = view.findViewById(R.id.classNameTextView)
        val confidence: TextView = view.findViewById(R.id.confidenceTextView)
        val minusButton: ImageButton = view.findViewById(R.id.minusButton)
        val plusButton: ImageButton = view.findViewById(R.id.plusButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_confidence_threshold, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val className = confidenceThresholds.keys.elementAt(position)
        holder.className.text = className
        holder.confidence.text = String.format("%.2f", confidenceThresholds[className])

        holder.minusButton.setOnClickListener {
            var value = confidenceThresholds[className] ?: 0.5f
            value -= 0.01f
            if (value < 0) value = 0f
            confidenceThresholds[className] = value
            holder.confidence.text = String.format("%.2f", value)
        }

        holder.plusButton.setOnClickListener {
            var value = confidenceThresholds[className] ?: 0.5f
            value += 0.01f
            if (value > 1) value = 1f
            confidenceThresholds[className] = value
            holder.confidence.text = String.format("%.2f", value)
        }
    }

    override fun getItemCount() = confidenceThresholds.size
}