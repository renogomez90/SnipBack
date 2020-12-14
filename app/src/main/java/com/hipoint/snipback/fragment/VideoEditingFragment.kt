package com.hipoint.snipback.fragment

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.VectorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
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
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.RangeSeekbarCustom
import com.hipoint.snipback.adapter.EditChangeListAdapter
import com.hipoint.snipback.adapter.TimelinePreviewAdapter
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.dialog.ExitEditConfirmationDialog
import com.hipoint.snipback.dialog.ProcessingDialog
import com.hipoint.snipback.dialog.SaveEditDialog
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.enums.EditAction
import com.hipoint.snipback.enums.EditSeekControl
import com.hipoint.snipback.listener.IJumpToEditPoint
import com.hipoint.snipback.listener.IReplaceRequired
import com.hipoint.snipback.listener.ISaveListener
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.videoControl.SpeedDetails
import com.hipoint.snipback.videoControl.VideoOpItem
import com.hipoint.snipback.videoControl.VideoService
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class VideoEditingFragment : Fragment(), ISaveListener, IJumpToEditPoint, AppRepository.HDSnipResult {
    private val TAG                 = VideoEditingFragment::class.java.simpleName
    private val SAVE_DIALOG         = "dialog_save"
    private val EXIT_CONFIRM_DIALOG = "dialog_exit_confirm"
    private val PROCESSING_DIALOG   = "dialog_processing"
    private val retries             = 3

    //    UI
    private lateinit var rootView          : View
    private lateinit var back              : ImageView         //  for leaving the edit fragment
    private lateinit var back1             : ImageView         //  for leaving the ongoing edit
    private lateinit var save              : ImageView         //  saving the edited file
    private lateinit var accept            : ImageView         //  saving the edited file
    private lateinit var reject            : ImageView         //  closing the ongoing edit
    private lateinit var playCon1          : LinearLayout
    private lateinit var playCon2          : LinearLayout
    private lateinit var acceptRejectHolder: LinearLayout
    private lateinit var speedIndicator    : TextView
    private lateinit var extentTextBtn     : TextView          //  extend or trim the video
    private lateinit var cutTextBtn        : TextView
    private lateinit var highlightTextBtn  : TextView
    private lateinit var slowTextBtn       : TextView
    private lateinit var speedTextBtn      : TextView
    private lateinit var end               : TextView
    private lateinit var start             : TextView
    private lateinit var playBtn           : ImageButton       //  playback the video
    private lateinit var pauseBtn          : ImageButton       //  stop video playback
    private lateinit var toStartBtn        : ImageButton       //  seek back to start of video
    private lateinit var playCon           : ConstraintLayout
    private lateinit var previewTileList   : RecyclerView
    private lateinit var changeList        : RecyclerView
    private lateinit var seekBar           : DefaultTimeBar
    private lateinit var timebarHolder     : FrameLayout
    private lateinit var colourOverlay     : LinearLayout
    private lateinit var previewBarProgress: ProgressBar
    private lateinit var swipeDetector     : SwipeDistanceView //  detects swiping actions for scrolling with preview

    //    Exoplayer
    private lateinit var playerView: PlayerView
    private lateinit var player    : SimpleExoPlayer

    private var progressTracker: ProgressTracker? = null

    //    Snip
    private var snip: Snip? = null

    //  adapters
    private var timelinePreviewAdapter: TimelinePreviewAdapter? = null
    private var editListAdapter       : EditChangeListAdapter?  = null

    //  Seek handling
    private var subscriptions = CompositeDisposable()
    private var paused        = true

    //  speed change
    var isSpeedChanged = false
    var isEditOnGoing  = false
    var isEditExisting = false
    var isSeekbarShown = true

    //  extend/trim
    private var showBuffer     = false
    private var bufferPath     = ""
    private var bufferDuration = 0L
    private var videoDuration  = 0L

    private var editHistory = arrayListOf<EditAction>() //  list of edit actions that were performed

    //  once the user decides to save the video after trimming/extending
    private var editedStart    = -1L
    private var editedEnd      = -1L

    private var maxDuration         = 0L
    private var previousMaxDuration = 0L
    private var currentSpeed        = 3
    private var startingTimestamps  = -1L
    private var endingTimestamps    = -1L
    private var segmentCount        = 0
    private var speedDuration       = Pair<Long, Long>(0, 0)
    private var speedDetailSet      = mutableSetOf<SpeedDetails>()
    private var editAction          = EditAction.NORMAL
    private var editSeekAction      = EditSeekControl.MOVE_NORMAL
    private var currentEditSegment  = -1

    private var tmpSpeedDetails: SpeedDetails?                  = null
    private var uiRangeSegments: ArrayList<RangeSeekbarCustom>? = null
    private var trimSegment    : RangeSeekbarCustom?            = null
    private var restrictList   : List<SpeedDetails>?            = null //  speed details to prevent users from selecting an existing edit

    private val replaceRequired: IReplaceRequired by lazy { requireActivity() as AppMainActivity }
    private val bufferOverlay  : RangeSeekbarCustom by lazy { RangeSeekbarCustom(requireContext()) }

    //  dialogs
    private var saveDialog      : SaveEditDialog?             = null
    private var exitConfirmation: ExitEditConfirmationDialog? = null
    private var processingDialog: ProcessingDialog?           = null

    private var thumbnailExtractionStarted:Boolean = false

    private val extendTrimReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let{
                val operation = it.getStringExtra("operation")
                val inputName = it.getStringExtra("fileName")

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val trimmedOutputPath = "${File(inputName).parent}/trimmed-$timeStamp.mp4"
                val speedChangedPath = "${File(inputName).parent}/VID_$timeStamp.mp4"

                val taskList = arrayListOf<VideoOpItem>()

                if(operation == IVideoOpListener.VideoOp.CONCAT.name){
                    val trimTask = VideoOpItem(
                            operation = IVideoOpListener.VideoOp.TRIMMED,
                            clips = arrayListOf(inputName),
                            startTime = TimeUnit.MILLISECONDS.toSeconds(editedStart).toInt(),
                            endTime = TimeUnit.MILLISECONDS.toSeconds(editedEnd).toInt(),
                            outputPath = trimmedOutputPath,
                            comingFrom = CurrentOperation.VIDEO_EDITING)

                    taskList.add(trimTask)
                }else if(operation == IVideoOpListener.VideoOp.TRIMMED.name) {
                    val speedChangeTask = VideoOpItem(
                            operation = IVideoOpListener.VideoOp.SPEED,
                            clips = arrayListOf(inputName),
                            outputPath = speedChangedPath,
                            speedDetailsList = speedDetailSet.toMutableList() as ArrayList<SpeedDetails>,
                            comingFrom = CurrentOperation.VIDEO_EDITING)

                    taskList.add(speedChangeTask)
                }

                val createNewVideoIntent = Intent(requireContext(), VideoService::class.java)
                createNewVideoIntent.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, taskList)
                VideoService.enqueueWork(requireContext(), createNewVideoIntent)
            }
        }
    }

    private val previewTileReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val previewThumbs = File(intent.getStringExtra("preview_path")!!)
                showThumbnailsIfAvailable(previewThumbs)
            }
        }
    }

    private val toRealCompletionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let{
                val realVideoPath = intent.getStringExtra("video_path")
                when(saveAction){
                    SaveActionType.SAVE,
                    SaveActionType.SAVE_AS -> {
                        val (clip, outputName) = createSpeedChangedVideo(realVideoPath!!)
                        (requireActivity() as AppMainActivity).showInGallery.add(File("${clip.parent}/$outputName").nameWithoutExtension)
                        Toast.makeText(requireContext(), "Saving Edits", Toast.LENGTH_SHORT).show()
                    }
                    else ->{}
                }
            }
        }
    }

    /**
     * to access the DB for getting the buffered video
     */
    private val appRepository by lazy { AppRepository(AppClass.getAppInstance()) }
    /**
     * To dynamically change the seek parameters so that seek appears to be more responsive
     */
    private val gestureDetector by lazy {
        GestureDetector(requireContext(), object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent?): Boolean {
                return false
            }

            override fun onShowPress(e: MotionEvent?) {
            }

            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                return false
            }

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

            override fun onLongPress(e: MotionEvent?) {
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                return false
            }
        })
    }

    private val speedDetailsComparator = Comparator<SpeedDetails> { s1, s2 ->
        (s1.timeDuration?.first!! - s2?.timeDuration!!.first).toInt()
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.video_editing_fragment_main2, container, false)
        snip = requireArguments().getParcelable("snip")
        thumbnailExtractionStarted = requireArguments().getBoolean("thumbnailExtractionStarted")
        saveAction = SaveActionType.CANCEL

        bindViews()
        bindListeners()
        setupPlayer()
        return rootView
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(previewTileReceiver, IntentFilter(PREVIEW_ACTION))
        requireActivity().registerReceiver(toRealCompletionReceiver, IntentFilter(VIRTUAL_TO_REAL_ACTION))
        requireActivity().registerReceiver(extendTrimReceiver, IntentFilter(EXTEND_TRIM_ACTION))
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(previewTileReceiver)
        requireActivity().unregisterReceiver(toRealCompletionReceiver)
        requireActivity().unregisterReceiver(extendTrimReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        if (this::player.isInitialized) {
            player.apply {
                playWhenReady = false
                stop(true)
                setVideoSurface(null)
                release()
            }

            subscriptions.dispose()
        }
        super.onDestroy()
    }

    /**
     * Binds views to layout references
     */
    private fun bindViews() {
        with(rootView) {
            playerView         = findViewById(R.id.player_view)
            playCon            = findViewById(R.id.play_con)
            playCon1           = findViewById(R.id.play_con1)
            playCon2           = findViewById(R.id.play_con2)
            acceptRejectHolder = findViewById(R.id.accept_reject_holder)
            speedIndicator     = findViewById(R.id.speed_indicator)
            extentTextBtn      = findViewById(R.id.extent_text)
            cutTextBtn         = findViewById(R.id.cut_text_btn)
            highlightTextBtn   = findViewById(R.id.highlight_text_btn)
            slowTextBtn        = findViewById(R.id.slow_text_btn)
            speedTextBtn       = findViewById(R.id.speedup_text_btn)
            save               = findViewById(R.id.save)
            accept             = findViewById(R.id.accept)
            end                = findViewById(R.id.end)
            start              = findViewById(R.id.start)
            reject             = findViewById(R.id.reject)
            playBtn            = findViewById(R.id.exo_play)
            pauseBtn           = findViewById(R.id.exo_pause)
            toStartBtn         = findViewById(R.id.toStartBtn)
            back               = findViewById(R.id.back)
            back1              = findViewById(R.id.back1)
            seekBar            = findViewById(R.id.exo_progress)
            timebarHolder      = findViewById(R.id.timebar_holder)
            colourOverlay      = findViewById(R.id.colour_overlay)
            previewTileList    = findViewById(R.id.previewFrameList)
            changeList         = findViewById(R.id.change_list)
            previewBarProgress = findViewById(R.id.previewBarProgress)
            swipeDetector      = findViewById(R.id.edit_swipe_detector)
        }
        setupIcons()
    }

    /**
     * Sets up UI icons
     */
    private fun setupIcons() {

        val density = resources.displayMetrics.density
        val bound1 = Rect(0, 0, (20 * density).roundToInt(), (20 * density).roundToInt())

        val extendDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_extend)
        extendDwg?.bounds = bound1
        extentTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, extendDwg, null, null)

        val cutDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_cutout)
        cutDwg?.bounds = bound1
        cutTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, cutDwg, null, null)

        val highlightDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_highlight)
        highlightDwg?.bounds = bound1
        highlightTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, highlightDwg, null, null)

        val slowDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_slow)
        slowDwg?.bounds = bound1
        slowTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, slowDwg, null, null)

        val speedDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_speed)
        speedDwg?.bounds = bound1
        speedTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, speedDwg, null, null)

    }

    /**
     * Binds listeners for view references
     */
    private fun bindListeners() {
        /**
         * sets up the start position UI and increments the segmentCount indicating the number of edit segments available
         */
        start.setOnClickListener {
            if(!isEditExisting) {
                if (checkSegmentTaken(player.currentPosition))
                    return@setOnClickListener
            }

            startRangeUI()

            editSeekAction = EditSeekControl.MOVE_START
            when(editAction){
                EditAction.SLOW,
                EditAction.FAST -> {
                    if (tmpSpeedDetails != null) {
                        //  this means the end point was already set
                        //  the user wishes to move the starting point only
                        endingTimestamps = player.currentPosition
                        acceptRejectHolder.visibility = View.VISIBLE
                    }
                }
                EditAction.EXTEND_TRIM -> {
                    if(startingTimestamps < bufferDuration)
                        player.seekTo(0, startingTimestamps)
                    else
                        player.seekTo(1, startingTimestamps - bufferDuration)
                }
                else -> {}
            }

            Log.d(TAG, "bindListeners: start: startingTS = $startingTimestamps, endingTS = $endingTimestamps")
        }

        /**
         * locks in the start edit point and sets up the rangeSeekBar for indication.
         * range indicator starts off with tmpSpeedDetails which should then be replaced with the actual details
         */
        end.setOnClickListener {
            if(!isEditExisting && editAction != EditAction.EXTEND_TRIM) {
                if (checkSegmentTaken(player.currentPosition))   // checking to see if the start positions is acceptable
                    return@setOnClickListener
            }

            start.setBackgroundResource(R.drawable.end_curve)
            end.setBackgroundResource(R.drawable.end_curve_red)
            val currentPosition = player.currentPosition
            editSeekAction = EditSeekControl.MOVE_END


            Log.d(TAG, "end clicked : starting time stamp = $startingTimestamps, current position = $currentPosition")
//            if (startingTimestamps != currentPosition) {

            when(editAction) {
                EditAction.FAST,
                EditAction.SLOW -> {
                    var startWindow = player.currentWindowIndex
                    var endWindow = player.currentWindowIndex
                    startingTimestamps = currentPosition  //    take the starting point when end is pressed

                    val startValue = if (player.currentWindowIndex == 0) (startingTimestamps * 100 / maxDuration).toFloat()
                    else ((bufferDuration + startingTimestamps) * 100 / maxDuration).toFloat()

                    var endValue = startValue

                    speedDuration = if (isEditExisting) {   //  if an exiting item is being modified
                        player.seekTo(endingTimestamps)

                        endValue = if (speedDetailSet.elementAt(currentEditSegment).startWindowIndex == 1) {
                            endWindow = 1
                            ((bufferDuration + endingTimestamps) * 100 / maxDuration).toFloat()
                        } else (endingTimestamps * 100 / maxDuration).toFloat()

                        Pair(startingTimestamps, endingTimestamps)  //  we may have come from edit
                    } else {
                        Pair(startingTimestamps, startingTimestamps)    //  we only have the starting position now
                    }
                    tmpSpeedDetails = SpeedDetails(
                            startWindowIndex = startWindow,
                            endWindowIndex = endWindow,
                            isFast = editAction == EditAction.FAST,
                            multiplier = getCurrentEditSpeed(),
                            timeDuration = speedDuration)

                    speedDetailSet.add(tmpSpeedDetails!!)

                    setupRangeMarker(startValue, endValue)    //  initial value for marker
                    if (timebarHolder.indexOfChild(uiRangeSegments!![currentEditSegment]) < 0)  //  View doesn't exist and can be added
                        timebarHolder.addView(uiRangeSegments!![currentEditSegment])
                }

                EditAction.EXTEND_TRIM -> {
                    startingTimestamps = getCorrectedTimebarStartPosition()
                    if(endingTimestamps > bufferDuration)
                        player.seekTo(1, endingTimestamps - bufferDuration)
                    else
                        player.seekTo(0, endingTimestamps)
                }

                else -> {}
            }
//            }
            Log.d(TAG, "bindListeners: end: startingTS = $startingTimestamps, endingTS = $endingTimestamps")
            acceptRejectHolder.visibility = View.VISIBLE
        }

        extentTextBtn.setOnClickListener {
            player.playWhenReady = false
            progressTracker?.stopTracking()
//            extent.setImageResource(R.drawable.ic_extent_red)
            val dwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_extent_red)
            dwg?.bounds = Rect(0, 0, 20, 20)
            extentTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, dwg, null, null)
            extentTextBtn.setTextColor(resources.getColor(R.color.colorPrimaryDimRed))

            resetPlaybackUI()
            playCon1.visibility           = View.VISIBLE
            playCon2.visibility           = View.GONE
            acceptRejectHolder.visibility = View.VISIBLE

            val videoId = snip!!.snip_id
            CoroutineScope(IO).launch {
                appRepository.getHDSnipsBySnipID(this@VideoEditingFragment, videoId)
                //  result for this will be available at queryResult
            }
        }

        back.setOnClickListener {
            confirmExitOnBackPressed()
        }

        back1.setOnClickListener {
            speedIndicator.visibility = View.GONE
            playCon1.visibility       = View.GONE
            changeList.visibility     = View.GONE
            playCon2.visibility       = View.VISIBLE

            startRangeUI()
            resetPlaybackUI()
            if(showBuffer){
                undoExtendTrim()
            }
            if(tmpSpeedDetails != null){
                uiRangeSegments?.removeAt(currentEditSegment)
                tmpSpeedDetails = null
            }
            if(trimSegment != null){
                timebarHolder.removeView(trimSegment)
                trimSegment = null
            }
            isEditOnGoing = false
            isEditExisting = false
        }

        save.setOnClickListener {
            showDialogSave()
//            VideoUtils(this).changeSpeed(File(snip!!.videoFilePath), speedDetailSet.toMutableList() as ArrayList<SpeedDetails>, "${File(snip!!.videoFilePath).parent}/output.mp4")
        }

        /**
         * accepts the changes to the edit that were made
         * takes in the end position and updates the rangeSeekBar.
         * progress tracker is run to make sure playback is in accordance with the edit.
         * reset the UI for new edit
         */
        accept.setOnClickListener {
            if (editAction == EditAction.FAST || editAction == EditAction.SLOW) {
                acceptSpeedChanges()
            } else if (editAction == EditAction.EXTEND_TRIM) {
                removeBufferOverlays()
                acceptTrimChanges()
                showAdjustedSpeedChanges()
            }

            editHistory.add(editAction)
            startRangeUI()
            resetPlaybackUI()
            Log.d(TAG, "bindListeners: accept: startingTS = $startingTimestamps, endingTS = $endingTimestamps")
        }

        /**
         * progressTracker speed is reset to normal
         * start and end time stamps are reset
         * edit segmentCount is decremented
         * UI is reset for new edit
         * */
        reject.setOnClickListener {
            progressTracker?.setChangeAccepted(false)
            currentSpeed = 1
            progressTracker?.setSpeed(currentSpeed.toFloat())

            if (tmpSpeedDetails != null) {
                progressTracker?.removeSpeedDetails(tmpSpeedDetails!!)
                val ref = uiRangeSegments?.removeAt(uiRangeSegments?.size!! - 1)
                timebarHolder.removeView(ref)
                tmpSpeedDetails = null
            }

            if(showBuffer){
                undoExtendTrim()
            }

            startingTimestamps = -1L
            endingTimestamps = -1L
            player.setPlaybackParameters(PlaybackParameters(1F))
            isSpeedChanged = false
            isEditOnGoing = false

            segmentCount -= 1
            currentEditSegment -= 1
            startRangeUI()
            resetPlaybackUI()

            restrictList = speedDetailSet.toList()
            restrictList?.sortedWith(speedDetailsComparator)
        }

        playBtn.setOnClickListener {
            if (player.currentPosition >= maxDuration)
                player.seekTo(0,0)
            player.playWhenReady = true
            paused = false
            Log.d(TAG, "Start Playback")
        }

        pauseBtn.setOnClickListener {
            if (player.isPlaying) {
                player.playWhenReady = false
                paused = true
                Log.d(TAG, "Stop Playback")
            }
        }

        toStartBtn.setOnClickListener {
            player.playWhenReady = false
            player.seekTo(0,0)
        }

        //  prevent touchs on seekbar
        seekBar.setOnTouchListener { _, _ ->
            true
        }
        seekBar.isClickable = false

        slowTextBtn.setOnClickListener {
            player.playWhenReady = false
            val currentPosition = player.currentPosition
            if(!isEditOnGoing){
                handleNewSpeedChange(currentPosition)
            }else{
                //  update the currently changing section to slow
                handleExistingSpeedChange(currentPosition, false)
            }
            editAction = EditAction.SLOW

            Log.d(TAG, "bindListeners: slow: startingTS = $startingTimestamps, endingTS = $endingTimestamps")
        }

        speedTextBtn.setOnClickListener {
            player.playWhenReady = false
            val currentPosition = player.currentPosition
            if(!isEditOnGoing){  //  we are editing afresh
                handleNewSpeedChange(currentPosition)
            }else{
                handleExistingSpeedChange(currentPosition, true)
            }
            editAction = EditAction.FAST

            Log.d(TAG, "bindListeners: speed: startingTS = $startingTimestamps, endingTS = $endingTimestamps")
        }

        speedIndicator.setOnClickListener {
            speedIndicator.text = changeSpeedText()
            progressTracker?.setSpeed(getCurrentEditSpeed().toFloat())
        }
    }

    /**
     * resets the editor to play the original video
     */
    private fun undoExtendTrim() {

        removeBufferOverlays()
        showBuffer = false
        bufferDuration = 0L
        bufferPath = ""
        maxDuration = videoDuration
        setupPlayer()
        showAdjustedSpeedChanges()
    }

    /**
     * adjusts the speed changes so that they remain as is regardless of extend/trim
     */
    private fun showAdjustedSpeedChanges() {
        val (_, height, padding) = setupCommonRangeUiElements()

        val thumbDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb_transparent, context?.theme) as VectorDrawable,
                resources.getColor(android.R.color.transparent, context?.theme))

        uiRangeSegments?.clear()

        speedDetailSet.forEach{
            val colour = if (it.isFast) resources.getColor(R.color.blueOverlay, context?.theme)
                else resources.getColor(R.color.greenOverlay, context?.theme)

            val tmp = RangeSeekbarCustom(requireContext())
            val startValue = (it.timeDuration!!.first * 100 / maxDuration).toFloat()
            val endValue = (it.timeDuration!!.second * 100 / maxDuration).toFloat()

            tmp.apply {
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
                setMinStartValue(startValue).apply()
                setMaxStartValue(endValue).apply()
                setOnTouchListener { _, _ -> true }
            }

            uiRangeSegments?.add(tmp)
            timebarHolder.addView(tmp)

            //  start tracking these changes
            progressTracker = null
            progressTracker = ProgressTracker(player)
            with(progressTracker!!) {
                setSpeedDetails(speedDetailSet.toMutableList() as ArrayList<SpeedDetails>)
                setChangeAccepted(true)
                run()
            }
        }
    }

    /**
     * adjusts the existing speed changes so that they remain the same with the modified video also
     *
     * @param isStartInBuffer Boolean
     * @param isEndInBuffer Boolean
     */
    private fun adjustPreviousSpeedEdits(isStartInBuffer: Boolean, isEndInBuffer: Boolean) {
        val moveBy = if(isStartInBuffer){
            bufferDuration - editedStart
        }else{
            editedStart - videoDuration
        }

        val removeSet = setOf<SpeedDetails>()
        var shouldRemove =false

        var counter = 0
        speedDetailSet.forEach { // check if some edit exists in buffered video from a previous extension/trim
            if(it.startWindowIndex == 0 && it.endWindowIndex == 0)
                counter++
        }

        val existingEditInBuffer = counter != speedDetailSet.size //  previous edits in buffer
        val difference = maxDuration - previousMaxDuration

        speedDetailSet.forEach{
            Log.d(TAG, "adjustPreviousSpeedEdits: original speed detail = $it")

            if(it.startWindowIndex == 0 && it.endWindowIndex == 0){
                it.startWindowIndex = 1
                it.endWindowIndex = 1
            }

            var s = if(!existingEditInBuffer) it.timeDuration!!.first + moveBy else it.timeDuration!!.first + difference
            var e = if(!existingEditInBuffer) it.timeDuration!!.second + moveBy else it.timeDuration!!.second + difference

            //  checking the starting points
            if(s < 0){  //  the starting portion is trimmed out
                s = 0
                if(e < 0){  //  both starting and ending changes are out of range
                    shouldRemove = true
                }
            }
            it.timeDuration = Pair(s, e)
            if(shouldRemove) {
                removeSet.plus(it)
                shouldRemove = false
            }

            //  checking the ending points
            if(e > editedEnd){
                e = editedEnd
                if(s > editedEnd){
                    shouldRemove = true
                }
            }
            it.timeDuration = Pair(s, e)
            if(shouldRemove) {
                removeSet.plus(it)
                shouldRemove = false
            }
            Log.d(TAG, "adjustPreviousSpeedEdits: updated speed detail = $it")
        }

        removeSet.forEach{
            speedDetailSet.remove(it)
        }

        previousMaxDuration = maxDuration
        maxDuration = editedEnd - editedStart
        bufferDuration = max(bufferDuration - editedStart, 0)
    }

    /**
     * removes the extend/trim related overlays
     */
    private fun removeBufferOverlays() {
        val dwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_extend)
        dwg?.bounds = Rect(0, 0, 20, 20)
        extentTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, dwg, null, null)
        extentTextBtn.setTextColor(resources.getColor(R.color.colorPrimaryGrey))

        trimSegment?.let {
            timebarHolder.removeView(trimSegment)
        }
        timebarHolder.removeView(bufferOverlay)
    }

    private fun acceptTrimChanges() {
        val bufferSource = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext())).createMediaSource(MediaItem.fromUri(Uri.parse(bufferPath)))
        val videoSource = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext())).createMediaSource(MediaItem.fromUri(Uri.parse(snip!!.videoFilePath)))

        // Clip the videos to the required positions
        val startBClip = if (startingTimestamps in 0 until bufferDuration) startingTimestamps else bufferDuration
        val endBClip = if (endingTimestamps in startingTimestamps until bufferDuration) endingTimestamps else bufferDuration

        //  the value is in the buffered video
        val isStartInBuffer = startBClip == startingTimestamps
        val isEndInBuffer = endBClip == endingTimestamps

        var clip1: ClippingMediaSource? = null
        var clip2: ClippingMediaSource? = null
        val clipsList = arrayListOf<ClippingMediaSource>()

        if (isStartInBuffer) {
            editedStart = startBClip
            if (isEndInBuffer) {//  clippingMediaSource used as workaround for timeline scrubbing
                clip1 = ClippingMediaSource(bufferSource, TimeUnit.MILLISECONDS.toMicros(startBClip), TimeUnit.MILLISECONDS.toMicros(endBClip))
                editedEnd = endBClip
            } else {
                clip1 = ClippingMediaSource(bufferSource, TimeUnit.MILLISECONDS.toMicros(startBClip), TimeUnit.MILLISECONDS.toMicros(bufferDuration))
                clip2 = ClippingMediaSource(videoSource, TimeUnit.MILLISECONDS.toMicros(0), TimeUnit.MILLISECONDS.toMicros(endingTimestamps - bufferDuration))
                editedEnd = endingTimestamps
            }
        } else {
            clip2 = ClippingMediaSource(videoSource, TimeUnit.MILLISECONDS.toMicros(startingTimestamps - bufferDuration), TimeUnit.MILLISECONDS.toMicros(endingTimestamps - bufferDuration))
            editedStart = startingTimestamps - bufferDuration
            editedEnd = endingTimestamps - bufferDuration
        }

        if (clip1 != null) {
            clipsList.add(clip1)
        }
        if (clip2 != null) {
            clipsList.add(clip2)
        }

        val mediaSource = ConcatenatingMediaSource(true, *clipsList.toTypedArray())
        player.setMediaSource(mediaSource)

        adjustPreviousSpeedEdits(isStartInBuffer, isEndInBuffer)
        isEditOnGoing = false
        startingTimestamps = -1
        endingTimestamps = -1
    }

    /**
     * Accepts the current speed changes made and also ensure playback happens to reflect the same.
     */
    private fun acceptSpeedChanges() {
        val currentPosition = if(player.currentWindowIndex == 0) player.currentPosition
            else bufferDuration + player.currentPosition

        if (!isEditExisting) {
            if (checkSegmentTaken(currentPosition) || tmpSpeedDetails == null)  //  checking to see if the end positions is acceptable and something is available
                return
        }

        var startWindow = tmpSpeedDetails?.startWindowIndex ?: 0
        var endWindow = tmpSpeedDetails?.endWindowIndex ?: 0

        if (editSeekAction == EditSeekControl.MOVE_END) {
            if (endingTimestamps != currentPosition) {
                endingTimestamps = currentPosition
                endWindow = player.currentWindowIndex
            }
        } else {
            if (startingTimestamps != currentPosition) {
                startingTimestamps = currentPosition
                startWindow = player.currentWindowIndex
            }
        }
        //  durations are correct and changes can be accepted

        speedDetailSet.remove(tmpSpeedDetails)

        if(progressTracker != null)
            progressTracker?.removeSpeedDetails(tmpSpeedDetails!!)

        tmpSpeedDetails = null

        if(endingTimestamps < startingTimestamps)
            endingTimestamps += bufferDuration

        if(startWindow == 1){
            startingTimestamps += bufferDuration
        }

        speedDuration = Pair(startingTimestamps, endingTimestamps)
        speedDetailSet.add(SpeedDetails(
                startWindowIndex = startWindow,
                endWindowIndex   = endWindow,
                isFast           = editAction== EditAction.FAST,
                multiplier       = getCurrentEditSpeed(),
                timeDuration     = speedDuration))

        if(progressTracker == null)
            progressTracker = ProgressTracker(player)
        with(progressTracker!!) {
            setChangeAccepted(true)
            setSpeed(getCurrentEditSpeed().toFloat())
            setSpeedDetails(speedDetailSet.toMutableList() as ArrayList<SpeedDetails>)
            run()
        }

        isEditOnGoing = false
        isSpeedChanged = true


        val startValue = (startingTimestamps * 100 / maxDuration).toFloat()
        val endValue = (endingTimestamps * 100 / maxDuration).toFloat()

        setupRangeMarker(startValue, endValue)
        fixRangeMarker(startValue, endValue)

        restrictList = speedDetailSet.toList()
        restrictList?.sortedWith(speedDetailsComparator)

        if (editListAdapter == null) {
            setupEditList()
        } else {
            updateEditList()
        }

        slideDownAnimation(changeList)
    }

    /**
     * adds the buffed content for playback
     *
     * @param videoPathProcessed String? content to be added as buffer
     */
    private suspend fun addToVideoPlayback(videoPathProcessed: String?) {
        videoPathProcessed?.let{
            withContext(Main) {
                bufferPath = it
                showBuffer = true
                player.playWhenReady = false
                player.release()
                clearSelectedRanges()
                setupPlayer()

                isEditOnGoing      = true
                isEditExisting     = false
                editAction         = EditAction.EXTEND_TRIM
                editSeekAction     = EditSeekControl.MOVE_START
                trimSegment        = RangeSeekbarCustom(requireContext())
                startingTimestamps = if(editedStart < 0) bufferDuration else editedStart
                endingTimestamps   = if(editedEnd < 0) bufferDuration + videoDuration else editedEnd

                val startValue     = (startingTimestamps * 100 / (bufferDuration + videoDuration)).toFloat()
                val endValue       = (endingTimestamps * 100 / (bufferDuration + videoDuration)).toFloat()
                extendRangeMarker(startValue, endValue)
            }
        }
    }

    /**
     * removes any previous selections from the UI
     */
    private fun clearSelectedRanges() {
        uiRangeSegments?.forEach {
            timebarHolder.removeView(it)
        }
    }

    /**
     * Handles changing an ongoing speed edit
     *
     * @param currentPosition Long
     * @param isFast Boolean
     */
    private fun handleExistingSpeedChange(currentPosition: Long, isFast: Boolean) {
        if (tmpSpeedDetails != null) {
            val startWindow = tmpSpeedDetails?.startWindowIndex ?: 0
            val endWindow = tmpSpeedDetails?.endWindowIndex ?: 0

            speedDetailSet.remove(tmpSpeedDetails)
            if (editSeekAction == EditSeekControl.MOVE_END &&
                    currentPosition != maxDuration) {
                endingTimestamps = currentPosition
            } else if (editSeekAction == EditSeekControl.MOVE_START &&
                    currentPosition != 0L) {
                startingTimestamps = currentPosition
            }

            tmpSpeedDetails = SpeedDetails(
                    startWindowIndex = startWindow,
                    endWindowIndex   = endWindow,
                    isFast           = isFast,
                    multiplier       = getCurrentEditSpeed(),
                    timeDuration     = Pair(startingTimestamps, endingTimestamps))
            speedDetailSet.add(tmpSpeedDetails!!)

    //                    uiRangeSegments?.removeAt(currentEditSegment)
            val startValue = (startingTimestamps * 100 / maxDuration).toFloat()
            val endValue = (endingTimestamps * 100 / maxDuration).toFloat()
            setupRangeMarker(startValue, endValue)
        }
    }

    /**
     * speed change clicked for new edit. (Not modification of existing edit)
     *
     * @param currentPosition Long
     */
    private fun handleNewSpeedChange(currentPosition: Long) {
        speedDetailSet.forEach {
            val correctBy =
                if (it.startWindowIndex != it.endWindowIndex)   //  correction is required if the previous ranges span across two clips
                    bufferDuration
                else if(it.startWindowIndex == 1) bufferDuration else 0L

            if(correctBy != 0L){
                if(player.currentWindowIndex == 1 && currentPosition in (0 .. (it.timeDuration?.second!! - bufferDuration))){
                    Toast.makeText(requireContext(), "Cannot choose existing segment", Toast.LENGTH_SHORT).show()
                    return
                }
            }else {
                if (currentPosition in (it.timeDuration?.first!! - correctBy)..(it.timeDuration?.second!! - correctBy) &&
                    (it.startWindowIndex == player.currentWindowIndex || it.endWindowIndex == player.currentWindowIndex)) {
                    Toast.makeText(requireContext(), "Cannot choose existing segment", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        setupForEdit()
        segmentCount += 1   //  a new segment is active
        currentEditSegment += 1

        if (uiRangeSegments == null)
            uiRangeSegments = arrayListOf()

        if (segmentCount > uiRangeSegments?.size ?: 0) {
            uiRangeSegments?.add(RangeSeekbarCustom(requireContext()))
        }
    }

    /**
     * resets the UI to playback the video inclusive of any edits made
     */
    private fun resetPlaybackUI() {
        speedIndicator.visibility     = View.GONE
        playCon1.visibility           = View.GONE
        changeList.visibility         = View.GONE
        acceptRejectHolder.visibility = View.INVISIBLE
        playCon2.visibility           = View.VISIBLE
        editAction                    = EditAction.NORMAL

        player.seekTo(0,0)
        seekBar.showScrubber()
        player.playWhenReady = false

        blockEditOptions(false)
    }

    /**
     * shows the edits as a clickable list
     */
    private fun setupEditList(){
        val editList  = arrayListOf<SpeedDetails>()
        val tmpList = arrayListOf<SpeedDetails>()
        tmpList.addAll(speedDetailSet.toMutableList())
        editList.addAll(tmpList.sortedWith(speedDetailsComparator))
        editListAdapter = EditChangeListAdapter(requireContext(), editList)
        editListAdapter?.setEditPressListener(this@VideoEditingFragment)
        changeList.adapter = editListAdapter
        changeList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        Log.d(TAG, "setupEditList: list created")
    }

    /**
     * Updates the edits list
     */
    private fun updateEditList(){
        if(changeList.visibility != View.VISIBLE) {
            slideUpAnimation(changeList)
//            changeList.visibility = View.VISIBLE
        }
        val tmpList = arrayListOf<SpeedDetails>()
        tmpList.addAll(speedDetailSet.toMutableList())
        editListAdapter?.updateList(tmpList.sortedWith(speedDetailsComparator))
        Log.d(TAG, "updateEditList: list updated")
    }

    /**
     * Sets up the UI and flags for editing
     */
    private fun setupForEdit() {
        speedIndicator.visibility = View.VISIBLE
        playCon1.visibility       = View.VISIBLE
        playCon2.visibility       = View.GONE
        isEditOnGoing             = true
        player.playWhenReady      = false

        blockEditOptions(true)
        editSeekAction     = EditSeekControl.MOVE_START
        startingTimestamps = -1L
        endingTimestamps   = maxDuration

        if(editListAdapter != null){    //  already some edit was saved here
            slideUpAnimation(changeList)
//            changeList.visibility = View.VISIBLE
        }
    }

    private fun blockEditOptions(shouldBlock: Boolean) {
        val enable                 = !shouldBlock
        extentTextBtn.isEnabled    = enable
        cutTextBtn.isEnabled       = enable
        highlightTextBtn.isEnabled = enable

        extentTextBtn.isClickable    = enable
        cutTextBtn.isClickable       = enable
        highlightTextBtn.isClickable = enable

        if (enable) {
            extentTextBtn.alpha    = 1.0F
            cutTextBtn.alpha       = 1.0F
            highlightTextBtn.alpha = 1.0F
        } else {
            extentTextBtn.alpha    = 0.5F
            cutTextBtn.alpha       = 0.5F
            highlightTextBtn.alpha = 0.5F
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
     * checks if the segment is already taken
     * @param currentPosition takes in the current position of the video
     * @return true if the segment is already taken, false if available
     */
    private fun checkSegmentTaken(currentPosition: Long): Boolean {
        if (speedDetailSet.size > 0) {
            speedDetailSet.forEach {
                val correctBy =
                    if (it.startWindowIndex != it.endWindowIndex)   //  correction is required if the previous ranges span across two clips
                        bufferDuration
                    else if(it.startWindowIndex == 1) bufferDuration else 0L

                if(correctBy != 0L){
                    if(player.currentWindowIndex == 1 && currentPosition in (0 .. (it.timeDuration?.second!! - bufferDuration))){
                        Toast.makeText(requireContext(), "Cannot choose existing segment", Toast.LENGTH_SHORT).show()
                        return true
                    }
                }else {
                    if (currentPosition in it.timeDuration?.first!! until it.timeDuration?.second!! &&
                        (it.startWindowIndex == player.currentWindowIndex || it.endWindowIndex == player.currentWindowIndex)
                    ) {
                        Toast.makeText(requireContext(), "segment is already taken", Toast.LENGTH_SHORT).show()
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Sets up the range marker for displaying the selected ranges.
     *
     * creates a rangeSeekBar with parameters based on @param startValue and @param endValue
     * and the details present in the speedDetails sorted set
     */
    private fun setupRangeMarker(startValue: Float, endValue: Float) {

        if (uiRangeSegments == null) {
            uiRangeSegments = arrayListOf()
            uiRangeSegments?.add(RangeSeekbarCustom(requireContext()))
            val layoutParam = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            uiRangeSegments!![currentEditSegment].layoutParams = layoutParam
        }

        val (colour, height, padding) = setupCommonRangeUiElements()

        val leftThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
                resources.getColor(android.R.color.holo_green_dark, context?.theme))
        val rightThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
                resources.getColor(android.R.color.holo_red_light, context?.theme))


        uiRangeSegments!![currentEditSegment].apply {
            minimumHeight = height
            elevation = 1F
            setPadding(padding, 0, padding, 0)
            setBarColor(resources.getColor(android.R.color.transparent, context?.theme))
            setBackgroundResource(R.drawable.range_background)
            setBarHighlightColor(colour)
            setLeftThumbColor(colour)
            setRightThumbColor(colour)
            setLeftThumbBitmap(leftThumbImageDrawable)
            setRightThumbBitmap(rightThumbImageDrawable)
            setMinValue(0F)
            setMaxValue(100F)
            setMinStartValue(startValue).apply()
//            setGap(endValue - startValue)
            setMaxStartValue(endValue).apply()

            setOnTouchListener { _, _ -> true }
        }
    }

    /**
     * Sets up the final UI for the range marker once editing is done
     *
     * @param startValue Float
     * @param endValue Float
     */
    private fun fixRangeMarker(startValue: Float, endValue: Float) {
        val (colour, height, padding) = setupCommonRangeUiElements()

        val thumbDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb_transparent, context?.theme) as VectorDrawable,
                resources.getColor(android.R.color.transparent, context?.theme))

        uiRangeSegments!![currentEditSegment].apply {
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
            setMinStartValue(startValue).apply()
            setMaxStartValue(endValue).apply()

            setOnTouchListener { _, _ -> true }
        }
    }

    /**
     * sets up the extend/trim markers and adds it to timebarHolder
     *
     * @param startValue Float
     * @param endValue Float
     */
    private fun extendRangeMarker(startValue: Float, endValue: Float) {
        val colour = resources.getColor(android.R.color.transparent, context?.theme)
        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()
        val leftThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
                resources.getColor(android.R.color.holo_green_dark, context?.theme))
        val rightThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
                resources.getColor(android.R.color.holo_red_light, context?.theme))

        trimSegment!!.apply {
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
     * Sets up the common ui parameters for range bar
     * */
    private fun setupCommonRangeUiElements(): Triple<Int, Int, Int> {
        val colour =
                if (!speedDetailSet.isNullOrEmpty()) {
                    if (speedDetailSet/*.sortedWith { s1, s2 ->
                                (s1.timeDuration?.first!! - s2?.timeDuration!!.first).toInt()
                            }*/.toList()[currentEditSegment].isFast)
                        resources.getColor(R.color.blueOverlay, context?.theme)
                    else
                        resources.getColor(R.color.greenOverlay, context?.theme)
                } else
                    resources.getColor(android.R.color.transparent, context?.theme)

        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()
        return Triple(colour, height, padding)
    }

    private fun changeSpeedText(): CharSequence? {
        return when (currentSpeed) {
            1 -> {
                currentSpeed = 3; "3X"
            }
            3 -> {
                currentSpeed = 4; "4X"
            }
            4 -> {
                currentSpeed = 5; "5X"
            }
            5 -> {
                currentSpeed = 10; "10X"
            }
            10 -> {
                currentSpeed = 15; "15X"
            }
            else -> {
                currentSpeed = 1; "1X"
            }
        }
    }

    private fun getCurrentEditSpeed(): Int = Integer.parseInt(speedIndicator.text.toString().substringBefore('X'))

    /**
     * Setting up the player to play the require snip for editing
     */
    private fun setupPlayer() {
        val videoUri = Uri.parse(snip!!.videoFilePath)
        val mediaItem = MediaItem.fromUri(videoUri)

        previewBarProgress.visibility = View.VISIBLE

        if(!thumbnailExtractionStarted) {
            CoroutineScope(Default).launch {
                getVideoPreviewFrames()
            }
        }else {
            val parentFilePath = File(snip!!.videoFilePath).parent
            showThumbnailsIfAvailable(File("$parentFilePath/previewThumbs/"))
        }

        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player

        if(!showBuffer) {
            if (snip!!.is_virtual_version == 1) {   // Virtual versions only play part of the media
                val defaultBandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()
                val dataSourceFactory = DefaultDataSourceFactory(requireContext(),
                        Util.getUserAgent(requireActivity(), "mediaPlayerSample"), defaultBandwidthMeter)

                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(snip!!.videoFilePath))

                val clippingMediaSource = ClippingMediaSource(mediaSource, TimeUnit.SECONDS.toMicros(snip!!.start_time.toLong()), TimeUnit.SECONDS.toMicros(snip!!.end_time.toLong()))
                seekBar.setDuration(snip!!.snip_duration.toLong() * 1000)
                player.addMediaSource(clippingMediaSource)
            } else {
                player.setMediaItem(mediaItem)
            }
        }else{
            if(bufferPath.isNotEmpty()) {
                val bufferSource = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext())).createMediaSource(MediaItem.fromUri(Uri.parse(bufferPath)))
                val videoSource = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext())).createMediaSource(MediaItem.fromUri(Uri.parse(snip!!.videoFilePath)))
                //  clippingMediaSource used as workaround for timeline scrubbing
                val clip1 = ClippingMediaSource(bufferSource, 0, TimeUnit.MILLISECONDS.toMicros(bufferDuration))
                val clip2 = ClippingMediaSource(videoSource, 0, TimeUnit.MILLISECONDS.toMicros(videoDuration))
                val mediaSource = ConcatenatingMediaSource(true, clip1, clip2)

                player.setMediaSource(mediaSource)
                playerView.setShowMultiWindowTimeBar(true)
                val jumpTo = if(editedStart < 0) bufferDuration else editedStart
                player.seekTo(0, jumpTo)
                showBufferOverlay()
            }
        }

        player.prepare()
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        Log.d(TAG, "setupPlayer: content duration = ${player.contentDuration}, duration = ${maxDuration}")

        playerView.apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            controllerAutoShow = false
            controllerShowTimeoutMs = -1
            controllerHideOnTouch = false
            setShutterBackgroundColor(Color.TRANSPARENT)    // removes the black screen when seeking or switching media
            setShowMultiWindowTimeBar(showBuffer)
            showController()
        }

        player.playWhenReady = false

        player.addListener(object : Player.EventListener {
            override fun onPlayerError(error: ExoPlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.message}")
                error.printStackTrace()
                tries++
                val fragManager = requireActivity().supportFragmentManager
                if (snip?.videoFilePath.isNotNullOrEmpty() && tries < retries) { //  retry in case of errors
                    CoroutineScope(Main).launch {
                        delay(500)
                        val frag = fragManager.findFragmentByTag(AppMainActivity.EDIT_VIDEO_TAG)
                        requireActivity().supportFragmentManager.beginTransaction()
                                .detach(frag!!)
                                .attach(frag)
                                .commit()
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    maxDuration = 0L
                    val currentTimeline = player.currentTimeline
                    for (i in 0 until currentTimeline.windowCount) {
                        val window = Timeline.Window()
                        currentTimeline.getWindow(i, window)
                        maxDuration += window.durationMs
                    }
                    if (showBuffer) {
                        seekBar.setDuration(maxDuration)
                        if(previousMaxDuration != maxDuration)
                            previousMaxDuration = maxDuration
                    }
                    Log.d(TAG, "onPlaybackStateChanged: prev duration = $previousMaxDuration, max duration = $maxDuration")
                }
            }
        })

        initSwipeControls()
    }

    /**
     * shows the overlay for the buffer on the timeline
     */
    private fun showBufferOverlay() {
        //todo: adjust or hide other changed ranges
        val colour = resources.getColor(R.color.blackOverlay, context?.theme)
        val (_, height, padding) = setupCommonRangeUiElements()

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

    private fun showDialogConfirmation() {
        if(exitConfirmation == null){
            exitConfirmation = ExitEditConfirmationDialog(this@VideoEditingFragment)
        }
        exitConfirmation!!.show(requireActivity().supportFragmentManager, EXIT_CONFIRM_DIALOG)

        /*val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.warningdialog_savevideodiscardchanges)
        dialog.show()*/
    }

    private fun showDialogSave() {
        if(saveDialog == null)
            saveDialog = SaveEditDialog(this@VideoEditingFragment)

        saveDialog!!.show(requireActivity().supportFragmentManager, SAVE_DIALOG)
    }

    private fun showDialogDelete() {
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.warningdialog_deletevideo)
        dialog.show()
    }

    /**
     * Controls the behaviour during swiping actions both during normal seeking and editing
     */
    private fun initSwipeControls() {
        var startScrollingSeekPosition = 0L
        var higher = maxDuration
        var lower = 0L

        swipeDetector.setOnClickListener {
            if (playerView.isControllerVisible)
                playerView.hideController()
            else
                playerView.showController()
        }

        swipeDetector.onIsScrollingChanged {
            if (it) {
                startScrollingSeekPosition = player.currentPosition
            }
            player.playWhenReady = !paused
        }

        val emitter = SeekPositionEmitter()
        player.seekWhenReady(emitter)
                .subscribe({
                    Log.v(TAG, "seekTo=${it.first} isSeeking=${it.second}")
                }, { Log.e(TAG, "${it.message}") })
                .addTo(subscriptions)

        swipeDetector.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }

        swipeDetector.onScroll { percentX, _ ->
            // left swipe is positive, right is negative

            val maxPercent = 0.75f
            val scaledPercent = percentX * maxPercent
            val percentOfDuration = /*if (showBuffer) {
                if (player.currentWindowIndex == 0)
                    scaledPercent * -1 * bufferDuration + startScrollingSeekPosition
                else
                    scaledPercent * -1 * videoDuration + startScrollingSeekPosition
            } else*/
                scaledPercent * -1 * maxDuration + startScrollingSeekPosition

            // shift in position domain and ensure circularity
            var newSeekPosition = percentOfDuration.roundToLong()
            /*((percentOfDuration + duration) % duration).roundToLong().absoluteValue*/

            if (restrictList.isNullOrEmpty() || restrictList?.size!! < speedDetailSet.size) {
                restrictList = speedDetailSet.toList()
                restrictList!!.sortedWith(speedDetailsComparator)
            }

            higher = if(showBuffer) maxDuration else nearestExistingHigherTS(player.currentPosition)
            lower = if(showBuffer) 0 else nearestExistingLowerTS(player.currentPosition)

            if (isEditOnGoing && editAction != EditAction.NORMAL) {
                if (isSeekbarShown) {
                    seekBar.hideScrubber()
                    isSeekbarShown = false
                }

                if (editAction == EditAction.FAST || editAction == EditAction.SLOW) {
                    when (editSeekAction) {
                        EditSeekControl.MOVE_START -> {
                            if (newSeekPosition >= endingTimestamps && endingTimestamps != 0L && !showBuffer) {
                                newSeekPosition = endingTimestamps
                            } else {
                                // prevent the user from seeking beyond the fixed start point
                                if (newSeekPosition <= lower) {
                                    newSeekPosition = lower
                                    seekBar.hideScrubber()
                                    isSeekbarShown = false
                                } else {
                                    if (!isSeekbarShown) {
                                        seekBar.showScrubber()
                                        isSeekbarShown = true
                                    }
                                }
                            }
                            if(player.currentWindowIndex == 1)
                                uiRangeSegments!![currentEditSegment].setMinStartValue(((bufferDuration + newSeekPosition) * 100 / maxDuration).toFloat()).apply()
                            else
                                uiRangeSegments!![currentEditSegment].setMinStartValue((newSeekPosition * 100 / maxDuration).toFloat()).apply()
                        }
                        EditSeekControl.MOVE_END -> {
                            if (newSeekPosition < startingTimestamps && !showBuffer) {
                                newSeekPosition = startingTimestamps
                            }
                            if (newSeekPosition > higher)
                                newSeekPosition = higher
                            if(player.currentWindowIndex == 1)
                                uiRangeSegments!![currentEditSegment].setMaxStartValue(((bufferDuration + newSeekPosition) * 100 / maxDuration).toFloat()).apply()
                            else
                                uiRangeSegments!![currentEditSegment].setMaxStartValue((newSeekPosition * 100 / maxDuration).toFloat()).apply()
                        }
                        else -> {
                        }
                    }
                    Log.d(TAG, "initSwipeControls: \nlower = $lower\nhigher = $higher\nstartingTS = $startingTimestamps\nendingTS = $endingTimestamps" +
                            "\nnew position = $newSeekPosition\nmin start = ${uiRangeSegments!![currentEditSegment].selectedMinValue}\nmax start = ${uiRangeSegments!![currentEditSegment].selectedMaxValue}")
                }
            }

            if (!isSeekbarShown && !isEditOnGoing) {
                seekBar.showScrubber()
                isSeekbarShown = true
            }

            if(showBuffer) {
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
            }

            if (editAction == EditAction.EXTEND_TRIM) {
                when (editSeekAction) {
                    EditSeekControl.MOVE_START -> {
                        if (getCorrectedTimebarStartPosition() > endingTimestamps) {
                            newSeekPosition = endingTimestamps
                        } else {
                            trimSegment!!.setMinStartValue((getCorrectedTimebarStartPosition() * 100 / maxDuration).toFloat()).apply()
                            startingTimestamps = getCorrectedTimebarStartPosition()
                        }
                    }
                    EditSeekControl.MOVE_END -> {
                        if(getCorrectedTimebarEndPosition() < startingTimestamps){
                            newSeekPosition = startingTimestamps
                        }else {
                            trimSegment!!.setMaxStartValue((getCorrectedTimebarEndPosition() * 100 / maxDuration).toFloat()).apply()
                            endingTimestamps = getCorrectedTimebarEndPosition()
                        }
                    }
                    else -> {

                    }
                }
            }

            if (newSeekPosition < 0) {
                newSeekPosition = 0
            }else if (newSeekPosition > maxDuration) {
                newSeekPosition = maxDuration
            }

            player.seekTo(newSeekPosition)  //  window is chosen previously
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
     * Populates preview frames in the seekBar area from the video
     */
    private fun getVideoPreviewFrames() {
        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
                operation = IVideoOpListener.VideoOp.FRAMES,
                clips = arrayListOf(snip!!.videoFilePath),
                outputPath = File(snip!!.videoFilePath).parent!!,
                speedDetailsList = speedDetailSet.toMutableList() as ArrayList<SpeedDetails>,
                comingFrom = CurrentOperation.VIDEO_EDITING))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
    }

    companion object {
        const val PREVIEW_ACTION = "com.hipoint.snipback.previewTile"
        const val VIRTUAL_TO_REAL_ACTION = "com.hipoint.snipback.virtualToReal"
        const val EXTEND_TRIM_ACTION = "com.hipoint.snipback.extendTrim"
        private var tries = 0

        var saveAction: SaveActionType = SaveActionType.CANCEL
        var fragment: VideoEditingFragment? = null

        @JvmStatic
        fun newInstance(aSnip: Snip?, thumbnailExtractionStarted: Boolean): VideoEditingFragment {
            fragment = VideoEditingFragment()
            val bundle = Bundle()
            bundle.putParcelable("snip", aSnip)
            bundle.putBoolean("thumbnailExtractionStarted", thumbnailExtractionStarted)
            fragment!!.arguments = bundle
            return fragment!!
        }
    }

    private fun nearestExistingHigherTS(current: Long): Long {
        if(restrictList.isNullOrEmpty()){
            return maxDuration
        }

        val diff = arrayListOf<Long>()
        restrictList?.forEach {
            if(player.currentWindowIndex == it.startWindowIndex) {
                val one = it.timeDuration!!.first - current
                val two = it.timeDuration!!.second - current

                if (one > 0)
                    diff.add(one)
                if (two > 0)
                    diff.add(two)
            }
        }
        return if(diff.isEmpty()){
            maxDuration
        }else {
            diff.sort()
            diff[0] + current
        }
    }

    private fun nearestExistingLowerTS(current: Long): Long {
        if(restrictList.isNullOrEmpty()){
            return 0L
        }

        val diff = arrayListOf<Long>()
        restrictList?.forEach {
            if(player.currentWindowIndex == it.endWindowIndex) {
                val one = it.timeDuration!!.first - current
                val two = it.timeDuration!!.second - current
                if (one < 0)
                    diff.add(one)
                if (two < 0)
                    diff.add(two)
            }
        }

        return if(diff.isEmpty()){
            0
        }else {
            diff.sortDescending()
            current + diff[0]   //   + because the value is already negative
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

    /**
     * Creates a video with the required speed changes
     *
     * @param inputPath String Defaults to "snip!!.videoFilePath" to get the current playing video path
     * @return Pair<File, String>   Pair of <Parent file, output filename>
     */
    private fun createSpeedChangedVideo(inputPath: String = snip!!.videoFilePath): Pair<File, String> {
        val clip = File(inputPath)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputName = "VID_$timeStamp.mp4"
        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
                operation = IVideoOpListener.VideoOp.SPEED,
                clips = arrayListOf(clip.absolutePath),
                outputPath = "${clip.parent}/$outputName",
                speedDetailsList = speedDetailSet.toMutableList() as ArrayList<SpeedDetails>,
                comingFrom = CurrentOperation.VIDEO_EDITING))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
        return Pair(clip, outputName)
    }

    /**
     * Trims down the original video file to the required durations,
     * This is to be called before any edits are performed on the file.
     *
     * @param startTime Int
     * @param endTime Int
     */
    private fun makeVirtualReal(startTime: Int, endTime: Int){
        val clip = File(snip!!.videoFilePath)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputName = "VID_$timeStamp.mp4"
        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
                operation = IVideoOpListener.VideoOp.TRIMMED,
                clips = arrayListOf(clip.absolutePath),
                outputPath = "${clip.parent}/$outputName",
                startTime = startTime,
                endTime = endTime,
                comingFrom = CurrentOperation.VIDEO_EDITING))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
    }


    fun showProgress(){
        if(processingDialog == null)
            processingDialog = ProcessingDialog()
        processingDialog!!.isCancelable = false
        processingDialog!!.show(requireActivity().supportFragmentManager, PROCESSING_DIALOG)
    }

    fun hideProgress(){
        processingDialog?.dismiss()
    }

    fun confirmExitOnBackPressed(){
        if(speedDetailSet.isNotEmpty()) //  todo: should work for all edits not just speed change
            showDialogConfirmation()
        else {
            isEditExisting = false
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private suspend fun showContentUnavailableToast() {
        withContext(Main) {
            Toast.makeText(requireContext(), "buffered content unavailable", Toast.LENGTH_SHORT).show()
            removeBufferOverlays()
            resetPlaybackUI()
        }
    }


//  implementations

    /**
     * our query result is avaialble here.
     *
     * @param hdSnips List<Hd_snips>?
     */
    override suspend fun queryResult(hdSnips: List<Hd_snips>?) {
        hdSnips?.let{
            if(it.size < 2){
                showContentUnavailableToast()
                return@let
            }

            val sorted = it.sortedBy { hdSnips -> hdSnips.video_path_processed.toLowerCase() }

            sorted.forEach { item -> Log.d(TAG, "queryResult: ${item.video_path_processed}") }

            if(sorted[0].video_path_processed == sorted[1].video_path_processed){       //  there is no buffer
                showContentUnavailableToast()
                return@let
            }else {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(sorted[0].video_path_processed)
                    bufferDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                    retriever.setDataSource(snip!!.videoFilePath)
                    videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                    retriever.release()

                    if(bufferDuration > 0L && videoDuration > 0L)
                        addToVideoPlayback(sorted[0].video_path_processed)
                    else {
                        showContentUnavailableToast()
                    }

                }catch (e: IllegalArgumentException){
                    showContentUnavailableToast()
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * saves as a new file
     * */
    override fun saveAs() {
        saveAction = SaveActionType.SAVE_AS
        saveDialog?.dismiss()
        showProgress()
        //  show in gallery
        Log.d(TAG, "saveAs: will save as child of snip id = ${snip!!.snip_id}")
        replaceRequired.parent(snip!!.snip_id)

        var wasExtended = false
        editHistory.forEach {
            if(it == EditAction.EXTEND_TRIM) {
                wasExtended = true
                return@forEach
            }
        }
        if(!wasExtended) {
            if (snip!!.is_virtual_version == 1) {
                (requireActivity() as AppMainActivity).setVirtualToReal(true)
                makeVirtualReal(snip!!.start_time.toInt(), snip!!.end_time.toInt())
            } else {
                val (clip, outputName) = createSpeedChangedVideo()
                (requireActivity() as AppMainActivity).showInGallery.add(File("${clip.parent}/$outputName").nameWithoutExtension)
                Toast.makeText(requireContext(), "Saving Edited Video", Toast.LENGTH_SHORT).show()
            }
        }else{
            createModifiedVideo()
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
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoPath = snip!!.videoFilePath
        val concatOutputPath = "${File(videoPath).parent}/$timeStamp.mp4"

        val concatenateTask = VideoOpItem(
                operation = IVideoOpListener.VideoOp.CONCAT,
                clips = arrayListOf(bufferPath, snip!!.videoFilePath),
                outputPath = concatOutputPath,
                comingFrom = CurrentOperation.VIDEO_EDITING)

        val taskList = arrayListOf<VideoOpItem>()

        taskList.apply {
            add(concatenateTask)
        }
        VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.CONCAT)
        VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.TRIMMED)

        val createNewVideoIntent = Intent(requireContext(), VideoService::class.java)
        createNewVideoIntent.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, taskList)
        VideoService.enqueueWork(requireContext(), createNewVideoIntent)
    }

    /**
     * save over existing file
     * */
    override fun save() {
        saveAction = SaveActionType.SAVE
        saveDialog?.dismiss()
        showProgress()

        var wasExtended = false
        editHistory.forEach {
            if(it == EditAction.EXTEND_TRIM) {
                wasExtended = true
                return@forEach
            }
        }

        if(!wasExtended) {
            if(snip!!.is_virtual_version == 1){
                (requireActivity() as AppMainActivity).setVirtualToReal(true)
                makeVirtualReal(snip!!.start_time.toInt(), snip!!.end_time.toInt())
            }else {
                val (clip, outputName) = createSpeedChangedVideo()
                replaceRequired.replace(clip.absolutePath, "${clip.parent}/$outputName")
                Toast.makeText(requireContext(), "Saving Edited Video", Toast.LENGTH_SHORT).show()
            }
        }else{
            /*todo:
            *  trim buffer video and actual video
            *  concatenate trimmed videos
            *  make edits on the concatenated video
            * */
        }
    }

    /**
     * cancels the save action
     * */
    override fun cancel() {
        saveAction = SaveActionType.CANCEL
        saveDialog?.dismiss()
    }

    /**
     * exit from this fragment
     * */
    override fun exit() {
        saveAction = SaveActionType.CANCEL
        isEditExisting = false
        exitConfirmation?.dismiss()
        reject.performClick()
        requireActivity().supportFragmentManager.popBackStack()
    }

    /**
     * if there were any ongoing edits stop it
     * move the cursor to the starting point of the edit segment with start selected.
     * */
    override fun editPoint(position: Int, speedDetails: SpeedDetails) {
        isEditExisting = true

        if(uiRangeSegments?.size!! > speedDetailSet.size){
            val ref = uiRangeSegments?.removeAt(uiRangeSegments?.size!! - 1)    //  this needs to be called outside after checking size same as speedDetails
            timebarHolder.removeView(ref)
            tmpSpeedDetails = null
            segmentCount -= 1
        }

        tmpSpeedDetails = speedDetails

        player.setPlaybackParameters(PlaybackParameters(speedDetails.multiplier.toFloat()))
        speedDetailSet.forEachIndexed { index, it ->
            if(it.timeDuration!!.first == speedDetails.timeDuration!!.first && it.timeDuration!!.second == speedDetails.timeDuration!!.second){
                currentEditSegment = index
            }
        }
//        currentEditSegment = position
        if(tmpSpeedDetails!!.startWindowIndex == 1) {
            player.seekTo(tmpSpeedDetails!!.startWindowIndex, tmpSpeedDetails!!.timeDuration!!.first - bufferDuration)
        }else{
            player.seekTo(tmpSpeedDetails!!.startWindowIndex, tmpSpeedDetails!!.timeDuration!!.first)
        }
        start.performClick()
        startingTimestamps = speedDetails.timeDuration!!.first
        endingTimestamps = speedDetails.timeDuration!!.second
        editAction = if(speedDetails.isFast) EditAction.FAST else EditAction.SLOW
        acceptRejectHolder.visibility = View.VISIBLE
    }

//  Animations
    /**
     * Slides up the passed in view
     *
     * @param view View
     */
    private fun slideUpAnimation(view: View){
        val animation = AnimationSet(true)

        val slideAnimation = TranslateAnimation(
                0.0F,
                0.0F,
                0.0F,
                view.height.toFloat())
        slideAnimation.duration = 700
        slideAnimation.fillAfter = true

        val alphaAnimation = AlphaAnimation(0.0F, 1.0F)
        alphaAnimation.startOffset = 500
        alphaAnimation.duration = 200
        alphaAnimation.fillAfter

        animation.addAnimation(slideAnimation)
        animation.addAnimation(alphaAnimation)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                view.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animation?) {

            }

            override fun onAnimationRepeat(animation: Animation?) {

            }
        })
        view.startAnimation(animation)
    }

    /**
     * Slides down the passed in view
     *
     * @param view View
     */
    private fun slideDownAnimation(view: View){
        val animation = AnimationSet(true)

        val slideAnimation = TranslateAnimation(
                0.0F,
                0.0F,
                0.0F,
                view.height.toFloat())
        slideAnimation.duration = 700
        slideAnimation.fillAfter = true

        val alphaAnimation = AlphaAnimation(1.0F, 0.0F)
        alphaAnimation.duration = 200
        alphaAnimation.fillAfter = true

        animation.addAnimation(slideAnimation)
        animation.addAnimation(alphaAnimation)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {

            }

            override fun onAnimationEnd(animation: Animation?) {
                view.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation?) {

            }
        })
        view.startAnimation(animation)
    }

