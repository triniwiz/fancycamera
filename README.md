# FancyCamera

![Maven Central](https://img.shields.io/maven-central/v/io.github.triniwiz/fancycamera?color=blue&label=Download&style=for-the-badge)

## Installation

```
implementation 'io.github.triniwiz:fancycamera:3.0.0-alpha21'
```

## Usage

```xml
<co.fitcom.fancycamera.FancyCamera
        app:fcCameraPosition="back"
        app:fcQuality="max1080p"
        android:id="@+id/cameraView"
        android:layout_width="match_parent"
        android:layout_height="300dp"
        android:orientation="vertical"
/>
```

## Api

| Method                  | Default | Type    | Description                                           |
| ----------------------- | ------- | ------- | ----------------------------------------------------- |
| start()                 |         | void    | Starts the camera preview                             |
| stop()                  |         | void    | Stop the camera preview                               |
| startRecording()        |         | void    | Start recording camera preview.                       |
| stopRecording()         |         | void    | Stop recording camera preview.                        |
| toggleCamera()          |         | void    | Toggles between front or the back camera.             |
| getDuration()           |         | int     | Get the current recording video duration.             |
| hasCamera()             |         | boolean | Checks if there are any camera available.             |
| setCameraPosition()     |         | void    | Sets camera position front/back                       |
| setCameraOrientation()  |         | void    | Used to force an orientation in the output file       |

# TODO

- [x] Take photos
- [x] Enable/Disable flash
