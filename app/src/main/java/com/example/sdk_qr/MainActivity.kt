package com.example.sdk_qr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var flashButton: Button
    private lateinit var zoomButton: Button
    private lateinit var galleryButton: Button
    private var isFlashOn = false
    private lateinit var cameraControl: CameraControl
    private lateinit var frameLayout: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        frameLayout = findViewById(R.id.frameLayout)

        flashButton = findViewById(R.id.flashButton)
        zoomButton = findViewById(R.id.zoomButton)
        galleryButton = findViewById(R.id.galleryButton)

        setupButtons()
        requestCameraPermission()
    }

    private fun setupButtons() {
        flashButton.setOnClickListener {
            isFlashOn = !isFlashOn
            cameraControl.enableTorch(isFlashOn)
        }

        zoomButton.setOnClickListener {
            cameraControl.setLinearZoom(0.5f) // Zoom to 50%
        }

        galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, GALLERY_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraPreview(cameraProvider)
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

        cameraControl = camera.cameraControl

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), QRCodeAnalyzer())
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    inner class QRCodeAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            val buffer: ByteBuffer = image.planes[0].buffer
            val data = ByteArray(buffer.capacity())
            buffer.get(data)
            val intData = byteArrayToIntArray(data)

            val width = image.width
            val height = image.height
            val source: LuminanceSource = RGBLuminanceSource(width, height, intData)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result: Result = QRCodeMultiReader().decode(binaryBitmap)
                val qrCodeText: String = result.text
                Log.d("QRCodeAnalyzer", "QR Code detected: $qrCodeText")

                // Vibrate on QR detection
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(200)

                // Draw a box around the QR code
                runOnUiThread { drawBoundingBox() }

                redirectToUrl(qrCodeText) // Handle redirection here
            } catch (e: Exception) {
                // No QR code detected
            }

            image.close()
        }

        private fun redirectToUrl(url: String) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(browserIntent)
        }

        private fun byteArrayToIntArray(byteArray: ByteArray): IntArray {
            return byteArray.map { it.toInt() and 0xFF }.toIntArray()
        }
    }

    private fun drawBoundingBox() {
        val box = FrameLayout(this)
        val layoutParams = FrameLayout.LayoutParams(400, 400) // Size of the bounding box
        layoutParams.leftMargin = (previewView.width / 2) - 200
        layoutParams.topMargin = (previewView.height / 2) - 200
        box.layoutParams = layoutParams
        box.setBackgroundResource(R.drawable.bounding_box) // Create a drawable resource for the box

        frameLayout.removeAllViews()
        frameLayout.addView(box)
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val GALLERY_REQUEST_CODE = 1002
    }
}
