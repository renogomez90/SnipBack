package com.hipoint.snipback.application;

import android.app.Application;

import com.hipoint.snipback.room.db.RoomDB;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.EventData;
import com.hipoint.snipback.room.entities.Snip;

import java.util.ArrayList;
import java.util.List;

public class  AppClass extends Application {

    public RoomDB database;
    private List<Integer> snipDurations = new ArrayList<>();
    private static AppClass appInstance;
    private List<EventData> allEventSnips = new ArrayList<>();
    private List<EventData> eventParentSnips = new ArrayList<>();
    private int lastEventId;
    private int lastSnipId;
    private long lastHDSnipId;

    public Event getLastCreatedEvent() {
        return lastCreatedEvent;
    }

    public void setLastCreatedEvent(Event lastCreatedEvent) {
        lastCreatedEvent.setEvent_id(getLastEventId());
        this.lastCreatedEvent = lastCreatedEvent;
    }

    public Event lastCreatedEvent;

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

    public void saveAllEventSnips(EventData snip){
        int index = allEventSnips.size() > 0 ? allEventSnips.indexOf(snip) : -1;
        if(index >= 0){
            allEventSnips.remove(index);
            allEventSnips.add(index,snip);
        }else {
            allEventSnips.add(snip);
        }
    }

    public void updateVirtualToRealInAllSnipEvent(Snip snip){
        for(EventData eventData : allEventSnips){
            int eventSnipIndex = eventData.getSnips().size() > 0 ? eventData.getSnips().indexOf(snip) : -1;
            if(eventSnipIndex >= 0){
                allEventSnips.remove(eventData);
                eventData.getSnips().remove(eventSnipIndex);
                snip.setIs_virtual_version(0);
                eventData.addEventSnip(snip);
                allEventSnips.add(eventData);
            }
        }
    }

    public void clearAllSnips(){
        allEventSnips.clear();
    }

    public List<EventData> getAllSnip() {
        return allEventSnips;
    }

    public void clearAllParentSnips(){
        eventParentSnips.clear();
    }

    public List<EventData> getAllParentSnip() {
        return eventParentSnips;
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

    public List<EventData> getEventParentSnips() {
        return eventParentSnips;
    }

    public void setEventParentSnips(EventData eventParentSnip) {
        int index = eventParentSnips.size() > 0 ? eventParentSnips.indexOf(eventParentSnips) : -1;
        if(index >= 0){
            eventParentSnips.remove(index);
            eventParentSnips.add(index,eventParentSnip);
        }else {
            eventParentSnips.add(eventParentSnip);
        }
    }

    public List<Snip> getChildSnipsByParentSnipId(List<Integer> parentId){
        List<Snip> childSnips = new ArrayList<>();
        for(EventData eventData : getAllSnip()){
            for(Snip snip : eventData.getSnips()){
                for(int parentSnipId : parentId) {
                    if (snip.getParent_snip_id() == parentSnipId || snip.getSnip_id() == parentSnipId) {
                        childSnips.add(snip);
                    }
                }
            }
        }
        return childSnips;
    }
}
