package co.fitcom.videorecorder;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.Timer;
import java.util.TimerTask;

import co.fitcom.fancycamera.CameraEventListenerUI;
import co.fitcom.fancycamera.EventType;
import co.fitcom.fancycamera.FancyCamera;
import co.fitcom.fancycamera.PhotoEvent;
import co.fitcom.fancycamera.VideoEvent;

public class MainActivity extends AppCompatActivity {
    FancyCamera cameraView;
    VideoView videoPlayer;
    TextView durationView;
    Timer timer;
    TimerTask timerTask;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoPlayer = findViewById(R.id.videoPlayer);
        durationView = findViewById(R.id.durationView);
        cameraView = findViewById(R.id.holder);
        cameraView.setQuality(FancyCamera.Quality.HIGHEST.getValue());
        cameraView.setListener(new CameraEventListenerUI(){
            @Override
            public void onPhotoEventUI(PhotoEvent event) {

            }

            @Override
            public void onVideoEventUI(VideoEvent event) {
                if(event.getType() == EventType.INFO && event.getMessage().equals(VideoEvent.EventInfo.RECORDING_FINISHED.toString())){
                    timerTask.cancel();
                    timer.cancel();
                    videoPlayer.setVideoURI(Uri.fromFile(event.getFile()));
                    videoPlayer.start();
                }else if(event.getType() == EventType.INFO && event.getMessage().equals(VideoEvent.EventInfo.RECORDING_STARTED.toString())){
                    System.out.println("Recording Started");
                    timer = new Timer();
                    timerTask = new TimerTask() {
                        @Override
                         public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    durationView.setText(String.valueOf(cameraView.getDuration()));
                                }
                            });
                        }
                    };
                    timer.schedule(timerTask, 0, 1000);

                }else{
                    System.out.println(event.getMessage());
                }
            }

        });
    }

    public void startRecording(View view){
        cameraView.startRecording();
    }

    public void stopRecording(View view){
        cameraView.stopRecording();
    }

    public void toggleCamera(View view){
        cameraView.toggleCamera();
    }


    public void goToHome(View view){
        Intent i = new Intent(this,Home.class);
        startActivity(i);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED){
            cameraView.start();
        }
    }

}
