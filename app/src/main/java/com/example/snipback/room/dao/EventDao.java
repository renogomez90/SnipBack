package com.example.snipback.room.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.snipback.room.entities.Event;

import java.util.List;
@Dao
public interface EventDao {
    @Insert
    void insert(Event event);

    @Query("DELETE FROM EVENT")
    void deleteAll();

    @Query("SELECT * from EVENT")
    LiveData<List<Event>> getEventData();
}
