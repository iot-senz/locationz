package com.score.senz.ui;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.score.senz.ISenzService;
import com.score.senz.R;
import com.score.senz.db.SenzorsDbSource;
import com.score.senz.services.LocationAddressReceiver;
import com.score.senz.utils.ActivityUtils;
import com.score.senz.utils.NetworkUtil;
import com.score.senzc.enums.SenzTypeEnum;
import com.score.senzc.pojos.Senz;
import com.score.senzc.pojos.User;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Display sensor list/ Fragment
 *
 * @author erangaeb@gmail.com (eranga herath)
 */
public class SenzListFragment extends Fragment {

    private static final String TAG = SenzListFragment.class.getName();

    // list view components
    private ListView sensorListView;
    private ArrayList<Senz> senzList;
    private SenzListAdapter adapter;

    // empty view to display when no sensors available
    private ViewStub emptyView;

    // use custom font here
    private Typeface typeface;

    boolean isServiceBound = false;

    // service interface
    private ISenzService senzService = null;

    // service connection
    private ServiceConnection senzServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d("TAG", "Connected with senz service");
            senzService = ISenzService.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            senzService = null;
            Log.d("TAG", "Disconnected from senz service");
        }
    };

    // use to track response timeout
    private SenzCountDownTimer senzCountDownTimer;
    private boolean isResponseReceived;
    private Senz selectedSenz;

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.sensor_list_layout, container, false);

        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        typeface = Typeface.createFromAsset(getActivity().getAssets(), "fonts/vegur_2.otf");

        senzCountDownTimer = new SenzCountDownTimer(16000, 5000);

        initEmptyView();
        initSensorListView();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart() {
        super.onStart();

        // bind to senz service
        if (!isServiceBound) {
            Intent intent = new Intent();
            intent.setClassName("com.score.senz", "com.score.senz.services.RemoteSenzService");
            getActivity().bindService(intent, senzServiceConnection, Context.BIND_AUTO_CREATE);
            isServiceBound = true;
        }

        getActivity().registerReceiver(senzMessageReceiver, new IntentFilter("DATA"));
    }

    /**
     * {@inheritDoc}
     */
    public void onResume() {
        super.onResume();

        setUpActionBarTitle("#LocationZ");
        displaySensorList();
    }

    /**
     * {@inheritDoc}
     */
    public void onPause() {
        super.onPause();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {
        super.onStop();

        // Unbind from the service
        if (isServiceBound) {
            getActivity().unbindService(senzServiceConnection);
            isServiceBound = false;
        }

        getActivity().unregisterReceiver(senzMessageReceiver);
    }

    /**
     * Initialize UI components
     */
    private void initSensorListView() {
        sensorListView = (ListView) getActivity().findViewById(R.id.sensor_list_layout_sensor_list);

        // add header and footer for list
        View headerView = View.inflate(this.getActivity(), R.layout.list_footer, null);
        View footerView = View.inflate(this.getActivity(), R.layout.list_footer, null);
        sensorListView.addHeaderView(headerView);
        sensorListView.addFooterView(footerView);

        // set up click listener
        sensorListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position <= senzList.size()) {
                    Log.d(TAG, "onItemClick: click on sensor list item");
                    selectedSenz = senzList.get(position - 1);
                    handleListItmeClick(selectedSenz);
                }
            }
        });

        // set long click listener to unshare
        sensorListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int pos, long id) {
                Log.v(TAG, "Long clicked" + pos);

                if (pos > 0 && pos <= senzList.size()) {
                    Log.d(TAG, "Long click: click on sensor list item");
                    selectedSenz = senzList.get(pos - 1);
                    String message = "<font color=#000000>Are you sure you want to delete the senz of </font> <font color=#eada00>" + "<b>" + selectedSenz.getSender().getUsername() + "</b>" + "</font>";
                    displayDeleteMessageDialog(message, selectedSenz);
                }

                return true;
            }
        });
    }

    private void handleListItmeClick(Senz senz) {
        // location senz
        if (NetworkUtil.isAvailableNetwork(getActivity())) {
            ActivityUtils.showProgressDialog(getActivity(), "Please wait...");
            isResponseReceived = false;
            senzCountDownTimer.start();
        } else {
            Toast.makeText(getActivity(), "No network connection available", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Initialize empty view for list view
     * empty view need to be display when no sensors available
     */
    private void initEmptyView() {
        emptyView = (ViewStub) getActivity().findViewById(R.id.sensor_list_layout_empty_view);
        View inflatedEmptyView = emptyView.inflate();
        TextView emptyText = (TextView) inflatedEmptyView.findViewById(R.id.empty_text);
        emptyText.setText("No Senz available. Give your username to a friend and ask them to share senzs to you");
        emptyText.setTextColor(Color.parseColor("#eada00"));
        emptyText.setTypeface(typeface, Typeface.BOLD);
    }

    /**
     * Display sensor list
     * Basically setup list adapter if have items to display otherwise display empty view
     */
    private void displaySensorList() {
        // get sensors from db
        senzList = (ArrayList<Senz>) new SenzorsDbSource(this.getActivity()).getSenzes();

        // construct list adapter
        if (senzList.size() > 0) {
            adapter = new SenzListAdapter(SenzListFragment.this.getActivity(), senzList);
            adapter.notifyDataSetChanged();
            sensorListView.setAdapter(adapter);
        } else {
            adapter = new SenzListAdapter(SenzListFragment.this.getActivity(), senzList);
            sensorListView.setAdapter(adapter);
            //sensorListView.setEmptyView(emptyView);
        }
    }

    /**
     * Set action bar title according to currently selected sensor type
     * Set custom font to title
     *
     * @param title action bar title
     */
    private void setUpActionBarTitle(String title) {
        int titleId = getResources().getIdentifier("action_bar_title", "id", "android");
        TextView yourTextView = (TextView) (this.getActivity().findViewById(titleId));
        yourTextView.setTextColor(getResources().getColor(R.color.white));
        yourTextView.setTypeface(typeface);

        getActivity().getActionBar().setTitle(title);
    }

    /**
     * Send GET senz to service, actual senz sending task done by SenzService
     *
     * @param receiver senz receiver
     */
    private void getSenz(User receiver) {
        try {
            // create senz attributes
            HashMap<String, String> senzAttributes = new HashMap<>();
            senzAttributes.put("time", ((Long) (System.currentTimeMillis() / 1000)).toString());
            senzAttributes.put("lat", "lat");
            senzAttributes.put("lon", "lon");

            // new senz
            String id = "_ID";
            String signature = "_SIGNATURE";
            SenzTypeEnum senzType = SenzTypeEnum.GET;
            Senz senz = new Senz(id, signature, senzType, null, receiver, senzAttributes);

            senzService.send(senz);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private BroadcastReceiver senzMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Got message from Senz service");
            handleMessage(intent);
        }
    };

    /**
     * Handle broadcast message receives
     * Need to handle registration success failure here
     *
     * @param intent intent
     */
    private void handleMessage(Intent intent) {
        String action = intent.getAction();

        if (action.equalsIgnoreCase("DATA")) {
            Senz senz = intent.getExtras().getParcelable("SENZ");

            if (senz.getAttributes().containsKey("lat")) {
                // location response received
                ActivityUtils.cancelProgressDialog();
                isResponseReceived = true;
                senzCountDownTimer.cancel();

                double lat = Double.parseDouble(senz.getAttributes().get("lat"));
                double lan = Double.parseDouble(senz.getAttributes().get("lon"));
                LatLng latLng = new LatLng(lat, lan);

                // start location address receiver
                new LocationAddressReceiver(getActivity(), latLng, senz.getSender()).execute("PARAM");

                // start map activity
                Intent mapIntent = new Intent(getActivity(), SenzMapActivity.class);
                mapIntent.putExtra("extra", latLng);
                getActivity().startActivity(mapIntent);
                getActivity().overridePendingTransition(R.anim.right_in, R.anim.stay_in);
            }
        }
    }

    /**
     * Keep track with share response timeout
     */
    private class SenzCountDownTimer extends CountDownTimer {

        public SenzCountDownTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            // if response not received yet, resend share
            if (!isResponseReceived) {
                getSenz(selectedSenz.getSender());
                Log.d(TAG, "Response not received yet");
            }
        }

        @Override
        public void onFinish() {
            ActivityUtils.cancelProgressDialog();

            // display message dialog that we couldn't reach the user
            if (!isResponseReceived) {
                String message = "<font color=#000000>Seems we couldn't get the location of user </font> <font color=#eada00>" + "<b>" + selectedSenz.getSender().getUsername() + "</b>" + "</font> <font color=#000000> at this moment</font>";
                displayInformationMessageDialog("#Get Fail", message);
            }

        }
    }


    /**
     * Display message dialog when user request(click) to delete invoice
     *
     * @param message message to be display
     */
    public void displayInformationMessageDialog(String title, String message) {
        final Dialog dialog = new Dialog(getActivity());

        //set layout for dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.information_message_dialog);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(true);

        // set dialog texts
        TextView messageHeaderTextView = (TextView) dialog.findViewById(R.id.information_message_dialog_layout_message_header_text);
        TextView messageTextView = (TextView) dialog.findViewById(R.id.information_message_dialog_layout_message_text);
        messageHeaderTextView.setText(title);
        messageTextView.setText(Html.fromHtml(message));

        // set custom font
        messageHeaderTextView.setTypeface(typeface);
        messageTextView.setTypeface(typeface);

        //set ok button
        Button okButton = (Button) dialog.findViewById(R.id.information_message_dialog_layout_ok_button);
        okButton.setTypeface(typeface);
        okButton.setTypeface(null, Typeface.BOLD);
        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.cancel();
            }
        });

        dialog.show();
    }

    /**
     * Display message dialog when user request(click) to delete invoice
     *
     * @param message message to be display
     */
    public void displayDeleteMessageDialog(String message, final Senz senz) {
        final Dialog dialog = new Dialog(getActivity());

        //set layout for dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.share_confirm_message_dialog);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(true);

        // set dialog texts
        TextView messageHeaderTextView = (TextView) dialog.findViewById(R.id.information_message_dialog_layout_message_header_text);
        TextView messageTextView = (TextView) dialog.findViewById(R.id.information_message_dialog_layout_message_text);
        messageHeaderTextView.setText("#Delete senz");
        messageTextView.setText(Html.fromHtml(message));

        // set custom font
        messageHeaderTextView.setTypeface(typeface);
        messageTextView.setTypeface(typeface);

        //set ok button
        Button okButton = (Button) dialog.findViewById(R.id.information_message_dialog_layout_ok_button);
        okButton.setTypeface(typeface);
        okButton.setTypeface(null, Typeface.BOLD);
        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.cancel();
                deleteSenz(senz);
            }
        });

        // cancel button
        Button cancelButton = (Button) dialog.findViewById(R.id.information_message_dialog_layout_cancel_button);
        cancelButton.setTypeface(typeface);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.cancel();
            }
        });

        dialog.show();
    }

    private void deleteSenz(Senz senz) {
        new SenzorsDbSource(getActivity()).deleteSenz(senz);
        displaySensorList();
    }

}
