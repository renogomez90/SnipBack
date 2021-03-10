package com.hipoint.snipback.fragment

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.*
import android.view.View.OnTouchListener
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import android.widget.Chronometer.OnChronometerTickListener
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.exozet.android.core.extensions.disable
import com.exozet.android.core.extensions.enable
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.FocusView
import com.hipoint.snipback.R
import com.hipoint.snipback.SwipedRecording
import com.hipoint.snipback.Utils.AutoFitTextureView
import com.hipoint.snipback.Utils.BufferDataDetails
import com.hipoint.snipback.Utils.SimpleOrientationListener
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.application.AppClass.swipeProcessed
import com.hipoint.snipback.control.CameraControl
import com.hipoint.snipback.dialog.SettingsDialog
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.enums.SwipeAction
import com.hipoint.snipback.listener.IRecordUIListener
import com.hipoint.snipback.listener.ISettingsClosedListener
import com.hipoint.snipback.listener.IVideoOpListener.VideoOp
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.room.repository.AppRepository.Companion.instance
import com.hipoint.snipback.service.VideoService
import com.hipoint.snipback.service.VideoService.Companion.bufferDetails
import com.hipoint.snipback.videoControl.VideoOpItem
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.fragment_gallery.*
import kotlinx.android.synthetic.main.fragment_videomode.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt


class VideoMode : Fragment(), View.OnClickListener, OnTouchListener, ActivityCompat.OnRequestPermissionsResultCallback, IRecordUIListener, ISettingsClosedListener {

    var swipeValue         = 5 * 1000L  //  swipeBack duration
    var clipDuration       = 30 * 1000L
    var userRecordDuration = 0          //  duration of user recorded time

    private val VIDEO_DIRECTORY_NAME1 = "Snipback"
    private val swipedFileNames : ArrayList<String> = arrayListOf()                   //  names of files generated from swiping left
    private var parentSnip      : Snip?             = null
    private var currentOperation: CurrentOperation  = CurrentOperation.CLIP_RECORDING
    //  orientation previous orientation to decide button rotation animation
    var previousOrientation = SimpleOrientationListener.VideoModeOrientation.PORTRAIT

    //dialogs
    var settingsDialog: SettingsDialog? = null

    var zoomFactor: TextView? = null

    //zoom slider controls
    var mProgress       = 0f
    var mMinZoom        = 0f
    var mMaxZoom        = 0f
    var currentProgress = 1f
    val zoomStep        = 1f

    //left swipe
    private var point1 = 0f
    private var point2 = 0f

    private val swipeAnimationListener = object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator?) = Unit

        override fun onAnimationEnd(animation: Animator?) {
            blinkAnimation()
        }

        override fun onAnimationCancel(animation: Animator?) = Unit

        override fun onAnimationRepeat(animation: Animator?) = Unit
    }

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
        if(v.id == R.id.swipeDetection) {
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    point1 = when (previousOrientation) {
                        SimpleOrientationListener.VideoModeOrientation.PORTRAIT -> {
                            event.x
                        }
                        SimpleOrientationListener.VideoModeOrientation.REV_LANDSCAPE -> {
                            -(event.y)
                        }
                        else -> {
                            event.y
                        }
                    }
                MotionEvent.ACTION_UP -> {
                    point2 = when (previousOrientation) {
                        SimpleOrientationListener.VideoModeOrientation.PORTRAIT -> {
                            event.x
                        }
                        SimpleOrientationListener.VideoModeOrientation.REV_LANDSCAPE -> {
                            -(event.y)
                        }
                        else -> {
                            event.y
                        }
                    }
                    val deltaX = point2 - point1
                    if (abs(deltaX) > MIN_DISTANCE) {
                        // Left to Right swipe action
                        if (point2 > point1) {
                            if (cameraControl!!.isRecordingVideo()) {   //  media recorder is capturing
                                if (cameraControl!!.isRecordingClips()) {
                                    handleRightSwipe()
                                } else {
                                    saveSnipTimeToLocal()
                                }
                            }
                        }
                        //  Right to left swipe action
                        if (point2 < point1) {
                            if (cameraControl!!.isRecordingVideo()) {
                                handleLeftSwipe()
                            }
                        }
                    } else {
                        startFocusAtTouchPoint(v, event)
                    }
                }
            }
        }else {
            if (cameraControl!!.handleZoomEvent(event)) return false
        }
        return true
    }

    /**
     * Attempts to start focusing on the current touch point
     */
    private fun startFocusAtTouchPoint(view: View, event: MotionEvent) {
        val sensorArraySize = cameraControl?.getSensorSize()
        sensorArraySize?.let {
            val halfTouchWidth = 50
            val halfTouchHeight = 50
            val focusAreaTouch =
                MeteringRectangle(
                        max(((event.x / view.width.toFloat()) * sensorArraySize.width().toFloat()) - halfTouchHeight, 0F).roundToInt(),
                        max(((event.y / view.height.toFloat()) * sensorArraySize.height().toFloat()) - halfTouchWidth, 0F).roundToInt(),
                        halfTouchWidth * 2,
                        halfTouchHeight * 2,
                        MeteringRectangle.METERING_WEIGHT_MAX - 1)

            cameraControl?.startFocus(focusAreaTouch)

            //  show indication of touch
            focusOverlay.setFocusPoint(event.x, event.y)
            focusOverlay.startShowing()
        }
    }

    interface OnTaskCompleted {
        fun onTaskCompleted(success: Boolean)
    }

    companion object {
        const val VIDEO_DIRECTORY_NAME = "SnipBackVirtual"
        const val SNAPBACK_ACTION      = "com.hipoint.snipback.SNAPBACK_ACTION"
        const val PENDING_SWIPE_ACTION = "com.hipoint.snipback.PROCESS_SWIPE_ACTION"
        const val UI_UPDATE_ACTION     = "com.hipoint.snipback.UPDATE_UI"
        const val MIN_DISTANCE         = 150

        private var videoMode      : VideoMode?       = null
        internal var swipedRecording: SwipedRecording? = null

        private const val TAG = "Camera2VideoFragment"
        private const val FRAGMENT_DIALOG = "dialog"
        private const val THUMBS_DIRECTORY_NAME = "Thumbs"

        private const val REQUEST_VIDEO_PERMISSIONS = 1
        fun getSwipedRecording(): SwipedRecording{
            if(swipedRecording == null)
                swipedRecording = SwipedRecording("")

            return swipedRecording!!
        }

        var recordClips = true //  to check if short clips should be recorded
        val VIDEO_PERMISSIONS = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)

        @JvmStatic
        fun newInstance(): VideoMode {
            if (videoMode == null)
                videoMode = VideoMode()
            return videoMode!!
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
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val mSurfaceTextureListener: TextureView.SurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int, height: Int,
        ) {
            cameraControl!!.openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int, height: Int,
        ) {
            cameraControl!!.configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = true

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) { }
    }

    private var timerSecond                 : Int              = 0
    private var appRepository               : AppRepository?   = null
    private var animBlink                   : Animation?       = null
    private var thumbnailProcessingCompleted: OnTaskCompleted? = null
    private var cameraControl               : CameraControl?   = null

    //Views
    private lateinit var rootView        : View
    private lateinit var swipeDetection  : View
    private lateinit var focusOverlay    : FocusView
    private lateinit var gallery         : ImageButton
    private lateinit var settings        : ImageButton
    private lateinit var recordButton    : ImageButton
    private lateinit var recordStopButton: ImageButton
    private lateinit var r3Bookmark      : ImageButton
    private lateinit var capturePrevious : ImageButton
    private lateinit var changeCamera    : ImageButton
    private lateinit var r2Shutter       : ImageButton
    private lateinit var takePhoto       : ImageButton
    private lateinit var snapbackBtn     : ImageButton
    private lateinit var tvTimer         : TextView
    private lateinit var mChronometer    : Chronometer
    private lateinit var blinkEffect     : View
    private lateinit var rlVideo         : ConstraintLayout
    private lateinit var recStartLayout  : ConstraintLayout
    private lateinit var bottomContainer : ConstraintLayout
    private lateinit var seekBar         : SeekBar

    private val pref: SharedPreferences by lazy { requireContext().getSharedPreferences(
            SettingsDialog.SETTINGS_PREFERENCES,
            Context.MODE_PRIVATE) }
    private val uiUpdateReceiver: BroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val showProgress = intent.getIntExtra("progress", -1)
