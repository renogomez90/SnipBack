package com.example.snipback.room.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.snipback.room.dao.EventDao;
import com.example.snipback.room.dao.Hd_snipsDao;
import com.example.snipback.room.dao.SnipsDao;
import com.example.snipback.room.entities.Event;
import com.example.snipback.room.entities.Hd_snips;
import com.example.snipback.room.entities.Snips;

@Database(entities = {Event.class, Hd_snips.class, Snips.class}, version = 1, exportSchema = false)
public abstract class RoomDB extends RoomDatabase {

    private static RoomDB INSTANCE;
    private final static String DB_NAME= "SnipbackDb";
    public abstract EventDao eventDao();
    public abstract Hd_snipsDao hd_snipsDao();
    public abstract SnipsDao snipsDao();


    public static RoomDB getDatabase(final Context context){
        if(INSTANCE == null){

            INSTANCE= Room.databaseBuilder(context,
                    RoomDB.class, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build();

        }
        return INSTANCE;
    }


}