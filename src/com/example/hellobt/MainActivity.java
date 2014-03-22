package com.example.hellobt;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class MainActivity extends FragmentActivity implements
		OnSeekBarChangeListener {

	private BtFragment mFrag = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		FragmentManager fm = getSupportFragmentManager();

		mFrag = (BtFragment) fm.findFragmentByTag("frag");

		if (mFrag == null) {
			mFrag = new BtFragment();
			fm.beginTransaction().add(mFrag, "frag").commit();
		}

		SeekBar sb = (SeekBar) findViewById(R.id.seekBar1);
		sb.setOnSeekBarChangeListener(this);

		sb = (SeekBar) findViewById(R.id.seekBar2);
		sb.setOnSeekBarChangeListener(this);

		MqttService.actionStart(getApplicationContext());
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		mFrag.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onProgressChanged(SeekBar seek, int progress, boolean arg2) {

	}

	@Override
	public void onStartTrackingTouch(SeekBar arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		int progress = seekBar.getProgress();

		if (seekBar.getId() == R.id.seekBar1) {
			mFrag.drive(progress);
		} else {
			mFrag.turn(progress);
		}
	}

}
