package com.example.admin.gammy;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import android.os.AsyncTask;


public class MainActivity extends AppCompatActivity implements Credentials,
        Session.SessionListener,
        PublisherKit.PublisherListener,
        SubscriberKit.SubscriberListener,
        SubscriberKit.VideoListener {

    private static final String logTag = "MainActivity";

    Session mSession;
    Publisher mPublisher;
    Subscriber mSubscriber;

    String session_ID = null;
    String session_Token = null;

    Button  connectBtn,
            disconnectBtn,
            publishBtn,
            unpublishBtn,
            unsubscribeBtn,
            cameraToggleBtn;

    RelativeLayout  subscriberRl,
                    publisherRl;

    final private int request_code_ask_permissions = 123;
    int hasCameraPermission, hasInternetPermission, hasAudioPermission, hasReadStoragePermission, hasWriteStoragePermission = 0;

    public class WebServiceTask extends AsyncTask<String, String, String> implements Credentials{

        private Context mContext;

        public WebServiceTask(Context context) {
            this.mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showMessage("Fetching session details");
        }

        @Override
        protected String doInBackground(String... args) {
            String r = "";
            String fetchedJSON = connectAndFetch();
            System.out.println("=======JSON from Web Service=======" + fetchedJSON);
            if (fetchedJSON != null) {
                try {
                    JSONObject jObject = new JSONObject(fetchedJSON);
                    session_ID = jObject.getString("SessionID");
                    session_Token = jObject.getString("Token");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return r;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(logTag, "Session ID "+session_ID);
            Log.d(logTag, "Token "+session_Token);
            showMessage("Connecting to session");
            mSession = new Session(MainActivity.this, API_KEY, session_ID);
            mSession.setSessionListener(MainActivity.this);
            mSession.connect(session_Token);
        }

        public String connectAndFetch() {
            String stringJSON = null;
            int responseCode;
            try {
                URL webServiceURL = new URL("http://webservice.cloud.cms500.com/get/SessionAndToken");
                URLConnection urlConn = webServiceURL.openConnection();

                HttpURLConnection httpConn = (HttpURLConnection) urlConn;

                httpConn.setAllowUserInteraction(false);
                httpConn.setInstanceFollowRedirects(true);
                httpConn.setRequestMethod("GET");
                httpConn.setRequestProperty("Content-Type", "application/json");
                httpConn.setRequestProperty("Accept", "application/json");

                httpConn.connect();

                responseCode = httpConn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(httpConn.getInputStream()));
                    String inputLine;
                    StringBuffer response = new StringBuffer();
                    while ((inputLine = input.readLine()) != null) {
                        response.append(inputLine);
                    }
                    input.close();
                    stringJSON = response.toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return stringJSON;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(logTag, "Checking for permissions");
        checkForPermissions();

        setContentView(R.layout.activity_main);

        connectBtn      = (Button) findViewById(R.id.connectBtn);
        disconnectBtn   = (Button) findViewById(R.id.disconnectBtn);
        publishBtn      = (Button) findViewById(R.id.publishBtn);
        unpublishBtn    = (Button) findViewById(R.id.unpublishBtn);
        unsubscribeBtn  = (Button) findViewById(R.id.unsubscribeBtn);
        cameraToggleBtn = (Button) findViewById(R.id.cameraToggleBtn);

        subscriberRl = (RelativeLayout) findViewById(R.id.subscriberRl);
        publisherRl  = (RelativeLayout) findViewById(R.id.publisherRl);


        connectBtn.setOnClickListener(clickListener);
        disconnectBtn.setOnClickListener(clickListener);
        publishBtn.setOnClickListener(clickListener);
        unpublishBtn.setOnClickListener(clickListener);
        unsubscribeBtn.setOnClickListener(clickListener);
        cameraToggleBtn.setOnClickListener(clickListener);
    }

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.connectBtn:
                    Log.d(logTag, "Connect button pressed");

                    enableButton(connectBtn,false);

                    connectToSession();
                    showMessage("Connecting");

                    break;

                case R.id.disconnectBtn:
                    Log.d(logTag, "Disconnect button pressed");

                    enableButton(disconnectBtn,false);
                    enableButton(publishBtn,false);

                    disconnectFromSession();
                    showMessage("Disconnecting");

                    break;

                case R.id.publishBtn:
                    Log.d(logTag, "Publish button pressed");

                    enableButton(publishBtn, false);
                    enableButton(connectBtn, false);
                    enableButton(disconnectBtn, false);

                    publishToSession();

                    break;

                case R.id.unpublishBtn:
                    Log.d(logTag, "Unpublish button pressed");

                    enableButton(unpublishBtn, false);

                    unpublishInSession();

                    break;

                case R.id.unsubscribeBtn:
                    unsubscribeToStream();
                    Log.d(logTag, "Unsubscribe button pressed");

                    break;

                case R.id.cameraToggleBtn:
                    Log.d(logTag, "Camera toggled");
                    mPublisher.cycleCamera();

                    break;
            }
        }
    };

    private void connectToSession() {

        if (mSession == null) {
            Log.d(logTag, "Connecting for the first time by calling Web Service");
            new WebServiceTask(MainActivity.this).execute();
        }
        else {
            Log.d(logTag, "Reconnecting Again");
            showMessage("Reconnecting");
            mSession.setSessionListener(this);
            mSession.connect(session_Token);
            Log.d(logTag, "Session ID "+session_ID);
            Log.d(logTag, "Token "+session_Token);
        }
    }

    private void disconnectFromSession() {

        Log.d(logTag, "Disconnecting fom session");
        mSession.disconnect();
    }

    private void publishToSession () {

        Log.d(logTag, "Publishing in session");

        if (mPublisher == null) {
            mPublisher = new Publisher(MainActivity.this, "Publisher", true, true);
            mPublisher.setPublisherListener(this);
        }

        mSession.publish(mPublisher);
    }

    private void unpublishInSession() {

        Log.d(logTag, "Stopping publish in session");

        mSession.unpublish(mPublisher);
        removePublisherView();
    }

    private void subscribetToStream(Stream stream) {

        Log.d(logTag, "Starting to subscribe in session");

        if (mSubscriber == null) {
            mSubscriber = new Subscriber(MainActivity.this, stream);
            mSubscriber.setVideoListener(this);
        }

        mSession.subscribe(mSubscriber);
        enableButton(unsubscribeBtn, true);
    }

    private void unsubscribeToStream() {

        Log.d(logTag, "Stopping to subscribe in session");

        mSession.unsubscribe(mSubscriber);
        removeSubscriberView();
        enableButton(unsubscribeBtn, false);
    }

    @Override
    public void onConnected(Session session) {

        showMessage("Connected to session");
        Log.d(logTag, "Connected to session");

        enableButton(disconnectBtn,true);
        enableButton(publishBtn,true);
    }

    @Override
    public void onDisconnected(Session session) {

        showMessage("Disconnected from session");
        Log.d(logTag, "Disconnected from session");

        showDialog(this);
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        showMessage("Another client is publishing");
        Log.d(logTag, "Another client is publishing");

        subscribetToStream(stream);
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        showMessage("Client has stopped publishing");
        Log.d(logTag, "Client has stopped publishing");

        unsubscribeToStream();
        enableButton(unsubscribeBtn, false);
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        showMessage("Error connecting !");
        Log.d(logTag, "Error connecting !");
        System.out.println(opentokError.toString());
    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {

        attachPublisherView(mPublisher);

        showMessage("Now publishing");
        Log.d(logTag, "Now publishing");

        enableButton(unpublishBtn, true);
        enableButton(cameraToggleBtn, true);
    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {

        showMessage("Stopped publishing");
        Log.d(logTag, "Stopped publishing");

        enableButton(connectBtn, false);
        enableButton(disconnectBtn, true);
        enableButton(publishBtn, true);
        enableButton(cameraToggleBtn, false);
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {
        showMessage("Error in publishing");
        Log.d(logTag, "Error in publishing");
    }

    @Override
    public void onConnected(SubscriberKit subscriberKit) {
        showMessage("Subscribing stream");
        Log.d(logTag, "Subscribing stream");
    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {
        showMessage("Not Subscribing");
        Log.d(logTag, "Not Subscribing");
    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {
        showMessage("Error in subscribing");
        Log.d(logTag, "Error in subscribing");
    }

    @Override
    public void onVideoDisabled(SubscriberKit subscriberKit, String s) {
        showMessage("Subscriber Has Disabled Video ");
    }

    @Override
    public void onVideoDisableWarning(SubscriberKit subscriberKit) {
        showMessage("Internet Speed Slow. Video Disabling Possibleo");
    }

    @Override
    public void onVideoDisableWarningLifted(SubscriberKit subscriberKit) {
        showMessage("Internet Speed Good. Video Disabling Lifted");
    }

    @Override
    public void onVideoEnabled(SubscriberKit subscriberKit, String s) {
        showMessage("Subscriber Has Enabled Video");
    }

    @Override
    public void onVideoDataReceived(SubscriberKit subscriberKit) {
        attachSubscriberView(mSubscriber);
    }

    private void checkForPermissions() {

        Log.d(logTag, "Inside permission check");

        hasCameraPermission       = checkSelfPermission(Manifest.permission.CAMERA);
        hasInternetPermission     = checkSelfPermission(Manifest.permission.INTERNET);
        hasAudioPermission        = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        hasReadStoragePermission  = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        hasWriteStoragePermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (hasCameraPermission == PackageManager.PERMISSION_GRANTED &&
                hasInternetPermission == PackageManager.PERMISSION_GRANTED &&
                    hasAudioPermission == PackageManager.PERMISSION_GRANTED &&
                        hasWriteStoragePermission == PackageManager.PERMISSION_GRANTED &&
                            hasReadStoragePermission == PackageManager.PERMISSION_GRANTED) {

            Log.d(logTag, "All permissions granted");
            showMessage("All permissions granted");
        }
        else
            grantPermissions();
    }

    private void grantPermissions() {
        if (hasCameraPermission != PackageManager.PERMISSION_GRANTED ||
                hasInternetPermission != PackageManager.PERMISSION_GRANTED ||
                    hasAudioPermission != PackageManager.PERMISSION_GRANTED ||
                        hasWriteStoragePermission != PackageManager.PERMISSION_GRANTED ||
                            hasReadStoragePermission != PackageManager.PERMISSION_GRANTED) {

            Log.d(logTag, "Granting permissions");

            requestPermissions(new String[] {   Manifest.permission.CAMERA,
                                                Manifest.permission.INTERNET,
                                                Manifest.permission.RECORD_AUDIO,
                                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    request_code_ask_permissions);

            hasCameraPermission       = checkSelfPermission(Manifest.permission.CAMERA);
            hasInternetPermission     = checkSelfPermission(Manifest.permission.INTERNET);
            hasAudioPermission        = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
            hasReadStoragePermission  = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            hasWriteStoragePermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);

            if (hasCameraPermission == PackageManager.PERMISSION_GRANTED &&
                    hasInternetPermission == PackageManager.PERMISSION_GRANTED &&
                    hasAudioPermission == PackageManager.PERMISSION_GRANTED &&
                    hasWriteStoragePermission == PackageManager.PERMISSION_GRANTED &&
                    hasReadStoragePermission == PackageManager.PERMISSION_GRANTED) {

                Log.d(logTag, "All permissions granted");
                showMessage("All permissions granted");
            }
        }
    }

    private void attachPublisherView(Publisher publisher) {

        mPublisher.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
        publisherRl.addView(mPublisher.getView());
        publisherRl.bringToFront();
    }

    private void attachSubscriberView(Subscriber subscriber) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);

        subscriberRl.removeView(mSubscriber.getView());
        subscriberRl.addView(mSubscriber.getView(), layoutParams);

        subscriber.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,BaseVideoRenderer.STYLE_VIDEO_FILL);
    }

    private void removePublisherView() {

        if (publisherRl.getChildAt(0) != null) {
            ViewGroup parent = (ViewGroup) publisherRl.getChildAt(0).getParent();
            if (parent != null) {
                parent.removeView(publisherRl.getChildAt(0));
            }
        }
    }

    private void removeSubscriberView() {

        if (subscriberRl.getChildAt(0) != null) {
            ViewGroup parent = (ViewGroup) subscriberRl.getChildAt(0).getParent();
            if (parent != null) {
                parent.removeView(subscriberRl.getChildAt(0));
            }
        }
    }

    public void showDialog(Activity activity) {

        final Dialog dialog = new Dialog(activity);

        dialog.setCancelable(false);
        dialog.setContentView(R.layout.alert_dialog);

        Button ok     = (Button) dialog.findViewById(R.id.ok_action);
        Button exit   = (Button) dialog.findViewById(R.id.exit_action);

        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToSession();
                dialog.dismiss();
            }
        });

        exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                finish();
                System.exit(0);
            }
        });

        dialog.show();
    }

    private void enableButton(View view, boolean state) {
        view.setEnabled(state);
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}