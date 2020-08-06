package com.hipoint.snipback.application;

import android.app.Application;

import com.hipoint.snipback.room.db.RoomDB;
import com.hipoint.snipback.room.entities.Snip;

import java.util.ArrayList;
import java.util.List;

public class  AppClass extends Application {

    public RoomDB database;
    private List<Integer> snipDurations = new ArrayList<>();
    private static AppClass appInstance;
    private List<Snip> allSnips = new ArrayList<>();
    private int lastEventId;
    private int lastSnipId;
    private long lastHDSnipId;

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
        int index = allSnips.size() > 0 ? allSnips.indexOf(snip) : -1;
        if(index >= 0){
            allSnips.remove(index);
            allSnips.add(index,snip);
        }else {
            allSnips.add(snip);
        }
    }

    public void clearAllSnips(){
        allSnips.clear();
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

    public int getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(int lastEventId) {
        this.lastEventId = lastEventId;
    }

    public int getLastSnipId() {
        return lastSnipId;
    }

    public void setLastSnipId(int lastSnipId) {
        this.lastSnipId = lastSnipId;
    }

    public long getLastHDSnipId() {
        return lastHDSnipId;
    }

    public void setLastHDSnipId(long lastHDSnipId) {
        this.lastHDSnipId = lastHDSnipId;
    }
}
