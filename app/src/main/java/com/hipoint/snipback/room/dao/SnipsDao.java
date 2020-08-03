package com.hipoint.snipback.room.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;


import com.hipoint.snipback.room.entities.Snip;

import java.util.List;
@Dao
public interface SnipsDao {
    @Insert
    void insert(Snip snip);

    @Insert
    void update(Snip snip);
    @Insert
    void delete(Snip snip);

    @Query("DELETE FROM Snip")
    void deleteAll();

    @Query("SELECT * from Snip")
    LiveData<List<Snip>> getSnipsData();
}
