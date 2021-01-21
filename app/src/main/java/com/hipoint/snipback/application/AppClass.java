package com.hipoint.snipback.application;

import android.app.Application;
import android.util.Log;

import com.hipoint.snipback.receiver.VideoOperationReceiver;
import com.hipoint.snipback.room.db.RoomDB;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.EventData;
import com.hipoint.snipback.room.entities.Snip;

import java.util.ArrayList;
import java.util.List;

public class AppClass extends Application {

    public RoomDB database;
   private List<Integer> snipDurations = new ArrayList<>();
    private static AppClass appInstance;
    private List<EventData> allEventSnips = new ArrayList<>();
    private List<EventData> eventParentSnips = new ArrayList<>();
    private int lastEventId;
    private int lastSnipId;
    private long lastHDSnipId;
    private boolean isInsertionInProgress = false;
    private String thumbFilePathRoot;
    private VideoOperationReceiver videoCompletion;

    public static boolean swipeProcessed = false;
    public static ArrayList<String> showInGallery = new ArrayList<>();    //  names of files that need to be displayed in the gallery

    public Event getLastCreatedEvent() {
        return lastCreatedEvent;
    }

    public void setLastCreatedEvent(Event lastCreatedEvent) {
        lastCreatedEvent.setEvent_id(getLastEventId());
        this.lastCreatedEvent = lastCreatedEvent;
        saveLastEvent(lastCreatedEvent);
    }

    public Event lastCreatedEvent;

    public static AppClass getAppInstance() {
        if (appInstance == null)
            appInstance = new AppClass();
        return appInstance;
    }

    public AppClass getContext() {
        return this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        database = RoomDB.getDatabase(this);
    }

    List<Snip> snipList = new ArrayList<>();

    public void saveAllSnips(Snip snip) {
        snipList.add(snip);
    }

    public List<Snip> getSnips() {
        return snipList;
    }

    public void saveLastEvent(Event event) {
        EventData eventData = new EventData();
        eventData.setEvent(event);
        allEventSnips.add(eventData);
    }

    public void saveAllEventSnips() {
        int eventId = getLastEventId();
        List<EventData> tempAllSnips = allEventSnips;
        for (EventData eventData : allEventSnips) {
            if (eventData.getEvent().getEvent_id() == eventId) {
//                eventData.setEvent(eventData.getEvent());
                eventData.addEventAllSnip(getSnips());
                tempAllSnips.set(tempAllSnips.indexOf(eventData), eventData);
            }
        }
        allEventSnips = tempAllSnips;


//        if (allEventSnips.size() > 0) {
//            for (EventData eventData : allEventSnips) {
//                Log.i("eventId",eventData.getEvent().getEvent_id()+"");
//                Log.i("New Snip Event Id",snip.getEvent_id()+"");
//                if (eventData.getEvent().getEvent_id() == snip.getEvent_id()) {
//                    eventData.addEventSnip(snip);
////                    allEventSnips.set(allEventSnips.indexOf(eventData), eventData);
//                } else {
//                    EventData newEvent = new EventData();
//                    newEvent.setEvent(getLastCreatedEvent());
//                    newEvent.addEventSnip(snip);
//                    allEventSnips.add(newEvent);
//                }
//            }
//        } else {
//            EventData newEvent = new EventData();
//            newEvent.setEvent(getLastCreatedEvent());
//            newEvent.addEventSnip(snip);
//            allEventSnips.add(newEvent);
//        }
    }

    List<Snip> parentsnipList = new ArrayList<>();

    public void saveAllParentSnips(Snip snip) {
        parentsnipList.add(snip);
    }

    public List<Snip> getParentSnips() {
        return parentsnipList;
    }

    public void setEventParentSnips() {
        int eventId = getLastEventId();
        List<EventData> tempAllParents = eventParentSnips;
        for (EventData eventData : allEventSnips) {
            if (eventData.getEvent().getEvent_id() == eventId) {
//                eventData.setEvent(eventData.getEvent());
                eventData.addEventAllParentSnip(getParentSnips());
                tempAllParents.set(tempAllParents.indexOf(eventData), eventData);
            }
        }
        eventParentSnips = tempAllParents;
//
//        if (eventParentSnips.size() > 0) {
//            for (EventData eventData : eventParentSnips) {
//                if (eventData.getEvent().getEvent_id() == parentSnip.getEvent_id()) {
//                    eventData.addEventParentSnip(parentSnip);
////                    eventParentSnips.set(eventParentSnips.indexOf(eventData), eventData);
//                } else {
//                    EventData newEvent = new EventData();
//                    newEvent.setEvent(getLastCreatedEvent());
//                    newEvent.addEventParentSnip(parentSnip);
//                    eventParentSnips.add(newEvent);
//                }
//            }
//        } else {
//            EventData newEvent = new EventData();
//            newEvent.setEvent(getLastCreatedEvent());
//            newEvent.addEventParentSnip(parentSnip);
//            eventParentSnips.add(newEvent);
//        }
    }

