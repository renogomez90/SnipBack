package com.hipoint.snipback;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.hipoint.snipback.R;
import com.hipoint.snipback.Utils.CommonUtils;
import com.hipoint.snipback.Utils.gesture.GestureFilter;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.fragment.FragmentGalleryNew;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.EventData;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;
import com.hipoint.snipback.room.repository.AppRepository;
import com.hipoint.snipback.room.repository.AppViewModel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class AppMainActivity extends AppCompatActivity {
    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.INTERNET};

//    private static String VIDEO_DIRECTORY_NAME = "SnipBackVirtual";
//    private static String THUMBS_DIRECTORY_NAME = "Thumbs";
    private List<MyOnTouchListener> onTouchListeners;

    private AppViewModel appViewModel;
//    private ArrayList<String> thumbs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.appmain_activity);

        if (onTouchListeners == null) {
            onTouchListeners = new ArrayList<>();
        }

        appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);

//        RegisterFragment videoMode = new RegisterFragment();
//        loadFragment(videoMode);
        if24HoursCompleted();

//        appViewModel.loadGalleryDataFromDB(this);

        loadFragment(VideoMode.newInstance(), false);

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

    }

    public void registerMyOnTouchListener(MyOnTouchListener listener) {
        onTouchListeners.add(listener);
    }

    private void addDailyEvent() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
        String currentDateandTime = sdf.format(new Date());
        Event event = new Event();
        event.setEvent_title(CommonUtils.today() + ", " + currentDateandTime);
        event.setEvent_created(System.currentTimeMillis());
        AppRepository appRepository = new AppRepository(AppClass.getAppInsatnce());
        appRepository.insertEvent(event);
    }

    private void if24HoursCompleted() {
        appViewModel.getEventLiveData().observe(this, events -> {
            if (events != null && events.size() > 0) {
                Event lastEvent = events.get(events.size() - 1);
                long diff = System.currentTimeMillis() - lastEvent.getEvent_created();
//                long seconds = diff / 1000;
//                long minutes = diff / 1000 / 60;
                long hours = diff / 1000 / 60 / 60;
                if (hours >= 24) {
                    addDailyEvent();
                }
            } else {
                addDailyEvent();
            }
        });
    }

//    private void loadGalleryDataFromDB() {
//        getFilePathFromInternalStorage();
//        List<Event> allEvents = new ArrayList<>();
//        appViewModel.getEventLiveData().observe(this, events -> {
//            if (events != null && events.size() > 0) {
//                allEvents.addAll(events);
//            }
//        });
//        List<Hd_snips> hdSnips = new ArrayList<>();
//        appViewModel.getHDSnipsLiveData().observe(this, hd_snips -> {
//            if (hd_snips != null && hd_snips.size() > 0) {
//                hdSnips.addAll(hd_snips);
//            }
//        });
//        appViewModel.getSnipsLiveData().observe(this, snips -> {
//            if (snips != null && snips.size() > 0) {
//                AppClass.getAppInsatnce().clearAllSnips();
//                AppClass.getAppInsatnce().clearAllParentSnips();
//                for (Snip snip : snips) {
//                    for (Hd_snips hdSnip : hdSnips) {
//                        if (hdSnip.getSnip_id() == snip.getParent_snip_id() || hdSnip.getSnip_id() == snip.getSnip_id()) {
//                            snip.setVideoFilePath(hdSnip.getVideo_path_processed());
//                            if (thumbs.size() > 0) {
//                                for (String filePath : thumbs) {
//                                    File file = new File(filePath);
//                                    String[] snipNameWithExtension = file.getName().split("_");
//                                    if (snipNameWithExtension.length > 0) {
//                                        String[] snipName = snipNameWithExtension[1].split("\\.");
//                                        if (snipName.length > 0) {
//                                            int snipId = Integer.parseInt(snipName[0]);
//                                            if (snipId == snip.getSnip_id()) {
//                                                snip.setThumbnailPath(filePath);
//                                                for (Event event : allEvents) {
//                                                    if (event.getEvent_id() == snip.getEvent_id()) {
//                                                        EventData eventData = new EventData();
//                                                        eventData.setEvent_id(event.getEvent_id());
//                                                        eventData.setEvent_created(event.getEvent_created());
//                                                        eventData.setEvent_title(event.getEvent_title());
//                                                        eventData.addEventSnip(snip);
//                                                        AppClass.getAppInsatnce().saveAllEventSnips(eventData);
//                                                    }
//                                                    if (event.getEvent_id() == snip.getEvent_id() && snip.getParent_snip_id() == 0) {
//                                                        EventData eventData = new EventData();
//                                                        eventData.setEvent_id(event.getEvent_id());
//                                                        eventData.setEvent_created(event.getEvent_created());
//                                                        eventData.setEvent_title(event.getEvent_title());
//                                                        eventData.addEventParentSnip(snip);
//                                                        AppClass.getAppInsatnce().setEventParentSnips(eventData);
//                                                    }
//                                                }
//                                            }
//                                        }
//
//                                    }
//                                }
//                            }
//                            Log.i("HOME", "LOOPING COMPLETED");
//                        }
//                    }
//
//                }
//
//            }
//        });
//    }

