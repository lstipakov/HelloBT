package com.example.hellobt;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;

public class BtFragment extends Fragment {
	BluetoothAdapter mBluetoothAdapter;

	public static class DevicesListDialog extends DialogFragment {
		public static DevicesListDialog newInstance(
				Set<BluetoothDevice> pDevices) {
			DevicesListDialog dlg = new DevicesListDialog();
			Bundle bundle = new Bundle();
			String[] names = new String[pDevices.size()];
			String[] addresses = new String[pDevices.size()];
			int i = 0;
			for (BluetoothDevice d : pDevices) {
				names[i] = d.getName();
				addresses[i++] = d.getAddress();

			}
			bundle.putStringArray("devices", names);
			bundle.putStringArray("addresses", addresses);
			dlg.setArguments(bundle);
			return dlg;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {

			String[] names = getArguments().getStringArray("devices");
			final String[] addresses = getArguments().getStringArray(
					"addresses");
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

			builder.setTitle("Choose a device").setItems(names,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							FragmentManager fm = getFragmentManager();
							BtFragment frag = (BtFragment) fm
									.findFragmentByTag("frag");
							frag.startConnection(addresses[which]);

						}
					});
			return builder.create();
		}
	}

	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			// Use a temporary object that is later assigned to mmSocket,
			// because mmSocket is final
			BluetoothSocket tmp = null;
			mmDevice = device;

			// Get a BluetoothSocket to connect with the given BluetoothDevice
			try {
				// MY_UUID is the app's UUID string, also used by the server
				// code
				tmp = device.createRfcommSocketToServiceRecord(UUID
						.fromString("00001101-0000-1000-8000-00805F9B34FB"));
			} catch (IOException e) {
			}
			mmSocket = tmp;
		}

		@Override
		public void run() {
			// Cancel discovery because it will slow down the connection
			mBluetoothAdapter.cancelDiscovery();

			try {
				// Connect the device through the socket. This will block
				// until it succeeds or throws an exception
				mmSocket.connect();
			} catch (IOException connectException) {
				// Unable to connect; close the socket and get out
				try {
					mmSocket.close();
				} catch (IOException closeException) {
				}
				return;
			}

			// Do work to manage the connection (in a separate thread)
			// manageConnectedSocket(mmSocket);

			try {
				mOS = mmSocket.getOutputStream();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		OutputStream mOS;

		/** Will cancel an in-progress connection, and close the socket */
		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
			}
		}
	}

	private final static int REQUEST_ENABLE_BT = 100;

	@Override
	public void onCreate(Bundle pState) {
		super.onCreate(pState);

		BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
		if (!ad.isEnabled()) {
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		} else {
			promptDevices();
		}

		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
				new BroadcastReceiver() {

					@Override
					public void onReceive(Context context, Intent intent) {
						String msg = intent.getStringExtra("msg");
						if (msg.equals("u")) {
							BtFragment.this.drive(180);
						} else if (msg.equals("s")) {
							BtFragment.this.drive(128);
						} else if (msg.equals("r")) {
							BtFragment.this.turn(180);
						} else if (msg.equals("l")) {
							BtFragment.this.turn(60);
						} else if (msg.equals("d")) {
							BtFragment.this.drive(60);
						}
					}
				}, new IntentFilter("mqtt"));
	}

	protected void startConnection(String string) {
		BluetoothDevice d = BtFragment.this.mBluetoothAdapter
				.getRemoteDevice(string);
		mTh = new ConnectThread(d);
		mTh.start();
	}

	ConnectThread mTh;

	void promptDevices() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
		DialogFragment dialog = DevicesListDialog.newInstance(devices);
		dialog.show(getActivity().getSupportFragmentManager(),
				"DevicesListDialog");
	}

	boolean pendingPromptDialogOnResume = false;

	@Override
	public void onResume() {
		super.onResume();

		if (pendingPromptDialogOnResume) {
			promptDevices();
			pendingPromptDialogOnResume = false;
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			pendingPromptDialogOnResume = true;
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setRetainInstance(true);
	}

	public void send(int progress) {
		try {
			if ((mTh != null) && (mTh.mOS != null)) {
				mTh.mOS.write(progress);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void drive(int progress) {
		send((progress >> 1) & ~0x80);
	}

	public void turn(int progress) {
		send((progress >> 1) | 0x80);
	}
}
