package com.example.sdk_qr

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var flashButton: Button
    private lateinit var zoomButton: Button
    private lateinit var galleryButton: Button
    private var isFlashOn = false
    private lateinit var cameraControl: CameraControl
    private lateinit var frameLayout: FrameLayout
    private var zoomLevel = 0.0f
    private var hasVibrated = false

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
            zoomLevel = if (zoomLevel == 0.0f) {
                0.5f
            } else {
                0.0f
            }
            cameraControl.setLinearZoom(zoomLevel)
        }

        galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, GALLERY_REQUEST_CODE)
        }
    }

    @Deprecated("Deprecated in Java")
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val selectedImageUri: Uri? = data.data
            if (selectedImageUri != null) {
                try {
                    // Get the bitmap from the selected image
                    val bitmap = loadBitmapFromUri(selectedImageUri)

                    // Pass the bitmap to the QR code scanning function
                    bitmap?.let {
                        scanQRCodeFromBitmap(it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        // Load the bitmap from the content resolver
        val source = ImageDecoder.createSource(this.contentResolver, uri)
        val originalBitmap = ImageDecoder.decodeBitmap(source)

        // Check if the bitmap uses Config.HARDWARE and copy it to a mutable bitmap
        return if (originalBitmap.config == Bitmap.Config.HARDWARE) {
            originalBitmap.copy(Bitmap.Config.ARGB_8888, true) // Copy to ARGB_8888
        } else {
            originalBitmap
        }
    }

    private fun scanQRCodeFromBitmap(bitmap: Bitmap) {
        // Convert the bitmap to an array of pixels
        val intArray = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // Create a luminance source for the QR code
        val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        try {
            // Decode the QR code
            val result: Result = QRCodeMultiReader().decode(binaryBitmap)
            val qrCodeText: String = result.text
            Log.d("QRCodeAnalyzer", "QR Code detected: $qrCodeText")

            // Handle the detected QR code (e.g., redirect or perform some action)
            redirectToUrl(qrCodeText)

        } catch (e: Exception) {
            Log.e("QRCodeAnalyzer", "Error decoding QR Code", e)
        }
    }

    private fun redirectToUrl(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app can handle this URL", Toast.LENGTH_SHORT).show()
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

        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), QRCodeAnalyzer())
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    inner class QRCodeAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            val yBuffer = image.planes[0].buffer // Y plane
            val yData = ByteArray(yBuffer.capacity())
            yBuffer.get(yData)

            try {
                // Capture the image dimensions
                val width = image.width
                val height = image.height

                // Handle YUV image data using PlanarYUVLuminanceSource
                val source = PlanarYUVLuminanceSource(
                    yData,
                    width, height,
                    0, 0,  // Crop the full image
                    width, height,
                    false  // No need to reverse horizontally
                )
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                // Prepare decoding hints
                val hints = mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )

                val multiFormatReader = QRCodeMultiReader()
                val result: Result = multiFormatReader.decode(binaryBitmap, hints) // Pass hints

                val qrCodeText: String = result.text
                Log.d("QRCodeAnalyzer", "QR Code detected: $qrCodeText")

                if (!hasVibrated) {
                    try {
                        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        vibrator.vibrate(200)
                        hasVibrated = true // Set flag to prevent further vibration
                    } catch (e: Exception) {
                        Log.d("HapticHandling", "Vibration failed")
                    }
                }

                // Redirect to the URL or handle the QR code text
                redirectToUrl(qrCodeText)

            } catch (e: NotFoundException) {
                // QR code not found in this frame, continue scanning
                Log.e("QRCodeAnalyzer", "QR Code not found in image")
            } catch (e: ChecksumException) {
                // QR code detected but there was a checksum error
                Log.e("QRCodeAnalyzer", "Checksum error decoding QR code", e)
            } catch (e: FormatException) {
                // The format of the QR code could not be parsed
                Log.e("QRCodeAnalyzer", "Format error decoding QR code", e)
            } catch (e: Exception) {
                // Catch any other unexpected exceptions
                Log.e("QRCodeAnalyzer", "Unknown error decoding QR code", e)
            } finally {
                // Always close the image when done
                image.close()
            }
        }

        private fun redirectToUrl(url: String) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(browserIntent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, "No app can handle this URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val GALLERY_REQUEST_CODE = 1002
    }
}
