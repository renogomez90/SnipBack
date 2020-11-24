package com.hipoint.snipback.fragment

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.*
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
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.SwipedRecording
import com.hipoint.snipback.Utils.AutoFitTextureView
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.control.CameraControl
import com.hipoint.snipback.listener.IRecordUIListener
import com.hipoint.snipback.listener.IVideoOpListener.VideoOp
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
import kotlinx.android.synthetic.main.fragment_gallery.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class VideoMode : Fragment(), View.OnClickListener, OnTouchListener, ActivityCompat.OnRequestPermissionsResultCallback, IRecordUIListener {
    val swipeValue = 5 * 1000L  //  swipeBack duration

    private val VIDEO_DIRECTORY_NAME1 = "Snipback"
    private var userRecordDuration    = 0             //  duration of user recorded time

    private var parentSnip     : Snip?             = null
    private var swipedFileNames: ArrayList<String> = arrayListOf() //  names of files generated from swiping left
    private var swipedRecording: SwipedRecording?  = null

    //    private var actualClipTime = 0L
    private var clipDuration = 30 * 1000L

    var zoomFactor: TextView? = null

    //zoom slider controls
    var mProgress         = 0f
    var mMinZoom          = 0f
    var mMaxZoom          = 0f
    var currentProgress   = 1f
    val zoomStep          = 1f

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
                        if (cameraControl!!.isRecordingVideo()) {
                            saveSnipTimeToLocal()
                        }
                    }
                    //  Right to left swipe action
                    if (x2 < x1) {
                        if (cameraControl!!.isRecordingVideo()) {
                            handleLeftSwipe()
                        }
                    }
                }
            }
        }

        if (cameraControl!!.handleZoomEvent(event)) return false
        return true
    }

    interface OnTaskCompleted {
        fun onTaskCompleted(success: Boolean)
    }

    companion object {
        private var videoMode: VideoMode? = null
        private const val TAG = "Camera2VideoFragment"
        private const val FRAGMENT_DIALOG = "dialog"
        private const val REQUEST_VIDEO_PERMISSIONS = 1
        const val VIDEO_DIRECTORY_NAME = "SnipBackVirtual"
        private const val THUMBS_DIRECTORY_NAME = "Thumbs"

        //    private GestureFilter detector;
        //clips
//        private var recordPressed = false //  to know if the user has actively started recording
//        private var stopPressed = false //  to know if the user has actively ended recording, todo: this can be removed once a better handing is in place
        var recordClips = true //  to check if short clips should be recorded

        const val MIN_DISTANCE = 150
        val VIDEO_PERMISSIONS = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO)

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
        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture,
                                               width: Int, height: Int) {
            cameraControl!!.openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture,
                                                 width: Int, height: Int) {
            cameraControl!!.configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
    }

    private var timerSecond                 : Int              = 0
    private var appRepository               : AppRepository?   = null
    private var animBlink                   : Animation?       = null
    private var thumbnailProcessingCompleted: OnTaskCompleted? = null
    private var cameraControl               : CameraControl?   = null
    
    //Views
    private lateinit var rootView         : View
    private lateinit var gallery          : ImageButton
    private lateinit var settings         : ImageButton
    private lateinit var recordButton     : ImageButton
    private lateinit var recordStopButton : ImageButton
    private lateinit var r3Bookmark       : ImageButton
    private lateinit var capturePrevious  : ImageButton
    private lateinit var changeCamera     : ImageButton
    private lateinit var r2Shutter        : ImageButton
    private lateinit var tvTimer          : TextView
    private lateinit var mChronometer     : Chronometer
    private lateinit var blinkEffect      : View
    private lateinit var rlVideo          : ConstraintLayout
    private lateinit var recStartLayout   : ConstraintLayout
    private lateinit var bottomContainer  : ConstraintLayout
    private lateinit var zoomControlLayout: ConstraintLayout
    private lateinit var seekBar          : SeekBar
    private lateinit var zoomOut          : ImageButton
    private lateinit var zoomIn           : ImageButton

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        rootView = inflater.inflate(R.layout.fragment_videomode, container, false)

        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        animBlink = AnimationUtils.loadAnimation(context, R.anim.blink)

        bindViews()
        setupCameraControl()
        bindListeners()

        return rootView
    }

    override fun onDestroyView() {  //  so that clutter is removed
        while(cameraControl?.clipQueueSize()?:0 > 0){
            cameraControl?.removeClipQueueItem()!!.delete()
        }
        super.onDestroyView()
    }

    private fun setupCameraControl() {
        if(cameraControl == null)
            cameraControl = CameraControl(requireActivity())
        
        cameraControl!!.apply {
            setRecordUIListener(this@VideoMode)
            setPreviewTexture(mTextureView)
            setClipDuration(clipDuration)
            setRecordClips(true)
        }
    }

    /**
     * Binds views to references
     * */
    private fun bindViews() {
        rlVideo           = rootView.findViewById(R.id.rl_video)
        gallery           = rootView.findViewById(R.id.gallery_btn)
        settings          = rootView.findViewById(R.id.menu_btn)
        capturePrevious   = rootView.findViewById(R.id.back_video_btn)
        changeCamera      = rootView.findViewById(R.id.switch_cam_btn)
        recordButton      = rootView.findViewById(R.id.rec)
        mChronometer      = rootView.findViewById(R.id.chronometer)
        mTextureView      = rootView.findViewById(R.id.texture)
        blinkEffect       = rootView.findViewById(R.id.overlay)
        recStartLayout    = rootView.findViewById(R.id.rec_start_container)
        bottomContainer   = rootView.findViewById(R.id.bottom_cont)
        recordStopButton  = rootView.findViewById(R.id.rec_stop)
        zoomControlLayout = rootView.findViewById(R.id.zoom_control_layout)
        seekBar           = rootView.findViewById(R.id.zoom_controller)
        zoomOut           = rootView.findViewById(R.id.zoom_out_btn)
        zoomIn            = rootView.findViewById(R.id.zoom_in_btn)
        r3Bookmark        = rootView.findViewById(R.id.r_3_bookmark)
        r2Shutter         = rootView.findViewById(R.id.r_2_shutter)
        zoomFactor        = rootView.findViewById(R.id.zoom_factor)
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
        gallery.setOnClickListener { (requireActivity() as AppMainActivity).loadFragment(FragmentGalleryNew.newInstance(), true) }
        settings.setOnClickListener { showDialogSettingsMain() }
        changeCamera.setOnClickListener {
            cameraControl!!.closeToSwitchCamera()
            if (mTextureView.isAvailable) {
                cameraControl?.openCamera(mTextureView.width, mTextureView.height)
            } else {
                mTextureView.surfaceTextureListener = mSurfaceTextureListener
            }
        }

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
        mMinZoom = cameraControl!!.getMinZoom()
        mMaxZoom = cameraControl!!.getMaxZoom() - 1
        seekBar.max = (mMaxZoom - mMinZoom).roundToInt()
        seekBar.setOnSeekBarChangeListener(
                object : OnSeekBarChangeListener {
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        cameraControl?.setCurrentZoom((mMinZoom + 1 + mProgress * zoomStep).roundToInt().toFloat())
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        cameraControl?.setCurrentZoom((mMinZoom + 1 + progress.toFloat() * zoomStep).roundToInt().toFloat())
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
    }

    /**
     * Closes the camera and stops the background thread when fragment is not in the foreground
     * */
    override fun onPause() {
        cameraControl?.closeCamera()
        cameraControl?.stopBackgroundThread()
        cameraControl = null

        super.onPause()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.rec -> {   //  Sets up UI and starts user triggered recording
                bottomContainer.visibility = View.INVISIBLE
                recStartLayout.visibility = View.VISIBLE

                // Once the record button is pressed we no longer need to capture clips since the
                // the user has started actively recording.
                recordClips = false //  todo can these be removed?
                updateFlags(recordClips = recordClips, recordPressed = true, stopPressed = false)
                userRecordDuration = 0
//                startRecordingVideo()
                with(cameraControl!!) {
                    if (isRecordingVideo() && isRecordingClips()) {
                        try {
                            restartRecording()
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

                swipedRecording = null
                while (cameraControl!!.clipQueueSize() > 2) {
                    cameraControl?.removeClipQueueItem()?.delete()
                }
            }
            R.id.rec_stop -> {  // sets up UI and stops user triggered recording
                bottomContainer.visibility = View.VISIBLE
                recStartLayout.visibility = View.INVISIBLE
                (requireActivity() as AppMainActivity).showInGallery.add(File(cameraControl?.getCurrentOutputPath()!!).nameWithoutExtension)
                parentSnip = null   //  resetting the session parent Snip
                cameraControl?.stopRecordingVideo()    // don't close session here since we have to resume saving clips
                attemptClipConcat() //  merge what is in the buffer with the recording
                // we can restart recoding clips if it is required at this point
                recordClips = true
                updateFlags(recordClips = recordClips, recordPressed = false, stopPressed = true)
                cameraControl?.startRecordingVideo()
            }
            R.id.r_3_bookmark -> {
                saveSnipTimeToLocal()
            }
            R.id.zoom_out_btn -> {
                if (cameraControl!!.getZoomLevel() <= mMaxZoom + 1) {
                    if (mProgress > mMinZoom) {
                        mProgress--
                        cameraControl?.setCurrentZoom((mMinZoom + mProgress * zoomStep).roundToInt().toFloat())
                        seekBar.progress = cameraControl!!.getCurrentZoom().toInt()
                    } else {
                        mProgress = 0f
                    }
                }
            }
            R.id.zoom_in_btn -> {
                if (cameraControl!!.getCurrentZoom() <= mMaxZoom) {
                    if (mProgress < mMaxZoom) {
                        mProgress++
                        cameraControl?.setCurrentZoom((mMinZoom + mProgress * zoomStep).roundToInt().toFloat())
                        seekBar.progress = cameraControl!!.getCurrentZoom().toInt()
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
                getVideoThumbnailClick(File(cameraControl?.getCurrentOutputPath()!!))
                rlVideo.clearAnimation()
            }
            R.id.texture -> {
                saveSnipTimeToLocal()
            }
            R.id.back_video_btn -> {

            }
        }
    }

    private fun updateFlags(recordClips: Boolean, recordPressed: Boolean, stopPressed: Boolean) {
        cameraControl?.apply {
            setRecordClips(recordClips)
            setRecordPressed(recordPressed)
            setStopPressed(stopPressed)
        }
    }

    /**
     * Processes the swipe that were made during user recording
     * */
    fun processPendingSwipes() {

        (requireActivity() as AppMainActivity).swipeProcessed = true

        if (swipedRecording != null) {
            if (swipedRecording?.originalFilePath.equals(cameraControl?.getLastUserRecordedPath())) {
                val intentService = Intent(requireContext(), VideoService::class.java)
                val task = arrayListOf<VideoOpItem>()

                swipedRecording?.timestamps?.forEachIndexed { index, timeStamp ->

                    val outputFileName = "${File(swipedRecording?.originalFilePath!!).parent}/${File(swipedRecording?.originalFilePath!!).nameWithoutExtension}-$index.mp4"

                    (requireActivity() as AppMainActivity).showInGallery.add(File(outputFileName).nameWithoutExtension)
                    Log.d(TAG, "processPendingSwipes: \n Output = $outputFileName, \n start = ${(timeStamp - (swipeValue / 1000)).toInt()} \n end = $timeStamp")

                    task.add(VideoOpItem(
                            operation = VideoOp.TRIMMED,
                            clip1 = swipedRecording?.originalFilePath!!,
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

        if (cameraControl!!.isRecordingClips()) {
            val t1Animation = ObjectAnimator.ofFloat(recordButton, "translationX", 0f, -80f, 0f)
            t1Animation.duration = 1500
            t1Animation.start()
        }

        if (cameraControl!!.isRecordingClips()) {    //  if clips are being recorded

            if (cameraControl!!.clipQueueSize() > 1) { // there is more than 1 items in the queue
                if (concatOnSwipeDuringClipRecording())
                    return
            } else {    //  there is only  item in the queue
                //  1.  check duration of clip and if swipe duration < video duration
                //      1.  restart recording session
                //      2.  remove item from queue and split the video
                //  2.  else save what we have. restart the recording inform user.
                if (cameraControl!!.clipQueueSize() > 0) {
                    ensureRecordingRestart()
                    trimOnSwipeDuringClipRecording()
                }
            }
        } else {    // swiped during video recording
            swipedFileNames.add(File(cameraControl?.getCurrentOutputPath()!!).nameWithoutExtension)    // video file currently being recorded
            if (swipedRecording == null) {
                swipedRecording = cameraControl?.getCurrentOutputPath()?.let { SwipedRecording(it) }
            }
            if (swipedRecording!!.originalFilePath?.equals(cameraControl?.getCurrentOutputPath())!!) {
                swipedRecording!!.timestamps.add(timerSecond)
            }
        }

//        Toast.makeText(requireActivity(), "Capturing Previous", Toast.LENGTH_SHORT).show()

        blinkAnimation()

        (requireActivity() as AppMainActivity).swipeProcessed = false
    }

    /**
     * handles right swipes that take a clip of length suggested by swipeValue and uses that to take pictures
     */
    private fun handleRightSwipe(){
        if(cameraControl!!.clipQueueSize() > 1){    //  more han 1 item is available in the queue
            concatOnSwipeDuringClipRecording()
        }else { //  only 1 items is available
            if(cameraControl!!.clipQueueSize() == 1){
                trimOnSwipeDuringClipRecording()
            }
        }
    }

    /**
     * called during swipe operation for concatenating videos while clip recording
     * 1.  remove the 1st item - clip1
     * 2.  restart recording session so that the recording doesn't stop and we get the file we need to merge
     * 3.  remove the next item - clip 2@return Boolean
     * 4.  merge and split
     *
     * @return Boolean  to determine if we should exit the calling function.
     * */
    private fun concatOnSwipeDuringClipRecording(): Boolean {

        val clip1 = cameraControl?.removeClipQueueItem()!!
        ensureRecordingRestart()
        val clip2 = cameraControl?.removeClipQueueItem()!!

        if (clip1.length() == 0L || clip2.length() == 0L)
            return true

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val mergeFilePath = "${clip1.parent!!}/merged-$timeStamp.mp4"
        swipedFileNames.add("${File(cameraControl?.getCurrentOutputPath()!!).parent}/merged-$timeStamp-1")  //  indication of swiped file,"-1" since we want the second half of the split
        (requireActivity() as AppMainActivity).showInGallery.add("merged-$timeStamp-1")  //  indication of swiped file,"-1" since we want the second half of the split

        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
                operation = VideoOp.CONCAT,
                clip1 = clip1.absolutePath,
                clip2 = clip2.absolutePath,
                outputPath = mergeFilePath))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
        return false
    }

    /**
     * Trims the clip in the queue to @link{swipeValue},
     * clip recording to be restarted before calling this, since this is designed to be called when the queue only contains the
     * currently recording clip.
     **/
    private fun trimOnSwipeDuringClipRecording() {
        val clip = cameraControl?.removeClipQueueItem()!!
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

    private fun ensureRecordingRestart() {
        with(cameraControl!!) {
            try {
                restartRecording()
            } catch (e: IllegalStateException) {
                //  attempt to reopen the camera
                Log.e(TAG, "Forcing camera restart")
                closeCamera()
                if (mTextureView.isAvailable) {
                    openCamera(mTextureView.width, mTextureView.height)
                    startRecordingVideo()
                }
            }
        }
    }

    /**
     * Check if recorded files can be concatenated, else proceed to process left swipes
     */
    private fun attemptClipConcat() {
        if (cameraControl!!.clipQueueSize() >= 2) {
            val clip1: File = cameraControl?.removeClipQueueItem()!!
            val clip2: File = cameraControl?.removeClipQueueItem()!!

            if (clip1.length() == 0L || clip2.length() == 0L)    // the file is not formed and concat will not work
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
        if (timerSecond != 0) { //  user recording is in progress
            val endSecond = timerSecond
            AppClass.getAppInstance().setSnipDurations(endSecond)
            // on screen tap blinking starts
            blinkAnimation()
//        Log.d("seconds", String.valueOf(endSecond));
        }else{
            //todo: show the clip that we captured.
            blinkAnimation()
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

    fun getSwipedRecording(): SwipedRecording{
        if(swipedRecording == null)
            swipedRecording = SwipedRecording("")

        return swipedRecording!!
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
        mChronometer.base = SystemClock.elapsedRealtime()
        mChronometer.start()
        mChronometer.visibility = View.VISIBLE
    }

    override fun stopChronometerUI() {
        userRecordDuration = timerSecond
        mChronometer.stop()
        mChronometer.visibility = View.INVISIBLE
        mChronometer.text = ""
    }
}