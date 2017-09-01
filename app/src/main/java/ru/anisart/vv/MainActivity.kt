package ru.anisart.vv

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.dd.processbutton.iml.ActionProcessButton
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.toObservable
import io.reactivex.schedulers.Schedulers
import java.io.*
import kotlin.collections.ArrayList
import permissions.dispatcher.*
import com.codekidlabs.storagechooser.StorageChooser
import com.codekidlabs.storagechooser.utils.DiskUtil

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    var osmandFolder = ""
    val explorerDirPath = "tiles/Explorer/14/"
    val tileExt = ".png.tile"
    val explorerAssetName = "explorer.png"
    val notExplorerAssetName = "not_explorer.png"
    val metaAssetName = "metainfo"
    val allRidesFileName = "tracks/VV_all_rides.gpx"
    var explorerTiles = ArrayList<Pair<String, String>>()

    lateinit var preferences: SharedPreferences

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
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.javaScriptEnabled = true
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults)
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
    fun onOsmandButtonClick(v: View) {
        MainActivityPermissionsDispatcher.selectOsmandFolderWithCheck(this)
    }

    @OnClick(R.id.updateBtn)
    fun onUpdateClick(v: View) {
        setupWebviewForUpdate()
    }

    @OnClick(R.id.recreateBtn)
    fun onRecleateButtonClick(v: View) {
        osmandFolder = preferences.getString(DiskUtil.SC_PREFERENCE_KEY, "")
//        if (osmandFolder.isEmpty()) {
//            Toast.makeText(this, "You need select OsmAnd data folder!", Toast.LENGTH_SHORT).show()
//            return
//        } else {
            MainActivityPermissionsDispatcher.setupWebviewForExplorerWithCheck(this)
//        }
//        MainActivityPermissionsDispatcher.mockRecteateTilesAndRidesWithCheck(this)
    }

    @OnClick(R.id.mapBtn)
    fun onMapButtonClick(v: View) {
        startActivity(Intent(this, MapActivity::class.java))
    }

    @JavascriptInterface
    fun setExplorerTiles(tiles: Array<String>?) {
        if (tiles == null) return

        preferences.edit()
                .putStringSet(App.PREFERENCE_TILES, tiles.toSet())
                .apply()

        explorerTiles.clear()
        for (tile in tiles) {
            explorerTiles.add(Pair(tile.substringBefore('-', "-1"), tile.substringAfter('-', "-1")))
        }

        if (!osmandFolder.isEmpty()) {
            createTiles()
        }
    }

    @JavascriptInterface
    fun setAllRidesGpx(gpx: String) {
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
    fun setAllRidesJson(json: String) {
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

    private fun createDirs(): Boolean {
        val explorerDir = File(osmandFolder, explorerDirPath)
        if (!explorerDir.exists()) {
            if (!explorerDir.mkdirs()) {
                return false
            }
        }
        if (!File(explorerDir.parent, metaAssetName).exists()) {
            copyAssetTFile(metaAssetName, File(explorerDir.parent, "." + metaAssetName))
        }
        return true
    }

    private fun createTiles() {
        if (!createDirs()) return
        var completeCount = 0
        runOnUiThread { recreateBtn.progress = 0 }
        explorerTiles.toObservable()
                .doOnNext { (first, second) -> copyAssetTFile(explorerAssetName,
                        File(osmandFolder, explorerDirPath + first + "/" + second + tileExt)) }
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
        preferences.edit()
                .putString(App.PREFERENCE_RIDES_JSON, json)
                .apply()
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
                System.out.println(url)
                if (url == null || !url.contains("veloviewer.com/\\S*activities".toRegex())) {
                    webView.setOnTouchListener(null)
                    return
                }
                webView.setOnTouchListener { _, _ -> true }  // DISABLE TOUCH
                view?.loadUrl("javascript: " +
                        "document.getElementById('viewMapCheckBox').click(); " +
                        "document.getElementById('showExplorerCheckBox').click(); " +
                        "var features = []; " +
                        "liveData.forEach( function(d, i) { if (d.mapFeature != null) features.push(d.mapFeature); } ); " +
                        "var gpx = new OpenLayers.Format.GPX({ 'internalProjection': toProjection, 'externalProjection': fromProjection }); " +
                        "window.JSInterface.setAllRidesGpx(gpx.write(features)); " +
                        "var geojson = new OpenLayers.Format.GeoJSON({ 'internalProjection': toProjection, 'externalProjection': fromProjection }); " +
                        "window.JSInterface.setAllRidesJson(geojson.write(features)); " +
                        "window.JSInterface.setExplorerTiles(Object.keys(window.explorerTiles)); " +
                        "document.getElementById('viewMapCheckBox').click();")
            }
        }
        webView.loadUrl("https://veloviewer.com/activities")
    }

    fun setupWebviewForUpdate() {
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
                view?.loadUrl("javascript: " +
                        "document.getElementById('GetNewActivities').click();")
            }
        }
        webView.loadUrl("https://veloviewer.com/update")
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun mockRecteateTilesAndRides() {
        osmandFolder = Environment.getExternalStorageDirectory().absolutePath
        setAllRidesGpx(Mock.instance.geojson)
        setAllRidesJson(Mock.instance.geojson)
        setExplorerTiles(Mock.instance.explorerTiles)
    }
}
