package org.apache.cordova.mediacapture;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.ai.cucom.opaas.app.R;

import java.io.File;
import java.util.List;

public class VideoCaptureActivity extends Activity {

    private static final String TAG = VideoCaptureActivity.class.getName();

    private MediaRecorder recorder;
    private Camera camera;
    private Camera.Size size;
    private MediaPlayer player;

    private SurfaceView surfaceView;
    private Button switchCameraBtn;
    private Button captureBtn;
    private Button playBtn;
    private Button confirmBtn;
    private SurfaceHolder surfaceHolder;
    private TextView timeView;
    private int recordTime = 0;

    private boolean isRecording = false;

    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private File outputFile;

    private Handler handler = new Handler();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            recordTime++;
            int minute = recordTime / 60;
            int second = recordTime % 60;
            timeView.setText((minute < 10 ? "0" + minute : minute) + ":" + (second < 10 ? "0" + second : second));
            handler.postDelayed(this, 1000);
        }
    };

    private SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            VideoCaptureActivity.this.surfaceHolder = surfaceHolder;
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
            VideoCaptureActivity.this.surfaceHolder = surfaceHolder;
            startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            releaseMediaRecorder();
            releaseCamera();
            releasePlayer();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_video_capture);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        switchCameraBtn = (Button) findViewById(R.id.switchCameraBtn);
        captureBtn = (Button) findViewById(R.id.captureBtn);
        playBtn = (Button) findViewById(R.id.playBtn);
        confirmBtn = (Button) findViewById(R.id.confirmBtn);
        timeView = (TextView) findViewById(R.id.time);

        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(surfaceHolderCallback);
    }

    private void startPreview() {
        try {
            if (camera == null) {
                if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    camera = CameraHelper.getDefaultBackFacingCameraInstance();
                } else {
                    camera = CameraHelper.getDefaultFrontFacingCameraInstance();
                }

                Camera.Parameters parameters = camera.getParameters();
                int w = surfaceView.getWidth();
                int h = surfaceView.getHeight();
                if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                    w = surfaceView.getHeight();
                    h = surfaceView.getWidth();
                    parameters.set("orientation", "portrait");
                    camera.setDisplayOrientation(90);
                } else {
                    parameters.set("orientation", "landscape");
                    camera.setDisplayOrientation(0);
                }

                List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
                List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
                while (Math.max(w, h) > 800) {
                    w /= 2;
                    h /= 2;
                }
                this.size = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes, mSupportedPreviewSizes, w, h);

                parameters.setPreviewSize(this.size.width, this.size.height);
                if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                }
                camera.setParameters(parameters);

                camera.setPreviewDisplay(surfaceView.getHolder());
                camera.startPreview();
            }
        } catch (Exception e) {
            Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
        }
    }


    public void switchCamera(View view) {
        releaseMediaRecorder();
        releaseCamera();
        releasePlayer();
        playBtn.setVisibility(View.GONE);
        confirmBtn.setVisibility(View.GONE);
        captureBtn.setVisibility(View.VISIBLE);

        if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        startPreview();
    }

    public void startCapture(View view) {
        if (isRecording) {
            try {
                recorder.stop();
            } catch (RuntimeException e) {
                Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
                outputFile.delete();
            }
            releaseMediaRecorder();
            releaseCamera();
            setCaptureBtnBg();
            playBtn.setVisibility(View.VISIBLE);
            confirmBtn.setVisibility(View.VISIBLE);
            timeView.setVisibility(View.GONE);
            isRecording = false;
        } else {
            if (player != null && player.isPlaying()) {
                player.stop();
            }
            playBtn.setVisibility(View.GONE);
            confirmBtn.setVisibility(View.GONE);
            startPreview();
            if (prepareVideoRecorder()) {
                try {
                    recorder.start();
                    timeView.setVisibility(View.VISIBLE);
                    recordTime = 0;
                    handler.post(runnable);
                    setCaptureBtnBg();
                } catch (Exception e) {
                    Log.d(TAG, "", e);
                }
                isRecording = true;
            } else {
                releaseMediaRecorder();
            }
        }
    }

    private void setCaptureBtnBg() {
        if (isRecording) {
            captureBtn.setBackgroundResource(R.drawable.capture_video_record_bg);
        } else {
            captureBtn.setBackgroundResource(R.drawable.capture_video_stop_record_bg);
        }
    }

    private boolean prepareVideoRecorder() {
        try {
            recorder = new MediaRecorder();
            camera.unlock();
            recorder.setCamera(camera);

            recorder.setOrientationHint(90);//视频旋转90度

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

            recorder.setVideoEncodingBitRate(1024 * 1024);
            recorder.setVideoSize(this.size.width, this.size.height);

            outputFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO);
            if (outputFile == null) {
                return false;
            }
            recorder.setPreviewDisplay(surfaceHolder.getSurface());
            recorder.setOutputFile(outputFile.getPath());
            recorder.prepare();
        } catch (Exception e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    public void play(View view) {
        try {
            player = new MediaPlayer();
            player.reset();
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    playBtn.setVisibility(View.VISIBLE);
                    captureBtn.setVisibility(View.VISIBLE);
                    confirmBtn.setVisibility(View.VISIBLE);
                    switchCameraBtn.setVisibility(View.VISIBLE);
                    releasePlayer();
                }
            });
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(outputFile.getPath());
            player.setDisplay(surfaceHolder);
            player.prepare();
            player.start();
            switchCameraBtn.setVisibility(View.GONE);
            playBtn.setVisibility(View.GONE);
            captureBtn.setVisibility(View.GONE);
            confirmBtn.setVisibility(View.GONE);
        } catch (Exception e) {
            Log.d(TAG, "播放失败", e);
        }
    }

    public void confirm(View view) {
        Intent intent = this.getIntent();
        intent.setData(Uri.fromFile(outputFile));
        this.setResult(Activity.RESULT_OK, intent);
        this.finish();
    }

    private void releaseMediaRecorder() {
        if (recorder != null) {
            recorder.reset();
            recorder.release();
            recorder = null;
            if (camera != null) {
                camera.lock();
            }
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.reset();
            player.release();
            player = null;
        }
    }
}
