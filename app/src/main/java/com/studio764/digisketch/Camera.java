package com.studio764.digisketch;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcelable;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.util.TimingLogger;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;

import com.dropbox.core.v2.DbxClientV2;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.util.DateTime;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.sql.Time;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Camera extends AppCompatActivity {
    private static final String TAG = "AndroidCameraApi";

    private Button takePictureButton;
    private TextureView textureView;
    private ImageView cam_flicker;
    private ImageView cam_processing;
    private ImageButton flash_on;
    private ImageButton flash_off;

    private GoogleSignInAccount google_account;
    private DriveServiceHelper mDriveServiceHelper;
    private String dropbox_authToken;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size[] sizes;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private int jpegWidth;
    private int jpegHeight;
    private int starting_y;
    private float scaling_factor;

    private boolean flashMode;
    private ProgressBar progressBar;
    private TextView progressText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);

        takePictureButton = (Button) findViewById(R.id.btn_takepicture);
        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        google_account = getIntent().getParcelableExtra("googleAccount");
        mDriveServiceHelper = getIntent().getParcelableExtra("googleServiceHelper");
        dropbox_authToken = getIntent().getStringExtra("dropboxAuthToken");

        flashMode = false;
    }
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCameraWithHandlerThread();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(Camera.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };
    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    protected void takePicture() {
        cam_flicker.setVisibility(View.VISIBLE);
        cam_flicker.postDelayed(new Runnable() {
            @Override
            public void run() {
                cam_flicker.setVisibility(View.INVISIBLE);
            }
        }, 100);

        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }

        takePictureButton.setEnabled(false);

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            Size bestsize = getBestSize(jpegSizes);
            jpegWidth = bestsize.getWidth();
            jpegHeight = bestsize.getHeight();

            ImageReader reader = ImageReader.newInstance(jpegWidth, jpegHeight, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Flash
            if (flashMode)
                captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
            // Orientation
            final int rotation = getWindowManager().getDefaultDisplay().getRotation();
            //captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION));
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 80);
            //final File file = new File(Environment.getExternalStorageDirectory()+"/pic.jpg");
            final File file = new File(this.getCacheDir()+"/cached_pic.jpg");

            cam_processing.setAlpha(0f);
            cam_processing.setVisibility(View.VISIBLE);
            cam_processing.animate()
                    .alpha(1f)
                    .setStartDelay(200)
                    .setDuration(200)
                    .setListener(null);
            progressBar.setAlpha(0f);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.animate()
                    .alpha(1f)
                    .setStartDelay(200)
                    .setDuration(200)
                    .setListener(null);
            progressText.setAlpha(0f);
            progressText.setVisibility(View.VISIBLE);
            progressText.animate()
                    .alpha(1f)
                    .setStartDelay(200)
                    .setDuration(200)
                    .setListener(null);

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        //4032:3024 -> 4032:2367
                        //1038:1768
                        int cropped_height = jpegHeight;
                        int cropped_width = jpegWidth;
                        int new_height = (int) (((double) textureView.getWidth() / textureView.getHeight()) * cropped_width);

                        progressBar.setProgress(5);
                        String cached_path = save(bytes);
                        progressBar.setProgress(15);

                        try {
                            Thread.sleep(100);
                        } catch (Exception e){
                            e.printStackTrace();
                        }

                        // Rotate image
                        // Find out if the picture needs rotating by looking at its Exif data
                        ExifInterface exifInterface = new ExifInterface(new ByteArrayInputStream(bytes));
                        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                        int rotationDegrees = 0;
                        switch (orientation) {
                            case ExifInterface.ORIENTATION_ROTATE_90:
                                rotationDegrees = 90;
                                break;
                            case ExifInterface.ORIENTATION_ROTATE_180:
                                rotationDegrees = 180;
                                break;
                            case ExifInterface.ORIENTATION_ROTATE_270:
                                rotationDegrees = 270;
                                break;
                        }
                        // Create and rotate the bitmap by rotationDegrees

                        openPostProcessActivity(cached_path, jpegHeight, jpegWidth, starting_y, scaling_factor, flashMode, rotationDegrees);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
                private String save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                    return file.getPath();
                }
                private Bitmap RotateBitmap(Bitmap source, float angle)
                {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(angle);
                    return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(Camera.this, "Saved2:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            Size imageDimension = getBestSize(sizes); // 1.1513
            Log.d("texture_test", "best size: w" + imageDimension.getWidth() + " h" + imageDimension.getHeight());
            Log.d("texture_test", "wrapper size: w" + findViewById(R.id.image_wrapper).getWidth() + " h" + findViewById(R.id.image_wrapper).getHeight());

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ConstraintLayout.LayoutParams cl = new ConstraintLayout.LayoutParams((int) (imageDimension.getHeight() * scaling_factor), ViewGroup.LayoutParams.MATCH_PARENT); // 1768, parent
                    cl.leftToLeft = R.id.image_wrapper;
                    cl.rightToRight = R.id.image_wrapper;
                    textureView.setLayoutParams(cl);
                }
            });

            jpegHeight = imageDimension.getWidth();
            jpegWidth = imageDimension.getHeight();
            scaling_factor = (float) findViewById(R.id.image_wrapper).getHeight()/imageDimension.getWidth();    //1.1513f;
            Log.d("scaling_test", scaling_factor + ".");
            Log.d("scaling_test", (imageDimension.getHeight() * scaling_factor) + ".");

            starting_y = (jpegWidth - findViewById(R.id.image_wrapper).getWidth());
            Log.d("_test_test", starting_y +"/");

            float texture_ratio = (float)findViewById(R.id.image_wrapper).getWidth()/findViewById(R.id.image_wrapper).getHeight();
            if (texture_ratio > 0.75) {
                texture.setDefaultBufferSize((int) (imageDimension.getHeight() * 0.75f), imageDimension.getHeight());
                starting_y = 0;
            }
            else {
                texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            }


            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(Camera.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private Size getBestSize(Size[] sizes) {
        float texture_ratio = (float)findViewById(R.id.image_wrapper).getWidth()/findViewById(R.id.image_wrapper).getHeight();
        Log.d("bestsize_testtest", findViewById(R.id.image_wrapper).getWidth() + " " + findViewById(R.id.image_wrapper).getHeight() + " " + texture_ratio);

        for (int i = 0; i < sizes.length; i++) {
            float size_ratio = ((float)sizes[i].getHeight()/(float)sizes[i].getWidth());
            Log.d("bestsize_testtest", sizes[i].getWidth() + " " + sizes[i].getHeight() + " " + ((float) sizes[i].getHeight()/sizes[i].getWidth()));

            if (findViewById(R.id.image_wrapper).getHeight() >= sizes[i].getWidth()) {
                if (size_ratio == 0.75) {
                    Log.d("bestsize_testtest", "^ this one");

                    return sizes[i];
                }
            }
        }

        return sizes[8];
    }
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            sizes = map.getOutputSizes(SurfaceTexture.class);
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(Camera.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }
    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(Camera.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCameraWithHandlerThread();
        }
        else {
            textureView.setSurfaceTextureListener(textureListener);
        }

        cam_flicker = findViewById(R.id.camera_captureFlicker);
        progressBar = findViewById(R.id.camera_progressBar);
        progressText = findViewById(R.id.camera_progressText);
        cam_processing = findViewById(R.id.camera_captureProcess);
        flash_on = findViewById(R.id.btn_flash_on);
        flash_off = findViewById(R.id.btn_flash_off);

        cam_flicker.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.INVISIBLE);
        progressText.setVisibility(View.INVISIBLE);
        cam_processing.setVisibility(View.INVISIBLE);
        takePictureButton.setEnabled(true);
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        stopBackgroundThread();
        super.onPause();
    }

    public void openPostProcessActivity(String img_path, int jpg_h, int jpg_w, int start_y, float scale, boolean flash, int rotationDeg) {
        Intent intent = new Intent(this , PostProcess.class);
        intent.putExtra("img_path", img_path);
        intent.putExtra("jpeg_height", jpg_w);
        intent.putExtra("jpeg_width", jpg_h);
        intent.putExtra("starting_y", start_y);
        intent.putExtra("scaling_factor", (float) 1/scale);
        intent.putExtra("flash", flash);
        intent.putExtra("account", google_account);
        intent.putExtra("googleServiceHelper", (Parcelable) mDriveServiceHelper);
        intent.putExtra("dropboxAuthToken", dropbox_authToken);
        intent.putExtra("rotationDegree", rotationDeg);

        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

        startActivity(intent);
    }

    //=================
    public void toggleFlashOn(View view) {
        flashMode = true;
        flash_on.setVisibility(View.VISIBLE);
        flash_off.setVisibility(View.INVISIBLE);
    }

    public void toggleFlashOff(View view) {
        flashMode = false;
        flash_on.setVisibility(View.INVISIBLE);
        flash_off.setVisibility(View.VISIBLE);
    }

    //=================
    private void openCameraWithHandlerThread() {
        if (mThread == null) {
            mThread = new CameraHandlerThread();
        }

        synchronized (mThread) {
            mThread.openCamera_ht();
        }
    }
    private CameraHandlerThread mThread = null;
    private class CameraHandlerThread extends HandlerThread {
        Handler mHandler = null;

        CameraHandlerThread() {
            super("CameraHandlerThread");
            start();
            mHandler = new Handler(getLooper());
        }

        synchronized void notifyCameraOpened() {
            notify();
        }

        void openCamera_ht() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    openCamera();
                    notifyCameraOpened();
                }
            });
            try {
                wait();
            }
            catch (InterruptedException e) {
                Log.w("TESTING", "wait was interrupted");
            }
        }
    }
}



