package com.suksesjt.pcgamelauncherplus

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
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
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

private data class RuntimeApp(val label: String, val packageName: String)
private data class ContainerProfile(val id: Int, val name: String)
private data class ContainerQuery(val bridgeAvailable: Boolean, val items: List<ContainerProfile>)
private data class GameEntry(
    val name: String,
    val sourceUri: String,
    val runtimePackage: String = "",
    val containerId: Int = 0,
    val exePath: String = ""
)

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("game_library", MODE_PRIVATE) }
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private lateinit var gameList: LinearLayout
    private lateinit var runtimeStatus: TextView
    private lateinit var runtimeSpinner: Spinner
    private lateinit var containerSpinner: Spinner
    private lateinit var containerStatus: TextView
    private val runtimes = mutableListOf<RuntimeApp>()
    private val containerCache = mutableMapOf<String, ContainerQuery>()
    private var mainContainers: List<ContainerProfile> = emptyList()
    private var updatingRuntimeSpinner = false
    private var updatingContainerSpinner = false
    private var pendingImportUri: Uri? = null
    private var pendingImportRuntimePackage: String = ""
    private var pendingImportContainerId: Int = 0

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addGame(uri)
    }

    private val downloadsFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null) {
            pendingImportUri = null
            toast("Pemilihan folder Download dibatalkan.")
            return@registerForActivityResult
        }
        val documentId = try { DocumentsContract.getTreeDocumentId(treeUri) } catch (_: Exception) { "" }
        if (documentId != "primary:Download" && documentId != "primary:Download/PCGameLauncher") {
            pendingImportUri = null
            toast("Pilih folder Download atau Download/PCGameLauncher pada penyimpanan internal.")
            return@registerForActivityResult
        }
        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString("downloads_tree_uri", treeUri.toString()).apply()
            val source = pendingImportUri
            val runtime = pendingImportRuntimePackage
            val containerId = pendingImportContainerId
            pendingImportUri = null
            pendingImportRuntimePackage = ""
            pendingImportContainerId = 0
            if (source != null) copyGameIntoLauncherFolder(source, runtime, containerId)
        } catch (_: SecurityException) {
            pendingImportUri = null
            toast("Aplikasi tidak mendapat izin tulis ke folder Download.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gameList = findViewById(R.id.gameList)
        runtimeStatus = findViewById(R.id.runtimeStatus)
        runtimeSpinner = findViewById(R.id.runtimeSpinner)
        containerSpinner = findViewById(R.id.containerSpinner)
        containerStatus = findViewById(R.id.containerStatus)

        findViewById<MaterialButton>(R.id.addGameButton).setOnClickListener {
            filePicker.launch(arrayOf("application/octet-stream", "*/*"))
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
        containerCache.clear()
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
        updatingRuntimeSpinner = true
        runtimeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val selectedIndex = runtimes.indexOfFirst { it.packageName == preferredPackage }
        runtimeSpinner.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0)
        updatingRuntimeSpinner = false
        runtimeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingRuntimeSpinner) return
                val pkg = runtimes.getOrNull(position - 1)?.packageName.orEmpty()
                prefs.edit().putString("selected_runtime", pkg).putInt("selected_container_id", 0).apply()
                refreshMainContainers()
                updateRuntimeStatus()
                renderLibrary()
            }
        }
        refreshMainContainers()
        updateRuntimeStatus()
    }

    private fun selectedRuntimePackage(): String =
        runtimes.getOrNull(runtimeSpinner.selectedItemPosition - 1)?.packageName.orEmpty()

    private fun selectedContainerId(): Int =
        mainContainers.getOrNull(containerSpinner.selectedItemPosition - 1)?.id ?: 0

    private fun loadContainers(packageName: String): ContainerQuery {
        if (packageName.isBlank()) return ContainerQuery(false, emptyList())
        containerCache[packageName]?.let { return it }
        val result = try {
            val authority = "${packageName}.launcherbridge"
            val uri = Uri.parse("content://$authority/containers")
            val items = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex("container_id")
                val nameColumn = cursor.getColumnIndex("name")
                if (idColumn < 0 || nameColumn < 0) emptyList()
                else buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(idColumn)
                        val name = cursor.getString(nameColumn).orEmpty()
                        if (id > 0) add(ContainerProfile(id, name.ifBlank { "Container $id" }))
                    }
                }
            }
            if (items == null) ContainerQuery(false, emptyList())
            else ContainerQuery(true, items)
        } catch (_: Exception) {
            ContainerQuery(false, emptyList())
        }
        containerCache[packageName] = result
        return result
    }

    private fun refreshMainContainers() {
        val query = loadContainers(selectedRuntimePackage())
        mainContainers = query.items
        val labels = if (mainContainers.isEmpty()) {
            listOf(if (query.bridgeAvailable) "Belum ada container" else "Container langsung tidak didukung")
        } else {
            listOf("Pilih container") + mainContainers.map { "${it.name}  (ID ${it.id})" }
        }
        updatingContainerSpinner = true
        containerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val savedId = prefs.getInt("selected_container_id", 0)
        val index = mainContainers.indexOfFirst { it.id == savedId }
        containerSpinner.setSelection(if (index >= 0) index + 1 else 0)
        updatingContainerSpinner = false
        containerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingContainerSpinner) return
                prefs.edit().putInt("selected_container_id", mainContainers.getOrNull(position - 1)?.id ?: 0).apply()
                updateContainerStatus(query.bridgeAvailable)
            }
        }
        updateContainerStatus(query.bridgeAvailable)
    }

    private fun updateContainerStatus(bridgeAvailable: Boolean = loadContainers(selectedRuntimePackage()).bridgeAvailable) {
        containerStatus.text = when {
            !bridgeAvailable -> "Pilih Winlator Mali+ Helio dari repository WinlatorMaliPlus yang sudah diperbarui untuk integrasi langsung."
            mainContainers.isEmpty() -> "Bridge aktif, tetapi belum ada container dibuat di emulator."
            selectedContainerId() == 0 -> "${mainContainers.size} container ditemukan. Pilih satu untuk game baru."
            else -> "Container aktif: ${mainContainers.firstOrNull { it.id == selectedContainerId() }?.name.orEmpty()}"
        }
    }

    private fun updateRuntimeStatus() {
        val pkg = selectedRuntimePackage()
        if (pkg.isBlank()) {
            runtimeStatus.text = if (runtimes.isEmpty()) "Runtime: tidak ditemukan" else "Runtime terdeteksi: ${runtimes.size} • pilih emulator"
            return
        }
        val bridge = loadContainers(pkg)
        runtimeStatus.text = when {
            bridge.bridgeAvailable && bridge.items.isNotEmpty() -> "Runtime siap • ${bridge.items.size} container tersedia • peluncuran EXE langsung aktif"
            bridge.bridgeAvailable -> "Bridge aktif • buat container terlebih dahulu di emulator"
            else -> "Emulator terdeteksi • peluncuran langsung perlu Winlator Mali+ Bridge"
        }
    }

    private fun showNoRuntimeMessage() {
        toast("Tidak ada aplikasi Winlator yang terdeteksi. Periksa apakah emulator sudah terpasang.")
    }

    private fun addGame(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            toast("Izin file tidak bisa disimpan. Pilih sumber file lain atau salin ulang saat dibutuhkan.")
        }
        val name = queryDisplayName(uri) ?: "Game.exe"
        if (!name.endsWith(".exe", ignoreCase = true)) {
            toast("Pilih file dengan ekstensi .exe")
            return
        }
        val sharedPath = getSharedDownloadPath(uri)
        if (sharedPath != null) {
            val entries = loadGames().filterNot { it.sourceUri == uri.toString() }.toMutableList()
            entries.add(
                GameEntry(
                    name = name,
                    sourceUri = uri.toString(),
                    runtimePackage = selectedRuntimePackage(),
                    containerId = selectedContainerId(),
                    exePath = sharedPath
                )
            )
            saveGames(entries)
            renderLibrary()
            toast("EXE ditambahkan dari folder game. File pendamping tetap di tempatnya.")
            return
        }
        val treeUri = prefs.getString("downloads_tree_uri", null)?.let(Uri::parse)
        if (treeUri == null) {
            pendingImportUri = uri
            pendingImportRuntimePackage = selectedRuntimePackage()
            pendingImportContainerId = selectedContainerId()
            downloadsFolderPicker.launch(null)
            return
        }
        copyGameIntoLauncherFolder(uri, selectedRuntimePackage(), selectedContainerId())
    }

    private fun getSharedDownloadPath(uri: Uri): String? {
        return try {
            if (!DocumentsContract.isDocumentUri(this, uri)) return null
            val documentId = DocumentsContract.getDocumentId(uri)
            val prefix = "primary:Download/PCGameLauncher/"
            if (!documentId.startsWith(prefix)) return null
            val relativePath = documentId.removePrefix("primary:")
            File(Environment.getExternalStorageDirectory(), relativePath).absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun copyGameIntoLauncherFolder(sourceUri: Uri, runtimePackage: String, containerId: Int) {
        val name = queryDisplayName(sourceUri) ?: "Game.exe"
        if (!name.endsWith(".exe", ignoreCase = true)) {
            toast("Hanya file .exe yang dapat ditambahkan.")
            return
        }
        containerStatus.text = "Menyalin $name ke Download/PCGameLauncher..."
        ioExecutor.execute {
            try {
                val treeUri = prefs.getString("downloads_tree_uri", null)?.let(Uri::parse)
                    ?: throw IOException("Folder Download belum dipilih.")
                val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                val folderUri: Uri
                if (treeDocumentId == "primary:Download/PCGameLauncher") {
                    folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
                } else if (treeDocumentId == "primary:Download") {
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
                    var found: Uri? = null
                    contentResolver.query(
                        childrenUri,
                        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                        null, null, null
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameCol) == "PCGameLauncher" &&
                                cursor.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR) {
                                found = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                                break
                            }
                        }
                    }
                    folderUri = found ?: DocumentsContract.createDocument(
                        contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, "PCGameLauncher"
                    ) ?: throw IOException("Gagal membuat folder game.")
                } else {
                    throw IOException("Pilih folder Download internal.")
                }

                val folderDocumentId = DocumentsContract.getDocumentId(folderUri)
                if (!folderDocumentId.startsWith("primary:Download/PCGameLauncher")) {
                    throw IOException("Folder tujuan bukan Download/PCGameLauncher.")
                }

                val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val targetUri = DocumentsContract.createDocument(
                    contentResolver, folderUri, "application/octet-stream", safeName
                ) ?: throw IOException("Tidak bisa membuat file tujuan.")
                try {
                    val input = contentResolver.openInputStream(sourceUri) ?: throw IOException("File sumber tidak dapat dibaca.")
                    val output = contentResolver.openOutputStream(targetUri, "w") ?: throw IOException("File tujuan tidak dapat ditulis.")
                    input.use { source -> output.use { destination -> source.copyTo(destination) } }
                } catch (e: Exception) {
                    try { DocumentsContract.deleteDocument(contentResolver, targetUri) } catch (_: Exception) {}
                    throw e
                }

                val destinationDocumentId = DocumentsContract.getDocumentId(targetUri)
                val relativePath = destinationDocumentId.removePrefix("primary:")
                if (!relativePath.startsWith("Download/PCGameLauncher/")) {
                    throw IOException("Lokasi EXE tidak cocok dengan drive Download emulator.")
                }
                val destinationFile = File(Environment.getExternalStorageDirectory(), relativePath).canonicalFile
                runOnUiThread {
                    val existing = loadGames().filterNot { it.sourceUri == sourceUri.toString() }
                    val updated = existing + GameEntry(
                        name = safeName,
                        sourceUri = sourceUri.toString(),
                        runtimePackage = runtimePackage,
                        containerId = containerId,
                        exePath = destinationFile.absolutePath
                    )
                    saveGames(updated)
                    renderLibrary()
                    containerStatus.text = "Game siap dijalankan: $safeName"
                    toast("$safeName disalin. Pilih container lalu tekan Mainkan.")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    containerStatus.text = "Gagal menyalin EXE."
                    toast(e.message ?: "Gagal menyalin file game.")
                }
            }
        }
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
            val uri = item.optString("uri", item.optString("sourceUri"))
            if (name.isBlank() || uri.isBlank()) null
            else GameEntry(
                name = name,
                sourceUri = uri,
                runtimePackage = item.optString("runtimePackage", ""),
                containerId = item.optInt("containerId", 0),
                exePath = item.optString("exePath", "")
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun saveGames(entries: List<GameEntry>) {
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("uri", it.sourceUri)
                    .put("runtimePackage", it.runtimePackage)
                    .put("containerId", it.containerId)
                    .put("exePath", it.exePath)
            )
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }

    private fun updateGame(game: GameEntry, transform: (GameEntry) -> GameEntry) {
        saveGames(loadGames().map { if (it.sourceUri == game.sourceUri) transform(it) else it })
    }

    private fun renderLibrary() {
        gameList.removeAllViews()
        val entries = loadGames()
        if (entries.isEmpty()) {
            gameList.addView(TextView(this).apply {
                text = "Belum ada game. Tambahkan file .exe untuk menyalin game ke folder Download/PCGameLauncher."
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
                text = if (entry.exePath.isBlank()) "Perlu disalin ke folder emulator saat pertama dijalankan" else entry.exePath
                textSize = 11f
                setTextColor(getColor(R.color.launcher_muted))
                setPadding(0, 4, 0, 8)
            })

            val runtimeSelector = Spinner(this)
            val runtimeOptions = mutableListOf("Ikuti emulator utama") + runtimes.map { "${it.label}  (${it.packageName})" }
            runtimeSelector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, runtimeOptions)
            val runtimeIndex = runtimes.indexOfFirst { it.packageName == entry.runtimePackage }
            runtimeSelector.setSelection(if (runtimeIndex >= 0) runtimeIndex + 1 else 0)
            content.addView(TextView(this).apply {
                text = "Emulator"
                textSize = 12f
                setTextColor(getColor(R.color.launcher_muted))
            })
            content.addView(runtimeSelector)

            val effectivePackage = entry.runtimePackage.ifBlank { selectedRuntimePackage() }
            val query = loadContainers(effectivePackage)
            val containerOptions = if (query.items.isEmpty()) {
                listOf(if (query.bridgeAvailable) "Belum ada container" else "Bridge container tidak tersedia")
            } else {
                listOf("Pilih container") + query.items.map { "${it.name}  (ID ${it.id})" }
            }
            val gameContainerSelector = Spinner(this)
            gameContainerSelector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, containerOptions)
            val containerIndex = query.items.indexOfFirst { it.id == entry.containerId }
            gameContainerSelector.setSelection(if (containerIndex >= 0) containerIndex + 1 else 0)
            content.addView(TextView(this).apply {
                text = "Container game"
                textSize = 12f
                setTextColor(getColor(R.color.launcher_muted))
            })
            content.addView(gameContainerSelector)

            runtimeSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                private var initial = true
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (initial) { initial = false; return }
                    val pkg = runtimes.getOrNull(position - 1)?.packageName.orEmpty()
                    updateGame(entry) { it.copy(runtimePackage = pkg, containerId = 0) }
                    renderLibrary()
                }
            }
            gameContainerSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                private var initial = true
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (initial) { initial = false; return }
                    val selectedId = query.items.getOrNull(position - 1)?.id ?: 0
                    updateGame(entry) { it.copy(containerId = selectedId) }
                }
            }

            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(MaterialButton(this).apply {
                text = if (entry.exePath.isBlank()) "Siapkan game" else "Mainkan"
                setOnClickListener {
                    val selectedRuntime = entry.runtimePackage.ifBlank { selectedRuntimePackage() }
                    val chosenQuery = loadContainers(selectedRuntime)
                    val currentGame = loadGames().firstOrNull { it.sourceUri == entry.sourceUri } ?: entry
                    val chosenContainer = currentGame.containerId.takeIf { it > 0 }
                        ?: if (selectedRuntime == selectedRuntimePackage()) selectedContainerId() else 0
                    if (currentGame.exePath.isBlank()) {
                        if (chosenContainer == 0) {
                            toast("Pilih emulator dan container terlebih dahulu.")
                            return@setOnClickListener
                        }
                        copyGameIntoLauncherFolder(Uri.parse(currentGame.sourceUri), selectedRuntime, chosenContainer)
                    } else {
                        launchGame(currentGame.copy(runtimePackage = selectedRuntime, containerId = chosenContainer), chosenQuery)
                    }
                }
            })
            actions.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Hapus"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("Hapus ${entry.name} dari library?")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Hapus") { _, _ ->
                            saveGames(loadGames().filterNot { it.sourceUri == entry.sourceUri })
                            renderLibrary()
                        }.show()
                }
            })
            content.addView(actions)
            card.addView(content)
            gameList.addView(card)
        }
    }

    private fun launchGame(entry: GameEntry, query: ContainerQuery) {
        if (!query.bridgeAvailable) {
            toast("Runtime ini belum mendukung jembatan peluncuran langsung. Gunakan Winlator Mali+ yang sudah diperbarui.")
            if (entry.runtimePackage.isNotBlank()) openRuntime(entry.runtimePackage)
            return
        }
        if (entry.containerId <= 0) {
            toast("Pilih container untuk game ini.")
            return
        }
        val exe = File(entry.exePath)
        if (!exe.isFile) {
            toast("File EXE tidak ditemukan. Tekan Siapkan game untuk menyalinnya kembali.")
            return
        }
        try {
            val authorityUri = Uri.parse("content://${entry.runtimePackage}.launcherbridge")
            val reply = contentResolver.call(
                authorityUri,
                "launch_exe",
                null,
                Bundle().apply {
                    putInt("container_id", entry.containerId)
                    putString("exe_path", exe.absolutePath)
                }
            )
            val success = reply?.getBoolean("success", false) == true
            val message = reply?.getString("message").orEmpty()
            if (!success) toast(message.ifBlank { "Winlator menolak permintaan peluncuran." })
            else runtimeStatus.text = message
        } catch (e: Exception) {
            toast("Gagal menghubungi Winlator Mali+ Bridge: ${e.message ?: "unknown error"}")
        }
    }

    private fun openRuntime(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            toast("Emulator tidak tersedia. Pindai ulang aplikasi.")
            scanRuntimes()
            return
        }
        try {
            startActivity(intent)
            val runtimeName = runtimes.firstOrNull { it.packageName == packageName }?.label ?: packageName
            runtimeStatus.text = "Membuka runtime: $runtimeName"
        } catch (_: ActivityNotFoundException) {
            toast("Aplikasi emulator tidak dapat dibuka.")
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
