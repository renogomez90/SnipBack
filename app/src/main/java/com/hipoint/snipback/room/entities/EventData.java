package com.hipoint.snipback.room.entities;

import androidx.room.ColumnInfo;

import java.util.ArrayList;
import java.util.List;

public class EventData {

    private int event_id;
    private String event_title;
    private long event_created;

    private List<Snip> snips = new ArrayList<>();
    private List<Snip> parentSnip = new ArrayList<>();

    public int getEvent_id() {
        return event_id;
    }

    public void setEvent_id(int event_id) {
        this.event_id = event_id;
    }

    public String getEvent_title() {
        return event_title;
    }

    public void setEvent_title(String event_title) {
        this.event_title = event_title;
    }

    public long getEvent_created() {
        return event_created;
    }

    public void setEvent_created(long event_created) {
        this.event_created = event_created;
    }

    public List<Snip> getSnips() {
        return snips;
    }

    public List<Snip> getParentSnip() {
        return parentSnip;
    }

    public void addEventSnip(Snip snip) {
        this.snips.add(snip);
    }

    public void addEventParentSnip(Snip snip) {
        this.parentSnip.add(snip);
    }
}
