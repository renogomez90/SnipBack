package com.hipoint.snipback.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.VectorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ClippingMediaSource
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.RangeSeekbarCustom
import com.hipoint.snipback.Utils.milliToFloatSecond
import com.hipoint.snipback.adapter.TimelinePreviewAdapter
import com.hipoint.snipback.dialog.ProcessingDialog
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.enums.EditSeekControl
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.room.repository.AppViewModel
import com.hipoint.snipback.videoControl.VideoOpItem
import com.hipoint.snipback.videoControl.VideoService
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
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

class QuickEditFragment: Fragment() {
    private val TAG = QuickEditFragment::class.java.simpleName
    private val PROCESSING_DIALOG = "dialog_processing"

    private var seekAction = EditSeekControl.MOVE_NORMAL

    private var timeStamp    : String? = null
    private var previewThumbs: File?   = null
    // file paths
    private var bufferHdSnipId: Int     = 0
    private var videoSnipId   : Int     = 0
    private var bufferPath    : String? = null
    private var videoPath     : String? = null
    //  adapters
    private var timelinePreviewAdapter: TimelinePreviewAdapter? = null
    // fast seeking
    private var subscriptions: CompositeDisposable? = null
    //  dialogs
    private var processingDialog: ProcessingDialog? = null
    //  retires on failure
    private val retries = 3
    private var tries   = 0
    
    private lateinit var rootView          : View
    private lateinit var appRepository     : AppRepository
    private lateinit var appViewModel      : AppViewModel
    private lateinit var player            : SimpleExoPlayer
    private lateinit var playerView        : PlayerView
    private lateinit var start             : TextView
    private lateinit var end               : TextView
    private lateinit var editBackBtn       : ImageView
    private lateinit var acceptBtn         : ImageView
    private lateinit var rejectBtn         : ImageView
    private lateinit var acceptRejectHolder: LinearLayout
    private lateinit var previewBarProgress: ProgressBar
    private lateinit var previewTileList   : RecyclerView
    private lateinit var swipeDetector     : SwipeDistanceView
    private lateinit var timebarHolder     : FrameLayout

    private var maxDuration    = 0L
    private var bufferDuration = -1L
    private var videoDuration  = -1L
    private var startWindow    = -1
    private var endWindow      = -1
    private var editedStart    = -1L
    private var editedEnd      = -1L
    
    private val trimSegment  : RangeSeekbarCustom by lazy { RangeSeekbarCustom(requireContext()) }
    private val bufferOverlay: RangeSeekbarCustom by lazy { RangeSeekbarCustom(requireContext()) }

