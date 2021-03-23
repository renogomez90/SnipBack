package com.hipoint.snipback.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.*
import android.graphics.drawable.VectorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ClippingMediaSource
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.RangeSeekbarCustom
import com.hipoint.snipback.Utils.SnipbackTimeBar
import com.hipoint.snipback.Utils.milliToFloatSecond
import com.hipoint.snipback.adapter.TimelinePreviewAdapter
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.dialog.KeepVideoDialog
import com.hipoint.snipback.dialog.ProcessingDialog
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.enums.EditSeekControl
import com.hipoint.snipback.fragment.VideoEditingFragment.Companion.DISMISS_ACTION
import com.hipoint.snipback.fragment.VideoEditingFragment.Companion.EXTEND_TRIM_ACTION
import com.hipoint.snipback.fragment.VideoEditingFragment.Companion.PREVIEW_ACTION
import com.hipoint.snipback.listener.ISaveListener
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.service.VideoService
import com.hipoint.snipback.videoControl.SpeedDetails
import com.hipoint.snipback.videoControl.VideoOpItem
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToLong


class FragmentSlowMo : Fragment(), ISaveListener {
    private val TAG                  = FragmentSlowMo::class.java.simpleName
    private val SAVE_SNAPBACK_DIALOG = "com.hipoint.snipback.dialog_save"
    private val PROCESSING_DIALOG    = "com.hipoint.snipback.dialog_processing"

    private lateinit var rootView          : View
    private lateinit var playerView        : PlayerView
    private lateinit var currentSpeed      : TextView
    private lateinit var start             : TextView
    private lateinit var end               : TextView
    private lateinit var editBackBtn       : ImageView
    private lateinit var acceptBtn         : ImageView
    private lateinit var rejectBtn         : ImageView
    private lateinit var playBtn           : ImageView
    private lateinit var pauseBtn          : ImageView
    private lateinit var toStartBtn        : ImageButton
    private lateinit var acceptRejectHolder: LinearLayout
    private lateinit var previewBarProgress: ProgressBar
    private lateinit var previewTileList   : RecyclerView
    private lateinit var swipeDetector     : SwipeDistanceView
    private lateinit var timebarHolder     : FrameLayout
    private lateinit var seekbar           : SnipbackTimeBar

    private var player: SimpleExoPlayer? = null

    private var gotFrames = false
    //  retires on failure
    private val retries = 5
    //  save dialog
    private var saveVideoDialog: KeepVideoDialog? = null
    private var videoSaved     : Boolean          = false
    //  progress tracker
    private var progressTracker: ProgressTracker? = null
    //  range marker
    private var trimSegment: RangeSeekbarCustom? = null
    //  slow down factor
    private var multiplier: Int = 3
    //  preview thumbnail
    private var previewThumbs: File?   = null
    //  dialogs
    private var processingDialog: ProcessingDialog? = null
    //  adapters
    private var timelinePreviewAdapter: TimelinePreviewAdapter? = null
    // fast seeking
    private var subscriptions: CompositeDisposable? = null
    //  seek actions
    private var seekAction        = EditSeekControl.MOVE_START
    private var onGoingSeekAction = EditSeekControl.MOVE_START

    private var bufferDuration: Long = -1L
    private var videoDuration : Long = -1L
    private var maxDuration   : Long = -1L

    private var startWindow    = -1
    private var endWindow      = -1
    private var editedStart    = -1L
    private var editedEnd      = -1L

    private var timeStamp: String? = null

    //  App repository
    private val appRepository by lazy { AppRepository(AppClass.getAppInstance()) }

