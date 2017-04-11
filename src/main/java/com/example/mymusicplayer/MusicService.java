package com.example.mymusicplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.IOException;
import java.util.List;

public class MusicService extends Service{
    public static final String MEDIA_ACTION = "org.crazyit.action.MEDIA_ACTION";
    public static final String CHANGE_TEXT ="com.example.action.CHANGE_TEXT";
    private MediaPlayer mediaPlayer;
    private List<Music> list;

    private int index;

    private SharedPreferences shp;
    private SharedPreferences.Editor editor;
    private int max;
    private int now;

    private NotificationManager manager;
    private Notification notification;
    private RemoteViews remoteView;
    private boolean isPlaying;
    private Thread thread;
    private MyReceiver receiver;
    public MusicService() {}
    @Override
    public IBinder onBind(Intent intent) {
        return new MyBinder();
    }
    @Override
    public void onCreate() {
        super.onCreate();
        MusicDataUtils.getAllMusic(MusicService.this);
        list = MusicDataUtils.allMusic;
        initNotification();
        mediaPlayer = new MediaPlayer();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(600);
                        now = mediaPlayer.getCurrentPosition();
                        max = mediaPlayer.getDuration();
                        shp = getSharedPreferences("data", MODE_PRIVATE);
                        editor = shp.edit();
                        editor.putInt("now", now);
                        editor.putInt("max", max);
                        editor.commit();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
    //初始化notification
    public void initNotification(){
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, PlayActivity.class), 0);
        remoteView = new RemoteViews(this.getPackageName(), R.layout.notification_layout);
        manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notification = new NotificationCompat.Builder(this)
                .setCustomContentView(remoteView)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(contentIntent)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),
                        R.drawable.icon))
                .build();
        manager.notify(1, notification);

        Intent notiIntent = new Intent(MainActivity.class.getSimpleName());
        notiIntent.putExtra(BUTTON_ACTION, 1);
        PendingIntent preIntent = PendingIntent.getBroadcast(MusicService.this,
                1, notiIntent, 0);
        remoteView.setOnClickPendingIntent(R.id.noti_player_btn_shang, preIntent);

        notiIntent.putExtra(BUTTON_ACTION, 2);
        PendingIntent playOrPauseIntent = PendingIntent.getBroadcast(MusicService.this,
                2, notiIntent, 0);
        remoteView.setOnClickPendingIntent(R.id.noti_player_btn_pauseorplay, playOrPauseIntent);

        notiIntent.putExtra(BUTTON_ACTION, 3);
        PendingIntent nextIntent = PendingIntent.getBroadcast(MusicService.this,
                3, notiIntent, 0);
        remoteView.setOnClickPendingIntent(R.id.noti_player_btn_xia, nextIntent);

        notiIntent.putExtra(BUTTON_ACTION, 4);
        PendingIntent exitIntent = PendingIntent.getBroadcast(MusicService.this,
                4, notiIntent, 0);
        remoteView.setOnClickPendingIntent(R.id.noti_exit, exitIntent);

        setNotiControl();
        remoteView.setImageViewResource(R.id.noti_player_btn_pauseorplay, R.drawable.player_btn_ting);
        manager.notify(1, notification);
        IntentFilter filter = new IntentFilter(MainActivity.class.getSimpleName());
        receiver = new MyReceiver();
        registerReceiver(receiver, filter);
    }

    public static final String BUTTON_ACTION = "com.example.mymusicplayer.Action";
    //设置notification中控件
    public void setNotiControl(){
        shp = getSharedPreferences("data", MODE_PRIVATE);
        index = shp.getInt("index", 0);
        isPlaying = shp.getBoolean("isPlaying", false);
        remoteView.setTextViewText(R.id.noti_player_music_name, list.get(index).getTitle());
        remoteView.setTextViewText(R.id.noti_player_music_artist, list.get(index).getArtist());
        if (isPlaying){
            remoteView.setImageViewResource(R.id.noti_player_btn_pauseorplay, R.drawable.player_btn_kai);
        }else {
            remoteView.setImageViewResource(R.id.noti_player_btn_pauseorplay, R.drawable.player_btn_ting);
        }
        manager.notify(1, notification);
    }

    private static final String TAG = "MusicService";
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy:  onDestroy");
        unregisterReceiver(receiver);
        manager.cancel(1);
        shp = getSharedPreferences("data", MODE_PRIVATE);
        editor = shp.edit();
        editor.putBoolean("isPlaying", false);
        editor.putBoolean("isFirstClick", true);
        editor.commit();

        mediaPlayer.stop();
        mediaPlayer.release();
        myBinder.stopMusicService(MusicService.this);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        return super.onStartCommand(intent, flags, startId);
    }

    boolean isFirstClick;
    /**
     * playMusic方法，用于MediaPlayer初始化，并开始播放。
     */
    public void playMusic(String path){
        try{
            mediaPlayer.reset();
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();

            remoteView.setImageViewResource(R.id.noti_player_btn_pauseorplay, R.drawable.player_btn_kai);
            manager.notify(1, notification);

            shp = getSharedPreferences("data",MODE_PRIVATE);
            index = shp.getInt("index",0);
            MyDBManage myDBManage = new MyDBManage(MusicService.this, "MusicStore.db");
            myDBManage.addData("RecentMusic", list.get(index));
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                index++;
                if (index == list.size()){index = 0;}
                shp = getSharedPreferences("data", MODE_PRIVATE);
                editor = shp.edit();
                editor.putInt("index", index);
                editor.commit();
                playMusic(list.get(index).getData());
                setNotiControl();
                Log.d(TAG, "onCompletion: onCompletion  index = " + index);
                Intent intent = new Intent(CHANGE_TEXT);
                sendBroadcast(intent);
            }
        });
    }

    /**
     * 自定义MyBinder类
     */
    private MyBinder  myBinder= new MyBinder();
    public class MyBinder extends Binder{
        //播放进度，并保存到shp
        public void pre(){
            shp = getSharedPreferences("data",MODE_PRIVATE);
            index = shp.getInt("index",0);
            if (index == 0 ){
                playMusic(list.get(list.size()-1).getData());
                index = list.size() - 1;
            }else {
                playMusic(list.get(index - 1).getData());
                index = index -1;
            }
            editor = shp.edit();
            editor.putInt("index", index);
            editor.putBoolean("isPlaying", true);
            editor.commit();
            setNotiControl();
        }
        public void next(){
            shp = getSharedPreferences("data",MODE_PRIVATE);
            index = shp.getInt("index",0);
            if (index == list.size() - 1){
                playMusic(list.get(0).getData());
                index = 0;
            }else {
                playMusic(list.get(index + 1).getData());
                index = index + 1;
            }
            editor = shp.edit();
            editor.putInt("index", index);
            editor.putBoolean("isPlaying", true);
            editor.commit();
            setNotiControl();
        }
        private static final String TAG = "MyBinder";
        public void play(){
            Log.d(TAG, "play:  play");
            shp = getSharedPreferences("data",MODE_PRIVATE);
            index = shp.getInt("index",0);
            playMusic(list.get(index).getData());
            setNotiControl();
        }
        public void pause(){
            Log.d(TAG, "pause: ");
            setNotiControl();
            remoteView.setImageViewResource(R.id.noti_player_btn_pauseorplay, R.drawable.player_btn_ting);
            manager.notify(1, notification);
            mediaPlayer.pause();
        }
        public void playOrPause(){
            shp = getSharedPreferences("data",MODE_PRIVATE);
            isFirstClick = shp.getBoolean("isFirstClick", true);
            index = shp.getInt("index", 0);
            if (mediaPlayer.isPlaying()){
                pause();
            } else {
                if (isFirstClick){
                    playMusic(list.get(index).getData());
                }else {
                    remoteView.setImageViewResource(R.id.noti_player_btn_pauseorplay, R.drawable.player_btn_kai);
                    manager.notify(1, notification);
                    mediaPlayer.start();}
            }
            editor = shp.edit();
            editor.putBoolean("isFirstClick", false);
            editor.putBoolean("isPlaying", mediaPlayer.isPlaying());
            editor.commit();
        }
        public void cancelNoti(){
            Log.d(TAG, "cancelNoti: ");
            manager.cancel(1);}
        public void stopMusicService(Context context){
            Log.d(TAG, "stopMusicService: ");
            Intent stopIntent = new Intent(context, MusicService.class);
            stopService(stopIntent);
        }
    }
    private class MyReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            setNotiControl();
            switch (intent.getIntExtra(BUTTON_ACTION, 0)){
                case 1:
                    myBinder.pre();
                    setNotiControl();
                    break;
                case 2:
                    myBinder.playOrPause();
                    setNotiControl();
                    break;
                case 3:
                    myBinder.next();
                    setNotiControl();
                    break;
                case 4:
                    Log.d(TAG, "onReceive: onReceive");
                    manager.cancel(1);
                    myBinder.stopMusicService(context);
                    ActivityCollector.finishAll();
                    break;
                default:
            }
        }
    }
}

