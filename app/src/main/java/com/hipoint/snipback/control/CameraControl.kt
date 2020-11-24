package com.hipoint.snipback.control

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.CAMERA_SERVICE
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.CamcorderProfile
import android.media.MediaCodec
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.MotionEvent
import android.view.Surface
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.Utils.AutoFitTextureView
import com.hipoint.snipback.fragment.VideoMode
import com.hipoint.snipback.listener.IRecordUIListener
import com.hipoint.snipback.room.entities.Hd_snips
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class CameraControl(val activity: FragmentActivity) {
    companion object {
        private val TAG = CameraControl::class.java.simpleName
        
        //Camera Orientation
        const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270

        private val DEFAULT_ORIENTATIONS = SparseIntArray()
        private val INVERSE_ORIENTATIONS = SparseIntArray()
    }

    init {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90)
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0)
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270)
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180)

        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270)
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180)
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90)
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0)
    }
    
    private var recordUIListener : IRecordUIListener?      = null
    private val totalDuration    : IntArray                = intArrayOf(0)                             //  total combined duration of merged clip
    private var mTextureView     : AutoFitTextureView?     = null
    private var persistentSurface: Surface                 = MediaCodec.createPersistentInputSurface()
    private var mPreviewBuilder  : CaptureRequest.Builder? = null

    private var clipDuration  = 30 * 1000L //  Buffer duration
    private var recordPressed = false      //  to know if the user has actively started recording
    private var stopPressed   = false      //  to know if the user has actively ended recording, todo: this can be removed once a better handing is in place
    private var recordClips   = true       //  to check if short clips should be recorded
    
    private var chosenCProfile      : CamcorderProfile? = null
    private var mSensorOrientation  : Int?              = null
    private var outputFilePath      : String?           = null
    private var lastUserRecordedPath: String?           = null
    private var clipQueue           : Queue<File>?      = null

    //two finger pinch zoom
    private var finger_spacing = 0f
    private var zoomLevel      = 1.0

    private var zoom: Rect? = null
    

    /**
     * The [android.util.Size] of camera preview.
     */
    private var mPreviewSize: Size? = null

    /**
     * The [android.util.Size] of video recording.
     */
    private var mVideoSize: Size? = null
    
    /**
     * A reference to the opened [android.hardware.camera2.CameraDevice].
     */
    private var mCameraDevice: CameraDevice? = null

    /**
     * A reference to check the required lens facing.
     */
    private var isBackFacingRequired: Boolean = true

    /**
     * A reference to the current [android.hardware.camera2.CameraCaptureSession] for
     * preview.
     */
    private var mPreviewSession: CameraCaptureSession? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock = Semaphore(1)

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its status.
     */
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraDevice = cameraDevice
            //            startPreview();
            //  If recordClips is true the app will automatically start recording
            //  and saving clips while displaying the preview
            //  else preview is shown without recording.
            if (recordClips && !stopPressed) {
//                parentSnipId = AppClass.getAppInstance().lastSnipId + 1
                startRecordingVideo()
            } else startPreview()
            mCameraOpenCloseLock.release()
            configureTransform(mTextureView!!.width, mTextureView!!.height)
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            activity.finish()
        }
    }

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var mBackgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var mBackgroundHandler: Handler? = null

    /**
     * MediaRecorder
     */
    private var mMediaRecorder: MediaRecorder? = null

    /**
     * Whether the app is recording video now
     */
    private var mIsRecordingVideo = false
    
    /**
     * Stops and restarts the mediaRecorder,
     * assuming the mediaRecorder is initialized and already recording
     */
    internal fun restartRecording() {
        Log.d(TAG, "restartRecording: recording restart")
        try {
            mMediaRecorder?.stop()
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
        setUpMediaRecorder()
        mMediaRecorder?.start()
    }

    /**
     * Starts a background thread and its [Handler].
     */
    fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    //  setters and getters
    fun setRecordUIListener(uiListener: IRecordUIListener){
        recordUIListener = uiListener
    }
    
    fun setPreviewTexture(previewTexture: AutoFitTextureView){
        mTextureView = previewTexture
    }

    fun setClipDuration(duration: Long){
        clipDuration = duration
    }

    fun setRecordPressed(isPressed: Boolean){
        recordPressed = isPressed
    }

    fun setStopPressed(isPressed: Boolean){
        stopPressed = isPressed
    }
    
    fun isRecordingVideo(): Boolean{
        return mIsRecordingVideo
    }
    
    fun setRecordClips(recordingClips: Boolean){
        recordClips = recordingClips
    }
    
    fun isRecordingClips(): Boolean{
        return recordClips
    }

    fun getZoomLevel(): Double{
        return zoomLevel
    }

    fun clipQueueSize(): Int{
        return clipQueue?.size ?: 0
    }

    fun removeClipQueueItem(): File?{
        return clipQueue?.remove()
    }

    fun getCurrentOutputPath(): String?{
        return outputFilePath
    }

    fun getLastUserRecordedPath(): String?{
        return lastUserRecordedPath
    }
    /**
     * Tries to open a [CameraDevice]. The result is listened by `mStateCallback`.
     */
    @SuppressLint("MissingPermission")
    internal fun openCamera(width: Int, height: Int) {
        if (activity.isFinishing) {
            return
        }
        val manager = activity.getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            Log.d(TAG, "tryAcquire")
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
//                throw RuntimeException("Time out waiting to lock camera opening.")
                Toast.makeText(activity, "Unable to open camera", Toast.LENGTH_SHORT).show()
                activity.finish()
            }
            val cameraId = getCameraId(if (isBackFacingRequired) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT)

            // Choose the sizes for camera preview and video recording
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            if (map == null) {
                throw RuntimeException("Cannot get available preview/video sizes")
            }
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            /*mPreviewSize = Size(width, height)  //  Fixes jumpy UI in devices*/
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), width, height, mVideoSize)
            val orientation = activity.resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView?.setAspectRatio(mPreviewSize!!.width, mPreviewSize!!.height)
            } else {
                mTextureView?.setAspectRatio(mPreviewSize!!.height, mPreviewSize!!.width)
            }
            configureTransform(width, height)
            mMediaRecorder = MediaRecorder()

            manager.openCamera(cameraId, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show()
            activity.finish()
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            /*ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);*/
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.")
        }
    }

    /**
     * Switches between front and back facing cameras
     */
    internal fun closeToSwitchCamera() {
        isBackFacingRequired = !isBackFacingRequired
        chosenCProfile = null
        closeCamera()
    }

    private fun getCameraId(lens: Int): String {
        val manager = activity.getSystemService(CAMERA_SERVICE) as CameraManager
        var deviceId = listOf<String>()
        try {
            val cameraIdList = manager.cameraIdList
            deviceId = cameraIdList.filter { lens == cameraCharacteristics(it, CameraCharacteristics.LENS_FACING) }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "getCameraId: ${e.message}")
            e.printStackTrace()
        }

        return deviceId[0]
    }


    internal fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            closePreviewSession()
            mCameraDevice?.close()
            mCameraDevice = null
            mMediaRecorder?.release()
            mMediaRecorder = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.")
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Start the camera preview.
     */
    private fun startPreview() {
        if (null == mCameraDevice || !mTextureView!!.isAvailable || null == mPreviewSize) {
            return
        }
        try {
            closePreviewSession()
            val texture = mTextureView!!.surfaceTexture!!
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val previewSurface = Surface(texture)
            mPreviewBuilder!!.addTarget(previewSurface)
            mCameraDevice!!.createCaptureSession(listOf(previewSurface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            mPreviewSession = session
                            //                            updatePreview();
                            //  calling capture request without starting another thread.
                            mPreviewBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                            try {
                                mPreviewSession!!.setRepeatingRequest(mPreviewBuilder!!.build(), null, mBackgroundHandler)
                            } catch (e: CameraAccessException) {
                                e.printStackTrace()
                                Toast.makeText(activity, "Cannot connect to camera", Toast.LENGTH_SHORT).show()
                                activity.finish()
                            } catch (e1: IllegalStateException) {
                                e1.printStackTrace()
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
                        }
                    }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Update the camera preview. [.startPreview] needs to be called in advance.
     */
    private fun updatePreview() {
        if (null == mCameraDevice) {
            return
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder)
            val thread = HandlerThread("CameraPreview")
            thread.start()
            mPreviewSession!!.setRepeatingRequest(mPreviewBuilder!!.build(), null, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e1: IllegalStateException) {
            e1.printStackTrace()
        }
    }

    private fun setUpCaptureRequestBuilder(builder: CaptureRequest.Builder?) {
        builder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    fun configureTransform(viewWidth: Int, viewHeight: Int) {

        if (null == mPreviewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager?.defaultDisplay?.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0F, 0F, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(
                    viewHeight.toFloat() / mPreviewSize!!.height,
                    viewWidth.toFloat() / mPreviewSize!!.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }

        CoroutineScope(Dispatchers.Main).launch { mTextureView!!.setTransform(matrix) }
    }

    /**
     * Sets up the media recorder for capturing the video from the surface
     * will output as H264 MPEG4 with AAC audio from the mic.
     *
     *
     * if recordClips is true, mMediaRecorder.setMaxDuration is set to clipDuration
     * this is monitored using infoListener and the recording is restarted automatically.
     * else recording proceeds as normal.
     *
     * MediaRecorder is currently not supported by the emulator
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        //  ensuring the media recorder is recreated
        try {
            mMediaRecorder!!.reset()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
        outputFilePath = outputMediaFile!!.absolutePath

        val rotation = activity.windowManager?.defaultDisplay?.rotation
        when (mSensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES -> mMediaRecorder!!.setOrientationHint(DEFAULT_ORIENTATIONS[rotation!!])
            SENSOR_ORIENTATION_INVERSE_DEGREES -> mMediaRecorder!!.setOrientationHint(INVERSE_ORIENTATIONS[rotation!!])
        }
        mMediaRecorder!!.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            if (recordClips) {    //  so that the actual recording is not affected by clip duration.
                setMaxDuration(clipDuration.toInt())
            } else {
                setMaxDuration(0)
            }
            val profile = chooseCamcorderProfile()
            setProfile(profile)
            setInputSurface(persistentSurface)
            setOutputFile(outputFilePath)

            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && recordClips) {
                    try {
                        restartRecording()
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                        //  attempt to reopen the camera
                        closeCamera()
                        if (mTextureView!!.isAvailable) {
                            openCamera(mTextureView!!.width, mTextureView!!.height)
                        } /*else {
                            mTextureView!!.surfaceTextureListener = textureListener
                        }*/
                    }
                }
            }
            prepare()
        }
    }

    fun getVideoFilePath(context: Context): String {
        val dir = activity.getExternalFilesDir(null)
        return ((if (dir == null) "" else dir.absolutePath + "/")
                + System.currentTimeMillis() + ".mp4")
    }

    // External sdcard file location
    private val outputMediaFile: File?
        get() {
            if (clipQueue == null) {
                clipQueue = LinkedList()
            }

            // External sdcard file location
            val mediaStorageDir = File(activity.dataDir,
                    VideoMode.VIDEO_DIRECTORY_NAME)
            // Create storage directory if it does not exist
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    Log.d(TAG, "Oops! Failed create "
                            + VideoMode.VIDEO_DIRECTORY_NAME + " directory")
                    return null
                }
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.getDefault()).format(Date())
            val mediaFile: File
            mediaFile = File(mediaStorageDir.path + File.separator
                    + "VID_" + timeStamp + ".mp4")

            //  adds the created clips to queue
            Log.d(TAG, "clip added to queue")
            clipQueue!!.add(mediaFile)
            return mediaFile
        }

    /**
     * Sets up the media recorder and starts the recording session
     */
    fun startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView!!.isAvailable || null == mPreviewSize) {
            return
        }
        try {
//            closePreviewSession();
            setUpMediaRecorder()

            val texture = mTextureView!!.surfaceTexture!!
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces: MutableList<Surface> = ArrayList()

            // Set up Surface for the camera preview
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            mPreviewBuilder!!.addTarget(previewSurface)

            // Set up Surface for the MediaRecorder
//            val recorderSurface = mMediaRecorder!!.surface
            surfaces.add(persistentSurface)
            mPreviewBuilder!!.addTarget(persistentSurface)
            val startRecTime = System.currentTimeMillis()
            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice!!.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession
                    //                    updatePreview();
                    mPreviewBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    try {
                        mPreviewSession!!.setRepeatingRequest(mPreviewBuilder!!.build(), null, mBackgroundHandler)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                        Toast.makeText(activity, "Cannot connect to camera", Toast.LENGTH_SHORT).show()
                        activity.finish()
                    } catch (e1: IllegalStateException) {
                        e1.printStackTrace()
                        return
                    }
                    mIsRecordingVideo = true
                    val vmFrag = activity.supportFragmentManager.findFragmentByTag(AppMainActivity.VIDEO_MODE_TAG)
                    if (vmFrag != null) {
                        if ((vmFrag as VideoMode).isVisible) {
                            mMediaRecorder!!.start()
                            Log.d(TAG, "setUpMediaRecorder start time = ${System.currentTimeMillis() - startRecTime}")
                        }
                    }
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
                }
            }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        //  we don't need to show the chronometer till the user presses the record button.
        if (recordPressed) {
            recordUIListener?.startChronometerUI()
        }
    }

    /**
     * Given `choices` of `Size`s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    private fun chooseOptimalSize(choices: Array<Size>, width: Int = 1080, height: Int = 1920, aspectRatio: Size?): Size {
        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough = arrayListOf<Size>()
        // Collect the supported resolutions that are smaller than the preview Surface
        val notBigEnough = arrayListOf<Size>()
        val w = aspectRatio!!.width
        val h = aspectRatio.height
        for (option in choices) {
            /*if (option.height == option.width * h / w) {*/
            if(option.height == h || option.width == w) {
                if (option.width >= width && option.height >= height || option.width >= height && option.height >= width) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        return when {
            bigEnough.isNotEmpty() -> {
                Collections.min(bigEnough, VideoMode.CompareSizesByArea())
            }
            notBigEnough.isNotEmpty() -> {
                Collections.max(notBigEnough, VideoMode.CompareSizesByArea())
            }
            else -> {
                Log.e(TAG, "Couldn't find any suitable preview size")
                choices[0]
            }
        }
    }

    /**
     * Gets the best Camcorder profile based on the preview dimensions.
     *
     * @return CamcorderProfile
     */
    private fun chooseCamcorderProfile(): CamcorderProfile {
        if(chosenCProfile!=null)
            return chosenCProfile!!

        val cameraProfiles = ArrayList<Int>()
        val candidateProfiles = ArrayList<CamcorderProfile>()
        cameraProfiles.add(CamcorderProfile.QUALITY_2160P)
        cameraProfiles.add(CamcorderProfile.QUALITY_1080P)
        cameraProfiles.add(CamcorderProfile.QUALITY_720P)
        cameraProfiles.add(CamcorderProfile.QUALITY_480P)
        for (p in cameraProfiles) {
            if (CamcorderProfile.hasProfile(p)) {
                val tmp = CamcorderProfile.get(p)
                if (tmp.videoFrameWidth <= mPreviewSize!!.width && tmp.videoFrameHeight <= mPreviewSize!!.height ||
                        tmp.videoFrameWidth <= mPreviewSize!!.height && tmp.videoFrameHeight <= mPreviewSize!!.width) candidateProfiles.add(tmp)
            }
        }
        val comparator = Comparator<CamcorderProfile?> { p1, p2 -> if (p1 != null && p2 != null) p2.videoFrameWidth * p2.videoFrameHeight - p1.videoFrameWidth * p1.videoFrameHeight else 0 }
        candidateProfiles.sortWith(comparator)
        chosenCProfile = if (candidateProfiles.size != 0) candidateProfiles[0] else CamcorderProfile.get(CamcorderProfile.QUALITY_LOW)
        return chosenCProfile!!
    }

    private fun closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession!!.close()
            mPreviewSession = null
        }
    }

    /**
     * Stops the mediaRecorder and resets it.
     *
     *
     * Adds snip details
     */
    fun stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false
        recordUIListener?.stopChronometerUI()
        
        // Stop recording
        mMediaRecorder!!.stop()
