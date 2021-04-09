package com.hipoint.snipback.dialog

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.fragment.app.DialogFragment
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.fragment.Feedback_fragment
import com.hipoint.snipback.listener.ISettingsClosedListener


class SettingsDialog(context: Context,val closeListener: ISettingsClosedListener) : DialogFragment() {
    private val TAG = SettingsDialog::class.java.simpleName
    private val pref: SharedPreferences by lazy { context.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE) }

    private lateinit var quality              : RelativeLayout
    private lateinit var feedback             : RelativeLayout
    private lateinit var bufferDurationSeekbar: AppCompatSeekBar
    private lateinit var quickbackSeekbar     : AppCompatSeekBar
    private lateinit var bufferValue          : TextView
    private lateinit var qbValue              : TextView

    companion object {
        const val BUFFER_DURATION      = "BUFFER_DURATION"
        const val QB_DURATION          = "QUICKBACK_DURATION"
        const val SLOW_MO_QB_DURATION  = "SLOW_MO_QUICKBACK_DURATION"
        const val SLOW_MO_CLICKED      = "SLOW_MO_CLICKED"
        const val SETTINGS_PREFERENCES = "PREFERENCES"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        bindViews(view)
        bindListener()
        initialize()
        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    private fun bindViews(rootView: View) {
        with(rootView) {
            feedback              = findViewById(R.id.feedback_holder)
            quality               = findViewById(R.id.quality_holder)
            bufferDurationSeekbar = findViewById(R.id.buffer_progress)
            quickbackSeekbar      = findViewById(R.id.quickback_progress)
            bufferValue           = findViewById(R.id.buffer_value_txt)
            qbValue               = findViewById(R.id.qb_value_txt)
        }
    }

    private fun bindListener() {
        feedback.setOnClickListener { showFeedbackFragment() }
        quality.setOnClickListener { showDialogSettingsResolution() }

        bufferDurationSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                bufferValue.text = "$progress mins"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        quickbackSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                qbValue.text = "$progress secs"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun initialize() {
        val currentBuffer = pref.getInt(BUFFER_DURATION, 1)
        val currentQb = pref.getInt(QB_DURATION, 5)

        bufferDurationSeekbar.min = 1
        bufferDurationSeekbar.max = 60

        quickbackSeekbar.min = 5
        quickbackSeekbar.max = 60

        bufferDurationSeekbar.progress = currentBuffer
        quickbackSeekbar.progress = currentQb
    }

    private fun showDialogSettingsResolution() {
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.fragment_resolution)
        dialog.show()
    }

    private fun showFeedbackFragment(){
        (activity as AppMainActivity?)!!.loadFragment(Feedback_fragment.newInstance(), true)
        dialog?.dismiss()
    }

    private fun updateSettings() {
        val editor = pref.edit()
        with(editor){
            putInt(BUFFER_DURATION, bufferDurationSeekbar.progress)
            putInt(QB_DURATION, quickbackSeekbar.progress)
            apply()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        updateSettings()
        closeListener.settingsSaved()
        super.onCancel(dialog)
    }
}