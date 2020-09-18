package com.hipoint.snipback

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.CamcorderProfile
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.view.TextureView.SurfaceTextureListener
import android.view.View.OnTouchListener
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import android.widget.Chronometer.OnChronometerTickListener
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.legacy.app.FragmentCompat
import com.hipoint.snipback.Utils.AutoFitTextureView
import com.hipoint.snipback.Utils.CountUpTimer
import com.hipoint.snipback.Utils.VideoUtils
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.dialog.ProcessingDialog
import com.hipoint.snipback.fragment.Feedback_fragment
import com.hipoint.snipback.fragment.FragmentGalleryNew
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.listener.IVideoOpListener.VideoOp
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.room.repository.AppRepository.Companion.instance
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.DexterError
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.math.roundToInt

class VideoMode : Fragment(), View.OnClickListener, OnTouchListener, ActivityCompat.OnRequestPermissionsResultCallback, AppRepository.OnTaskCompleted, IVideoOpListener {
    private val VIDEO_DIRECTORY_NAME1 = "Snipback"
    private val totalDuration = intArrayOf(0) //  total combined duration of merged clip
    private val clipDuration = 30 * 1000L //  Buffer duration
    private val swipeValue = 5 * 1000L //  swipeBack duration
    private val videoUtils = VideoUtils(this@VideoMode)
    private var processingDialog: ProcessingDialog? = null
    private var actualClipTime = 0L
    private var clipQueue: Queue<File>? = null

    //two finger pinch zoom
    var finger_spacing = 0f
    var zoom_level = 1.0
    var zoom: Rect? = null
    var zoomFactor: TextView? = null

    //zoom slider controls
    var mProgress = 0f
    var mMinZoom = 0f
    var mMaxZoom = 0f
    private var zoomLevel = 0f
    var currentProgress = 1f
    val zoomStep = 1f

    //left swipe
    private var x1 = 0f
    private var x2 = 0f

    /**
     * Called when a touch event is dispatched to a view. This allows listeners to
     * get a chance to respond before the target view.
     *
     * @param v     The view the touch event has been dispatched to.
     * @param event The MotionEvent object containing full information about
     * the event.
     * @return True if the listener has consumed the event, false otherwise.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> x1 = event.x
            MotionEvent.ACTION_UP -> {
                x2 = event.x
                val deltaX = x2 - x1
                if (Math.abs(deltaX) > MIN_DISTANCE) {
                    // Left to Right swipe action
                    if (x2 > x1) {
                        if (mIsRecordingVideo) {
                            saveSnipTimeToLocal()
                        }
                    }
                }
            }
        }
        try {
            val manager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val mCameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(mCameraId)
            val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
            val m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    ?: return false
            val action = event.action
            val current_finger_spacing: Float
            //            setUpCaptureRequestBuilder(mPreviewBuilder);
            if (event.pointerCount == 2) {
                // Multi touch logic
                current_finger_spacing = getFingerSpacing(event)
                var delta = 0.03f //control the zoom sensitivity
                if (finger_spacing != 0f) {
                    if (current_finger_spacing > finger_spacing) {
                        if (maxZoom - zoom_level <= delta) {
                            delta = (maxZoom - zoom_level).toFloat()
                        }
                        zoom_level = zoom_level + delta
                        //                        seekBar.setProgress((int)zoom_level);
                    } else if (current_finger_spacing < finger_spacing) {
                        if (zoom_level - delta < 1f) {
                            delta = (zoom_level - 1f).toFloat()
                        }
                        zoom_level = zoom_level - delta
                        //                        if (zoom_level == 1){
//                            seekBar.setProgress(0);
//                        } else {
////                            seekBar.setProgress((int) zoom_level);
//                        }
                    }
                    val ratio = (1.toFloat() / zoom_level).toFloat()
                    //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
                    //croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped
                    val croppedWidth = m.width() - Math.round(m.width().toFloat() * ratio)
                    val croppedHeight = m.height() - Math.round(m.height().toFloat() * ratio)

                    // zoom represents the zoomed visible area
                    zoom = Rect(croppedWidth / 2, croppedHeight / 2,
                            m.width() - croppedWidth / 2, m.height() - croppedHeight / 2)
                    mPreviewBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, zoom)
                }
                finger_spacing = current_finger_spacing
            } else {
                if (action == MotionEvent.ACTION_UP) {
                    //single touch logic
                }
            }
            try {
                mPreviewSession!!.setRepeatingRequest(mPreviewBuilder!!.build(), null,
                        mBackgroundHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        } catch (e: CameraAccessException) {
            throw RuntimeException("can not access camera.", e)
        }
        return true
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
        return Math.sqrt(x * x + y * y.toDouble()).toFloat()
    }

    interface OnTaskCompleted {
        fun onTaskCompleted(success: Boolean)
    }

    companion object {
        //Camera Orientation
        private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        private val DEFAULT_ORIENTATIONS = SparseIntArray()
        private val INVERSE_ORIENTATIONS = SparseIntArray()
        private const val TAG = "Camera2VideoFragment"
        private const val FRAGMENT_DIALOG = "dialog"
        private const val REQUEST_VIDEO_PERMISSIONS = 1
        private const val VIDEO_DIRECTORY_NAME = "SnipBackVirtual"
        private const val THUMBS_DIRECTORY_NAME = "Thumbs"

        //    private GestureFilter detector;
        //clips
        private var recordPressed = false //  to know if the user has actively started recording
        private var recordClips = true //  to check if short clips should be recorded
        const val MIN_DISTANCE = 150
        private val VIDEO_PERMISSIONS = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO)

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
        private fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int, aspectRatio: Size?): Size {
            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough: MutableList<Size> = ArrayList()
            val w = aspectRatio!!.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.height == option.width * h / w && option.width >= width && option.height >= height) {
                    bigEnough.add(option)
                }
            }

            // Pick the smallest of those, assuming we found any
            return if (bigEnough.size > 0) {
                Collections.min(bigEnough, CompareSizesByArea())
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                choices[0]
            }
        }

        @JvmStatic
        fun newInstance(): VideoMode {
            return VideoMode()
        }

        init {
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        init {
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0)
        }
    }

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private lateinit var mTextureView: AutoFitTextureView

    /**
     * Button to record video
     */
    private lateinit var mButtonVideo: Button

