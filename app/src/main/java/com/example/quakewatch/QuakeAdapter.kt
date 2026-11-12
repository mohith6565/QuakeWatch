package com.example.quakewatch

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QuakeAdapter(private val onClick: (Quake) -> Unit) :
    RecyclerView.Adapter<QuakeAdapter.VH>() {

    private var items = listOf<Quake>()
    private var radius = 0f

    fun submit(list: List<Quake>, r: Float) {
        items = list
        radius = r
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvMag: TextView = v.findViewById(R.id.tvMag)
        val tvPlace: TextView = v.findViewById(R.id.tvPlace)
        val tvTime: TextView = v.findViewById(R.id.tvTime)
        val tvDist: TextView = v.findViewById(R.id.tvDist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_quake, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val q = items[position]
        h.tvMag.text = "%.1f".format(q.mag)
        h.tvPlace.text = q.place
        h.tvTime.text = formatTime(q.time)
        h.tvDist.text = if (q.distanceKm < 0) "Distance: unknown" else "%.0f km away".format(q.distanceKm)
        h.itemView.setBackgroundColor(
            if (q.distanceKm >= 0 && q.distanceKm <= radius) Color.parseColor("#FFCDD2") else Color.WHITE
        )
        h.itemView.setOnClickListener { onClick(q) }
    }
}