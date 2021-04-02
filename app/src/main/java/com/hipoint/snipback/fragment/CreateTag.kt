package com.hipoint.snipback.fragment

import android.annotation.SuppressLint
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.*
import android.widget.Chronometer.OnChronometerTickListener
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.Utils.SnipPaths
import com.hipoint.snipback.adapter.TagsRecyclerAdapter
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.enums.TagColours
import com.hipoint.snipback.fragment.VideoEditingFragment.Companion.newInstance
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
import java.util.concurrent.TimeUnit

class CreateTag : Fragment() {

    private lateinit var rootView     : View
    private lateinit var edit         : ImageButton
    private lateinit var mic          : ImageButton
    private lateinit var tick         : ImageButton
    private lateinit var delVoiceTag  : ImageButton
    private lateinit var afterBtn     : SwitchCompat
    private lateinit var beforeBtn    : SwitchCompat
    private lateinit var play         : CheckBox
    private lateinit var tagText      : EditText
    private lateinit var afterText    : TextView
    private lateinit var beforeText   : TextView
    private lateinit var mChronometer : Chronometer
    private lateinit var shareLater   : CheckBox
    private lateinit var linkLater    : CheckBox
    private lateinit var videoTagsList: RecyclerView
    private lateinit var colorOne     : CheckBox
    private lateinit var colorTwo     : CheckBox
    private lateinit var colorThree   : CheckBox
    private lateinit var colorFour    : CheckBox
    private lateinit var colorFive    : CheckBox


    private val currentFormat = 0

    private val output_formats = intArrayOf(
        MediaRecorder.OutputFormat.MPEG_4,
        MediaRecorder.OutputFormat.THREE_GPP)

    private val file_exts = arrayOf(
        AUDIO_RECORDER_FILE_EXT_MP4,
        AUDIO_RECORDER_FILE_EXT_3GP)

    private var snip: Snip? = null
    private var recorder: MediaRecorder? = null

    private var isAudioPlaying = false
    private var timerSecond = 0
    private var posToChoose = 0

    private var savedAudioPath: String = ""
    private var tagsAdapter: TagsRecyclerAdapter? = null

    private val paths by lazy { SnipPaths(requireContext()) }
    private val appRepository by lazy { AppRepository(AppClass.getAppInstance()) }

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

    /**
     * binds the views to layout
     */
    private fun bindViews() {
        snip = requireArguments().getParcelable("snip")

        tagText       = rootView.findViewById(R.id.tag_text)
        afterText     = rootView.findViewById(R.id.after_text)
        beforeText    = rootView.findViewById(R.id.before_text)
        afterBtn      = rootView.findViewById(R.id.after_switch)
        beforeBtn     = rootView.findViewById(R.id.before_switch)
        tick          = rootView.findViewById(R.id.tick)
        play          = rootView.findViewById(R.id.play_pause_btn)
        mic           = rootView.findViewById(R.id.mic)
        delVoiceTag   = rootView.findViewById(R.id.del_voice_tag)
        edit          = rootView.findViewById(R.id.edit)
        mChronometer  = rootView.findViewById(R.id.chronometer)
        shareLater    = rootView.findViewById(R.id.share_later)
        linkLater     = rootView.findViewById(R.id.link_later)
        videoTagsList = rootView.findViewById(R.id.videoTagsList)
        colorOne      = rootView.findViewById(R.id.color_one)
        colorTwo      = rootView.findViewById(R.id.color_two)
        colorThree    = rootView.findViewById(R.id.color_three)
        colorFour     = rootView.findViewById(R.id.color_four)
        colorFive     = rootView.findViewById(R.id.color_five)

    }

    /**
     * binds listeners to views
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun bindListeners() {
        afterBtn.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                if (beforeBtn.isChecked) //  this needs to be called before we set the new position
                    beforeBtn.performClick()

                afterText.setTextColor(ResourcesCompat.getColor(resources, R.color.red_tag, requireContext().theme))
                posToChoose = 1
            } else {
                afterBtn.isChecked = false
                afterText.setTextColor(resources.getColor(R.color.colorPrimaryWhite))
                posToChoose = 0
            }
        })

        beforeBtn.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                if (afterBtn.isChecked)  //  this needs to be called before we set the new position
                    afterBtn.performClick()

                beforeText.setTextColor(resources.getColor(R.color.red_tag))
                posToChoose = 2
            } else {
                beforeBtn.isChecked = false
                beforeText.setTextColor(ResourcesCompat.getColor(resources, R.color.colorPrimaryWhite, requireContext().theme))
                posToChoose = 0
            }
        })

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
        tick.setOnClickListener(View.OnClickListener {
            /*val intent = Intent(requireActivity(), ActivityPlayVideo::class.java)
            intent.putExtra("snip", snip)
            startActivity(intent)
            requireActivity().finish()*/
            saveTag()
            requireActivity().supportFragmentManager.popBackStack()
        })

        play.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
            }
        })

        // record audio
        mic.setOnTouchListener(OnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startRecording()
                return@OnTouchListener true
            }
            if (event.action == MotionEvent.ACTION_UP) {
                stopRecording()
                afterBtn.isChecked = true
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
            }
        }

        //  todo: what is this for?
        edit.setOnClickListener(View.OnClickListener {
            (requireActivity() as AppMainActivity).loadFragment(
                    newInstance(snip, false), true)
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

        colorTwo.setOnClickListener{

        }

        setupVideoTags()
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
                    tagList.addAll(it.textTag.split(','))
                }
            }

            withContext(Main){
                tagsAdapter = TagsRecyclerAdapter(requireContext(), tagList.toList())
                videoTagsList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
                videoTagsList.adapter = tagsAdapter
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
        val colourId      = TagColours.NO_COLOR.ordinal
        val shareLater    = false
        val linkLater     = false
        val textTag       = getSelectedTextTags()

        val tag = Tags(
            snipId        = snipId,
            audioPath     = audioPath,
            audioPosition = audioPosition,
            colourId      = colourId,
            shareLater    = shareLater,
            linkLater     = linkLater,
            textTag       = textTag
        )

        CoroutineScope(IO).launch { appRepository.insertTag(tag) }
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
    }

    private val filename: String
        private get() {
            val filepath = paths.INTERNAL_VIDEO_DIR
            val file = File(filepath, AUDIO_RECORDER_FOLDER)
            if (!file.exists()) {
                file.mkdirs()
            }

//        return (file.getAbsolutePath() + "/" +snip.getSnip_id()+ file_exts[currentFormat]);\
            return file.absolutePath + "/" + snip!!.snip_id + ".mp3"
        }
    private val errorListener = MediaRecorder.OnErrorListener { mr, what, extra ->
        //            AppLog.logString("Error: " + what + ", " + extra);
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

        mChronometer.stop()
        mChronometer.visibility = View.INVISIBLE
        mChronometer.text = ""
    }

    companion object {
        private const val TAG = "CreateTag"
        private const val AUDIO_RECORDER_FILE_EXT_3GP = ".3gp"
        private const val AUDIO_RECORDER_FILE_EXT_MP4 = ".mp4"
        private const val AUDIO_RECORDER_FOLDER = "SnipRec"

        fun newInstance(snip: Snip?): CreateTag {
            val fragment = CreateTag()
            val bundle = Bundle()
            bundle.putParcelable("snip", snip)
            fragment.arguments = bundle
            return fragment
        }
    }
}