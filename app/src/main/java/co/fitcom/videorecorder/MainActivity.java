package co.fitcom.videorecorder;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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
    Timer levelsTask;
    double level = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoPlayer = findViewById(R.id.videoPlayer);
        durationView = findViewById(R.id.durationView);
        cameraView = findViewById(R.id.holder);
        cameraView.setCameraPosition(FancyCamera.CameraPosition.BACK);
        cameraView.setQuality(FancyCamera.Quality.HIGHEST.getValue());
        cameraView.setListener(new CameraEventListenerUI() {
            @Override
            public void onCameraOpenUI() {
                Log.d("co.fitcom.test", "Camera Opened");
            }

            @Override
            public void onCameraCloseUI() {
                Log.d("co.fitcom.test", "Camera Close");
            }

            @Override
            public void onPhotoEventUI(PhotoEvent event) {

            }

            @Override
            public void onVideoEventUI(VideoEvent event) {
                if (event.getType() == EventType.INFO && event.getMessage().equals(VideoEvent.EventInfo.RECORDING_FINISHED.toString())) {
                    timerTask.cancel();
                    timer.cancel();
                    videoPlayer.setVideoURI(Uri.fromFile(event.getFile()));
                    videoPlayer.start();
                } else if (event.getType() == EventType.INFO && event.getMessage().equals(VideoEvent.EventInfo.RECORDING_STARTED.toString())) {
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

                } else {
                    System.out.println(event.getMessage());
                }
            }

        });
    }

    public void startRecording(View view) {
        cameraView.startRecording();
    }

    public void stopRecording(View view) {
        cameraView.stopRecording();
    }

    public void toggleCamera(View view) {
        cameraView.toggleCamera();
    }


    public void goToVideo(View view) {
        Intent i = new Intent(this, MainActivity.class);
        startActivity(i);
    }

    public void goToPhoto(View view) {
        Intent i = new Intent(this, Photo.class);
        startActivity(i);
    }

    public void toggleFlash(View view) {
        cameraView.toggleFlash();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.release();
        if (levelsTask != null) {
            levelsTask.cancel();
        }
        if (timerTask != null) {
            timerTask.cancel();
        }
        timerTask = null;
        levelsTask = null;
    }

    void start() {
        if (levelsTask == null) {
            levelsTask = new Timer();
            levelsTask.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            level = Math.pow(10, (0.02 * cameraView.getDB()));
                            Log.d("co.test", "Audio Levels" + level);
                        }
                    });
                }
            }, 0, 1000);
        }
        cameraView.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Boolean b = cameraView.hasPermission();
        Log.d("hasPermission", b.toString());
        if (cameraView.hasPermission()) {
            start();
        } else {
            cameraView.requestPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            start();
        }
    }

}