    /**
     * A reference to the opened [android.hardware.camera2.CameraDevice].
     */
    private var mCameraDevice: CameraDevice? = null

    /**
     * A reference to the current [android.hardware.camera2.CameraCaptureSession] for
     * preview.
     */
    private var mPreviewSession: CameraCaptureSession? = null
    //    AppMainActivity.MyOnTouchListener onTouchListener;
    //
    //    private OnSwipeTouchListener touchListener=new OnSwipeTouchListener(getActivity()) {
    //        public void impelinfragment() {
    //            //do what you want:D
    //            Log.i("swipe","swipe left");
    //        }
    //    };
    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val mSurfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture,
                                               width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture,
                                                 width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
    }

    /**
     * The [android.util.Size] of camera preview.
     */
    private var mPreviewSize: Size? = null

    /**
     * The [android.util.Size] of video recording.
     */
    private var mVideoSize: Size? = null
    private val countUpTimer: CountUpTimer? = null

    /**
     * MediaRecorder
     */
    private var mMediaRecorder: MediaRecorder? = null

    /**
     * Whether the app is recording video now
     */
    private var mIsRecordingVideo = false

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var mBackgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var mBackgroundHandler: Handler? = null

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
            if (recordClips) {
                startRecordingVideo()
            } else startPreview()
            mCameraOpenCloseLock.release()
            if (null != mTextureView) {
                configureTransform(mTextureView.width, mTextureView.height)
            }
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
            val activity: Activity? = activity
            activity?.finish()
        }
    }
    private var hdSnips: Hd_snips? = null
    private var mSensorOrientation: Int? = null
    private var outputFilePath: String? = null
    private var mPreviewBuilder: CaptureRequest.Builder? = null
    private var timerSecond = 0
    private var appRepository: AppRepository? = null
    private var animBlink: Animation? = null
    private var thumbnailProcesingCompleted: OnTaskCompleted? = null

    //Views
    private lateinit var rootView: View
    private lateinit var gallery: ImageButton
    private lateinit var settings: ImageButton
    private lateinit var recordButton: ImageButton
    private lateinit var recordStopButton: ImageButton
    private lateinit var r_3_bookmark: ImageButton
    private lateinit var r_2_shutter: ImageButton
    private lateinit var tvTimer: TextView
    private lateinit var mChronometer: Chronometer
    private lateinit var blinkEffect: View
    private lateinit var rlVideo: RelativeLayout
    private lateinit var recStartLayout: RelativeLayout
    private lateinit var bottomContainer: RelativeLayout
    private lateinit var zoomControlLayout: RelativeLayout
    private lateinit var seekBar: SeekBar
    private lateinit var zoomOut: ImageButton
    private lateinit var zoomIn: ImageButton

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_videomode, container, false)

        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        animBlink = AnimationUtils.loadAnimation(context, R.anim.blink)

        rlVideo = rootView.findViewById(R.id.rl_video)
        gallery = rootView.findViewById(R.id.r_1)
        settings = rootView.findViewById(R.id.r_5)
        recordButton = rootView.findViewById(R.id.rec)
        mChronometer = rootView.findViewById(R.id.chronometer)
        mTextureView = rootView.findViewById(R.id.texture)
        blinkEffect = rootView.findViewById(R.id.overlay)
        recStartLayout = rootView.findViewById(R.id.rec_start_container)
        bottomContainer = rootView.findViewById(R.id.bottom_cont)
        recordStopButton = rootView.findViewById(R.id.rec_stop)
        zoomControlLayout = rootView.findViewById(R.id.zoom_control_layout)
        seekBar = rootView.findViewById(R.id.zoom_controller)
        zoomOut = rootView.findViewById(R.id.zoom_out_btn)
        zoomIn = rootView.findViewById(R.id.zoom_in_btn)
        recordStopButton.setOnClickListener(this)
        r_3_bookmark = rootView.findViewById(R.id.r_3_bookmark)
        r_3_bookmark.setOnClickListener(this)
        r_2_shutter = rootView.findViewById(R.id.r_2_shutter)
        r_2_shutter.setOnClickListener(this)
        zoomFactor = rootView.findViewById(R.id.zoom_factor)
        zoomControlLayout.setOnClickListener(this)
        zoomIn.setOnClickListener(this)
        zoomOut.setOnClickListener(this)
        rlVideo.setOnTouchListener(this)
        rlVideo.setOnClickListener(this)
        recordButton.setOnClickListener(this)

//        detector = new GestureFilter(getActivity(), this);

