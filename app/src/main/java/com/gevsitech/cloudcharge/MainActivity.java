package com.gevsitech.cloudcharge;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;

import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import android.media.ImageReader;
import android.graphics.ImageFormat;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;


//用來讀取照片
import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private static final int STATE_CAPTURE_ONESHOT = 2;

    private TextureView mTextureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener;

    private CameraDevice mCarmerDevice;
    private CameraDevice.StateCallback mCarmerDeviceStateCallback;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private String mCameraId;

    private Button scanBtn;
    private FrameLayout previewFrame;
    //private Camera camera;

    private ImageScanner imageScanner;

    private boolean showingTheResult = false;
    private CaptureRequest.Builder previewRequestBuilder = null;
    private boolean barcodeScanning = true;
    private int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private int api;
    private ImageReader mImageReader;
    private int mSensorOrientation;
    private File mfile;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mfile = new File(this.getExternalCacheDir() , "pic.jpg");

        mTextureView = (TextureView) findViewById(R.id.textureView);
        mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                System.out.println("onSurfaceTextureAvailable");
                setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
                connect2Camera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        };

        //相機狀態回報
        mCarmerDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                mCarmerDevice = camera;
                Log.d("First", "mCarmerDevice Open");
                startPreview();
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                mCarmerDevice.close();
                mCarmerDevice = null;
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                mCarmerDevice.close();
                mCarmerDevice = null;
            }
        };

