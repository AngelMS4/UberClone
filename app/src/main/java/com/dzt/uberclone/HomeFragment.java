package com.dzt.uberclone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by David on 2/10/2015.
 */
public class HomeFragment extends Fragment implements View.OnClickListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerDragListener {
    private GoogleMap map;
    private static View v;
    private GoogleApiClient mGoogleApiClient;
    double latitude, longitude;
    boolean centerOnCurrent = true;
    TextView selectPickup;
    Button requestuber;
    LatLng currentLocation;
    int shortestTime;
    int nearbyUbers, ubercount;
    boolean syncWithServer = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (v != null) {
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent != null)
                parent.removeView(v);
        }

        try {
            v = inflater.inflate(R.layout.fragment_home, container, false);
        } catch (InflateException e) {

        }

        map = ((SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map)).getMap();
        map.setMyLocationEnabled(true);

        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        map.setOnMapClickListener(this);
        map.setOnMarkerDragListener(this);

        requestuber = (Button) v.findViewById(R.id.request_uber);
        requestuber.setOnClickListener(this);

        SharedPreferences sp = getActivity().getSharedPreferences("Session", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor;
        editor = sp.edit();
        String location = sp.getString("location","");

        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        selectPickup = (TextView) v.findViewById(R.id.select_location);
        selectPickup.setOnClickListener(this);

        if(location.equals(""))
        {
            selectPickup.setText("Select pick up location");
        }
        else
        {
            String latstr = sp.getString("locationlat", "");
            String longstr = sp.getString("locationlong","");
            latitude = Double.parseDouble(latstr);
            longitude = Double.parseDouble(longstr);
            selectPickup.setText(location);

            editor.putString("location","");
            editor.putString("locationlat","");
            editor.putString("locationlong","");
            editor.commit();
            centerOnCurrent = false;
        }

        mGoogleApiClient.connect();

        return v;
    }
    public void onClick(View v)
    {
        Fragment fragment;
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        switch(v.getId())
        {
            default:
            case R.id.select_location:
                //Toast.makeText(getActivity(), "select location", Toast.LENGTH_SHORT).show();
                fragment = new SelectLocationFragment();
                fragmentManager.beginTransaction()
                        .replace(R.id.container, fragment).addToBackStack("selectlocation")
                        .commit();
                break;
            case R.id.request_uber:
                SharedPreferences sp = getActivity().getSharedPreferences("Session", Context.MODE_PRIVATE);
                String email = sp.getString("email", "");

                StringBuilder sb = new StringBuilder();
                sb.append(getResources().getString(R.string.ip));
                sb.append("uber/request/send?user_id=");
                sb.append(email);
                sb.append("&user_lat=");
                sb.append(currentLocation.latitude);
                sb.append("&user_lon=");
                sb.append(currentLocation.longitude);

                URLpetition petition = new URLpetition("send uber request");
                petition.execute(sb.toString());

                break;
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null)
        {
            if (centerOnCurrent)
            {
                latitude = mLastLocation.getLatitude();
                longitude = mLastLocation.getLongitude();

            }
            LatLng latLng = new LatLng(latitude, longitude);
            currentLocation = latLng;

            map.moveCamera(CameraUpdateFactory.newLatLng(latLng));

            map.animateCamera(CameraUpdateFactory.zoomTo(16));
            map.addMarker(new MarkerOptions().position(latLng).title("Pick me up here").draggable(true)); //.icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon_top)).anchor(0.5,0.5)

            URLpetition petition = new URLpetition("geocoder inverse");
            petition.execute("http://maps.googleapis.com/maps/api/geocode/json?latlng="+latLng.latitude+","+latLng.longitude+"&sensor=false");
        }
        else
        {
            Toast.makeText(getActivity(), "Could not retrieve your location", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }


    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onMapClick(LatLng latLng) {
        //poner un pin y cambiar la direccion
        currentLocation = latLng;

        map.moveCamera(CameraUpdateFactory.newLatLng(latLng));

        map.animateCamera(CameraUpdateFactory.zoomTo(16));
        map.clear();
        map.addMarker(new MarkerOptions().position(latLng).title("Pick me up here").draggable(true)); //.icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon_top)).anchor(0.5,0.5)

        URLpetition petition = new URLpetition("geocoder inverse");
        petition.execute("http://maps.googleapis.com/maps/api/geocode/json?latlng="+latLng.latitude+","+latLng.longitude+"&sensor=false");
    }

    @Override
    public void onMarkerDragStart(Marker marker) {

    }

    @Override
    public void onMarkerDrag(Marker marker) {

    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        LatLng latLng = marker.getPosition();
        currentLocation = latLng;
        map.clear();
        map.addMarker(new MarkerOptions().position(latLng).title("Pick me up here").draggable(true));

        URLpetition petition = new URLpetition("geocoder inverse");
        petition.execute("http://maps.googleapis.com/maps/api/geocode/json?latlng="+latLng.latitude+","+latLng.longitude+"&sensor=false");
    }


    private class URLpetition extends AsyncTask<String, Void, String>
    {
        String action;
        public URLpetition(String action)
        {
            this.action = action;
        }
        @Override
        protected String doInBackground(String... params) {
            HttpClient client = new DefaultHttpClient();
            Log.d("url = ", params[0]);
            HttpGet get = new HttpGet(params[0]);
            String retorno="";
            StringBuilder stringBuilder = new StringBuilder();
            try {
                HttpResponse response = client.execute(get);
                HttpEntity entity = response.getEntity();
                //InputStream stream = new InputStream(entity.getContent(),"UTF-8");
                InputStream stream = entity.getContent();
                BufferedReader r = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                String line;
                while ((line= r.readLine()) != null) {
                    stringBuilder.append(line);
                }

                if(action.equals("geocoder inverse") || action.equals("get nearby ubers") || action.equals("get shortest time") || action.equals("send uber request"))
                {
                    return stringBuilder.toString();
                }
            }
            catch(IOException e) {
                Log.d("Error: ", e.getMessage());
            }
            Log.d("Return text = ", retorno);
            return retorno;
        }

        @Override
        protected void onPostExecute(String result) {
            if (action.equals("geocoder inverse"))
            {
                String locationname="";
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    try {

                        locationname = ((JSONArray) jsonObject.get("results")).getJSONObject(0)
                                .getString("formatted_address");

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                catch(JSONException e)
                {
                    e.printStackTrace();
                }
                Log.d("location = ", locationname);
                selectPickup.setText(locationname);
                getNearbyUbers();
            }
            else
            {
                if(action.equals("get nearby ubers"))
                {
                    try
                    {
                        shortestTime = 0;
                        JSONArray jsonArray = new JSONArray(result);
                        nearbyUbers = jsonArray.length();
                        ubercount = 0;
                        if(nearbyUbers == 0)
                        {
                            displayNoUbersMessage();
                        }
                        for(int i = 0; i<nearbyUbers; i++)
                        {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            double lat = jsonObject.getDouble("pos_lat");
                            double lon = jsonObject.getDouble("pos_long");
                            addUberMarker(lat,lon);
                            getShortestTime(lat, lon);
                        }


                    }
                    catch(JSONException e)
                    {
                        e.printStackTrace();
                    }
                }
                else
                {
                    if (action.equals("get shortest time"))
                    {
                        try
                        {
                            JSONObject jsonObject = new JSONObject(result);
                            int shortestTimeTemp = jsonObject.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONObject("duration").getInt("value");
                            shortestTime += shortestTimeTemp;
                            ubercount++;
                            if(ubercount == nearbyUbers)
                            {
                                displayShortestTime(shortestTime, ubercount);
                            }
                        }
                        catch(JSONException e)
                        {
                            e.printStackTrace();
                        }
                    }
                    else
                    {
                        if (action.equals("send uber request"))
                        {
                            // TODO sync with server until a driver accepts
                            try
                            {
                                JSONObject jsonObject = new JSONObject(result);
                                int pendingrideid = jsonObject.getInt("pending_ride_id");
                                waitForUberDriver(pendingrideid);
                            }
                            catch(JSONException e)
                            {
                                e.printStackTrace();
                            }

                            showMSG("Waiting for a driver to accept");

                            // TODO disable things
                        }
                    }
                }
            }
        }

        @Override
        protected void onPreExecute() {}
    }

    public void waitForUberDriver(final int pendingrideid)
    {
        Thread timer = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true)
                {
                    if (syncWithServer)
                    {
                        String response = syncForUber(pendingrideid);
                        threadMsg(response);
                    }
                    else
                    {
                        return;
                    }

                    try
                    {
                        Thread.sleep(1000);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }

            private void threadMsg(String msg) {
                if (!msg.equals(null) && !msg.equals("")) {
                    Message msgObj = handler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("message", msg);
                    msgObj.setData(b);
                    handler.sendMessage(msgObj);
                }
            }

            private final Handler handler = new Handler() {

                public void handleMessage(Message msg) {

                    String aResponse = msg.getData().getString("message");

                    if ((null != aResponse))
                    {

                    }
                    else
                    {
                        Toast.makeText(getActivity(),"Not Got Response From Server.",Toast.LENGTH_SHORT).show();
                    }
                }
            };
        });

    }

    public String syncForUber(int pendingrideid)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getResources().getString(R.string.ip));
        sb.append("uber/request/pending?pendingride=");
        sb.append(pendingrideid);


        return "";
    }

    public void getNearbyUbers()
    {
        URLpetition petition = new URLpetition("get nearby ubers");
        StringBuilder sb = new StringBuilder();
        sb.append(getResources().getString(R.string.ip));
        sb.append("drivers/get/nearby?latitude=");
        sb.append(currentLocation.latitude);
        sb.append("&longitude=");
        sb.append(currentLocation.longitude);
        sb.append("&radius=0.005");

        petition.execute(sb.toString());
    }

    public void addUberMarker(double lat, double lon)
    {
        map.addMarker(new MarkerOptions().position(new LatLng(lat, lon)).icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon_top)).anchor(0.5f,0.5f)); //.icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon_top)).anchor(0.5,0.5)
    }

    public void getShortestTime(double uberlat, double uberlon)
    {
        URLpetition petition = new URLpetition("get shortest time");
        StringBuilder sb = new StringBuilder();
        sb.append("http://maps.googleapis.com/maps/api/directions/json?origin=");
        sb.append(uberlat);
        sb.append(",");
        sb.append(uberlon);
        sb.append("&destination=");
        sb.append(currentLocation.latitude);
        sb.append(",");
        sb.append(currentLocation.longitude);
        sb.append("&mode=driving&sensor=false");
        petition.execute(sb.toString());
    }

    public void displayShortestTime(int time, int ubers)
    {
        int avg = time/ubers;
        Toast.makeText(getActivity(), "Estimate time = "+avg, Toast.LENGTH_SHORT).show();
    }

    public void displayNoUbersMessage()
    {
        Toast.makeText(getActivity(), "There are no Ubers near this location", Toast.LENGTH_SHORT).show();
    }

    public void showMSG(String msg)
    {
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
    }
}