    private val previewTileReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                previewThumbs = File(intent.getStringExtra("preview_path")!!)
                showThumbnailsIfAvailable(previewThumbs!!)
                gotFrames = true
            }
        }
    }

    private val progressDismissReceiver: BroadcastReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val msg  = intent.getStringExtra("log")
                msg?.let { Log.d(TAG, "onReceive: $msg") }
                if(!videoSaved) {
                    bufferPath = intent.getStringExtra(EXTRA_BUFFER_PATH)
                    videoPath = intent.getStringExtra(EXTRA_RECEIVER_VIDEO_PATH)

                    hideProgress()
                    setupPlayer()
                } else {
                    videoPath = intent.getStringExtra(EXTRA_RECEIVER_VIDEO_PATH)
                    hideProgress()
                    if(videoPath != null) {
                        CoroutineScope(IO).launch {
                            val snip = appRepository.getSnipByVideoPath(videoPath!!)
                            (requireActivity() as AppMainActivity).loadFragment(
                                FragmentPlayVideo2.newInstance(snip),
                                true)
                        }
                    }
                }
            }
        }
    }

    /**
     * The first time this is triggered is after the CONCAT is completed.
     * the received inputFile is then enqueued for trimming the required video.
     *
     * Once this is completed the speed changes are triggered
     */
    private val extendTrimReceiver: BroadcastReceiver = object: BroadcastReceiver() {
        var concatOutput     = ""
        var fullExtension    = false

        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val retriever = MediaMetadataRetriever()
                val operation = it.getStringExtra("operation")
                val inputName = it.getStringExtra("fileName")
                val trimmedOutputPath = "${File(inputName!!).parent}/trimmed-$timeStamp.mp4"
                val speedChangedPath = "${File(inputName).parent}/VID_$timeStamp.mp4"

                retriever.setDataSource(inputName)

                val taskList = arrayListOf<VideoOpItem>()

                if (operation == IVideoOpListener.VideoOp.CONCAT.name) {  //  concat is completed, trim is triggered
                    val concatDuration =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            .toLong()
                    Log.d(TAG, "onReceive: CONCAT duration = $concatDuration")
                    concatOutput = inputName
                    fullExtension = (editedStart == 0L)

                    if (fullExtension) {
                        editedStart = 100L
                    }
                    val trimTask = VideoOpItem(
                        operation = IVideoOpListener.VideoOp.TRIMMED,
                        clips = arrayListOf(inputName),
                        startTime = editedStart.milliToFloatSecond(),
                        endTime = editedEnd.milliToFloatSecond(),
                        outputPath = trimmedOutputPath,
                        comingFrom = CurrentOperation.VIDEO_EDITING)

                    taskList.add(trimTask)
                } else if (operation == IVideoOpListener.VideoOp.TRIMMED.name) {  //  trim is completed
                    val trimmedDuration =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            .toLong()
                    Log.d(TAG, "onReceive: TRIMMED duration = $trimmedDuration")
                    val speedDetails = SpeedDetails(
                        isFast = false,
                        timeDuration = Pair(0, trimmedDuration),
                        multiplier = multiplier
                    )
                    val speedChangeTask = VideoOpItem(
                        operation = IVideoOpListener.VideoOp.SPEED,
                        clips = arrayListOf(inputName),
                        outputPath = speedChangedPath,
                        speedDetailsList = arrayListOf(speedDetails),
                        comingFrom = CurrentOperation.VIDEO_EDITING)

                    taskList.add(speedChangeTask)
                    AppClass.showInGallery.add(File(speedChangedPath).nameWithoutExtension)
                }

                val createNewVideoIntent = Intent(requireContext(), VideoService::class.java)
                createNewVideoIntent.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM,
                    taskList)
                VideoService.enqueueWork(requireContext(), createNewVideoIntent)
                retriever.release()
            }
        }
    }

    private fun showProgress(){
        if(processingDialog == null) {
            processingDialog = ProcessingDialog()
        }
        else if(processingDialog!!.isAdded||processingDialog!!.isVisible){
            return
        }
        processingDialog!!.isCancelable = false
        processingDialog!!.show(requireActivity().supportFragmentManager, PROCESSING_DIALOG)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun hideProgress(){
        processingDialog?.dismiss()
    }

    private fun showThumbnailsIfAvailable(previewThumbs: File) {
        if (previewThumbs.exists()) {
            val thumbList = previewThumbs.listFiles()
            val imageList = arrayListOf<Bitmap>()
            thumbList?.forEach {
                imageList.add(BitmapFactory.decodeFile(it.absolutePath))
            }
            timelinePreviewAdapter = TimelinePreviewAdapter(requireContext(), imageList)
            previewTileList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            //  change context for updating the UI
            timelinePreviewAdapter!!.setHasStableIds(true)
            previewTileList.adapter = timelinePreviewAdapter
            previewTileList.adapter?.notifyDataSetChanged()
            previewTileList.scrollToPosition(timelinePreviewAdapter!!.itemCount)
            previewBarProgress.visibility = View.GONE
        } else {
            getVideoPreviewFrames()
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

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
                if (e1 != null && e2 != null) {
                    val speed = (distanceX / (e2.eventTime - e1.eventTime)).absoluteValue
                    if ((speed * 100) < 1.0F) {  // slow
                        if (player!!.seekParameters != SeekParameters.EXACT) {
                            player!!.setSeekParameters(SeekParameters.EXACT)
                        }
                    } else { //  fast
                        if (player!!.seekParameters != SeekParameters.CLOSEST_SYNC) {
//                            player!!.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                            player!!.setSeekParameters(SeekParameters.EXACT)
                        }
                    }
                }
                return false
            }

            override fun onLongPress(e: MotionEvent?) = Unit

            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean =
                false
        })
    }

    /**
     * Populates preview frames in the seekBar area from the video
     */
    private fun getVideoPreviewFrames() {
        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
            operation = IVideoOpListener.VideoOp.FRAMES,
            clips = arrayListOf(videoPath!!),
            outputPath = File(videoPath!!).parent!!,
            comingFrom = CurrentOperation.VIDEO_EDITING))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
        Log.d(TAG, "getVideoPreviewFrames: started")
    }

    companion object {
        const val EXTRA_BUFFER_PATH        : String = "bufferPath"
        const val EXTRA_VIDEO_PATH         : String = "videoPath"
        const val EXTRA_RECEIVER_VIDEO_PATH: String = "processedVideoPath"
        const val EXTRA_INITIAL_MULTIPLIER : String = "multiplier"

        @Volatile
        private var fragment  : FragmentSlowMo? = null

        private var bufferPath: String?         = null
        private var videoPath : String?         = null
        private var tries     : Int             = 0

        @JvmStatic
        fun newInstance(buffer: String?, video: String?, multiplier: Int = 3): FragmentSlowMo {
            return (fragment ?: FragmentSlowMo().also {
                val bundle = Bundle()
                bundle.putString(EXTRA_BUFFER_PATH, buffer)
                bundle.putString(EXTRA_VIDEO_PATH, video)
                bundle.putInt(EXTRA_INITIAL_MULTIPLIER, multiplier)
                it.arguments = bundle

                fragment = it
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoSaved  = false
        gotFrames   = false
        bufferPath  = null
        videoPath   = null
        trimSegment = null

        maxDuration = 0L
        editedStart = -1L
        editedEnd   = -1L
        startWindow = 0
        endWindow   = -1
        tries       = 0
        savedInstanceState?.let { restoreState(it) }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("KEY_START", editedStart)
        outState.putLong("KEY_END", editedEnd)
        outState.putInt("KEY_START_WINDOW", startWindow)
        outState.putInt("KEY_END_WINDOW", endWindow)
        outState.putString("KEY_VIDEO_PATH", videoPath)
        outState.putString("KEY_BUFFER_PATH", bufferPath)

    }

    private fun restoreState(savedState: Bundle){
        editedStart = savedState.getLong("KEY_START")
        editedEnd   = savedState.getLong("KEY_END")
        startWindow = savedState.getInt("KEY_START_WINDOW")
        endWindow   = savedState.getInt("KEY_END_WINDOW")
        videoPath   = savedState.getString("KEY_VIDEO_PATH")
        bufferPath   = savedState.getString("KEY_BUFFER_PATH")

    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
    ): View? {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        rootView = inflater.inflate(R.layout.fragment_slo_mo, container, false)

        bindViews()
        bindListeners()
        return rootView
    }

    /**
     * Called when the fragment is no longer in use.  This is called
     * after [.onStop] and before [.onDetach].
     */
    override fun onDestroy() {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        editedStart = -1L
        editedEnd   = -1L
        startWindow = 0
        endWindow =  -1
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playerView.onPause()
        if (player != null) {
            player!!.apply {
                clearVideoSurface()
                clearMediaItems()
                release()
            }
            player = null
            subscriptions?.dispose()
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(extendTrimReceiver, IntentFilter(EXTEND_TRIM_ACTION))
        requireActivity().registerReceiver(previewTileReceiver, IntentFilter(PREVIEW_ACTION))
        requireActivity().registerReceiver(progressDismissReceiver, IntentFilter(DISMISS_ACTION))

        arguments?.let {
            multiplier  = it.getInt(EXTRA_INITIAL_MULTIPLIER, 3)

            if(videoPath.isNullOrEmpty()) {
                bufferPath = it.getString(EXTRA_BUFFER_PATH)
                videoPath = it.getString(EXTRA_VIDEO_PATH)
            }
        }
        if (videoPath.isNullOrEmpty()) {
            showProgress()
        } else if (player == null) {
            setupPlayer()
        }
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(extendTrimReceiver)
        requireActivity().unregisterReceiver(previewTileReceiver)
        requireActivity().unregisterReceiver(progressDismissReceiver)

        timebarHolder.removeView(trimSegment)
        hideProgress()
        super.onPause()
    }

    private fun bindViews() {
        playerView         = rootView.findViewById(R.id.player_view)
        currentSpeed       = rootView.findViewById(R.id.speed_indicator)
        start              = rootView.findViewById(R.id.start)
        end                = rootView.findViewById(R.id.end)
        editBackBtn        = rootView.findViewById(R.id.back_arrow)
        acceptBtn          = rootView.findViewById(R.id.accept)
        rejectBtn          = rootView.findViewById(R.id.reject)
        playBtn            = rootView.findViewById(R.id.exo_play)
        pauseBtn           = rootView.findViewById(R.id.exo_pause)
        toStartBtn         = rootView.findViewById(R.id.toStartBtn)
        acceptRejectHolder = rootView.findViewById(R.id.accept_reject_holder)
        swipeDetector      = rootView.findViewById(R.id.swipe_detector)
        timebarHolder      = rootView.findViewById(R.id.timebar_holder)
        previewTileList    = rootView.findViewById(R.id.previewFrameList)
        previewBarProgress = rootView.findViewById(R.id.previewBarProgress)
        seekbar            = rootView.findViewById(R.id.exo_progress)
    }

    private fun setupPlayer(){
        if(videoPath.isNullOrEmpty()) return

        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        setupMediaSource()
        playerView.setShowMultiWindowTimeBar(true)
        maxDuration = bufferDuration + videoDuration

        player!!.apply {
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.EXACT)
            setPlaybackParameters(PlaybackParameters(1 / multiplier.toFloat()))
            playWhenReady = false
        }
        currentSpeed.text = "$multiplier X"

        playerView.apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            controllerAutoShow = false
            controllerShowTimeoutMs = -1
            controllerHideOnTouch = false
            setBackgroundColor(Color.BLACK)
            setShutterBackgroundColor(Color.TRANSPARENT)    // removes the black screen when seeking or switching media
            showController()
        }

        seekbar.hideScrubber(0)

        player!!.addListener(object : Player.EventListener {
            override fun onPlayerError(error: ExoPlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.message}")
                error.printStackTrace()
                tries++
                if (videoPath.isNotNullOrEmpty() && tries < retries) {  //  retry in case of errors
                    CoroutineScope(Dispatchers.Main).launch {
                        player!!.clearMediaItems()
                        player!!.release()
                        player = null
                        Log.d(TAG, "TEST123onPlayerError: retrying = $tries")
                        delay(200)

                        val frag = requireActivity().supportFragmentManager.findFragmentByTag(
                            AppMainActivity.SLOW_MO_TAG)
                        requireActivity().supportFragmentManager.beginTransaction()
                            .detach(frag!!)
                            .attach(frag)
                            .commit()
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    progressTracker?.stopTracking()
                }
            }
        })
        initSwipeControls()
        previewThumbs = File("${ File(videoPath!!).parent}/previewThumbs")
        if (previewThumbs != null) {
            showThumbnailsIfAvailable(previewThumbs!!)
        }
        prepareForEdit()

        if(bufferPath != null){
            player!!.seekTo(0, bufferDuration)
        }else {
            player!!.seekTo(0)
        }

        if (!VideoService.isProcessing && !gotFrames)  //  in case we are coming from video editing there is a chance for crash
            getVideoPreviewFrames()

    }

    private fun prepareForEdit() {
        if (startWindow < 0 || endWindow < 0) {
            startWindow = 0
            endWindow = 1
        }

        if(bufferPath == null){ //  in case there is no buffer
            bufferDuration = 0
            endWindow = 0
        }

        if (editedStart < 0 || editedEnd < 0) {
            editedStart = bufferDuration
            editedEnd = bufferDuration + videoDuration
        }

        val startValue: Float = editedStart.toFloat() * 100 / maxDuration
        val endValue: Float = editedEnd.toFloat() * 100 / maxDuration
        maxDuration = bufferDuration + videoDuration

        if(startWindow == 0) player!!.seekTo(startWindow, editedStart)
        else player!!.seekTo(startWindow, editedStart - bufferDuration)
        seekbar.setDuration(editedStart)

        extendRangeMarker(startValue, endValue)
        start.performClick()
        Log.d(TAG, "prepareForEdit: " +
                "\n start window = $startWindow, edited start = $editedStart" +
                "\n end window = $endWindow, edited end = $editedEnd" +
                "\n max duration = $maxDuration" +
                "\n start and end values = $startValue, $endValue")
    }

    /**
     * changes the speed text on every tap
     */
    private fun changeSpeedText(): CharSequence {
        return when (multiplier) {
            3 -> {
                multiplier = 4
                player?.setPlaybackParameters(PlaybackParameters(1/multiplier.toFloat()))
                "4X"
            }
            4 -> {
                multiplier = 5
                player?.setPlaybackParameters(PlaybackParameters(1/multiplier.toFloat()))
                "5X"
            }
            5 -> {
                multiplier = 10
                player?.setPlaybackParameters(PlaybackParameters(1/multiplier.toFloat()))
                "10X"
            }
            10 -> {
                multiplier = 15
                player?.setPlaybackParameters(PlaybackParameters(1/multiplier.toFloat()))
                "15X"
            }
            else -> {
                multiplier = 3
                player?.setPlaybackParameters(PlaybackParameters(1/multiplier.toFloat()))
                "3X"
            }
        }
    }

    private fun bindListeners() {
        playBtn.setOnClickListener {
            if(startWindow < 0)
                startWindow = 0

            player?.setSeekParameters(SeekParameters.EXACT)
            seekAction = EditSeekControl.MOVE_NORMAL

            if (startWindow == 0) {
                player?.seekTo(startWindow, editedStart)
            }else {
                player?.seekTo(startWindow,editedStart - bufferDuration)
            }

            seekbar.showScrubber()
            player?.playWhenReady = true

            if(player != null)
                setupProgressTracker()
        }

        pauseBtn.setOnClickListener {
            seekbar.hideScrubber(0)
            progressTracker?.stopTracking()
            progressTracker = null

            player?.playWhenReady = false

            //  resetting to start of end point
            seekAction = onGoingSeekAction
            if(seekAction == EditSeekControl.MOVE_START) {
                player?.seekTo(startWindow, if(startWindow == 0) editedStart else (editedStart - bufferDuration))
            }else {
                player?.seekTo(endWindow,
                    if (endWindow == 0) editedEnd else (editedEnd - bufferDuration))
            }
        }

        start.setOnClickListener {
            // saves the current end point if available
            player?.setSeekParameters(SeekParameters.EXACT)
            if(editedEnd != maxDuration){
                endWindow = player!!.currentWindowIndex
                editedEnd = getCorrectedTimebarPosition()
            }

            // update button UI and flags
            startRangeUI()
            seekAction = EditSeekControl.MOVE_START
            onGoingSeekAction = seekAction

            if(editedStart < 0) {
                player?.seekTo(0, bufferDuration)
            }else {
                player?.seekTo(startWindow, editedStart)
            }
        }

        end.setOnClickListener {
            // saves the current start point
            player?.setSeekParameters(SeekParameters.EXACT)
            startWindow = player!!.currentWindowIndex
            editedStart = getCorrectedTimebarPosition()

            // update button UI and flags
            endRangeUI()
            seekAction = EditSeekControl.MOVE_END
            onGoingSeekAction = seekAction

            //  moves the cursor to required point
            if(editedEnd < 0) {
                player?.seekTo(1, videoDuration)
            }
            else {
                if (endWindow == 0)
                    player?.seekTo(endWindow, editedEnd)
                else
                    player?.seekTo(endWindow, editedEnd - bufferDuration)
            }
        }

        editBackBtn.setOnClickListener {
            showSaveDialog()
        }
        acceptBtn.setOnClickListener {
            saveAs()
        }

        currentSpeed.setOnClickListener {
            currentSpeed.text = changeSpeedText()
        }

        rejectBtn.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        rootView.isFocusableInTouchMode = true
        rootView.requestFocus()
        rootView.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
                event?.let {
                    if (it.action == KeyEvent.ACTION_UP) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            showSaveDialog()
                            return true
                        }
                    }
                }
                return false
            }
        })

        toStartBtn.setOnClickListener {
            if(startWindow == 0)
                player?.seekTo(startWindow, editedStart)
            else
                player?.seekTo(startWindow, editedStart - bufferDuration)
        }
    }

    /**
     * sets up the video files for playback
     */
    private fun setupMediaSource() {
        val retriever = MediaMetadataRetriever()
        val clips = arrayListOf<MediaSource>()

        if(bufferPath.isNotNullOrEmpty()) {
            try {
                retriever.setDataSource(bufferPath)
                bufferDuration =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()

                val bufferSource =
                    ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
                        .createMediaSource(MediaItem.fromUri(Uri.parse(bufferPath)))

                val clip1 = ClippingMediaSource(
                    bufferSource,
                    0,
                    TimeUnit.MILLISECONDS.toMicros(bufferDuration)
                )

                clips.add(clip1)
            }catch (e: IllegalArgumentException){
                Log.e(TAG, "setupMediaSource: error loading buffer")
                e.printStackTrace()
            }
        }

        if(videoPath.isNullOrEmpty())
            return

        retriever.setDataSource(videoPath)
        videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        retriever.release()

        Log.d(TAG, "setupMediaSource: setting up videos: \n$bufferPath and \n$videoPath")

        val videoSource =
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
                .createMediaSource(MediaItem.fromUri(Uri.parse(videoPath)))

        //  clippingMediaSource used as workaround for timeline scrubbing

        val clippedVideoDuration = videoDuration/1000
        val clip2 = ClippingMediaSource(
            videoSource,
            0,
            TimeUnit.SECONDS.toMicros(clippedVideoDuration)
        )

        clips.add(clip2)
        val mediaSource = ConcatenatingMediaSource(true, *clips.toTypedArray())

        player!!.setMediaSource(mediaSource)
        player!!.prepare()
        showBufferOverlay()
    }

    private fun initSwipeControls() {
        var startScrollingSeekPosition = 0L

        swipeDetector.onIsScrollingChanged {
            if (it) {
                startScrollingSeekPosition = player!!.currentPosition
                player!!.playWhenReady = false
                seekbar.hideScrubber(0)
            }
        }

        val emitter = SeekPositionEmitter()
        subscriptions = CompositeDisposable()

        player!!.seekWhenReady(emitter)
            ?.subscribe({
                Log.v(TAG, "seekTo=${it.first} isSeeking=${it.second}")
            }, { Log.e(TAG, "${it.message}") })
            ?.addTo(subscriptions!!)

        swipeDetector.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }

        swipeDetector.onScroll { percentX, _ ->

            val maxPercent = 0.75f
            val scaledPercent = percentX * maxPercent
            val percentOfDuration = scaledPercent * -1 * maxDuration + startScrollingSeekPosition
            var newSeekPosition = percentOfDuration.roundToLong()

            if (newSeekPosition >= player!!.contentDuration && player!!.currentWindowIndex == 0) {
                if (player!!.hasNext()) {
                    player!!.next()
                    startScrollingSeekPosition = 0
                    player!!.setSeekParameters(SeekParameters.EXACT)
                    player!!.seekTo(startScrollingSeekPosition)
                }
            }
            if (newSeekPosition <= 0L && player!!.currentWindowIndex == 1) {
                if (player!!.hasPrevious()) {
                    player!!.previous()
                    startScrollingSeekPosition = bufferDuration
                    player!!.setSeekParameters(SeekParameters.EXACT)
                    player!!.seekTo(startScrollingSeekPosition)
                }
            }

            if(player!!.currentPosition > (player!!.duration - 1500) ||
            player!!.currentPosition < 1500){
                player!!.setSeekParameters(SeekParameters.EXACT)
            }else {
                player!!.setSeekParameters(SeekParameters.CLOSEST_SYNC)
//                player!!.setSeekParameters(SeekParameters.EXACT)
            }

            when (seekAction) {
                EditSeekControl.MOVE_START -> {
                    editedStart = getCorrectedSeek(newSeekPosition)
                    if (player!!.currentWindowIndex == 1) {
                        if (newSeekPosition + bufferDuration > editedEnd) {
                            editedStart = editedEnd - 50
                            newSeekPosition = editedStart - bufferDuration
                        }
                        startWindow = 1
                    } else {
                        if (newSeekPosition > editedEnd) {
                            editedStart = editedEnd - 50
                            newSeekPosition = editedStart
                        }
                        startWindow = 0
                    }
                    trimSegment?.setMinStartValue(editedStart.toFloat() * 100 / maxDuration)?.apply()
                    trimSegment?.setMaxStartValue(editedEnd.toFloat() * 100 / maxDuration)?.apply()
                }
                EditSeekControl.MOVE_END -> {
                    editedEnd = getCorrectedSeek(newSeekPosition)
                    if (player!!.currentWindowIndex == 1) {
                        if (newSeekPosition + bufferDuration < editedStart) {
                            editedEnd = editedStart + 50
                            newSeekPosition = editedEnd - bufferDuration
                        }
                        endWindow = 1
                    } else {
                        if (newSeekPosition < editedStart) {
                            editedEnd = editedStart + 50
                            newSeekPosition = editedEnd
                        }
                        endWindow = 0
                    }
                    //  if endingTimestamps are near max duration we probably need to make that max duration
                    if (editedEnd > (maxDuration - 50))
                        editedEnd = maxDuration

                    trimSegment?.setMaxStartValue(editedEnd.toFloat() * 100 / maxDuration)?.apply()
                }
                else -> {}
            }

            if(player!!.currentWindowIndex == 0) {
                if (newSeekPosition < 0) {
                    newSeekPosition = 0
                }else if (newSeekPosition > maxDuration) {
                    newSeekPosition = maxDuration
                }
            }else {
                if (newSeekPosition < 0) {
                    newSeekPosition = 0
                }else if (newSeekPosition > maxDuration - bufferDuration) {
                    newSeekPosition = maxDuration - bufferDuration
                }
            }

            //  if we are scrolling to the beginning of the video or to the end, seek exactly
            if(player!!.currentWindowIndex == 0) {
                if (newSeekPosition < 1500 ||
                    newSeekPosition > (maxDuration - 1500)) {
                    player!!.setSeekParameters(SeekParameters.EXACT)
                }
            } else {
                if(newSeekPosition < 1500 ||
                    newSeekPosition > (maxDuration - bufferDuration - 1500)){
                    player!!.setSeekParameters(SeekParameters.EXACT)
                }
            }

            emitter.seekFast(newSeekPosition)
        }
    }

    /**
     * places a dark overlay to identify the buffer region
     */
    private fun showBufferOverlay() {
        val bufferOverlay = RangeSeekbarCustom(requireContext())
        val colour = resources.getColor(R.color.blackOverlay, context?.theme)
        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()

        val thumbDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb_transparent, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.transparent, context?.theme))

        val endValue = (bufferDuration * 100 / (bufferDuration + videoDuration)).toFloat()

        bufferOverlay.apply {
            minimumHeight = height
            elevation = 4F
            setPadding(padding, 0, padding, 0)
            setBarColor(resources.getColor(android.R.color.transparent, context?.theme))
            setBackgroundResource(R.drawable.range_background)
            setLeftThumbBitmap(thumbDrawable)
            setRightThumbBitmap(thumbDrawable)
            setBarHighlightColor(colour)
            setMinValue(0F)
            setMaxValue(100F)
            setMinStartValue(0F).apply()
            setMaxStartValue(endValue).apply()

            setOnTouchListener { _, _ -> true }
        }

        if(bufferOverlay.parent == null)
            timebarHolder.addView(bufferOverlay)
    }

    private fun extendRangeMarker(startValue: Float, endValue: Float) {
        trimSegment = RangeSeekbarCustom(requireContext())
        val colour = resources.getColor(android.R.color.transparent, context?.theme)
        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()
        val leftThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.holo_green_dark, context?.theme))
        val rightThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.holo_red_light, context?.theme))

        trimSegment!!.apply {
            minimumHeight = height
            elevation = 6F
            setPadding(padding, 0, padding, 0)
            setBarColor(resources.getColor(android.R.color.transparent, context?.theme))
            setBackgroundResource(R.drawable.range_background)
            setLeftThumbBitmap(leftThumbImageDrawable)
            setRightThumbBitmap(rightThumbImageDrawable)
            setBarHighlightColor(colour)
            setMinValue(0F)
            setMaxValue(100F)
            setMinStartValue(startValue).apply()
            setMaxStartValue(endValue).apply()

            setOnTouchListener { _, _ -> true }
        }

        if(trimSegment!!.parent == null)
            timebarHolder.addView(trimSegment)
    }

    /**
     * sets up the UI component and indicators with the correct colours for editing
     */
    private fun startRangeUI() {
        start.setBackgroundResource(R.drawable.start_curve)
        end.setBackgroundResource(R.drawable.end_curve)
    }

    /**
     * sets up the UI component and indicators with the correct colours for editing
     */
    private fun endRangeUI() {
        start.setBackgroundResource(R.drawable.end_curve)
        end.setBackgroundResource(R.drawable.end_curve_red)
    }

    private fun getCorrectedTimebarPosition(): Long {
        return if(player!!.currentWindowIndex == 0){
            player!!.currentPosition
        }else{  //  exoplayer can be messed up
            player!!.setSeekParameters(SeekParameters.EXACT)
            if (player!!.currentPosition + bufferDuration > maxDuration)
                maxDuration
            else
                player!!.currentPosition + bufferDuration
        }
    }

    private fun getCorrectedSeek(newPos: Long): Long {
        return if(player!!.currentWindowIndex == 0){
            newPos
        }else{  //  exoplayer can be messed up
            if (newPos + bufferDuration >= maxDuration)
                maxDuration
            else
                newPos + bufferDuration
        }
    }

    /**
     *  Converts vector drawable to bitmap image
     *
     *  @param vectorDrawable
     *  @return Bitmap
     * */
    private fun getBitmap(vectorDrawable: VectorDrawable, colorResource: Int): Bitmap? {
        vectorDrawable.colorFilter = PorterDuffColorFilter(colorResource, PorterDuff.Mode.SRC_ATOP)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return bitmap
    }

    fun showSaveDialog(){
        if (saveVideoDialog == null) {
            saveVideoDialog = KeepVideoDialog(this)
        }

        if(!saveVideoDialog!!.isAdded) {
            saveVideoDialog!!.isCancelable = true
            saveVideoDialog!!.show(requireActivity().supportFragmentManager, SAVE_SNAPBACK_DIALOG)
        }
    }

    override fun saveAs() {
        saveVideoDialog?.dismiss()
        showProgress()
        createModifiedVideo()
    }

    override fun save() = Unit

    override fun cancel() = Unit

    override fun exit() {
        requireActivity().supportFragmentManager.popBackStack()
    }

    /**
     *  prepares the concatenated and trimmed video with any edits
     *  concatenate buffer and video
     *  trimmed video to spec
     *  make edits
     *  save to db
     */
    private fun createModifiedVideo() {
        timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val concatOutputPath = "${File(videoPath!!).parent}/$timeStamp.mp4"
        val taskList = arrayListOf<VideoOpItem>()

        if (bufferPath != null) {
            val concatenateTask = VideoOpItem(
                operation = IVideoOpListener.VideoOp.CONCAT,
                clips = arrayListOf(bufferPath!!, videoPath!!),
                outputPath = concatOutputPath,
                comingFrom = CurrentOperation.VIDEO_EDITING)

            taskList.apply { add(concatenateTask) }
            VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.CONCAT)
            VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.TRIMMED)
            if (VideoEditingFragment.saveAction == VideoEditingFragment.SaveActionType.SAVE && bufferPath.isNotNullOrEmpty())
                VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.TRIMMED)
        } else {
            val trimmedOutputPath = "${File(videoPath!!).parent}/trimmed-$timeStamp.mp4"
            val trimTask = VideoOpItem(
                operation = IVideoOpListener.VideoOp.TRIMMED,
                clips = arrayListOf(videoPath!!),
                startTime = editedStart.milliToFloatSecond(),
                endTime = editedEnd.milliToFloatSecond(),
                outputPath = trimmedOutputPath,
                comingFrom = CurrentOperation.VIDEO_EDITING)

            taskList.apply { add(trimTask) }
            VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.TRIMMED)
        }

        val createNewVideoIntent = Intent(requireContext(), VideoService::class.java)
        createNewVideoIntent.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, taskList)
        VideoService.enqueueWork(requireContext(), createNewVideoIntent)
        videoSaved = true
    }

    /**
     * start tracking progress
     */
    private fun setupProgressTracker() {
        progressTracker?.stopTracking()
        progressTracker = null

        progressTracker = ProgressTracker(player!!)
        progressTracker!!.run()
    }

    inner class ProgressTracker(private val player: Player) : Runnable {

        private var handler: Handler? = null
        private var isChangeAccepted: Boolean = false
        private var isTrackingProgress = false

        override fun run() {
            if (context != null) {
                var currentPosition = player.currentPosition
                if(player.currentWindowIndex == 1)
                    currentPosition += bufferDuration

                if(currentPosition >= editedEnd || currentPosition >= maxDuration - 50){
                    player.playWhenReady = false
                    stopTracking()

                    seekbar.hideScrubber(0)
                    seekAction = onGoingSeekAction
                    if(onGoingSeekAction == EditSeekControl.MOVE_START) {
                        player.seekTo(startWindow, if(startWindow == 0) editedStart else (editedStart - bufferDuration))
                    }else
                        player.seekTo(endWindow, if(endWindow == 0) editedEnd else (editedEnd - bufferDuration))

                } else {
                    handler?.postDelayed(this, 20 /* ms */)
                }
            }
        }

        init {
            handler = Handler()
            handler?.post(this)
        }

        fun setChangeAccepted(isAccepted: Boolean) {
            isChangeAccepted = isAccepted
            isTrackingProgress = isAccepted
            if (handler == null && isAccepted) {
                handler = Handler()
                handler!!.post(this)
            }
        }

        fun stopTracking() {
            handler?.removeCallbacksAndMessages(null)
            handler = null
            isTrackingProgress = false
        }

        fun isCurrentlyTracking(): Boolean {
            return isTrackingProgress && handler != null
        }
    }
}