//        scanBtn = (Button) findViewById(R.id.ScanButton);
//        scanBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                lockFocus();
//            }
//        });
        // previewFrame = (FrameLayout) findViewById(R.id.cameraPreview);

    }

    @Override
    protected void onResume() {
        super.onResume();

        startHandleThread();

        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            connect2Camera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        closeHandleThread();
        super.onPause();

    }

    private void closeCamera() {
        if (mCarmerDevice != null) {
            mCarmerDevice.close();
            mCarmerDevice = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setupCamera(int Width, int Height) {
        CameraManager mCameraManger = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String CameraId : mCameraManger.getCameraIdList()) {
                CameraCharacteristics C = mCameraManger.getCameraCharacteristics(CameraId);
//                StreamConfigurationMap map = C.get(
//                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//                //map.getOutputFormats();
//                int[] A = map.getOutputFormats();
//                for(int i = 0 ; i< A.length ; i++){
//                    System.out.println( A[i] );
//                }

                if (C.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }


//YUV_420_888
                mImageReader = ImageReader.newInstance(Width,Height, ImageFormat.JPEG,1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);

                mCameraId = CameraId;
                Log.d("setupCamera", mCameraId);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startHandleThread() {
        mHandlerThread = new HandlerThread("ThreadOne");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    private void closeHandleThread() {
        mHandlerThread.quitSafely();
        try {
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void connect2Camera() {
        CameraManager cameraManger = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.d("openCamera:", mCameraId);
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            try {
                cameraManger.openCamera(mCameraId, mCarmerDeviceStateCallback, mHandler);
            } catch (CameraAccessException e1) {
                e1.printStackTrace();
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Toast.makeText(this,
                            "Video app required access to camera", Toast.LENGTH_SHORT).show();
                }
                    requestPermissions(new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
                }, REQUEST_CAMERA_PERMISSION_RESULT);
            }
        }
    }

    private CameraCaptureSession mPreviewCaptureSession;


    private CaptureRequest captureRequest;


    private void startPreview(){
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mTextureView.getWidth(),mTextureView.getHeight());
        Surface perviewSurface = new Surface(surfaceTexture);
        try {
            mCaptureRequestBuilder = mCarmerDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(perviewSurface);
            //mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCarmerDevice.createCaptureSession(Arrays.asList(perviewSurface,mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Log.d("onConfigured", "onConfigured: startPreview");
                    mPreviewCaptureSession = session;
                    captureRequest  = mCaptureRequestBuilder.build();
                    try {
                       // mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        mPreviewCaptureSession.setRepeatingRequest(captureRequest,
                                mPreviewCaptureCallback, mHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            },mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not run without camera services", Toast.LENGTH_SHORT).show();
            }
            if(grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not have audio on record", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private void lockFocus() {
        mCaptureState = STATE_WAIT_LOCK;
        //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);

        try {
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback,
                    mHandler);
            // After this, the camera will go back to the normal state of preview.
            mCaptureState = STATE_PREVIEW;
            mPreviewCaptureSession.setRepeatingRequest(captureRequest, mPreviewCaptureCallback,
                    mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {

                    if (mImageReader != null) {
                        android.media.Image image = mImageReader.acquireLatestImage();
                        if (image != null) {
                            mHandler.post(new ImageSaver(image));
                        }
                    }
                }
            };

    /**
     * 保存jpeg到指定的文件夹下, 开启子线程执行保存操作
     */
    private class ImageSaver implements Runnable {
        /**
         * jpeg格式的文件
         */
        private final android.media.Image mImage;

        /**
         * 保存的文件
         */

        public ImageSaver(android.media.Image image) {
            mImage = image;
        }

        @Override
        public void run() {

            if(mImage!=null){
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                Bitmap bitmap2 = BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                int bmWidth  = bitmap2.getWidth();
                int bmHeight = bitmap2.getHeight();
                int[] newBitmap = new int[bmWidth * bmHeight];
                bitmap2.getPixels(newBitmap, 0, bmWidth, 0, 0, bmWidth, bmHeight);
                Image barcode = new Image(bmWidth, bmHeight, "RGB4");
                barcode.setData(newBitmap);

                //get camera ready
                imageScanner = new ImageScanner();
                imageScanner.setConfig(Symbol.QRCODE, Config.ENABLE, 1); //Only QRCODE is enable
                imageScanner.setConfig(64  , Config.X_DENSITY, 3);
                imageScanner.setConfig(64 , Config.Y_DENSITY, 3);

//                net.sourceforge.zbar.Image barcodeImg = new net.sourceforge.zbar.Image(mTextureView.getWidth(), mTextureView.getHeight(), "Y800");
//                barcodeImg.setData(bytes);
//                barcodeImg.setFormat("Y800");
                /*******處理照片QR CODE位置*******/
                int result = imageScanner.scanImage(barcode.convert("Y800"));
                System.out.println("處理照片" + result);
                //success in scanning; show the result
                if (result != 0) {
                    SymbolSet syms = imageScanner.getResults();
                    for (Symbol sym : syms) {
                        String resultMessage = sym.getData().trim();
                        Log.d("resultMessage", resultMessage);
                        showingTheResult = true;
                        break;
                    }
                    try {
                        mPreviewCaptureSession.stopRepeating();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                    showAlertDialog();
                }

            }
            mImage.close();
        }
    }

    private void showAlertDialog(){
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("確認")
                .setMessage("確認後將開始進行")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(getApplicationContext(), "開關已開通", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Cancle", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(getApplicationContext(), "下次再來", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();

    }
    private int mCaptureState;
    private int timedelay;
    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {

                private void process(CaptureResult captureResult) {
                    switch (mCaptureState) {
                        case STATE_PREVIEW:
                            timedelay++;
                            System.out.println(timedelay);
                            if(timedelay >=50)mCaptureState = STATE_CAPTURE_ONESHOT;
                            break;
                        case STATE_WAIT_LOCK:
                            mCaptureState = STATE_PREVIEW;
                            Log.d("mPreviewCapture", "startStillCaptureRequest()");
                            startStillCaptureRequest();
                            break;
                        case STATE_CAPTURE_ONESHOT:
                            Log.d("mPreviewCapture", "STATE_CAPTURE_ONESHOT");
                            mCaptureState = STATE_PREVIEW;
                            if (captureResult.get(CaptureResult.CONTROL_AF_STATE) == CaptureRequest.CONTROL_AF_STATE_PASSIVE_FOCUSED) {
                                    timedelay = 0 ;
                                    //捕捉图片
                                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                                    mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
                                try {
                                        mPreviewCaptureSession.stopRepeating();
                                        mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                                                    @Override
                                                    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                                        super.onCaptureCompleted(session, request, result);
                                                        System.out.println("onCaptureCompleted");
                                                        System.out.println("調用unlockFocus()");

                                                        unlockFocus();
                                                    }
                                        }, mHandler);
                                    } catch (CameraAccessException e) {
                                        e.printStackTrace();
                                    }
                            }
                            break;
                    }
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    process(result);
                }
            };

    private void startStillCaptureRequest() {
        try {
                mCaptureRequestBuilder = mCarmerDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
                System.out.println(mImageReader.getSurface());

                CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                            Log.d("startStillCapture", "onCaptureStarted");
                            unlockFocus();
                        }
                    };
                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}