    public void setEventSnipsFromDb(Event event, Snip snip) {
        boolean eventStatus = false;

        for (EventData eventData : allEventSnips) {
            if (eventData.getEvent().getEvent_id() == snip.getEvent_id()) {
                eventStatus = true;
                break;
            }
        }
        if (!eventStatus) {
            EventData newEvent = new EventData();
            newEvent.setEvent(event);
            newEvent.addEventSnip(snip);
            allEventSnips.add(newEvent);
        }

        for (EventData eventData : allEventSnips) {
            if (eventData.getEvent().getEvent_id() == snip.getEvent_id()) {
                eventData.addEventSnip(snip);
                int index = allEventSnips.indexOf(eventData);
                allEventSnips.get(index).addEventSnip(snip);
            }
        }

    }

    public void setEventParentSnipsFromDb(Event event, Snip parentSnip) {
        boolean eventStatus = false;

        for (EventData eventData : eventParentSnips) {
            if (eventData.getEvent().getEvent_id() == parentSnip.getEvent_id()) {
                eventStatus = true;
                break;
            }
        }
        if (!eventStatus) {
            EventData newEvent = new EventData();
            newEvent.setEvent(event);
            newEvent.addEventParentSnip(parentSnip);
            eventParentSnips.add(newEvent);
        }

        for (EventData eventData : eventParentSnips) {
            if (eventData.getEvent().getEvent_id() == parentSnip.getEvent_id()) {
                eventData.addEventParentSnip(parentSnip);
                int index = eventParentSnips.indexOf(eventData);
                eventParentSnips.get(index).addEventParentSnip(parentSnip);
            }
        }
    }

    public void updateVirtualToRealInAllSnipEvent(Snip snip) {
        snip.setIs_virtual_version(0);
        if (allEventSnips.size() > 0) {
            for (EventData eventData : allEventSnips) {
                if (eventData.getEvent().getEvent_id() == snip.getEvent_id()) {
                    eventData.addEventSnip(snip);
                    allEventSnips.set(allEventSnips.indexOf(eventData), eventData);
                } else {
                    EventData newEvent = new EventData();
                    newEvent.setEvent(getLastCreatedEvent());
                    newEvent.addEventSnip(snip);
                    allEventSnips.add(newEvent);
                }
            }
        } else {
            EventData newEvent = new EventData();
            newEvent.setEvent(getLastCreatedEvent());
            newEvent.addEventSnip(snip);
            allEventSnips.add(newEvent);
        }

//        for (EventData eventData : allEventSnips) {
//            int eventSnipIndex = eventData.getSnips().size() > 0 ? eventData.getSnips().indexOf(snip) : -1;
//            if (eventSnipIndex >= 0) {
//                allEventSnips.remove(eventData);
//                eventData.getSnips().remove(eventSnipIndex);
//                snip.setIs_virtual_version(0);
//                eventData.addEventSnip(snip);
//                allEventSnips.add(eventData);
//            }
//        }
    }

    public void clearAllSnips() {
        allEventSnips.clear();
        snipList.clear();
    }

    public List<EventData> getAllSnip() {
        return allEventSnips;
    }

    public void clearAllParentSnips() {
        eventParentSnips.clear();
        parentsnipList.clear();
    }

    public List<EventData> getAllParentSnip() {
        return eventParentSnips;
    }

    public void setSnipDurations(int duration) {
        snipDurations.add(duration);
    }

    public List<Integer> getSnipDurations() {
        return snipDurations;
    }

    public void clearSnipDurations() {
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

    public List<Snip> getChildSnipsByParentSnipId(int eventId, int parentId) {
        List<Snip> childSnips = new ArrayList<>();
        for (EventData eventData : getAllSnip()) {
            if (eventId == eventData.getEvent().getEvent_id()) {
                for (Snip snip : eventData.getParentSnip()) {
                    if (snip.getSnip_id() == parentId) {
                        childSnips.add(snip);
                    }
                }
                for (Snip snip : eventData.getSnips()) {
                    if (snip.getParent_snip_id() == parentId || snip.getSnip_id() == parentId) {
                        childSnips.add(snip);
                    }
                }
            }
        }
        return childSnips;
    }

    public boolean isInsertionInProgress() {
        return isInsertionInProgress;
    }

    public void setInsertionInProgress(boolean insertionInProgress) {
        isInsertionInProgress = insertionInProgress;
    }

    public String getThumbFilePathRoot() {
        return thumbFilePathRoot;
    }

    public void setThumbFilePathRoot(String thumbFilePathRoot) {
        this.thumbFilePathRoot = thumbFilePathRoot;
    }
}
