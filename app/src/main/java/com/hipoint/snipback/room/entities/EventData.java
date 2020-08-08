package com.hipoint.snipback.room.entities;

import android.util.Log;

import androidx.room.ColumnInfo;

import java.util.ArrayList;
import java.util.List;

public class EventData {

    private Event event;

    private List<Snip> snips = new ArrayList<>();
    private List<Snip> parentSnip = new ArrayList<>();

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public List<Snip> getSnips() {
        return snips;
    }

    public List<Snip> getParentSnip() {
        return parentSnip;
    }

    public void addEventAllSnip(List<Snip> newSnip) {
        snips.addAll(newSnip);
    }
    public void addEventAllParentSnip(List<Snip> newSnip) {
        parentSnip.addAll(newSnip);
    }

    public void addEventSnip(Snip newSnip) {
        boolean snipStatus = false;

        for (Snip snip : snips) {
            if (snip.getSnip_id() == newSnip.getSnip_id()) {
                snipStatus = true;
                break;
            }
        }
        if (!snipStatus) {
            this.snips.add(newSnip);
        }
        for (Snip snip : snips) {
            if (snip.getSnip_id() == newSnip.getSnip_id()) {
                int index = snips.indexOf(newSnip);
                if(index >= 0) {
                    snips.set(index,newSnip);
                }
            }
        }
//        Log.i("Snip Update","Success");
//        int index = snips.size() > 0 ? snips.indexOf(newSnip) : -1;
//        if(index >= 0){
//            this.snips.remove(index);
//            this.snips.add(index,newSnip);
//        }else {
//            this.snips.add(newSnip);
//        }
    }

    public void clearSnip(){
        if(snips.size() > 0) snips.clear();
    }

    public void addEventParentSnip(Snip snip) {
        int index = parentSnip.size() > 0 ? parentSnip.indexOf(snip) : -1;
        if(index >= 0){
            this.parentSnip.remove(index);
            this.parentSnip.add(index,snip);
        }else {
            this.parentSnip.add(snip);
        }
    }

}
