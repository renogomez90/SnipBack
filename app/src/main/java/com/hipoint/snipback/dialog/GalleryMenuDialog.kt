package com.hipoint.snipback.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.fragment.FragmentMultiDeletePhoto
import com.hipoint.snipback.listener.IMenuClosedListener

class GalleryMenuDialog(private val closeListener: IMenuClosedListener): DialogFragment() {
    private val TAG = GalleryMenuDialog::class.java.simpleName

    private lateinit var relativeLayout_menu: LinearLayout
    private lateinit var layout_autodelete  : LinearLayout
    private lateinit var layout_filter      : LinearLayout
    private lateinit var layout_multidelete : LinearLayout
    private lateinit var layout_import      : LinearLayout
    private lateinit var autodeleteactions  : RelativeLayout
    private lateinit var autodelete_arrow   : ImageView

    companion object{
        const val GALLERY_DIALOG_TAG = "com.hipoint.snipback.GALLERY_MENU"
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        val rootView = inflater.inflate(R.layout.menu_layout, container, false)
        bindViews(rootView)
        bindListeners()
        return rootView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        val window = dialog.window
        val windowParams = window!!.attributes

        windowParams.gravity = Gravity.BOTTOM
//        windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
        window.attributes = windowParams

        val params = dialog.window!!.attributes // change this to your dialog.
        params.y = 150
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes = params

        return dialog
    }

    private fun bindViews(rootView: View){
        with(rootView){
            layout_autodelete  = findViewById(R.id.auto_delete_holder)
            autodeleteactions  = findViewById(R.id.layout_autodeleteactions)
            autodelete_arrow   = findViewById(R.id.autodelete_arrow)
            layout_multidelete = findViewById(R.id.multiple_del_holder)
            layout_import      = findViewById(R.id.import_holder)
        }
    }

    private fun bindListeners() {
        layout_autodelete.setOnClickListener {
            if(autodeleteactions.visibility == View.GONE) {
                autodeleteactions.visibility = View.VISIBLE
                autodelete_arrow.setImageResource(R.drawable.ic_down_arrow)
                autodelete_arrow.imageTintList = ColorStateList.valueOf(ResourcesCompat.getColor(resources, R.color.colorPrimary, requireContext().theme))
            }else {
                autodeleteactions.visibility = View.GONE
                autodelete_arrow.setImageResource(R.drawable.ic_forward)
            }
        }

        layout_multidelete.setOnClickListener {
            autodeleteactions.visibility = View.GONE
            autodelete_arrow.setImageResource(R.drawable.ic_forward)
            dismiss()
            (requireActivity() as AppMainActivity).loadFragment(FragmentMultiDeletePhoto.newInstance(),
                true)
        }

        layout_import.setOnClickListener {
            val intent = Intent()
            intent.type = "video/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Video"), 1111)
            dialog?.dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        closeListener.settingsSaved()
        super.onDismiss(dialog)
    }
}