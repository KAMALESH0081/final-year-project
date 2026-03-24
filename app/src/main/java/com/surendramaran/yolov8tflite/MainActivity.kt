package com.surendramaran.yolov8tflite

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener, LocationListener, NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    private val isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: Detector? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var locationManager: LocationManager

    private var alertClasses: MutableSet<String> = mutableSetOf()
    private var confidenceThresholds: MutableMap<String, Float> = mutableMapOf()
    private var globalConfidence: Float = 0.3f
    private var iouThreshold: Float = 0.5f
    private var cooldown: Int = 5
    private val lastAlertTimes = mutableMapOf<String, Long>()
    private val alertHistory = mutableListOf<AlertRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
            detector?.labels?.forEach {
                confidenceThresholds[it] = 0.5f
                alertClasses.add(it)
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setupDrawer()
        bindListeners()
    }

    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)

        val menu = binding.navView.menu

        // GPU Switch Initialization
        val gpuItem = menu.findItem(R.id.action_gpu)
        val gpuSwitch = gpuItem.actionView as SwitchCompat
        gpuSwitch.isChecked = true
        gpuSwitch.setOnCheckedChangeListener { _, isChecked ->
            cameraExecutor.submit {
                detector?.restart(isGpu = isChecked)
            }
        }

        // Initialize values
        (menu.findItem(R.id.action_global_confidence).actionView as? EditText)?.setText(globalConfidence.toString())
        (menu.findItem(R.id.action_iou_threshold).actionView as? EditText)?.setText(iouThreshold.toString())
        (menu.findItem(R.id.action_cooldown).actionView as? EditText)?.setText(cooldown.toString())
    }

    private fun startLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
    }

    private fun bindListeners() {
        binding.btnSettings.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider  = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview =  Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer =
                Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                if (isFrontCamera) {
                    postScale(
                        -1f,
                        1f,
                        imageProxy.width.toFloat(),
                        imageProxy.height.toFloat()
                    )
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            detector?.detect(rotatedBitmap)
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true && permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startCamera()
            startLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        cameraExecutor.shutdown()
        locationManager.removeUpdates(this)
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()){
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf (
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).toTypedArray()
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            binding.overlay.clear()
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }

            val currentTime = System.currentTimeMillis()
            val alertedClassesInFrame = mutableSetOf<String>()
            for (box in boundingBoxes) {
                if (alertClasses.contains(box.clsName) && !alertedClassesInFrame.contains(box.clsName)) {
                    val threshold = confidenceThresholds[box.clsName] ?: 0.5f
                    if (box.cnf >= threshold) {
                        val lastAlertTimeForClass = lastAlertTimes[box.clsName] ?: 0L
                        if (currentTime - lastAlertTimeForClass > cooldown * 1000) {
                            var speed = 0f
                            if (ActivityCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                speed = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.speed ?: 0f
                            }
                            alertHistory.add(AlertRecord(box.clsName, box.cnf, speed * 3.6f))
                            val toneGen = ToneGenerator(5, 100)
                            toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                            lastAlertTimes[box.clsName] = currentTime
                            alertedClassesInFrame.add(box.clsName)
                        }
                    }
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        val speed = location.speed * 3.6 // Convert m/s to km/h
        runOnUiThread {
            binding.tvSpeed.text = String.format("%.2f km/h", speed)
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_alert_classes -> {
                showClassSelectionDialog()
            }
            R.id.action_confidence -> {
                showConfidenceDialog()
            }
            R.id.action_view_alerts -> {
                showAlertHistoryDialog()
            }
            R.id.action_save -> {
                val menu = binding.navView.menu
                val cooldownEditText = menu.findItem(R.id.action_cooldown).actionView as EditText
                val globalConfidenceEditText = menu.findItem(R.id.action_global_confidence).actionView as EditText
                val iouThresholdEditText = menu.findItem(R.id.action_iou_threshold).actionView as EditText

                cooldown = cooldownEditText.text.toString().toIntOrNull() ?: 5
                globalConfidence = globalConfidenceEditText.text.toString().toFloatOrNull() ?: 0.3f
                iouThreshold = iouThresholdEditText.text.toString().toFloatOrNull() ?: 0.5f

                cameraExecutor.submit {
                    detector?.updateThresholds(globalConfidence, iouThreshold)
                }

                binding.drawerLayout.closeDrawer(GravityCompat.END)
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private fun showAlertHistoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_alert_history, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvAlertHistory)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = AlertHistoryAdapter(alertHistory)
        recyclerView.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Alert History")
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showClassSelectionDialog() {
        val labels = detector?.labels?.toTypedArray() ?: emptyArray()
        val checkedItems = BooleanArray(labels.size) {
            alertClasses.contains(labels[it])
        }

        AlertDialog.Builder(this)
            .setTitle("Select Alert Classes")
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                val selectedClass = labels[which]
                if (isChecked) {
                    alertClasses.add(selectedClass)
                } else {
                    alertClasses.remove(selectedClass)
                }
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showConfidenceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confidence_thresholds, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvConfidence)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = ConfidenceThresholdAdapter(confidenceThresholds)
        recyclerView.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Confidence Thresholds")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                Toast.makeText(this, "Confidence thresholds saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
