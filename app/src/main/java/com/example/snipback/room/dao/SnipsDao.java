package com.example.snipback.room.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;


import com.example.snipback.room.entities.Snips;

import java.util.List;
@Dao
public interface SnipsDao {
    @Insert
    void insert(Snips snips);

    @Query("DELETE FROM SNIPS")
    void deleteAll();

    @Query("SELECT * from SNIPS")
    LiveData<List<Snips>> getSnipsData();
}
