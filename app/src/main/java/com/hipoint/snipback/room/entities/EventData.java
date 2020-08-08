package com.hipoint.snipback.room.entities;

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

    @Override()
    public boolean equals(Object other) {
        // This is unavoidable, since equals() must accept an Object and not something more derived
        if (other instanceof EventData) {
            // Note that I use equals() here too, otherwise, again, we will check for referential equality.
            // Using equals() here allows the Model class to implement it's own version of equality, rather than
            // us always checking for referential equality.
            EventData otherProduct = (EventData) other;
            return otherProduct.getEvent().getEvent_id() == this.getEvent().getEvent_id();
        }

        return false;
    }

}
