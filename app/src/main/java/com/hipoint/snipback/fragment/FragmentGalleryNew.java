package com.hipoint.snipback.fragment;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.hipoint.snipback.ActivityPlayVideo;
import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;

import com.hipoint.snipback.adapter.MainRecyclerAdapter;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.EventData;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;
import com.hipoint.snipback.room.repository.AppViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FragmentGalleryNew extends Fragment {
    private static FragmentGalleryNew mMyFragment;

    private View rootView;
    RecyclerView mainCategoryRecycler;
    MainRecyclerAdapter mainRecyclerAdapter;
    SwipeRefreshLayout pullToRefresh;
    ImageButton filter_button, view_button, menu_button, camera_button;
    TextView filter_label, view_label, menu_label, photolabel;
    ImageView autodelete_arrow, player_view_image;
    RecyclerView recycler_view;

    private BandwidthMeter bandwidthMeter;
    private TrackSelector trackSelector;
    private TrackSelection.Factory trackSelectionFactory;
    private SimpleExoPlayer player;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;
    private DefaultBandwidthMeter defaultBandwidthMeter;
    private MediaSource mediaSource;
    private Uri uri;
    private PlayerView simpleExoPlayerView;
    private RelativeLayout rlLoader;


    RelativeLayout relativeLayout_menu, relativeLayout_autodeleteactions, layout_autodelete, layout_filter, layout_multidelete, click, import_con, viewButtonLayout;

    List<Snip> snipArrayList = new ArrayList<>();

    public static FragmentGalleryNew newInstance() {
        if (mMyFragment == null)
            mMyFragment = new FragmentGalleryNew();
        return mMyFragment;
    }

    boolean viewButtonClicked = false;
    public String viewChange;
    public Integer orientation;

    public enum ViewType {
        NORMAL, ENLARGED;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("viewChangeValue", viewChange);
        outState.putBoolean("buttonClickedValue",viewButtonClicked);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if(savedInstanceState!=null){
            viewChange = savedInstanceState.getString("viewChangeValue");
            viewButtonClicked = savedInstanceState.getBoolean("buttonClickedValue");
            updateViewButtonUI(viewButtonClicked); // update viewButton on orientation change
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_gallery_new, container, false);

        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        setRetainInstance(true);
        import_con = rootView.findViewById(R.id.import_con);
        player_view_image = rootView.findViewById(R.id.player_view_image);
        photolabel = rootView.findViewById(R.id.photolabel);
        recycler_view = rootView.findViewById(R.id.recycler_view);
        menu_button = rootView.findViewById(R.id.dropdown_menu);
        view_button = rootView.findViewById(R.id._button_view);
        camera_button = rootView.findViewById(R.id.camera);
        filter_button = rootView.findViewById(R.id.filter);
        filter_label = rootView.findViewById(R.id.highlight_text_btn);
        view_label = rootView.findViewById(R.id.slow_text_btn);
        click = rootView.findViewById(R.id.click);
        rlLoader = rootView.findViewById(R.id.showLoader);
        pullToRefresh = rootView.findViewById(R.id.pullToRefresh);
        viewButtonLayout = rootView.findViewById(R.id.layout_view);
        click.setVisibility(View.GONE);

        if (AppClass.getAppInstance().isInsertionInProgress()) {
            rlLoader.setVisibility(View.VISIBLE);
        } else {
            rlLoader.setVisibility(View.INVISIBLE);
        }


        // direct to gallery to view

        photolabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setType("image/*");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        camera_button.setOnClickListener(v -> {
//            ((AppMainActivity) requireActivity()).loadFragment(VideoMode.newInstance(), false);
            requireActivity().getSupportFragmentManager().popBackStack();   //  assuming that FragmentGalleryNew is loaded only from VideoMode
        });
        menu_button.setOnClickListener(v -> {
            final Dialog dialog = new Dialog(requireActivity());
            view_button.setImageResource(R.drawable.ic_view_unselected);
            view_label.setTextColor(getResources().getColor(R.color.colorDarkGreyDim));
            Window window = dialog.getWindow();
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.gravity = Gravity.BOTTOM;
            wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            window.setAttributes(wlp);
            dialog.setContentView(R.layout.menu_layout);
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes(); // change this to your dialog.
            params.y = 150;
            dialog.getWindow().setAttributes(params);
            layout_autodelete = dialog.findViewById(R.id.layout_autodelete);
            relativeLayout_autodeleteactions = dialog.findViewById(R.id.layout_autodeleteactions);
            autodelete_arrow = dialog.findViewById(R.id.autodelete_arrow);
            layout_multidelete = dialog.findViewById(R.id.layout_multipledelete);
            layout_autodelete.setOnClickListener(v1 -> {
                relativeLayout_autodeleteactions.setVisibility(View.VISIBLE);
                autodelete_arrow.setImageResource(R.drawable.ic_keyboard_arrow_down_black_24dp);
            });
            layout_multidelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    relativeLayout_autodeleteactions.setVisibility(View.GONE);
                    autodelete_arrow.setImageResource(R.drawable.ic_forward);
                    dialog.cancel();
                    ((AppMainActivity) requireActivity()).loadFragment(FragmentMultiDeletePhoto.newInstance(), true);


                }
            });

            RelativeLayout layout_import = dialog.findViewById(R.id.layout_import);
            layout_import.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Intent intent = new Intent();
                    intent.setType("video/*");
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(Intent.createChooser(intent, "Select Video"), 1111);

                    dialog.dismiss();
                }
            });

            dialog.show();
        });
        filter_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Dialog dialogFilter = new Dialog(requireActivity());
                Window window = dialogFilter.getWindow();
                filter_button.setImageResource(R.drawable.ic_filter_selected);
                filter_label.setTextColor(getResources().getColor(R.color.colorPrimaryDimRed));
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dialogFilter.setContentView(R.layout.filter_layout);
                dialogFilter.show();
            }
        });
        click.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                ((AppMainActivity) requireActivity()).loadFragment(FragmentPlayVideo.newInstance(uri.toString()));
