package ru.anisart.vv.util

import android.os.Environment.DIRECTORY_PICTURES
import android.os.Environment.getExternalStoragePublicDirectory
import android.support.test.runner.screenshot.BasicScreenCaptureProcessor
import java.io.File

class MyScreenCaptureProcessor(parentFolderPath: String) : BasicScreenCaptureProcessor() {

    init {
        this.mDefaultScreenshotPath = File(
                File(
                        getExternalStoragePublicDirectory(DIRECTORY_PICTURES),
                        "VV"
                ).absolutePath,
                "screenshots/$parentFolderPath"
        )
    }

    override fun getFilename(prefix: String): String = prefix
}