//        mMediaRecorder!!.reset()
        lastUserRecordedPath = outputFilePath

        val retriever = MediaMetadataRetriever()
        clipQueue!!.forEach(Consumer { file: File ->
            if (file.length() > 0L) {
                retriever.setDataSource(file.absolutePath)
                Log.d(TAG, "stopRecordingVideo: file in queue = ${file.absolutePath}")
                val currentClipDuration: Int = TimeUnit.MILLISECONDS.toSeconds(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()).toInt()
                Log.d(TAG, "stopRecordingVideo:\nCurrent clip duration: $currentClipDuration\nTotalDuration: ${totalDuration[0]}")
                totalDuration[0] += currentClipDuration
                (activity as AppMainActivity).addSnip(file.absolutePath, currentClipDuration,  /*totalDuration[0]*/currentClipDuration)
            }
        })
        retriever.release()
//        outputFilePath = null
        startPreview()
    }
    
    private fun <T> cameraCharacteristics(cameraId: String, key: CameraCharacteristics.Key<T>): T {
        val manager = activity.getSystemService(CAMERA_SERVICE) as CameraManager
        val characteristics = manager.getCameraCharacteristics(cameraId)
        return when (key) {
            CameraCharacteristics.LENS_FACING,
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP,
            CameraCharacteristics.SENSOR_ORIENTATION -> {
                characteristics.get(key)!!
            }
            else -> throw IllegalArgumentException("Key not recognized")
        }
    }

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private fun chooseVideoSize(choices: Array<Size>): Size {
//        for (Size size : choices) {
//            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
//                return size;
//            }
//        }
//        Log.e(TAG, "Couldn't find any suitable video size");
//        return choices[choices.length - 1];
        for (size in choices) {
            if (1920 == size.width && 1080 == size.height) {
                return size
            }
        }
        for (size in choices) {
            if (size.width == size.height * 4 / 3 && size.width <= 1080) {
                return size
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size")
        return choices[choices.size - 1]
    }

    //  Zoom Handler
    fun handleZoomEvent(event: MotionEvent): Boolean {
        try {
            val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val mCameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(mCameraId)
            val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
            val m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    ?: return true
            val currentFingerSpacing: Float
            //            setUpCaptureRequestBuilder(mPreviewBuilder);
            if (event.pointerCount == 2) {
                // Multi touch logic
                currentFingerSpacing = getFingerSpacing(event)
                var delta = 0.03f //control the zoom sensitivity
                if (finger_spacing != 0f) {
                    if (currentFingerSpacing > finger_spacing) {
                        if (maxZoom - zoomLevel <= delta) {
                            delta = (maxZoom - zoomLevel).toFloat()
                        }
                        zoomLevel += delta
                        //                        seekBar.setProgress((int)zoom_level);
                    } else if (currentFingerSpacing < finger_spacing) {
                        if (zoomLevel - delta < 1f) {
                            delta = (zoomLevel - 1f).toFloat()
                        }
                        zoomLevel -= delta
                        //                        if (zoom_level == 1){
                        //                            seekBar.setProgress(0);
                        //                        } else {
                        ////                            seekBar.setProgress((int) zoom_level);
                        //                        }
                    }
                    val ratio = (1.toFloat() / zoomLevel).toFloat()
                    //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
                    //croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped
                    val croppedWidth = m.width() - (m.width().toFloat() * ratio).roundToInt()
                    val croppedHeight = m.height() - (m.height().toFloat() * ratio).roundToInt()

                    // zoom represents the zoomed visible area
                    zoom = Rect(croppedWidth / 2, croppedHeight / 2,
                            m.width() - croppedWidth / 2, m.height() - croppedHeight / 2)
                    mPreviewBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, zoom)
                }
                finger_spacing = currentFingerSpacing
            } /*else {
                    if (action == MotionEvent.ACTION_UP) {
                        //single touch logic
                    }
                }*/
            try {
                mPreviewSession!!.setRepeatingRequest(mPreviewBuilder!!.build(), null, mBackgroundHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
                Toast.makeText(activity, "Cannot connect to camera", Toast.LENGTH_SHORT).show()
                activity.finish()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        } catch (e: CameraAccessException) {
            throw RuntimeException("can not access camera.", e)
        }
        return false
    }

    fun setCurrentZoom(zoomLevel: Float) {
        val zoomRect = getZoomRect(zoomLevel)
        if (zoomRect != null) {
            try {
                //you can try to add the synchronized object here
                mPreviewBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
                mPreviewSession!!.setRepeatingRequest(mPreviewBuilder!!.build(), null, mBackgroundHandler)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating preview: ", e)
            }
            this.zoomLevel = zoomLevel.toDouble()
        }
    }


    internal fun getMinZoom(): Float {
        return 0f
    }

    internal fun getCurrentZoom(): Float {
        return zoomLevel.toFloat()
    }

    private fun getZoomRect(zoomLevel: Float): Rect? {
        return try {
            val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val mCameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(mCameraId)
            val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
            val activeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (zoomLevel in 1.0..maxZoom.toDouble()) {
                val ratio = 1.toFloat() / zoomLevel //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
                //croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped
                val croppedWidth = activeRect!!.width() - (activeRect.width().toFloat() * ratio).roundToInt()
                val croppedHeight = activeRect.height() - (activeRect.height().toFloat() * ratio).roundToInt()
                //Finally, zoom represents the zoomed visible area
                return Rect(croppedWidth / 2, croppedHeight / 2,
                        activeRect.width() - croppedWidth / 2, activeRect.height() - croppedHeight / 2)
                //                mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
//                return  zoom;
            } else if (zoomLevel == 0f) {
                return Rect(0, 0, activeRect!!.width(), activeRect.height())
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error during camera init")
            null
        }
    }


    fun getMaxZoom(): Float {
        return try {
            val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val mCameraId = manager.cameraIdList[0]
            manager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
        } catch (e: Exception) {
            Log.e(TAG, "Error during camera init")
            (-1).toFloat()
        }
    }
    
    /**
     * Returns the calculated space between touch points
     *
     * @param event
     * @return float space between touched points
     */
    private fun getFingerSpacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt(x * x + y * y.toDouble()).toFloat()
    }
}