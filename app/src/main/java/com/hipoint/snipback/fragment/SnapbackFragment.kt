package com.hipoint.snipback.fragment

import VideoHandle.EpEditor
import VideoHandle.OnEditorListener
import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.MimeTypeMap
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.Utils.SnipbackTimeBar
import com.hipoint.snipback.application.AppClass.showInGallery
import com.hipoint.snipback.dialog.KeepSnapbackVideoDialog
import com.hipoint.snipback.dialog.SnapbackProcessingDialog
import com.hipoint.snipback.listener.ISaveListener
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_main2.*
import kotlinx.android.synthetic.main.warningdialog_savevideo.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToLong


class SnapbackFragment: Fragment(), ISaveListener {
    private val TAG = SnapbackFragment::class.java.simpleName

    private val PROCESSING_SNAPBACK_DIALOG = "com.hipoint.snipback.SNAPBACK_VIDEO_PROCESSING"
    private val SAVE_SNAPBACK_DIALOG       = "com.hipoint.snipback.SNAPBACK_SAVE_VIDEO"
    private val EXTERNAL_DIR_NAME          = "Snipback"

    private val retries = 3
    private var tries   = 0

    private var progressDialog : SnapbackProcessingDialog? = null
    private var saveVideoDialog: KeepSnapbackVideoDialog?  = null
    private var subscriptions  : CompositeDisposable?      = null
    private var animBlink      : Animation?                = null

    private lateinit var playerView   : PlayerView
    private lateinit var rootView     : View
    private lateinit var blinkEffect  : View
    private lateinit var player       : SimpleExoPlayer
    private lateinit var backBtn      : RelativeLayout
    private lateinit var captureBtn   : RelativeLayout
    private lateinit var galleryBtn   : RelativeLayout
    private lateinit var playerHolder : ConstraintLayout
    private lateinit var seekBar      : SnipbackTimeBar
    private lateinit var swipeDetector: SwipeDistanceView

