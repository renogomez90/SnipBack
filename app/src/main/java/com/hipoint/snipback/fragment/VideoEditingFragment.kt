package com.hipoint.snipback.fragment

import android.app.Dialog
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
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityOptionsCompat
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
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.RangeSeekbarCustom
import com.hipoint.snipback.Utils.SimpleOrientationListener
import com.hipoint.snipback.Utils.SnipbackTimeBar
import com.hipoint.snipback.Utils.milliToFloatSecond
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
import com.hipoint.snipback.service.VideoService
import com.hipoint.snipback.videoControl.SpeedDetails
import com.hipoint.snipback.videoControl.VideoOpItem
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_main2.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.Comparator
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
    private lateinit var extendTextBtn     : TextView          //  extend or trim the video
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
    private lateinit var seekBar           : SnipbackTimeBar
    private lateinit var timebarHolder     : FrameLayout
    private lateinit var colourOverlay     : LinearLayout
    private lateinit var previewBarProgress: ProgressBar
    private lateinit var swipeDetector     : SwipeDistanceView //  detects swiping actions for scrolling with preview

    private lateinit var commonTransition  : ArrayList<androidx.core.util.Pair<View, String>>
    private lateinit var editControls      : LinearLayout
    private lateinit var layoutImages      : FrameLayout

    //horizontal line
    private lateinit var horizontalView    : View

    //    Exoplayer
    private lateinit var playerView: PlayerView
    private lateinit var player    : SimpleExoPlayer

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

    private var tmpSpeedDetails: SpeedDetails? = null

    //  extend/trim
    private var bufferPath      = ""
    private var bufferHdSnipId  = 0
    private var bufferDuration  = 0L
    private var videoDuration   = 0L
    private var showBuffer      = false
    private var trimOnly        = false
    private var isStartInBuffer = true
    private var isEndInBuffer   = false

    private var trimSegment: RangeSeekbarCustom? = null

    //  handling existing edits
    private var newSpeedChangeStart    = false
    private var originalBufferDuration = 0L
    private var originalVideoDuration  = 0L
    private var editHistory            = arrayListOf<EditAction>() //  list of edit actions that were performed

    //  once the user decides to save the video after trimming/extending
    private var editedStart    = -1L
    private var editedEnd      = -1L

    private var restoreCurrentWindow  = 0
    private var restoreCurrentPoint   = 0L
    private var maxDuration           = 0L
    private var previousMaxDuration   = 0L
    private var previousEditStart     = -1L
    private var previousEditEnd       = -1L
    private var previousStartInBuffer = true
    private var previousEndInBuffer   = false
    private var currentSpeed          = 3
    private var startingTimestamps    = -1L
    private var endingTimestamps      = -1L
    private var segmentCount          = 0
    private var speedDuration         = Pair<Long, Long>(0, 0)
    private var editAction            = EditAction.NORMAL
    private var editSeekAction        = EditSeekControl.MOVE_NORMAL
    private var currentEditSegment    = -1

    //  dialogs
    private var saveDialog      : SaveEditDialog?             = null
    private var exitConfirmation: ExitEditConfirmationDialog? = null
    private var processingDialog: ProcessingDialog?           = null

    private var thumbnailExtractionStarted: Boolean = false
    private var generatePreviewTile       : Boolean = true

    private var timeStamp: String? = null

    private val replaceRequired: IReplaceRequired by lazy { requireActivity() as AppMainActivity }
    private val bufferOverlay  : RangeSeekbarCustom by lazy { RangeSeekbarCustom(requireContext()) }

    /**
     * The first time this is triggered is after the CONCAT is completed.
     * the received inputFile is then enqueued for trimming as buffer and required video.
     *
     * This happens in 2 stages: TRIMMED may be entered twice
     * step 1
     * if the buffer is unavailable or the video is being saved with saveAction == SaveActionType.SAVE_AS
     * we can proceed to just trim the original video.
     * else we trim the buffered file.
     *
     * step 2
     * replace is set up if it is required
     * required video is enqueued for trimming
     *
     * Once this is completed the speed changes are triggered
     */
    private val extendTrimReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        var trimmedItemCount = 0
        var concatOutput: String =""
        var fullExtension = false

        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let{
                val retriever = MediaMetadataRetriever()
                val operation = it.getStringExtra("operation")
                val inputName = it.getStringExtra("fileName")
                val trimmedOutputPath = "${File(inputName!!).parent}/trimmed-$timeStamp.mp4"
                val speedChangedPath = "${File(inputName).parent}/VID_$timeStamp.mp4"

                retriever.setDataSource(inputName)

                val taskList = arrayListOf<VideoOpItem>()

                if(operation == IVideoOpListener.VideoOp.CONCAT.name){  //  concat is completed, trim is triggered
                    val concatDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                    Log.d(TAG, "onReceive: CONCAT duration = $concatDuration")
                    concatOutput = inputName
                    fullExtension = (editedStart == 0L)

                    if(fullExtension){
                        editedStart = 100L
                    }

                    if(saveAction == SaveActionType.SAVE && bufferPath.isNotNullOrEmpty()) {    //  buffer
                        val bufferTask = VideoOpItem(
                                operation = IVideoOpListener.VideoOp.TRIMMED,
                                clips = arrayListOf(inputName),
                                startTime = 0F,
                                endTime = editedStart.milliToFloatSecond(),
                                outputPath = bufferPath,
                                comingFrom = CurrentOperation.VIDEO_EDITING)

                        taskList.add(bufferTask)
                    } else {    //  video without affecting buffer
                        val trimTask = VideoOpItem(
                                operation = IVideoOpListener.VideoOp.TRIMMED,
                                clips = arrayListOf(inputName),
                                startTime = editedStart.milliToFloatSecond(),
                                endTime = editedEnd.milliToFloatSecond(),
                                outputPath = trimmedOutputPath,
                                comingFrom = CurrentOperation.VIDEO_EDITING)

                        taskList.add(trimTask)
                    }
                }else if(operation == IVideoOpListener.VideoOp.TRIMMED.name) {  //  trim is completed
                    Log.d(TAG, "onReceive: TRIM DONE")
                    trimmedItemCount++

                    if(saveAction == SaveActionType.SAVE) {
//                        if(speedDetailSet.isNotEmpty())
                            replaceRequired.replace(snip!!.videoFilePath, speedChangedPath)
//                        else
//                            replaceRequired.replace(snip!!.videoFilePath, trimmedOutputPath)
                    }

                    if(saveAction == SaveActionType.SAVE && bufferPath.isNotNullOrEmpty() && trimmedItemCount == 1){
                        CoroutineScope(Default).launch{
                            //  update video in DB
                            snip!!.total_video_duration =
                                (editedEnd.milliToFloatSecond() - editedStart.milliToFloatSecond()).toInt()
                            snip!!.snip_duration =
                                (editedEnd.milliToFloatSecond() - editedStart.milliToFloatSecond()).toDouble()
                            appRepository.updateSnip(snip!!)
                        }
                        val trimTask = VideoOpItem(
                                operation = IVideoOpListener.VideoOp.TRIMMED,
                                clips = arrayListOf(concatOutput),
                                startTime = editedStart.milliToFloatSecond(),
                                endTime = editedEnd.milliToFloatSecond(),
                                outputPath = trimmedOutputPath,
                                comingFrom = CurrentOperation.VIDEO_EDITING)

                        taskList.add(trimTask)
                    }

                    if(trimmedItemCount == 2 || saveAction == SaveActionType.SAVE_AS || trimOnly) {
                        trimmedItemCount = 0

                        val trimmedDuration =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                .toLong()
                        Log.d(TAG, "onReceive: TRIMMED duration = $trimmedDuration")

                        val speedChangeTask = VideoOpItem(
                                operation = IVideoOpListener.VideoOp.SPEED,
                                clips = arrayListOf(inputName),
                                outputPath = speedChangedPath,
                                speedDetailsList = speedDetailSet.toMutableList() as ArrayList<SpeedDetails>,
                                comingFrom = CurrentOperation.VIDEO_EDITING)

                        taskList.add(speedChangeTask)
                    }
                }

                val createNewVideoIntent = Intent(requireContext(), VideoService::class.java)
                createNewVideoIntent.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM,
                        taskList)
                VideoService.enqueueWork(requireContext(), createNewVideoIntent)
                retriever.release()
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
                    SaveActionType.SAVE_AS,
                    -> {
                        val (clip, outputName) = createSpeedChangedVideo(realVideoPath!!)
                        AppClass.showInGallery.add(File("${clip.parent}/$outputName").nameWithoutExtension)
                        Toast.makeText(requireContext(), "Saving Edits", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "onReceive: real video output = $outputName")
                    }
                    else ->{}
                }
            }
        }
    }

    /**
     * Dismisses the progress dialog in edit fragment if available,
     * starts the FragmentPlayVideo2 with the edited video for playback
     *
     * @param processedVideoPath String - path of edited video to be played
     */
    private val progressDismissReceiver: BroadcastReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let{
                val processedVideoPath = intent.getStringExtra("processedVideoPath")

                hideProgress()

                if (processedVideoPath != null) {
                    CoroutineScope(IO).launch {
                        var snip: Snip? = null
                        var tries = 0
                        while (snip == null && tries < 5) {
                            delay(50)
                            snip = appRepository.getSnipByVideoPath(processedVideoPath)
                            tries++
                        }
                        withContext(Main) {
                            if (snip != null) {
                                (requireActivity() as AppMainActivity).loadFragment(
                                        FragmentPlayVideo2.newInstance(snip),
                                        true)
                            }
                        }
                    }
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

            override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent?,
                    distanceX: Float,
                    distanceY: Float,
            ): Boolean {
                if (e1 != null && e2 != null) {
                    val speed = (distanceX / (e2.eventTime - e1.eventTime)).absoluteValue
                    if (editAction == EditAction.EXTEND_TRIM)
                        player.setSeekParameters(SeekParameters.EXACT)
                    else {
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
                }
                return false
            }

            override fun onLongPress(e: MotionEvent?) {
            }

            override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent?,
                    velocityX: Float,
                    velocityY: Float,
            ): Boolean {
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
            previewTileList.layoutManager = LinearLayoutManager(requireContext(),
                    RecyclerView.HORIZONTAL,
                    false)
            //  change context for updating the UI
            timelinePreviewAdapter!!.setHasStableIds(true)
            previewTileList.adapter = timelinePreviewAdapter
            previewTileList.adapter?.notifyDataSetChanged()
            previewTileList.scrollToPosition(timelinePreviewAdapter!!.itemCount)
            previewBarProgress.visibility = View.GONE
            generatePreviewTile = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        if(savedInstanceState != null){
            restoreFromBundle(savedInstanceState)
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.video_editing_fragment_main2, container, false)
        snip = requireArguments().getParcelable("snip")
        thumbnailExtractionStarted = requireArguments().getBoolean("thumbnailExtractionStarted")
        saveAction = SaveActionType.CANCEL

        videoDuration = TimeUnit.SECONDS.toMillis(snip!!.snip_duration.toLong())
        originalVideoDuration = videoDuration
        maxDuration = videoDuration

        bindViews()
        bindListeners()
        setupPlayer()
        restorePreviousState()
        return rootView
    }

    private fun restorePreviousState() {
        //  restores the UI to match on going edit
        if(editHistory.isNotEmpty() && !isEditOnGoing){
            showAdjustedSpeedChanges()  //  just to show the existing speed change markers
            if(editHistory.contains(EditAction.EXTEND_TRIM))
                removeBufferOverlays()
        }
        if(isEditOnGoing){
            playCon1.visibility       = View.VISIBLE
            playCon2.visibility       = View.GONE

            if(editListAdapter != null){    //  already some edit was saved here
                slideUpAnimation(changeList)
            } else if(speedDetailSet.isNotEmpty() && !newSpeedChangeStart){
                setupEditList()
                slideUpAnimation(changeList)
            }

            if(isEditActionSpeedChange()) {
                //  when the edit has started but the end point is not yet fixed, the value is in uiRangeSegments but not in the speedDetails
                val tmpUiRangeSegment = if(uiRangeSegments!!.size >= speedDetailSet.size){
                    uiRangeSegments!![uiRangeSegments!!.size - 1]
                } else null

                showAdjustedSpeedChanges()
                /*if(newSpeedChangeStart) {
                    progressTracker?.setChangeAccepted(false)
                    progressTracker?.stopTracking()
                    progressTracker = null
                }*/
                if(tmpUiRangeSegment != null){
                    uiRangeSegments?.add(tmpUiRangeSegment)
                }

                speedIndicator.visibility = View.VISIBLE
                if(editSeekAction == EditSeekControl.MOVE_START){
                    if (tmpSpeedDetails != null) {
                        //  this means the end point was already set
                        //  the user wishes to move the starting point only
                        acceptRejectHolder.visibility = View.VISIBLE
                        if (startingTimestamps != -1L) {
                            if (tmpSpeedDetails?.startWindowIndex ?: 0 == 0 || trimOnly)
                                player.seekTo(0, startingTimestamps)
                            else
                                player.seekTo(1, startingTimestamps - bufferDuration)
                        }
                    }
                }
                if (editSeekAction == EditSeekControl.MOVE_END) {
                    start.setBackgroundResource(R.drawable.end_curve)
                    end.setBackgroundResource(R.drawable.end_curve_red)

                    acceptRejectHolder.visibility = View.VISIBLE

                    val endValue =
                        if (restoreCurrentWindow == 1) ((bufferDuration + restoreCurrentPoint) * 100 / maxDuration).toFloat()
                        else (restoreCurrentPoint * 100 / maxDuration).toFloat()
                    uiRangeSegments!![currentEditSegment].setMaxStartValue(endValue).apply()
                }

                if (isSeekbarShown && !isEditExisting) {
                    seekBar.hideScrubber()
                    isSeekbarShown = false
                }

            } else {    //  extend/trim is ongoing
                speedIndicator.visibility     = View.GONE
                changeList.visibility         = View.GONE
                playCon1.visibility           = View.VISIBLE
                playCon2.visibility           = View.GONE
                acceptRejectHolder.visibility = View.VISIBLE
                seekBar.hideScrubber()
                setIconActive()
                trimSegment = RangeSeekbarCustom(requireContext())
                val startValue = startingTimestamps.toFloat() * 100 / maxDuration
                val endValue = endingTimestamps.toFloat() * 100 / maxDuration
                extendRangeMarker(startValue, endValue)
            }

            enableEditOptions(false)
            setIconActive()
        }
        //  restores the current position after orientation change
        if(this@VideoEditingFragment::player.isInitialized){
            player.seekTo(restoreCurrentWindow, restoreCurrentPoint)
            player.playWhenReady = !paused
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            if(this@VideoEditingFragment::player.isInitialized) {
                putInt("currentWindow", player.currentWindowIndex)
                putLong("currentSeek", player.currentPosition)
            }
            putInt("currentEditSegment", currentEditSegment)
            putInt("currentEditSegment", currentEditSegment)
            putInt("bufferHdSnipId", bufferHdSnipId)
            putInt("currentSpeed", currentSpeed)
            putInt("segmentCount", segmentCount)
            putLong("bufferDuration", bufferDuration)
            putLong("videoDuration", videoDuration)
            putLong("originalBufferDuration", originalBufferDuration)
            putLong("originalVideoDuration", originalVideoDuration)
            putLong("editedStart", editedStart)
            putLong("editedEnd", editedEnd)
            putLong("maxDuration", maxDuration)
            putLong("previousMaxDuration", previousMaxDuration)
            putLong("previousEditStart", previousEditStart)
            putLong("previousEditEnd", previousEditEnd)
            putLong("startingTimestamps", startingTimestamps)
            putLong("endingTimestamps", endingTimestamps)
            putString("bufferPath", bufferPath)
            putBoolean("isSpeedChanged", isSpeedChanged)
            putBoolean("isEditOnGoing", isEditOnGoing)
            putBoolean("isEditExisting", isEditExisting)
            putBoolean("isSeekbarShown", isSeekbarShown)
            putBoolean("showBuffer", showBuffer)
            putBoolean("trimOnly", trimOnly)
            putBoolean("isStartInBuffer", isStartInBuffer)
            putBoolean("isEndInBuffer", isEndInBuffer)
            putBoolean("previousStartInBuffer", previousStartInBuffer)
            putBoolean("previousEndInBuffer", previousEndInBuffer)
            putBoolean("paused", paused)
            putBoolean("newSpeedChangeStart", newSpeedChangeStart)
            putSerializable("editHistory", editHistory)
            putSerializable("speedDuration", speedDuration)
            putSerializable("editAction", editAction)
            putSerializable("editSeekAction", editSeekAction)
            putParcelable("tmpSpeedDetails", tmpSpeedDetails)
        }
    }

    private fun restoreFromBundle(inState: Bundle?){
        inState?.apply {
            restoreCurrentWindow   = getInt("currentWindow")
            restoreCurrentPoint    = getLong("currentSeek")
            currentEditSegment     = getInt("currentEditSegment")
            bufferHdSnipId         = getInt("bufferHdSnipId")
            currentSpeed           = getInt("currentSpeed")
            segmentCount           = getInt("segmentCount")
            bufferDuration         = getLong("bufferDuration")
            videoDuration          = getLong("videoDuration")
            originalBufferDuration = getLong("originalBufferDuration")
            originalVideoDuration  = getLong("originalVideoDuration")
            editedStart            = getLong("editedStart")
            editedEnd              = getLong("editedEnd")
            maxDuration            = getLong("maxDuration")
            previousMaxDuration    = getLong("previousMaxDuration")
            previousEditStart      = getLong("previousEditStart")
            previousEditEnd        = getLong("previousEditEnd")
            startingTimestamps     = getLong("startingTimestamps")
            endingTimestamps       = getLong("endingTimestamps")
            bufferPath             = getString("bufferPath", "")
            isSpeedChanged         = getBoolean("isSpeedChanged")
            isEditOnGoing          = getBoolean("isEditOnGoing")
            isEditExisting         = getBoolean("isEditExisting")
            isSeekbarShown         = getBoolean("isSeekbarShown")
            showBuffer             = getBoolean("showBuffer")
            trimOnly               = getBoolean("trimOnly")
            isStartInBuffer        = getBoolean("isStartInBuffer")
            isEndInBuffer          = getBoolean("isEndInBuffer")
            previousStartInBuffer  = getBoolean("previousStartInBuffer")
            previousEndInBuffer    = getBoolean("previousEndInBuffer")
            paused                 = getBoolean("paused")
            newSpeedChangeStart    = getBoolean("newSpeedChangeStart")
            editHistory            = getSerializable("editHistory") as ArrayList<EditAction>
            speedDuration          = getSerializable("speedDuration") as Pair<Long, Long>
            editAction             = getSerializable("editAction") as EditAction
            editSeekAction         = getSerializable("editSeekAction") as EditSeekControl
            tmpSpeedDetails        = getParcelable("tmpSpeedDetails")
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(previewTileReceiver, IntentFilter(PREVIEW_ACTION))
        requireActivity().registerReceiver(toRealCompletionReceiver, IntentFilter(
                VIRTUAL_TO_REAL_ACTION))
        requireActivity().registerReceiver(extendTrimReceiver, IntentFilter(EXTEND_TRIM_ACTION))
        requireActivity().registerReceiver(progressDismissReceiver, IntentFilter(DISMISS_ACTION))
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(previewTileReceiver)
        requireActivity().unregisterReceiver(toRealCompletionReceiver)
        requireActivity().unregisterReceiver(extendTrimReceiver)
        requireActivity().unregisterReceiver(progressDismissReceiver)
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
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            extendTextBtn      = findViewById(R.id.extent_text)
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

            editControls       = findViewById(R.id.edit_controls)
            layoutImages       = findViewById(R.id.layout_images)
            horizontalView     = findViewById(R.id.separator)
        }

        commonTransition = arrayListOf()
        commonTransition.add(androidx.core.util.Pair(layoutImages, "layout_images"))
        commonTransition.add(androidx.core.util.Pair(editControls, "edit_controls"))

        setupIcons()
    }

    /**
     * Sets up UI icons to default state
     */
    private fun setupIcons() {

        val density = resources.displayMetrics.density
        val bound1 = Rect(0, 0, (20 * density).roundToInt(), (20 * density).roundToInt())

        val extendDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_extend)
        extendDwg?.bounds = bound1
        extendTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, extendDwg, null, null)
        extendTextBtn.setTextColor(resources.getColor(R.color.white, requireContext().theme))

        val cutDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_cutout)
        cutDwg?.bounds = bound1
        cutTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, cutDwg, null, null)
        cutTextBtn.setTextColor(resources.getColor(R.color.white, requireContext().theme))

        val highlightDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_highlight)
        highlightDwg?.bounds = bound1
        highlightTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, highlightDwg, null, null)
        highlightTextBtn.setTextColor(resources.getColor(R.color.white, requireContext().theme))

        val slowDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_slow)
        slowDwg?.bounds = bound1
        slowTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, slowDwg, null, null)
        slowTextBtn.setTextColor(resources.getColor(R.color.white, requireContext().theme))

        val speedDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_speed)
        speedDwg?.bounds = bound1
        speedTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, speedDwg, null, null)
        speedTextBtn.setTextColor(resources.getColor(R.color.white, requireContext().theme))

    }

    private fun setIconActive() {
        setupIcons()

        val density = resources.displayMetrics.density
        val bound1 = Rect(0, 0, (20 * density).roundToInt(), (20 * density).roundToInt())

        when (editAction) {
            EditAction.FAST -> {
                val speedDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_speed_red)
                speedDwg?.bounds = bound1
                speedTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, speedDwg, null, null)
                speedTextBtn.setTextColor(resources.getColor(R.color.colorPrimaryDimRed,
                        requireContext().theme))
            }
            EditAction.SLOW -> {
                val slowDwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_slow_red)
                slowDwg?.bounds = bound1
                slowTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, slowDwg, null, null)
                slowTextBtn.setTextColor(
                        resources.getColor(
                                R.color.colorPrimaryDimRed,
                                requireContext().theme
                        )
                )
            }
            EditAction.EXTEND_TRIM -> {
                val dwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_extent_red)
                dwg?.bounds = Rect(0, 0, 20, 20)
                extendTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, dwg, null, null)
                extendTextBtn.setTextColor(
                        resources.getColor(
                                R.color.colorPrimaryDimRed,
                                requireContext().theme
                        )
                )
            }
            else -> {

            }
        }
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
            endingTimestamps = if (player.currentWindowIndex == 0)
                player.currentPosition
            else {
                if(bufferDuration + player.currentPosition >= maxDuration){
                    maxDuration
                }else {
                    bufferDuration + player.currentPosition
                }
            }

            when(editAction){
                EditAction.SLOW,
                EditAction.FAST,
                -> {
                    if (tmpSpeedDetails != null) {
                        //  this means the end point was already set
                        //  the user wishes to move the starting point only
                        acceptRejectHolder.visibility = View.VISIBLE
                        if (startingTimestamps != -1L) {
                            if (tmpSpeedDetails?.startWindowIndex ?: 0 == 0 || trimOnly)
                                player.seekTo(0, startingTimestamps)
                            else
                                player.seekTo(1, startingTimestamps - bufferDuration)
                        }
                    }
                }
                EditAction.EXTEND_TRIM -> {
                    if (startingTimestamps < bufferDuration || trimOnly) {
                        player.setSeekParameters(SeekParameters.EXACT)
                        player.seekTo(0, startingTimestamps)
                    } else {
                        player.setSeekParameters(SeekParameters.EXACT)
                        player.seekTo(1, startingTimestamps - bufferDuration)
                    }
                }
                else -> {}
            }

            if (isSeekbarShown && !isEditExisting) {
                seekBar.hideScrubber()
                isSeekbarShown = false
            }

            newSpeedChangeStart = false

            Log.d(TAG,
                    "bindListeners: start: startingTS = $startingTimestamps, endingTS = $endingTimestamps")
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
            horizontalView.setBackgroundResource(R.color.colorPrimaryAccentRed)
            val currentPosition = player.currentPosition
            editSeekAction = EditSeekControl.MOVE_END


            Log.d(TAG,
                    "end clicked : starting time stamp = $startingTimestamps, current position = $currentPosition")