//        ((AppMainActivity)getActivity()).registerMyOnTouchListener(new AppMainActivity.MyOnTouchListener() {
//            @Override
//            public void onTouch(MotionEvent ev) {
//                touchListener.onTouch(ev);
//            }
//        });

//        rlVideo.setOnTouchListener((view, motionEvent) -> mTextureView.onTouch(view, motionEvent));
//        accessRoomDatabase();
        mTextureView.setOnClickListener(this)
        mTextureView.setOnTouchListener(this)
        gallery.setOnClickListener(View.OnClickListener { v: View? -> (activity as AppMainActivity?)!!.loadFragment(FragmentGalleryNew.newInstance(), true) })
        settings.setOnClickListener(View.OnClickListener { v: View? -> showDialogSettingsMain() })
        appRepository = instance
        mChronometer.onChronometerTickListener = OnChronometerTickListener { arg0: Chronometer? ->
//                if (!resume) {
            val time = SystemClock.elapsedRealtime() - mChronometer.base
            val h = (time / 3600000).toInt()
            val m = (time - h * 3600000).toInt() / 60000
            val s = (time - h * 3600000 - m * 60000).toInt() / 1000
            val t = (if (h < 10) "0$h" else h).toString() + ":" + (if (m < 10) "0$m" else m) + ":" + if (s < 10) "0$s" else s
            mChronometer.text = t
            val minutes = (SystemClock.elapsedRealtime() - mChronometer.base) / 1000 / 60
            val seconds = (SystemClock.elapsedRealtime() - mChronometer.base) / 1000 % 60
            val elapsedMillis = (SystemClock.elapsedRealtime() - mChronometer.base).toInt()
            timerSecond = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis.toLong()).toInt()
            //                    elapsedTime = SystemClock.elapsedRealtime();
            Log.d(TAG, "onChronometerTick: $minutes : $seconds")
        }
        mMinZoom = getMinZoom()
        mMaxZoom = getMaxZoom() - 1
        seekBar.max = Math.round(mMaxZoom - mMinZoom)
        seekBar.setOnSeekBarChangeListener(
                object : OnSeekBarChangeListener {
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        setCurrentZoom(Math.round(mMinZoom + 1 + mProgress * zoomStep).toFloat())
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        setCurrentZoom(Math.round(mMinZoom + 1 + progress.toFloat() * zoomStep).toFloat())
                        if (fromUser) mProgress = progress.toFloat()
                    }
                }
        )
        return rootView
    }

    private fun getMinZoom(): Float {
        return 0f
    }

    private fun getCurrentZoom(zoomLevel: Float): Float {
        return zoomLevel
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
            this.zoomLevel = zoomLevel
        }
    }

    private fun getZoomRect(zoomLevel: Float): Rect? {
        return try {
            val manager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val mCameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(mCameraId)
            val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
            val activeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (zoomLevel <= maxZoom && zoomLevel >= 1) {
                val ratio = 1.toFloat() / zoomLevel //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
                //croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped
                val croppedWidth = activeRect!!.width() - Math.round(activeRect.width().toFloat() * ratio)
                val croppedHeight = activeRect.height() - Math.round(activeRect.height().toFloat() * ratio)
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
            val manager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val mCameraId = manager.cameraIdList[0]
            manager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
        } catch (e: Exception) {
            Log.e(TAG, "Error during camera init")
            (-1).toFloat()
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (mTextureView.isAvailable) {
            openCamera(mTextureView.width, mTextureView.height)
        } else {
            mTextureView.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.rec -> {
                bottomContainer.visibility = View.INVISIBLE
                recStartLayout.visibility = View.VISIBLE

                // Once the record button is pressed we no longer need to capture clips since the
                // the user has started actively recording.
                if (recordClips) recordClips = false
                recordPressed = true
                startRecordingVideo()
                while (clipQueue!!.size > 2) {
                    clipQueue!!.remove().delete()
                }
            }
            R.id.rec_stop -> {
                bottomContainer.visibility = View.VISIBLE
                recStartLayout.visibility = View.INVISIBLE
                stopRecordingVideo()
                // we can restart recoding clips if it is required at this point
                attemptClipConcat()
            }
            R.id.r_3_bookmark -> {
                saveSnipTimeToLocal()
            }
            R.id.zoom_out_btn -> {
                if (getCurrentZoom(zoomLevel) <= mMaxZoom + 1) {
                    if (mProgress > mMinZoom) {
                        mProgress--
                        setCurrentZoom(Math.round(mMinZoom + mProgress * zoomStep).toFloat())
                        seekBar.progress = getCurrentZoom(zoomLevel).toInt()
                    } else {
                        mProgress = 0f
                    }
                }
            }
            R.id.zoom_in_btn -> {
                if (getCurrentZoom(zoomLevel) <= mMaxZoom) {
                    if (mProgress < mMaxZoom) {
                        mProgress++
                        setCurrentZoom(Math.round(mMinZoom + mProgress * zoomStep).toFloat())
                        seekBar.progress = getCurrentZoom(zoomLevel).toInt()
                    } else {
                        mProgress = 3f
                    }
                }
            }
            R.id.r_2_shutter -> {
                rlVideo.startAnimation(animBlink)
                blinkEffect.visibility = View.VISIBLE
                blinkEffect.animate()
                        .alpha(02f)
                        .setDuration(100)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                blinkEffect.visibility = View.GONE
                                blinkEffect.clearAnimation()
                            }
                        })

//                File filevideopath = new File(outputFilePath);
//
//                try {
//
//                    File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
//                            VIDEO_DIRECTORY_NAME1);
//                    // Create storage directory if it does not exist
//                    if (!mediaStorageDir.exists()) {
//                        if (!mediaStorageDir.mkdirs()) {
//                            return;
//                        }
//                    }
//                    File mediaFile = new File(mediaStorageDir.getPath() + File.separator
//                            + "PhotoTHUM_" + System.currentTimeMillis() + ".png");
//
//
//                    if (!mediaStorageDir.exists()) {
//                        if (!mediaStorageDir.mkdirs()) {
//                            Log.d(TAG, "Oops! Failed create "
//                                    + VIDEO_DIRECTORY_NAME1 + " directory");
//                            return;
//                        }
//                    }
//
//
//                    Log.d(TAG, "saving video thumbnail at path: " + mediaFile + ", video path: " + filevideopath.getAbsolutePath());
//                    //Save the thumbnail in a PNG compressed format, and close everything. If something fails, return null
//                    FileOutputStream streamThumbnail = new FileOutputStream(mediaFile);
//
//                    //Other method to get a thumbnail. The problem is that it doesn't allow to get at a specific time
//                    ; //= ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(),MediaStore.Images.Thumbnails.MINI_KIND)
//
//                    thumb=ThumbnailUtils.createVideoThumbnail(filevideopath.getAbsolutePath(), MediaStore.Images.Thumbnails.MICRO_KIND);
//
//
//
//
////                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
////                    try {
////                        retriever.setDataSource(filevideopath.getAbsolutePath());
////                        thumb = retriever.getFrameAtTime((int)  1000000,
////                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
////                        thumb.compress(Bitmap.CompressFormat.PNG, 80, streamThumbnail);
////                        thumb.recycle(); //ensure the image is freed;
////                    } catch (Exception ex) {
////                        Log.i(TAG, "MediaMetadataRetriever got exception:" + ex);
////                    }
////                    streamThumbnail.close();
//                    //update Snip
//
//                    Log.d(TAG, "thumbnail saved successfully");
//                } catch (FileNotFoundException e) {
//                    Log.d(TAG, "File Not Found Exception : check directory path");
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    Log.d(TAG, "IOException while closing the stream");
//                    e.printStackTrace();
//                }
                getVideoThumbnailclick(File(outputFilePath))
                rlVideo.clearAnimation()
            }
            R.id.texture -> {
                saveSnipTimeToLocal()
            }
        }
    }

    private fun attemptClipConcat() {
        if (clipQueue!!.size >= 2) {
            val clip1: File = clipQueue!!.remove()
            val clip2: File = clipQueue!!.remove()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val mergeFilePath = "merged-$timeStamp.mp4"
            if (processingDialog == null) processingDialog = ProcessingDialog(requireActivity())
            processingDialog!!.show()
            CoroutineScope(Default).launch {
                videoUtils.concatenateFiles(clip1, clip2, "${clip1.parent}/$mergeFilePath")
            }
        }
    }

    protected fun showDialogSettingsMain() {
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.fragment_settings)
        val con6 = dialog.findViewById<RelativeLayout>(R.id.con6)
        con6.setOnClickListener { showDialogSettingsResolution() }
        val feedback = dialog.findViewById<RelativeLayout>(R.id.con2)
        feedback.setOnClickListener {
            (activity as AppMainActivity?)!!.loadFragment(Feedback_fragment.newInstance(), true)
            dialog.dismiss()
        }
        dialog.show()
    }

    protected fun showDialogSettingsResolution() {
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.fragment_resolution)
        dialog.show()
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private fun shouldShowRequestPermissionRationale(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)) {
                return true
            }
        }
        return false
    }

    /**
     * Requests permissions needed for recording video.
     */
    private fun requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            //new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(requireActivity(), VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        Log.d(TAG, "onRequestPermissionsResult")
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.size == VIDEO_PERMISSIONS.size) {
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {

                        /*  ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);*/
                        break
                    }
                }
            } else {
                /* ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);*/
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun hasPermissionsGranted(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(requireActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /**
     * Tries to open a [CameraDevice]. The result is listened by `mStateCallback`.
     */
    private fun openCamera(width: Int, height: Int) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions()
            return
        }
        val activity: Activity? = activity
        if (null == activity || activity.isFinishing) {
            return
        }
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            Log.d(TAG, "tryAcquire")
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            val cameraId = manager.cameraIdList[0]

            // Choose the sizes for camera preview and video recording
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            if (map == null) {
                throw RuntimeException("Cannot get available preview/video sizes")
            }
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                    width, height, mVideoSize)
            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize!!.width, mPreviewSize!!.height)
            } else {
                mTextureView.setAspectRatio(mPreviewSize!!.height, mPreviewSize!!.width)
            }
            configureTransform(width, height)
            mMediaRecorder = MediaRecorder()
            if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                requestPermission()
                return
            }
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

    fun requestPermission() {
        Dexter.withActivity(activity).withPermissions(Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        // check if all permissions are granted or not
                        if (report.areAllPermissionsGranted()) {
                            if (mTextureView.isAvailable) {
                                openCamera(mTextureView.width, mTextureView.height)
                            } else {
                                mTextureView.surfaceTextureListener = mSurfaceTextureListener
                            }
                        }
                        // check for permanent denial of any permission show alert dialog
                        if (report.isAnyPermissionPermanentlyDenied) {
                            // open Settings activity
                            showSettingsDialog()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(permissions: List<PermissionRequest>, token: PermissionToken) {
                        token.continuePermissionRequest()
                    }
                }).withErrorListener { error: DexterError? -> Toast.makeText(requireActivity().applicationContext, "Error occurred! ", Toast.LENGTH_SHORT).show() }
                .onSameThread()
                .check()
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(getString(R.string.message_need_permission))
        builder.setMessage(getString(R.string.message_permission))
        builder.setPositiveButton(getString(R.string.title_go_to_setting)) { dialog: DialogInterface, which: Int ->
            dialog.cancel()
            openSettings()
        }
        builder.show()
    }

    // navigating settings app
    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", requireActivity().packageName, null)
        intent.data = uri
        startActivityForResult(intent, 101)
    }

    private fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            closePreviewSession()
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
            if (null != mMediaRecorder) {
                mMediaRecorder!!.release()
                mMediaRecorder = null
            }
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
        Log.d(TAG, "AVA start preview")
        if (null == mCameraDevice || !mTextureView.isAvailable || null == mPreviewSize) {
            return
        }
        try {
            closePreviewSession()
            val texture = mTextureView.surfaceTexture!!
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
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            val activity: Activity? = activity
                            if (null != activity) {
                                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
                            }
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
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity: Activity? = activity
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0F, 0F, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / mPreviewSize!!.height,
                    viewWidth.toFloat() / mPreviewSize!!.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        mTextureView.setTransform(matrix)
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
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        val activity = activity ?: return
        mMediaRecorder!!.reset()
        val rotation = activity.windowManager.defaultDisplay.rotation
        when (mSensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES -> mMediaRecorder!!.setOrientationHint(DEFAULT_ORIENTATIONS[rotation])
            SENSOR_ORIENTATION_INVERSE_DEGREES -> mMediaRecorder!!.setOrientationHint(INVERSE_ORIENTATIONS[rotation])
        }
        mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        //        if (outputFilePath == null || outputFilePath.isEmpty()) {
        outputFilePath = outputMediaFile!!.absolutePath
        //        }
        mMediaRecorder!!.setOutputFile(outputFilePath)
        if (recordClips) {    //  so that the actual recording is not affected by clip duration.
            mMediaRecorder!!.setMaxDuration(clipDuration.toInt())
        }
        //        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        val profile = chooseCamcorderProfile()
        mMediaRecorder!!.setVideoFrameRate(profile.videoFrameRate)
        mMediaRecorder!!.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
        mMediaRecorder!!.setVideoEncodingBitRate(profile.videoBitRate)
        mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mMediaRecorder!!.setAudioEncodingBitRate(profile.audioBitRate)
        mMediaRecorder!!.setAudioSamplingRate(profile.audioSampleRate)

//        mMediaRecorder.setVideoEncodingBitRate(10000000);
//        mMediaRecorder.setVideoFrameRate(30);
//        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
//        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder!!.setOnInfoListener { mr, what, extra ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && recordClips) {
                try {
                    startRecordingVideo()
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            }
        }
        mMediaRecorder!!.prepare()
    }

    private fun getVideoFilePath(context: Context): String {
        val dir = context.getExternalFilesDir(null)
        return ((if (dir == null) "" else dir.absolutePath + "/")
                + System.currentTimeMillis() + ".mp4")
    }

    // External sdcard file location
    private val outputMediaFile: File
    // Create storage directory if it does not exist

    //  adds the created clips to queue
