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


class ExitEditConfirmationDialog(private val saveListener: ISaveListener) : DialogFragment() {

    private lateinit var exitWithoutSave: TextView
    private lateinit var cancel         : TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.warningdialog_savevideodiscardchanges, container, false)
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
            exitWithoutSave = findViewById(R.id.dialog_ok)
            cancel          = findViewById(R.id.dialog_cancel)
        }
    }

    private fun bindListener() {
        exitWithoutSave.setOnClickListener {
            saveListener.exit()
        }

        cancel.setOnClickListener {
            dismiss()
        }
    }
}