//            if (startingTimestamps != currentPosition) {

            when(editAction) {
                EditAction.FAST,
                EditAction.SLOW,
                -> {
                    var startWindow = player.currentWindowIndex
                    var endWindow = player.currentWindowIndex
                    startingTimestamps =
                            if (startWindow == 0) currentPosition  //    take the starting point when end is pressed
                            else bufferDuration + currentPosition

                    val startValue = (startingTimestamps * 100 / maxDuration).toFloat()
                    var endValue = startValue

                    speedDuration = if (isEditExisting) {   //  if an exiting item is being modified
                        player.setSeekParameters(SeekParameters.EXACT)
                        player.seekTo(
                                if (player.currentWindowIndex == 1) {
                                    endingTimestamps - bufferDuration
                                } else
                                    endingTimestamps
                        )

                        endValue =
                                if (speedDetailSet.elementAt(currentEditSegment).startWindowIndex == 1) {
                                    endWindow = 1
                                    ((bufferDuration + endingTimestamps) * 100 / maxDuration).toFloat()
                                } else (endingTimestamps * 100 / maxDuration).toFloat()

                        Pair(startingTimestamps, endingTimestamps)  //  we may have come from edit
                    } else {
                        Pair(
                                startingTimestamps,
                                startingTimestamps
                        )    //  we only have the starting position now
                    }

                    if (!isEditExisting) {
                        if (tmpSpeedDetails == null) {
                            tmpSpeedDetails = SpeedDetails(
                                    startWindowIndex = startWindow,
                                    endWindowIndex = endWindow,
                                    isFast = editAction == EditAction.FAST,
                                    multiplier = getCurrentEditSpeed(),
                                    timeDuration = speedDuration
                            )

                            speedDetailSet.add(tmpSpeedDetails!!)

                            setupRangeMarker(startValue, endValue)    //  initial value for marker
                        } else {
                            if (endingTimestamps != maxDuration) {
                                player.setSeekParameters(SeekParameters.EXACT)
                                player.seekTo(
                                        if (player.currentWindowIndex == 1) {
                                            endingTimestamps - bufferDuration
                                        } else
                                            endingTimestamps
                                )
                            }
                        }
                    }

                    if (timebarHolder.indexOfChild(uiRangeSegments!![currentEditSegment]) < 0)  //  View doesn't exist and can be added
                        timebarHolder.addView(uiRangeSegments!![currentEditSegment])
                }

                EditAction.EXTEND_TRIM -> {
                    startingTimestamps = getCorrectedTimeBarPosition()
                    if (endingTimestamps > bufferDuration && !trimOnly) {
                        player.setSeekParameters(SeekParameters.EXACT)
                        player.seekTo(1, endingTimestamps - bufferDuration)
                    } else {
                        player.setSeekParameters(SeekParameters.EXACT)
                        player.seekTo(0, endingTimestamps)
                    }
                }

                else -> {}
            }