//    private void getFilePathFromInternalStorage() {
//        File directory;
//        File photoDirectory;
////        if (Environment.getExternalStorageState() == null) {
//        //create new file directory object
//        directory = new File(getDataDir()
//                + "/" + VIDEO_DIRECTORY_NAME + "/");
//        photoDirectory = new File(getDataDir()
//                + "/" + VIDEO_DIRECTORY_NAME + "/" + THUMBS_DIRECTORY_NAME + "/");
//        if (photoDirectory.exists()) {
//            File[] dirFiles = photoDirectory.listFiles();
//            if (dirFiles != null && dirFiles.length != 0) {
//                for (int ii = 0; ii < dirFiles.length; ii++) {
//                    thumbs.add(dirFiles[ii].getAbsolutePath());
//                }
//            }
//        }
//        // if no directory exists, create new directory
//        if (!directory.exists()) {
//            directory.mkdir();
//        }
////        }
////        else if (Environment.getExternalStorageState() != null) {
////            // search for directory on SD card
////            directory = new File(Environment.getExternalStorageDirectory()
////                    + "/" + VIDEO_DIRECTORY_NAME + "/");
////            photoDirectory = new File(
////                    Environment.getExternalStorageDirectory()
////                            + "/" + VIDEO_DIRECTORY_NAME + "/" + THUMBS_DIRECTORY_NAME + "/");
////            if (photoDirectory.exists()) {
////                File[] dirFiles = photoDirectory.listFiles();
////                if (dirFiles != null && dirFiles.length > 0) {
////                    for (File dirFile : dirFiles) {
////                        thumbs.add(dirFile.getAbsolutePath());
////                    }
////                    dirFiles = null;
////                }
////            }
////            // if no directory exists, create new directory to store test
////            // results
////            if (!directory.exists()) {
////                directory.mkdir();
////            }
////        }
//    }

    //    public void loadFragment(Fragment fragment,boolean addtoBackStack) {
    public void loadFragment(Fragment fragment, boolean addtoBackStack) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.mainFragment, fragment);
        if (addtoBackStack || fragment instanceof FragmentGalleryNew) {
            ft.addToBackStack(null);
        }
        ft.commitAllowingStateLoss();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        for (MyOnTouchListener listener : onTouchListeners)
            listener.onTouch(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
//        Fragment myFragment = getSupportFragmentManager().findFragmentById(R.id.mainFragment);
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (count == 0) {
            super.onBackPressed();
        } else {
            getSupportFragmentManager().popBackStack();
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public interface MyOnTouchListener {
        public void onTouch(MotionEvent ev);
    }


}
