package com.example.hellobt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends FragmentActivity {

    private SurfaceView preview=null;
    private SurfaceHolder previewHolder=null;
    private Camera camera=null;
    private boolean inPreview=false;
    private boolean cameraConfigured=false;

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// ignore orientation/keyboard change
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onResume() {
		super.onResume();

        Log.d(TAG, "onResume");

        camera=Camera.open();
        startPreview();
    }

	@Override
	public void onPause() {
        if (inPreview) {
            camera.stopPreview();
        }

        camera.release();
        camera=null;
        inPreview=false;

        super.onPause();
	}

	Handler poster;

	int REQUEST_ENABLE_BT = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
		if (!ad.isEnabled()) {
			startActivityForResult(new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
		} else {
			startService(new Intent(this, BtService.class));
		}

        startService(new Intent(this, MqttService.class));

        setContentView(R.layout.activity_main);

        preview=(SurfaceView)findViewById(R.id.preview);
        previewHolder=preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

    private static final String TAG = "MainActivity";

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			startService(new Intent(this, BtService.class));
		}
	}

    private Camera.Size getBestPreviewSize(int width, int height,
                                           Camera.Parameters parameters) {
        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        Collections.sort(sizes, new Comparator<Camera.Size>() {
            public int compare(final Camera.Size a, final Camera.Size b) {
                return a.width * a.height - b.width * b.height;
            }
        });

        if (sizes.size() > 2) {
            return sizes.get(2);
        } else {
            return sizes.get(0);
        }
    }

    private void initPreview(int width, int height) {
        if (camera!=null && previewHolder.getSurface()!=null) {
            try {
                camera.setPreviewDisplay(previewHolder);
            }
            catch (Throwable t) {
                Log.e("PreviewDemo-surfaceCallback",
                        "Exception in setPreviewDisplay()", t);
                Toast
                        .makeText(MainActivity.this, t.getMessage(), Toast.LENGTH_LONG)
                        .show();
            }

            if (!cameraConfigured) {
                Camera.Parameters parameters=camera.getParameters();
                Camera.Size size=getBestPreviewSize(width, height,
                        parameters);

                if (size!=null) {
                    parameters.setPreviewSize(size.width, size.height);
                    camera.setParameters(parameters);
                    cameraConfigured=true;
                }
            }
        }
    }

    byte[] buf = new byte[1024 * 1024];

    class SenderTask extends AsyncTask<Void, Void, Void> {

        byte[] getJpegBytes() {
            Camera.Size previewSize = camera.getParameters().getPreviewSize();
            YuvImage yuvimage=new YuvImage(buf, ImageFormat.NV21, previewSize.width,
                    previewSize.height, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 80, baos);
            return baos.toByteArray();
        }

        void send(byte[] bytes) {
            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(params, 5000);
            HttpConnectionParams.setSoTimeout(params, 10000);
            HttpClient client = new DefaultHttpClient(params);
            HttpPost httppost = new HttpPost("http://stipakov.fi/hello/");
            try {
                httppost.setEntity(new ByteArrayEntity(bytes));
                client.execute(httppost);
            } catch (ClientProtocolException e) {
                Log.e(TAG, e.toString());
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }

            Log.d(TAG, "sent");
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (!inPreview || camera == null) {
                Log.d(TAG, "not in preview");
                return null;
            }

            byte[] bytes = getJpegBytes();
            send(bytes);
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            if (camera != null) {
                camera.addCallbackBuffer(buf);
            } else {
                Log.d(TAG, "not in preview");
            }
        }
    }

    private void startPreview() {
        if (cameraConfigured && camera!=null) {

            camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                public void onPreviewFrame(byte[] imageData, Camera arg1) {
                    Log.d(TAG, "onPreviewFrame");

                    new SenderTask().execute();
                }
            });

            camera.addCallbackBuffer(buf);

            camera.startPreview();
            inPreview=true;
        }
    }

    SurfaceHolder.Callback surfaceCallback=new SurfaceHolder.Callback() {
        public void surfaceCreated(SurfaceHolder holder) {
            // no-op -- wait until surfaceChanged()
        }

        public void surfaceChanged(SurfaceHolder holder,
                                   int format, int width,
                                   int height) {
            initPreview(width, height);
            startPreview();
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // no-op
        }
    };
}
