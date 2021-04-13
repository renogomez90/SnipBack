package com.hipoint.snipback.fragment

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.hipoint.snipback.ActivityPlayVideo
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.Utils.TagFilter
import com.hipoint.snipback.adapter.MainRecyclerAdapter
import com.hipoint.snipback.application.AppClass
import com.hipoint.snipback.dialog.FilterDialog
import com.hipoint.snipback.dialog.GalleryMenuDialog
import com.hipoint.snipback.listener.IFilterListener
import com.hipoint.snipback.listener.IMenuClosedListener
import com.hipoint.snipback.room.entities.Event
import com.hipoint.snipback.room.entities.Hd_snips
import com.hipoint.snipback.room.entities.Snip
import com.hipoint.snipback.room.repository.AppRepository
import com.hipoint.snipback.room.repository.AppViewModel
import com.hipoint.snipback.service.VideoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import kotlin.collections.ArrayList


class FragmentGalleryNew : Fragment(), IFilterListener, IMenuClosedListener {
    private val TAG = FragmentPlayVideo2::class.java.simpleName

    private lateinit var rootView                        : View
    private lateinit var mainCategoryRecycler            : RecyclerView
    private lateinit var pullToRefresh                   : SwipeRefreshLayout
    private lateinit var filter_button                   : TextView
    private lateinit var view_button                     : TextView
    private lateinit var menu_button                     : TextView
    private lateinit var camera_button                   : TextView
    private lateinit var menu_label                      : TextView
    private lateinit var photolabel                      : TextView
    private lateinit var player_view_image               : ImageView
    private lateinit var rlLoader                        : RelativeLayout
    private lateinit var click                           : RelativeLayout
    private lateinit var import_con                      : RelativeLayout

    private var mainRecyclerAdapter: MainRecyclerAdapter? = null

    var snipArrayList: List<Snip> = ArrayList()
    var viewChange   : String?    = null
    var orientation  : Int?       = null

    private var viewButtonClicked = false
    private val uri: Uri? = null
    private var currentTagFilter: TagFilter? = null

    private val allEvents: MutableList<Event> by lazy { ArrayList() }
    private val hdSnips: MutableList<Hd_snips> by lazy { ArrayList() }
    private val snip: MutableList<Snip> by lazy { ArrayList() }
    private val appRepository by lazy { AppRepository(AppClass.getAppInstance()) }