//                Intent intent = new Intent(requireActivity(), ActivityPlayVideo.class);
                Intent intent = new Intent(requireActivity(), ActivityPlayVideo.class);
                intent.putExtra("uri", uri.toString());
                startActivity(intent);

            }
        });

        view_button.setOnClickListener(new View.OnClickListener() { //enlarge Views on click
            @Override
            public void onClick(View v) {
                if (!viewButtonClicked) {
                    viewButtonClicked = true;
                    viewChange = String.valueOf(ViewType.ENLARGED);
                } else {
                    viewButtonClicked = false;
                    viewChange = String.valueOf(ViewType.NORMAL);
                }
                galleryEnlargedView(viewChange,viewButtonClicked);
            }
        });

        mainCategoryRecycler = rootView.findViewById(R.id.main_recycler);

//        AppViewModel appViewModel = ViewModelProviders.ofrequireActivity().get(AppViewModel.class);
        pulltoRefresh();
        pullToRefresh.setRefreshing(false);
        return rootView;
    }

    private void updateViewButtonUI(Boolean viewButtonClicked){ // view button wasn't changing on rotation
        if (viewButtonClicked){
            view_button.setImageResource(R.drawable.ic_view);
            view_label.setTextColor(getResources().getColor(R.color.colorPrimaryDimRed));
        } else {
            view_button.setImageResource(R.drawable.ic_view_unselected);
            view_label.setTextColor(getResources().getColor(R.color.colorDarkGreyDim));
        }
    }

    private void galleryEnlargedView(String viewChange,Boolean viewButtonClicked) {
        updateViewButtonUI(viewButtonClicked);//update view when button clicked
        List<EventData> allSnips = AppClass.getAppInstance().getAllSnip();
        List<EventData> allParentSnip = AppClass.getAppInstance().getAllParentSnip();
        mainRecyclerAdapter = new MainRecyclerAdapter(requireActivity(), allParentSnip, allSnips, viewChange);
        mainCategoryRecycler.setAdapter(mainRecyclerAdapter);
        mainRecyclerAdapter.notifyDataSetChanged();

    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

    }

    private void pulltoRefresh() {
        pullToRefresh.setOnRefreshListener(() -> {
            pullToRefresh.setRefreshing(false);
            loadGalleryDataFromDB();
        });
    }


    @Override
    public void onResume() {
        super.onResume();
        loadGalleryDataFromDB();
//        if(AppClass.getAppInsatnce().getAllParentSnip().size() == 0) {
//            loadGalleryDataFromDB();
//            AppClass.getAppInsatnce().setInsertionInProgress(false);
//        }else{
//            List<EventData> allSnipEvent = AppClass.getAppInsatnce().getAllSnip();
//            List<EventData> allParentSnipEvent = AppClass.getAppInsatnce().getAllParentSnip();
//            RecyclerView.LayoutManager layoutManager=new LinearLayoutManager(requireActivity());
//            mainCategoryRecycler.setLayoutManager(layoutManager);
//            mainRecyclerAdapter=new MainRecyclerAdapter(requireActivity(),allParentSnipEvent,allSnipEvent);
//            mainCategoryRecycler.setAdapter(mainRecyclerAdapter);
//        }
    }

    private void loadGalleryDataFromDB() {
        AppClass.getAppInstance().clearAllParentSnips();
        AppClass.getAppInstance().clearAllSnips();
        AppViewModel appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);