//            }
            newSpeedChangeStart = false
            Log.d(TAG,
                    "bindListeners: end: startingTS = $startingTimestamps, endingTS = $endingTimestamps")
            acceptRejectHolder.visibility = View.VISIBLE

            if (isSeekbarShown && !isEditExisting) {
                seekBar.hideScrubber()
                isSeekbarShown = false
            }
        }

        extendTextBtn.setOnClickListener {
            pauseVideo()
            if(editAction == EditAction.EXTEND_TRIM && isEditOnGoing){
                return@setOnClickListener
            }

            // reject ongoing edit and extend
            if(isEditOnGoing && isEditActionSpeedChange())
                reject.performClick()

            progressTracker?.stopTracking()
            progressTracker = null

            resetPlaybackUI()
            playCon1.visibility           = View.VISIBLE
            playCon2.visibility           = View.GONE
            acceptRejectHolder.visibility = View.VISIBLE

            enableEditOptions(false)
//            enableSpeedEdit(false)

            editAction = EditAction.EXTEND_TRIM
            setIconActive()
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

            reject.performClick()

            if(trimSegment != null){
                timebarHolder.removeView(trimSegment)
                trimSegment = null
            }
            isEditOnGoing = false
            isEditExisting = false
            editAction = EditAction.NORMAL
        }

        save.setOnClickListener {
            showDialogSave()
        }

        /**
         * accepts the changes to the edit that were made
         * takes in the end position and updates the rangeSeekBar.
         * progress tracker is run to make sure playback is in accordance with the edit.
         * reset the UI for new edit
         */
        accept.setOnClickListener {
            if (isEditActionSpeedChange()) {
                acceptSpeedChanges()

                if (!isSeekbarShown) {
                    seekBar.showScrubber()
                    isSeekbarShown = true
                }
            } else if (editAction == EditAction.EXTEND_TRIM) {
                if(startingTimestamps >= endingTimestamps ||
                    trimSegment!!.selectedMinValue.toFloat() == trimSegment!!.selectedMaxValue.toFloat()){
                    Toast.makeText(requireContext(),
                            "Modified range is not possible",
                            Toast.LENGTH_SHORT).show()
                }else {
                    removeBufferOverlays()
                    acceptTrimChanges()
                    showAdjustedSpeedChanges()

                    editHistory.add(editAction)
                    startRangeUI()
                    resetPlaybackUI()

                    if (!isSeekbarShown) {
                        seekBar.showScrubber()
                        isSeekbarShown = true
                    }
                }
            }
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
                speedDetailSet.remove(tmpSpeedDetails)
                progressTracker?.removeSpeedDetails(tmpSpeedDetails!!)
                val ref = uiRangeSegments!!.removeAt(/*uiRangeSegments!!.size - 1*/
                        currentEditSegment)
                timebarHolder.removeView(ref)
                tmpSpeedDetails = null
            }

            if(isEditActionSpeedChange()){
                segmentCount -= 1
                currentEditSegment -= 1
            }

            if(editAction == EditAction.EXTEND_TRIM){
                undoExtendTrim()
            }

            if (isEditExisting) {
                updateEditList()
            }

            startRangeUI()
            resetPlaybackUI()

            setupProgressTracker()
            updateRestrictedList()

            editAction = EditAction.NORMAL
            startingTimestamps = -1L
            endingTimestamps = -1L
            player.setPlaybackParameters(PlaybackParameters(1F))
            isSpeedChanged = false
            isEditOnGoing = false
            isEditExisting = false

            if (!isSeekbarShown) {
                seekBar.showScrubber()
                isSeekbarShown = true
            }
        }

        playBtn.setOnClickListener {
            if (player.currentPosition >= maxDuration)
                player.seekTo(0, 0)
            playVideo()

            setupProgressTracker()
//            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "Start Playback")
        }

        pauseBtn.setOnClickListener {
            if (player.isPlaying) {
                pauseVideo()
                Log.d(TAG, "Stop Playback")
            }
        }

        toStartBtn.setOnClickListener {
            pauseVideo()
            player.seekTo(0, 0)
        }

        //  prevent touchs on seekbar
        seekBar.setOnTouchListener { _, _ ->
            true
        }
        seekBar.isClickable = false

        slowTextBtn.setOnClickListener {
            pauseVideo()
            if(isEditOnGoing && editAction == EditAction.EXTEND_TRIM) { //  reject the ongoing edit and do this one
                reject.performClick()
            }

            val currentPosition = player.currentPosition
            editAction = EditAction.SLOW
            setIconActive()
            if(!isEditOnGoing){
                handleNewSpeedChange(currentPosition)
            }else{
                //  update the currently changing section to slow
//                isEditExisting = true
                handleExistingSpeedChange(currentPosition, false)
            }
            Log.d(TAG,
                    "bindListeners: slow: startingTS = $startingTimestamps, endingTS = $endingTimestamps")
        }

        speedTextBtn.setOnClickListener {
            pauseVideo()
            if(isEditOnGoing && editAction == EditAction.EXTEND_TRIM) { //  reject the ongoing edit and do this one
                reject.performClick()
            }

            val currentPosition = player.currentPosition
            editAction = EditAction.FAST
            setIconActive()
            if(!isEditOnGoing){  //  we are editing afresh
                handleNewSpeedChange(currentPosition)
            }else{
//                isEditExisting = true
                handleExistingSpeedChange(currentPosition, true)
            }
            Log.d(TAG,
                    "bindListeners: speed: startingTS = $startingTimestamps, endingTS = $endingTimestamps")
        }

        speedIndicator.setOnClickListener {
            speedIndicator.text = changeSpeedText()
            progressTracker?.setSpeed(getCurrentEditSpeed().toFloat())
        }

        val mOrientationListener: SimpleOrientationListener = object : SimpleOrientationListener(
                context) {
            override fun onSimpleOrientationChanged(orientation: Int) {
                if(activity != null) {  //because rotating a few times was causing the activity to be null
                    when (orientation) {
                        VideoModeOrientation.REV_LANDSCAPE.ordinal,
                        VideoModeOrientation.LANDSCAPE.ordinal,
                        -> {
                            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                                    requireActivity(),
                                    *commonTransition.toTypedArray())
                            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            activity?.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                        else -> {
                            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            activity?.requestedOrientation =
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                    Log.d("orientation", "$orientation")
                }
            }
        }
        if (android.provider.Settings.System.getInt(requireContext().contentResolver,
                        Settings.System.ACCELEROMETER_ROTATION, 0) == 1){
            mOrientationListener.enable()

        }
        else{
            mOrientationListener.disable()
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
        player.release()
        setupPlayer()
        showAdjustedSpeedChanges()
    }

    /**
     * adjusts the speed changes so that they remain as is regardless of extend/trim
     */
    private fun showAdjustedSpeedChanges() {
//        val (_, height, padding) = setupCommonRangeUiElements()
        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()

        val thumbDrawable = getBitmap(ResourcesCompat.getDrawable(resources,
                R.drawable.ic_thumb_transparent,
                context?.theme) as VectorDrawable,
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
        }

        //  start tracking these changes
        setupProgressTracker()
    }

    /**
     * start tracking progress
     */
    private fun setupProgressTracker() {
        progressTracker?.setChangeAccepted(false)
        progressTracker?.stopTracking()
        progressTracker = null

        progressTracker = ProgressTracker(player)
        with(progressTracker!!) {
            setSpeedDetails(speedDetailSet.toMutableList() as ArrayList<SpeedDetails>)
            setSpeed(getCurrentEditSpeed().toFloat())
            setChangeAccepted(true)
            run()
        }
    }

    /**
     * adjusts the existing speed changes so that they remain the same with the modified video also
     *
     * @param isStartInBuffer Boolean
     * @param isEndInBuffer Boolean
     */
    private fun adjustPreviousSpeedEdits(isStartInBuffer: Boolean, isEndInBuffer: Boolean) {
        if(editedStart == previousEditStart && editedEnd == previousEditEnd)
            return

        /*var moveBy: Long = if(isStartInBuffer){   // amount to move the edits by
            if(previousEditStart != -1L) {
                (originalBufferDuration - editedStart) - (originalBufferDuration - previousEditStart)
            }
            else {
                (originalBufferDuration - editedStart)
            }
        }else{
            if(previousEditStart != -1L) {
                - editedStart
            }
            else {
                editedStart + previousEditStart
            }
        }*/

        val moveBy = if(isStartInBuffer){   //  starting is in buffer
            if(previousEditStart != -1L){   //  previous edit exists
                if(previousStartInBuffer) {   //  previous start in buffer
                    (originalBufferDuration - editedStart) - (originalBufferDuration - previousEditStart)
                }else { //  previous start not in buffer
                    previousEditStart + (originalBufferDuration - editedStart)
                }
            }else { //  no previous edits
                originalBufferDuration - editedStart
            }
        } else {    //  current start is not in buffer. i.e. in the actual video
            if(previousEditStart != -1L){   //  previous edit exists
                if(previousStartInBuffer){  //  previous start is in buffer and current is in video
                    - (originalBufferDuration - previousEditStart + editedStart)
                }else { //  previous is also in the video segment
                    - (editedStart - previousEditStart)
                }
            }else { //  no previous edits
                - editedStart
            }
        }

        Log.d(TAG, "adjustPreviousSpeedEdits: initial moveBy = $moveBy")

//        if(previousEditStart != -1L)
//            moveBy -= previousEditStart

        Log.d(TAG, "adjustPreviousSpeedEdits: moveBy adjusted with previous = $moveBy")

        val removeSet = arrayListOf<SpeedDetails>()
        var shouldRemove =false

        var counter = 0
        speedDetailSet.forEach { // check if some edit exists in buffered video from a previous extension/trim
            if(it.startWindowIndex == 0 && it.endWindowIndex == 0)
                counter++
        }

        val existingEditEntirelyInBuffer = counter != speedDetailSet.size //  previous edits in buffer
//        val difference = maxDuration - previousMaxDuration
//        Log.d(TAG, "adjustPreviousSpeedEdits: difference = $difference")

        speedDetailSet.forEach{
//            Log.d(TAG, "adjustPreviousSpeedEdits: original speed detail = $it")

            if(it.startWindowIndex == 0 && it.endWindowIndex == 0){
                it.startWindowIndex = 1
                it.endWindowIndex = 1
            }

            var s = it.timeDuration!!.first + moveBy
            var e = it.timeDuration!!.second + moveBy
//            var s = if(!existingEditEntirelyInBuffer) (it.timeDuration!!.first + difference + moveBy) else (it.timeDuration!!.first + moveBy)
//            var e = if(!existingEditEntirelyInBuffer) (it.timeDuration!!.second + difference + moveBy) else (it.timeDuration!!.second + moveBy)

            Log.d(TAG,
                    "adjustPreviousSpeedEdits: original segments = ${it.timeDuration!!.first},${it.timeDuration!!.second} ")
            Log.d(TAG, "adjustPreviousSpeedEdits: corrected segments = $s,$e ")

            //  checking the starting points
            if(s < 0){  //  the starting portion is trimmed out
                s = 0
                if(e < 0){  //  both starting and ending changes are out of range
                    shouldRemove = true
                }
            }
            //  checking the ending points  //  todo: this needs work because we are not taking into consideration which window is being worker on
            if(e > (editedEnd - editedStart)){
                e = editedEnd - editedStart
                if(s > (editedEnd - editedStart)){
                    shouldRemove = true
                }
            }

            it.timeDuration = Pair(s, e)

            if(shouldRemove) {
                removeSet.add(it)
                shouldRemove = false
            }
        }

        removeSet.forEach{
            val result = speedDetailSet.removeIf { speedSetItem ->
                speedSetItem.timeDuration!!.first == it.timeDuration!!.first
            }
            Log.d(TAG, "adjustPreviousSpeedEdits: removed = $result")
        }
//        speedDetailSet.forEach{ Log.d(TAG, "adjustPreviousSpeedEdits: $it\n") }

        //  to remember the changes we made
        previousMaxDuration   = maxDuration
        previousEditStart     = editedStart
        previousEditEnd       = editedEnd
        previousStartInBuffer = isStartInBuffer
        previousEndInBuffer   = isEndInBuffer
    }

    /**
     * removes the extend/trim related overlays
     */
    private fun removeBufferOverlays() {
        val dwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_extend)
        dwg?.bounds = Rect(0, 0, 20, 20)
        extendTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, dwg, null, null)
        extendTextBtn.setTextColor(resources.getColor(R.color.colorPrimaryGrey))

        trimSegment?.let {
            timebarHolder.removeView(trimSegment)
        }
        timebarHolder.removeView(bufferOverlay)
    }

    /**
     * Accepts the extend/trim duration changes made and also ensure playback happens to reflect the same.
     */
    private fun acceptTrimChanges() {
        val bufferSource = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext())).createMediaSource(
                MediaItem.fromUri(
                        Uri.parse(
                                bufferPath)))
        val videoSource = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext())).createMediaSource(
                MediaItem.fromUri(
                        Uri.parse(
                                snip!!.videoFilePath)))

        // Clip the videos to the required positions
        val startBClip = if (startingTimestamps in 0 until originalBufferDuration) startingTimestamps else originalBufferDuration
        val endBClip = if (endingTimestamps in startingTimestamps until originalBufferDuration) endingTimestamps else originalBufferDuration

        //  the value is in the buffered video
        isStartInBuffer = startBClip == startingTimestamps
        isEndInBuffer = endBClip == endingTimestamps

        var clip1: ClippingMediaSource? = null
        var clip2: ClippingMediaSource? = null
        val clipsList = arrayListOf<ClippingMediaSource>()

        if (isStartInBuffer && !trimOnly) {
            editedStart = startBClip
            if (isEndInBuffer) {//  clippingMediaSource used as workaround for timeline scrubbing
                clip1 = ClippingMediaSource(bufferSource,
                        TimeUnit.MILLISECONDS.toMicros(startBClip),
                        TimeUnit.MILLISECONDS.toMicros(
                                endBClip))
                editedEnd = endBClip
                bufferDuration = endBClip - startBClip
                videoDuration = 0
            } else {
                clip1 = ClippingMediaSource(bufferSource,
                        TimeUnit.MILLISECONDS.toMicros(startBClip),
                        TimeUnit.MILLISECONDS.toMicros(
                                originalBufferDuration))
                clip2 = ClippingMediaSource(videoSource,
                        TimeUnit.MILLISECONDS.toMicros(0),
                        TimeUnit.MILLISECONDS.toMicros(
                                endingTimestamps - originalBufferDuration))
                editedEnd = endingTimestamps
                bufferDuration = originalBufferDuration - startBClip
                videoDuration = endingTimestamps - originalBufferDuration
            }
        } else {
            editedStart = startingTimestamps - originalBufferDuration
            editedEnd = endingTimestamps - originalBufferDuration
            clip2 = ClippingMediaSource(videoSource,
                    TimeUnit.MILLISECONDS.toMicros(editedStart),
                    TimeUnit.MILLISECONDS.toMicros(
                            editedEnd))
            bufferDuration = 0
            videoDuration = editedEnd - editedStart
        }

        if (clip1 != null) {
            clipsList.add(clip1)
        }
        if (clip2 != null) {
            clipsList.add(clip2)
        }

        val mediaSource = ConcatenatingMediaSource(true, *clipsList.toTypedArray())
        player.setMediaSource(mediaSource)

        maxDuration = bufferDuration + videoDuration

        if(editedEnd - editedStart != 0L) {
            adjustPreviousSpeedEdits(isStartInBuffer, isEndInBuffer)
        }
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
        if(progressTracker != null)
            progressTracker?.removeSpeedDetails(tmpSpeedDetails!!)

        if(endingTimestamps < startingTimestamps)
            endingTimestamps += bufferDuration

        /*if(startWindow == 1){
            startingTimestamps += bufferDuration
        }*/

        if(isEditExisting){
            with(speedDetailSet.elementAt(currentEditSegment)) {
                startWindowIndex = startWindow
                endWindowIndex   = endWindow
                isFast           = editAction == EditAction.FAST
                multiplier       = getCurrentEditSpeed()
                timeDuration     = Pair(startingTimestamps, endingTimestamps)
            }
        }else{
            speedDetailSet.remove(tmpSpeedDetails)

            Log.d(TAG, "acceptSpeedChanges: edit action = ${editAction.name}")
            speedDuration = Pair(startingTimestamps, endingTimestamps)
            val speedDetails = SpeedDetails(
                    startWindowIndex = startWindow,
                    endWindowIndex = endWindow,
                    isFast = editAction == EditAction.FAST,
                    multiplier = getCurrentEditSpeed(),
                    timeDuration = speedDuration)

            speedDetailSet.add(speedDetails)
        }

        tmpSpeedDetails = null

        setupProgressTracker()

        isEditOnGoing = false
        isEditExisting = false
        isSpeedChanged = true

        val startValue = (startingTimestamps * 100 / maxDuration).toFloat()
        val endValue = (endingTimestamps * 100 / maxDuration).toFloat()

        setupRangeMarker(startValue, endValue)
        fixRangeMarker(startValue, endValue)

        updateRestrictedList()

        if (editListAdapter == null) {
            setupEditList()
        } else {
            updateEditList()
        }

        slideDownAnimation(changeList)

        if(!isEditExisting)
            editHistory.add(editAction)

        startRangeUI()
        resetPlaybackUI()
        currentEditSegment = speedDetailSet.size - 1
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
                pauseVideo()
                player.release()
                clearSelectedRanges()
                isEditOnGoing  = true
                isEditExisting = false
                editAction     = EditAction.EXTEND_TRIM
                setIconActive()
                editSeekAction = EditSeekControl.MOVE_START
                trimSegment    = RangeSeekbarCustom(requireContext())

                setupPlayer()
                startingTimestamps = if(editedStart < 0) {
                    bufferDuration
                } else {
                    if(isStartInBuffer) {
                        previousEditStart
                    }else{
                        originalBufferDuration + previousEditStart
                    }
                }

                endingTimestamps = if(editedEnd < 0) {
                    bufferDuration + videoDuration
                }else {
                    previousEditEnd
                }

                maxDuration    = bufferDuration + videoDuration
                val startValue = (startingTimestamps * 100 / maxDuration).toFloat()
                val endValue   = (endingTimestamps * 100 / maxDuration).toFloat()
                extendRangeMarker(startValue, endValue)
                player.setSeekParameters(SeekParameters.EXACT)

                if (isSeekbarShown) {
                    seekBar.hideScrubber()
                    isSeekbarShown = false
                }
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
                    endWindowIndex = endWindow,
                    isFast = isFast,
                    multiplier = getCurrentEditSpeed(),
                    timeDuration = Pair(startingTimestamps, endingTimestamps))
            speedDetailSet.add(tmpSpeedDetails!!)

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
        //  the the actual position so that we can check with the existing TS
        val position = if(player.currentWindowIndex == 0) currentPosition
        else bufferDuration + currentPosition

        setupForEdit()
        segmentCount += 1   //  a new segment is active
        currentEditSegment += 1

        if (uiRangeSegments == null)
            uiRangeSegments = arrayListOf()

        if (segmentCount > uiRangeSegments?.size ?: 0) {
            uiRangeSegments?.add(RangeSeekbarCustom(requireContext()))
        }

        val startPoint = if (player.currentWindowIndex == 0) currentPosition  //    take the starting point when end is pressed
            else bufferDuration + currentPosition

        val startValue = (startPoint * 100 / maxDuration).toFloat()

        tmpSpeedDetails = SpeedDetails(
                startWindowIndex = player.currentWindowIndex,
                endWindowIndex = player.currentWindowIndex,
                isFast = editAction == EditAction.FAST,
                multiplier = getCurrentEditSpeed(),
                timeDuration = Pair(player.currentPosition, player.currentPosition)
        )

        speedDetailSet.add(tmpSpeedDetails!!)

        setupRangeMarker(startValue, startValue + 1)
        seekBar.hideScrubber()

        if (timebarHolder.indexOfChild(uiRangeSegments!![currentEditSegment]) < 0) { //  View doesn't exist and can be added
            try {
                timebarHolder.addView(uiRangeSegments!![currentEditSegment])
            } catch (e: IllegalStateException) {
                Log.e(TAG,
                        "handleNewSpeedChange: video cannot be added, since it was already added")
                e.printStackTrace()
            }
        }

        newSpeedChangeStart = true
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

        player.seekTo(0, 0)
        seekBar.showScrubber()
        pauseVideo()

        enableEditOptions(true)
        setupIcons()
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
        changeList.layoutManager = LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false)
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

        enableEditOptions(false)

        editSeekAction     = EditSeekControl.MOVE_START
        startingTimestamps = -1L
        endingTimestamps   = maxDuration

        if(editListAdapter != null){    //  already some edit was saved here
            slideUpAnimation(changeList)
//            changeList.visibility = View.VISIBLE
        }
    }

    /**
     * control if views are clickable or not and update UI accordingly
     */
    private fun enableEditOptions(enable: Boolean) {
//        extentTextBtn.isEnabled    = enable
        cutTextBtn.isEnabled       = enable
        highlightTextBtn.isEnabled = enable

//        extentTextBtn.isClickable    = enable
        cutTextBtn.isClickable       = enable
        highlightTextBtn.isClickable = enable

        if (enable) {
//            extentTextBtn.alpha    = 1.0F
            cutTextBtn.alpha       = 1.0F
            highlightTextBtn.alpha = 1.0F
//            enableSpeedEdit(enable)
        } else {
//            extentTextBtn.alpha    = 0.5F
            cutTextBtn.alpha       = 0.5F
            highlightTextBtn.alpha = 0.5F
        }
    }

    /**
     * sets up the UI component and indicators with the correct colours for editing
     */
    private fun startRangeUI() {
        start.setBackgroundResource(R.drawable.start_curve)
        horizontalView.setBackgroundResource(R.color.horizontalViewColor)
        end.setBackgroundResource(R.drawable.end_curve)
    }

    /**
     * checks if the segment is already taken
     * @param currentPosition takes in the current position of the video
     * @return true if the segment is already taken, false if available
     */
    private fun checkSegmentTaken(currentPosition: Long): Boolean {
        val position = if(player.currentWindowIndex == 0) currentPosition
            else bufferDuration + currentPosition
        if (speedDetailSet.size > 0) {
            speedDetailSet.forEachIndexed { index, it ->
                if(position in it.timeDuration!!.first .. it.timeDuration!!.second && index != currentEditSegment){
//                    resetPlaybackUI()
                    Toast.makeText(requireContext(),
                            "Cannot choose existing segment",
                            Toast.LENGTH_SHORT).show()
                    return true
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
            val layoutParam = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            uiRangeSegments!![currentEditSegment].layoutParams = layoutParam
        }

        val (colour, height, padding) = setupCommonRangeUiElements()

        val leftThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources,
                R.drawable.ic_thumb,
                context?.theme) as VectorDrawable,
                resources.getColor(android.R.color.holo_green_dark, context?.theme))
        val rightThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources,
                R.drawable.ic_thumb,
                context?.theme) as VectorDrawable,
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

        val thumbDrawable = getBitmap(ResourcesCompat.getDrawable(resources,
                R.drawable.ic_thumb_transparent,
                context?.theme) as VectorDrawable,
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
        val leftThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources,
                R.drawable.ic_thumb,
                context?.theme) as VectorDrawable,
                resources.getColor(android.R.color.holo_green_dark, context?.theme))
        val rightThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources,
                R.drawable.ic_thumb,
                context?.theme) as VectorDrawable,
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
            setGap(0.1F)
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
            if (!speedDetailSet.isNullOrEmpty() && currentEditSegment >= 0) {
                if (speedDetailSet.toList()[currentEditSegment].isFast)
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

    private fun getCurrentEditSpeed(): Int = Integer.parseInt(speedIndicator.text.toString()
            .substringBefore(
                    'X'))

    /**
     * Setting up the player to play the require snip for editing
     */
    private fun setupPlayer() {
        val videoUri = Uri.parse(snip!!.videoFilePath)
        val mediaItem = MediaItem.fromUri(videoUri)

        previewBarProgress.visibility = View.VISIBLE

        if(!thumbnailExtractionStarted && generatePreviewTile) {
            CoroutineScope(Default).launch {
                getVideoPreviewFrames()
            }
        }else {
            val parentFilePath = File(snip!!.videoFilePath).parent
            showThumbnailsIfAvailable(File("$parentFilePath/previewThumbs/"))
        }

        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player

        if (!showBuffer) {
            val defaultBandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()
            val dataSourceFactory = DefaultDataSourceFactory(
                    requireContext(),
                    Util.getUserAgent(requireActivity(), "mediaPlayerSample"), defaultBandwidthMeter
            )

            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(Uri.parse(snip!!.videoFilePath))
            if (snip!!.is_virtual_version == 1) {   // Virtual versions only play part of the media
                val clippingMediaSource = ClippingMediaSource(
                        mediaSource,
                        TimeUnit.SECONDS.toMicros(snip!!.start_time.toLong()),
                        TimeUnit.SECONDS.toMicros(snip!!.end_time.toLong())
                )
                seekBar.setDuration(snip!!.snip_duration.toLong() * 1000)
                maxDuration = TimeUnit.SECONDS.toMillis(snip!!.snip_duration.toLong())
                player.addMediaSource(clippingMediaSource)
            } else {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(snip!!.videoFilePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                maxDuration = duration
                val clippingMediaSource = ClippingMediaSource(
                        mediaSource,
                        0,
                        TimeUnit.SECONDS.toMicros(snip!!.total_video_duration.toLong())
                )
                seekBar.setDuration(snip!!.total_video_duration.toLong() * 1000)
                maxDuration = TimeUnit.SECONDS.toMillis(snip!!.total_video_duration.toLong())
                player.addMediaSource(clippingMediaSource)
            }
        } else {    //  show buffer if available else show trim
            val clipList = arrayListOf<ClippingMediaSource>()
            if(!trimOnly) {
                val bufferSource =
                    ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
                        .createMediaSource(MediaItem.fromUri(Uri.parse(bufferPath)))
                val clip1 = ClippingMediaSource(
                        bufferSource,
                        0,
                        TimeUnit.MILLISECONDS.toMicros(bufferDuration)
                )
                clipList.add(clip1)
            }

            val videoSource =
                ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
                    .createMediaSource(MediaItem.fromUri(Uri.parse(snip!!.videoFilePath)))
            //  clippingMediaSource used as workaround for timeline scrubbing
            val clip2 = ClippingMediaSource(
                    videoSource,
                    if (bufferDuration == 0L && editedStart > 0) TimeUnit.MILLISECONDS.toMicros(editedStart) else 0,
                    TimeUnit.MILLISECONDS.toMicros(videoDuration)
            )
            clipList.add(clip2)
            maxDuration = bufferDuration + videoDuration
            val mediaSource = ConcatenatingMediaSource(true, *clipList.toTypedArray())
            player.setMediaSource(mediaSource)
            playerView.setShowMultiWindowTimeBar(true)
            val jumpTo = if (editedStart < 0) bufferDuration else editedStart
            player.seekTo(0, jumpTo)
            showBufferOverlay()
        }

        player.apply {
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
        }

        playerView.apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            controllerAutoShow = false
            controllerShowTimeoutMs = -1
            controllerHideOnTouch = false
            setBackgroundColor(Color.BLACK)
            setShutterBackgroundColor(Color.TRANSPARENT)    // removes the black screen when seeking or switching media
            setShowMultiWindowTimeBar(showBuffer)
            showController()
        }

//        pauseVideo()

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
                if (state == Player.STATE_ENDED &&
                        ((player.currentWindowIndex == 0 && player.duration >= maxDuration) ||
                                (player.currentWindowIndex == 1 && (player.duration + bufferDuration) >= maxDuration))) {
                    pauseVideo()
                }
            }
        })

        initSwipeControls()
    }

    /**
     * shows the overlay for the buffer on the timeline
     */
    private fun showBufferOverlay() {
        val colour = resources.getColor(R.color.blackOverlay, context?.theme)
        val (_, height, padding) = setupCommonRangeUiElements()

        val thumbDrawable = getBitmap(ResourcesCompat.getDrawable(resources,
                R.drawable.ic_thumb_transparent,
                context?.theme) as VectorDrawable,
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
    }

    private fun showDialogSave() {
        if(saveDialog == null)
            saveDialog = SaveEditDialog(this@VideoEditingFragment)

        saveDialog!!.show(requireActivity().supportFragmentManager, SAVE_DIALOG)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            val percentOfDuration = scaledPercent * -1 * maxDuration + startScrollingSeekPosition

            // shift in position domain and ensure circularity
            var newSeekPosition = percentOfDuration.roundToLong()
            /*((percentOfDuration + duration) % duration).roundToLong().absoluteValue*/

            if (restrictList.isNullOrEmpty() || restrictList?.size!! < speedDetailSet.size) {
                updateRestrictedList()
            }

            higher = if(showBuffer) maxDuration else nearestExistingHigherTS(player.currentPosition)
            lower = if(showBuffer) 0 else nearestExistingLowerTS(player.currentPosition)

            if (isEditOnGoing && isEditActionSpeedChange()) {
                val change = checkOverlappingTS(newSeekPosition)
                Log.d(TAG, "initSwipeControls: change = $change")
                if(change >= 0L/* && !isEditExisting*/){
                    newSeekPosition = change
                    player.setSeekParameters(SeekParameters.EXACT)
                    player.seekTo(newSeekPosition)
                    return@onScroll
                }

                when (editSeekAction) {
                    EditSeekControl.MOVE_START -> {
                        if (newSeekPosition >= endingTimestamps && endingTimestamps != 0L && !showBuffer) {
                            newSeekPosition = endingTimestamps
                        } else {
                            // prevent the user from seeking beyond the fixed start point
                            if (newSeekPosition <= lower) {
                                newSeekPosition = lower
                            }
                        }
                        if (newSeekPosition==startingTimestamps){
                            player.setSeekParameters(SeekParameters.EXACT)
                        }

//                        if (uiRangeSegments!![currentEditSegment].maxSelection().toInt() == 100 ) {
                        if (newSpeedChangeStart) {
                            val startPosition = if (player.currentWindowIndex == 1) player.currentPosition + bufferDuration else player.currentPosition
                            val adjustedEnd = ((startPosition * 100 / maxDuration) + 1).toFloat()  // to maintain a small gap

                            uiRangeSegments!![currentEditSegment].setMaxStartValue(adjustedEnd)
                                    .apply()
                        } else {
                            uiRangeSegments!![currentEditSegment].setMaxStartValue(((endingTimestamps) * 100 / maxDuration).toFloat())
                                    .apply()
                        }
//                        }

                        var startValue =
                                if (player.currentWindowIndex == 1) ((bufferDuration + newSeekPosition) * 100 / maxDuration).toFloat()
                                else (newSeekPosition * 100 / maxDuration).toFloat()

                        //  prevent point on top of each other
                        if (startValue == uiRangeSegments!![currentEditSegment].selectedMaxValue.toFloat()) {
                            startValue -= 1 //  reduce the start point and adjust the newSeekPosition accordingly
                            newSeekPosition = if (player.currentWindowIndex == 1) {
                                (startValue * maxDuration / 100 - bufferDuration).toLong()
                            } else {
                                (startValue * maxDuration / 100).toLong()
                            }
                        }

                        uiRangeSegments!![currentEditSegment].setMinStartValue(startValue).apply()
                    }
                    EditSeekControl.MOVE_END -> {
                        if (newSeekPosition < startingTimestamps && !showBuffer) {
                            newSeekPosition = startingTimestamps
                        }
                        if (newSeekPosition > higher)
                            newSeekPosition = higher

                        var endValue =
                                if (player.currentWindowIndex == 1) ((bufferDuration + newSeekPosition) * 100 / maxDuration).toFloat()
                                else (newSeekPosition * 100 / maxDuration).toFloat()

                        //  prevent point on top of each other
                        if (endValue == uiRangeSegments!![currentEditSegment].selectedMinValue.toFloat()) {
                            endValue += 1 //  reduce the start point and adjust the newSeekPosition accordingly
                            newSeekPosition = if (player.currentWindowIndex == 1) {
                                (endValue * maxDuration / 100 - bufferDuration).toLong()
                            } else {
                                (endValue * maxDuration / 100).toLong()
                            }
                        }
                        if (newSeekPosition==endingTimestamps){
                            player.setSeekParameters(SeekParameters.EXACT)
                        }

                        uiRangeSegments!![currentEditSegment].setMaxStartValue(endValue).apply()
                    }
                    else -> {
                    }
                }
            }

            if (editAction == EditAction.EXTEND_TRIM) {
                // newSeekPosition resets on each player window.
                when (editSeekAction) {
                    EditSeekControl.MOVE_START -> {
                        startingTimestamps = getCorrectedTimeBarPosition()
                        if (player.currentWindowIndex == 1) {
                            if (newSeekPosition + bufferDuration > endingTimestamps) {
                                startingTimestamps = endingTimestamps - 50
                                newSeekPosition = startingTimestamps - bufferDuration
                            }
                        } else {
                            if (newSeekPosition > endingTimestamps) {
                                startingTimestamps = endingTimestamps - 50
                                newSeekPosition = startingTimestamps
                            }
                        }

                        trimSegment!!.setMinStartValue((startingTimestamps * 100 / maxDuration).toFloat())
                                .apply()
                        trimSegment!!.setMaxStartValue((endingTimestamps * 100 / maxDuration).toFloat())
                                .apply()
                    }
                    EditSeekControl.MOVE_END -> {
                        endingTimestamps = getCorrectedTimeBarPosition()
                        if (player.currentWindowIndex == 1) {
                            if (newSeekPosition + bufferDuration < startingTimestamps) {
                                endingTimestamps = startingTimestamps + 50
                                newSeekPosition = endingTimestamps - bufferDuration
                            }
                        } else {
                            if (newSeekPosition < startingTimestamps) {
                                endingTimestamps = startingTimestamps + 50
                                newSeekPosition = endingTimestamps
                            }
                        }
                        //  if endingTimestamps are near max duration we probably need to make that max duration
                        if(endingTimestamps > maxDuration - 50)
                            endingTimestamps = maxDuration
                        trimSegment!!.setMaxStartValue((endingTimestamps * 100 / maxDuration).toFloat())
                                .apply()
                    }
                    else -> { }
                }
            }

            if(showBuffer && !trimOnly) {
                //  select which player window needs to be played
                if (newSeekPosition >= player.contentDuration && player.currentWindowIndex == 0) {
                    if (player.hasNext()) {
                        player.next()
                        startScrollingSeekPosition = 0
                        player.seekTo(1, 0)
                    }
                }
                if (newSeekPosition <= 0L && player.currentWindowIndex == 1) {
                    if (player.hasPrevious()) {
                        player.previous()
                        startScrollingSeekPosition = bufferDuration
                        player.seekTo(0, bufferDuration)
                    }
                }
            }

             if(player.currentWindowIndex == 0) {
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
            if(player.currentWindowIndex == 0) {
                if (newSeekPosition in 0..1500 ||
                    newSeekPosition in ((maxDuration - 1500)..maxDuration)
                ) {
                    player.setSeekParameters(SeekParameters.EXACT)
                }
            } else {
                if(newSeekPosition in 0 .. 1500 ||
                        newSeekPosition in ((maxDuration - bufferDuration - 1500) .. (maxDuration - bufferDuration))){
                    player.setSeekParameters(SeekParameters.EXACT)
                }
            }

            player.seekTo(newSeekPosition)  //  window is chosen previously
        }
    }

    private fun updateRestrictedList() {
        restrictList?.clear()
        restrictList = arrayListOf()
        restrictList!!.addAll(speedDetailSet.toList())
        restrictList!!.sortedWith(speedDetailsComparator)
    }

    private fun isEditActionSpeedChange() = editAction == EditAction.FAST || editAction == EditAction.SLOW

    private fun getCorrectedTimeBarPosition(): Long {
        return if(player.currentWindowIndex == 0){
            if(player.currentPosition == 0L && isEditActionSpeedChange())    //  we are getting 0 as the current position when we make the jump this is a problem
                bufferDuration
            else
                player.currentPosition
        }else{  //  exoplayer can be messed up
            if(player.currentPosition + bufferDuration > maxDuration){
                maxDuration
            }else {
                player.currentPosition + bufferDuration
            }
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
        const val PREVIEW_ACTION         = "com.hipoint.snipback.previewTile"
        const val VIRTUAL_TO_REAL_ACTION = "com.hipoint.snipback.virtualToReal"
        const val EXTEND_TRIM_ACTION     = "com.hipoint.snipback.extendTrim"
        const val DISMISS_ACTION         = "com.hipoint.snipback.dismiss"

        private var tries              = 0
        private var currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        private var speedDetailSet     = mutableSetOf<SpeedDetails>()

        private var uiRangeSegments: ArrayList<RangeSeekbarCustom>? = null
        private var restrictList   : ArrayList<SpeedDetails>?       = null //  speed details to prevent users from selecting an existing edit
        private var progressTracker: ProgressTracker?               = null

        var saveAction: SaveActionType        = SaveActionType.CANCEL
        var fragment  : VideoEditingFragment? = null

        @JvmStatic
        fun newInstance(aSnip: Snip?, thumbnailExtractionStarted: Boolean): VideoEditingFragment {
            fragment = VideoEditingFragment()
            val bundle = Bundle()
            bundle.putParcelable("snip", aSnip)
            bundle.putBoolean("thumbnailExtractionStarted", thumbnailExtractionStarted)
            fragment!!.arguments = bundle

            speedDetailSet.clear()
            uiRangeSegments = null
            restrictList    = null
            saveAction      = SaveActionType.CANCEL

            return fragment!!
        }
    }

    /**
     * returns the closest existing selection above the current cursor position
     */
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

    /**
     * returns the closest existing selection below the current cursor position
     */
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
     * prevents overlapping edits by returning the limiting value or -1 is the cursor position is acceptable
     */
    private fun checkOverlappingTS(nextPosition: Long): Long {
        if((startingTimestamps == -1L && endingTimestamps == maxDuration) || restrictList.isNullOrEmpty()){ //  cases when change is not required
            return -1L
        }

        restrictList?.let { restrictions->
            for (it in restrictions){
                if (isEditExisting && tmpSpeedDetails == it) {  //   we are looking at the current segment
                    continue
                }
                if(/*startingTimestamps == endingTimestamps ||*/
                    it.timeDuration!!.first == it.timeDuration!!.second){   //  if the start and end TS are the same or saved TS are the same no change because edit is ongoing
                    return -1L
                }
                if(editSeekAction == EditSeekControl.MOVE_END){
                    if(it.startWindowIndex == player.currentWindowIndex) {
                        if (startingTimestamps <= it.timeDuration!!.first && nextPosition >= it.timeDuration!!.first && player.currentWindowIndex == 0) {   //  if we are in the the first window and the next position is above the saved startingTS
                            return it.timeDuration!!.first
                        }
                        val tmp = (it.timeDuration!!.first - bufferDuration)
                        if(startingTimestamps <= it.timeDuration!!.first && nextPosition >= tmp && player.currentWindowIndex == 1){ //  if we are in the second window and next position is above the saved startingTS
                            return tmp
                        }
                    }
                }else if(editSeekAction == EditSeekControl.MOVE_START){
                    if(endingTimestamps >= it.timeDuration!!.second && nextPosition <= it.timeDuration!!.second && player.currentWindowIndex == 0){   //  if we are in the first window and nextIndex is below the saved secondTS
                        return it.timeDuration!!.second
                    }
                     val tmp = (it.timeDuration!!.second - bufferDuration)
                    if(endingTimestamps >= it.timeDuration!!.second && nextPosition <= tmp && player.currentWindowIndex == 1){ //  if we are in the second window and next position is above the saved startingTS
                        return tmp
                    }
                }
            }
        }
        return -1L
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
        timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
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
        timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputName = "VID_$timeStamp.mp4"
        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
                operation = IVideoOpListener.VideoOp.TRIMMED,
                clips = arrayListOf(clip.absolutePath),
                outputPath = "${clip.parent}/$outputName",
                startTime = startTime.toFloat(),
                endTime = endTime.toFloat(),
                comingFrom = CurrentOperation.VIDEO_EDITING))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
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
        }
    }

    private fun pauseVideo(){
        player.playWhenReady = false
        paused = true
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun playVideo(){
        player.playWhenReady = true
        paused = false
    }

//  implementations

    /**
     * our query result is available here.
     *
     * @param hdSnips List<Hd_snips>?
     */
    override suspend fun queryResult(hdSnips: List<Hd_snips>?) {
        hdSnips?.let{
            if(it.size < 2){
                setupForTrimOnly()
                return@let
            }

            val sorted = it.sortedBy { hdSnips -> hdSnips.video_path_processed.toLowerCase() }

            sorted.forEach { item -> Log.d(TAG, "queryResult: ${item.video_path_processed}") }

            if(sorted[0].video_path_processed == sorted[1].video_path_processed){       //  there is no buffer
                setupForTrimOnly()
            }else {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(sorted[0].video_path_processed)
                    bufferDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                    retriever.setDataSource(snip!!.videoFilePath)
                    videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                    retriever.release()

                    originalBufferDuration = bufferDuration
                    originalVideoDuration  = videoDuration

                    if(bufferDuration > 0L && videoDuration > 0L) {
                        trimOnly = false
                        bufferHdSnipId = sorted[0].hd_snip_id
                        addToVideoPlayback(sorted[0].video_path_processed)
                    }else {
                        setupForTrimOnly()
                    }

                }catch (e: IllegalArgumentException){
                    setupForTrimOnly()
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun setupForTrimOnly() {
        trimOnly = true
        showContentUnavailableToast()
        clearSelectedRanges()
        originalBufferDuration = bufferDuration
        originalVideoDuration = videoDuration
        addToVideoPlayback("")
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
                 AppClass.showInGallery.add(File("${clip.parent}/$outputName").nameWithoutExtension)
                Toast.makeText(requireContext(), "Saving Edited Video", Toast.LENGTH_SHORT).show()
            }
        }else{
            createModifiedVideo()
        }
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
            timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
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
        timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoPath = snip!!.videoFilePath
        val concatOutputPath = "${File(videoPath).parent}/$timeStamp.mp4"
        val taskList = arrayListOf<VideoOpItem>()

        if(!trimOnly) {
            val concatenateTask = VideoOpItem(
                    operation = IVideoOpListener.VideoOp.CONCAT,
                    clips = arrayListOf(bufferPath, snip!!.videoFilePath),
                    outputPath = concatOutputPath,
                    comingFrom = CurrentOperation.VIDEO_EDITING)

            taskList.apply { add(concatenateTask) }
            VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.CONCAT)
            VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.TRIMMED)
            if (saveAction == SaveActionType.SAVE && bufferPath.isNotNullOrEmpty())
                VideoService.ignoreResultOf.add(IVideoOpListener.VideoOp.TRIMMED)
        } else {
            val trimmedOutputPath = "${File(videoPath).parent}/trimmed-$timeStamp.mp4"
            val trimTask = VideoOpItem(
                    operation = IVideoOpListener.VideoOp.TRIMMED,
                    clips = arrayListOf(videoPath),
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
        if(isEditExisting) {
            return
        }

        isEditExisting = true
        newSpeedChangeStart = false

        if(uiRangeSegments?.size!! >= speedDetailSet.size){
            if(uiRangeSegments!!.size == speedDetailSet.size)
                speedDetailSet.remove(tmpSpeedDetails)

            val ref = uiRangeSegments?.removeAt(uiRangeSegments?.size!! - 1)    //  this needs to be called outside after checking size same as speedDetails
            timebarHolder.removeView(ref)
            tmpSpeedDetails = null
            segmentCount -= 1
        }

        tmpSpeedDetails = speedDetails

        player.setPlaybackParameters(PlaybackParameters(speedDetails.multiplier.toFloat()))
        updateCurrentEditIndex(speedDetails)

        if(tmpSpeedDetails!!.startWindowIndex == 1) {
            player.setSeekParameters(SeekParameters.EXACT)
            player.seekTo(tmpSpeedDetails!!.startWindowIndex,
                    tmpSpeedDetails!!.timeDuration!!.first - bufferDuration)
        }else{
            player.setSeekParameters(SeekParameters.EXACT)
            player.seekTo(tmpSpeedDetails!!.startWindowIndex,
                    tmpSpeedDetails!!.timeDuration!!.first)
        }
        startingTimestamps = speedDetails.timeDuration!!.first
        endingTimestamps = speedDetails.timeDuration!!.second

        //  setting up the right UI for the edit action
        val startValue = startingTimestamps * 100 / maxDuration
        val endValue = endingTimestamps * 100 / maxDuration
        setupRangeMarker(startValue.toFloat(), endValue.toFloat())
        startRangeUI()
        editSeekAction = EditSeekControl.MOVE_START
        editAction = if(speedDetails.isFast) EditAction.FAST else EditAction.SLOW
        setIconActive()
        acceptRejectHolder.visibility = View.VISIBLE

        if (isSeekbarShown) {  //  otherwise we won't have a clue as to where it is
            seekBar.hideScrubber()
            isSeekbarShown = false
        }
    }

    /**
     * This method searches through the existing set of speed details and sets the currentEditSegment as index.
     * this was created because there was an issue where the set order was changing.
     */
    private fun updateCurrentEditIndex(speedDetails: SpeedDetails) {
        speedDetailSet.forEachIndexed { index, it ->
            Log.d(
                    TAG,
                    "editPoint: index = $index, speed details = ${it.timeDuration!!.first}, ${it.timeDuration!!.second}"
            )
            if (it.timeDuration!!.first == speedDetails.timeDuration!!.first && it.timeDuration!!.second == speedDetails.timeDuration!!.second) {
                currentEditSegment = index
            }
        }
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
        private var isTrackingProgress = false

        override fun run() {
            if(context != null) {
                if (isChangeAccepted) { //  Edit is present
                    val currentPosition = if(player.currentWindowIndex == 0)
                        player.currentPosition
                    else {
                        val adjust = player.currentTimeline.getWindow(0, Timeline.Window()).durationMs
                        player.currentPosition + adjust
                    }
                    var isPresent = false
                    var tmp: SpeedDetails? = null
                    speedDetailList.forEach{
                        if (currentPosition in it.timeDuration!!.first..it.timeDuration!!.second &&
                            !(it.timeDuration!!.first == it.timeDuration!!.second &&
                                    it.startWindowIndex == it.endWindowIndex)) {
                            isPresent = true
                            tmp = it
                            return@forEach
                        }
                    }
                    if(isPresent){
                        if (tmp!!.isFast) {
                            val overlayColour = resources.getColor(R.color.blueOverlay,
                                    requireContext().theme)
                            if (!colourOverlay.isShown) {
                                colourOverlay.visibility = View.VISIBLE
                            }
                            colourOverlay.setBackgroundColor(overlayColour)
                            player.setPlaybackParameters(PlaybackParameters(tmp!!.multiplier.toFloat()))
                        } else {
                            val overlayColour = resources.getColor(R.color.greenOverlay,
                                    requireContext().theme)
                            if (!colourOverlay.isShown) {
                                colourOverlay.visibility = View.VISIBLE
                            }
                            colourOverlay.setBackgroundColor(overlayColour)
                            player.setPlaybackParameters(PlaybackParameters(1 / tmp!!.multiplier.toFloat()))
                        }

                        handler?.postDelayed(this, 20 /* ms */)
                        return
                    } else {
                        if (player.playbackParameters != PlaybackParameters(1F))
                                player.setPlaybackParameters(PlaybackParameters(1F))
                            else {
                                colourOverlay.visibility = View.GONE
                            }
                    }
                } else {
                    if (player.playbackParameters != PlaybackParameters(1F))
                        player.setPlaybackParameters(PlaybackParameters(1F))
                    else {
                        colourOverlay.visibility = View.GONE
                    }
                }
                handler?.postDelayed(this, 20 /* ms */)
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
            isTrackingProgress = isAccepted
            if(handler == null && isAccepted){
                handler = Handler()
                handler!!.post(this)
            }
        }

        fun setSpeedDetails(speedDetails: ArrayList<SpeedDetails>) {
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
            colourOverlay.visibility = View.GONE
            handler?.removeCallbacksAndMessages(null)
            handler = null
            isTrackingProgress = false
        }

        fun isCurrentlyTracking(): Boolean {
            return isTrackingProgress && handler != null
        }
    }
}