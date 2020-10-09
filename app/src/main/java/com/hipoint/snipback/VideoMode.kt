package com.hipoint.snipback

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.CamcorderProfile
import android.media.MediaCodec
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
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.hipoint.snipback.Utils.AutoFitTextureView
import com.hipoint.snipback.Utils.CountUpTimer
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.fragment.Feedback_fragment
import com.hipoint.snipback.fragment.FragmentGalleryNew
import com.hipoint.snipback.listener.IVideoOpListener.VideoOp
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.room.repository.AppRepository.Companion.instance
import com.hipoint.snipback.videoControl.VideoOpItem
import com.hipoint.snipback.videoControl.VideoService
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class VideoMode : Fragment(), View.OnClickListener, OnTouchListener, ActivityCompat.OnRequestPermissionsResultCallback {
    val swipeValue = 5 * 1000L  //  swipeBack duration

    private val VIDEO_DIRECTORY_NAME1 = "Snipback"
    private val totalDuration = intArrayOf(0)//  total combined duration of merged clip
    private val clipDuration = 30 * 1000L    //  Buffer duration
    private var userRecordDuration = 0       //  duration of user recorded time

    private var parentSnip: Snip? = null
    private var swipedFileNames: ArrayList<String> = arrayListOf() //  names of files generated from swiping left
    private var swipedRecording: SwipedRecording? = null

    //    private var actualClipTime = 0L
    private var clipQueue: Queue<File>? = null
    private var persistentSurface = MediaCodec.createPersistentInputSurface()

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
                if (abs(deltaX) > MIN_DISTANCE) {
                    // Left to Right swipe action
                    if (x2 > x1) {
                        if (mIsRecordingVideo) {
                            saveSnipTimeToLocal()
                        }
                    }
                    //  Right to left swipe action
                    if (x2 < x1) {
                        if (mIsRecordingVideo) {
                            handleLeftSwipe()
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
            val currentFingerSpacing: Float
            //            setUpCaptureRequestBuilder(mPreviewBuilder);
            if (event.pointerCount == 2) {
                // Multi touch logic
                currentFingerSpacing = getFingerSpacing(event)
                var delta = 0.03f //control the zoom sensitivity
                if (finger_spacing != 0f) {
                    if (currentFingerSpacing > finger_spacing) {
                        if (maxZoom - zoom_level <= delta) {
                            delta = (maxZoom - zoom_level).toFloat()
                        }
                        zoom_level += delta
                        //                        seekBar.setProgress((int)zoom_level);
                    } else if (currentFingerSpacing < finger_spacing) {
                        if (zoom_level - delta < 1f) {
                            delta = (zoom_level - 1f).toFloat()
                        }
                        zoom_level -= delta
                        //                        if (zoom_level == 1){
//                            seekBar.setProgress(0);
//                        } else {
////                            seekBar.setProgress((int) zoom_level);
//                        }
                    }
                    val ratio = (1.toFloat() / zoom_level).toFloat()
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
                mPreviewSession!!.setRepeatingRequest(mPreviewBuilder!!.build(), null,
                        mBackgroundHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Cannot connect to camera", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
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
        return sqrt(x * x + y * y.toDouble()).toFloat()
    }

    interface OnTaskCompleted {
        fun onTaskCompleted(success: Boolean)
    }

    companion object {
        private var videoMode: VideoMode? = null
//        var swipeProcessed: Boolean = false

        //Camera Orientation
        private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        private const val TAG = "Camera2VideoFragment"
        private const val FRAGMENT_DIALOG = "dialog"
        private const val REQUEST_VIDEO_PERMISSIONS = 1
        private const val VIDEO_DIRECTORY_NAME = "SnipBackVirtual"
        private const val THUMBS_DIRECTORY_NAME = "Thumbs"

        private val DEFAULT_ORIENTATIONS = SparseIntArray()
        private val INVERSE_ORIENTATIONS = SparseIntArray()

        //    private GestureFilter detector;
        //clips
        private var recordPressed = false //  to know if the user has actively started recording
        private var stopPressed = false //  to know if the user has actively ended recording, todo: this can be removed once a better handing is in place
        var recordClips = true //  to check if short clips should be recorded
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

        @JvmStatic
        fun newInstance(): VideoMode {
            if (videoMode == null)
                videoMode = VideoMode()

            return videoMode!!
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
     * A reference to check the required lens facing.
     */
    private var isBackFacingRequired: Boolean = true

    /**
     * A reference to the current [android.hardware.camera2.CameraCaptureSession] for
     * preview.
     */
    private var mPreviewSession: CameraCaptureSession? = null
    //    AppMainActivity.MyOnTouchListener onTouchListener;
    //
    //    private OnSwipeTouchListener touchListener=new OnSwipeTouchListener(requireActivity()) {
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
            if (recordClips && !stopPressed) {
//                parentSnipId = AppClass.getAppInstance().lastSnipId + 1
                startRecordingVideo()
            } else startPreview()
            mCameraOpenCloseLock.release()
            configureTransform(mTextureView.width, mTextureView.height)
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

    private var timerSecond: Int = 0
    private var hdSnips: Hd_snips? = null
    private var mSensorOrientation: Int? = null
    private var outputFilePath: String? = null
    private var lastUserRecordedPath: String? = null
    private var mPreviewBuilder: CaptureRequest.Builder? = null
    private var appRepository: AppRepository? = null
    private var animBlink: Animation? = null
    private var thumbnailProcessingCompleted: OnTaskCompleted? = null

    //Views
    private lateinit var rootView: View
    private lateinit var gallery: ImageButton
    private lateinit var settings: ImageButton
    private lateinit var recordButton: ImageButton
    private lateinit var recordStopButton: ImageButton
    private lateinit var r3Bookmark: ImageButton
    private lateinit var capturePrevious: ImageButton
    private lateinit var changeCamera: ImageButton
    private lateinit var r2Shutter: ImageButton
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
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        rootView = inflater.inflate(R.layout.fragment_videomode, container, false)

        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        animBlink = AnimationUtils.loadAnimation(context, R.anim.blink)

        bindViews()
        bindListeners()

        return rootView
    }

    /**
     * Binds views to references
     * */
    private fun bindViews() {
        rlVideo = rootView.findViewById(R.id.rl_video)
        gallery = rootView.findViewById(R.id.r_1)
        settings = rootView.findViewById(R.id.r_5)
        capturePrevious = rootView.findViewById(R.id.r_3)
        changeCamera = rootView.findViewById(R.id.r_2)
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
        r3Bookmark = rootView.findViewById(R.id.r_3_bookmark)
        r2Shutter = rootView.findViewById(R.id.r_2_shutter)
        zoomFactor = rootView.findViewById(R.id.zoom_factor)
    }

    /**
     * Binds views to required listeners
     * */
    private fun bindListeners() {
        r3Bookmark.setOnClickListener(this)
        recordStopButton.setOnClickListener(this)
        r2Shutter.setOnClickListener(this)
        zoomControlLayout.setOnClickListener(this)
        zoomIn.setOnClickListener(this)
        zoomOut.setOnClickListener(this)
        rlVideo.setOnTouchListener(this)
        rlVideo.setOnClickListener(this)
        recordButton.setOnClickListener(this)
        capturePrevious.setOnClickListener(this)
        //        detector = new GestureFilter(requireActivity(), this);

        //        ((AppMainActivity)requireActivity()).registerMyOnTouchListener(new AppMainActivity.MyOnTouchListener() {
        //            @Override
        //            public void onTouch(MotionEvent ev) {
        //                touchListener.onTouch(ev);
        //            }
        //        });

        //        rlVideo.setOnTouchListener((view, motionEvent) -> mTextureView.onTouch(view, motionEvent));
        //        accessRoomDatabase();
        mTextureView.setOnClickListener(this)
        mTextureView.setOnTouchListener(this)
        gallery.setOnClickListener { (activity as AppMainActivity?)!!.loadFragment(FragmentGalleryNew.newInstance(), true) }
        settings.setOnClickListener { showDialogSettingsMain() }
        changeCamera.setOnClickListener { switchCamera() }
        appRepository = instance
        mChronometer.onChronometerTickListener = OnChronometerTickListener {
            //                if (!resume) {
            val time = SystemClock.elapsedRealtime() - mChronometer.base
            val h = (time / 3600000).toInt()
            val m = (time - h * 3600000).toInt() / 60000
            val s = (time - h * 3600000 - m * 60000).toInt() / 1000
            val t = (if (h < 10) "0$h" else h.toString()) + ":" + (if (m < 10) "0$m" else m) + ":" + if (s < 10) "0$s" else s
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
        seekBar.max = (mMaxZoom - mMinZoom).roundToInt()
        seekBar.setOnSeekBarChangeListener(
                object : OnSeekBarChangeListener {
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        setCurrentZoom((mMinZoom + 1 + mProgress * zoomStep).roundToInt().toFloat())
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        setCurrentZoom((mMinZoom + 1 + progress.toFloat() * zoomStep).roundToInt().toFloat())
                        if (fromUser) mProgress = progress.toFloat()
                    }
                }
        )
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
            val manager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val mCameraId = manager.cameraIdList[0]
            manager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
        } catch (e: Exception) {
            Log.e(TAG, "Error during camera init")
            (-1).toFloat()
        }
    }

    /**
     * Sets up background thread and camera when the fragment is in the foreground
     * */
    override fun onResume() {
        super.onResume()

        startBackgroundThread()
        if (mTextureView.isAvailable) {
            openCamera(mTextureView.width, mTextureView.height)
        } else {
            mTextureView.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    /**
     * Closes the camrea and stops the background thread when fragment is not in the foreground
     * */
    override fun onPause() {
        closeCamera()
        stopBackgroundThread()

        super.onPause()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.rec -> {   //  Sets up UI and starts user triggered recording
                bottomContainer.visibility = View.INVISIBLE
                recStartLayout.visibility = View.VISIBLE

                // Once the record button is pressed we no longer need to capture clips since the
                // the user has started actively recording.
                recordClips = false
                recordPressed = true
                stopPressed = false
                userRecordDuration = 0
//                startRecordingVideo()
                if (mIsRecordingVideo && recordClips) {
                    restartRecording()  //  if record is pressed immediately after the app launches, causes crash todo: fix this

                    mChronometer.base = SystemClock.elapsedRealtime()
                    mChronometer.start()
                    mChronometer.visibility = View.VISIBLE
                } else {
                    startRecordingVideo()
                }
                swipedRecording = null
                while (clipQueue!!.size > 2) {
                    clipQueue!!.remove().delete()
                }
            }
            R.id.rec_stop -> {  // sets up UI and stops user triggered recording
                bottomContainer.visibility = View.VISIBLE
                recStartLayout.visibility = View.INVISIBLE
                (requireActivity() as AppMainActivity).showInGallery.add(File(outputFilePath!!).nameWithoutExtension)
                parentSnip = null   //  resetting the session parent Snip
                stopRecordingVideo()    // don't close session here since we have to resume saving clips
                attemptClipConcat() //  merge what is in the buffer with the recording
                // we can restart recoding clips if it is required at this point
                recordClips = true
                recordPressed = false
                stopPressed = true
                startRecordingVideo()
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
                getVideoThumbnailClick(File(outputFilePath!!))
                rlVideo.clearAnimation()
            }
            R.id.texture -> {
                saveSnipTimeToLocal()
            }
            R.id.r_3 -> {

            }
        }
    }

    /**
     * Processes the swipe that were made during user recording
     * */
    fun processPendingSwipes() {

        (requireActivity() as AppMainActivity).swipeProcessed = true

        if (swipedRecording != null) {
            if (swipedRecording?.fileName.equals(lastUserRecordedPath)) {
                val intentService = Intent(requireContext(), VideoService::class.java)
                val task = arrayListOf<VideoOpItem>()
                swipedRecording?.timestamps?.forEachIndexed { index, timeStamp ->

                    val outputFileName = "${File(swipedRecording?.fileName!!).parent}/${File(swipedRecording?.fileName!!).nameWithoutExtension}-$index.mp4"

                    (requireActivity() as AppMainActivity).showInGallery.add(File(outputFileName).nameWithoutExtension)
                    Log.d(TAG, "processPendingSwipes: \n Output = $outputFileName, \n start = ${(timeStamp - (swipeValue / 1000)).toInt()} \n end = $timeStamp")

                    task.add(VideoOpItem(
                            operation = VideoOp.TRIMMED,
                            clip1 = swipedRecording?.fileName!!,
                            clip2 = "",
                            startTime = max((timeStamp - (swipeValue / 1000)).toInt(), 0),
                            endTime = timeStamp,
                            outputPath = outputFileName))

                }

                intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
                VideoService.enqueueWork(requireContext(), intentService)
            }
        }
    }

    private fun handleLeftSwipe() {

        if (recordClips) {
            val t1Animation = ObjectAnimator.ofFloat(recordButton, "translationX", 0f, -80f, 0f)
            t1Animation.duration = 1500
            t1Animation.start()
        }

        if (recordClips) {    //  if clips are being recorded

            if (clipQueue!!.size > 1) { // there is more than 1 items in the queue
                //  1.  remove the 1st item - clip1
                //  2.  restart recording session so that the recording doesn't stop and we get the file we need to merge
                //  3.  remove the next item - clip 2
                //  4.  merge and split

                val clip1 = clipQueue!!.remove()
                restartRecording()
                val clip2 = clipQueue!!.remove()

                if(clip1.length() == 0L || clip2.length() == 0L)
                    return

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val mergeFilePath = "${clip1.parent!!}/merged-$timeStamp.mp4"
                swipedFileNames.add("${File(outputFilePath!!).parent}/merged-$timeStamp-1")  //  indication of swiped file,"-1" since we want the second half of the split
                (requireActivity() as AppMainActivity).showInGallery.add("merged-$timeStamp-1")  //  indication of swiped file,"-1" since we want the second half of the split

                val intentService = Intent(requireContext(), VideoService::class.java)
                val task = arrayListOf(VideoOpItem(
                        operation = VideoOp.CONCAT,
                        clip1 = clip1.absolutePath,
                        clip2 = clip2.absolutePath,
                        outputPath = mergeFilePath))
                intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
                VideoService.enqueueWork(requireContext(), intentService)

            } else {    //  there is only  item in the queue
                //  1.  check duration of clip and if swipe duration < video duration
                //      1.  restart recording session
                //      2.  remove item from queue and split the video
                //  2.  else save what we have. restart the recording inform user.
                if (clipQueue!!.isNotEmpty()) {

                    restartRecording()

                    val clip = clipQueue!!.remove()
                    val actualClipTime = (requireActivity() as AppMainActivity).getMetadataDurations(arrayListOf(clip.absolutePath))[0]
                    val swipeClipDuration = swipeValue / 1000
                    if (actualClipTime >= swipeClipDuration) {
                        //  splitting may not work for this so we opt for trim
                        Log.d(TAG, "actualClipTime: $actualClipTime\nswipeValue: $swipeValue\nswipeClipDuration: $swipeClipDuration")
                        swipedFileNames.add("trimmed-${clip.nameWithoutExtension}")
                        (requireActivity() as AppMainActivity).showInGallery.add("trimmed-${clip.nameWithoutExtension}")

                        val intentService = Intent(requireContext(), VideoService::class.java)
                        val task = arrayListOf(VideoOpItem(
                                operation = VideoOp.TRIMMED,
                                clip1 = clip.absolutePath,
                                clip2 = "",
                                startTime = max((actualClipTime - swipeClipDuration).toInt(), 0),
                                endTime = actualClipTime,
                                outputPath = "${clip.parent}/trimmed-${clip.name}").also { toString() })
                        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
                        VideoService.enqueueWork(requireContext(), intentService)
                    } else { //  save what we have
                        swipedFileNames.add(clip.nameWithoutExtension)
                        (requireActivity() as AppMainActivity).showInGallery.add(clip.nameWithoutExtension)
                        (requireActivity() as AppMainActivity).addSnip(clip.absolutePath, actualClipTime, actualClipTime)
                    }
                }
            }
        } else {    // swiped during video recording
            swipedFileNames.add(File(outputFilePath!!).nameWithoutExtension)    // video file currently being recorded
            if (swipedRecording == null) {
                swipedRecording = outputFilePath?.let { SwipedRecording(it) }
            }
            if (swipedRecording!!.fileName?.equals(outputFilePath)!!) {
                swipedRecording!!.timestamps.add(timerSecond)
            }
        }

//        Toast.makeText(requireActivity(), "Capturing Previous", Toast.LENGTH_SHORT).show()

        blinkAnimation()

        (requireActivity() as AppMainActivity).swipeProcessed = false
    }

    /**
     * Stops and restarts the mediaRecorder,
     * assuming the mediaRecorder is initialized and already recording
     */
    private fun restartRecording() {
        try {
            mMediaRecorder?.stop()
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
        setUpMediaRecorder()
        mMediaRecorder?.start()
    }

    /**
     * Check if recorded files can be concatenated, else proceed to process left swipes
     */
    private fun attemptClipConcat() {
        if (clipQueue!!.size >= 2) {
            val clip1: File = clipQueue!!.remove()
            val clip2: File = clipQueue!!.remove()

            if(clip1.length() == 0L || clip2.length() == 0L)    // the file is not formed and concat will not work
                return

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val mergeFilePath = "${clip1.parent!!}/merged-$timeStamp.mp4"
            (requireActivity() as AppMainActivity).showInGallery.add(clip2.nameWithoutExtension)   //  only the actual recording is shown

            val intentService = Intent(requireContext(), VideoService::class.java)
            val task = arrayListOf(VideoOpItem(
                    operation = VideoOp.CONCAT,
                    clip1 = clip1.absolutePath,
                    clip2 = clip2.absolutePath,
                    outputPath = mergeFilePath))
            intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
            VideoService.enqueueWork(requireContext(), intentService)
        } else {
            processPendingSwipes()
        }
    }

    private fun showDialogSettingsMain() {
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

    private fun showDialogSettingsResolution() {
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
            requestPermissions(requireActivity(), VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS)
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
            } /*else {
                 ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG)
            }*/
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

        if (null == activity || requireActivity().isFinishing) {
            return
        }
        val manager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            Log.d(TAG, "tryAcquire")
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
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
            requireActivity().finish()
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
    private fun switchCamera() {
        isBackFacingRequired = !isBackFacingRequired
        closeCamera()
        reOpenCamera()
    }

    private fun reOpenCamera() {
        if (mTextureView.isAvailable) {
            openCamera(mTextureView.width, mTextureView.height)
        } else {
            mTextureView.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    private fun getCameraId(lens: Int): String {
        val manager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
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

    private fun requestPermission() {
        Dexter.withContext(requireContext()).withPermissions(Manifest.permission.CAMERA,
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
                }).withErrorListener { Toast.makeText(requireActivity().applicationContext, "Error occurred! ", Toast.LENGTH_SHORT).show() }
                .onSameThread()
                .check()
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(activity)
        builder.apply {
            setTitle(getString(R.string.message_need_permission))
            setMessage(getString(R.string.message_permission))
            setPositiveButton(getString(R.string.title_go_to_setting)) { dialog: DialogInterface, _: Int ->
                dialog.cancel()
                openSettings()
            }
            show()
        }
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
                                Toast.makeText(requireContext(), "Cannot connect to camera", Toast.LENGTH_SHORT).show()
                                requireActivity().finish()
                            } catch (e1: IllegalStateException) {
                                e1.printStackTrace()
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            activity
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
        if (null == mPreviewSize || null == activity) {
            return
        }
        val rotation = requireActivity().windowManager.defaultDisplay.rotation
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

        CoroutineScope(Main).launch { mTextureView.setTransform(matrix) }
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
        if (activity == null)
            return
        //  ensuring the media recorder is recreated
        try {
            mMediaRecorder!!.reset()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }

        val rotation = requireActivity().windowManager.defaultDisplay.rotation
        when (mSensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES -> mMediaRecorder!!.setOrientationHint(DEFAULT_ORIENTATIONS[rotation])
            SENSOR_ORIENTATION_INVERSE_DEGREES -> mMediaRecorder!!.setOrientationHint(INVERSE_ORIENTATIONS[rotation])
        }
        mMediaRecorder!!.apply {
//            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            //        if (outputFilePath == null || outputFilePath.isEmpty()) {
            outputFilePath = outputMediaFile!!.absolutePath
            //        }
            setOutputFile(outputFilePath)
            if (recordClips) {    //  so that the actual recording is not affected by clip duration.
                setMaxDuration(clipDuration.toInt())
            } else {
                setMaxDuration(0)
            }
            //        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            val profile = chooseCamcorderProfile()
            setVideoFrameRate(profile.videoFrameRate)
            setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
            setVideoEncodingBitRate(profile.videoBitRate)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
//            setAudioEncodingBitRate(profile.audioBitRate)
//            setAudioSamplingRate(profile.audioSampleRate)
            setInputSurface(persistentSurface)

            setOnInfoListener { mr, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && recordClips) {
                    try {
                        restartRecording()
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                    }
                }
            }
            prepare()
        }
    }

    private fun getVideoFilePath(context: Context): String {
        val dir = context.getExternalFilesDir(null)
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
            Log.d(TAG, "clip added to queue")
            clipQueue!!.add(mediaFile)
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
//            val recorderSurface = mMediaRecorder!!.surface
            surfaces.add(persistentSurface)
            mPreviewBuilder!!.addTarget(persistentSurface)

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
                        Toast.makeText(requireContext(), "Cannot connect to camera", Toast.LENGTH_SHORT).show()
                        requireActivity().finish()
                    } catch (e1: IllegalStateException) {
                        e1.printStackTrace()
                    }
                    mIsRecordingVideo = true
                    mMediaRecorder!!.start()
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
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
            mChronometer.base = SystemClock.elapsedRealtime()
            mChronometer.start()
            mChronometer.visibility = View.VISIBLE
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
            if (option.height == option.width * h / w) {
                if (option.width >= width &&
                        option.height >= height) {
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
                Collections.min(bigEnough, CompareSizesByArea())
            }
            notBigEnough.isNotEmpty() -> {
                Collections.max(notBigEnough, CompareSizesByArea())
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
        userRecordDuration = timerSecond
        mChronometer.stop()
        mChronometer.visibility = View.INVISIBLE
        mChronometer.text = ""
        lastUserRecordedPath = outputFilePath

        val retriever = MediaMetadataRetriever()
        clipQueue!!.forEach(Consumer { file: File ->
            if(file.length() > 0L) {
                retriever.setDataSource(file.absolutePath)
                Log.d(TAG, "stopRecordingVideo: file in queue = ${file.absolutePath}")
                val currentClipDuration: Int = TimeUnit.MILLISECONDS.toSeconds(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()).toInt()
                Log.d(TAG, "stopRecordingVideo:\nCurrent clip duration: $currentClipDuration\nTotalDuration: ${totalDuration[0]}")
                totalDuration[0] += currentClipDuration
                (requireActivity() as AppMainActivity).addSnip(file.absolutePath, currentClipDuration,  /*totalDuration[0]*/currentClipDuration)
            }
        })
        retriever.release()
//        outputFilePath = null
        startPreview()
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
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(activity)
                    .setMessage(arguments?.getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok) { _, _ -> activity?.finish() }
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
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(activity)
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        requestPermissions(requireActivity(), VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS)
                    }
                    .setNegativeButton(android.R.string.cancel
                    ) { _, _ -> requireActivity().finish() }
                    .create()
        }
    }


    private fun saveSnipTimeToLocal() {
        if (timerSecond != 0) {
            val endSecond = timerSecond
            AppClass.getAppInstance().setSnipDurations(endSecond)
            // on screen tap blinking starts
            blinkAnimation()

//        Log.d("seconds", String.valueOf(endSecond));
        }
    }

    /**
     * Flashes the UI to indicate that an action has occurred
     * */
    private fun blinkAnimation() {
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
    }

    private fun getVideoThumbnailClick(videoFile: File) {
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

    fun videoProcessing(isProcessing: Boolean) {
        if (isProcessing) {
            gallery.alpha = .5F
            changeCamera.alpha = .5F
            gallery.isEnabled = false
            changeCamera.isEnabled = false
        } else {
            gallery.alpha = 1F
            changeCamera.alpha = 1F
            gallery.isEnabled = true
            changeCamera.isEnabled = true
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
    //        Toast.makeText(requireActivity(), showToastMessage, Toast.LENGTH_SHORT).show();
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
        thumbnailProcessingCompleted = if (context is OnTaskCompleted) {
            context
        } else {
            throw RuntimeException(context.toString()
                    + " must implement OnGreenFragmentListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        thumbnailProcessingCompleted = null
    }

    private fun <T> cameraCharacteristics(cameraId: String, key: CameraCharacteristics.Key<T>): T {
        val manager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
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
}