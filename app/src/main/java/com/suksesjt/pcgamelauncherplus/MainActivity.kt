package com.suksesjt.pcgamelauncherplus

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject

private data class RuntimeApp(val label: String, val packageName: String)
private data class GameEntry(val name: String, val uri: String, val runtimePackage: String = "")

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("game_library", MODE_PRIVATE) }
    private lateinit var gameList: LinearLayout
    private lateinit var runtimeStatus: TextView
    private lateinit var runtimeSpinner: Spinner
    private val runtimes = mutableListOf<RuntimeApp>()
    private var updatingSpinner = false

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addGame(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gameList = findViewById(R.id.gameList)
        runtimeStatus = findViewById(R.id.runtimeStatus)
        runtimeSpinner = findViewById(R.id.runtimeSpinner)
        findViewById<MaterialButton>(R.id.addGameButton).setOnClickListener {
            filePicker.launch(arrayOf("*/*"))
        }
        findViewById<MaterialButton>(R.id.runtimeButton).setOnClickListener {
            val pkg = selectedRuntimePackage()
            if (pkg.isBlank()) showNoRuntimeMessage() else openRuntime(pkg)
        }
        scanRuntimes()
        renderLibrary()
    }

    override fun onResume() {
        super.onResume()
        if (::runtimeSpinner.isInitialized) {
            val previous = selectedRuntimePackage()
            scanRuntimes(previous)
        }
    }

    private fun scanRuntimes(preferredPackage: String = prefs.getString("selected_runtime", "") ?: "") {
        runtimes.clear()
        val pm = packageManager
        val installed = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) { emptyList() }
        installed.forEach { info ->
            val label = pm.getApplicationLabel(info).toString()
            val identity = (label + " " + info.packageName).lowercase()
            val looksLikeRuntime = identity.contains("winlator") ||
                identity.contains("ludashi") || identity.contains("mobox") ||
                identity.contains("frost") || identity.contains("glibc") ||
                identity.contains("cmod")
            if (looksLikeRuntime && pm.getLaunchIntentForPackage(info.packageName) != null) {
                runtimes.add(RuntimeApp(label, info.packageName))
            }
        }
        runtimes.sortBy { it.label.lowercase() }
        val labels = mutableListOf("Pilih emulator") + runtimes.map { "${it.label}  (${it.packageName})" }
        updatingSpinner = true
        runtimeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val selectedIndex = runtimes.indexOfFirst { it.packageName == preferredPackage }
        runtimeSpinner.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0)
        updatingSpinner = false
        runtimeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingSpinner) return
                val pkg = runtimes.getOrNull(position - 1)?.packageName.orEmpty()
                prefs.edit().putString("selected_runtime", pkg).apply()
                updateRuntimeStatus()
                renderLibrary()
            }
        }
        updateRuntimeStatus()
    }

    private fun updateRuntimeStatus() {
        runtimeStatus.text = if (runtimes.isEmpty()) {
            "Runtime: tidak ditemukan • pasang Winlator atau variannya"
        } else {
            "Runtime terdeteksi: ${runtimes.size} • pilih emulator utama di bawah"
        }
    }

    private fun selectedRuntimePackage(): String =
        runtimes.getOrNull(runtimeSpinner.selectedItemPosition - 1)?.packageName.orEmpty()

    private fun showNoRuntimeMessage() {
        Toast.makeText(this, "Tidak ada aplikasi Winlator yang terdeteksi. Periksa apakah emulator sudah terpasang.", Toast.LENGTH_LONG).show()
    }

    private fun addGame(uri: Uri) {
        val name = queryDisplayName(uri) ?: "Game.exe"
        if (!name.endsWith(".exe", ignoreCase = true)) {
            Toast.makeText(this, "Pilih file dengan ekstensi .exe", Toast.LENGTH_LONG).show()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            Toast.makeText(this, "Izin file tidak dapat disimpan permanen.", Toast.LENGTH_LONG).show()
        }
        val entries = loadGames().toMutableList()
        if (entries.none { it.uri == uri.toString() }) {
            entries.add(GameEntry(name, uri.toString(), selectedRuntimePackage()))
            saveGames(entries)
        }
        renderLibrary()
        Toast.makeText(this, "$name ditambahkan", Toast.LENGTH_SHORT).show()
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun loadGames(): List<GameEntry> = try {
        val array = JSONArray(prefs.getString("entries", "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name")
            val uri = item.optString("uri")
            if (name.isNotBlank() && uri.isNotBlank()) {
                GameEntry(name, uri, item.optString("runtimePackage", ""))
            } else null
        }
    } catch (_: Exception) { emptyList() }

    private fun saveGames(entries: List<GameEntry>) {
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject().put("name", it.name).put("uri", it.uri)
                .put("runtimePackage", it.runtimePackage))
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }

    private fun renderLibrary() {
        gameList.removeAllViews()
        val entries = loadGames()
        if (entries.isEmpty()) {
            gameList.addView(TextView(this).apply {
                text = "Belum ada game. Tekan Tambah game untuk memilih file .exe."
                textSize = 14f
                setTextColor(getColor(R.color.launcher_muted))
                setPadding(4, 20, 4, 20)
            })
            return
        }
        entries.forEach { entry ->
            val card = MaterialCardView(this).apply {
                radius = 18f
                cardElevation = 0f
                setCardBackgroundColor(getColor(R.color.launcher_surface))
                useCompatPadding = true
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
            }
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
            }
            content.addView(TextView(this).apply {
                text = entry.name
                textSize = 16f
                setTextColor(getColor(R.color.launcher_text))
                maxLines = 2
            })
            content.addView(TextView(this).apply {
                text = "Game Windows • EXE"
                textSize = 12f
                setTextColor(getColor(R.color.launcher_muted))
                setPadding(0, 4, 0, 8)
            })
            val options = mutableListOf("Ikuti emulator utama") + runtimes.map { it.label }
            val selector = Spinner(this)
            selector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
            val perGameIndex = runtimes.indexOfFirst { it.packageName == entry.runtimePackage }
            selector.setSelection(if (perGameIndex >= 0) perGameIndex + 1 else 0)
            selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                private var first = true
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (first) { first = false; return }
                    val pkg = runtimes.getOrNull(position - 1)?.packageName.orEmpty()
                    val updated = loadGames().map {
                        if (it.uri == entry.uri) it.copy(runtimePackage = pkg) else it
                    }
                    saveGames(updated)
                }
            }
            content.addView(TextView(this).apply {
                text = "Emulator untuk game ini"
                textSize = 12f
                setTextColor(getColor(R.color.launcher_muted))
            })
            content.addView(selector)
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(MaterialButton(this).apply {
                text = "Buka emulator"
                setOnClickListener {
                    val chosen = entry.runtimePackage.ifBlank { selectedRuntimePackage() }
                    if (chosen.isBlank()) showNoRuntimeMessage() else openRuntime(chosen)
                }
            })
            actions.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Hapus"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("Hapus ${entry.name} dari library?")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Hapus") { _, _ ->
                            saveGames(loadGames().filterNot { it.uri == entry.uri })
                            renderLibrary()
                        }.show()
                }
            })
            content.addView(actions)
            card.addView(content)
            gameList.addView(card)
        }
    }

    private fun openRuntime(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(this, "Emulator tidak tersedia. Pindai ulang aplikasi.", Toast.LENGTH_LONG).show()
            scanRuntimes()
            return
        }
        try {
            startActivity(intent)
            val runtimeName = runtimes.firstOrNull { it.packageName == packageName }?.label ?: packageName
            runtimeStatus.text = "Membuka runtime: $runtimeName"
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Aplikasi emulator tidak dapat dibuka.", Toast.LENGTH_LONG).show()
        }
    }
}
