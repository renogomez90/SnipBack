package com.hipoint.snipback.fragment

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Color
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.*
import android.view.View.OnTouchListener
import android.widget.*
import android.widget.Chronometer.OnChronometerTickListener
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exozet.android.core.extensions.disable
import com.exozet.android.core.extensions.enable
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.math.Line
import com.google.android.exoplayer2.util.MimeTypes
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.Utils.SnipPaths
import com.hipoint.snipback.adapter.TagsRecyclerAdapter
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.dialog.ProcessingDialog
import com.hipoint.snipback.dialog.SettingsDialog
import com.hipoint.snipback.enums.TagColours
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.entities.Tags
import com.hipoint.snipback.room.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CreateTag : Fragment() {

    private lateinit var rootView       : View
    private lateinit var playVideo      : ImageButton
    private lateinit var mic            : ImageButton
    private lateinit var tick           : ImageButton
    private lateinit var share          : ImageButton
    private lateinit var delVoiceTag    : ImageButton
    private lateinit var delete         : ImageButton
    private lateinit var playBtn        : CheckBox
    private lateinit var tagText        : EditText
    private lateinit var afterBtn       : RadioButton
    private lateinit var beforeBtn      : RadioButton
    private lateinit var mChronometer   : Chronometer
    private lateinit var shareLater     : CheckBox
    private lateinit var linkLater      : CheckBox
    private lateinit var videoTagsList  : RecyclerView
    private lateinit var colorOne       : RadioButton
    private lateinit var colorTwo       : RadioButton
    private lateinit var colorThree     : RadioButton
    private lateinit var colorFour      : RadioButton
    private lateinit var colorFive      : RadioButton
    private lateinit var subContainer   : ConstraintLayout

    private var audioPlayer: MediaPlayer? = null
    private val currentFormat = 0

    private val output_formats = intArrayOf(
        MediaRecorder.OutputFormat.MPEG_4,
        MediaRecorder.OutputFormat.AAC_ADTS)

    private val file_exts = arrayOf(
        AUDIO_RECORDER_FILE_EXT_MP4,
        AUDIO_RECORDER_FILE_EXT_MP3)

    private var snip: Snip? = null
    private var recorder: MediaRecorder? = null

    private var isAudioPlaying = false
    private var timerSecond    = 0
    private var posToChoose    = 0

    private var savedAudioPath  : String               = ""
    private var tagsAdapter     : TagsRecyclerAdapter? = null
    private var processingDialog: ProcessingDialog?    = null
    private var lastAction      : LastAction           = LastAction.NO_ACTION

    private val paths by lazy { SnipPaths(requireContext()) }
    private val appRepository by lazy { AppRepository(AppClass.getAppInstance()) }
    private val pref: SharedPreferences by lazy { requireContext().getSharedPreferences(
        SettingsDialog.SETTINGS_PREFERENCES, Context.MODE_PRIVATE) }
    private val filename: String by lazy { prepareAudioFileName() }

    private fun prepareAudioFileName(): String {
        val filepath = paths.INTERNAL_VIDEO_DIR
        val file = File(filepath, AUDIO_RECORDER_FOLDER)
        if (!file.exists()) {
            file.mkdirs()
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val newFile = File(file.absolutePath + "/" + timeStamp + ".mp3")
        if(newFile.exists()){
            newFile.delete()
        }

        return file.absolutePath + "/" + timeStamp + ".mp3"
    }


    /**
     * gets snip to be used for tagging
     * if some action was performed while the snip was
     */
    private val taggingInfoReceiver: BroadcastReceiver by lazy {
        object : BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let{
                    snip = it.getParcelableExtra("snip") as Snip
                    if(processingDialog != null){
                        if(processingDialog!!.isVisible) {  //  dialog will only be shown when save was attempted
                            dismissProgress()
                            pref.edit().putInt("snipTagRequired", -1).apply()

                            when(lastAction){
                                LastAction.SAVE_TAG -> { saveTag() }

                                LastAction.PLAY_VIDEO -> {
                                    if (snip != null) {
                                        (requireActivity() as AppMainActivity).loadFragment(
                                            FragmentPlayVideo2.newInstance(
                                                snip),
                                            true)
                                    }
                                }

                                LastAction.DELETE -> {
                                    delete.performClick()
                                }

                                LastAction.SHARE -> {
                                    share.performClick()
                                }

                                LastAction.NO_ACTION -> {}
                            }
                        }
                    }
                }
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.create_tag_fragment, container, false)

        bindViews()
        bindListeners()
        return rootView
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(taggingInfoReceiver, IntentFilter(TAG_ACTION))

        if(snip == null){
            val snipId = pref.getInt("snipTagRequired", -1)
            if(snipId != -1) {  //  there is a saved snip
                CoroutineScope(IO).launch {
                    val tag = appRepository.getTagBySnipId(snipId)
                    if (tag == null) { //  this is a new snip, and looks like our receiver missed it
                        snip = appRepository.getSnipById(snipId)
                        if(snip != null)
                            pref.edit().putInt("snipTagRequired", -1).apply()
                    }
                }
            }
        }
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(taggingInfoReceiver)
        super.onPause()
    }

    /**
     * binds the views to layout
     */
    private fun bindViews() {
        snip = requireArguments().getParcelable("snip")

        tagText         = rootView.findViewById(R.id.tag_text)
        afterBtn        = rootView.findViewById(R.id.after_switch)
        beforeBtn       = rootView.findViewById(R.id.before_switch)
        tick            = rootView.findViewById(R.id.tick)
        share           = rootView.findViewById(R.id.share)
        playBtn         = rootView.findViewById(R.id.play_pause_btn)
        mic             = rootView.findViewById(R.id.mic)
        delVoiceTag     = rootView.findViewById(R.id.del_voice_tag)
        delete          = rootView.findViewById(R.id.delete)
        playVideo       = rootView.findViewById(R.id.edit)
        mChronometer    = rootView.findViewById(R.id.chronometer)
        shareLater      = rootView.findViewById(R.id.share_later)
        linkLater       = rootView.findViewById(R.id.link_later)
        videoTagsList   = rootView.findViewById(R.id.videoTagsList)
        colorOne        = rootView.findViewById(R.id.color_one)
        colorTwo        = rootView.findViewById(R.id.color_two)
        colorThree      = rootView.findViewById(R.id.color_three)
        colorFour       = rootView.findViewById(R.id.color_four)
        colorFive       = rootView.findViewById(R.id.color_five)
        subContainer    = rootView.findViewById(R.id.sub_cont)
    }

    /**
     * binds listeners to views
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun bindListeners() {

        afterBtn.setOnCheckedChangeListener { view, isChecked ->
            if (isChecked) {
                view.setTextColor(ResourcesCompat.getColor(resources,
                    R.color.red_tag,
                    requireContext().theme))
                posToChoose = AUDIO_AFTER
            } else {
                afterBtn.isChecked = false
                view.setTextColor(ResourcesCompat.getColor(resources, R.color.colorPrimaryWhite, requireContext().theme))
//                posToChoose = NO_AUDIO
            }
        }

        beforeBtn.setOnCheckedChangeListener { view, isChecked ->
            if (isChecked) {
                view.setTextColor(ResourcesCompat.getColor(resources, R.color.red_tag, requireContext().theme))
                posToChoose = AUDIO_BEFORE
            } else {
                beforeBtn.isChecked = false
                view.setTextColor(ResourcesCompat.getColor(resources,
                    R.color.colorPrimaryWhite,
                    requireContext().theme))
//                posToChoose = NO_AUDIO
            }
        }

        mChronometer.onChronometerTickListener = OnChronometerTickListener { arg0: Chronometer? ->
            //                if (!resume) {
            val time = SystemClock.elapsedRealtime() - mChronometer.base
            val h = (time / 3600000).toInt()
            val m = (time - h * 3600000).toInt() / 60000
            val s = (time - h * 3600000 - m * 60000).toInt() / 1000
            val t =
                (if (h < 10) "0$h" else h).toString() + ":" + (if (m < 10) "0$m" else m) + ":" + if (s < 10) "0$s" else s
            mChronometer.text = t
            val minutes = (SystemClock.elapsedRealtime() - mChronometer.base) / 1000 / 60
            val seconds = (SystemClock.elapsedRealtime() - mChronometer.base) / 1000 % 60
            val elapsedMillis = (SystemClock.elapsedRealtime() - mChronometer.base).toInt()
            timerSecond = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis.toLong()).toInt()
            //                    elapsedTime = SystemClock.elapsedRealtime();
            Log.d(TAG, "onChronometerTick: $minutes : $seconds")
        }

        /**
         * save the tag and
         * dismiss the fragment
         */
        tick.setOnClickListener {
            if (snip == null) {
                if (processingDialog == null) {
                    showProgress()
                }
                lastAction = LastAction.SAVE_TAG
            } else {
                saveTag()
            }
        }

        //  plays the video snip
        playBtn.setOnCheckedChangeListener { _, isChecked ->
            if (savedAudioPath.isNotNullOrEmpty()) {
                if (isChecked) {
                    if(audioPlayer == null) {
                        audioPlayer = MediaPlayer.create(context, Uri.parse(savedAudioPath))
                        audioPlayer!!.setOnCompletionListener {
                            playBtn.isChecked = false
                        }
                    }

                    audioPlayer?.start()
                } else {
                    audioPlayer?.stop()
                    audioPlayer?.release()
                    audioPlayer = null
                }
            }
        }

        // record audio
        mic.setOnTouchListener(OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startRecording()
                return@OnTouchListener true
            }
            if (event.action == MotionEvent.ACTION_UP) {
                stopRecording()
                beforeBtn.isChecked = true
                return@OnTouchListener true
            }
            true
        })

        delVoiceTag.setOnClickListener {
            if(savedAudioPath.isBlank()) {
                return@setOnClickListener
            }

            val audioFile = File(savedAudioPath)
            if(audioFile.exists()){
                audioFile.delete()
                savedAudioPath = ""

                audioPlayer?.release()
                audioPlayer = null
                disableAudioControls()
            }
        }

        playVideo.setOnClickListener(View.OnClickListener {
            /*(requireActivity() as AppMainActivity).loadFragment(
                    newInstance(snip, false), true)*/
            if (snip != null) {
                (requireActivity() as AppMainActivity).loadFragment(FragmentPlayVideo2.newInstance(
                    snip), true)
            } else {
                if (processingDialog == null) {
                        showProgress()
                }
                lastAction = LastAction.PLAY_VIDEO
            }
        })

        shareLater.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                shareLater.setBackgroundResource(R.drawable.tag_bg_red)
                shareLater.setTextColor(Color.WHITE)
            } else {
                shareLater.setBackgroundResource(R.drawable.tag_bg_white)
                shareLater.setTextColor(Color.BLACK)

            }
        })

        linkLater.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                linkLater.setBackgroundResource(R.drawable.tag_bg_red)
                linkLater.setTextColor(Color.WHITE)
            } else {
                linkLater.setBackgroundResource(R.drawable.tag_bg_white)
                linkLater.setTextColor(Color.BLACK)

            }
        })

        //  deletes the snip
        delete.setOnClickListener {
            if(snip == null){
                showProgress()
                lastAction = LastAction.DELETE
                return@setOnClickListener
            }

            CoroutineScope(IO).launch {
                appRepository.deleteSnip(snip!!)
                appRepository.getHDSnipsBySnipID(object : AppRepository.HDSnipResult {
                    override suspend fun queryResult(hdSnips: List<Hd_snips>?) {
                        if (!hdSnips.isNullOrEmpty()) {
                            for (items in hdSnips) {
                                appRepository.deleteHDSnip(items)
                            }
                        }

                        withContext(Main) {
                            requireActivity().supportFragmentManager.popBackStack()
                        }
                    }
                }, snip!!.snip_id)
            }
        }

        //  shares the snip
        share.setOnClickListener {
            if(snip == null){
                showProgress()
                lastAction = LastAction.SHARE
                return@setOnClickListener
            }

            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = MimeTypes.VIDEO_MP4
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(snip!!.videoFilePath))
            startActivity(Intent.createChooser(shareIntent, null))
        }

        setupVideoTags()
