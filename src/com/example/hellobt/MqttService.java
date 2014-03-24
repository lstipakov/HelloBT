package com.example.hellobt;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Created by stipa on 24.3.2014.
 */
public class MqttService extends Service implements MqttCallback {
    private final static String TAG = "MqttService";
    private final static String SERVER = "tcp://stipakov.fi:1883";
    private final int RECONNECT_INTERVAL = 5000;
    private final int KEEP_ALIVE_INTERVAL = 30;

    private MqttConnectOptions opts;
    private IMqttClient client;
    private Handler connHandler;
    private String clientId;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate");

        opts = new MqttConnectOptions();

        try {
            client = new MqttClient(SERVER, MqttClient.generateClientId(), new MemoryPersistence());
        } catch (MqttException e) {
            Log.e(TAG, e.toString());
            throw new RuntimeException(e);
        }

        HandlerThread thread = new HandlerThread("MqttThread");
        thread.start();

        connHandler = new Handler(thread.getLooper());
        connHandler.post(connect);
    }

    private Runnable connect = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Connecting");
            try {
                MqttConnectOptions opts = new MqttConnectOptions();
                opts.setKeepAliveInterval(KEEP_ALIVE_INTERVAL);
                client.connect(opts);
                client.subscribe("r", 0);
                client.setCallback(MqttService.this);
                Log.d(TAG, "Connected");
            } catch (MqttException e) {
                Log.e(TAG, e.toString());
            }
        }
    };

    @Override
    public void connectionLost(Throwable cause) {
        Log.e(TAG, "connection lost: " + cause.toString());

        connHandler.removeCallbacks(connect);
        connHandler.postDelayed(connect, RECONNECT_INTERVAL);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.d(TAG, "message arrived: " + message.toString());

        Intent intent = new Intent("mqtt");
        intent.putExtra("msg", message.toString());
        LocalBroadcastManager.getInstance(MqttService.this).sendBroadcast(intent);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }
}