    private val videoProcessingReceiver: BroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let{
                    if(mainCategoryRecycler.adapter != null) {
                        (mainCategoryRecycler.adapter as MainRecyclerAdapter)
                            .showLoading(it.getIntExtra("progress", VideoService.STATUS_NO_VALUE) == VideoService.STATUS_SHOW_PROGRESS)
                    }
                }
            }
        }
    }

    enum class ViewType {
        NORMAL, ENLARGED
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("viewChangeValue", viewChange)
        outState.putBoolean("buttonClickedValue", viewButtonClicked)
        outState.putParcelable("currentTag", currentTagFilter)
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            viewChange = savedInstanceState.getString("viewChangeValue")
            viewButtonClicked = savedInstanceState.getBoolean("buttonClickedValue")
            currentTagFilter = savedInstanceState.getParcelable("currentTag")
            updateViewButtonUI(viewButtonClicked) // update viewButton on orientation change
        }
    }

    /**
     * Called when the fragment is no longer in use.  This is called
     * after [.onStop] and before [.onDetach].
     */
    override fun onDestroy() {
        super.onDestroy()
        (requireActivity() as AppMainActivity).showSystemUI1()

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity() as AppMainActivity).hideSystemUI1()
    }
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
    ): View? {
        postponeEnterTransition()
        (requireActivity() as AppMainActivity).hideSystemUI1()
                requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        postponeEnterTransition()
        rootView       = inflater.inflate(R.layout.fragment_gallery_new, container, false)
        retainInstance = true

        import_con        = rootView.findViewById(R.id.import_con)
        player_view_image = rootView.findViewById(R.id.player_view_image)
        photolabel        = rootView.findViewById(R.id.photolabel)
        menu_button       = rootView.findViewById(R.id.dropdown_menu)
        view_button       = rootView.findViewById(R.id._button_view)
        camera_button     = rootView.findViewById(R.id.camera)
        filter_button     = rootView.findViewById(R.id.filter)
        click             = rootView.findViewById(R.id.click)
        rlLoader          = rootView.findViewById(R.id.showLoader)
        pullToRefresh     = rootView.findViewById(R.id.pullToRefresh)

        click.visibility = View.GONE

        if (AppClass.getAppInstance().isInsertionInProgress) {
            rlLoader.visibility = View.VISIBLE
        } else {
            rlLoader.visibility = View.INVISIBLE
        }

        // direct to gallery to view
        photolabel.setOnClickListener {
            /*Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setType("image/ *");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);*/

            val photoLaunchIntent = Intent(Intent.ACTION_VIEW)
            val mediaDirPath = requireContext().externalMediaDirs[0].absolutePath + "/Snipback/"
            //                Uri fileUri = Uri.fromFile(new File(mediaDirPath));
            val fileUri = FileProvider.getUriForFile(requireContext().applicationContext,
                    requireContext().packageName + ".fileprovider",
                    File(mediaDirPath))
            photoLaunchIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            photoLaunchIntent.setDataAndType(fileUri,
                    DocumentsContract.Document.MIME_TYPE_DIR) //   this is correct way to do this BUT Samsung and Huawei doesn't support it
            if (photoLaunchIntent.resolveActivityInfo(requireContext().packageManager, 0) == null) {
                photoLaunchIntent.setDataAndType(fileUri,
                        "resource/folder") //  this will work with some file managers
                if (photoLaunchIntent.resolveActivityInfo(requireContext().packageManager,
                                0) == null
                ) {
                    photoLaunchIntent.setDataAndType(fileUri, "*/*") //  just open with anything
                }
            }
            startActivity(Intent.createChooser(photoLaunchIntent, "Choose"))
        }

        camera_button.setOnClickListener { v: View? ->
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            requireActivity().supportFragmentManager.popBackStack() //  assuming that FragmentGalleryNew is loaded only from VideoMode
        }

        menu_button.setOnClickListener { v: View? ->
            val dialog = GalleryMenuDialog(this@FragmentGalleryNew)

            menu_button.setCompoundDrawablesWithIntrinsicBounds(0,
                    R.drawable.ic_menu_selected,
                    0,
                    0)
            menu_button.setTextColor(resources.getColor(R.color.colorPrimaryDimRed))

            dialog.show(requireActivity().supportFragmentManager, GalleryMenuDialog.GALLERY_DIALOG_TAG)

        }

        filter_button.setOnClickListener {
            val dialogFilter = FilterDialog(this@FragmentGalleryNew)
            dialogFilter.show(requireActivity().supportFragmentManager, FILTER_DIALOG)

            filter_button.setCompoundDrawablesWithIntrinsicBounds(0,
                R.drawable.ic_filter_selected,
                0,
                0)
            filter_button.setTextColor(resources.getColor(R.color.colorPrimaryDimRed))

        }

        click.setOnClickListener {
//                ((AppMainActivity) requireActivity()).loadFragment(FragmentPlayVideo.newInstance(uri.toString()));
//                Intent intent = new Intent(requireActivity(), ActivityPlayVideo.class);
//            val intent = Intent(requireActivity(), ActivityPlayVideo::class.java)
//            intent.putExtra("uri", uri.toString())
//            startActivity(intent)
        }

        view_button.setOnClickListener {
            if (!viewButtonClicked) {
                viewButtonClicked = true
                viewChange = ViewType.ENLARGED.toString()
            } else {
                viewButtonClicked = false
                viewChange = ViewType.NORMAL.toString()
            }
            galleryEnlargedView(viewChange!!, viewButtonClicked)
        }

        mainCategoryRecycler = rootView.findViewById(R.id.main_recycler)

//        AppViewModel appViewModel = ViewModelProviders.ofrequireActivity().get(AppViewModel.class);
        pullToRefresh()
        pullToRefresh.isRefreshing = false
        return rootView
    }

    private fun updateViewButtonUI(viewButtonClicked: Boolean) { // view button wasn't changing on rotation
        if (viewButtonClicked) {
            view_button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_view, 0, 0)
            view_button.setTextColor(resources.getColor(R.color.colorPrimaryDimRed))
        } else {
            view_button.setCompoundDrawablesWithIntrinsicBounds(0,
                    R.drawable.ic_view_unselected,
                    0,
                    0)
            view_button.setTextColor(resources.getColor(R.color.colorDarkGreyDim))
        }
    }

    private fun galleryEnlargedView(viewChange: String, viewButtonClicked: Boolean) = CoroutineScope(Main).launch {
        updateViewButtonUI(viewButtonClicked) //update view when button clicked
        val allSnips = AppClass.getAppInstance().allSnip
        val allParentSnip = AppClass.getAppInstance().allParentSnip

        val tagsList = appRepository.getAllTags()

        mainRecyclerAdapter =
            MainRecyclerAdapter(requireActivity(), allParentSnip, allSnips, viewChange, tagsList)
        mainRecyclerAdapter!!.setHasStableIds(true)
        mainCategoryRecycler.adapter = mainRecyclerAdapter
        mainRecyclerAdapter?.notifyDataSetChanged()
    }

    private fun pullToRefresh() {
        pullToRefresh.setOnRefreshListener {
            pullToRefresh.isRefreshing = false
            /*loadGalleryDataFromDB()*/

            prepareGalleryItems(allEvents, hdSnips, snip)
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as AppMainActivity).hideOrShowProgress(visible = false)
        requireActivity().registerReceiver(videoProcessingReceiver, IntentFilter(VideoMode.UI_UPDATE_ACTION))
        /*loadGalleryDataFromDB()*/
        loadData()
        startPostponedEnterTransition()
        updateViewButtonUI(viewButtonClicked)
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(videoProcessingReceiver)
        super.onPause()
    }

    private fun loadData(){
        AppClass.getAppInstance().clearAllParentSnips()
        AppClass.getAppInstance().clearAllSnips()
        val appViewModel = ViewModelProvider(this@FragmentGalleryNew).get(AppViewModel::class.java)

        appViewModel.eventLiveData.observe(viewLifecycleOwner, { events: List<Event>? ->
            events?.let {
                allEvents.clear()
                allEvents.addAll(it)
            }
        })

        appViewModel.hdSnipsLiveData.observe(viewLifecycleOwner, { hd_snips: List<Hd_snips>? ->
            hd_snips?.let {
                hdSnips.clear()
                hdSnips.addAll(it)
                removeBufferContent(hdSnips as ArrayList<Hd_snips>)
                if (snip.isNotEmpty()) {
                    prepareGalleryItems(allEvents, hdSnips, snip)
                }
            }
        })

        appViewModel.snipsLiveData.observe(viewLifecycleOwner, { snips: List<Snip>? ->
            snips?.let {
                snip.clear()
                snip.addAll(it)
                prepareGalleryItems(allEvents, hdSnips, snips)
            }
        })
    }

    private fun prepareGalleryItems(allEvents: MutableList<Event>, hdSnips: MutableList<Hd_snips>, snips: List<Snip>) {
        if (snips.isNotEmpty()) {
            for (snip in snips) {
                for (hdSnip in hdSnips) {
                    if (hdSnip.snip_id == snip.parent_snip_id || hdSnip.snip_id == snip.snip_id) {  //  if HD snip is a parent of a snip or HD snip is the current snip
                        if (snip.videoFilePath == null && hdSnip.snip_id == snip.parent_snip_id) {
                            snip.videoFilePath =
                                hdSnip.video_path_processed
                        }
                        //  snip.setVideoFilePath(hdSnip.getVideo_path_processed());    //  sets the video path for the snip
                        for (event in allEvents) {
                            if (event.event_id == snip.event_id) {
                                AppClass.getAppInstance().setEventSnipsFromDb(event, snip)

                                if (snip.parent_snip_id == 0) {
                                    AppClass.getAppInstance()
                                        .setEventParentSnipsFromDb(event, snip)
                                }
                            }
                        }
                    }
                }
            }

            pullToRefresh.isRefreshing = false
            val allSnips = AppClass.getAppInstance().allSnip
            val allParentSnip = AppClass.getAppInstance().allParentSnip
            if(mainCategoryRecycler.layoutManager == null)
                mainCategoryRecycler.layoutManager = LinearLayoutManager(requireActivity())

            CoroutineScope(Main).launch {
                val tagsList = appRepository.getAllTags()

                if(mainCategoryRecycler.adapter == null) {
                    mainRecyclerAdapter = MainRecyclerAdapter(requireActivity(), allParentSnip, allSnips, viewChange, tagsList)
                    mainCategoryRecycler.adapter = mainRecyclerAdapter
                } else {
                    (mainCategoryRecycler.adapter as MainRecyclerAdapter).updateData(allParentSnip, allSnips, viewChange, tagsList)
                }
                if(VideoService.isProcessing)
                    (mainCategoryRecycler.adapter as MainRecyclerAdapter).showLoading(true)

                filterSet(currentTagFilter)
            }
//            mainCategoryRecycler.adapter!!.notifyDataSetChanged()
        }
    }

    private fun loadGalleryDataFromDB() {
        /*AppClass.getAppInstance().clearAllParentSnips()
        AppClass.getAppInstance().clearAllSnips()
        val appViewModel = ViewModelProvider(this@FragmentGalleryNew).get(AppViewModel::class.java)
        //        getFilePathFromInternalStorage();
        val allEvents: MutableList<Event> = ArrayList()
        appViewModel.eventLiveData.observe(viewLifecycleOwner, { events: List<Event>? ->
            if (events != null && events.isNotEmpty()) {  //  get available events
                allEvents.addAll(events)
                val hdSnips: MutableList<Hd_snips> = ArrayList()
                appViewModel.hdSnipsLiveData.observe(viewLifecycleOwner,
                        { hd_snips: List<Hd_snips>? ->
                            if (hd_snips != null && hd_snips.isNotEmpty()) {  //  get available HD Snips
                                hdSnips.addAll(hd_snips)

                                // sort by Snip ID then by name, to weed out the buffer videos
                                removeBufferContent(hdSnips as ArrayList<Hd_snips>)
                                appViewModel.snipsLiveData.observe(viewLifecycleOwner
                                ) { snips: List<Snip>? ->  //get snips

                                    if (snips != null && snips.isNotEmpty()) {
                                        for (snip in snips) {
                                            for (hdSnip in hdSnips) {
                                                if (hdSnip.snip_id == snip.parent_snip_id || hdSnip.snip_id == snip.snip_id) {  //  if HD snip is a parent of a snip or HD snip is the current snip
                                                    if (snip.videoFilePath == null && hdSnip.snip_id == snip.parent_snip_id) {
                                                        snip.videoFilePath =
                                                                hdSnip.video_path_processed
                                                    }
                                                    //  snip.setVideoFilePath(hdSnip.getVideo_path_processed());    //  sets the video path for the snip
                                                    for (event in allEvents) {
                                                        if (event.event_id == snip.event_id) {
                                                            AppClass.getAppInstance()
                                                                    .setEventSnipsFromDb(event,
                                                                            snip)
                                                            if (snip.parent_snip_id == 0) {
                                                                AppClass.getAppInstance()
                                                                        .setEventParentSnipsFromDb(
                                                                                event,
                                                                                snip)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        pullToRefresh.isRefreshing = false
                                        val allSnips = AppClass.getAppInstance().allSnip
                                        val allParentSnip =
                                                AppClass.getAppInstance().allParentSnip
                                        //  if (mainRecyclerAdapter == null) {
                                        val layoutManager: RecyclerView.LayoutManager =
                                                LinearLayoutManager(requireActivity())
                                        mainCategoryRecycler.layoutManager =
                                                layoutManager
                                        mainRecyclerAdapter =
                                                MainRecyclerAdapter(requireActivity(),
                                                        allParentSnip,
                                                        allSnips,
                                                        viewChange)
                                        mainCategoryRecycler.adapter =
                                                mainRecyclerAdapter
                                        //                                }
                                        mainRecyclerAdapter?.notifyDataSetChanged()
                                    }
                                }
                            }
                        }
                )
            }
        })*/
    }

    /**
     * filters out the buffered content from the DB list by sorting and removing from list
     *
     * @param hdSnips
     */
    private fun removeBufferContent(hdSnips: ArrayList<Hd_snips>) {
        hdSnips.sortedWith { o1: Hd_snips, o2: Hd_snips ->
            val id1 = o1.snip_id
            val id2 = o2.snip_id
            val comp = id1.compareTo(id2)
            if (comp != 0) {
                return@sortedWith comp
            }
            val n1 = o1.video_path_processed.toLowerCase(Locale.getDefault())
            val n2 = o2.video_path_processed.toLowerCase(Locale.getDefault())
            n1.compareTo(n2)
        }

        val removableElement = ArrayList<Hd_snips>()
        for (i in 1 until hdSnips.size) {
            if (hdSnips[i - 1].snip_id == hdSnips[i].snip_id) {
//                hdSnips.removeAt(i - 1)
                removableElement.add(hdSnips[i - 1])
            }
        }
        removableElement.forEach{
            hdSnips.remove(it)
        }
    }

    private val thumbs = ArrayList<String>()

    //        if (Environment.getExternalStorageState() == null) {
    //create new file directory object
    // if no directory exists, create new directory
    //        } else if (Environment.getExternalStorageState() != null) {
