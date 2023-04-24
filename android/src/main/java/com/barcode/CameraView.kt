package com.barcode
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

import android.widget.FrameLayout
import android.annotation.SuppressLint
import android.util.Log
import android.util.Range
import android.hardware.camera2.*
import android.view.*
import android.view.View.OnTouchListener

import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.core.impl.*
import androidx.camera.extensions.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.events.RCTEventEmitter

import com.barcode.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import java.lang.IllegalArgumentException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage


class CameraView(context: Context): FrameLayout(context), LifecycleOwner{
    companion object {
        const val TAG = "CameraView"
        const val TAG_PERF = "CameraView.performance"
        private val propsThatRequireSessionReconfiguration = arrayListOf("cameraId", "format", "fps", "hdr", "lowLightBoost", "photo", "video", "enableFrameProcessor")
        private val arrayListOfZoom = arrayListOf("zoom")
    }
    // react properties
    // props that require reconfiguring
    var cameraId: String? = null // this is actually not a react prop directly, but the result of setting device={}
    var enableDepthData = false
    var enableHighQualityPhotos: Boolean? = null
    var enablePortraitEffectsMatteDelivery = false

    // use-cases
    var photo: Boolean? = true
    var video: Boolean? = null
    var audio: Boolean? = null
    var enableFrameProcessor:Boolean = true //仅用于barcode和qrcode的识别
    //photo,video,barcode

    // props that require format reconfiguring
    var format: ReadableMap? = null
    var fps: Int? = null
    var hdr: Boolean? = null // nullable bool
    var colorSpace: String? = null
    var lowLightBoost: Boolean? = null // nullable bool

    // other props
    var isActive = false
    var torch = "off" //手电筒
    var zoom: Float = 1f // in "factor"
    var orientation: String? = null
    var enableZoomGesture = false
        set(value) {
            field = value
            setOnTouchListener(if (value) touchEventListener else null)
        }
   
    // private properties
    private var isMounted = false
    private val reactContext: ReactContext
        get() = context as ReactContext
    @Suppress("JoinDeclarationAndAssignment")
    lateinit var previewView: PreviewView

    @Suppress("JoinDeclarationAndAssignment")
    lateinit var textureView: TextureView
   
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    internal val takePhotoExecutor = Executors.newSingleThreadExecutor()
    internal val recordVideoExecutor = Executors.newSingleThreadExecutor()
    internal var coroutineScope = CoroutineScope(Dispatchers.Main)

    internal var camera: Camera? = null
    internal var imageCapture: ImageCapture? = null
    internal var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var mScanner:BarcodeScanner?=null

    internal var activeVideoRecording: Recording? = null

   

    private val scaleGestureListener: ScaleGestureDetector.SimpleOnScaleGestureListener
    private val scaleGestureDetector: ScaleGestureDetector
    private val touchEventListener: OnTouchListener

    private val lifecycleRegistry: LifecycleRegistry
    private var hostLifecycleState: Lifecycle.State
    private var actualFrameProcessorFps = 30.0
    private var lastFrameProcessorCall = System.currentTimeMillis()
 
    private val inputRotation: Int
        get() {
        return context.displayRotation
        }
    private val outputRotation: Int
        get() {
        if (orientation != null) {
            // user is overriding output orientation
            return when (orientation!!) {
            "portrait" -> Surface.ROTATION_0
            "landscapeRight" -> Surface.ROTATION_90
            "portraitUpsideDown" -> Surface.ROTATION_180
            "landscapeLeft" -> Surface.ROTATION_270
            else -> throw InvalidTypeScriptUnionError("orientation", orientation!!)
            }
        } else {
            // use same as input rotation
            return inputRotation
        }
        }

    private var minZoom: Float = 1f
    private var maxZoom: Float = 1f

   
    init{
        val options1=BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,Barcode.FORMAT_EAN_13
        ).build()
        mScanner=BarcodeScanning.getClient(options1)
        // if(enableFrameProcessor){
        //     Log.d(TAG, "init previewview for barcode")

        // }else
        // {
            previewView = PreviewView(context)
            previewView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            previewView.installHierarchyFitter() // If this is not called correctly, view finder will be black/blank
            addView(previewView)
            Log.d(TAG, "init previewview not barcode"+previewView.height)

