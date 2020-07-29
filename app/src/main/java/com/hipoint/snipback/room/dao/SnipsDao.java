package com.hipoint.snipback.room.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;


import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Snips;

import java.util.List;
@Dao
public interface SnipsDao {
    @Insert
    void insert(Snips snips);

    @Insert
    void update(Snips snips);
    @Insert
    void delete(Snips snips);

    @Query("DELETE FROM SNIPS")
    void deleteAll();

    @Query("SELECT * from SNIPS")
    LiveData<List<Snips>> getSnipsData();
}
