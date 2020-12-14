package com.hipoint.snipback.room.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;

import java.util.List;

@Dao
public interface Hd_snipsDao {
    @Insert
    long insert(Hd_snips hd_snips);

    @Update
    void update(Hd_snips hd_snips);

    @Delete
    void delete(Hd_snips hd_snips);

    @Query("DELETE FROM Hd_snips")
    void deleteAll();

    @Query("SELECT * FROM Hd_snips")
    LiveData<List<Hd_snips>> getHDSnipsData();

    @Query("SELECT * FROM Hd_snips WHERE snip_id LIKE :snipId")
    List<Hd_snips> getBySnipId(int snipId);
}