        // }
        scaleGestureListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = max(min((zoom * detector.scaleFactor), maxZoom), minZoom)
                update(arrayListOfZoom)
                return true
            }
        }
        scaleGestureDetector = ScaleGestureDetector(context, scaleGestureListener)
        touchEventListener = OnTouchListener { _, event -> return@OnTouchListener scaleGestureDetector.onTouchEvent(event) }
    
        hostLifecycleState = Lifecycle.State.INITIALIZED
        lifecycleRegistry = LifecycleRegistry(this)
        reactContext.addLifecycleEventListener(object : LifecycleEventListener {
            override fun onHostResume() {
                hostLifecycleState = Lifecycle.State.RESUMED
                updateLifecycleState()
                update(propsThatRequireSessionReconfiguration)
            }
            override fun onHostPause() {
                hostLifecycleState = Lifecycle.State.CREATED
                updateLifecycleState()
            }
            override fun onHostDestroy() {
                hostLifecycleState = Lifecycle.State.DESTROYED
                updateLifecycleState()
                cameraExecutor.shutdown()
                takePhotoExecutor.shutdown()
                recordVideoExecutor.shutdown()
                reactContext.removeLifecycleEventListener(this)
            }
        })
        update(propsThatRequireSessionReconfiguration)
    }
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateOrientation()
    } 
    @SuppressLint("RestrictedApi")
    private fun updateOrientation() {
        preview?.targetRotation = inputRotation
        imageCapture?.targetRotation = outputRotation
        videoCapture?.targetRotation = outputRotation
        imageAnalysis?.targetRotation = outputRotation
    }


    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    /**
   * Updates the custom Lifecycle to match the host activity's lifecycle, and if it's active we narrow it down to the [isActive] and [isAttachedToWindow] fields.
   */
    private fun updateLifecycleState() {
        val lifecycleBefore = lifecycleRegistry.currentState
        if (hostLifecycleState == Lifecycle.State.RESUMED) {
        // Host Lifecycle (Activity) is currently active (RESUMED), so we narrow it down to the view's lifecycle
        if (isActive && isAttachedToWindow) {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        } else {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        } else {
        // Host Lifecycle (Activity) is currently inactive (STARTED or DESTROYED), so that overrules our view's lifecycle
        lifecycleRegistry.currentState = hostLifecycleState
        }
        Log.d(TAG, "Lifecycle went from ${lifecycleBefore.name} -> ${lifecycleRegistry.currentState.name} (isActive: $isActive | isAttachedToWindow: $isAttachedToWindow)")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLifecycleState()
        Log.d(TAG, "onAttachedToWindow。。。。。。。。")
        if (!isMounted) {
            isMounted = true
            invokeOnViewReady()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        updateLifecycleState()
    }


    /**
    * Invalidate all React Props and reconfigure the device
    */
    fun update(changedProps: ArrayList<String>) = previewView.post {
        Log.d(TAG,"update ...."+changedProps)
        // TODO: Does this introduce too much overhead?
        //  I need to .post on the previewView because it might've not been initialized yet
        //  I need to use CoroutineScope.launch because of the suspend fun [configureSession]
        coroutineScope.launch {
        try {
            val shouldReconfigureSession = changedProps.containsAny(propsThatRequireSessionReconfiguration)
            val shouldReconfigureZoom = shouldReconfigureSession || changedProps.contains("zoom")
            val shouldReconfigureTorch = shouldReconfigureSession || changedProps.contains("torch")
            val shouldUpdateOrientation = shouldReconfigureSession ||  changedProps.contains("orientation")

            if (changedProps.contains("isActive")) {
                updateLifecycleState()
            }
            if (shouldReconfigureSession) {
                configureSession()
            }
            if (shouldReconfigureZoom) {
                val zoomClamped = max(min(zoom, maxZoom), minZoom)
                camera!!.cameraControl.setZoomRatio(zoomClamped)
            }
            if (shouldReconfigureTorch) {
                camera!!.cameraControl.enableTorch(torch == "on")
            }
            if (shouldUpdateOrientation) {
                updateOrientation()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "update() threw: ${e.message}")
            invokeOnError(e)
        }
        }
    }
     /**
    * Configures the camera capture session. This should only be called when the camera device changes.
    */
    @SuppressLint("RestrictedApi")
    private suspend fun configureSession() {
        try {
            val startTime = System.currentTimeMillis()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                throw CameraPermissionError()
            }
            if (cameraId == null) {
                throw NoCameraDeviceError()
            }
        
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider = ProcessCameraProvider.getInstance(reactContext).await()

            var cameraSelector = CameraSelector.Builder().byID(cameraId!!).build()

            // var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            Log.d(TAG, "onAttachedToWindow。。。。。。。。"+cameraId!!)


            val previewBuilder = Preview.Builder()
                .setTargetRotation(inputRotation)

            val imageCaptureBuilder = ImageCapture.Builder()
                .setTargetRotation(outputRotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)

            val videoRecorderBuilder = Recorder.Builder()
                .setExecutor(cameraExecutor)

            val imageAnalysisBuilder = ImageAnalysis.Builder()
                .setTargetRotation(outputRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setBackgroundExecutor(cameraExecutor)
                // .setAnalyzer(cameraExecutor){imageProxy:ImageProxy->
                //     val mediaImage=imageProxy.image
                //     if(mediaImage!=null){
                //         val image:InputImage=InputImage.fromMediaImage(mediaImage,imageProxy.imageInfo.rotationDegrees)
                //         mScanner?.process(image)?.addOnSuccessListener{barcodeList:List<Barcode>->
                //             val barcode=barcodeList.getOrNull(0);
                //             barcode?.rawValue?.let{value:String->
                //                 invokeOnBarcodeScaned(value)
                //             }
                //         }
                //     }

                // }

            if (format == null) {
                // let CameraX automatically find best resolution for the target aspect ratio
                val aspectRatio = aspectRatio(previewView.height, previewView.width) // flipped because it's in sensor orientation.
                previewBuilder.setTargetAspectRatio(aspectRatio)
                imageCaptureBuilder.setTargetAspectRatio(aspectRatio)
                // TODO: Aspect Ratio for Video Recorder?
                imageAnalysisBuilder.setTargetAspectRatio(aspectRatio)
            } 

            Log.d(TAG, "previewView.height, previewView.width:"+previewView.height+":::"+previewView.width)


            // Unbind use cases before rebinding
            videoCapture = null
            imageCapture = null
            imageAnalysis = null
            cameraProvider.unbindAll()

            // Bind use cases to camera
            val useCases = ArrayList<UseCase>()
            if (enableFrameProcessor==true) {
               
                Log.d(TAG, "enableFrameProcessor is true,so do")
                imageAnalysis = imageAnalysisBuilder.build().apply() {
                setAnalyzer(cameraExecutor, { imageProxy:ImageProxy ->
                        val now = System.currentTimeMillis()
                        val intervalMs = (1.0 / actualFrameProcessorFps) * 1000.0
                        if (now - lastFrameProcessorCall > intervalMs) {
                            lastFrameProcessorCall = now
                            val now = System.currentTimeMillis()
                            val mediaImage=imageProxy.image
                            if(mediaImage!=null){
                                val image:InputImage=InputImage.fromMediaImage(mediaImage,imageProxy.imageInfo.rotationDegrees)
                                mScanner?.process(image)?.addOnSuccessListener{barcodeList:List<Barcode>->
                                    val barcode=barcodeList.getOrNull(0);
                                    Log.d(TAG, "barcodey get"+barcode?.rawValue)
                                    barcode?.rawValue?.let{value:String->
                                        Log.d(TAG, "invokeOnBarcodeScaned::"+value)
                                        invokeOnBarcodeScaned(value)
                                    }
                                }
                            }
                        }                    
                        imageProxy.close()
                    
                    })
                }
                useCases.add(imageAnalysis!!)
            }else{
                    if (video == true) {
                        val videoRecorder = videoRecorderBuilder.build()
                        videoCapture = VideoCapture.withOutput(videoRecorder)
                        videoCapture!!.targetRotation = outputRotation
                        useCases.add(videoCapture!!)
                    }
                    if (photo == true) {
                        imageCapture = imageCaptureBuilder.build()
                        useCases.add(imageCapture!!)
                    }
            }
            preview = previewBuilder.build()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, *useCases.toTypedArray())
            //camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview)
            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            preview!!.setSurfaceProvider(previewView.surfaceProvider)
            Log.d(TAG, "cameraProvider.bindToLifecycle")

            minZoom = camera!!.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
            maxZoom = camera!!.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f

            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG_PERF, "Session configured in $duration ms! Camera: ${camera!!}")
            invokeOnInitialized()
        } catch (exc: Throwable) {
            throw when (exc) {
                is CameraError -> exc
                is IllegalArgumentException -> {
                if (exc.message?.contains("too many use cases") == true) {
                    ParallelVideoProcessingNotSupportedError(exc)
                } else {
                    InvalidCameraDeviceError(exc)
                }
                }
                else -> UnknownCameraError(exc)
            }
        }
    }

   

}