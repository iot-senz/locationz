package com.score.locationz.services;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.score.locationz.db.SenzorsDbSource;
import com.score.senzc.pojos.User;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Created by eranga on 9/23/15.
 */
public class LocationAddressReceiver extends AsyncTask<String, String, String> {

    private static final String TAG = LocationAddressReceiver.class.getName();

    Context context;
    LatLng latLng;
    User sender;

    public LocationAddressReceiver(Context context, LatLng latLng, User sender) {
        this.context = context;
        this.latLng = latLng;
        this.sender = sender;
    }

    @Override
    protected String doInBackground(String... params) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        StringBuilder address = new StringBuilder();
        try {
            List<Address> addressList = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addressList != null && addressList.size() > 0) {
                String subAdminArea = addressList.get(0).getSubAdminArea();
                String adminArea = addressList.get(0).getAdminArea();
                String country = addressList.get(0).getCountryName();

                if (subAdminArea != null)
                    address.append(subAdminArea).append(" ");
                else if (adminArea != null) address.append(adminArea).append(" ");

                if (country != null) address.append(country);
            }
        } catch (IOException e) {
            Log.d("Address", "Unable connect to Geocoder", e);
        }

        Log.d(TAG, address.toString());

        return address.toString();
    }

    @Override
    protected void onPostExecute(String address) {
        super.onPostExecute(address);

        // update sender's last know location in database
        new SenzorsDbSource(context).updateSenz(sender, address);
    }

}
