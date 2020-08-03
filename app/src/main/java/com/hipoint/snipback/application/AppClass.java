package com.hipoint.snipback.application;

import android.app.Application;

import com.hipoint.snipback.room.db.RoomDB;
import com.hipoint.snipback.room.entities.Snip;

import java.util.ArrayList;
import java.util.List;

public class AppClass extends Application {

    public RoomDB database;
    private List<Integer> snipDurations = new ArrayList<>();
    private static AppClass appInstance;
    private List<Snip> allSnips = new ArrayList<>();

    public static AppClass getAppInsatnce(){
        if(appInstance == null)
            appInstance = new AppClass();
        return appInstance;
    }

    public AppClass getContext(){
        return this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        database = RoomDB.getDatabase(this);
    }

    public void saveAllSnips(Snip snip){
        int index = allSnips.size() > 0 ? allSnips.indexOf(snip) : 0;
        if(index > 0){
            allSnips.remove(index);
            allSnips.set(index,snip);
        }else {
            allSnips.add(snip);
        }
    }

    public List<Snip> getAllSnip() {
        return allSnips;
    }

    public void setSnipDurations(int duration){
        snipDurations.add(duration);
    }

    public List<Integer> getSnipDurations(){
        return snipDurations;
    }

    public void clearSnipDurations(){
        snipDurations.clear();
    }

}
