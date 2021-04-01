package com.hipoint.snipback.room.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.hipoint.snipback.room.dao.EventDao;
import com.hipoint.snipback.room.dao.Hd_snipsDao;
import com.hipoint.snipback.room.dao.SnipsDao;
import com.hipoint.snipback.room.dao.TagDao;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;
import com.hipoint.snipback.room.entities.Tags;

@Database(entities = {Event.class, Hd_snips.class, Snip.class, Tags.class}, version = 2, exportSchema = false)
public abstract class RoomDB extends RoomDatabase {

    private static RoomDB INSTANCE;
    private final static String DB_NAME = "SnipbackDb";

    public static synchronized RoomDB getDatabase(final Context context) {
        if (INSTANCE == null) {

            INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                    RoomDB.class, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_1_2)
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build();

        }
        return INSTANCE;
    }

    public abstract EventDao eventDao();

    public abstract Hd_snipsDao hd_snipsDao();

    public abstract SnipsDao snipsDao();

    public abstract TagDao tagDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE Tag (" +
                    "tag_id INTEGER PRIMARY KEY, " +
                    "snip_id INTEGER, " +
                    "audio_path TEXT, " +
                    "audio_position INTEGER, " +
                    "tag_colour_id INTEGER, " +
                    "share_later INTEGER, "+
                    "link_later INTEGER, " +
                    "text_tag TEXT)");
        }
    };

}