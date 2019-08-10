package co.fitcom.videorecorder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import co.fitcom.fancycamera.CameraEventListenerUI;
import co.fitcom.fancycamera.EventType;
import co.fitcom.fancycamera.FancyCamera;
import co.fitcom.fancycamera.PhotoEvent;
import co.fitcom.fancycamera.VideoEvent;

public class Photo extends AppCompatActivity {
    FancyCamera cameraView;
    ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        imageView = findViewById(R.id.imageView);
        cameraView = findViewById(R.id.PhotoView);
        cameraView.setQuality(FancyCamera.Quality.HIGHEST.getValue());
        cameraView.setListener(new CameraEventListenerUI() {
            @Override
            public void onCameraOpenUI() {

            }

            @Override
            public void onCameraCloseUI() {

            }

            @Override
            public void onPhotoEventUI(PhotoEvent event) {
                if (event.getType() == EventType.INFO && event.getMessage().equals(PhotoEvent.EventInfo.PHOTO_TAKEN.toString())) {
                    Bitmap myBitmap = BitmapFactory.decodeFile(event.getFile().getAbsolutePath());
                    imageView.setImageBitmap(myBitmap);
                } else {
                    System.out.println(event.getMessage());
                }
            }

            @Override
            public void onVideoEventUI(VideoEvent event) {

            }

        });
        cameraView.setSaveToGallery(true);
    }

    public void takePhoto(View view) {
        if(cameraView.hasStoragePermission()){
            cameraView.takePhoto();
        }else {
            cameraView.requestStoragePermission();
        }
    }

    public void toggleFlash(View view) {
        cameraView.toggleFlash();
    }

    public void toggleCamera(View view) {
        cameraView.toggleCamera();
    }

    public void goToHome(View view) {
        Intent i = new Intent(this, Home.class);
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
        int count = 0;
        for (int grant : grantResults) {
            if (permissions[count].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) && grant == PackageManager.PERMISSION_GRANTED) {
                cameraView.takePhoto();
                break;
            }
            count++;
        }
        if (cameraView.hasPermission()) {
            cameraView.start();
        }
    }

}
