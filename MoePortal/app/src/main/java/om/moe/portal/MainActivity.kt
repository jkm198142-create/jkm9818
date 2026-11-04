package om.moe.portal

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val PORTAL_URL =
            "https://script.google.com/macros/s/AKfycbyqE1jHhVYiUA-xCeiq7LlDXJzuWsELg9cirQvY6JOq_mYF7mzEvOufbQr6iW-nnrPV/exec"
        private val ALLOWED_HOSTS = listOf("google.com", "googleusercontent.com", "gstatic.com", "googleapis.com")
    }

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var printView: WebView? = null
    private val ui = Handler(Looper.getMainLooper())

    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false      // لتشغيل صوت الترحيب
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString + " MoePortalApp/1.0"
        }
        web.addJavascriptInterface(Bridge(), "AndroidApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val host = u.host ?: ""
                val ok = u.scheme == "https" && ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
                if (ok) return false
                openExternal(u)
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) view.loadUrl("file:///android_asset/offline.html")
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {}
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    chooser.launch(params.createIntent()); true
                } catch (e: Exception) {
                    fileCallback = null; false
                }
            }

            // روابط target=_blank (واتساب، المرفقات، الاتصال) تُفتح خارج التطبيق
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val tmp = WebView(this@MainActivity)
                tmp.webViewClient = object : WebViewClient() {
                    private var handled = false
                    private fun go(u: Uri?) {
                        if (handled || u == null || u.toString() == "about:blank") return
                        handled = true
                        openExternal(u)
                        ui.post { tmp.destroy() }
                    }
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean { go(r.url); return true }
                    override fun onPageStarted(v: WebView, url: String?, f: Bitmap?) { if (url != null) go(Uri.parse(url)) }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = tmp
                resultMsg.sendToTarget()
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(PORTAL_URL)
    }

    private fun openExternal(u: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر فتح الرابط", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        printView?.destroy()
        web.destroy()
        super.onDestroy()
    }

    /** الجسر بين صفحة البوابة والتطبيق (حفظ الملفات والطباعة) */
    inner class Bridge {
        @JavascriptInterface
        fun saveFile(name: String, mime: String, base64: String) {
            Thread {
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safe)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("insert failed")
                    contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                    ui.post { Toast.makeText(this@MainActivity, "تم حفظ الملف في مجلد التنزيلات: $safe", Toast.LENGTH_LONG).show() }
                } catch (e: Exception) {
                    ui.post { Toast.makeText(this@MainActivity, "تعذّر حفظ الملف", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }

        @JavascriptInterface
        fun printHtml(title: String, html: String) {
            ui.post {
                val pv = WebView(this@MainActivity)
                printView = pv
                pv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView, url: String?) {
                        val pm = getSystemService(PRINT_SERVICE) as PrintManager
                        pm.print(
                            title, v.createPrintDocumentAdapter(title),
                            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
                        )
                    }
                }
                pv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    }
}
