package com.hipoint.snipback.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.hipoint.snipback.R
import com.hipoint.snipback.listener.ISaveListener


class KeepVideoDialog(private val saveListener: ISaveListener) : DialogFragment() {

    private lateinit var saveAs: TextView
    private lateinit var exit: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.warningdialog_save_snapback_video, container, false)
        bindViews(view)
        bindListener()
        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    private fun bindViews(rootView: View) {
        with(rootView) {
            saveAs = findViewById(R.id.dialog_savebutton)
            exit = findViewById(R.id.dialog_cancel)
        }
    }

    private fun bindListener() {
        saveAs.setOnClickListener {
            saveListener.saveAs()
            dismiss()
        }

        exit.setOnClickListener {
            saveListener.exit()
            dismiss()
        }
    }
}