//                    if (showProgress == VideoService.STATUS_SHOW_PROGRESS) {
//                        videoProcessing(true)
//                    } else {
//                        videoProcessing(false)
//                    }
                }
            }
        }
    }

    //  get trigger for processing pending swipe and start processing
    private val processSwipeReceiver: BroadcastReceiver by lazy {
        object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let{
                    val processedVideoPath = it.getStringExtra("processedVideoPath")!!
                    val comingFrom = it.getSerializableExtra("comingFrom") as CurrentOperation
                    processPendingSwipes(processedVideoPath, comingFrom)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
    ): View? {
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

        previousOrientation = if(resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
            SimpleOrientationListener.VideoModeOrientation.PORTRAIT
        else
            SimpleOrientationListener.VideoModeOrientation.LANDSCAPE

        rootView = inflater.inflate(R.layout.fragment_videomode, container, false)
        animBlink = AnimationUtils.loadAnimation(context, R.anim.blink)
        bindViews()
        setupCameraControl()
        bindListeners()


        val mOrientationListener: SimpleOrientationListener = object : SimpleOrientationListener(
                context) {
            override fun onSimpleOrientationChanged(orientation: Int) {
                previousOrientation = when (orientation) {
                    VideoModeOrientation.LANDSCAPE.ordinal -> {
                        cameraControl?.setCurrentOrientation(90)
                        doRotation90F()
                        VideoModeOrientation.LANDSCAPE
                    }
                    VideoModeOrientation.REV_LANDSCAPE.ordinal -> {
                        cameraControl?.setCurrentOrientation(270)
                        doRotation270F()
                        VideoModeOrientation.REV_LANDSCAPE
                    }
                    else -> {
                        cameraControl?.setCurrentOrientation(0)
                        doRotation0F()
                        VideoModeOrientation.PORTRAIT
                    }
                }
            }
        }
        mOrientationListener.enable()

        return rootView
    }

    override fun onDestroy() {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun landScapeMode(view: View?) {
        val rotate = ObjectAnimator.ofFloat(view, "rotation", 0F, 90F)
        rotate.duration = 800
        rotate.start()
  }

    private fun portraitMode(view: View?) {
        if (previousOrientation == SimpleOrientationListener.VideoModeOrientation.LANDSCAPE){
            val rotate = ObjectAnimator.ofFloat(view, "rotation", 90F, 0F)
            rotate.duration = 800
            rotate.start()
        } else if(previousOrientation == SimpleOrientationListener.VideoModeOrientation.REV_LANDSCAPE){
            val rotate = ObjectAnimator.ofFloat(view, "rotation", -90F, 0F)
            rotate.duration = 800
            rotate.start()
        }
    }

    private fun revLandScapeMode(view: View?) {
        val rotate = ObjectAnimator.ofFloat(view, "rotation", 0F, -90F)
        rotate.duration = 800
        rotate.start()
    }

    fun doRotation0F() {
        portraitMode(takePhoto)
        portraitMode(gallery)
        portraitMode(changeCamera)
        portraitMode(settings)
        portraitMode(con)
    }

    fun doRotation90F() {
        landScapeMode(takePhoto)
        landScapeMode(gallery)
        landScapeMode(changeCamera)
        landScapeMode(settings)
        landScapeMode(con)
    }

    fun doRotation270F(){
        revLandScapeMode(takePhoto)
        revLandScapeMode(gallery)
        revLandScapeMode(changeCamera)
        revLandScapeMode(settings)
        revLandScapeMode(con)
    }

    private fun setupCameraControl() {
        if(cameraControl == null)
            cameraControl = CameraControl(requireActivity())

        clipDuration = (pref.getInt(SettingsDialog.BUFFER_DURATION, 1) * 60 * 1000).toLong()
        swipeValue = (pref.getInt(SettingsDialog.QB_DURATION, 5) * 1000).toLong()

        cameraControl!!.apply {
            setRecordUIListener(this@VideoMode)
            setPreviewTexture(mTextureView)
            setClipDuration(clipDuration)
            setRecordClips(true)
            currentOperation = CurrentOperation.CLIP_RECORDING
        }
    }

    /**
     * Binds views to references
     * */
    private fun bindViews() {
        rlVideo          = rootView.findViewById(R.id.rl_video)
        gallery          = rootView.findViewById(R.id.gallery_btn)
        settings         = rootView.findViewById(R.id.menu_btn)
        capturePrevious  = rootView.findViewById(R.id.back_video_btn)
        changeCamera     = rootView.findViewById(R.id.switch_cam_btn)
        recordButton     = rootView.findViewById(R.id.rec)
        mChronometer     = rootView.findViewById(R.id.chronometer)
        mTextureView     = rootView.findViewById(R.id.texture)
        blinkEffect      = rootView.findViewById(R.id.overlay)
        recStartLayout   = rootView.findViewById(R.id.rec_start_container)
        bottomContainer  = rootView.findViewById(R.id.bottom_cont)
        recordStopButton = rootView.findViewById(R.id.rec_stop)
        seekBar          = rootView.findViewById(R.id.zoom_controller)
        r3Bookmark       = rootView.findViewById(R.id.r_3_bookmark)
        r2Shutter        = rootView.findViewById(R.id.r_2_shutter)
        takePhoto        = rootView.findViewById(R.id.shutter_btn)
        snapbackBtn      = rootView.findViewById(R.id.back_photo_btn)
        zoomFactor       = rootView.findViewById(R.id.zoom_factor)
        swipeDetection   = rootView.findViewById(R.id.swipeDetection)
        focusOverlay     = rootView.findViewById(R.id.focus_overlay)
    }

    /**
     * Binds views to required listeners
     * */
    private fun bindListeners() {
        r3Bookmark.setOnClickListener(this)
        recordStopButton.setOnClickListener(this)
        r2Shutter.setOnClickListener(this)
        rlVideo.setOnClickListener(this)
        recordButton.setOnClickListener(this)
        capturePrevious.setOnClickListener(this)
        mTextureView.setOnClickListener(this)
        takePhoto.setOnClickListener(this)
        snapbackBtn.setOnClickListener(this)
        rlVideo.setOnTouchListener(this)
        mTextureView.setOnTouchListener(this)
        swipeDetection.setOnTouchListener(this)
        gallery.setOnClickListener {
            Log.d(TAG, "bindListeners: gallery btn clicked")
            (requireActivity() as AppMainActivity).loadFragment(FragmentGalleryNew.newInstance()!!,
                    true)
        }
        settings.setOnClickListener { showDialogSettingsMain() }

        changeCamera.setOnClickListener {
            cameraControl!!.closeToSwitchCamera()
            cameraControl!!.stopBackgroundThread()
            cameraControl = null

            updateFlags(recordClips = true, recordPressed = false, stopPressed = false) //  so the camera will continue recording clips with the new camera
            setupCameraControl()
            cameraControl!!.startBackgroundThread()
            if (mTextureView.isAvailable) {
                cameraControl?.openCamera(mTextureView.width, mTextureView.height)
            } else {
                mTextureView.surfaceTextureListener = mSurfaceTextureListener
            }
        }

        appRepository = instance
        mChronometer.onChronometerTickListener = OnChronometerTickListener {
            CoroutineScope(Main).launch {
                val time = SystemClock.elapsedRealtime() - mChronometer.base
                val h = (time / 3600000).toInt()
                val m = (time - h * 3600000).toInt() / 60000
                val s = (time - h * 3600000 - m * 60000).toInt() / 1000
                val t = "${if (h < 10) "0$h" else h.toString()}:${if (m < 10) "0$m" else m}:${if (s < 10) "0$s" else s}"
                mChronometer.text = t
                val minutes = (SystemClock.elapsedRealtime() - mChronometer.base) / 1000 / 60
                val seconds = (SystemClock.elapsedRealtime() - mChronometer.base) / 1000 % 60
                val elapsedMillis = (SystemClock.elapsedRealtime() - mChronometer.base).toInt()
                timerSecond = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis.toLong()).toInt()
                //                    elapsedTime = SystemClock.elapsedRealtime();
                Log.d(TAG, "onChronometerTick: $minutes : $seconds")
            }
        }
        mMinZoom = cameraControl!!.getMinZoom()
        mMaxZoom = cameraControl!!.getMaxZoom() - 1
//        seekBar.max = (mMaxZoom - mMinZoom).roundToInt()
        seekBar.max = 100
        seekBar.setOnSeekBarChangeListener(
                object : OnSeekBarChangeListener {
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        cameraControl?.setCurrentZoom(1 + mMaxZoom * mProgress / 100)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        cameraControl?.setCurrentZoom(1 + mMaxZoom * progress.toFloat() / 100)
                        if (fromUser) mProgress = progress.toFloat()
                    }
                }
        )
    }

    /**
     * Sets up background thread and camera when the fragment is in the foreground
     * */
    override fun onResume() {
        super.onResume()
        (requireActivity() as AppMainActivity).hideOrShowProgress(visible = true)

        if(cameraControl == null){
            setupCameraControl()
        }

        cameraControl?.startBackgroundThread()
        if(hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            if (mTextureView.isAvailable) {
                cameraControl?.openCamera(mTextureView.width, mTextureView.height)
            } else {
                mTextureView.surfaceTextureListener = mSurfaceTextureListener
            }
        }else{
            requestVideoPermissions()
        }
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(uiUpdateReceiver,
                IntentFilter(
                        UI_UPDATE_ACTION))
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(processSwipeReceiver,
                IntentFilter(
                        PENDING_SWIPE_ACTION))
//        videoProcessing(false)

    }

    /**
     * Closes the camera and stops the background thread when fragment is not in the foreground
     * */
    override fun onPause() {
        cameraControl?.closeCamera()
        cameraControl?.stopBackgroundThread()
        cameraControl = null
        seekBar.progress = 0

        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(uiUpdateReceiver)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(processSwipeReceiver)
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.rec -> {   //  Sets up UI and starts user triggered recording
                bottomContainer.visibility = View.INVISIBLE
                recStartLayout.visibility = View.VISIBLE

                // Once the record button is pressed we no longer need to capture clips since the
                // the user has started actively recording.

                userRecordDuration = 0
//                startRecordingVideo()
                with(cameraControl!!) {
                    if (isRecordingVideo() && isRecordingClips()) {
                        updateFlags(recordClips = false, recordPressed = true, stopPressed = false)
                        try {
                            CoroutineScope(Default).launch { ensureRecordingRestart() }
                        } catch (e: IllegalStateException) {
                            //  attempt to reopen the camera
                            Log.e(TAG, "Forcing camera restart")
                            closeCamera()
                            if (mTextureView.isAvailable) {
                                openCamera(mTextureView.width, mTextureView.height)
                                startRecordingVideo()
                            }
                        }
                        mChronometer.base = SystemClock.elapsedRealtime()
                        mChronometer.start()
                        mChronometer.visibility = View.VISIBLE
                    } else {
                        startRecordingVideo()
                    }
                }

                updateFlags(recordClips = false, recordPressed = true, stopPressed = false)
                swipeProcessed = false
                swipedRecording = null
                while (cameraControl!!.clipQueueSize() > 3) {
                    cameraControl?.removeClipQueueItem()?.delete()
                }
            }
            R.id.rec_stop -> {  // sets up UI and stops user triggered recording
                bottomContainer.visibility = View.VISIBLE
                recStartLayout.visibility = View.INVISIBLE
                AppClass.showInGallery.add(File(cameraControl?.getCurrentOutputPath()!!).nameWithoutExtension)
                parentSnip = null   //  resetting the session parent Snip
                CoroutineScope(Default).launch {
                    cameraControl?.stopRecordingVideo()    // don't close session here since we have to resume saving clips
                    attemptClipConcat() //  merge what is in the buffer with the recording
                    // we can restart recoding clips if it is required at this point
                    recordClips = true
                    updateFlags(recordClips = recordClips,
                            recordPressed = false,
                            stopPressed = true)
                    ensureRecordingRestart()
                }
            }
            R.id.r_3_bookmark -> {
                saveSnipTimeToLocal()
            }
            R.id.shutter_btn,
            R.id.r_2_shutter,
            -> {
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

                cameraControl?.startStillCaptureRequest()
//                File filevideopath = new File(cameraControl?.getCurrentOutputPath());
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

//                getVideoThumbnailClick(File(cameraControl?.getCurrentOutputPath()!!))
                rlVideo.clearAnimation()
            }
            R.id.texture -> saveSnipTimeToLocal()
            R.id.back_video_btn -> handleLeftSwipe()
            R.id.back_photo_btn -> handleRightSwipe()
        }
    }

    private fun updateFlags(recordClips: Boolean, recordPressed: Boolean, stopPressed: Boolean) {
        cameraControl?.apply {
            setRecordClips(recordClips)
            setRecordPressed(recordPressed)
            setStopPressed(stopPressed)

            currentOperation = if(recordClips)
                CurrentOperation.CLIP_RECORDING
            else
                CurrentOperation.VIDEO_RECORDING
        }
    }

    /**
     * Processes the swipe that were made during user recording
     * */
    fun processPendingSwipes(
            newVideoPath: String = swipedRecording?.originalFilePath ?: "",
            currentOperation: CurrentOperation = CurrentOperation.VIDEO_RECORDING,
    ) {
        Log.d(TAG, "processPendingSwipes: started")
        swipeProcessed = true

        if (swipedRecording != null) {  // we have swiped during the user recording
            if (swipedRecording?.originalFilePath.equals(cameraControl?.getLastUserRecordedPath())) {
                val parentPath = File(swipedRecording?.originalFilePath!!).parent

                val intentService = Intent(requireContext(), VideoService::class.java)
                val task = arrayListOf<VideoOpItem>()

                val retriever = MediaMetadataRetriever()
                val originalBuffer = "$parentPath/buff-${File(swipedRecording?.originalFilePath!!).nameWithoutExtension}.mp4"
                val originalVideo = "$parentPath/${File(swipedRecording?.originalFilePath!!).nameWithoutExtension}.mp4"

                retriever.setDataSource(newVideoPath)

                val totalDuration = TimeUnit.MILLISECONDS.toSeconds(retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()).toInt()
                val originalVideoDuration = userRecordDuration

                val endTime = max((totalDuration - originalVideoDuration), 0).toFloat()
                    //  trim out original video and buffer from combined video file
                    val oBufferFile = VideoOpItem(
                            operation = VideoOp.TRIMMED,
                            clips = arrayListOf(newVideoPath),
                            startTime = 0F,
                            endTime = max((totalDuration - originalVideoDuration), 0).toFloat(),
                            outputPath = originalBuffer,
                            comingFrom = currentOperation)

                    bufferDetails.add(BufferDataDetails(originalBuffer, originalVideo))
                    task.add(oBufferFile)

                //  creating the video file
                val oVideoFile = VideoOpItem(
                        operation = VideoOp.TRIMMED,
                        clips = arrayListOf(newVideoPath),
                        startTime = max((totalDuration - originalVideoDuration), 0).toFloat(),
                        endTime = totalDuration.toFloat(),
                        outputPath = originalVideo,
                        comingFrom = currentOperation)

                task.add(oVideoFile)
                //  create buffer and video for each swipe
                swipedRecording?.timestamps?.forEachIndexed { index, timeStamp ->

                    val buffFileName = "$parentPath/buff-${File(originalVideo).nameWithoutExtension}-$index.mp4"
                    val outputFileName = "$parentPath/${File(originalVideo).nameWithoutExtension}-$index.mp4"

                    AppClass.showInGallery.add(File(outputFileName).nameWithoutExtension)
                    Log.d(TAG,
                            "processPendingSwipes: \n Output = $outputFileName, \n start = ${(timeStamp - (swipeValue / 1000)).toInt()} \n end = $timeStamp")

                    //  if the merged video is passed in, then trim from the merged video to create the parts that were swiped
                    if(newVideoPath.isNotEmpty() && newVideoPath != swipedRecording!!.originalFilePath){
                        retriever.setDataSource(swipedRecording!!.originalFilePath)
                        val originalDuration = TimeUnit.MILLISECONDS.toSeconds(retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION).toLong())
                        retriever.setDataSource(newVideoPath)
                        val mergedDuration = TimeUnit.MILLISECONDS.toSeconds(retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION).toLong())

                        val videoTs = (mergedDuration - originalDuration) + timeStamp
                        //  creating the buffer file
                        val bufferFile = VideoOpItem(
                                operation = VideoOp.TRIMMED,
                                clips = arrayListOf(newVideoPath),
                                startTime = max((videoTs - (swipeValue / 1000) - (clipDuration / 1000)).toInt(),
                                        0).toFloat(),
                                endTime = max((videoTs - (swipeValue / 1000)).toInt(), 0).toFloat(),
                                outputPath = buffFileName,
                                comingFrom = currentOperation)

                            bufferDetails.add(BufferDataDetails(buffFileName, outputFileName))
                            task.add(bufferFile)

                        //  creating the video file
                        val videoFile = VideoOpItem(
                                operation = VideoOp.TRIMMED,
                                clips = arrayListOf(newVideoPath),
                                startTime = max((videoTs - (swipeValue / 1000)).toInt(), 0).toFloat(),
                                endTime = videoTs.toFloat(),
                                outputPath = outputFileName,
                                comingFrom = currentOperation)

                        task.add(videoFile)
                    }else {
                        //  creating the buffer file
                        val bufferFile = VideoOpItem(
                                operation = VideoOp.TRIMMED,
                                clips = arrayListOf(swipedRecording?.originalFilePath!!),
                                startTime = max((timeStamp - (swipeValue / 1000) - (clipDuration / 1000)).toInt(),
                                        0).toFloat(),
                                endTime = max((timeStamp - (swipeValue / 1000)).toInt(), 0).toFloat(),
                                outputPath = buffFileName,
                                comingFrom = currentOperation)

                            bufferDetails.add(BufferDataDetails(buffFileName, outputFileName))
                            task.add(bufferFile)

                        //  creating the video file
                        val videoFile = VideoOpItem(
                                operation = VideoOp.TRIMMED,
                                clips = arrayListOf(swipedRecording?.originalFilePath!!),
                                startTime = max((timeStamp - (swipeValue / 1000)).toInt(), 0).toFloat(),
                                endTime = timeStamp.toFloat(),
                                outputPath = outputFileName,
                                comingFrom = currentOperation)

                        task.add(videoFile)
                    }
                }

                retriever.release()
                intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
                VideoService.enqueueWork(requireContext(), intentService)
            }
        } else {
            Log.d(TAG, "videoConcatCompleted: no pending swipes make buffer and video")
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(newVideoPath)
            val totalDuration = TimeUnit.MILLISECONDS.toSeconds(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION).toLong())

            val videoDuration = userRecordDuration
            val intentService = Intent(requireContext(), VideoService::class.java)
            val bufferFilePath = "${File(newVideoPath).parent}/buff-${File(newVideoPath).nameWithoutExtension}-1.mp4"    //  this is the file that is the buffer
            val videoFilePath = "${File(newVideoPath).parent}/${File(newVideoPath).nameWithoutExtension}-1.mp4"    //  this is the file that the user will see
            val taskList = arrayListOf<VideoOpItem>()

            val bufferFile = VideoOpItem(
                    operation = VideoOp.TRIMMED,
                    clips = arrayListOf(newVideoPath),
                    startTime = max((totalDuration - videoDuration - (clipDuration / 1000)).toInt(),
                            0).toFloat(),
                    endTime = (totalDuration - videoDuration).toFloat(),
                    outputPath = bufferFilePath,
                    comingFrom = CurrentOperation.VIDEO_RECORDING)

            bufferDetails.add(BufferDataDetails(bufferFilePath, videoFilePath))

            val videoFile = VideoOpItem(
                    operation = VideoOp.TRIMMED,
                    clips = arrayListOf(newVideoPath),
                    startTime = (totalDuration - videoDuration).toFloat(),
                    endTime = totalDuration.toFloat(),
                    outputPath = videoFilePath,
                    comingFrom = CurrentOperation.VIDEO_RECORDING)

            taskList.add(bufferFile)
            taskList.add(videoFile)

            intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, taskList)
            VideoService.enqueueWork(requireContext(), intentService)

            AppClass.showInGallery.add(File(videoFilePath).nameWithoutExtension)
        }
    }

    /**
     * handles the left swipe action for both clips and video recording
     */
    private fun handleLeftSwipe() {
        //  button move animation
        triggerLeftActionAnimation()
        if (cameraControl!!.isRecordingClips() && currentOperation == CurrentOperation.CLIP_RECORDING) {    //  if clips are being recorded
            gallery.disable()
            if (cameraControl!!.clipQueueSize() > 1) { // there is more than 1 items in the queue
                if (concatOnSwipeDuringClipRecording(SwipeAction.SWIPE_LEFT))
                    return
            } else {    //  there is only  item in the queue
                //  1.  check duration of clip and if swipe duration < video duration
                //      1.  restart recording session
                //      2.  remove item from queue and split the video
                //  2.  else save what we have. restart the recording inform user.
                if (cameraControl!!.clipQueueSize() > 0) {
                    CoroutineScope(Default).launch {
                        ensureRecordingRestart()
                        trimOnSwipeDuringClipRecording(SwipeAction.SWIPE_LEFT)
                    }
                }
            }
            gallery.enable()
        } else {    // swiped during video recording
            swipedFileNames.add(File(cameraControl?.getCurrentOutputPath()!!).nameWithoutExtension)    // video file currently being recorded
            if (swipedRecording == null) {
                swipedRecording = cameraControl?.getCurrentOutputPath()?.let { SwipedRecording(it) }
            }
            if (swipedRecording!!.originalFilePath?.equals(cameraControl?.getCurrentOutputPath())!!) {
                swipedRecording!!.timestamps.add(timerSecond)
            }

            blinkAnimation()
        }

//        Toast.makeText(requireActivity(), "Capturing Previous", Toast.LENGTH_SHORT).show()

        swipeProcessed = false
    }

    /**
     * handles right swipes that take a clip of length suggested by swipeValue and uses that to take pictures
     */
    private fun handleRightSwipe(){
        //  button move animation
        triggerRightSwipeAnimation()

        if(currentOperation == CurrentOperation.CLIP_RECORDING) {
            gallery.disable()
//            videoProcessing(true)
            takePhoto.isEnabled = false
            takePhoto.alpha = 0.5F
            r2Shutter.isEnabled = false
            r2Shutter.alpha = 0.5F

            if (cameraControl!!.clipQueueSize() > 1) {    //  more han 1 item is available in the queue
                VideoService.ignoreResultOf.add(VideoOp.TRIMMED)
                concatOnSwipeDuringClipRecording(SwipeAction.SWIPE_RIGHT)
            } else { //  only 1 items is available
                if (cameraControl!!.clipQueueSize() > 0) {
                    CoroutineScope(Default).launch {
                        VideoService.ignoreResultOf.add(VideoOp.TRIMMED)
                        ensureRecordingRestart()
                        trimOnSwipeDuringClipRecording(SwipeAction.SWIPE_RIGHT)
                    }
                }
            }
            gallery.enable()
        }
    }

    /**
     * show the animations when left swipe is performed
     */
    private fun triggerLeftActionAnimation() {
        if (cameraControl!!.isRecordingClips()) {
            val t1Animation = ObjectAnimator.ofFloat(recordButton, "translationX", 0f, -80f, 0f)
            t1Animation.duration = 1000
            t1Animation.addListener(swipeAnimationListener)
            t1Animation.start()
        }
    }

    /**
     * show the animations when right swipe is performed
     */
    private fun triggerRightSwipeAnimation() {
        if (cameraControl!!.isRecordingClips()) {
            val t1Animation = ObjectAnimator.ofFloat(recordButton, "translationX", 0f, 80f, 0f)
            t1Animation.duration = 1000
            t1Animation.addListener(swipeAnimationListener)
            t1Animation.start()
        }
    }

    /**
     * Triggered when the swipe right action has completed with the new video path
     */
    private fun launchSnapbackVideoCapture(videoFilePath: String) =
        (requireActivity() as AppMainActivity).loadFragment(SnapbackFragment.newInstance(
                videoFilePath), true)

    /**
     * called during swipe operation for concatenating videos while clip recording
     * 1.  remove the 1st item - clip1
     * 2.  restart recording session so that the recording doesn't stop and we get the file we need to merge
     * 3.  remove the next item - clip 2@return Boolean
     * 4.  merge and split
     *
     * @return Boolean  to determine if we should exit the calling function.
     * */
    private fun concatOnSwipeDuringClipRecording(swipeAction: SwipeAction): Boolean {
        Log.d(TAG, "concatOnSwipeDuringClipRecording: started")
        CoroutineScope(Default).launch {
            val clips = arrayListOf<String>()
            if (cameraControl!!.clipQueueSize() >= 2) {
                val queueSize = cameraControl!!.clipQueueSize()
                for (i in 0 until queueSize) {
                    clips.add(cameraControl!!.removeClipQueueItem()!!.absolutePath)
                    if (i == queueSize - 2 && swipeAction == SwipeAction.SWIPE_LEFT) {
                        ensureRecordingRestart()
                    }
                }
            }

            val timeStamp =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val mergeFilePath = "${File(clips[0]).parent!!}/merged-$timeStamp.mp4"
            swipedFileNames.add("${File(cameraControl?.getCurrentOutputPath()!!).parent}/merged-$timeStamp-1")  //  indication of swiped file,"-1" since we want the second half of the split
            AppClass.showInGallery.add("merged-$timeStamp-1")  //  indication of swiped file,"-1" since we want the second half of the split

            val intentService = Intent(requireContext(), VideoService::class.java)
            val task = arrayListOf(
                    VideoOpItem(
                            operation = VideoOp.CONCAT,
                            clips = clips,
                            outputPath = mergeFilePath,
                            comingFrom = CurrentOperation.CLIP_RECORDING,
                            swipeAction = swipeAction
                    )
            )
            intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
            VideoService.enqueueWork(requireContext(), intentService)
        }
        if(swipeAction == SwipeAction.SWIPE_RIGHT) {
            CoroutineScope(Main).launch{
//                videoProcessing(false)
                takePhoto.isEnabled = true
                takePhoto.alpha = 1F
                r2Shutter.isEnabled = true
                r2Shutter.alpha = 1F
                launchSnapbackVideoCapture("")
            }
        }
        return false
    }

    /**
     * Trims the clip in the queue to @link{swipeValue},
     *
     * clip recording to be restarted before calling this; since this is designed to be called when the queue only contains the
     * currently recording clip.
     **/
    private fun trimOnSwipeDuringClipRecording(swipeAction: SwipeAction) {
        Log.d(TAG, "trimOnSwipeDuringClipRecording: started")
        val clip = cameraControl!!.removeClipQueueItem()!!
        val actualClipTime = try {
            Log.d(TAG, "AVA trimOnSwipeDuringClipRecording: checking duration for file = ${clip.absolutePath}")
            (requireActivity() as AppMainActivity).getMetadataDurations(arrayListOf(clip.absolutePath))[0]
        } catch (e: NullPointerException) {
            e.printStackTrace()
            -1
        }
        if (actualClipTime == -1){
            return
        }

        val swipeClipDuration = swipeValue / 1000
        if (actualClipTime > swipeClipDuration) {
            //  splitting may not work for this so we opt for trim
            Log.d(TAG,
                    "actualClipTime: $actualClipTime\nswipeValue: $swipeValue\nswipeClipDuration: $swipeClipDuration")
            swipedFileNames.add("trimmed-${clip.nameWithoutExtension}")
            AppClass.showInGallery.add("trimmed-${clip.nameWithoutExtension}")

            val bufferFile = "${clip.parent}/buff-${clip.name}"
            val videoFile = "${clip.parent}/trimmed-${clip.name}"

            val intentService = Intent(requireContext(), VideoService::class.java)
            val taskList = arrayListOf<VideoOpItem>()

            /*
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180)

            INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0)
            */

            val orientationPref = when(previousOrientation){
                SimpleOrientationListener.VideoModeOrientation.LANDSCAPE -> 90 - 90
                SimpleOrientationListener.VideoModeOrientation.REV_LANDSCAPE -> 270 - 90
                else -> 0 - 90
            }

            if (swipeAction == SwipeAction.SWIPE_LEFT) {   //  since we don't need the buffer for right swipe
                val bufferTask = VideoOpItem(
                        operation = VideoOp.TRIMMED,
                        clips = arrayListOf(clip.absolutePath),
                        startTime = 0F,
                        endTime = (actualClipTime - swipeClipDuration).toFloat(),
                        outputPath = bufferFile,
                        comingFrom = CurrentOperation.CLIP_RECORDING,
                        swipeAction = swipeAction,
                        orientationPreference = orientationPref)

                bufferDetails.add(BufferDataDetails(bufferFile, videoFile))
                taskList.add(bufferTask)
            }
            val videoTask = VideoOpItem(
                    operation = VideoOp.TRIMMED,
                    clips = arrayListOf(clip.absolutePath),
                    startTime = max((actualClipTime - swipeClipDuration).toInt(), 0).toFloat(),
                    endTime = actualClipTime.toFloat(),
                    outputPath = videoFile,
                    comingFrom = CurrentOperation.CLIP_RECORDING,
                    swipeAction = swipeAction,
                    orientationPreference = orientationPref)

            taskList.add(videoTask)

            intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, taskList)
            VideoService.enqueueWork(requireContext(), intentService)

            if(swipeAction == SwipeAction.SWIPE_RIGHT) {
                CoroutineScope(Main).launch{
//                    videoProcessing(false)
                    takePhoto.isEnabled = true
                    takePhoto.alpha = 1F
                    r2Shutter.isEnabled = true
                    r2Shutter.alpha = 1F
                    launchSnapbackVideoCapture("")
                }
            }
        } else { //  save what we have
            swipedFileNames.add(clip.nameWithoutExtension)
            AppClass.showInGallery.add(clip.nameWithoutExtension)
            if(swipeAction == SwipeAction.SWIPE_LEFT) {  //  we only need to save the snip in DB for left swipe
                (requireActivity() as AppMainActivity).addSnip(clip.absolutePath,
                        actualClipTime,
                        actualClipTime)

                //  saving the clip itself as buffer since no buffer exists
                bufferDetails.add(BufferDataDetails(clip.absolutePath, clip.absolutePath))
            }
            if(swipeAction == SwipeAction.SWIPE_RIGHT) {
                CoroutineScope(Main).launch{
//                    videoProcessing(false)
                    takePhoto.isEnabled = true
                    takePhoto.alpha = 1F
                    r2Shutter.isEnabled = true
                    r2Shutter.alpha = 1F
                    launchSnapbackVideoCapture(clip.absolutePath)
                }
            }
        }
    }

    private suspend fun ensureRecordingRestart() {
        val done = CoroutineScope(Default).async {
            with(cameraControl!!) {
                try {
                    restartRecording()
                } catch (e: IllegalStateException) {
                    //  attempt to reopen the camera
                    Log.e(TAG, "Forcing camera restart")
                    closeCamera()
                    if (mTextureView.isAvailable && !AppMainActivity.isPausing) {   //  if the app is going to be paused don't restart
                        if(!isBgThreadRunning()){
                            cameraControl?.startBackgroundThread()
                        }
                        openCamera(mTextureView.width, mTextureView.height)
                        startRecordingVideo()
                        currentOperation = CurrentOperation.CLIP_RECORDING
                    }
                }
            }

            return@async true
        }
        done.await()
    }

    /**
     * Check if recorded files can be concatenated, else proceed to process left swipes
     */
    private fun attemptClipConcat() {
        Log.d(TAG, "attemptClipConcat: started")
        if (cameraControl!!.clipQueueSize() >= 2) {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val clips = arrayListOf<String>()
            val queueSize = cameraControl!!.clipQueueSize()
            for(i in 0 until queueSize){
                clips.add(cameraControl!!.removeClipQueueItem()!!.absolutePath)
            }
            /*
            val clip1: File = cameraControl?.removeClipQueueItem()!!
            val clip2: File = cameraControl?.removeClipQueueItem()!!
            */
            val mergeFilePath = "${File(clips[0]).parent!!}/merged-$timeStamp.mp4"

            /*if (clip1.length() == 0L || clip2.length() == 0L)    // the file is not formed and concat will not work
                return*/

            /*showInGallery.add(clip2.nameWithoutExtension)*/   //  only the actual recording is shown, but wasn't this already save?

//            bufferDetails.add(BufferDataDetails(clip1.absolutePath, mergeFilePath))   //  todo: we cannot add to as buffer here before trimming

            val intentService = Intent(requireContext(), VideoService::class.java)
            val task = arrayListOf(VideoOpItem(
                    operation = VideoOp.CONCAT,
                    clips = clips,
                    outputPath = mergeFilePath,
                    comingFrom = CurrentOperation.VIDEO_RECORDING))
            intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
            VideoService.enqueueWork(requireContext(), intentService)
        } else {
            processPendingSwipes(currentOperation = CurrentOperation.VIDEO_RECORDING)
        }
    }

    private fun showDialogSettingsMain() {
        settingsDialog = SettingsDialog(requireContext(), this@VideoMode)
        settingsDialog?.show(requireActivity().supportFragmentManager, "Settings Frag")


        /*val dialog = Dialog(requireActivity())
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(true)
            dialog.setContentView(R.layout.fragment_settings)
            val con6 = dialog.findViewById<RelativeLayout>(R.id.quality_holder)
            con6.setOnClickListener { showDialogSettingsResolution() }
            val feedback = dialog.findViewById<RelativeLayout>(R.id.feedback_holder)
            feedback.setOnClickListener {
                (activity as AppMainActivity?)!!.loadFragment(Feedback_fragment.newInstance(), true)
                dialog.dismiss()
            }
            dialog.show()*/
    }

    private fun showDialogSettingsResolution() {
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.fragment_resolution)
        dialog.show()
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

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>,
            grantResults: IntArray,
    ) {
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

    fun hasPermissionsGranted(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(requireActivity(), permission)
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
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
                            cameraControl?.openCamera(mTextureView.width, mTextureView.height)
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

                override fun onPermissionRationaleShouldBeShown(
                        permissions: List<PermissionRequest>,
                        token: PermissionToken,
                ) {
                    token.continuePermissionRequest()
                }
            }).withErrorListener { Toast.makeText(requireActivity().applicationContext,
                        "Error occurred! ",
                        Toast.LENGTH_SHORT).show() }
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
                    requestPermissions(requireActivity(),
                            VIDEO_PERMISSIONS,
                            REQUEST_VIDEO_PERMISSIONS)
                }
                .setNegativeButton(android.R.string.cancel
                ) { _, _ -> requireActivity().finish() }
                .create()
        }
    }

    private fun saveSnipTimeToLocal() {
        swipedFileNames.add(File(cameraControl?.getCurrentOutputPath()!!).nameWithoutExtension)    // video file currently being recorded
        if (swipedRecording == null) {
            swipedRecording = cameraControl?.getCurrentOutputPath()
                ?.let { SwipedRecording(it) }
        }
        if (timerSecond != 0) { //  user recording is in progress
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
            Log.d(TAG,
                    "saving video thumbnail at path: " + mediaFile + ", video path: " + videoFile.absolutePath)
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
//            gallery.alpha = .5F
//            changeCamera.alpha = .5F
            gallery.isEnabled = false
            changeCamera.isEnabled = false
        } else {
            gallery.alpha = 1F
            changeCamera.alpha = 1F
            gallery.isEnabled = true
            changeCamera.isEnabled = true
        }
    }

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

    override fun confirmCamPermission() {
        requestPermission()
    }

    override fun startChronometerUI() {
        CoroutineScope(Main).launch {
            mChronometer.base = SystemClock.elapsedRealtime()
            mChronometer.start()
            mChronometer.visibility = View.VISIBLE
        }
    }

    override fun stopChronometerUI() {
        CoroutineScope(Main).launch {
            userRecordDuration = timerSecond
            mChronometer.stop()
            mChronometer.visibility = View.INVISIBLE
            mChronometer.text = "00:00:00"
        }
    }

    override fun settingsSaved() {
        clipDuration = (pref.getInt(SettingsDialog.BUFFER_DURATION, 1) * 60 * 1000).toLong()
        swipeValue = (pref.getInt(SettingsDialog.QB_DURATION, 5) * 1000).toLong()
        cameraControl!!.setClipDuration(clipDuration)
        Toast.makeText(requireContext(), "settings updated", Toast.LENGTH_SHORT).show()
        CoroutineScope(Default).launch { ensureRecordingRestart() }
    }
}