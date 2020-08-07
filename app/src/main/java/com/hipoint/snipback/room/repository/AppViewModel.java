package com.hipoint.snipback.room.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;

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

    public LiveData<List<Hd_snips>> getHDSnipsLiveData() {
        return mAllHDSnip;
    }

    public LiveData<List<Snip>> getSnipsLiveData() {
        return mAllSnips;
    }

}
