package com.example.quakewatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val place = intent.getStringExtra("place") ?: ""
        val mag = intent.getDoubleExtra("mag", 0.0)
        val time = intent.getLongExtra("time", 0L)
        val url = intent.getStringExtra("url") ?: ""
        val lat = intent.getDoubleExtra("lat", 0.0)
        val lon = intent.getDoubleExtra("lon", 0.0)
        val dist = intent.getFloatExtra("dist", -1f)

        findViewById<TextView>(R.id.tvDPlace).text = place
        findViewById<TextView>(R.id.tvDMag).text = "Magnitude: %.1f".format(mag)
        findViewById<TextView>(R.id.tvDTime).text = "Time: ${formatTime(time)}"
        findViewById<TextView>(R.id.tvDCoords).text = "Location: %.3f, %.3f".format(lat, lon)
        findViewById<TextView>(R.id.tvDDist).text =
            if (dist < 0) "Distance: unknown" else "Distance: %.0f km from you".format(dist)

        findViewById<Button>(R.id.btnUsgs).setOnClickListener {
            if (url.isEmpty()) {
                Toast.makeText(this, "No URL available", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }
}