    private val extendTrimReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        var trimmedItemCount = 0
        var fullExtension = false
        var concatedFile = ""
        var videoSnip: Snip? = null

        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let{
                val retriever = MediaMetadataRetriever()
                val operation = it.getStringExtra("operation")
                val inputName = it.getStringExtra("fileName")

                retriever.setDataSource(inputName)
                val taskList = arrayListOf<VideoOpItem>()

                if(operation == IVideoOpListener.VideoOp.CONCAT.name){
                    val concatDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                    Log.d(TAG, "onReceive: CONCAT duration = $concatDuration")

                    concatedFile = inputName!!
                    fullExtension = (editedStart == 0L)

                    if(fullExtension) {
                        editedStart = 100    // 100 ,milli seconds into the video
                    }
                    val bufferTask = VideoOpItem(
                            operation = IVideoOpListener.VideoOp.TRIMMED,
                            clips = arrayListOf(concatedFile),
                            startTime = 0F,
                            endTime = editedStart.milliToFloatSecond(),
                            outputPath = bufferPath!!,
                            comingFrom = CurrentOperation.VIDEO_EDITING)

                        taskList.add(bufferTask)

                }else {
                    if (operation == IVideoOpListener.VideoOp.TRIMMED.name) {
                        trimmedItemCount++
                        Log.d(TAG, "onReceive: trimmed count = $trimmedItemCount")

                        if(trimmedItemCount == 1) {

                            CoroutineScope(Default).launch {
                                //  update video in DB
                                videoSnip = appRepository.getSnipById(videoSnipId)
                                videoSnip!!.total_video_duration = (editedEnd.milliToFloatSecond() - editedStart.milliToFloatSecond()).toInt()
                                videoSnip!!.snip_duration = (editedEnd.milliToFloatSecond() - editedStart.milliToFloatSecond()).toDouble()
                                appRepository.updateSnip(videoSnip!!)
                            }

                            val videoTask = VideoOpItem(
                                operation = IVideoOpListener.VideoOp.TRIMMED,
                                clips = arrayListOf(concatedFile),
                                startTime = editedStart.milliToFloatSecond(),
                                endTime = editedEnd.milliToFloatSecond(),
                                outputPath = videoPath!!,
                                comingFrom = CurrentOperation.VIDEO_EDITING)
                            taskList.add(videoTask)
                        }

                        if (trimmedItemCount == 2) {
                            Log.d(TAG, "onReceive: both files received")
                            trimmedItemCount = 0
                            hideProgress()
                            videoSnip?.let{ snip -> //  we can't just pop since the fragment only contains the unaltered snip as an argument
                                (requireActivity() as AppMainActivity).loadFragment(FragmentPlayVideo2.newInstance(snip), true)
                            }
                        }
                    }
                }

                val createNewVideoIntent = Intent(requireContext(), VideoService::class.java)
                createNewVideoIntent.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, taskList)
                VideoService.enqueueWork(requireContext(), createNewVideoIntent)
            }
        }
    }

    private val progressDismissReceiver: BroadcastReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            hideProgress()
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
                previewThumbs = File(intent.getStringExtra("preview_path")!!)
                showThumbnailsIfAvailable(previewThumbs!!)
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
        } else {
            getVideoPreviewFrames()
        }
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
    }

    companion object {
        var fragment: QuickEditFragment? = null

        @JvmStatic
        fun newInstance(bufferId: Int, videoSnipId: Int, bufferPath: String, videoPath: String): QuickEditFragment {
            //  we need to create new fragments for each video
            // otherwise the smooth scrolling is having issues for some reason
            if(fragment == null)
                fragment = QuickEditFragment()

            val bundle = Bundle()
            bundle.putInt("bufferId", bufferId)
            bundle.putInt("videoSnipId", videoSnipId)
            bundle.putString("bufferPath", bufferPath)
            bundle.putString("videoPath", videoPath)
            fragment!!.arguments = bundle
            return fragment!!
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(previewTileReceiver, IntentFilter(VideoEditingFragment.PREVIEW_ACTION))
        requireActivity().registerReceiver(extendTrimReceiver, IntentFilter(VideoEditingFragment.EXTEND_TRIM_ACTION))
        requireActivity().registerReceiver(progressDismissReceiver, IntentFilter(VideoEditingFragment.DISMISS_ACTION))
        setupPlayer()
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(previewTileReceiver)
        requireActivity().unregisterReceiver(extendTrimReceiver)
        requireActivity().unregisterReceiver(progressDismissReceiver)

        if (this::player.isInitialized) {
            player.apply {
                stop()
                setVideoSurface(null)
                release()
            }
        }

        subscriptions?.dispose()
        timebarHolder.removeView(bufferOverlay)
        timebarHolder.removeView(trimSegment)

        super.onPause()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        rootView       = inflater.inflate(R.layout.fragment_quickedit         , container, false)
        appRepository  = AppRepository(requireActivity().applicationContext)
        appViewModel   = ViewModelProvider(this).get(AppViewModel::class.java)
        bufferHdSnipId = requireArguments().getInt("bufferId")
        videoSnipId  = requireArguments().getInt("videoSnipId")
        bufferPath     = requireArguments().getString("bufferPath")
        videoPath      = requireArguments().getString("videoPath")

        bindViews()
        bindListeners()
        return rootView
    }

    private fun setupPlayer(){
        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player

        setupMediaSource()

        playerView.setShowMultiWindowTimeBar(true)

        maxDuration = bufferDuration + videoDuration

        player.apply {
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            playWhenReady = false
        }

        playerView.apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            controllerShowTimeoutMs = 2000
            setShutterBackgroundColor(Color.TRANSPARENT)    // removes the black screen when seeking or switching media
            showController()
        }

        player.addListener(object : Player.EventListener {
            override fun onPlayerError(error: ExoPlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.message}")
                error.printStackTrace()
                tries++
                if (videoPath.isNotNullOrEmpty() && tries < retries) {  //  retry in case of errors
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(500)
                        val frag = requireActivity().supportFragmentManager.findFragmentByTag(
                            AppMainActivity.PLAY_VIDEO_TAG)
                        requireActivity().supportFragmentManager.beginTransaction()
                            .detach(frag!!)
                            .attach(frag)
                            .commit()
                    }
                }
            }
        })


        initSwipeControls()
        previewThumbs = File("${ File(videoPath).parent}/previewThumbs")
        if (previewThumbs != null) {
            showThumbnailsIfAvailable(previewThumbs!!)
        }
        prepareForEdit()
    }

    private fun setupMediaSource() {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(bufferPath)
        bufferDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        retriever.setDataSource(videoPath)
        videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        retriever.release()

        Log.d(TAG, "setupMediaSource: setting up videos: \n$bufferPath and \n$videoPath")
        val bufferSource =
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
                .createMediaSource(MediaItem.fromUri(Uri.parse(bufferPath)))
        val videoSource =
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
                .createMediaSource(MediaItem.fromUri(Uri.parse(videoPath)))

        //  clippingMediaSource used as workaround for timeline scrubbing
        val clip1 = ClippingMediaSource(
            bufferSource,
            0,
            TimeUnit.MILLISECONDS.toMicros(bufferDuration)
        )
        val clippedVideoDuration = videoDuration/1000
        val clip2 = ClippingMediaSource(
            videoSource,
            0,
            TimeUnit.SECONDS.toMicros(clippedVideoDuration)
        )
        val mediaSource = ConcatenatingMediaSource(true, clip1, clip2)

        player.setMediaSource(mediaSource)
        player.prepare()
        showBufferOverlay()
    }

    private fun prepareForEdit() {
        val startValue = (bufferDuration * 100 / (bufferDuration + videoDuration)).toFloat()
        val endValue = 100F

        startWindow = 0
        endWindow   = 1
        editedStart = bufferDuration
        editedEnd   = bufferDuration + videoDuration
        maxDuration = bufferDuration + videoDuration

        player.seekTo(startWindow, editedStart)

        extendRangeMarker(startValue, endValue)
        start.performClick()
    }

    private fun bindListeners() {
        start.setOnClickListener {
            // saves the current end point if available
            if(editedEnd != maxDuration){
                endWindow = player.currentWindowIndex
                editedEnd = if(endWindow == 0)
                    player.currentPosition
                else
                    player.currentPosition + bufferDuration
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
            editedStart = if(startWindow == 0)
                player.currentPosition
            else
                player.currentPosition + bufferDuration

            // update button UI and flags
            endRangeUI()
            seekAction = EditSeekControl.MOVE_END

            //  moves the cursor to required point
            if(editedEnd < 0)
                player.seekTo(1, videoDuration)
            else
                player.seekTo(endWindow, editedEnd)
        }
        editBackBtn.setOnClickListener {

        }
        acceptBtn.setOnClickListener {
            Log.d(TAG, "bindListeners: selected start = $editedStart, selected end = $editedEnd")
            createModifiedVideo()
            showProgress()
        }

        rejectBtn.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        editBackBtn.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        rootView.isFocusableInTouchMode = true
        rootView.requestFocus()
        rootView.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    requireActivity().supportFragmentManager.popBackStack()
                    return true
                }
                return false
            }
        })
    }

    private fun bindViews(){
        playerView         = rootView.findViewById(R.id.player_view)
        start              = rootView.findViewById(R.id.start)
        end                = rootView.findViewById(R.id.end)
        editBackBtn        = rootView.findViewById(R.id.back1)
        acceptBtn          = rootView.findViewById(R.id.accept)
        rejectBtn          = rootView.findViewById(R.id.reject)
        acceptRejectHolder = rootView.findViewById(R.id.accept_reject_holder)
        swipeDetector      = rootView.findViewById(R.id.swipe_detector)
        timebarHolder      = rootView.findViewById(R.id.timebar_holder)
        previewTileList    = rootView.findViewById(R.id.previewFrameList)
        previewBarProgress = rootView.findViewById(R.id.previewBarProgress)
    }

    private fun initSwipeControls() {
        var startScrollingSeekPosition = 0L

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

            val maxPercent = 0.75f
            val scaledPercent = percentX * maxPercent
            val percentOfDuration = scaledPercent * -1 * maxDuration + startScrollingSeekPosition
            // shift in position domain and ensure circularity
            /*val newSeekPosition = ((percentOfDuration + duration) % duration).roundToLong().absoluteValue*/
            var newSeekPosition = percentOfDuration.roundToLong()

            if (newSeekPosition >= player.contentDuration && player.currentWindowIndex == 0) {
                if (player.hasNext()) {
                    player.next()
                    startScrollingSeekPosition = 0
                    player.seekTo(startScrollingSeekPosition)
                }
            }
            if (newSeekPosition <= 0L && player.currentWindowIndex == 1) {
                if (player.hasPrevious()) {
                    player.previous()
                    startScrollingSeekPosition = bufferDuration
                    player.seekTo(startScrollingSeekPosition)
                }
            }

            when (seekAction) {
                EditSeekControl.MOVE_START -> {
                    if(getCorrectedTimebarPosition() > editedEnd){
                        newSeekPosition =
                            (if(player.currentWindowIndex == 1) editedEnd - bufferDuration else editedEnd)
                    }else {
                        editedStart = getCorrectedTimebarPosition()
                    }
                    trimSegment.setMinStartValue((editedStart * 100 / maxDuration).toFloat()).apply()
                    trimSegment.setMaxStartValue((editedEnd * 100 / maxDuration).toFloat()).apply()
                }
                EditSeekControl.MOVE_END -> {
                    if(getCorrectedTimebarPosition() < editedStart){
                        newSeekPosition =
                            (if(player.currentWindowIndex == 1) editedStart - bufferDuration else editedStart)
                    }else{
                        editedEnd = getCorrectedTimebarPosition()
                    }
                    trimSegment.setMaxStartValue((editedEnd * 100 / maxDuration).toFloat()).apply()
                }
                else -> {}
            }

            if (newSeekPosition < 0) {
                newSeekPosition = 0
            }else if (newSeekPosition > maxDuration) {
                newSeekPosition = maxDuration
            }

            player.seekTo(newSeekPosition)
//            emitter.seekFast(newSeekPosition)
        }
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

        val concatenateTask = VideoOpItem(
            operation = IVideoOpListener.VideoOp.CONCAT,
            clips = arrayListOf(bufferPath!!, videoPath!!),
            outputPath = concatOutputPath,
            comingFrom = CurrentOperation.VIDEO_EDITING)

        val taskList = arrayListOf<VideoOpItem>()

        taskList.apply {
            add(concatenateTask)
        }
        VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.CONCAT)    //  for the concatenated file
        VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.TRIMMED)   //  for the buffer file
        VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.TRIMMED)   //  for the video file

        val createNewVideoIntent = Intent(requireContext(), VideoService::class.java)
        createNewVideoIntent.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, taskList)
        VideoService.enqueueWork(requireContext(), createNewVideoIntent)
    }

    private fun getCorrectedTimebarPosition(): Long {
        return if(player.currentWindowIndex == 0){
            player.currentPosition
        }else{  //  exoplayer can be messed up
            player.currentPosition + bufferDuration
        }
    }

    private fun showProgress(){
        if(processingDialog == null)
            processingDialog = ProcessingDialog()
        processingDialog!!.isCancelable = false
        processingDialog!!.show(requireActivity().supportFragmentManager, PROCESSING_DIALOG)
    }

    fun hideProgress(){
        processingDialog?.dismiss()
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

    /**
     * places a dark overlay to identify the buffer region
     */
    private fun showBufferOverlay() {
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
        val colour = resources.getColor(android.R.color.transparent, context?.theme)
        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()
        val leftThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.holo_green_dark, context?.theme))
        val rightThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
            resources.getColor(android.R.color.holo_red_light, context?.theme))

        trimSegment.apply {
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

        if(trimSegment.parent == null)
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
}