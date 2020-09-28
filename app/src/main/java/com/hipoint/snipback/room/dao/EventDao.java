package com.hipoint.snipback.room.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.hipoint.snipback.room.entities.Event;

import java.util.List;

@Dao
public interface EventDao {
    @Insert
    long insert(Event event);

    @Insert
    void update(Event event);

    @Insert
    void delete(Event event);

    @Query("DELETE FROM EVENT")
    void deleteAll();

    @Query("SELECT * from EVENT")
    LiveData<List<Event>> getEventData();

    @Query("SELECT * from EVENT WHERE event_id=:eventId")
    LiveData<Event> getEventByEventId(int eventId);
}
