package com.hipoint.snipback.fragment

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.VectorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
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
import com.hipoint.snipback.adapter.EditChangeListAdapter
import com.hipoint.snipback.adapter.TimelinePreviewAdapter
import com.hipoint.snipback.dialog.ExitEditConfirmationDialog
import com.hipoint.snipback.dialog.ProcessingDialog
import com.hipoint.snipback.dialog.SaveEditDialog
import com.hipoint.snipback.enums.EditAction
import com.hipoint.snipback.enums.EditSeekControl
import com.hipoint.snipback.listener.IReplaceRequired
import com.hipoint.snipback.listener.ISaveListener
import com.hipoint.snipback.listener.IVideoOpListener
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.videoControl.SpeedDetails
import com.hipoint.snipback.videoControl.VideoOpItem
import com.hipoint.snipback.videoControl.VideoService
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kibotu.fastexoplayerseeker.SeekPositionEmitter
import net.kibotu.fastexoplayerseeker.seekWhenReady
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class VideoEditingFragment : Fragment(), ISaveListener {
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
    private lateinit var playerView           : PlayerView
    private lateinit var player               : SimpleExoPlayer
    private lateinit var defaultBandwidthMeter: DefaultBandwidthMeter
    private lateinit var dataSourceFactory    : DataSource.Factory
    private lateinit var mediaSource          : MediaSource

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
    var isSeekbarShown = true

    private var currentSpeed       = 3
    private var startingTimestamps = -1L
    private var endingTimestamps   = -1L
    private var speedDuration      = Pair<Long, Long>(0, 0)
    private var speedDetailSet     = mutableSetOf<SpeedDetails>()
    private var segmentCount       = 0
    private var editAction         = EditAction.NORMAL
    private var editSeekAction     = EditSeekControl.MOVE_NORMAL

    private var tmpSpeedDetails: SpeedDetails? = null
    private var uiRangeSegments: ArrayList<RangeSeekbarCustom>? = null

    private var restrictList: List<SpeedDetails>? = null    //  speed details to prevent users from selecting an existing edit

    private val progressTracker: ProgressTracker by lazy { ProgressTracker(player) }
    private val replaceRequired: IReplaceRequired by lazy { requireActivity() as AppMainActivity }

    //  dialogs
    private var saveDialog: SaveEditDialog? = null
    private var exitConfirmation: ExitEditConfirmationDialog? = null
    private var processingDialog: ProcessingDialog? = null

    private var thumbnailExtractionStarted:Boolean = false

    private val previewTileReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val previewThumbs = File(intent.getStringExtra("preview_path")!!)
                showThumbnailsIfAvailable(previewThumbs)
            }
        }
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

        bindViews()
        bindListeners()
        setupPlayer()

        return rootView
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(previewTileReceiver, IntentFilter(PREVIEW_ACTION))
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(previewTileReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        if (this::player.isInitialized)
            player.apply {
                playWhenReady = false
                stop(true)
                setVideoSurface(null)
                release()
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
         * todo: ensure the start point is the one moving and end point if selected remains as is.
         */
        start.setOnClickListener {
            startRangeUI()

            editSeekAction = EditSeekControl.MOVE_START
            if (tmpSpeedDetails != null) {
                //  this means the end point was already set
                //  the user wishes to move the starting point only
                endingTimestamps = player.currentPosition
                acceptRejectHolder.visibility = View.VISIBLE
            }
        }

        /**
         * locks in the start edit point and sets up the rangeSeekBar for indication.
         * range indicator starts off with tmpSpeedDetails which should then be replaced with the actual details
         */
        end.setOnClickListener {
            start.setBackgroundResource(R.drawable.end_curve)
            end.setBackgroundResource(R.drawable.end_curve_red)
            val currentPosition = player.currentPosition
            editSeekAction = EditSeekControl.MOVE_END

            if (checkSegmentTaken(currentPosition))   // checking to see if the start positions is acceptable
                return@setOnClickListener
            Log.d(TAG, "end clicked : starting time stamp = $startingTimestamps, current position = $currentPosition")
            if (startingTimestamps != currentPosition) {
                startingTimestamps = currentPosition  //    take the starting point when end is pressed
                val startValue = (startingTimestamps * 100 / player.duration).toFloat()

                speedDuration = Pair(startingTimestamps, startingTimestamps)    //  we only have the starting position now
                tmpSpeedDetails = SpeedDetails(editAction == EditAction.FAST, currentSpeed, speedDuration)
                speedDetailSet.add(tmpSpeedDetails!!)

                setupRangeMarker(startValue, startValue)    //  initial value for marker
                if (timebarHolder.indexOfChild(uiRangeSegments!!.last()) < 0)  //  View doesn't exist and can be added
                    timebarHolder.addView(uiRangeSegments!!.last())
            }
            acceptRejectHolder.visibility = View.VISIBLE
        }

        extentTextBtn.setOnClickListener {
//            extent.setImageResource(R.drawable.ic_extent_red)
            val dwg = ContextCompat.getDrawable(requireContext(), R.drawable.ic_extent_red)
            dwg?.bounds = Rect(0, 0, 20, 20)
            extentTextBtn.setCompoundDrawablesWithIntrinsicBounds(null, dwg, null, null)
            extentTextBtn.setTextColor(resources.getColor(R.color.colorPrimaryDimRed))
            playCon1.visibility = View.VISIBLE
            playCon2.visibility = View.GONE
        }

        back.setOnClickListener {
            if(speedDetailSet.isNotEmpty()) //  todo: should work for all edits not just speed change
                showDialogConfirmation()
            else
                requireActivity().supportFragmentManager.popBackStack()
        }

        back1.setOnClickListener {
            speedIndicator.visibility = View.GONE
            playCon1.visibility       = View.GONE
            changeList.visibility     = View.GONE
            playCon2.visibility       = View.VISIBLE
            reject.performClick()
        }

        save.setOnClickListener {
            showDialogSave()
//            VideoUtils(this).changeSpeed(File(snip!!.videoFilePath), speedDetailSet.toMutableList() as ArrayList<SpeedDetails>, "${File(snip!!.videoFilePath).parent}/output.mp4")
        }

        /**
         * accepts the changes to the edit that were made
         * todo: place checks to ensure that edit is completed before the details are accepted
         * takes in the end position and updates the rangeSeekBar.
         * progress tracker is run to make sure playback is in accordance with the edit.
         * reset the UI for new edit
         */
        accept.setOnClickListener {
            val currentPosition = player.currentPosition

            if (checkSegmentTaken(currentPosition) || tmpSpeedDetails == null)  //  checking to see if the end positions is acceptable and something is available
                return@setOnClickListener

            if (editSeekAction == EditSeekControl.MOVE_END) {
                if (endingTimestamps != currentPosition) {
                    endingTimestamps = currentPosition
                }
            } else {
                if (startingTimestamps != currentPosition) {
                    startingTimestamps = currentPosition
                }
            }
            //  durations are correct and changes can be accepted

            speedDetailSet.remove(tmpSpeedDetails)
            progressTracker.removeSpeedDetails(tmpSpeedDetails!!)
            tmpSpeedDetails = null

            speedDuration = Pair(startingTimestamps, endingTimestamps)
            speedDetailSet.add(SpeedDetails(editAction == EditAction.FAST, currentSpeed, speedDuration))

            progressTracker.setChangeAccepted(true)
            progressTracker.setSpeed(currentSpeed.toFloat())
            progressTracker.setSpeedDetails(speedDetailSet.toMutableList() as ArrayList<SpeedDetails>)
            progressTracker.run()

            isEditOnGoing = false
            isSpeedChanged = true

            val startValue = (startingTimestamps * 100 / player.duration).toFloat()
            val endValue = (endingTimestamps * 100 / player.duration).toFloat()

            setupRangeMarker(startValue, endValue)
            startRangeUI()
            resetPlaybackUI()

            restrictList = speedDetailSet.sortedWith { s1, s2 ->
                (s1.timeDuration?.first!! - s2?.timeDuration!!.first).toInt()
            }.toList()

            if(editListAdapter == null) {
                setupEditList()
            }else{
                updateEditList()
            }
            changeList.visibility = View.GONE   //  todo: animate changelist in and out on visibility change
        }

        /**
         * progressTracker speed is reset to normal
         * start and end time stamps are reset
         * edit segmentCount is decremented
         * UI is reset for new edit
         * */
        reject.setOnClickListener {
            /*showDialogdelete()*/
            progressTracker.setChangeAccepted(false)
            currentSpeed = 1
            progressTracker.setSpeed(currentSpeed.toFloat())

            if (tmpSpeedDetails != null) {
                progressTracker.removeSpeedDetails(tmpSpeedDetails!!)
                val ref = uiRangeSegments?.removeAt(uiRangeSegments?.size!! - 1)
                timebarHolder.removeView(ref)
                tmpSpeedDetails = null
            }

            startingTimestamps = -1L
            endingTimestamps = -1L
            player.setPlaybackParameters(PlaybackParameters(1F))
            isSpeedChanged = false
            isEditOnGoing = false

            segmentCount -= 1
            startRangeUI()
            resetPlaybackUI()

            restrictList = speedDetailSet.sortedWith { s1, s2 ->
                (s1.timeDuration?.first!! - s2?.timeDuration!!.first).toInt()
            }.toList()
        }

        playBtn.setOnClickListener {
            if (player.currentPosition >= player.duration)
                player.seekTo(0)
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
            player.seekTo(0)
        }

        //  prevent touchs on seekbar
        seekBar.setOnTouchListener { _, _ ->
            true
        }
        seekBar.isClickable = false

        slowTextBtn.setOnClickListener {
            player.playWhenReady = false
            val currentPosition = player.currentPosition
            speedDetailSet.forEach {
                if (currentPosition in it.timeDuration?.first!!..it.timeDuration?.second!!) {
                    Toast.makeText(requireContext(), "Cannot choose existing segment", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            setupForEdit()
            editAction = EditAction.SLOW
            segmentCount += 1   //  a new segment is active

            if (uiRangeSegments == null)
                uiRangeSegments = arrayListOf()

            if (segmentCount > uiRangeSegments?.size ?: 0) {
                uiRangeSegments?.add(RangeSeekbarCustom(requireContext()))
            }
        }

        speedTextBtn.setOnClickListener {
            player.playWhenReady = false
            val currentPosition = player.currentPosition
            speedDetailSet.forEach {
                if (currentPosition in it.timeDuration?.first!!..it.timeDuration?.second!!) {
                    Toast.makeText(requireContext(), "Cannot choose existing segment", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            setupForEdit()
            editAction = EditAction.FAST
            segmentCount += 1   //  a new segment is active

            if (uiRangeSegments == null)
                uiRangeSegments = arrayListOf()

            if (segmentCount > uiRangeSegments?.size ?: 0) {
                uiRangeSegments?.add(RangeSeekbarCustom(requireContext()))
            }
        }

        speedIndicator.setOnClickListener {
            speedIndicator.text = changeSpeedText()
            progressTracker.setSpeed(currentSpeed.toFloat())
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

        player.seekTo(0)
        seekBar.showScrubber()
        player.playWhenReady = false

        blockEditOptions(false)
    }

    /**
     * shows the edits as a clickable list
     */
    private fun setupEditList(){
        val editList  = arrayListOf<SpeedDetails>()
        editList.addAll(speedDetailSet.toMutableList().sortedWith(speedDetailsComparator))
        editListAdapter = EditChangeListAdapter(requireContext(), editList)
        changeList.adapter = editListAdapter
        changeList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        Log.d(TAG, "setupEditList: list created")
    }

    /**
     * Updates the edits list
     */
    private fun updateEditList(){
        if(changeList.visibility != View.VISIBLE)
            changeList.visibility = View.VISIBLE

        editListAdapter?.updateList(speedDetailSet.toMutableList().sortedWith(speedDetailsComparator))
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
        endingTimestamps   = player.duration

        if(editListAdapter != null){    //  already some edit was saved here
            changeList.visibility = View.VISIBLE
        }
    }

    private fun blockEditOptions(shouldBlock: Boolean) {
        val enable                 = !shouldBlock
        extentTextBtn.isEnabled    = enable
        cutTextBtn.isEnabled       = enable
        highlightTextBtn.isEnabled = enable
        slowTextBtn.isEnabled      = enable
        speedTextBtn.isEnabled     = enable

        extentTextBtn.isClickable    = enable
        cutTextBtn.isClickable       = enable
        highlightTextBtn.isClickable = enable
        slowTextBtn.isClickable      = enable
        speedTextBtn.isClickable     = enable

        if (enable) {
            extentTextBtn.alpha    = 1.0F
            cutTextBtn.alpha       = 1.0F
            highlightTextBtn.alpha = 1.0F
            slowTextBtn.alpha      = 1.0F
            speedTextBtn.alpha     = 1.0F
        } else {
            extentTextBtn.alpha    = 0.5F
            cutTextBtn.alpha       = 0.5F
            highlightTextBtn.alpha = 0.5F
            slowTextBtn.alpha      = 0.5F
            speedTextBtn.alpha     = 0.5F
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
        if (speedDetailSet.size > 1) {
            speedDetailSet.forEach {
                if (currentPosition in it.timeDuration?.first!!..it.timeDuration?.second!!) {
                    Toast.makeText(requireContext(), "segment is already taken", Toast.LENGTH_SHORT).show()
                    return true
                }
            }
        }
        return false
    }

    /**
     * Sets up the crystal range marker for displaying the selected ranges.
     *
     * creates a rangeSeekBar with parameters based on @param startValue and @param endValue
     * and the details present in the speedDetails sorted set
     */
    private fun setupRangeMarker(startValue: Float, endValue: Float) {

        if (uiRangeSegments == null) {
            uiRangeSegments = arrayListOf()
            uiRangeSegments?.add(RangeSeekbarCustom(requireContext()))
            val layoutParam = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            uiRangeSegments?.last()?.layoutParams = layoutParam
        }

        val colour =
                if (!speedDetailSet.isNullOrEmpty()) {
                    if (speedDetailSet.sortedWith { s1, s2 ->
                                (s1.timeDuration?.first!! - s2?.timeDuration!!.first).toInt()
                            }.toList().last().isFast)
                        resources.getColor(R.color.blueOverlay, context?.theme)
                    else
                        resources.getColor(R.color.greenOverlay, context?.theme)
                } else
                    resources.getColor(android.R.color.transparent, context?.theme)

        val leftThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
                resources.getColor(android.R.color.holo_green_dark, context?.theme))
        val rightThumbImageDrawable = getBitmap(ResourcesCompat.getDrawable(resources, R.drawable.ic_thumb, context?.theme) as VectorDrawable,
                resources.getColor(android.R.color.holo_red_light, context?.theme))

        val height = (35 * resources.displayMetrics.density + 0.5f).toInt()
        val padding = (8 * resources.displayMetrics.density + 0.5f).toInt()

        uiRangeSegments?.last()?.apply {
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
            setLeftThumbHighlightBitmap(leftThumbImageDrawable)
            setRightThumbHighlightBitmap(rightThumbImageDrawable)
            setMinValue(0F)
            setMaxValue(100F)
            setMinStartValue(startValue).apply()
//            setGap(endValue - startValue)
            setMaxStartValue(endValue).apply()

            setOnTouchListener { _, _ -> true }
        }
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

    /**
     * Setting up the player to play the require snip for editing
     */
    private fun setupPlayer() {
        val videoUri = Uri.parse(snip!!.videoFilePath)

        previewBarProgress.visibility = View.VISIBLE

        if(!thumbnailExtractionStarted) {
            CoroutineScope(Default).launch {
                getVideoPreviewFrames()
            }
        }else {
            val parentFilePath = File(snip!!.videoFilePath).parent
            showThumbnailsIfAvailable(File("$parentFilePath/previewThumbs/"))
        }

        defaultBandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()
        dataSourceFactory = DefaultDataSourceFactory(activity,
                Util.getUserAgent(requireActivity(), "mediaPlayerSample"), defaultBandwidthMeter)

        mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(videoUri)

        player = SimpleExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        player.prepare(mediaSource)
        player.repeatMode = Player.REPEAT_MODE_OFF

        playerView.apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            controllerAutoShow = false
            controllerShowTimeoutMs = -1
            controllerHideOnTouch = false
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
        })

        initSwipeControls()
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

    private fun showDialogdelete() {
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
        var higher = player.duration
        var lower = 0L

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
        player.seekWhenReady(emitter)
                .subscribe({
                    Log.v(TAG, "seekTo=${it.first} isSeeking=${it.second}")
                }, { Log.e(TAG, "${it.message}") })
                .addTo(subscriptions)

        swipeDetector.onScroll { percentX, _ ->
            // left swipe is positive, right is negative
            val duration = player.duration
            val maxPercent = 0.75f
            val scaledPercent = percentX * maxPercent
            val percentOfDuration = scaledPercent * -1 * duration + startScrollingSeekPosition
            // shift in position domain and ensure circularity
            var newSeekPosition = percentOfDuration.roundToLong()
            /*((percentOfDuration + duration) % duration).roundToLong().absoluteValue*/

            if (restrictList.isNullOrEmpty() || restrictList?.size!! < speedDetailSet.size) {
                restrictList = speedDetailSet.sortedWith { s1, s2 ->
                    (s1.timeDuration?.first!! - s2?.timeDuration!!.first).toInt()
                }.toList()
            }

            higher = nearestExistingHigherTS(player.currentPosition)
            lower = nearestExistingLowerTS(player.currentPosition)

            if (isEditOnGoing && !uiRangeSegments.isNullOrEmpty()) {
                if (isSeekbarShown && editSeekAction == EditSeekControl.MOVE_END) {
                    seekBar.hideScrubber()
                    isSeekbarShown = false
                }

                when (editSeekAction) {
                    EditSeekControl.MOVE_END -> {
                        // only the end point is being manipulated
                        // prevent the user from seeking beyond the fixed start point

                        if (newSeekPosition < startingTimestamps) {
                            newSeekPosition = startingTimestamps
                        }
                        if (newSeekPosition > higher)
                            newSeekPosition = higher

                        uiRangeSegments?.last()?.setMaxStartValue((newSeekPosition * 100 / player.duration).toFloat())?.apply()
                    }
                    EditSeekControl.MOVE_START -> {
                        // only the starting point is being manipulated
                        // prevent the user from seeking beyond the fixed start point

                        if (newSeekPosition >= endingTimestamps && endingTimestamps != 0L) {
                            newSeekPosition = endingTimestamps
                        }
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

                        uiRangeSegments?.last()?.setMinStartValue((newSeekPosition * 100 / player.duration).toFloat())?.apply()
                        /*uiRangeSegments?.last()?.setMaxStartValue(endingTimestamps.toFloat())?.apply()*/
                    }
                    EditSeekControl.MOVE_NORMAL -> {
                    }
                }
            }

            if (!isSeekbarShown && !isEditOnGoing) {
                seekBar.showScrubber()
                isSeekbarShown = true
            }
            if (newSeekPosition < 0)
                newSeekPosition = 0
            else if (newSeekPosition > player.duration)
                newSeekPosition = player.duration
            emitter.seekFast(newSeekPosition)
        }
    }

    /**
     * Populates preview frames in the seekBar area from the video
     */
    private fun getVideoPreviewFrames() {
        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
                operation = IVideoOpListener.VideoOp.FRAMES,
                clip1 = snip!!.videoFilePath,
                clip2 = "",
                outputPath = File(snip!!.videoFilePath).parent!!,
                speedDetailsList = speedDetailSet.toMutableList() as ArrayList<SpeedDetails>))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
    }

    companion object {
        val PREVIEW_ACTION = "com.hipoint.snipback.previewTile"
        private var tries = 0
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

    inner class ProgressTracker(private val player: Player) : Runnable {

        private val handler: Handler = Handler()
        private var speedDetailList: ArrayList<SpeedDetails> = arrayListOf()

        private var currentSpeed: Float = 1F
        private var isChangeAccepted: Boolean = false

        override fun run() {
            if(context != null) {
                if (isChangeAccepted) { //  Edit is present
                    val currentPosition = player.currentPosition
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

                            handler.postDelayed(this, 200 /* ms */)
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
                handler.postDelayed(this, 200 /* ms */)
            }
        }

        init {
            handler.post(this)
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
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun nearestExistingLowerTS(current: Long): Long {
        if(restrictList.isNullOrEmpty()){
            return 0L
        }

        val diff = arrayListOf<Long>()
        restrictList?.forEach {
            val one = it.timeDuration!!.first - current
            val two = it.timeDuration!!.second - current
            if(one < 0)
                diff.add(one)
            if(two < 0)
                diff.add(two)
            /*if (it.timeDuration!!.first < lower || it.timeDuration!!.second < lower) {
                lower = if (lower - it.timeDuration!!.first < lower - it.timeDuration!!.second) {  //  value closer to current
                    it.timeDuration!!.first
                } else {
                    it.timeDuration!!.second
                }
            }*/
        }

        return if(diff.isEmpty()){
            0
        }else {
            diff.sortDescending()
            current + diff[0]   //   + because the value is already negative
        }
        /*return if (lower == current)
            0L
        else
            lower*/
    }

    private fun nearestExistingHigherTS(current: Long): Long {
        if(restrictList.isNullOrEmpty()){
            return player.duration
        }

        val diff = arrayListOf<Long>()
        restrictList?.forEach {
            val one = it.timeDuration!!.first - current
            val two = it.timeDuration!!.second - current

            if(one > 0)
                diff.add(one)
            if(two > 0)
                diff.add(two)
            /*
            if (it.timeDuration!!.first > higher || it.timeDuration!!.second > higher) {
                higher = if (it.timeDuration!!.first - current < it.timeDuration!!.second - current) {  //  value closer to current
                    it.timeDuration!!.first
                } else {
                    it.timeDuration!!.second
                }
            }*/
        }
        return if(diff.isEmpty()){
            player.duration
        }else {
            diff.sort()
            diff[0] + current
        }
        /*return if (higher == current)
            player.duration
        else
            higher*/
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
     * saves as a new file
     * */
    override fun saveAs() {
        saveDialog?.dismiss()
        //  show in gallery
        Log.d(TAG, "saveAs: will save as child of snip id = ${snip!!.snip_id}")
        replaceRequired.parent(snip!!.snip_id)

        val (clip, outputName) = createSpeedChangedVideo()
        (requireActivity() as AppMainActivity).showInGallery.add(File("${clip.parent}/$outputName").nameWithoutExtension)
        Toast.makeText(requireContext(), "Saving Edited Video", Toast.LENGTH_SHORT).show()
    }

    /**
     * save over existing file
     * */
    override fun save() {
        saveDialog?.dismiss()
        val (clip, outputName) = createSpeedChangedVideo()
        replaceRequired.replace(clip.absolutePath, "${clip.parent}/$outputName")
        Toast.makeText(requireContext(), "Saving Edited Video", Toast.LENGTH_SHORT).show()
    }

    /**
     * Creates a video with the required speed changes
     *
     * @return Pair<File, String>   Pair of <Parent file, output filename>
     */
    private fun createSpeedChangedVideo(): Pair<File, String> {
        val clip = File(snip!!.videoFilePath)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputName = "VID_$timeStamp.mp4"
        val intentService = Intent(requireContext(), VideoService::class.java)
        val task = arrayListOf(VideoOpItem(
                operation = IVideoOpListener.VideoOp.SPEED,
                clip1 = clip.absolutePath,
                clip2 = "",
                outputPath = "${clip.parent}/$outputName",
                speedDetailsList = speedDetailSet.toMutableList() as ArrayList<SpeedDetails>))
        intentService.putParcelableArrayListExtra(VideoService.VIDEO_OP_ITEM, task)
        VideoService.enqueueWork(requireContext(), intentService)
        return Pair(clip, outputName)
    }

    /**
     * cancels the save action
     * */
    override fun cancel() {
        saveDialog?.dismiss()
    }

    /**
    * exit from this fragment
    * */
    override fun exit() {
        exitConfirmation?.dismiss()
        reject.performClick()
        requireActivity().supportFragmentManager.popBackStack()
    }

    fun showProgress(){
        if(processingDialog == null)
            processingDialog = ProcessingDialog()
        processingDialog?.show(requireActivity().supportFragmentManager, PROCESSING_DIALOG)
    }

    fun hideProgress(){
        processingDialog?.dismiss()
    }
}

/*
for smooth seeking? todo: give this a shot
* private static final int RENDERER_COUNT = 1;
    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 64; // default 256
.....

        // 1. Instantiate the player. RENDERER_COUNT is the number of renderers to be used.
        // In this case it’s 1 since we will only use a video renderer. If you need audio and video renderers it should be 2.
        mExoPlayer = ExoPlayer.Factory.newInstance(RENDERER_COUNT, 0, 0); //disable the buffer and rebuffer for better performance

        // 2. Construct renderer.
        Uri uri = Uri.parse(mVideoPath);
        Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);
        String userAgent = Util.getUserAgent(this, "Android Demo");
        DataSource dataSource = new DefaultUriDataSource(this, null, userAgent);
        ExtractorSampleSource sampleSource = new ExtractorSampleSource(uri, dataSource, allocator, BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);
        MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(
                this, sampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);

        // 3. Inject the renderer through prepare.
        mExoPlayer.prepare(videoRenderer);

        // 4. Pass the surface to the video renderer.
        mExoPlayer.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, holder.getSurface());

        // 5. Start playback.
        mExoPlayer.seekTo(currentVideoPosition);
* */