//        if (recordClips) {
    //        }
    ?
        private get() {
            if (clipQueue == null) {
                clipQueue = LinkedList()
            }

            // External sdcard file location
            val mediaStorageDir = File(requireActivity().dataDir,
                    VIDEO_DIRECTORY_NAME)
            // Create storage directory if it does not exist
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    Log.d(TAG, "Oops! Failed create "
                            + VIDEO_DIRECTORY_NAME + " directory")
                    return null
                }
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.getDefault()).format(Date())
            val mediaFile: File
            mediaFile = File(mediaStorageDir.path + File.separator
                    + "VID_" + timeStamp + ".mp4")

            //  adds the created clips to queue
//        if (recordClips) {
            Log.d(TAG, "clip added to queue")
            clipQueue!!.add(mediaFile)
            //        }
            return mediaFile
        }

    /**
     * Sets up the media recorder and starts the recording session
     */
    private fun startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable || null == mPreviewSize) {
            return
        }
        try {
//            closePreviewSession();
            setUpMediaRecorder()
            val texture = mTextureView.surfaceTexture!!
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces: MutableList<Surface> = ArrayList()

            // Set up Surface for the camera preview
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            mPreviewBuilder!!.addTarget(previewSurface)

            // Set up Surface for the MediaRecorder
            val recorderSurface = mMediaRecorder!!.surface
            surfaces.add(recorderSurface)
            mPreviewBuilder!!.addTarget(recorderSurface)

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
                    }
                    mIsRecordingVideo = true
                    mMediaRecorder!!.start()
                    /*getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
                            // mButtonVideo.setText(R.string.stop);
                            mIsRecordingVideo = true;
                            // Start recording
                            mMediaRecorder.start();
                        }
                    });*/
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    val activity: Activity? = activity
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        //  we don't need to show the chronometer till the user presses the record button.
        if (recordPressed) {
            actualClipTime = SystemClock.elapsedRealtime() - actualClipTime // clip has ended
            mChronometer.base = SystemClock.elapsedRealtime()
            mChronometer.start()
            mChronometer.visibility = View.VISIBLE
        } else {
            //  keeps a count of how long each clip was
            actualClipTime = SystemClock.elapsedRealtime() - actualClipTime
        }
    }

    /**
     * Gets the best Camcorder profile based on the preview dimensions.
     *
     * @return CamcorderProfile
     */
    private fun chooseCamcorderProfile(): CamcorderProfile {
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
        return if (candidateProfiles.size != 0) candidateProfiles[0] else CamcorderProfile.get(CamcorderProfile.QUALITY_LOW)
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
    private fun stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false
        //  mButtonVideo.setText(R.string.record);
        // Stop recording
        mMediaRecorder!!.stop()
        mMediaRecorder!!.reset()
        mChronometer.stop()
        mChronometer.visibility = View.INVISIBLE
        mChronometer.text = ""
        clipQueue!!.forEach(Consumer { file: File ->
            val currentClipDuration: Int = if (file === clipQueue!!.peek()) TimeUnit.MILLISECONDS.toSeconds(actualClipTime).toInt() else timerSecond
            totalDuration[0] += currentClipDuration
            addSnip(file.absolutePath, currentClipDuration,  /*totalDuration[0]*/currentClipDuration) //
        })
        outputFilePath = null
        startPreview()
    }

    private fun addSnip(snipFilePath: String, snipDuration: Int, totalDuration: Int) {
        //            Toast.makeText(activity, "Video saved: " + outputFilePath,
//                    Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Video saved: $outputFilePath")
        val parentSnip = Snip()
        parentSnip.apply {
            start_time = 0.0
            end_time = 0.0
            is_virtual_version = 0
            parent_snip_id = 0
            snip_duration = snipDuration.toDouble()
            total_video_duration = totalDuration
            vid_creation_date = System.currentTimeMillis()
            event_id = AppClass.getAppInstance().lastEventId
            has_virtual_versions = (if (AppClass.getAppInstance().snipDurations.size > 0) 1 else 0)
            videoFilePath = snipFilePath
        }
        AppClass.getAppInstance().isInsertionInProgress = true
        runBlocking {
            // So that the order of the videos don't change
            appRepository!!.insertSnip(this@VideoMode, parentSnip)
        }
        //            AppClass.getAppInsatnce().saveAllSnips(parentSnip);
//            AppClass.getAppInsatnce().saveAllEventSnips();
//            AppClass.getAppInsatnce().saveAllParentSnips(parentSnip);
//            AppClass.getAppInsatnce().setEventParentSnips();
//            parentSnip.setVideoFilePath(outputFilePath);
//            AppClass.getAppInsatnce().saveAllSnips(parentSnip);
    }

    override suspend fun onTaskCompleted(snip: Snip?) {
        //  todo: merged and trimmed file needs to be added in HD_snip nothing else
        if (snip!!.is_virtual_version == 0) {
//            snip.setSnip_id(AppClass.getAppInstance().getLastSnipId());
//            hdSnips!!.video_path_processed = snip.videoFilePath
            //            hdSnips.setSnip_id(AppClass.getAppInstance().getLastSnipId());

            hdSnips = Hd_snips()
            hdSnips!!.video_path_processed = snip.videoFilePath
            hdSnips!!.snip_id = snip.snip_id

            appRepository!!.insertHd_snips(hdSnips!!)
            saveSnipToDB(snip, hdSnips!!.video_path_processed)
        }
        getVideoThumbnail(snip, File(hdSnips!!.video_path_processed))
    }

    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(lhs.width.toLong() * lhs.height -
                    rhs.width.toLong() * rhs.height)
        }
    }

    class ErrorDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            val activity = activity
            return AlertDialog.Builder(activity)
                    .setMessage(arguments.getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok) { dialogInterface, i -> activity.finish() }
                    .create()
        }

        companion object {
            private const val ARG_MESSAGE = "message"
            fun newInstance(message: String?): ErrorDialog {
                val dialog = ErrorDialog()
                val args = Bundle()
                args.putString(ARG_MESSAGE, message)
                dialog.arguments = args
                return dialog
            }
        }
    }

    class ConfirmationDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            val parent = parentFragment
            return AlertDialog.Builder(activity)
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok) { dialog, which ->
                        FragmentCompat.requestPermissions(parent, VIDEO_PERMISSIONS,
                                REQUEST_VIDEO_PERMISSIONS)
                    }
                    .setNegativeButton(android.R.string.cancel
                    ) { dialog, which -> parent.activity.finish() }
                    .create()
        }
    }

    fun saveSnipToDB(parentSnip: Snip?, filePath: String?) {
//        String chronoText = mChronometer.getText().toString();
//        Log.e("chrono m reading",chronoText);
        val snipDurations = AppClass.getAppInstance().snipDurations
        if (snipDurations.size > 0) {
            val event = AppClass.getAppInstance().getLastCreatedEvent()
            //            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
//            String currentDateandTime = sdf.format(new Date());
//            EventData eventData = new EventData();
//            eventData.setEvent_id(event.getEvent_id());
//            eventData.setEvent_title(event.getEvent_title());
//            eventData.setEvent_created(event.getEvent_created());
            for (endSecond in snipDurations) {
                val startSecond = Math.max(endSecond - 5, 0)
                val snip = Snip()
                snip.start_time = startSecond.toDouble()
                snip.end_time = endSecond.toDouble()
                snip.is_virtual_version = 1
                snip.has_virtual_versions = 0
                snip.parent_snip_id = parentSnip!!.snip_id
                snip.snip_duration = endSecond - startSecond.toDouble()
                snip.vid_creation_date = System.currentTimeMillis()
                snip.event_id = event.event_id
                CoroutineScope(Default).launch {
                    appRepository!!.insertSnip(this@VideoMode, snip)
                }
                //                parentSnip.setHas_virtual_versions(1);
//                appRepository.updateSnip(parentSnip);
                snip.videoFilePath = filePath
                //                eventData.addEventSnip(snip);
//                AppClass.getAppInsatnce().saveAllSnips(snip);
//                eventData.addEventSnip(parentSnip);
            }
            //            AppClass.getAppInsatnce().saveAllEventSnips();
            AppClass.getAppInstance().clearSnipDurations()
        }

//        AppViewModel appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);
//        appViewModel.getSnipsLiveData().observe(this, snips -> {
//            if(snips.size() > 0) {
//                for (Snip snip : snips) {
//                    if (snip.getParent_snip_id() == parentSnip.getSnip_id() || snip.getSnip_id() == parentSnip.getSnip_id()) {
//                        getVideoThumbnail(snip, new File(filePath), snips.indexOf(snip) == snips.size() - 1);
//                    }
//                }
//            }
//            ((AppMainActivity) getActivity()).loadFragment(FragmentGalleryNew.newInstance());

//        });

        //TODO
    }

    private fun saveSnipTimeToLocal() {
        if (timerSecond != 0) {
            val endSecond = timerSecond
            AppClass.getAppInstance().setSnipDurations(endSecond)
            // on screen tap blinking starts
            rlVideo.startAnimation(animBlink)
            blinkEffect.visibility = View.VISIBLE
            blinkEffect.animate()
                    .alpha(02f)
                    .setDuration(100)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            blinkEffect.visibility = View.GONE
                            blinkEffect.clearAnimation()
                            rlVideo.clearAnimation()
                        }
                    })