//        showSelectedColourTags()
        if(savedAudioPath.isBlank())
            disableAudioControls()
    }

    /**
     * lists out existing tags that can be used
     */
    private fun setupVideoTags() {
        CoroutineScope(IO).launch {
            val tagList = mutableSetOf<String>()    //  Set; so that we don't have repetition
            val tagInfoList = appRepository.getAllTags()
            tagInfoList?.forEach {
                if(it.textTag.isNotNullOrEmpty()){
                    tagList.addAll(it.textTag.split(',').filter { item -> item.isNotEmpty() })
                }
            }

            withContext(Main){
                tagsAdapter = TagsRecyclerAdapter(requireContext(), tagList.toMutableList())
                videoTagsList.layoutManager = GridLayoutManager(requireContext(), 3, RecyclerView.VERTICAL, false)
                videoTagsList.adapter = tagsAdapter

                if(tagList.isNullOrEmpty())
                    videoTagsList.visibility = View.GONE
                else
                    videoTagsList.visibility = View.VISIBLE
            }
        }
    }

    /**
     * saves the tag with the current information
     */
    private fun saveTag() {

        val snipId        = snip!!.snip_id
        val audioPath     = savedAudioPath
        val audioPosition = posToChoose
        val colourId      = getSelectedColourTags()
        val shareLaterVal = shareLater.isChecked
        val linkLaterVal  = linkLater.isChecked
        val textTag       = getSelectedTextTags()

        val tag = Tags(
            snipId        = snipId,
            audioPath     = audioPath,
            audioPosition = audioPosition,
            colourId      = colourId,
            shareLater    = shareLaterVal,
            linkLater     = linkLaterVal,
            textTag       = textTag
        )

        CoroutineScope(IO).launch { appRepository.insertTag(tag) }

        requireActivity().supportFragmentManager.popBackStack()
    }

    private fun getSelectedColourTags(): String {
        /*  //  this is for multiple colour tag selection
        val sb = StringBuilder()
        if(colorOne.isChecked) {
            sb.append("${TagColours.BLUE.name},")
        }
        if(colorTwo.isChecked) {
            sb.append("${TagColours.RED.name},")
        }
        if(colorThree.isChecked) {
            sb.append("${TagColours.ORANGE.name},")
        }
        if(colorFour.isChecked) {
            sb.append("${TagColours.PURPLE.name},")
        }
        if(colorFive.isChecked) {
            sb.append("${TagColours.GREEN.name},")
        }
        return sb.toString()*/

        if(colorOne.isChecked) {
            return TagColours.BLUE.name
        }
        if(colorTwo.isChecked) {
            return TagColours.RED.name
        }
        if(colorThree.isChecked) {
            return TagColours.ORANGE.name
        }
        if(colorFour.isChecked) {
            return TagColours.PURPLE.name
        }
        if(colorFive.isChecked) {
            return TagColours.GREEN.name
        }
        return ""
    }

    private fun showSelectedColourTags(){
        CoroutineScope(IO).launch {
            val colourList = mutableSetOf<String>()    //  Set; so that we don't have repetition
            val tagInfo = appRepository.getTagBySnipId(snip!!.snip_id)

            tagInfo?.colourId?.let { colourList.addAll(it.split(',')) }

            withContext(Main){
                colorOne.isChecked   = colourList.contains(TagColours.BLUE.name)
                colorTwo.isChecked   = colourList.contains(TagColours.RED.name)
                colorThree.isChecked = colourList.contains(TagColours.ORANGE.name)
                colorFour.isChecked  = colourList.contains(TagColours.PURPLE.name)
                colorFive.isChecked  = colourList.contains(TagColours.GREEN.name)
            }
        }
    }

    /**
     * gets any selected tags as well
     */
    private fun getSelectedTextTags(): String {
        val sb = StringBuilder()
        val selected = (videoTagsList.adapter as? TagsRecyclerAdapter)?.getSelectedItems()
        selected?.forEach {
            sb.append(it)
            sb.append(",")
        }
        sb.append(tagText.text.toString())

        return sb.toString()
    }

    /**
     * sets up and starts the audio recorder.
     * starts the chronometer
     */
    private fun startRecording() {
        recorder = MediaRecorder()
        with(recorder!!){
            savedAudioPath = filename

            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(output_formats[currentFormat])
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(savedAudioPath)
            setOnErrorListener(errorListener)
            setOnInfoListener(infoListener)

            try {
                prepare()
                start()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        mChronometer.base = SystemClock.elapsedRealtime()
        mChronometer.start()
        mChronometer.visibility = View.VISIBLE
        subContainer.visibility = View.INVISIBLE

    }

    private val errorListener = MediaRecorder.OnErrorListener { mr, what, extra ->
        //            AppLog.logString("Error: " + what + ", " + extra);
        disableAudioControls()
    }
    private val infoListener = MediaRecorder.OnInfoListener { mr, what, extra ->
        //            AppLog.logString("Warning: " + what + ", " + extra);
    }

    /**
     * stops the audio recording and resets the counter
     */
    private fun stopRecording() {
        if (null != recorder) {
            with(recorder!!) {
                stop()
                reset()
                release()
            }
            recorder = null
        }

        enableAudioControls()

        mChronometer.stop()
        mChronometer.visibility = View.INVISIBLE
        subContainer.visibility = View.VISIBLE
        mChronometer.text = ""
    }

    /**
     * to be used to disable audio controls when no recorded audio is available
     */
    private fun disableAudioControls(){

        afterBtn.isChecked = false
        beforeBtn.isChecked = false

        playBtn.disable()
        delVoiceTag.disable()
        beforeBtn.disable()
        afterBtn.disable()

        playBtn.alpha     = 0.5F
        delVoiceTag.alpha = 0.5F
        beforeBtn.alpha   = 0.5F
        afterBtn.alpha    = 0.5F

        beforeBtn.setTextColor(ResourcesCompat.getColor(resources, R.color.colorHint, requireContext().theme))
        afterBtn.setTextColor(ResourcesCompat.getColor(resources, R.color.colorHint, requireContext().theme))
    }

    /**
     * to be used to enable audio controls once the audio is available for playback
     */
    private fun enableAudioControls(){
        playBtn.enable()
        delVoiceTag.enable()
        beforeBtn.enable()
        afterBtn.enable()

        playBtn.alpha     = 1F
        delVoiceTag.alpha = 1F
        beforeBtn.alpha   = 1F
        afterBtn.alpha    = 1F

        beforeBtn.setTextColor(ResourcesCompat.getColor(resources, R.color.colorPrimaryWhite, requireContext().theme))
        afterBtn.setTextColor(ResourcesCompat.getColor(resources, R.color.colorPrimaryWhite, requireContext().theme))

        beforeBtn.isChecked = true
    }

    private fun showProgress(){
        if(processingDialog == null)
            processingDialog = ProcessingDialog()
        processingDialog!!.isCancelable = false
        processingDialog!!.show(requireActivity().supportFragmentManager, PROCESSING_DIALOG)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun dismissProgress(){
        processingDialog?.dismiss()
        processingDialog = null
    }

    companion object {
        private const val TAG = "CreateTag"

        private const val AUDIO_RECORDER_FILE_EXT_MP3 = ".mp3"
        private const val AUDIO_RECORDER_FILE_EXT_MP4 = ".mp4"
        private const val AUDIO_RECORDER_FOLDER       = "SnipRec"
        private const val PROCESSING_DIALOG           = "dialog_processing"

        const val TAG_ACTION   = "com.hipoint.snipback.TAGGING"
        const val NO_AUDIO     = 0
        const val AUDIO_BEFORE = 1
        const val AUDIO_AFTER  = 2

        fun newInstance(snip: Snip?): CreateTag {
            val fragment = CreateTag()
            val bundle = Bundle()
            bundle.putParcelable("snip", snip)
            fragment.arguments = bundle
            return fragment
        }
    }

    enum class LastAction {
        NO_ACTION, PLAY_VIDEO, SAVE_TAG, DELETE, SHARE
    }
}