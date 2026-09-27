package com.himu.vercelapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var errorView: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var menuButton: ImageButton
    private var fileChooserCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private val fileChooserRequest = 7001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)

        buildUi()
        configureWebView()

        if (savedInstanceState == null) webView.loadUrl("https://vercel.com/dashboard")
        else webView.restoreState(savedInstanceState)
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(8), dp(7))
            setBackgroundColor(Color.rgb(5, 5, 5))
        }

        val logo = TextView(this).apply {
            text = "▲"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), 0, dp(8), 0)
        }

        titleView = TextView(this).apply {
            text = "Vercel"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        toolbar.addView(logo, LinearLayout.LayoutParams(dp(34), dp(44)))
        toolbar.addView(titleView, LinearLayout.LayoutParams(0, dp(44), 1f))

        backButton = toolbarButton("‹", "Back") { if (webView.canGoBack()) webView.goBack() }
        forwardButton = toolbarButton("›", "Forward") { if (webView.canGoForward()) webView.goForward() }
        refreshButton = toolbarButton("↻", "Refresh") { webView.reload() }
        menuButton = toolbarButton("⋮", "About") { showAbout() }

        toolbar.addView(backButton)
        toolbar.addView(forwardButton)
        toolbar.addView(refreshButton)
        toolbar.addView(menuButton)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable?.setTint(Color.WHITE)
            visibility = View.GONE
        }

        swipe = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.WHITE)
            setProgressBackgroundColorSchemeColor(Color.rgb(20, 20, 20))
        }

        webView = WebView(this)
        swipe.addView(webView, SwipeRefreshLayout.LayoutParams(-1, -1))

        errorView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        val errorTitle = TextView(this).apply {
            text = "Unable to load Vercel"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val errorMessage = TextView(this).apply {
            text = "Check your internet connection and try again."
            textSize = 13f
            setTextColor(Color.rgb(150, 150, 150))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(18))
        }
        val retry = TextView(this).apply {
            text = "Retry"
            textSize = 14f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(28), dp(12), dp(28), dp(12))
            setOnClickListener { errorView.visibility = View.GONE; webView.reload() }
        }
        errorView.addView(errorTitle)
        errorView.addView(errorMessage)
        errorView.addView(retry)

        page.addView(toolbar)
        page.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        page.addView(swipe, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(page)
        root.addView(errorView, FrameLayout.LayoutParams(-1, -1))

        swipe.setOnRefreshListener { webView.reload() }
        updateNavigation()
    }

    private fun configureWebView() {
        webView.setBackgroundColor(Color.BLACK)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = false
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString + " VercelAndroid/1.2"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                if (newProgress >= 100) swipe.isRefreshing = false
                updateNavigation()
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                titleView.text = if (title.isNullOrBlank()) "Vercel" else title
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                return try {
                    val intent = fileChooserParams.createIntent()
                    startActivityForResult(intent, fileChooserRequest)
                    true
                } catch (_: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                errorView.visibility = View.GONE
                progress.visibility = View.VISIBLE
                progress.progress = 8
                updateNavigation()
            }

            override fun onPageFinished(view: WebView, url: String) {
                swipe.isRefreshing = false
                progress.visibility = View.GONE
                updateNavigation()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) errorView.visibility = View.VISIBLE
                swipe.isRefreshing = false
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleUrl(request.url)

            @Deprecated("Deprecated in API 24")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleUrl(Uri.parse(url))
        }
    }

    private fun handleUrl(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return true
        if (scheme == "http" || scheme == "https") return false
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun updateNavigation() {
        if (!::webView.isInitialized) return
        backButton.alpha = if (webView.canGoBack()) 1f else 0.35f
        forwardButton.alpha = if (webView.canGoForward()) 1f else 0.35f
    }

    private fun toolbarButton(symbol: String, description: String, action: () -> Unit): ImageButton =
        ImageButton(this).apply {
            contentDescription = description
            setImageDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            val label = TextView(this@MainActivity).also {
                it.text = symbol
            }
            setOnClickListener { action() }
            tag = label
            val lp = LinearLayout.LayoutParams(dp(40), dp(44))
            lp.marginStart = dp(1)
            layoutParams = lp
        }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Vercel")
            .setMessage("Vercel Dashboard for Android\n\nDeveloped by Humayun Shariar Himu")
            .setPositiveButton("Close", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserRequest) {
            val result = if (resultCode == RESULT_OK && data?.data != null) {
                arrayOf(data.data!!)
            } else null
            fileChooserCallback?.onReceiveValue(result)
            fileChooserCallback = null
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = null
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
