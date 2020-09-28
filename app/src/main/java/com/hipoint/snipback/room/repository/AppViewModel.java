package com.hipoint.snipback.room.repository;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AppViewModel extends AndroidViewModel {
    AppRepository appRepository;
    private LiveData<List<Event>> mAllEvents;
    private LiveData<List<Hd_snips>> mAllHDSnip;
    private LiveData<List<Snip>> mAllSnips;
    private LiveData<Event> mEventById;

    public AppViewModel(@NonNull Application application) {
        super(application);
        appRepository = new AppRepository(application);
        mAllEvents = appRepository.getEventData();
        mAllHDSnip = appRepository.getHDSnipsData();
        mAllSnips = appRepository.getSnipsData();
    }

    public LiveData<List<Event>> getEventLiveData() {
        return mAllEvents;
    }

    public LiveData<Event> getEventByIdLiveData(int eventId) {
        mEventById = appRepository.getEventById(eventId);
        return mEventById;
    }

//    public LiveData<List<Snip>> getSnipByEventIdLiveData(int eventId) {
//        mEventById = appRepository.getEventById(eventId);
//        return mEventById;
//    }

    public LiveData<List<Hd_snips>> getHDSnipsLiveData() {
        return mAllHDSnip;
    }

    public LiveData<List<Snip>> getSnipsLiveData() {
        return mAllSnips;
    }

    public void loadLastInsertedEvent(AppCompatActivity activity) {
        getEventLiveData().observe(activity, events -> {
            if (events.size() > 0) {
                Event lastEvent = events.get(events.size() - 1);
                int eventId = lastEvent.getEvent_id();
                AppClass.getAppInstance().setLastEventId(eventId);
                AppClass.getAppInstance().setLastCreatedEvent(lastEvent);
            }
        });
    }

    public void loadGalleryDataFromDB(AppCompatActivity context) {
        AppClass.getAppInstance().clearAllSnips();
        AppClass.getAppInstance().clearAllParentSnips();
//        getFilePathFromInternalStorage(context);
        List<Event> allEvents = new ArrayList<>();
        getEventLiveData().observe(context, events -> {
            if (events != null && events.size() > 0) {
                allEvents.addAll(events);
                List<Hd_snips> hdSnips = new ArrayList<>();
                getHDSnipsLiveData().observe(context, hd_snips -> {
                    if (hd_snips != null && hd_snips.size() > 0) {
                        hdSnips.addAll(hd_snips);
                        getSnipsLiveData().observe(context, snips -> {
                            if (snips != null && snips.size() > 0) {
                                for (Snip snip : snips) {
                                    for (Hd_snips hdSnip : hdSnips) {
                                        if (hdSnip.getSnip_id() == snip.getParent_snip_id() || hdSnip.getSnip_id() == snip.getSnip_id()) {
                                            snip.setVideoFilePath(hdSnip.getVideo_path_processed());
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
                            }
                        });
                    }
                });
            }
        });

    }

    private static String VIDEO_DIRECTORY_NAME_VIRTUAL = "SnipBackVirtual";
    private static String THUMBS_DIRECTORY_NAME = "Thumbs";
    private ArrayList<String> thumbs = new ArrayList<>();

    private void getFilePathFromInternalStorage(Context context) {
        File directory;
        File photoDirectory;
//        if (Environment.getExternalStorageState() == null) {
        //create new file directory object
        directory = new File(context.getDataDir()
                + "/" + VIDEO_DIRECTORY_NAME_VIRTUAL + "/");
        photoDirectory = new File(context.getDataDir()
                + "/" + VIDEO_DIRECTORY_NAME_VIRTUAL + "/" + THUMBS_DIRECTORY_NAME + "/");
        if (photoDirectory.exists()) {
            File[] dirFiles = photoDirectory.listFiles();
            if (dirFiles != null && dirFiles.length != 0) {
                for (File dirFile : dirFiles) {
                    thumbs.add(dirFile.getAbsolutePath());
                }
            }
        }
        // if no directory exists, create new directory
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

}
