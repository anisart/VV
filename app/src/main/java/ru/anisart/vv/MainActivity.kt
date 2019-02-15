package ru.anisart.vv

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.codekidlabs.storagechooser.StorageChooser
import com.codekidlabs.storagechooser.utils.DiskUtil
import com.dd.processbutton.iml.ActionProcessButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.toObservable
import io.reactivex.schedulers.Schedulers
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnNeverAskAgain
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import java.io.*
import java.util.*

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    var osmandFolder = ""
    val explorerDirPath = "tiles/Explorer/14/"
    val tileExt = ".png.tile"
    val explorerAssetName = "explorer.png"
    val clusterAssetName = "cluster.png"
    val metaAssetName = "metainfo"
    val allRidesFileName = "tracks/VV_all_rides.gpx"
    var explorerTiles = HashSet<Tile>()

    lateinit var preferences: SharedPreferences
    lateinit var firebaseAnalytics: FirebaseAnalytics

    @BindView(R.id.webView)
    lateinit var webView: WebView
    @BindView(R.id.progressBar)
    lateinit var progressBar: ProgressBar
    @BindView(R.id.recreateBtn)
    lateinit var recreateBtn: ActionProcessButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 Google"
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) {
                progressBar.progress = progress
            }
        }
        webView.addJavascriptInterface(this, "JSInterface")
        recreateBtn.setMode(ActionProcessButton.Mode.PROGRESS)
    }

//    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        menuInflater.inflate(R.menu.menu_main, menu)
//        return true
//    }
//
//    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
//        if (item?.itemId == R.id.action_map) {
//            startActivity(Intent(this, MapActivity::class.java))
//                    return true
//        }
//        return super.onOptionsItemSelected(item)
//    }

