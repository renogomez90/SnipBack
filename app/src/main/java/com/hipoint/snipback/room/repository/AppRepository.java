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
    public void insertEvent(@NonNull Event event){
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
    public void updateEvent(@NonNull Event event){
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
    public void deleteEvent(@NonNull Event event){
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
    public void deleteAllEvent(){
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

    public void insertHd_snips(@NonNull Hd_snips hd_snips){
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

    //data update
    public void updateHDSnip(@NonNull Hd_snips hd_snips){
        new UpdateHDSnipAsync(hd_snipsDao).execute(hd_snips);
    }

    private class UpdateHDSnipAsync extends AsyncTask<Hd_snips, Void, Void> {

        private Hd_snipsDao dao;
        public UpdateHDSnipAsync(Hd_snipsDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Hd_snips... hd_snips) {
            dao.update(hd_snips[0]);
            return null;
        }
    }
    //data delete
    public void deleteHDSnip(@NonNull Hd_snips hd_snips){
        new DeleteHDSnipAsync(hd_snipsDao).execute(hd_snips);
    }

    private class DeleteHDSnipAsync extends AsyncTask<Hd_snips, Void, Void> {

        private Hd_snipsDao dao;
        public DeleteHDSnipAsync(Hd_snipsDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Hd_snips... hd_snips) {
            dao.delete(hd_snips[0]);
            return null;
        }
    }
    public void deleteAllHDSnip(){
        new DeleteAllHDSnipAsync(hd_snipsDao).execute();
    }

    private class DeleteAllHDSnipAsync extends AsyncTask<Void, Void, Void> {

        private Hd_snipsDao dao;
        public DeleteAllHDSnipAsync(Hd_snipsDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            dao.deleteAll();
            return null;
        }
    }


    //HDSNIP Table Actions END//

    //SNIP table Actions START//
    public LiveData<List<Snips>> getSnipsData(){
        return snipsDao.getSnipsData();
    }

    public void insertSnips(@NonNull Snips snips){
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

    //data update
    public void updateSnip(@NonNull Snips snips){
        new UpdateSnipAsync(snipsDao).execute(snips);
    }

    private class UpdateSnipAsync extends AsyncTask<Snips, Void, Void> {

        private SnipsDao dao;
        public UpdateSnipAsync(SnipsDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Snips... snips) {
            dao.update(snips[0]);
            return null;
        }
    }
    //data delete
    public void deleteSnip(@NonNull Snips snips){
        new DeleteSnipAsync(snipsDao).execute(snips);
    }

    private class DeleteSnipAsync extends AsyncTask<Snips, Void, Void> {

        private SnipsDao dao;
        public DeleteSnipAsync(SnipsDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Snips... snips) {
            dao.delete(snips[0]);
            return null;
        }
    }

    public void deleteAllSnip(){
        new DeleteAllSnipAsync(snipsDao).execute();
    }

    private class DeleteAllSnipAsync extends AsyncTask<Void, Void, Void> {

        private SnipsDao dao;
        public DeleteAllSnipAsync(SnipsDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            dao.deleteAll();
            return null;
        }
    }


    //Snip Table Actions END//
}
