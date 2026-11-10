package com.example

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val data = result.data
                val clipData = data?.clipData
                val dataString = data?.dataString

                val results: Array<Uri>? = when {
                    clipData != null -> {
                        Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                    }
                    dataString != null -> {
                        arrayOf(Uri.parse(dataString))
                    }
                    else -> null
                }
                fileUploadCallback?.onReceiveValue(results)
            } else {
                fileUploadCallback?.onReceiveValue(null)
            }
            fileUploadCallback = null
        }

        setContent {
            MyApplicationTheme {
                CrossBarAppScreen()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun CrossBarAppScreen() {
        var webViewRef by remember { mutableStateOf<WebView?>(null) }

        BackHandler(enabled = webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0F1E))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(0xFF0A0F1E.toInt())

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            builtInZoomControls = false
                            displayZoomControls = false
                        }

                        addJavascriptInterface(AndroidBridge(this@MainActivity), "AndroidBridge")

                        setDownloadListener { url, _, _, _, _ ->
                            if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "Cannot open download link", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return handleExternalLink(url)
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                if (url == null) return false
                                return handleExternalLink(url)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                fileUploadCallback?.onReceiveValue(null)
                                fileUploadCallback = filePathCallback

                                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "*/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                }

                                try {
                                    fileChooserLauncher.launch(intent)
                                } catch (e: Exception) {
                                    fileUploadCallback = null
                                    Toast.makeText(this@MainActivity, "Cannot open file picker", Toast.LENGTH_SHORT).show()
                                    return false
                                }
                                return true
                            }
                        }

                        loadUrl("file:///android_asset/www/index.html")
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    private fun getPreferredWaPackage(): String {
        val prefs = getSharedPreferences("cbd_prefs", Context.MODE_PRIVATE)
        return prefs.getString("wa_pkg", "com.whatsapp.w4b") ?: "com.whatsapp.w4b"
    }

    private fun handleExternalLink(url: String): Boolean {
        if (url.startsWith("javascript:") || url.startsWith("about:")) {
            return false
        }
        if (url.startsWith("whatsapp://") ||
            url.startsWith("https://api.whatsapp.com/") ||
            url.startsWith("https://wa.me/")
        ) {
            val parsed = Uri.parse(url)
            val waSchemeUri: Uri = if (url.startsWith("https://api.whatsapp.com/") || url.startsWith("https://wa.me/")) {
                val q = parsed.encodedQuery
                val phoneSeg = parsed.pathSegments.firstOrNull()?.takeIf { it.all(Char::isDigit) }
                when {
                    !q.isNullOrEmpty() && !phoneSeg.isNullOrEmpty() && !q.contains("phone=") ->
                        Uri.parse("whatsapp://send?phone=$phoneSeg&$q")
                    !q.isNullOrEmpty() -> Uri.parse("whatsapp://send?$q")
                    !phoneSeg.isNullOrEmpty() -> Uri.parse("whatsapp://send?phone=$phoneSeg")
                    else -> parsed
                }
            } else {
                parsed
            }

            val prefPkg = getPreferredWaPackage()
            val candidates = when (prefPkg) {
                "com.whatsapp.w4b" -> listOf("com.whatsapp.w4b", "com.whatsapp")
                "com.whatsapp" -> listOf("com.whatsapp", "com.whatsapp.w4b")
                else -> emptyList()
            }
            for (pkg in candidates) {
                val targetUris = if (waSchemeUri != parsed) listOf(waSchemeUri, parsed) else listOf(parsed)
                for (targetUri in targetUris) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                            setPackage(pkg)
                        }
                        startActivity(intent)
                        return true
                    } catch (_: Exception) {
                        // Try next URI or fallback package
                    }
                }
            }
            if (prefPkg.isEmpty()) {
                try {
                    val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, waSchemeUri), "Send WhatsApp message via")
                    startActivity(chooser)
                    return true
                } catch (_: Exception) {
                    // Fallback to URL below
                }
            }
            try {
                startActivity(Intent(Intent.ACTION_VIEW, parsed))
                return true
            } catch (err: Exception) {
                Toast.makeText(this, "Cannot open WhatsApp link", Toast.LENGTH_SHORT).show()
            }
        } else if (url.startsWith("tel:") || url.startsWith("mailto:")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                return true
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot handle action", Toast.LENGTH_SHORT).show()
            }
        } else if (!url.startsWith("file://") && !url.startsWith("http://localhost")) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
                return true
            } catch (e: Exception) {
                return false
            }
        }
        return false
    }

    inner class AndroidBridge(private val context: Context) {

        @JavascriptInterface
        fun setPreferredWhatsApp(pkg: String) {
            val cleanPkg = when (pkg) {
                "com.whatsapp", "com.whatsapp.w4b", "" -> pkg
                else -> "com.whatsapp.w4b"
            }
            context.getSharedPreferences("cbd_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("wa_pkg", cleanPkg)
                .apply()
        }

        @JavascriptInterface
        fun getPreferredWhatsApp(): String {
            return getPreferredWaPackage()
        }

        @JavascriptInterface
        fun getLogoDataUrl(): String {
            return try {
                context.assets.open("www/icons/icon-192.png").use { input ->
                    val bytes = input.readBytes()
                    "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            } catch (_: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun saveImageToGallery(filename: String, base64Data: String) {
            saveImageToGalleryInternal(filename, base64Data, showToastMsg = true)
        }

        private fun saveImageToGalleryInternal(filename: String, base64Data: String, showToastMsg: Boolean) {
            runOnUiThread {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val safeName = if (filename.endsWith(".png", ignoreCase = true)) filename else "$filename.png"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CrossBarBD")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(bytes)
                            }
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, contentValues, null, null)
                            if (showToastMsg) {
                                Toast.makeText(context, "🖼️ Invoice saved to Gallery / Photos: $safeName ✓", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            saveToAppExternalDir(safeName, bytes)
                        }
                    } else {
                        try {
                            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            val targetDir = File(picturesDir, "CrossBarBD")
                            if (!targetDir.exists()) targetDir.mkdirs()
                            val file = File(targetDir, safeName)
                            FileOutputStream(file).use { it.write(bytes) }

                            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                                data = Uri.fromFile(file)
                            }
                            context.sendBroadcast(mediaScanIntent)
                            if (showToastMsg) {
                                Toast.makeText(context, "🖼️ Invoice saved to Gallery: $safeName ✓", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            saveToAppExternalDir(safeName, bytes)
                        }
                    }
                } catch (e: Exception) {
                    if (showToastMsg) {
                        Toast.makeText(context, "Gallery save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        @JavascriptInterface
        fun saveFile(filename: String, base64Data: String, mimeType: String) {
            if (mimeType.startsWith("image/") || filename.endsWith(".png", ignoreCase = true) || filename.endsWith(".jpg", ignoreCase = true)) {
                saveImageToGallery(filename, base64Data)
                return
            }
            runOnUiThread {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CrossBarBD")
                        }
                        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(bytes)
                            }
                            Toast.makeText(context, "Saved to Downloads/CrossBarBD: $filename", Toast.LENGTH_LONG).show()
                        } else {
                            saveToAppExternalDir(filename, bytes)
                        }
                    } else {
                        try {
                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val targetDir = File(downloadsDir, "CrossBarBD")
                            if (!targetDir.exists()) targetDir.mkdirs()
                            val file = File(targetDir, filename)
                            FileOutputStream(file).use { it.write(bytes) }
                            Toast.makeText(context, "Saved to Downloads/CrossBarBD: $filename", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            saveToAppExternalDir(filename, bytes)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun saveToAppExternalDir(filename: String, bytes: ByteArray) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val file = File(dir, filename)
            FileOutputStream(file).use { it.write(bytes) }
            Toast.makeText(context, "Saved: ${file.name}", Toast.LENGTH_LONG).show()
        }

        @JavascriptInterface
        fun shareImage(filename: String, base64Data: String, textMessage: String, mimeType: String) {
            shareImageToWhatsApp(filename, base64Data, textMessage, mimeType, "")
        }

        @JavascriptInterface
        fun shareImageToWhatsApp(filename: String, base64Data: String, textMessage: String, mimeType: String, cleanPhone: String) {
            saveImageToGalleryInternal(filename, base64Data, showToastMsg = false)
            runOnUiThread {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val sharedDir = File(context.cacheDir, "shared")
                    if (!sharedDir.exists()) sharedDir.mkdirs()
                    val file = File(sharedDir, filename)
                    FileOutputStream(file).use { it.write(bytes) }

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    val prefPkg = getPreferredWaPackage()
                    if (prefPkg.isNotEmpty()) {
                        val directCandidates = if (prefPkg == "com.whatsapp.w4b") {
                            listOf("com.whatsapp.w4b", "com.whatsapp")
                        } else {
                            listOf("com.whatsapp", "com.whatsapp.w4b")
                        }
                        for (pkg in directCandidates) {
                            try {
                                val waIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    setPackage(pkg)
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TEXT, textMessage)
                                    if (cleanPhone.isNotBlank()) {
                                        putExtra("jid", "$cleanPhone@s.whatsapp.net")
                                    }
                                    clipData = ClipData.newRawUri("Invoice", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                } catch (_: Exception) {}
                                context.startActivity(waIntent)
                                return@runOnUiThread
                            } catch (_: Exception) {
                                // Try fallback package or chooser
                            }
                        }
                    }

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, textMessage)
                        clipData = ClipData.newRawUri("Invoice", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Invoice via"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun shareText(textMessage: String) {
            runOnUiThread {
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, textMessage)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun copyToClipboard(text: String) {
            runOnUiThread {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("CrossBar BD", text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard ✓", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Copy failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun exportApk() {
            Thread {
                try {
                    val srcFile = File(context.applicationInfo.sourceDir)
                    if (!srcFile.exists()) {
                        runOnUiThread {
                            Toast.makeText(context, "APK file unavailable", Toast.LENGTH_SHORT).show()
                        }
                        return@Thread
                    }
                    val filename = "CrossBarBD.apk"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CrossBarBD")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outStream ->
                                srcFile.inputStream().use { inStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            resolver.update(uri, contentValues, null, null)
                            runOnUiThread {
                                Toast.makeText(context, "📥 APK saved to Downloads/CrossBarBD/$filename ✓", Toast.LENGTH_LONG).show()
                            }
                            return@Thread
                        }
                    }
                    try {
                        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val dir = File(dl, "CrossBarBD").apply { if (!exists()) mkdirs() }
                        val outFile = File(dir, filename)
                        srcFile.inputStream().use { inStream ->
                            FileOutputStream(outFile).use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        runOnUiThread {
                            Toast.makeText(context, "📥 APK saved: ${outFile.absolutePath} ✓", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                        val outFile = File(fallbackDir, filename)
                        srcFile.inputStream().use { inStream ->
                            FileOutputStream(outFile).use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        runOnUiThread {
                            Toast.makeText(context, "📥 APK saved: ${outFile.absolutePath} ✓", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(context, "APK export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        @JavascriptInterface
        fun shareApk() {
            Thread {
                try {
                    val srcFile = File(context.applicationInfo.sourceDir)
                    if (!srcFile.exists()) {
                        runOnUiThread {
                            Toast.makeText(context, "APK file unavailable", Toast.LENGTH_SHORT).show()
                        }
                        return@Thread
                    }
                    val sharedDir = File(context.cacheDir, "shared")
                    if (!sharedDir.exists()) sharedDir.mkdirs()
                    val outFile = File(sharedDir, "CrossBarBD.apk")
                    srcFile.inputStream().use { inStream ->
                        FileOutputStream(outFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        outFile
                    )
                    runOnUiThread {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/vnd.android.package-archive"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, "CrossBar BD Android App (APK)")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share CrossBar BD APK"))
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(context, "Share APK error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
