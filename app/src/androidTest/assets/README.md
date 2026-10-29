`qa-video.mp4` is a synthetic, silent, 30-second blue frame (320×240, 15 fps, H.264 baseline). It contains no third-party media and is packaged only in the instrumentation test APK.

Regenerate with FFmpeg:

```text
ffmpeg -f lavfi -i color=c=blue:s=320x240:r=15 -t 30 -an -c:v libx264 -profile:v baseline -pix_fmt yuv420p -movflags +faststart qa-video.mp4
```