//        getFilePathFromInternalStorage();
        List<Event> allEvents = new ArrayList<>();
        appViewModel.getEventLiveData().observe(getViewLifecycleOwner(), events -> {
            if (events != null && events.size() > 0) {  //  get available events
                allEvents.addAll(events);
                List<Hd_snips> hdSnips = new ArrayList<>();
                appViewModel.getHDSnipsLiveData().observe(getViewLifecycleOwner(), hd_snips -> {
                    if (hd_snips != null && hd_snips.size() > 0) {  //  get available HD Snips
                        hdSnips.addAll(hd_snips);

                        // sort by Snip ID then by name, to weed out the buffer videos
                        removeBufferContent(hdSnips);

                        appViewModel.getSnipsLiveData().observe(getViewLifecycleOwner(), snips -> { //get snips
                            if (snips != null && snips.size() > 0) {
                                for (Snip snip : snips) {
                                    for (Hd_snips hdSnip : hdSnips) {
                                        if (hdSnip.getSnip_id() == snip.getParent_snip_id() || hdSnip.getSnip_id() == snip.getSnip_id()) {  //  if HD snip is a parent of a snip or HD snip is the current snip
                                            if(snip.getVideoFilePath() == null && hdSnip.getSnip_id() == snip.getParent_snip_id()){
                                                snip.setVideoFilePath(hdSnip.getVideo_path_processed());
                                            }
                                            //                                            snip.setVideoFilePath(hdSnip.getVideo_path_processed());    //  sets the video path for the snip
                                            for (Event event : allEvents) {
                                                if (event.getEvent_id() == snip.getEvent_id()) {
                                                    AppClass.getAppInstance().setEventSnipsFromDb(event, snip);
                                                    if (snip.getParent_snip_id() == 0) {
                                                        AppClass.getAppInstance().setEventParentSnipsFromDb(event, snip);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                pullToRefresh.setRefreshing(false);
                                List<EventData> allSnips = AppClass.getAppInstance().getAllSnip();
                                List<EventData> allParentSnip = AppClass.getAppInstance().getAllParentSnip();
//                                if (mainRecyclerAdapter == null) {
                                RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(requireActivity());
                                mainCategoryRecycler.setLayoutManager(layoutManager);
                                mainRecyclerAdapter = new MainRecyclerAdapter(requireActivity(), allParentSnip, allSnips, viewChange);
                                mainCategoryRecycler.setAdapter(mainRecyclerAdapter);
//                                }
                                mainRecyclerAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                });
            }
        });
    }

    /**
     * filters out the buffered content from the DB list by sorting and removing from list
     *
     * @param hdSnips
     */
    private void removeBufferContent(List<Hd_snips> hdSnips) {
        hdSnips.sort((o1, o2) -> {
            Integer id1 = o1.getSnip_id();
            Integer id2 = o2.getSnip_id();
            int comp = id1.compareTo(id2);

            if (comp != 0) {
                return comp;
            }

            String n1 = o1.getVideo_path_processed().toLowerCase();
            String n2 = o2.getVideo_path_processed().toLowerCase();

            return n1.compareTo(n2);
        });

        for (int i = 1; i < hdSnips.size(); i++) {
            if(hdSnips.get(i - 1).getSnip_id() == hdSnips.get(i).getSnip_id()){
                hdSnips.remove(i -1);
            }
        }
    }

    private static String VIDEO_DIRECTORY_NAME = "SnipBackVirtual";
    private static String THUMBS_DIRECTORY_NAME = "Thumbs";
    private ArrayList<String> thumbs = new ArrayList<>();

    private void getFilePathFromInternalStorage() {
        File directory;
        File photoDirectory;
//        if (Environment.getExternalStorageState() == null) {
        //create new file directory object
        directory = new File(requireActivity().getDataDir()
                + "/" + VIDEO_DIRECTORY_NAME + "/");
        photoDirectory = new File(requireActivity().getDataDir()
                + "/" + VIDEO_DIRECTORY_NAME + "/" + THUMBS_DIRECTORY_NAME + "/");
        if (photoDirectory.exists()) {
            File[] dirFiles = photoDirectory.listFiles();
            if (dirFiles != null && dirFiles.length != 0) {
                for (int ii = 0; ii < dirFiles.length; ii++) {
                    thumbs.add(dirFiles[ii].getAbsolutePath());
                }
            }
        }
        // if no directory exists, create new directory
        if (!directory.exists()) {
            directory.mkdir();
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

    public void onLoadingCompleted(boolean success) {
        if (success) {
            rlLoader.setVisibility(View.INVISIBLE);
            List<EventData> allSnips = AppClass.getAppInstance().getAllSnip();
            List<EventData> allParentSnip = AppClass.getAppInstance().getAllParentSnip();
            if (mainRecyclerAdapter == null) {
                RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(requireActivity());
                mainCategoryRecycler.setLayoutManager(layoutManager);
                mainRecyclerAdapter = new MainRecyclerAdapter(requireActivity(), allParentSnip, allSnips, null);
                mainCategoryRecycler.setAdapter(mainRecyclerAdapter);
            } else {
                mainRecyclerAdapter.notifyDataSetChanged();
            }
        }
    }


}







