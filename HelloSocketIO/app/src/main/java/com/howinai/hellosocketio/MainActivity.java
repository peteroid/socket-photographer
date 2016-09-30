package com.howinai.hellosocketio;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.Preference;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.loader.StreamLoader;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {

    Socket socket;
    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final String SOCKET_SERVER = "http://1218a097.ngrok.io";
//    static final String SOCKET_SERVER = "http://172.31.113.115:8080";

    String deviceName = String.format("%s-%s", Build.MODEL, Build.SERIAL).replaceAll(" ", "_");

    TextView textCount, textStatus, textTimestamp;
    Button btnTimeUp, btnTimeDown;

    private long timeMillisDelay = 0;
    private final static String PREF_TIME_DELAY = "pref.time.delay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textCount = (TextView) findViewById(R.id.text_count);
        textStatus = (TextView) findViewById(R.id.text_status);
        textTimestamp = (TextView) findViewById(R.id.text_timestamp);
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textTimestamp.setText(String.valueOf(getTunedCurrentTimeMillis()));
                    }
                });
            }
        }, 0, 100);

        btnTimeUp = (Button) findViewById(R.id.btn_time_up);
        btnTimeDown = (Button) findViewById(R.id.btn_time_down);

        btnTimeDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeMillisDelay -= 300;
            }
        });

        btnTimeUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeMillisDelay += 300;
            }
        });

        Ion.getDefault(this)
                .configure()
                .setLogging("socket-ion", Log.DEBUG);

        try {
            socket = IO.socket(MainActivity.SOCKET_SERVER);

            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textStatus.setText("Connected");
                        }
                    });
                    socket.emit("device", deviceName);
                    socket.on("shoot", new Emitter.Listener() {
                        @Override
                        public void call(Object... args) {
                            try {
                                JSONObject obj = (JSONObject) args[0];
                                final String shootTimeStr = obj.getString("timestamp");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        textStatus.setText(shootTimeStr);
                                    }
                                });
                                Long shootTime = Long.valueOf(obj.getString("timestamp")) + 8000 - timeMillisDelay;
                                Log.d("broadcast", "shoot it at " + String.valueOf(shootTime));
                                dispatchTakePictureIntent(new Date(shootTime));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            });

            socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.d("Socket", "Connect Error");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textStatus.setText("Error");
                        }
                    });
                }
            });

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Button shootButton = (Button) findViewById(R.id.btn_shoot);
        if (shootButton != null) {
            shootButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dispatchTakePictureIntent(new Date());
                }
            });
        }

        Button shootAllButton = (Button) findViewById(R.id.btn_shoot_all);
        if (shootAllButton != null) {
            shootAllButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    socket.emit("shoot");
                }
            });
        }
    }

    private long getTunedCurrentTimeMillis() {
        return System.currentTimeMillis() + timeMillisDelay;
    }

    @Override
    protected void onPause() {
        super.onPause();
        socket.disconnect();
        this.getPreferences(MODE_PRIVATE)
                .edit()
                .putLong(MainActivity.PREF_TIME_DELAY, timeMillisDelay)
                .apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        socket.connect();
        this.timeMillisDelay = this.getPreferences(MODE_PRIVATE)
                .getLong(MainActivity.PREF_TIME_DELAY, 0);
    }

    private void dispatchTakePictureIntent(final Date shootDate) {

        final Camera mCamera;
        try {
            mCamera = Camera.open(0);
        } catch (RuntimeException e) {
            return;
        }
        List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();
        Camera.Size biggestSize = sizes.get(0);

        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(biggestSize.width, biggestSize.height);
        mCamera.setParameters(parameters);

        final CamPreview camPreview = new CamPreview(this, mCamera);
        camPreview.setSurfaceTextureListener(camPreview);

        // Connect the preview object to a FrameLayout in your UI
        // You'll have to create a FrameLayout object in your UI to place this preview in
        final FrameLayout preview = (FrameLayout) findViewById(R.id.cameraView);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                preview.addView(camPreview);
            }
        });
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d("Timer", "run " + String.valueOf(getTunedCurrentTimeMillis()));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("runOnUi", "shoot now " + String.valueOf(getTunedCurrentTimeMillis()));
                        // Attach a callback for preview
                        mCamera.setPreviewCallback(new CamCallback(MainActivity.this));
                        timer.cancel();
                    }
                });
            }
        }, shootDate);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textStatus.setText(String.valueOf(getTunedCurrentTimeMillis()));
                        textCount.setText(String.valueOf((int) Math.floor(((shootDate.getTime() - System.currentTimeMillis()) / 1000.0))));
                    }
                });
            }
        }, 0, 1000);
    }
}
