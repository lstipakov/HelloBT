package com.example.hellobt;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

public class MainActivity extends FragmentActivity {

	private BtFragment mFrag = null;

	private Camera camera;

	SurfaceView preview;
	SurfaceHolder previewHolder;
	boolean inPreview;

	boolean cameraConfigured;

	Handler handler;

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// ignore orientation/keyboard change
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onResume() {
		super.onResume();

		camera = Camera.open();
		startPreview();
	}

	@Override
	public void onPause() {
		if (inPreview) {
			camera.stopPreview();
		}

		camera.release();
		camera = null;
		inPreview = false;

		super.onPause();
	}

	private Camera.Size getBestPreviewSize(int width, int height,
			Camera.Parameters parameters) {
		Camera.Size result = null;

		for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
			if (size.width <= width && size.height <= height) {
				if (result == null) {
					result = size;
				} else {
					int resultArea = result.width * result.height;
					int newArea = size.width * size.height;

					if (newArea > resultArea) {
						result = size;
					}
				}
			}
		}

		return (result);
	}

	Handler poster;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.activity_main);

		FragmentManager fm = getSupportFragmentManager();

		mFrag = (BtFragment) fm.findFragmentByTag("frag");

		if (mFrag == null) {
			mFrag = new BtFragment();
			fm.beginTransaction().add(mFrag, "frag").commit();
		}

		MqttService.actionStart(getApplicationContext());

		preview = (SurfaceView) findViewById(R.id.surface_view);
		previewHolder = preview.getHolder();
		previewHolder.addCallback(surfaceCallback);
		previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		handler = new Handler();

		handler.postDelayed(shooter, 10000);

		HandlerThread handlerThread = new HandlerThread("poster");
		handlerThread.start();
		poster = new Handler(handlerThread.getLooper());
	}

	void post(final byte[] data) {
		poster.post(new Runnable() {

			@Override
			public void run() {
				// Create a new HttpClient and Post Header
				HttpClient httpclient = new DefaultHttpClient();
				HttpPost httppost = new HttpPost(
						"http://stipakov.fi/s/upload.py");

				try {
					httppost.setEntity(new ByteArrayEntity(data));

					// Execute HTTP Post Request
					HttpResponse response = httpclient.execute(httppost);

				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
				} catch (IOException e) {
					Log.e("MainActivity", e.toString());
				}

			}
		});
	}

	PictureCallback pic = new PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			Toast.makeText(MainActivity.this, "pic taken", Toast.LENGTH_SHORT)
					.show();

			post(data);

			camera.startPreview();

			handler.postDelayed(shooter, 10000);
		}
	};

	Runnable shooter = new Runnable() {
		@Override
		public void run() {

			if (inPreview && cameraConfigured)
				camera.takePicture(null, null, pic);

		}
	};

	private void initPreview(int width, int height) {
		if (camera != null && previewHolder.getSurface() != null) {
			try {
				camera.setPreviewDisplay(previewHolder);
			} catch (Throwable t) {
				Log.e("PreviewDemo-surfaceCallback",
						"Exception in setPreviewDisplay()", t);

			}

			if (!cameraConfigured) {
				Camera.Parameters parameters = camera.getParameters();
				parameters.setJpegQuality(50);
				parameters.setPictureSize(512, 384);

				Camera.Size size = getBestPreviewSize(width, height, parameters);

				if (size != null) {
					parameters.setPreviewSize(size.width, size.height);
					camera.setParameters(parameters);
					cameraConfigured = true;
				}
			}
		}
	}

	private void startPreview() {
		if (cameraConfigured && camera != null) {
			camera.startPreview();
			inPreview = true;
		}
	}

	SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			// no-op -- wait until surfaceChanged()
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			initPreview(width, height);
			startPreview();
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			// no-op
		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		mFrag.onActivityResult(requestCode, resultCode, data);
	}
}
