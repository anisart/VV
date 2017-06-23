package ru.anisart.vw

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import android.widget.ProgressBar
import java.io.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    val explorerDirPath = "osmand/tiles/Explorer/14/"
    val tileExt = ".png.tile"
    val explorerAssetName = "explorer.png"
    val notExplorerAssetName = "not_explorer.png"
    var explorerTiles = ArrayList<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView = findViewById(R.id.webView) as WebView
        val progressBar = findViewById(R.id.progressBar) as ProgressBar
        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.javaScriptEnabled = true
        webView.setWebChromeClient(object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) {
                progressBar.progress = progress
            }
        })
        webView.setWebViewClient(object: WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.loadUrl("javascript: " +
                        "document.getElementById('viewMapCheckBox').click(); " +
                        "document.getElementById('showExplorerCheckBox').click(); " +
                        "window.JSInterface.setExplorerTiles(Object.keys(window.explorerTiles));" +
                        "document.getElementById('viewMapCheckBox').click(); ")
            }
        })
        webView.addJavascriptInterface(this, "JSInterface")

        webView.loadUrl("https://veloviewer.com/athlete/4753179/activities")
    }

    @JavascriptInterface
    fun setExplorerTiles(tiles: Array<String>?) {
        if (tiles == null) return

        explorerTiles.clear()
        for (tile in tiles) {
            explorerTiles.add(Pair(tile.substringBefore('-', "-1"), tile.substringAfter('-', "-1")))
        }
        System.out.println(explorerTiles)

        if (!createDirs()) return
        createTiles()
    }

    fun createDirs(): Boolean {
        val explorerDir = File(Environment.getExternalStorageDirectory(), explorerDirPath)
        if (!explorerDir.exists()) {
            if (!explorerDir.mkdirs()) {
                return false
            }
        }
        return true
    }

    fun createTiles() {
        for (tile in explorerTiles) {
            copyAssetToDir(explorerAssetName, tile.first, tile.second)
        }
    }

    fun copyAssetToDir(assetName: String, dirName: String, fileName: String) {
        val afd = assets.openFd(assetName)
        val file = File(Environment.getExternalStorageDirectory(), explorerDirPath + dirName + File.separator + fileName + tileExt)
        file.createNewFile()
        copyFdToFile(afd.fileDescriptor, file)
    }

    @Throws(IOException::class)
    fun copyFdToFile(src: FileDescriptor, dst: File) {
        val inChannel = FileInputStream(src).channel
        val outChannel = FileOutputStream(dst).channel
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel)
        } finally {
            inChannel?.close()
            outChannel?.close()
        }
    }


}
