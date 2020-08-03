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

    public AppViewModel(@NonNull Application application) {
        super(application);
        appRepository = AppRepository.getInstance();
    }

    public LiveData<List<Event>> getEventLiveData() {
        return appRepository.getEventData();
    }

    public LiveData<List<Hd_snips>> getHDSnipsLiveData() {
        return appRepository.getHDSnipsData();
    }

    public LiveData<List<Snip>> getSnipsLiveData() {
        return appRepository.getSnipsData();
    }
}