//    Inner classes

    enum class SaveActionType{
        SAVE, SAVE_AS, CANCEL
    }

    inner class ProgressTracker(private val player: Player) : Runnable {

        private var handler: Handler? = null
        private var speedDetailList: ArrayList<SpeedDetails> = arrayListOf()

        private var currentSpeed: Float = 1F
        private var isChangeAccepted: Boolean = false

        override fun run() {
            if(context != null) {
                if (isChangeAccepted) { //  Edit is present
                              val currentPosition = if(player.currentWindowIndex == 0)
                        player.currentPosition
                    else {
                        val adjust = player.currentTimeline.getWindow(0, Timeline.Window()).durationMs
                        player.currentPosition + adjust
                    }
                    speedDetailList.forEach {
                        if (currentPosition in it.timeDuration!!.first..it.timeDuration!!.second) {
                            if (it.isFast) {
                                val overlayColour = resources.getColor(R.color.blueOverlay, requireContext().theme)
                                if (!colourOverlay.isShown) {
                                    colourOverlay.visibility = View.VISIBLE
                                }
                                colourOverlay.setBackgroundColor(overlayColour)
                                player.setPlaybackParameters(PlaybackParameters(it.multiplier.toFloat()))
                            } else {
                                val overlayColour = resources.getColor(R.color.greenOverlay, requireContext().theme)
                                if (!colourOverlay.isShown) {
                                    colourOverlay.visibility = View.VISIBLE
                                }
                                colourOverlay.setBackgroundColor(overlayColour)
                                player.setPlaybackParameters(PlaybackParameters(1 / it.multiplier.toFloat()))
                            }

                            handler?.postDelayed(this, 200 /* ms */)
                            return
                        } else {
                            if (player.playbackParameters != PlaybackParameters(1F))
                                player.setPlaybackParameters(PlaybackParameters(1F))
                            else {
                                colourOverlay.visibility = View.GONE
                            }
                        }
                    }
                } else {
                    if (player.playbackParameters != PlaybackParameters(1F))
                        player.setPlaybackParameters(PlaybackParameters(1F))
                    else {
                        colourOverlay.visibility = View.GONE
                    }
                }
                handler?.postDelayed(this, 200 /* ms */)
            }
        }

        init {
            handler = Handler()
            handler?.post(this)
        }

        fun setSpeed(speed: Float) {
            currentSpeed = speed
        }

        fun setChangeAccepted(isAccepted: Boolean) {
            isChangeAccepted = isAccepted
        }

        fun setSpeedDetails(speedDetails: ArrayList<SpeedDetails>) {
//            speedDetailList.addAll(speedDetails)
            speedDetailList = speedDetails
            speedDetailList.sortWith(speedDetailsComparator)
        }

        fun removeSpeedDetails(speedDetail: SpeedDetails): Boolean {
            return if (speedDetailList.contains(speedDetail)) {
                speedDetailList.remove(speedDetail)
            } else
                false
        }

        fun stopTracking(){
            handler?.removeCallbacksAndMessages(null)
            handler = null
        }
    }

}