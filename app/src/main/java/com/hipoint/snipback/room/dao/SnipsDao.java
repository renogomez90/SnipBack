package com.hipoint.snipback.room.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;


import com.hipoint.snipback.room.entities.Snip;

import java.util.List;
@Dao
public interface SnipsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Snip snip);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(Snip snip);

    @Delete
    void delete(Snip snip);

    @Query("DELETE FROM Snip")
    void deleteAll();

    @Query("SELECT * FROM Snip WHERE snip_id LIKE :id")
    Snip getSnipById(Integer id);

    @Query("SELECT * from Snip")
    LiveData<List<Snip>> getSnipsData();
}
