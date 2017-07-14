package ru.anisart.vw

import android.Manifest
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import butterknife.BindView
import butterknife.ButterKnife
import com.dd.processbutton.iml.ActionProcessButton
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.rxkotlin.toObservable
import io.reactivex.schedulers.Schedulers
import java.io.*
import kotlin.collections.ArrayList
import permissions.dispatcher.*

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    val explorerDirPath = "osmand/tiles/Explorer/14/"
    val tileExt = ".png.tile"
    val explorerAssetName = "explorer.png"
    val notExplorerAssetName = "not_explorer.png"
    var explorerTiles = ArrayList<Pair<String, String>>()

    @BindView(R.id.webView)
    lateinit var webView: WebView
    @BindView(R.id.progressBar)
    lateinit var progressBar: ProgressBar
    @BindView(R.id.updateBtn)
    lateinit var updateBtn: Button
    @BindView(R.id.recreateBtn)
    lateinit var recreateBtn: ActionProcessButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)

        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 Google"
        webView.setWebChromeClient(object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) {
                progressBar.progress = progress
            }
        })
        webView.addJavascriptInterface(this, "JSInterface")
//        webView.setOnTouchListener { v, event -> true }  // DISABLE TOUCH

        recreateBtn.setMode(ActionProcessButton.Mode.PROGRESS)
        recreateBtn.setOnClickListener { v ->
            run {
                webView.setWebViewClient(object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        view?.loadUrl(url)
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (url == null || !url.contains("veloviewer.com/\\S*activities".toRegex())) {
                            return
                        }
                        view?.loadUrl("javascript: " +
                                "document.getElementById('viewMapCheckBox').click(); " +
                                "document.getElementById('showExplorerCheckBox').click(); " +
                                "var features = []; " +
                                "liveData.forEach( function(d, i) { if (d.mapFeature != null) features.push(d.mapFeature); } ); " +
                                "var gpx = new OpenLayers.Format.GPX({ 'internalProjection': toProjection, 'externalProjection': fromProjection }); " +
                                "window.JSInterface.setAllRidesGpx(gpx.write(features)); " +
                                "window.JSInterface.setExplorerTiles(Object.keys(window.explorerTiles)); " +
                                "document.getElementById('viewMapCheckBox').click();")
                        System.out.println(url)
                    }
                })
                webView.loadUrl("https://veloviewer.com/activities")
            }
        }

        updateBtn.setOnClickListener { v ->
            run {
                webView.setWebViewClient(object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        view?.loadUrl(url)
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (url == null || !url.contains("veloviewer.com/athlete/\\d+/update".toRegex())) {
                            return
                        }
                        view?.loadUrl("javascript: " +
                                "document.getElementById('GetNewActivities').click();")
                    }
                })
                webView.loadUrl("https://veloviewer.com/update")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults)
    }

    @JavascriptInterface
    fun setExplorerTiles(tiles: Array<String>?) {
        if (tiles == null) return

        explorerTiles.clear()
        for (tile in tiles) {
            explorerTiles.add(Pair(tile.substringBefore('-', "-1"), tile.substringAfter('-', "-1")))
        }

        MainActivityPermissionsDispatcher.createTilesWithCheck(this)
    }

    @JavascriptInterface
    fun setAllRidesGpx(gpx: String) {
        Observable.just(gpx)
                .map { s -> createGpxFile(s) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ Toast.makeText(this, "GPX saved!", Toast.LENGTH_SHORT).show() })
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

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun createTiles() {
        if (!createDirs()) return
        var completeCount = 0
        runOnUiThread { recreateBtn.progress = 0 }
        explorerTiles.toObservable()
                .doOnNext { (first, second) -> copyAssetToDir(explorerAssetName, first, second) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    completeCount += 1
                    recreateBtn.progress = completeCount * 100 / explorerTiles.size
                })
    }

    fun copyAssetToDir(assetName: String, dirName: String, fileName: String) {
        val dir = File(Environment.getExternalStorageDirectory(), explorerDirPath + dirName)
        dir.mkdir()
        val file = File(dir, fileName + tileExt)
        val inStream = assets.open(assetName)
        val outStream = FileOutputStream(file)
        copyFile(inStream, outStream)
        inStream.close()
        outStream.flush()
        outStream.close()
    }

    fun copyFile(inStream: InputStream, out: OutputStream) {
        val buffer = ByteArray(1024)
        var read = inStream.read(buffer)
        while (read != -1) {
            out.write(buffer, 0, read)
            read = inStream.read(buffer)
        }
    }

    fun createGpxFile(gpx: String) {
        val file = File(Environment.getExternalStorageDirectory(), "osmand/tracks/all_rides_test.gpx")
        val fileWriter = FileWriter(file)
        fileWriter.append(gpx)
        fileWriter.flush()
        fileWriter.close()
    }


}
