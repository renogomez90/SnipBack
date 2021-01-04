package com.hipoint.snipback.fragment

import Jni.FFmpegCmd
import VideoHandle.OnEditorListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.VectorDrawable
import android.media.MediaMetadataRetriever
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.extensions.onClick
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ClippingMediaSource
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.RangeSeekbarCustom
import com.hipoint.snipback.Utils.CommonUtils
import com.hipoint.snipback.Utils.TrimmerUtils
import com.hipoint.snipback.adapter.TimelinePreviewAdapter
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.enums.EditAction
import com.hipoint.snipback.enums.EditSeekControl
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.room.entities.Event
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.room.repository.AppViewModel
import com.hipoint.snipback.videoControl.VideoOpItem
import com.hipoint.snipback.videoControl.VideoService
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_main2.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class FragmentPlayVideo2 : Fragment(), AppRepository.HDSnipResult {
    private val TAG = FragmentPlayVideo2::class.java.simpleName
    private val VIDEO_DIRECTORY_NAME = "Snipback"

    private val retries = 3
    private var tries   = 0

    private var editedStart    = -1L
    private var editedEnd      = -1L
    private var seekAction = EditSeekControl.MOVE_NORMAL

    private var subscriptions: CompositeDisposable? = null

    private lateinit var mediaSource           : MediaSource
    private lateinit var player                : SimpleExoPlayer
    private lateinit var dataSourceFactory     : DataSource.Factory
    private lateinit var defaultBandwidthMeter : DefaultBandwidthMeter
    private lateinit var appRepository         : AppRepository
    private lateinit var appViewModel          : AppViewModel
    private lateinit var playerView            : PlayerView
    private lateinit var playBtn               : ImageButton
    private lateinit var pauseBtn              : ImageButton
    private lateinit var editBtn               : ImageButton
    private lateinit var start                 : TextView
    private lateinit var end                   : TextView
    private lateinit var acceptBtn             : ImageView
    private lateinit var rejectBtn             : ImageView
    private lateinit var acceptRejectHolder    : LinearLayout
    private lateinit var quickEditViewHolder   : LinearLayout
    private lateinit var progressDurationHolder: LinearLayout
    private lateinit var playPauseHolder       : FrameLayout
    private lateinit var quickEditBtn          : ConstraintLayout
    private lateinit var quickEditTimeTxt      : TextView
    private lateinit var seekBar               : DefaultTimeBar
    private lateinit var timebarHolder         : FrameLayout
    private lateinit var rootView              : View
    private lateinit var tag                   : ImageView
    private lateinit var previewBarProgress    : ProgressBar
    private lateinit var previewTileList       : RecyclerView
    private lateinit var backArrow             : RelativeLayout
    private lateinit var buttonCamera          : RelativeLayout
    private lateinit var tvConvertToReal       : ImageButton
    private lateinit var swipeDetector         : SwipeDistanceView

    // new
    private var event: Event? = null

    // new added
    private var snip: Snip? = null

    //  adapters
    private var timelinePreviewAdapter: TimelinePreviewAdapter? = null

    private var paused                     = false
    private var thumbnailExtractionStarted = false
    private var isInEditMode               = false
    private var bufferDuration             = -1L
    private var videoDuration              = -1L
    private var startWindow                = -1
    private var endWindow                  = -1
    private var maxDuration                = 0L

    private val trimSegment  : RangeSeekbarCustom by lazy { RangeSeekbarCustom(requireContext()) }
    private val bufferOverlay  : RangeSeekbarCustom by lazy { RangeSeekbarCustom(requireContext()) }

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

            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean =
                false
        })
    }

    private val previewTileReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val previewThumbs = File(intent.getStringExtra("preview_path")!!)
                showThumbnailsIfAvailable(previewThumbs)
            }
        }
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
        }
    }
    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(previewTileReceiver, IntentFilter(VideoEditingFragment.PREVIEW_ACTION))

        initSetup()
        bindListeners()
        Log.d(TAG, "onResume: started")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.layout_play_video, container, false)
        appRepository = AppRepository(requireActivity().applicationContext)
        appViewModel = ViewModelProvider(this).get(AppViewModel::class.java)
        snip = requireArguments().getParcelable("snip")

        appViewModel.getEventByIdLiveData(snip!!.event_id).observe(viewLifecycleOwner, Observer { snipevent: Event? -> event = snipevent })

        bindViews()

        return rootView
    }

    /**
     * restarts the player with the updated snips.
     * To be called after an edited file has been saved
     */
    fun updatePlaybackFile(updatedSnip: Snip){
        snip = updatedSnip
        player.release()
        initSetup()
    }

    private fun initSetup() {
        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player

        if (snip!!.is_virtual_version == 1) {   // Virtual versions only play part of the media
            defaultBandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()
            dataSourceFactory = DefaultDataSourceFactory(requireContext(),
                    Util.getUserAgent(requireActivity(), "mediaPlayerSample"), defaultBandwidthMeter)

            mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(snip!!.videoFilePath))

            val clippingMediaSource = ClippingMediaSource(mediaSource, TimeUnit.SECONDS.toMicros(snip!!.start_time.toLong()), TimeUnit.SECONDS.toMicros(snip!!.end_time.toLong()))
            seekBar.setDuration(snip!!.snip_duration.toLong() * 1000)
            player.addMediaSource(clippingMediaSource)
        } else {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(snip!!.videoFilePath)))
        }

        player.apply {
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            playWhenReady = true
        }

        playerView.apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            controllerShowTimeoutMs = 2000
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.TRANSPARENT)    // removes the black screen when seeking or switching media
        }

        player.addListener(object : Player.EventListener {
            override fun onPlayerError(error: ExoPlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.message}")
                error.printStackTrace()
                tries++
                if (snip?.videoFilePath.isNotNullOrEmpty() && tries < retries) {  //  retry in case of errors
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(500)
                        val frag = requireActivity().supportFragmentManager.findFragmentByTag(AppMainActivity.PLAY_VIDEO_TAG)
                        requireActivity().supportFragmentManager.beginTransaction()
                                .detach(frag!!)
                                .attach(frag)
                                .commit()
                    }
                }
            }

            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if(playbackState == Player.STATE_READY){
                    val currentTimeline = player.currentTimeline
                    maxDuration = 0L
                    for (i in 0 until currentTimeline.windowCount) {
                        val window = Timeline.Window()
                        currentTimeline.getWindow(i, window)
                        maxDuration += window.durationMs
                    }
                    if (isInEditMode) {
                        seekBar.setDuration(maxDuration)
                    }
                }
            }
        })

        maxDuration = player.duration
        checkBufferAvailable()

        if(!VideoService.isProcessing)  //  in case we are coming from video editing there is a chance for crash
            getVideoPreviewFrames()
    }

    private fun checkBufferAvailable() {
        val videoId = snip!!.snip_id
        CoroutineScope(IO).launch {
            appRepository.getHDSnipsBySnipID(this@FragmentPlayVideo2, videoId)
            //  result for this will be available at queryResult
        }
    }

    private fun bindViews() {
        buttonCamera           = rootView.findViewById(R.id.button_camera)
        backArrow              = rootView.findViewById(R.id.back_arrow)
        tvConvertToReal        = rootView.findViewById(R.id.tvConvertToReal)
        playerView             = rootView.findViewById(R.id.player_view)
        editBtn                = rootView.findViewById(R.id.edit)
        tag                    = rootView.findViewById(R.id.tag)
        swipeDetector          = rootView.findViewById(R.id.swipe_detector)
        progressDurationHolder = rootView.findViewById(R.id.progress_duration_holder)
        playPauseHolder        = rootView.findViewById(R.id.play_pause_holder)
        seekBar                = rootView.findViewById(R.id.exo_progress)
        timebarHolder          = rootView.findViewById(R.id.timebar_holder)
        playBtn                = rootView.findViewById(R.id.exo_play)
        pauseBtn               = rootView.findViewById(R.id.exo_pause)
        acceptBtn              = rootView.findViewById(R.id.accept)
        rejectBtn              = rootView.findViewById(R.id.reject)
        end                    = rootView.findViewById(R.id.end)
        start                  = rootView.findViewById(R.id.start)
        acceptRejectHolder     = rootView.findViewById(R.id.accept_reject_holder)
        quickEditBtn           = rootView.findViewById(R.id.quickEdit_button)
        quickEditTimeTxt       = rootView.findViewById(R.id.quick_edit_time)
        quickEditViewHolder    = rootView.findViewById(R.id.extend_trim_holder)
        previewBarProgress     = rootView.findViewById(R.id.previewBarProgress)
        previewTileList        = rootView.findViewById(R.id.previewFrameList)
    }

    private fun bindListeners() {
        playBtn.onClick {
            if (player.currentPosition >= player.contentDuration) {
                player.seekTo(0)
            }
            player.playWhenReady = true
            paused = false
        }

        pauseBtn.onClick {
            player.playWhenReady = false
            paused = true
        }

        tvConvertToReal.setOnClickListener { validateVideo(snip) }
        if ((if (snip != null) snip!!.is_virtual_version else 0) == 1) {
            tvConvertToReal.visibility = View.VISIBLE
        } else {
            tvConvertToReal.visibility = View.GONE
        }

        backArrow.setOnClickListener {
            player.release()
            requireActivity().onBackPressed()
        }

        buttonCamera.setOnClickListener {
//            (AppMainActivity).loadFragment(VideoMode.newInstance(),true);
            player.release()
            popToVideoMode()
        }

        quickEditBtn.setOnClickListener {
            launchQuickEdit()
        }

        tag.setOnClickListener {
            // ((AppMainActivity) requireActivity()).loadFragment(CreateTag.newInstance(), true);
        }

        editBtn.setOnClickListener {
            player.playWhenReady = false
            (activity as AppMainActivity?)!!.loadFragment(VideoEditingFragment.newInstance(snip, thumbnailExtractionStarted), true)
            thumbnailExtractionStarted = false
        }

        start.setOnClickListener {
            // saves the current end point if available
            if(editedStart > 0){
                endWindow = player.currentWindowIndex
                editedEnd = player.currentPosition
            }

            // update button UI and flags
            startRangeUI()
            seekAction = EditSeekControl.MOVE_START

            if(editedStart < 0)
                player.seekTo(0, bufferDuration)
            else
                player.seekTo(startWindow, editedStart)

        }

        end.setOnClickListener {
            // saves the current start point
            startWindow = player.currentWindowIndex
            editedStart = player.currentPosition

            // update button UI and flags
            endRangeUI()
            seekAction = EditSeekControl.MOVE_END

            //  moves the cursor to required point
            if(editedEnd < 0)
                player.seekTo(1, videoDuration)
            else
                player.seekTo(endWindow, editedEnd)

        }

        rootView.isFocusableInTouchMode = true
        rootView.requestFocus()
        rootView.setOnKeyListener(View.OnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (isInEditMode) {
                    hideEditUI()
                    restoreOriginalMedia()
                    return@OnKeyListener true
                } else {
                    player.release()
                    return@OnKeyListener false
                }
            }
            false
        })

        initSwipeControls()
    }

    /**
     * start quick edit
     */
    private fun launchQuickEdit() {
        showEditUI()
        showBufferVideo()

        val startValue = (bufferDuration * 100 / (bufferDuration + videoDuration)).toFloat()
        val endValue = 100F
        extendRangeMarker(startValue, endValue)
        start.performClick()
    }

    private fun showBufferVideo() {
        if (bufferPath.isNotEmpty()) {
            val bufferSource =
                ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
                    .createMediaSource(MediaItem.fromUri(Uri.parse(bufferPath)))
            val videoSource =
                ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
                    .createMediaSource(MediaItem.fromUri(Uri.parse(snip!!.videoFilePath)))
            //  clippingMediaSource used as workaround for timeline scrubbing
            val clip1 = ClippingMediaSource(
                bufferSource,
                0,
                TimeUnit.MILLISECONDS.toMicros(bufferDuration)
            )
            val clip2 = ClippingMediaSource(
                videoSource,
                0,
                TimeUnit.MILLISECONDS.toMicros(videoDuration)
            )
            val mediaSource = ConcatenatingMediaSource(true, clip1, clip2)

            player.setMediaSource(mediaSource)
            playerView.setShowMultiWindowTimeBar(true)
            val jumpTo = if (editedStart < 0) bufferDuration else editedStart
            player.seekTo(0, jumpTo)

            maxDuration = bufferDuration + videoDuration
            showBufferOverlay()
        }
    }

    /**
     * shows the quick edit UI for extend and trimming
     **/
    private fun showEditUI() {
        isInEditMode = true
        player.seekTo(0,0)
        player.playWhenReady = false
        playerView.controllerAutoShow = false
        playerView.controllerHideOnTouch = false
        playerView.controllerShowTimeoutMs = 0
        playerView.showController()
        quickEditViewHolder.visibility = View.VISIBLE

        progressDurationHolder.visibility = View.GONE
        backArrow.visibility = View.GONE
        buttonCamera.visibility = View.GONE
        seekBar.visibility = View.GONE
        quickEditBtn.visibility = View.GONE
        playPauseHolder.visibility = View.GONE

//        changeToEditSeekBar()
    }

    /**
     * hide the quick edit UI for extend and trimming
     **/
    private fun hideEditUI(){
        isInEditMode = false
        player.seekTo(0,0)
        playerView.controllerAutoShow = true
        playerView.controllerHideOnTouch = true
        playerView.controllerShowTimeoutMs = 3000
        playerView.hideController()
        quickEditViewHolder.visibility = View.GONE

        progressDurationHolder.visibility = View.VISIBLE
        backArrow.visibility = View.VISIBLE
        buttonCamera.visibility = View.VISIBLE
        seekBar.visibility = View.VISIBLE
        quickEditBtn.visibility = View.VISIBLE
        playPauseHolder.visibility = View.VISIBLE
    }

    /**
     * restores the original video to be played
     */
    private fun restoreOriginalMedia(){
        player.release()
        initSetup()
    }

    private fun popToVideoMode() {
        val fm = requireActivity().supportFragmentManager

        for(i in fm.backStackEntryCount - 1 downTo 0){
            if(!fm.getBackStackEntryAt(i).name.equals(AppMainActivity.VIDEO_MODE_TAG, true))
                fm.popBackStack()
        }
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

            player.playWhenReady = !paused
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

            val maxPercent = 0.75f
            val scaledPercent = percentX * maxPercent
            val percentOfDuration = scaledPercent * -1 * maxDuration + startScrollingSeekPosition
            // shift in position domain and ensure circularity
            /*val newSeekPosition = ((percentOfDuration + duration) % duration).roundToLong().absoluteValue*/
            val newSeekPosition = percentOfDuration.roundToLong()

            if(isInEditMode) {
                if (newSeekPosition >= player.contentDuration && player.currentWindowIndex == 0) {
                    if (player.hasNext()) {
                        player.next()
                        startScrollingSeekPosition = 0
                    }
                }
                if (newSeekPosition <= 0L && player.currentWindowIndex == 1) {
                    if (player.hasPrevious()) {
                        player.previous()
                        startScrollingSeekPosition = bufferDuration
                    }
                }

                when (seekAction) {
                    EditSeekControl.MOVE_START -> {
                        trimSegment.setMinStartValue((getCorrectedTimebarStartPosition() * 100 / maxDuration).toFloat()).apply()
                    }
                    EditSeekControl.MOVE_END -> {
                        trimSegment.setMaxStartValue((getCorrectedTimebarEndPosition() * 100 / maxDuration).toFloat()).apply()
                    }
                    else -> {}
                }
            }

            emitter.seekFast(newSeekPosition)
        }
    }

    private fun validateVideo(snip: Snip?) {
        val destinationPath = snip!!.videoFilePath
        val mediaStorageDir = File(Environment.getExternalStorageDirectory(),
                VIDEO_DIRECTORY_NAME)
        // Create storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return
            }
        }
        val mediaFile = File(mediaStorageDir.path + File.separator
                + "VID_" + System.currentTimeMillis() + ".mp4")
        val complexCommand = arrayOf("ffmpeg", "-i", destinationPath.toString(), "-ss", TrimmerUtils.formatCSeconds(snip.start_time.toLong()),
                "-to", TrimmerUtils.formatCSeconds(snip.end_time.toLong()), "-async", "1", mediaFile.toString())
        val hud = CommonUtils.showProgressDialog(activity)
        FFmpegCmd.exec(complexCommand, 0, object : OnEditorListener {
            override fun onSuccess() {
                snip.is_virtual_version = 0
                snip.videoFilePath = mediaFile.absolutePath
                AppClass.getAppInstance().setEventSnipsFromDb(event, snip)
                CoroutineScope(IO).launch { appRepository.updateSnip(snip) }
                val hdSnips = Hd_snips()
                hdSnips.video_path_processed = mediaFile.absolutePath
                hdSnips.snip_id = snip.snip_id

                CoroutineScope(IO).launch { appRepository.insertHd_snips(hdSnips) }
                AppClass.getAppInstance().isInsertionInProgress = true
                if (hud.isShowing) hud.dismiss()
                requireActivity().runOnUiThread { Toast.makeText(activity, "Video saved to gallery", Toast.LENGTH_SHORT).show() }

//                appViewModel.loadGalleryDataFromDB(ActivityPlayVideo.this);
            }

            override fun onFailure() {
                if (hud.isShowing) hud.dismiss()
                requireActivity().runOnUiThread { Toast.makeText(activity, "Failed to trim", Toast.LENGTH_SHORT).show() }
            }

            override fun onProgress(progress: Float) {}
        })
    }

    companion object {
        var fragment: FragmentPlayVideo2? = null

        private var currentPos = 0L
        private var bufferPath = ""
        private var bufferAvailable = false

        @JvmStatic
        fun newInstance(snip: Snip?): FragmentPlayVideo2 {
            //  we need to create new fragments for each video
            // otherwise the smooth scrolling is having issues for some reason
            if(fragment == null)
                fragment = FragmentPlayVideo2()

            val bundle = Bundle()
            bundle.putParcelable("snip", snip)
            fragment!!.arguments = bundle
            return fragment!!
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause: started")
        requireActivity().unregisterReceiver(previewTileReceiver)

        player.playWhenReady = false
        currentPos = player.currentPosition

        if (this::player.isInitialized) {
            player.apply {
                stop()
                setVideoSurface(null)
                release()
            }
        }

        subscriptions?.dispose()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * Populates preview frames in the seekBar area from the video
     */
    private fun getVideoPreviewFrames() {
        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
                operation = IVideoOpListener.VideoOp.FRAMES,
                clips = arrayListOf(snip!!.videoFilePath),
                outputPath = File(snip!!.videoFilePath).parent!!,
                comingFrom = CurrentOperation.VIDEO_EDITING))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
        thumbnailExtractionStarted = true
    }

    override suspend fun queryResult(hdSnips: List<Hd_snips>?) {
        hdSnips?.let {
            if (it.size < 2) {
                bufferAvailable = false
                withContext(Main) { quickEditBtn.visibility = View.INVISIBLE }
                return@let
            }

            val sorted = it.sortedBy { hdSnips -> hdSnips.video_path_processed.toLowerCase() }

            sorted.forEach { item -> Log.d(TAG, "queryResult: ${item.video_path_processed}") }

            if (sorted[0].video_path_processed == sorted[1].video_path_processed) {       //  there is no buffer
                bufferAvailable = false
                withContext(Main) { quickEditBtn.visibility = View.INVISIBLE }
                return@let
            } else {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(sorted[0].video_path_processed)
                    bufferDuration =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            .toLong()
                    retriever.setDataSource(snip!!.videoFilePath)
                    videoDuration =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            .toLong()
                    retriever.release()

                    if (bufferDuration > 0L && videoDuration > 0L) {
                        withContext(Main) {
                            quickEditBtn.visibility = View.VISIBLE
                            quickEditTimeTxt.text =
                                "-${TimeUnit.MILLISECONDS.toSeconds(bufferDuration)} s"
                        }
                        bufferAvailable = true
                        bufferPath = sorted[0].video_path_processed
//                        addToVideoPlayback(sorted[0].video_path_processed)
                    } else {
                        bufferAvailable = false
                        withContext(Main) { quickEditBtn.visibility = View.INVISIBLE }
                    }

                } catch (e: IllegalArgumentException) {
                    bufferAvailable = false
                    withContext(Main) { quickEditBtn.visibility = View.INVISIBLE }
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getCorrectedTimebarStartPosition(): Long {
        return if(player.currentWindowIndex == 0){
            player.currentPosition
        }else{  //  exoplayer can be messed up
            if(player.currentPosition + bufferDuration > maxDuration)
                player.currentPosition
            else
                player.currentPosition + bufferDuration
        }
    }

    private fun getCorrectedTimebarEndPosition(): Long {
        return if(player.currentWindowIndex == 0){
            player.currentPosition
        }else{  //  exoplayer can be messed up
            player.currentPosition + bufferDuration
        }
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

    private fun showBufferOverlay() {
        val colour = resources.getColor(R.color.blackOverlay, context?.theme)
        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()

        val thumbDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb_transparent, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.transparent, context?.theme))

        val endValue = (bufferDuration * 100 / (bufferDuration + videoDuration)).toFloat()

        bufferOverlay.apply {
            minimumHeight = height
            elevation = 1F
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
        val colour = resources.getColor(android.R.color.transparent, context?.theme)
        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()
        val leftThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.holo_green_dark, context?.theme))
        val rightThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.holo_red_light, context?.theme))

        trimSegment.apply {
            minimumHeight = height
            elevation = 1F
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

        timebarHolder.addView(trimSegment)
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

    private fun changeToEditSeekBar(){

        seekBar.layoutParams.height =  35.dpToPx()
        seekBar.elevation = 8.dpToPx().toFloat()
        seekBar.setPadding(seekBar.paddingLeft,seekBar.paddingTop,seekBar.paddingRight, 3.dpToPx())
        seekBar.setBufferedColor(requireContext().getColor(android.R.color.transparent))
        seekBar.setPlayedColor(requireContext().getColor(android.R.color.transparent))
        seekBar.setUnplayedColor(requireContext().getColor(android.R.color.transparent))

        /*android:layout_height="35dp"
        android:elevation="8dp"
        android:paddingBottom="3dp"
        app:bar_height="40dp"
        app:buffered_color="@android:color/transparent"
        app:played_color="@android:color/transparent"
        app:scrubber_drawable="@drawable/ic_thumb"
        app:scrubber_enabled_size="35dp"
        app:unplayed_color="@android:color/transparent"*/
    }

    private fun Int.dpToPx(): Int {
        val displayMetrics = requireContext().resources.displayMetrics
        return (this * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }
}