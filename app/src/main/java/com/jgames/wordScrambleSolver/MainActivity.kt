package com.jgames.wordScrambleSolver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.android.synthetic.main.scanning_layout.*
import java.io.ByteArrayOutputStream
import java.lang.System.currentTimeMillis
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private val PERMISSION_ALL = 105
    private val PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA
    )
    private var state = ScanState.PRE_SCAN
    private lateinit var bottomButton: Button
    private lateinit var overlay: ImageView
    private lateinit var analyzedTextView: TextView
    private lateinit var previewView: PreviewView
    private lateinit var tessBaseApi: TessBaseAPI

    private enum class ScanState {
        PRE_SCAN,
        SCANNING,
        ANALYZING
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scanning_layout)
        bottomButton = begin_scan_button
        bottomButton.setOnClickListener {
            when (state) {
                ScanState.PRE_SCAN -> scanImage()
                ScanState.SCANNING -> analyzeBoard()
                ScanState.ANALYZING -> {
                }
            }
        }
        overlay = grid_overlay
        analyzedTextView = analyzed_text_view
        getPermissions(PERMISSIONS)
        tessBaseApi = TessBaseAPI()
        println(packageResourcePath)
        tessBaseApi.init("$packageResourcePath/raw", "eng")
    }

    private fun analyzeBoard() {
        state = ScanState.ANALYZING
    }

    private fun onBadScan() {
        bottomButton.isEnabled = false
        analyzedTextView.text = ""
    }

    private fun scanImage() {
        state = ScanState.SCANNING
        overlay.visibility = View.VISIBLE
        bottomButton.text = resources.getString(R.string.analyze_scan_text)
        bottomButton.isEnabled = false
        previewView = preview_view
        analyzedTextView.visibility = View.VISIBLE
    }

    private fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean {
        permissions.forEach {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false;
            }
        }
        return true;
    }

    private fun getPermissions(permissions: Array<String>) {
        if (hasAllPermissions(this, permissions)) {
            requestCameraProvider()
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_ALL)
        }
    }

    private fun requestCameraProvider() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            bindUseCases(cameraProviderFuture.get())
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun bindUseCases(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder().build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val camera = cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            preview,
            analysis
        )

        preview.setSurfaceProvider(
            preview_view.createSurfaceProvider(camera.cameraInfo)
        )

        analysis.setAnalyzer(ContextCompat.getMainExecutor(this), ImageAnalysis.Analyzer {
            if (state == ScanState.SCANNING) {
                it.image?.also { image ->
//                    analyzedTextView.text = "Started scan"
//                    val timeStart = currentTimeMillis()
//
//                    tessBaseApi.setImage(image.toBitmap())
//                    val board = tessBaseApi.utF8Text
//
//                    println("Scanned in: ${currentTimeMillis() - timeStart} millis {$board}")
//                    analyzedTextView.text = "Ended scan"
                }
            }
            it.close()
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasAllPermissions(this, PERMISSIONS)) {
            requestCameraProvider()
        }
    }

    override fun onStop() {
        super.onStop()
        tessBaseApi.end()
    }
}

private fun String.asBoard() =
    replace(".{${sqrt(length.toDouble()).roundToInt()}}".toRegex(), "$0\n")
        .replace(".".toRegex(), "$0 ").trim()

private fun Image.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer // Y
    val uBuffer = planes[1].buffer // U
    val vBuffer = planes[2].buffer // V

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    //U and V are swapped
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}
