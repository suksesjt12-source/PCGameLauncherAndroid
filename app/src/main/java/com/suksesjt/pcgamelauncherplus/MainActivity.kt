package com.suksesjt.pcgamelauncherplus

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject

private data class GameEntry(val name: String, val uri: String)

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("game_library", MODE_PRIVATE) }
    private lateinit var gameList: LinearLayout
    private lateinit var runtimeStatus: TextView

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addGame(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gameList = findViewById(R.id.gameList)
        runtimeStatus = findViewById(R.id.runtimeStatus)
        findViewById<MaterialButton>(R.id.addGameButton).setOnClickListener {
            filePicker.launch(arrayOf("*/*"))
        }
        findViewById<MaterialButton>(R.id.runtimeButton).setOnClickListener { openRuntime() }
        renderLibrary()
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
            entries.add(GameEntry(name, uri.toString()))
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
            if (name.isNotBlank() && uri.isNotBlank()) GameEntry(name, uri) else null
        }
    } catch (_: Exception) { emptyList() }

    private fun saveGames(entries: List<GameEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("name", it.name).put("uri", it.uri)) }
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
                text = "File game Windows • EXE"
                textSize = 12f
                setTextColor(getColor(R.color.launcher_muted))
                setPadding(0, 4, 0, 8)
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(MaterialButton(this).apply {
                text = "Buka"
                setOnClickListener { openGame(entry) }
            })
            actions.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Hapus"
                setOnClickListener {
                    saveGames(loadGames().filterNot { it.uri == entry.uri })
                    renderLibrary()
                }
            })
            content.addView(actions)
            card.addView(content)
            gameList.addView(card)
        }
    }

    private fun openGame(entry: GameEntry) {
        val uri = Uri.parse(entry.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Buka file melalui aplikasi kompatibel"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Tidak ada runtime yang menerima file EXE. Buka runtime lalu impor file dari dalam aplikasi tersebut.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Runtime tidak menyediakan handler EXE. Impor file dari dalam runtime.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openRuntime() {
        val packages = listOf("com.winlator", "com.winlator.cmod")
        val launchIntent = packages.firstNotNullOfOrNull { packageName ->
            packageManager.getLaunchIntentForPackage(packageName)
        }
        if (launchIntent != null) {
            startActivity(launchIntent)
            runtimeStatus.text = "Runtime: Winlator terdeteksi"
        } else {
            runtimeStatus.text = "Runtime: buka secara manual"
            Toast.makeText(this, "Runtime tidak terdeteksi otomatis. Buka Winlator, Mobox, atau GameHub secara manual.", Toast.LENGTH_LONG).show()
        }
    }
}
