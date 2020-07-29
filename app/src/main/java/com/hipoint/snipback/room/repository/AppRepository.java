package com.hipoint.snipback.room.repository;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.hipoint.snipback.room.dao.EventDao;
import com.hipoint.snipback.room.dao.Hd_snipsDao;
import com.hipoint.snipback.room.dao.SnipsDao;
import com.hipoint.snipback.room.db.RoomDB;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snips;

import java.util.List;

public class AppRepository {
    private EventDao eventDao;
    private LiveData<Event> EventData;
    private Hd_snipsDao hd_snipsDao;
    private LiveData<Hd_snips> HdSnipsData;
    private SnipsDao snipsDao;
    private LiveData<Snips> SnipData;

    public AppRepository(Context context){
        RoomDB db = RoomDB.getDatabase(context);
        eventDao = db.eventDao();
        hd_snipsDao=db.hd_snipsDao();
        snipsDao= db.snipsDao();
    }

//Event Table Actions START//
    public LiveData<List<Event>> getEventData(){
        return eventDao.getEventData();
    }
//data insert
    public void insert(@NonNull Event event){
        new InsertEventAsync(eventDao).execute(event);
    }

    private class InsertEventAsync extends AsyncTask<Event, Void, Void> {

        private EventDao dao;
        public InsertEventAsync(EventDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Event... events) {
            dao.insert(events[0]);
            return null;
        }
    }
//data update
    public void update(@NonNull Event event){
        new UpdateEventAsync(eventDao).execute(event);
    }

    private class UpdateEventAsync extends AsyncTask<Event, Void, Void> {

        private EventDao dao;
        public UpdateEventAsync(EventDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Event... events) {
            dao.update(events[0]);
            return null;
        }
    }
    //data delete
    public void delete(@NonNull Event event){
        new DeleteEventAsync(eventDao).execute(event);
    }

    private class DeleteEventAsync extends AsyncTask<Event, Void, Void> {

        private EventDao dao;
        public DeleteEventAsync(EventDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Event... events) {
            dao.delete(events[0]);
            return null;
        }
    }
    public void deleteAll(){
        new DeleteAllEventAsync(eventDao).execute();
    }

    private class DeleteAllEventAsync extends AsyncTask<Void, Void, Void> {

        private EventDao dao;
        public DeleteAllEventAsync(EventDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            dao.deleteAll();
            return null;
        }
    }

    //Event Table Actions END//

    //HDSNIP Table Actions START//
    public LiveData<List<Hd_snips>> getHDSnipsData(){
        return hd_snipsDao.getHDSnipsData();
    }

    public void insert(@NonNull Hd_snips hd_snips){
        new InsertHDSnipAsync(hd_snipsDao).execute(hd_snips);
    }

    private class InsertHDSnipAsync extends AsyncTask<Hd_snips, Void, Void> {

        private Hd_snipsDao dao;
        public InsertHDSnipAsync(Hd_snipsDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Hd_snips... hd_snips) {
            dao.insert(hd_snips[0]);
            return null;
        }

    }


    //HDSNIP Table Actions END//

    //SNIP table Actions START//
    public LiveData<List<Snips>> getSnipsData(){
        return snipsDao.getSnipsData();
    }

    public void insert(@NonNull Snips snips){
        new InsertSnipAsync(snipsDao).execute(snips);
    }

    private class InsertSnipAsync extends AsyncTask<Snips, Void, Void> {

        private SnipsDao dao;
        public InsertSnipAsync(SnipsDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Snips... snips) {
            dao.insert(snips[0]);
            return null;
        }
    }
    //Snip Table Actions END//
}
