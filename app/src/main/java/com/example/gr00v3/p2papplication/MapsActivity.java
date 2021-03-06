package com.example.gr00v3.p2papplication;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import org.json.*;

import static android.R.attr.type;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private final static int REQUEST_ENABLE_BT = 1;

    private GoogleMap mMap;
    private RemoteBroadcastService remoteBroadcastService;

    //UI elements
    private Button startServerButton;
    private Button stopServerButton;
    private TextView debugTextView;
    private CheckBox placesAPICheckbox;
    private CheckBox p2pCheckbox;
    private CheckBox internalCheckbox;
    private EditText radiusEditText;
    private Spinner spinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        remoteBroadcastService = new RemoteBroadcastService(this);

        //UI Element references
        startServerButton = (Button) findViewById(R.id.server_start_button);
        startServerButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                remoteBroadcastService.startBTServer();
            }
        });

        stopServerButton = (Button) findViewById(R.id.server_stop_button);
        stopServerButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                remoteBroadcastService.stopBTServer();
            }
        });

        placesAPICheckbox = (CheckBox) findViewById(R.id.places_api_checkbox);
        p2pCheckbox = (CheckBox) findViewById(R.id.p_to_p_checkbox);
        internalCheckbox = (CheckBox) findViewById(R.id.internal_checkbox);

        spinner = (Spinner) findViewById(R.id.spinner);

        radiusEditText = (EditText) findViewById(R.id.radius_edit_text);

        debugTextView = (TextView) findViewById(R.id.debug_text);
        debugOnScreen("MAIN", "App Started...");
    }

    // Ensures Bluetooth is available on the device and it is enabled. If not,
    // displays a dialog requesting user permission to enable Bluetooth.
    @Override
    protected void onResume() {
        super.onResume();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth is not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapLongClickListener(this);
        // Add a marker in Stockholm and move the camera
        LatLng stockholm = new LatLng(90.3, 18.1);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(stockholm));
        mMap.animateCamera( CameraUpdateFactory.zoomTo( 4.0f ));

    }

    @Override
    public void onMapLongClick(LatLng point) {
        // case: internal, other units, google maps API

        //Clear all markers
        mMap.clear();

        //Get query parameters from UI components
        boolean queryPlacesAPI = placesAPICheckbox.isChecked();
        boolean queryP2P = p2pCheckbox.isChecked();
        boolean queryInternal = internalCheckbox.isChecked();
        String poiType = spinner.getSelectedItem().toString();
        int radius = 0;

        try {
            radius = Integer.parseInt(radiusEditText.getText().toString());
        } catch (NumberFormatException e){
            Toast.makeText(getApplicationContext(), "Radius must be an integer value",
                    Toast.LENGTH_SHORT).show();
            return;
        }


        // Get pois from google maps API
        if (queryPlacesAPI) {
            JSONArray newPoiArray = remoteBroadcastService.retrievePoisFromGoogleMaps(point, radius, poiType);
            remoteBroadcastService.updateInternalPois(newPoiArray);
            drawMarkers(newPoiArray);
        }

        // Get pois from bluetooth client
        if (queryP2P) {
            //Build POI request
            JSONObject poiRequestObj = new JSONObject();
            try {
                poiRequestObj.put("radius", radius);
                poiRequestObj.put("poiType", poiType);
                poiRequestObj.put("lat", point.latitude);
                poiRequestObj.put("lng", point.longitude);

            } catch (JSONException e) {
                e.printStackTrace();
            }
            remoteBroadcastService.writeBT(poiRequestObj, RemoteBroadcastService.MessageType.POIREQUEST,
                    BluetoothSocketsClient.ConnectionType.CLIENT);
        }

        // Get pois from internal storage
        if (queryInternal) {
            JSONArray newPoiArray = remoteBroadcastService.doInternalQuery(radius, poiType, point.latitude, point.longitude);
            drawMarkers(newPoiArray);
        }

        mMap.addMarker(new MarkerOptions()
                .position(point)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.person))
                .title("You pressed here"));
    }

    public void drawMarkers(JSONArray arrayIn) {
        //Draw markers from input array

        //TODO: Get POI:s from SQLite, not internal array
        for (int i = 0; i < arrayIn.length(); i++) {
            double lat = 0;
            double lng = 0;
            String name = "";
            try {
                JSONObject obj = arrayIn.getJSONObject(i);
                JSONObject geometry = obj.getJSONObject("geometry");
                JSONObject location = geometry.getJSONObject("location");
                lat = location.getDouble("lat");
                lng = location.getDouble("lng");
                name = obj.getString("name");
            }
            catch (JSONException e) {
                Log.e("Error", Log.getStackTraceString(e));
            }
            mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(lat,lng))
                    .title(name));
        }
    }

    public void debugOnScreen(String tag, String msg) {
        debugTextView.setText(tag + ": " + msg);
    }


    //Sources
    //https://developer.android.com/reference/android/widget/Button.html
    //http://stackoverflow.com/questions/14694119/how-to-add-buttons-at-top-of-map-fragment-api-v2-layout
}