    private val videoPathReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let{
                videoPath = it.getStringExtra(EXTRA_VIDEO_PATH) ?: ""
                hideProgressDialog()
                if(videoPath.isNotNullOrEmpty())
                    setupPlayer(videoPath!!)
            }
        }
    }

    private val mediaStorageDir by lazy { requireContext().externalMediaDirs[0] }
    private val EXTERNAL_DIR_PATH: String by lazy{
        "$mediaStorageDir/$EXTERNAL_DIR_NAME"
    }

    companion object{
        val SNAPBACK_PATH_ACTION = "com.hipoint.snipback.SNAPBACK_VIDEO_PATH"
        val EXTRA_VIDEO_PATH = "videoPath"

        var fragment: SnapbackFragment? = null

        @JvmStatic
        var videoPath: String? = null

        fun newInstance(videoPath: String): SnapbackFragment {
            val bundle = Bundle()
            bundle.putString(EXTRA_VIDEO_PATH, videoPath)

            if(fragment == null){
                fragment = SnapbackFragment()
            }

            fragment!!.arguments = bundle
            return fragment!!
        }
    }

    /**
     * To dynamically change the seek parameters so that seek appears to be more responsive
     */
    private val gestureDetector by lazy {
        GestureDetector(requireContext(), object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent?): Boolean = false

            override fun onShowPress(e: MotionEvent?) = Unit

            override fun onSingleTapUp(e: MotionEvent?): Boolean = false

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent?,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (e1 != null && e2 != null) {
                    val speed = (distanceX / (e2.eventTime - e1.eventTime)).absoluteValue
                    if ((speed * 100) < 1.0F) {  // slow
                        if (player.seekParameters != SeekParameters.EXACT) {
                            player.setSeekParameters(SeekParameters.EXACT)
                        }
                    } else { //  fast
                        if (player.seekParameters != SeekParameters.CLOSEST_SYNC) {
                            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                        }
                    }

                }
                return false
            }

            override fun onLongPress(e: MotionEvent?) = Unit

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent?,
                velocityX: Float,
                velocityY: Float,
            ): Boolean =
                false
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_snapback, container, false)
        bindViews()
        bindListeners()

        return rootView
    }

    override fun onResume() {
        super.onResume()
        if(videoPath.isNullOrEmpty())
            videoPath = arguments?.getString(EXTRA_VIDEO_PATH) ?: ""

        if(videoPath.isNotNullOrEmpty())
            setupPlayer(videoPath!!)
        else
            showProgressDialog()
        requireActivity().registerReceiver(videoPathReceiver, IntentFilter(SNAPBACK_PATH_ACTION))
    }

    override fun onPause() {
        if(this::player.isInitialized)
            player.release()

        requireActivity().unregisterReceiver(videoPathReceiver)
        super.onPause()
    }

    /**
     * sets up the video player like it is new
     */
    private fun setupPlayer(videoPath: String) {
        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

        player.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoPath)))
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            playWhenReady = false
        }

        playerView.controllerShowTimeoutMs = 2000
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

        player.addListener(object : Player.EventListener {
            override fun onPlayerError(error: ExoPlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.message}")
                error.printStackTrace()
                tries++
                if (videoPath.isNotBlank() && tries < retries) {  //  retry in case of errors
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(500)
                        val frag = requireActivity().supportFragmentManager
                            .findFragmentByTag(AppMainActivity.PLAY_SNAPBACK_TAG)
                        requireActivity().supportFragmentManager.beginTransaction()
                            .detach(frag!!)
                            .attach(frag)
                            .commit()
                    }
                }
            }
        })

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onSeekStarted(eventTime: AnalyticsListener.EventTime) {
//                start tracking
            }

            override fun onSeekProcessed(eventTime: AnalyticsListener.EventTime) {
//                stop tracking
            }
        })
        initSwipeControls()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindListeners() {
        captureBtn.setOnClickListener {
            if(hasStoragePermission()) {
                performCapture()
            }else {
                requestStoragePermission()
            }
        }

        backBtn.setOnClickListener {
            showSaveDialog()
        }

        galleryBtn.setOnClickListener {
            //  launch gallery
//            val intent = Intent()
//            intent.action = Intent.ACTION_VIEW
//            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
//            intent.type = "image/*"
//            intent.data = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//            startActivity(Intent.createChooser(intent, "Open folder"))

            val photoLaunchIntent = Intent(Intent.ACTION_VIEW)
            val mediaDirPath = requireContext().externalMediaDirs[0].absolutePath + "/Snipback/"
            val fileUri = FileProvider.getUriForFile(requireContext().applicationContext,
                requireContext().packageName + ".fileprovider",
                File(mediaDirPath))
            photoLaunchIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

            photoLaunchIntent.setDataAndType(fileUri,
                DocumentsContract.Document.MIME_TYPE_DIR) //   this is correct way to do this BUT Samsung and Huawei doesn't support it


            if (photoLaunchIntent.resolveActivityInfo(requireContext().packageManager, 0) == null) {
                photoLaunchIntent.setDataAndType(fileUri,
                    "resource/folder") //  this will work with some file managers
                if (photoLaunchIntent.resolveActivityInfo(requireContext().packageManager,
                        0) == null
                ) {
                    photoLaunchIntent.setDataAndType(fileUri, "*/*") //  just open with anything
                }
            }
            startActivity(Intent.createChooser(photoLaunchIntent, "Choose"))
        }

        seekBar.setOnTouchListener { v, event ->
            if(event.action == MotionEvent.ACTION_MOVE){
                val totalX = seekBar.width
                val seekPercent = (event.x*100)/totalX
                val newSeekPosition = player.duration * seekPercent / 100
                player.seekTo(newSeekPosition.toLong())
            }
            return@setOnTouchListener false
        }
    }

    /**
     * captures and saves the current frame
     */
    private fun performCapture() {
        val storageFile = File(EXTERNAL_DIR_PATH)
        if (!storageFile.exists()) {
            storageFile.mkdir()
        }

        CoroutineScope(Default).launch { saveFrame() }

        blinkAnimation()
    }

    private fun requestStoragePermission() {
        Dexter.withContext(requireContext()).withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    performCapture()
                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Toast.makeText(requireContext(),
                        "cannot capture without storage permission",
                        Toast.LENGTH_SHORT).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?,
                ) {
                }
            }).withErrorListener { Toast.makeText(requireActivity().applicationContext,
                "Error occurred! ",
                Toast.LENGTH_SHORT).show() }
            .onSameThread()
            .check()
    }

    private fun hasStoragePermission(): Boolean =
        ActivityCompat.checkSelfPermission(requireActivity(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED


    private fun bindViews() {
        playerHolder  = rootView.findViewById(R.id.player_holder)
        blinkEffect   = rootView.findViewById(R.id.overlay)
        playerView    = rootView.findViewById(R.id.snapback_player_view)
        backBtn       = rootView.findViewById(R.id.back_arrow)
        captureBtn    = rootView.findViewById(R.id.button_capture)
        galleryBtn    = rootView.findViewById(R.id.button_gallery)
        seekBar       = rootView.findViewById(R.id.exo_progress)
        swipeDetector = rootView.findViewById(R.id.swipe_detector)

        animBlink = AnimationUtils.loadAnimation(requireContext(), R.anim.blink)
    }

    private fun initSwipeControls() {
        var startScrollingSeekPosition = 0L

        swipeDetector.setOnClickListener {
            if (playerView.isControllerVisible)
                playerView.hideController()
            else
                playerView.showController()
        }

        swipeDetector.onIsScrollingChanged {
            if (it)
                startScrollingSeekPosition = player.currentPosition
        }

        val emitter = SeekPositionEmitter()
        subscriptions = CompositeDisposable()

        player.seekWhenReady(emitter)
            .subscribe({
                Log.v(TAG, "seekTo=${it.first} isSeeking=${it.second}")
            }, { Log.e(TAG, "${it.message}") })
            .addTo(subscriptions!!)

        swipeDetector.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }

        swipeDetector.onScroll { percentX, _ ->
            val duration = player.duration

            val maxPercent = 0.75f
            val scaledPercent = percentX * maxPercent
            val percentOfDuration = scaledPercent * -1 * duration + startScrollingSeekPosition
            val newSeekPosition = percentOfDuration.roundToLong()

            player.seekTo(newSeekPosition)
//            emitter.seekFast(newSeekPosition)
        }
    }

    private fun showProgressDialog(){
        if (progressDialog == null){
            progressDialog = SnapbackProcessingDialog()
        }

        progressDialog!!.isCancelable = true
        progressDialog!!.show(requireActivity().supportFragmentManager, PROCESSING_SNAPBACK_DIALOG)
    }

    fun hideProgressDialog() = progressDialog?.dismiss()

    fun showSaveDialog(){
        if (saveVideoDialog == null) {
            saveVideoDialog = KeepSnapbackVideoDialog(this@SnapbackFragment)
        }

        if(!saveVideoDialog!!.isAdded) {
            saveVideoDialog!!.isCancelable = true
            saveVideoDialog!!.show(requireActivity().supportFragmentManager, SAVE_SNAPBACK_DIALOG)
        }
    }

    /**
     * Flashes the UI to indicate that an action has occurred
     * */
    private fun blinkAnimation() {
        playerHolder.startAnimation(animBlink)
        blinkEffect.visibility = View.VISIBLE
        blinkEffect.animate()
            .alpha(02f)
            .setDuration(100)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    blinkEffect.visibility = View.GONE
                    blinkEffect.clearAnimation()
                    playerHolder.clearAnimation()
                }
            })
    }

    /**
     * captures the frame and saves it to the output folder.
     */
    private fun saveFrame(){
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())
        val cmd = "-ss ${player.currentPosition.toFloat()/1000} -i $videoPath -vframes 1 -f image2 ${EXTERNAL_DIR_PATH}/IMAGE_${timeStamp}.jpg"
        EpEditor.execCmd(cmd, 1, object : OnEditorListener {
            override fun onSuccess() {
                Log.d(TAG, "onSuccess: image saved")
                MediaScannerConnection.scanFile(
                    requireContext(),
                    arrayOf("${mediaStorageDir!!.path}/${timeStamp}.jpg"),
                    arrayOf(MimeTypeMap.getSingleton().getMimeTypeFromExtension("jpg")),
                    null
                )

                val values = ContentValues()

                values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis())
                values.put(Images.Media.MIME_TYPE, "image/jpeg")
                values.put(MediaStore.MediaColumns.DATA,
                    "${EXTERNAL_DIR_PATH}/IMAGE_${timeStamp}.jpg")

                context!!.contentResolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values)
            }

            override fun onFailure() {
                Log.e(TAG, "onFailure: save failed")
            }

            override fun onProgress(progress: Float) {}
        })
    }

    //  saves the video clip to DB and internal storage
    override fun saveAs() {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(videoPath)
        val duration = TimeUnit.MILLISECONDS.toSeconds(retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()).toInt()

        if(activity is AppMainActivity){
            showInGallery.add(File(videoPath!!).nameWithoutExtension)
            (activity as AppMainActivity).addSnip(videoPath!!, duration, duration)
            activity?.supportFragmentManager?.popBackStack()
        }
    }

    override fun save() {}

    override fun exit() {
        File(videoPath).delete()    //  since we can discard this file
        requireActivity().supportFragmentManager.popBackStack()
    }

    override fun cancel() {}

    override fun onDestroy() {
        videoPath = ""
        super.onDestroy()
    }

}