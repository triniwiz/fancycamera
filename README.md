# FancyCamera

[![Download][bintray_svg]][bintray_url]
![][camera_svg]
[![Build Status][build_status_svg]][build_status_link]

[build_status_svg]: https://travis-ci.org/triniwiz/fancycamera.svg?branch=master
[build_status_link]: https://travis-ci.org/triniwiz/fancycamera
[bintray_svg]: https://api.bintray.com/packages/triniwiz/maven/fancycamera/images/download.svg
[bintray_url]: https://bintray.com/triniwiz/maven/fancycamera/_latestVersion
[camera_svg]: https://img.shields.io/badge/Android-fancycamera-yellowgreen.svg
## Installation

```
compile 'co.fitcom:fancycamera:0.0.1'
```

## Usage

```xml
<co.fitcom.fancycamera.FancyCamera
        app:cameraPosition="back"
        app:quality="max1080p"
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

# TODO

- [x] Take photos
- [x] Enable/Disable flash
