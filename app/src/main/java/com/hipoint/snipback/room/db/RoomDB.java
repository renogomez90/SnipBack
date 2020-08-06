package com.hipoint.snipback.room.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.hipoint.snipback.room.dao.EventDao;
import com.hipoint.snipback.room.dao.Hd_snipsDao;
import com.hipoint.snipback.room.dao.SnipsDao;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;

@Database(entities = {Event.class, Hd_snips.class, Snip.class}, version = 1, exportSchema = false)
public abstract class RoomDB extends RoomDatabase {

    private static RoomDB INSTANCE;
    private final static String DB_NAME= "SnipbackDb";

    public static synchronized RoomDB getDatabase(final Context context){
        if(INSTANCE == null){

            INSTANCE= Room.databaseBuilder(context.getApplicationContext(),
                    RoomDB.class, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build();

        }
        return INSTANCE;
    }
    public abstract EventDao eventDao();

    public abstract Hd_snipsDao hd_snipsDao();

    public abstract SnipsDao snipsDao();

}