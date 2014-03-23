package com.example.hellobt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

public class BtService extends Service {

	private final static String TAG = "BtService";

	private ConnectThread connectThread;
	private ConnectedThread connectedThread;

	boolean connected = false;
	private final String BT_NAME = "linvor";

	private class ConnectThread extends Thread {
		private final BluetoothSocket socket;
		private final BluetoothDevice device;

		public ConnectThread(BluetoothDevice dev) {
			// Use a temporary object that is later assigned to mmSocket,
			// because mmSocket is final
			BluetoothSocket tmp = null;
			device = dev;

			// Get a BluetoothSocket to connect with the given BluetoothDevice
			try {
				// MY_UUID is the app's UUID string, also used by the server
				// code
				tmp = device.createRfcommSocketToServiceRecord(UUID
						.fromString("00001101-0000-1000-8000-00805F9B34FB"));
			} catch (IOException e) {
			}
			socket = tmp;
		}

		@Override
		public void run() {
			// Cancel discovery because it will slow down the connection
			BluetoothAdapter.getDefaultAdapter().cancelDiscovery();

			try {
				// Connect the device through the socket. This will block
				// until it succeeds or throws an exception
				socket.connect();
			} catch (IOException e) {
				Log.e(TAG, e.toString());
				// Unable to connect; close the socket and get out
				try {
					socket.close();
				} catch (IOException closeException) {
				}

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }

                startConnection();

				return;
			}

			// Do work to manage the connection (in a separate thread)
			connectedThread = new ConnectedThread(socket);
			connectedThread.start();
		}
	}

	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the input and output streams, using temp objects because
			// member streams are final
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;

            connected = true;
        }

		@Override
		public void run() {
			byte[] buffer = new byte[1024]; // buffer store for the stream

			// Keep listening to the InputStream until an exception occurs
			while (true) {
				try {
					// Read from the InputStream
					mmInStream.read(buffer);

				} catch (IOException e) {
					Log.d(TAG, "bt disconnected");
					connected = false;
					break;
				}
			}

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

            startConnection();
		}

		/* Call this from the main activity to send data to the remote device */
		public void write(int b) {
			try {
				mmOutStream.write(b);
			} catch (IOException e) {
				Log.d(TAG, "bt disconnected");
				connected = false;

                startConnection();
			}
		}
	}

    void startConnection() {
        Set<BluetoothDevice> devices = BluetoothAdapter.getDefaultAdapter()
                .getBondedDevices();
        for (BluetoothDevice d : devices) {
            if (d.getName().equals(BT_NAME)) {

                connectThread = new ConnectThread(d);
                connectThread.start();

                break;
            }
        }
    }

	@Override
	public void onCreate() {
		super.onCreate();

        startConnection();

		LocalBroadcastManager.getInstance(this).registerReceiver(
				new BroadcastReceiver() {

					@Override
					public void onReceive(Context context, Intent intent) {
						String msg = intent.getStringExtra("msg");

						Log.d(TAG, "received " + msg);

						if (msg.equals("u")) {
							drive(180);
						} else if (msg.equals("s")) {
							drive(128);
						} else if (msg.equals("r")) {
							turn(180);
						} else if (msg.equals("l")) {
							turn(60);
						} else if (msg.equals("d")) {
							drive(60);
						}
					}
				}, new IntentFilter("mqtt"));
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	public void drive(int progress) {
		if (!connected) {
			Log.d(TAG, "BT not connected");
			return;
		}
		connectedThread.write((progress >> 1) & ~0x80);
	}

	public void turn(int progress) {
		if (!connected) {
			Log.d(TAG, "BT not connected");
			return;
		}
		connectedThread.write((progress >> 1) | 0x80);
	}
}
