package com.hipoint.snipback.room.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "Event")
public class Event {

    public final static String TABLE_NAME = "Event";
    private final static String COLUMN_EVENT_ID = "event_id";
    private final static String COLUMN_EVENT_TITLE = "event_title";
    private final static String COLUMN_EVENT_CREATED = "event_created";


    @PrimaryKey(autoGenerate = true)
    @NonNull
    @ColumnInfo(name = COLUMN_EVENT_ID)
    private int event_id;

    @ColumnInfo(name = COLUMN_EVENT_TITLE)
    private String event_title;
    @ColumnInfo(name = COLUMN_EVENT_CREATED)
    private long event_created;

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

}
