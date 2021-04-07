package com.hipoint.snipback.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.hipoint.snipback.R
import com.hipoint.snipback.Utils.TagFilter
import com.hipoint.snipback.adapter.TagsRecyclerAdapter
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.enums.TagColours
import com.hipoint.snipback.listener.IFilterListener
import com.hipoint.snipback.room.entities.Tags
import com.hipoint.snipback.room.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FilterDialog(private val filterListener: IFilterListener) : DialogFragment() {
    private val TAG = FilterDialog::class.java.simpleName

    private lateinit var audioTag           : CheckBox
    private lateinit var shareLater         : CheckBox
    private lateinit var linkLater          : CheckBox
    private lateinit var colorOne           : CheckBox
    private lateinit var colorTwo           : CheckBox
    private lateinit var colorThree         : CheckBox
    private lateinit var colorFour          : CheckBox
    private lateinit var colorFive          : CheckBox
    private lateinit var filterVideoTagsList: RecyclerView

    private var tagsAdapter: TagsRecyclerAdapter? = null

    private val appRepository by lazy { AppRepository(AppClass.getAppInstance()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.filter_layout, container, false)
        bindViews(view)
        bindListener()
        setupVideoTags()
        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    private fun bindViews(rootView: View) {
        with(rootView) {
            audioTag            = findViewById(R.id.audio_tag)
            shareLater          = findViewById(R.id.share_later)
            linkLater           = findViewById(R.id.link_later)
            colorOne            = findViewById(R.id.color_one)
            colorTwo            = findViewById(R.id.color_two)
            colorThree          = findViewById(R.id.color_three)
            colorFour           = findViewById(R.id.color_four)
            colorFive           = findViewById(R.id.color_five)
            filterVideoTagsList = findViewById(R.id.filterVideoTagsList)
        }
    }

    private fun bindListener() {
        audioTag.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                audioTag.setBackgroundResource(R.drawable.red_outline_background)
                audioTag.setTextColor(Color.WHITE)
            } else {
                audioTag.setBackgroundResource(R.drawable.grey_outine_background)
                audioTag.setTextColor(Color.GRAY)

            }
        }

        shareLater.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                shareLater.setBackgroundResource(R.drawable.red_outline_background)
                shareLater.setTextColor(Color.WHITE)
            } else {
                shareLater.setBackgroundResource(R.drawable.grey_outine_background)
                shareLater.setTextColor(Color.GRAY)

            }
        }

        linkLater.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                linkLater.setBackgroundResource(R.drawable.red_outline_background)
                linkLater.setTextColor(Color.WHITE)
            } else {
                linkLater.setBackgroundResource(R.drawable.grey_outine_background)
                linkLater.setTextColor(Color.GRAY)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        filterListener.filterSet(TagFilter(audioTag.isChecked,
            shareLater.isChecked,
            linkLater.isChecked,
            getSelectedColours(),
            (filterVideoTagsList.adapter as TagsRecyclerAdapter).getSelectedItems()))
    }

    private fun getSelectedColours(): ArrayList<String> {
        val colourList = arrayListOf<String>()
        if (colorOne.isChecked) colourList.add(TagColours.BLUE.name)
        if (colorTwo.isChecked) colourList.add(TagColours.RED.name)
        if (colorThree.isChecked) colourList.add(TagColours.ORANGE.name)
        if (colorFour.isChecked) colourList.add(TagColours.PURPLE.name)
        if (colorFive.isChecked) colourList.add(TagColours.GREEN.name)
        return colourList
    }

    /**
     * lists out existing tags that can be used
     */
    private fun setupVideoTags() {
        CoroutineScope(Dispatchers.IO).launch {
            val tagList = mutableSetOf<String>()    //  Set; so that we don't have repetition
            val tagInfoList = appRepository.getAllTags()
            tagInfoList?.forEach {
                if(it.textTag.isNotNullOrEmpty()){
                    tagList.addAll(it.textTag.split(',').filter { item -> item.isNotEmpty() })
                }
            }

            withContext(Dispatchers.Main){
                tagsAdapter = TagsRecyclerAdapter(requireContext(), tagList.toMutableList())
                filterVideoTagsList.layoutManager = GridLayoutManager(requireContext(), 3, RecyclerView.VERTICAL, false)
                filterVideoTagsList.adapter = tagsAdapter
            }
        }
    }
}