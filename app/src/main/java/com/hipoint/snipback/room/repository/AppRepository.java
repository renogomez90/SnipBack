package com.hipoint.snipback.room.repository;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;

import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.dao.EventDao;
import com.hipoint.snipback.room.dao.Hd_snipsDao;
import com.hipoint.snipback.room.dao.SnipsDao;
import com.hipoint.snipback.room.db.RoomDB;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;

import java.util.List;

public class AppRepository {
    private EventDao eventDao;
    private LiveData<Event> EventData;
    private Hd_snipsDao hd_snipsDao;
    private LiveData<Hd_snips> HdSnipsData;
    private SnipsDao snipsDao;
    private LiveData<Snip> SnipData;
    private static AppRepository instance;

    public interface OnTaskCompleted{
        void onTaskCompleted(Snip snip);
    }

    public AppRepository(Context context){
//        RoomDB db = AppClass.getAppInsatnce().database;
        RoomDB db = RoomDB.getDatabase(context);
        eventDao = db.eventDao();
        hd_snipsDao=db.hd_snipsDao();
        snipsDao= db.snipsDao();
    }

    public static AppRepository getInstance(){
        if(instance == null) {
            instance = new AppRepository(AppClass.getAppInstance().getContext());
        }
        return instance;
    }


//Event Table Actions START//
    public LiveData<List<Event>> getEventData(){
        return eventDao.getEventData();
    }

    public LiveData<Event> getEventById(int eventId){
        return eventDao.getEventByEventId(eventId);
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
            AppClass.getAppInstance().setLastEventId((int) dao.insert(events[0]));
            AppClass.getAppInstance().setLastCreatedEvent(events[0]);
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
            AppClass.getAppInstance().setLastHDSnipId(dao.insert(hd_snips[0]));
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
    public LiveData<List<Snip>> getSnipsData(){
        return snipsDao.getSnipsData();
    }

    public void insertSnip(OnTaskCompleted listener,@NonNull Snip snip){
        new InsertSnipAsync(listener,snipsDao).execute(snip);
    }

    private class InsertSnipAsync extends AsyncTask<Snip, Void, Snip> {
        private OnTaskCompleted listener;
        private SnipsDao dao;
        private int snipId;
        public InsertSnipAsync(OnTaskCompleted listener,SnipsDao dao){
            this.listener = listener;
            this.dao = dao;
        }

        @Override
        protected Snip doInBackground(Snip... snips) {
            snipId = (int)dao.insert(snips[0]);
            AppClass.getAppInstance().setLastSnipId(snipId);
            return snips[0];
        }

        @Override
        protected void onPostExecute(Snip aVoid) {
//            aVoid.setSnip_id(AppClass.getAppInstance().getLastSnipId());
//  since lastSnipId is asyncly updated we are not getting the correct snip_id, only the latest one.
            aVoid.setSnip_id(snipId);
            listener.onTaskCompleted(aVoid);
            super.onPostExecute(aVoid);
        }
    }

    //data update
    public void updateSnip(@NonNull Snip snip){
        new UpdateSnipAsync(snipsDao).execute(snip);
    }

    private class UpdateSnipAsync extends AsyncTask<Snip, Void, Void> {

        private SnipsDao dao;
        public UpdateSnipAsync(SnipsDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Snip... snips) {
            dao.update(snips[0]);
            return null;
        }
    }
    //data delete
    public void deleteSnip(@NonNull Snip snip){
        new DeleteSnipAsync(snipsDao).execute(snip);
    }

    private class DeleteSnipAsync extends AsyncTask<Snip, Void, Void> {

        private SnipsDao dao;
        public DeleteSnipAsync(SnipsDao dao){
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(Snip... snips) {
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
    int eventId = 0;
    public int getLastInsertedEventId(AppCompatActivity activity){
        AppViewModel appViewModel = ViewModelProviders.of(activity).get(AppViewModel.class);
        appViewModel.getEventLiveData().observe(activity, events -> {
            if(events.size() > 0) {
                Event lastEvent = events.get(events.size() - 1);
                eventId = lastEvent.getEvent_id();
                AppClass.getAppInstance().setLastEventId(eventId);
                AppClass.getAppInstance().setLastCreatedEvent(lastEvent);
            }
        });
        return eventId;
    }

    int snipId = 0;
    public int getLastInsertedSnipId(Fragment activity){
        AppViewModel appViewModel = ViewModelProviders.of(activity).get(AppViewModel.class);
        appViewModel.getSnipsLiveData().observe(activity, snips -> {
            if(snips.size() > 0) {
                Snip lastSnip = snips.get(snips.size() - 1);
                snipId = lastSnip.getSnip_id();
                AppClass.getAppInstance().setLastSnipId(snipId);
            }
        });
        return snipId;
    }


    //Snip Table Actions END//
}