//            // search for directory on SD card
//            directory = new File(Environment.getExternalStorageDirectory()
//                    + "/" + VIDEO_DIRECTORY_NAME + "/");
//            photoDirectory = new File(
//                    Environment.getExternalStorageDirectory()
//                            + "/" + VIDEO_DIRECTORY_NAME + "/" + THUMBS_DIRECTORY_NAME + "/");
//            if (photoDirectory.exists()) {
//                File[] dirFiles = photoDirectory.listFiles();
//                if (dirFiles != null && dirFiles.length > 0) {
//                    for (File dirFile : dirFiles) {
//                        thumbs.add(dirFile.getAbsolutePath());
//                    }
//                    dirFiles = null;
//                }
//            }
//            // if no directory exists, create new directory to store test
//            // results
//            if (!directory.exists()) {
//                directory.mkdir();
//            }
//        }
    val filePathFromInternalStorage: Unit
        get() {
            val directory: File
            val photoDirectory: File
            //        if (Environment.getExternalStorageState() == null) {
            //create new file directory object
            directory = File(requireActivity().dataDir
                    .toString() + "/" + VIDEO_DIRECTORY_NAME + "/")
            photoDirectory = File(requireActivity().dataDir
                    .toString() + "/" + VIDEO_DIRECTORY_NAME + "/" + THUMBS_DIRECTORY_NAME + "/")
            if (photoDirectory.exists()) {
                val dirFiles = photoDirectory.listFiles()
                if (dirFiles != null && dirFiles.size != 0) {
                    for (ii in dirFiles.indices) {
                        thumbs.add(dirFiles[ii].absolutePath)
                    }
                }
            }
            // if no directory exists, create new directory
            if (!directory.exists()) {
                directory.mkdir()
            }
            //        } else if (Environment.getExternalStorageState() != null) {
//            // search for directory on SD card
//            directory = new File(Environment.getExternalStorageDirectory()
//                    + "/" + VIDEO_DIRECTORY_NAME + "/");
//            photoDirectory = new File(
//                    Environment.getExternalStorageDirectory()
//                            + "/" + VIDEO_DIRECTORY_NAME + "/" + THUMBS_DIRECTORY_NAME + "/");
//            if (photoDirectory.exists()) {
//                File[] dirFiles = photoDirectory.listFiles();
//                if (dirFiles != null && dirFiles.length > 0) {
//                    for (File dirFile : dirFiles) {
//                        thumbs.add(dirFile.getAbsolutePath());
//                    }
//                    dirFiles = null;
//                }
//            }
//            // if no directory exists, create new directory to store test
//            // results
//            if (!directory.exists()) {
//                directory.mkdir();
//            }
//        }
        }

    fun onLoadingCompleted(success: Boolean) = CoroutineScope(Main).launch{
        if (success) {
            rlLoader.visibility = View.INVISIBLE
            val allSnips = AppClass.getAppInstance().allSnip
            val allParentSnip = AppClass.getAppInstance().allParentSnip
            val tagsList = appRepository.getAllTags()

            if (mainRecyclerAdapter == null) {
                val layoutManager: RecyclerView.LayoutManager =
                    LinearLayoutManager(requireActivity())
                mainCategoryRecycler.layoutManager = layoutManager
                mainRecyclerAdapter =
                    MainRecyclerAdapter(requireActivity(), allParentSnip, allSnips, null, tagsList)
                mainCategoryRecycler.adapter = mainRecyclerAdapter
            } else {
                mainRecyclerAdapter?.notifyDataSetChanged()
            }
        }
    }

    companion object {
        private var mMyFragment: FragmentGalleryNew? = null
        fun newInstance(): FragmentGalleryNew? {
            if (mMyFragment == null) mMyFragment = FragmentGalleryNew()
            return mMyFragment
        }

        private const val VIDEO_DIRECTORY_NAME = "SnipBackVirtual"
        private const val THUMBS_DIRECTORY_NAME = "Thumbs"
        private const val FILTER_DIALOG = "com.hipoint.snipback.FILTER_DIALOG"
    }

    override fun filterSet(tag: TagFilter?) {
        currentTagFilter = tag
        if(mainCategoryRecycler.adapter == null)
            return

        if(isNoFilterSelected(tag)){
            CoroutineScope(IO).launch {
                val allSnips = AppClass.getAppInstance().allSnip
                val allParentSnip = AppClass.getAppInstance().allParentSnip
                val tagsList = appRepository.getAllTags()

                withContext(Main) {
                    val adapter = (mainCategoryRecycler.adapter as MainRecyclerAdapter)

                    adapter.setFilterIds(null)
                    adapter.updateData(allParentSnip,
                        allSnips,
                        viewChange,
                        tagsList)

                    //  since no filter is applied we can reset the button colour
                    filter_button.setCompoundDrawablesWithIntrinsicBounds(0,
                        R.drawable.ic_filter_results_button,
                        0,
                        0)
                    filter_button.setTextColor(resources.getColor(R.color.colorDarkGreyDim))
                }
            }

            return
        }

        val filteredSnipSet = mutableSetOf<Int>()

        tag?.let{
            CoroutineScope(IO).launch {
                if(it.hasAudio){
                    appRepository.getSnipIdsByAudioTag()?.let { idList -> filteredSnipSet.addAll(idList) }
                }
                if(it.hasLinkLater){
                    appRepository.getSnipIdsByLinkLater()?.let { idList -> filteredSnipSet.addAll(idList) }
                }
                if(it.hasShareLater){
                    appRepository.getSnipIdsByShareLater()?.let { idList -> filteredSnipSet.addAll(idList) }
                }
                if(it.hasColour.isNotEmpty()){
                    it.hasColour.forEach{
                        appRepository.getSnipIdsByColour(it)?.let { idList -> filteredSnipSet.addAll(idList) }
                    }
                }
                if(it.hasText.isNotEmpty()){
                    it.hasText.forEach{
                        appRepository.getSnipIdsByTextTag(it)?.let { idList -> filteredSnipSet.addAll(idList) }
                    }
                }

                withContext(Main) {
                    (mainCategoryRecycler.adapter as? MainRecyclerAdapter)?.setFilterIds(
                        filteredSnipSet.toList())
                }
            }
        }
    }

    private fun isNoFilterSelected(tag: TagFilter?): Boolean {
        return if(tag == null)
            true
        else {
            !tag.hasShareLater &&
            !tag.hasLinkLater &&
            !tag.hasAudio &&
            tag.hasText.isEmpty() &&
            tag.hasColour.isEmpty()
        }
    }

    /**
     * when the menu is closed
     */
    override fun settingsSaved() {
        //  resetting menu UI
        menu_button.setCompoundDrawablesWithIntrinsicBounds(0,
            R.drawable.ic_menu,
            0,
            0)
        menu_button.setTextColor(resources.getColor(R.color.colorDarkGreyDim))
    }
}