//    @SuppressLint("NeedOnRequestPermissionsResult")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun onStoragePermissionDenied() {
        Toast.makeText(this, "Permission is required to create tiles for OsmAnd!", Toast.LENGTH_SHORT).show()
    }

    @OnNeverAskAgain(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun onStorageNeverAskAgain() {
        Toast.makeText(this, "Check permissions for app in System Settings!", Toast.LENGTH_SHORT).show()
    }

    @OnClick(R.id.osmandBtn)
    fun onOsmandButtonClick() {
        selectOsmandFolderWithPermissionCheck()
    }

    @OnClick(R.id.updateBtn)
    fun onUpdateClick() {
        setupWebviewForUpdate()
        setResult(Activity.RESULT_OK)
    }

    @OnClick(R.id.recreateBtn)
    fun onRecleateButtonClick() {
        osmandFolder = preferences.getString(DiskUtil.SC_PREFERENCE_KEY, "")
        if (osmandFolder.isEmpty()) {
//            Toast.makeText(this, "You need select OsmAnd data folder!", Toast.LENGTH_SHORT).show()
//            return
            setupWebviewForExplorer()
        } else {
            setupWebviewForExplorerWithPermissionCheck()
        }
        setResult(Activity.RESULT_OK)
    }

//    @OnClick(R.id.mapBtn)
//    fun onMapButtonClick(v: View) {
//        startActivity(Intent(this, MapActivity::class.java))
//    }

    @JavascriptInterface
    fun setExplorerTiles(tiles: Array<String>?, clusterTiles: Array<String>?) {
        if (tiles == null || clusterTiles == null) return

        explorerTiles.clear()
        tiles.forEach { explorerTiles.add(Tile.fromString(it)) }
        clusterTiles.forEach { explorerTiles.addReplace(Tile.fromString(it).apply { isCluster = true }) }
        preferences.edit { putString(App.PREFERENCE_TILES, explorerTiles.toJson()) }

        Toast.makeText(this, "[MAP] Explorer tiles has been recreated!", Toast.LENGTH_SHORT).show()

        if (!osmandFolder.isEmpty()) {
            createTiles()
        }

        firebaseAnalytics.setUserProperty(Analytics.explorerTiles, tiles.size.toString())
        firebaseAnalytics.setUserProperty(Analytics.clusterTiles, clusterTiles.size.toString())
    }

    @JavascriptInterface
    fun setMaxSquares(json: String?) {
        preferences.edit { putString(App.PREFERENCE_MAX_SQUARES, json) }
    }

    @JavascriptInterface
    fun setAllRidesGpx(gpx: String?) {
        if (osmandFolder.isEmpty()) {
            return
        }
        Observable.just(gpx)
                .map { s -> createGpxFile(s) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    println("setAllRidesGpx: ok")
                    Toast.makeText(this, "All rides has been saved!", Toast.LENGTH_SHORT).show()
                }, { e -> run {
                    e.printStackTrace()
                    Toast.makeText(this, "Error during creating rides file.", Toast.LENGTH_SHORT).show()
                }})
    }

    @JavascriptInterface
    fun setAllRidesJson(json: String?) {
        Observable.just(json)
                .map { s -> saveGeoJson(s) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    println("setAllRidesJson: ok")
                    Toast.makeText(this, "[MAP] All rides has been saved!", Toast.LENGTH_SHORT).show()
                }, { e -> run {
                    e.printStackTrace()
                    Toast.makeText(this, "Error during creating rides file.", Toast.LENGTH_SHORT).show()
                }})
    }

    @JavascriptInterface
    fun setRidesCount(count: String?) {
        firebaseAnalytics.setUserProperty(Analytics.activities, count)
    }

    private fun createDirs(): Boolean {
        val explorerDir = File(osmandFolder, explorerDirPath)
        if (!explorerDir.exists()) {
            if (!explorerDir.mkdirs()) {
                return false
            }
        }
        if (!File(explorerDir.parent, metaAssetName).exists()) {
            copyAssetTFile(metaAssetName, File(explorerDir.parent, ".$metaAssetName"))
        }
        return true
    }

    private fun createTiles() {
        if (!createDirs()) return
        var completeCount = 0
        runOnUiThread { recreateBtn.progress = 0 }
        explorerTiles.toObservable()
                .doOnNext {
                    copyAssetTFile(
                            if (it.isCluster) clusterAssetName else explorerAssetName,
                            File(osmandFolder, explorerDirPath + it.x + "/" + it.y + tileExt))
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    completeCount += 1
                    recreateBtn.progress = completeCount * 100 / explorerTiles.size
                    if (recreateBtn.progress == 100) {
                        println("createTiles: ok")
                        Toast.makeText(this, "Explorer tiles has been recreated!", Toast.LENGTH_SHORT).show()
                    }
                }, { e -> run {
                    e.printStackTrace()
                    Toast.makeText(this, "Error during creating tiles.", Toast.LENGTH_SHORT).show()
                }})
    }

    private fun copyAssetTFile(assetName: String, file: File) {
        file.parentFile.mkdirs()
        val inStream = assets.open(assetName)
        val outStream = FileOutputStream(file)
        copyFile(inStream, outStream)
        inStream.close()
        outStream.flush()
        outStream.close()
    }

    private fun copyFile(inStream: InputStream, out: OutputStream) {
        val buffer = ByteArray(1024)
        var read = inStream.read(buffer)
        while (read != -1) {
            out.write(buffer, 0, read)
            read = inStream.read(buffer)
        }
    }

    private fun createGpxFile(gpx: String) {
        val file = File(osmandFolder, allRidesFileName)
        file.parentFile.mkdirs()
        file.createNewFile()
        val fileWriter = FileWriter(file)
        fileWriter.append(gpx)
        fileWriter.flush()
        fileWriter.close()
    }

    private fun saveGeoJson(json: String) {
        preferences.edit { putString(App.PREFERENCE_RIDES_JSON, json) }
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun selectOsmandFolder() {
        val chooser = StorageChooser.Builder()
                .withActivity(this)
                .withFragmentManager(fragmentManager)
                .allowCustomPath(true)
                .setType(StorageChooser.DIRECTORY_CHOOSER)
                .actionSave(true)
                .withPreference(preferences)
                .build()

        // Show dialog whenever you want by
        chooser.show()

        // get path that the user has chosen
        chooser.setOnSelectListener { path -> Toast.makeText(this, path, Toast.LENGTH_SHORT).show() }
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun setupWebviewForExplorer() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url == null || !url.contains("veloviewer.com/\\S*activities".toRegex())) {
                    webView.setOnTouchListener(null)
                    return
                }
                webView.setOnTouchListener { _, _ -> true }  // DISABLE TOUCH
                view?.loadUrl("""javascript:
                    |var togpx;
                    |var script = document.createElement("script");
                    |script.setAttribute("type", "text/javascript");
                    |script.setAttribute("src", "https://cdn.jsdelivr.net/npm/togpx@0.5.4/togpx.js");
                    |document.getElementsByTagName("head")[0].appendChild(script);

                    |function wait(condition, callback) {
                    |    if (typeof condition() !== "undefined") {
                    |        callback();
                    |    } else {
                    |        setTimeout(function () {
                    |            wait(condition, callback);
                    |        }, 0)
                    |    }
                    |}

                    |document.getElementById('viewMapCheckBox').click();
                    |document.getElementById('viewMapCheckBox').click();
                    |var collection = { "type": "FeatureCollection", "features": [] };
                    |liveData.forEach( function(d, i) { if (d.ll != null) collection.features.push(d.ll.toGeoJSON()); } );
                    |window.JSInterface.setAllRidesJson(JSON.stringify(collection));
                    |window.JSInterface.setRidesCount(String(collection.features.length));
                    |wait( function() { return togpx; }, function() {
                    |   window.JSInterface.setAllRidesGpx(togpx(collection));
                    |});
                    |wait( function() { return window.maxClump; }, function() {
                    |    window.JSInterface.setExplorerTiles(Object.keys(window.explorerTiles), Object.keys(window.maxClump));
                    |    window.JSInterface.setMaxSquares(JSON.stringify(window.explorerMaxs.map( function(max) {
                    |       square = {};
                    |       square.x = max.x;
                    |       square.y = max.y;
                    |       square.size = max.size;
                    |       return square;
                    |   })));
                    |});"""
                        .trimMargin())
            }
        }
        CookieManager.getInstance().setCookie(".veloviewer.com", "ExplorerMaxSquareShown=1")
        webView.loadUrl("https://veloviewer.com/activities")
    }

    private fun setupWebviewForHeatmap() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                setupWebviewForUpdate()
            }
        }
        webView.loadUrl("https://www.strava.com/heatmap")
    }

    private fun setupWebviewForUpdate() {
        val keyPairId = getCookie("CloudFront-Key-Pair-Id")
        val policy = getCookie("CloudFront-Policy")
        val signature = getCookie("CloudFront-Signature")

        if (keyPairId != null) {
            preferences.edit { putString(App.PREFERENCE_HEATMAP_AUTH, "?Key-Pair-Id=$keyPairId&Policy=$policy&Signature=$signature") }
        } else {
            preferences.edit { putString(App.PREFERENCE_HEATMAP_AUTH, null) }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url == null || !url.contains("veloviewer.com/athlete/\\d+/update".toRegex())) {
                    webView.setOnTouchListener(null)
                    return
                }
                webView.setOnTouchListener { _, _ -> true }  // DISABLE TOUCH
                if (getCookie("CloudFront-Key-Pair-Id") == null) {
                    setupWebviewForHeatmap()
                } else {
                    view?.loadUrl("javascript: " +
                            "document.getElementById('GetNewActivities').click();")
                }
            }
        }
        webView.loadUrl("https://veloviewer.com/update")
    }

    private fun getCookie(CookieName: String): String? {
        val stravaUrl = "https://www.strava.com"
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(stravaUrl) ?: return null
        val temp = cookies.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return temp
                .filter { it.contains(CookieName) }
                .map { ar1 -> ar1.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray() }
                .firstOrNull()
                ?.let { it[1] }
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun mockRecreateTilesAndRides() {
        osmandFolder = Environment.getExternalStorageDirectory().absolutePath
        setAllRidesGpx(Mock.instance.geojson)
        setAllRidesJson(Mock.instance.geojson)
        setExplorerTiles(Mock.instance.explorerTiles, Mock.instance.clusterTiles)
    }
}
