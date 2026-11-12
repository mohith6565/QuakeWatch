package com.example.quakewatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.slider.Slider
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val allQuakes = mutableListOf<Quake>()
    private var userLoc: Location? = null

    private lateinit var adapter: QuakeAdapter
    private lateinit var tvBanner: TextView
    private lateinit var tvMag: TextView
    private lateinit var tvRadius: TextView
    private lateinit var sliderMag: Slider
    private lateinit var sliderRadius: Slider
    private lateinit var spSort: Spinner

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { getLocationThenFetch() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvBanner = findViewById(R.id.tvBanner)
        tvMag = findViewById(R.id.tvMag)
        tvRadius = findViewById(R.id.tvRadius)
        sliderMag = findViewById(R.id.sliderMag)
        sliderRadius = findViewById(R.id.sliderRadius)
        spSort = findViewById(R.id.spSort)

        adapter = QuakeAdapter { q ->
            val i = Intent(this, DetailActivity::class.java)
            i.putExtra("place", q.place)
            i.putExtra("mag", q.mag)
            i.putExtra("time", q.time)
            i.putExtra("url", q.url)
            i.putExtra("lat", q.lat)
            i.putExtra("lon", q.lon)
            i.putExtra("dist", q.distanceKm)
            startActivity(i)
        }
        val rv = findViewById<RecyclerView>(R.id.rvQuakes)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        spSort.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Sort by: Time", "Sort by: Magnitude", "Sort by: Distance")
        )
        spSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { refresh() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        sliderMag.addOnChangeListener { _, v, _ ->
            tvMag.text = "Minimum magnitude: %.1f".format(v)
            refresh()
        }
        sliderRadius.addOnChangeListener { _, v, _ ->
            tvRadius.text = "Alert radius: ${v.toInt()} km"
            refresh()
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { getLocationThenFetch() }

        createChannel()

        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(perms.toTypedArray())
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun getLocationThenFetch() {
        if (!hasLocationPermission()) { fetchQuakes(); return }
        val client = LocationServices.getFusedLocationProviderClient(this)
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) { userLoc = loc; fetchQuakes() }
                else client.lastLocation
                    .addOnSuccessListener { userLoc = it; fetchQuakes() }
                    .addOnFailureListener { fetchQuakes() }
            }
            .addOnFailureListener { fetchQuakes() }
    }

    private fun fetchQuakes() {
        tvBanner.text = "Loading earthquakes..."
        Thread {
            try {
                val conn = URL("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val list = parse(json)
                runOnUiThread {
                    allQuakes.clear()
                    allQuakes.addAll(list)
                    computeDistances()
                    val nearby = refresh()
                    if (nearby > 0) showNotification(nearby)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvBanner.text = "Failed to load"
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun parse(json: String): List<Quake> {
        val out = mutableListOf<Quake>()
        val feats = JSONObject(json).getJSONArray("features")
        for (i in 0 until feats.length()) {
            val f = feats.getJSONObject(i)
            val p = f.getJSONObject("properties")
            if (p.isNull("mag")) continue
            val c = f.getJSONObject("geometry").getJSONArray("coordinates")
            out.add(
                Quake(
                    id = f.getString("id"),
                    mag = p.getDouble("mag"),
                    place = p.optString("place", "Unknown"),
                    time = p.getLong("time"),
                    url = p.optString("url", ""),
                    lat = c.getDouble(1),
                    lon = c.getDouble(0)
                )
            )
        }
        return out
    }

    private fun computeDistances() {
        val u = userLoc ?: return
        val r = FloatArray(1)
        for (q in allQuakes) {
            Location.distanceBetween(u.latitude, u.longitude, q.lat, q.lon, r)
            q.distanceKm = r[0] / 1000f
        }
    }

    /** Applies filter + sort, updates list and banner. Returns number of quakes inside alert radius. */
    private fun refresh(): Int {
        val minMag = sliderMag.value
        val radius = sliderRadius.value
        var list = allQuakes.filter { it.mag >= minMag }
        list = when (spSort.selectedItemPosition) {
            0 -> list.sortedByDescending { it.time }
            1 -> list.sortedByDescending { it.mag }
            else -> list.sortedBy { if (it.distanceKm < 0) Float.MAX_VALUE else it.distanceKm }
        }
        val nearby = list.count { it.distanceKm >= 0 && it.distanceKm <= radius }
        tvBanner.text = if (userLoc == null)
            "Location unavailable - showing ${list.size} quakes"
        else
            "$nearby quake(s) within ${radius.toInt()} km (${list.size} shown)"
        adapter.submit(list, radius)
        return nearby
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("quakes", "Earthquake alerts", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(count: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, "quakes")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Nearby earthquake alert")
            .setContentText("$count quake(s) found within your alert radius")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(1, n)
    }
}