//        Log.d("seconds", String.valueOf(endSecond));
        }
    }

    private fun getVideoThumbnail(snip: Snip?, videoFile: File) {
        try {
//            File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
//                    VIDEO_DIRECTORY_NAME);
            val thumbsStorageDir = File(requireActivity().dataDir.toString() + "/" + VIDEO_DIRECTORY_NAME,
                    THUMBS_DIRECTORY_NAME)
            if (!thumbsStorageDir.exists()) {
                if (!thumbsStorageDir.mkdirs()) {
                    Log.d(TAG, "Oops! Failed create "
                            + VIDEO_DIRECTORY_NAME + " directory")
                    return
                }
            }
            val fullThumbPath: File
            fullThumbPath = File(thumbsStorageDir.path + File.separator
                    + "snip_" + snip!!.snip_id + ".png")
            Log.d(TAG, "saving video thumbnail at path: " + fullThumbPath + ", video path: " + videoFile.absolutePath)
            //Save the thumbnail in a PNG compressed format, and close everything. If something fails, return null
            val streamThumbnail = FileOutputStream(fullThumbPath)

            //Other method to get a thumbnail. The problem is that it doesn't allow to get at a specific time
            val thumb: Bitmap //= ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(),MediaStore.Images.Thumbnails.MINI_KIND);
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoFile.absolutePath)
                thumb = if (snip.is_virtual_version != 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(snip.start_time.toInt() * 1000000.toLong(),
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 100, 100)
                    } else {
                        retriever.getFrameAtTime(snip.start_time.toInt() * 1000000.toLong(),
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                } else {
                    retriever.frameAtTime
                }
                thumb.compress(Bitmap.CompressFormat.PNG, 80, streamThumbnail)
                thumb.recycle() //ensure the image is freed;
            } catch (ex: Exception) {
                Log.i(TAG, "MediaMetadataRetriever got exception:$ex")
            }
            streamThumbnail.close()
            //            snip.setThumbnailPath(fullThumbPath.getAbsolutePath());
            //update Snip
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
//            String currentDateandTime = sdf.format(new Date());
//            EventData eventData = new EventData();
//            eventData.setEvent_id(AppClass.getAppInsatnce().getLastEventId());
//            eventData.setEvent_title(currentDateandTime);
//            eventData.addEventSnip(snip);
//            AppClass.getAppInsatnce().saveAllEventSnips(snip);
//            if(isLast){
//                AppClass.getAppInsatnce().setInsertionInProgress(false);
//                thumbnailProcesingCompleted.onTaskCompleted(true);
//            }
            Log.d(TAG, "thumbnail saved successfully")
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File Not Found Exception : check directory path")
            e.printStackTrace()
        } catch (e: IOException) {
            Log.d(TAG, "IOException while closing the stream")
            e.printStackTrace()
        }
    }

    private fun getVideoThumbnailclick(videoFile: File) {
        try {
            val mediaStorageDir = File(Environment.getExternalStorageDirectory(),
                    VIDEO_DIRECTORY_NAME1)
            // Create storage directory if it does not exist
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    return
                }
            }
            val mediaFile = File(mediaStorageDir.path + File.separator
                    + "PhotoTHUM_" + System.currentTimeMillis() + ".png")
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    Log.d(TAG, "Oops! Failed create "
                            + VIDEO_DIRECTORY_NAME1 + " directory")
                    return
                }
            }
            Log.d(TAG, "saving video thumbnail at path: " + mediaFile + ", video path: " + videoFile.absolutePath)
            //Save the thumbnail in a PNG compressed format, and close everything. If something fails, return null
            val streamThumbnail = FileOutputStream(mediaFile)

            //Other method to get a thumbnail. The problem is that it doesn't allow to get at a specific time
            val thumb: Bitmap //= ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(),MediaStore.Images.Thumbnails.MINI_KIND);
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoFile.absolutePath)
                thumb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(1 * 1000000.toLong(),
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 100, 100)
                } else {
                    retriever.getFrameAtTime(1 * 1000000.toLong(),
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }


                //     thumb = retriever.getFrameAtTime();
                thumb.compress(Bitmap.CompressFormat.PNG, 80, streamThumbnail)
                thumb.recycle() //ensure the image is freed;
            } catch (ex: Exception) {
                Log.i(TAG, "MediaMetadataRetriever got exception:$ex")
            }
            streamThumbnail.close()
            Log.d(TAG, "thumbnail saved successfully")
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File Not Found Exception : check directory path")
            e.printStackTrace()
        } catch (e: IOException) {
            Log.d(TAG, "IOException while closing the stream")
            e.printStackTrace()
        }
    }

    //    public void dispatchTouchEvent(MotionEvent me) {
    //        // Call onTouchEvent of SimpleGestureFilter class
    //        this.detector.onTouchEvent(me);
    //    }
    //    @Override
    //    public void onSwipe(int direction) {
    //
    //        //Detect the swipe gestures and display toast
    //        String showToastMessage = "";
    //
    //        switch (direction) {
    //
    //            case GestureFilter.SWIPE_RIGHT:
    //                showToastMessage = "You have Swiped Right.";
    //                break;
    //            case GestureFilter.SWIPE_LEFT:
    //                showToastMessage = "You have Swiped Left.";
    //                break;
    //            case GestureFilter.SWIPE_DOWN:
    //                showToastMessage = "You have Swiped Down.";
    //                break;
    //            case GestureFilter.SWIPE_UP:
    //                showToastMessage = "You have Swiped Up.";
    //                break;
    //
    //        }
    //        Toast.makeText(getActivity(), showToastMessage, Toast.LENGTH_SHORT).show();
    //    }
    //    public  void accessRoomDatabase(){
    //        //Inserting data to Table
    //        Event event = new Event();
    //        Snip snip = new Snip();
    //        Hd_snips hd_snips = new Hd_snips();
    //        event.setEvent_title("test data");
    //        event.setEvent_created(345678987);
    //        AppRepository appRepository = AppRepository.getInstance();
    //        //Insert Data
    ////        appRepository.insertEvent(event);
    //
    //        AppViewModel appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);
    //        //Retriving Data from table
    //        appViewModel.getEventLiveData().observe(this, new Observer<List<Event>>() {
    //            @Override
    //            public void onChanged(List<Event> events) {
    //                Log.e("data loaded","data loaded");
    //
    //                ///Handle data here
    //            }
    //        });
    //        //Updating Data
    //        appRepository.updateEvent(event);
    //
    //        //Delete data
    //        appRepository.deleteEvent(event);
    //
    //    }
    override fun onAttach(context: Context) {
        super.onAttach(context)
        thumbnailProcesingCompleted = if (context is OnTaskCompleted) {
            context
        } else {
            throw RuntimeException(context.toString()
                    + " must implement OnGreenFragmentListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        thumbnailProcesingCompleted = null
    }

    //    clip edit listener
    override fun failed(operation: VideoOp) {}
    override fun changed(operation: VideoOp, videoPath: String) {
        when (operation) {
            VideoOp.MERGED -> {
            }
            VideoOp.CONCAT -> {
                if (processingDialog != null) {
                    processingDialog!!.dismiss()
                }
                //  Associating with the latest HD_snip entry; getLastHDSnipID
                hdSnips!!.video_path_unprocessed = videoPath
                hdSnips!!.hd_snip_id = AppClass.getAppInstance().lastHDSnipId.toInt()
                CoroutineScope(Default).launch {
                    appRepository!!.updateHDSnip(hdSnips!!)
                }
                CoroutineScope(Default).launch {
                    var lastSnip: Snip? = null
                    if (AppClass.getAppInstance().snips.isNotEmpty())
                        lastSnip = AppClass.getAppInstance().snips[AppClass.getAppInstance().snips.size - 1]
                    val mergeFile = File(hdSnips!!.video_path_unprocessed)
                    val splitTime = (totalDuration[0] - lastSnip?.snip_duration!!.roundToInt() - swipeValue).toInt()
                    //  swiped + recorded clip
                    videoUtils.trimToClip(mergeFile,
                            "${mergeFile.parent}/swiped.mp4",
                            splitTime,
                            totalDuration[0])
                    //  buffered clip
                    videoUtils.trimToClip(mergeFile,
                            "${mergeFile.parent}/buffered.mp4",
                            0,
                            splitTime)
                }
            }
            VideoOp.TRIMMED -> {
                Log.d(TAG, "$videoPath Completed")
            }
            VideoOp.SPEED -> {
            }
            VideoOp.FRAMES -> {
            }
            VideoOp.KEY_FRAMES -> {
            }
            VideoOp.ERROR -> {
            }
        }
    }
}