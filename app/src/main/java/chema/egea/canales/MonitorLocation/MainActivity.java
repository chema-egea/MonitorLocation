package chema.egea.canales.MonitorLocation;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.ubidots.ApiClient;
import com.ubidots.Variable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;



public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<Status>
{

    //Interfaz
    ///////////////////////////////////////////////////
    private SeekBar seekBarTiempo;
    private CheckBox checkBoxTiempo;
    private SeekBar seekBarDistancia;
    private CheckBox checkBoxDistancia;
    private CheckBox checkBoxCambioZona;
    private TextView ultimaInfoRegistrada;
    private TextView ultimaInfoEnviada;

    ///////////////////////////////////////////////////
    // Localizacion
    LocationManager locationManager;
    LocationListener locationListener;
    //Localizaciones predefinidas
    Location locationUni;
    Location locationCasa;
    Location locationGimnasio;
    Location locationTrabajo;
    //////////////////////////////////////////////////

    //////////////////////////////////////////////////
    //Progreso
    int tiempoUpdate;
    int distanciaUpdate;
    int distanciaAcumulada;
    //////////////////////////////////////////////////

    //////////////////////////////////////////////////
    //CONTROL DE TIEMPO

    //runs without a timer by reposting this handler at the end of the runnable
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @Override
        public void run()
        {
            enviarDatos();
            timerHandler.postDelayed(this, tiempoUpdate);
        }
    };
    //////////////////////////////////////////////////

    //////////////////////////////////////////////////
    //ULTIMA INFO REGISTRADA
    double latitud_registrada;
    double longitud_registrada;
    String fecha_registrada;

    //ULTIMA INFO ENVIADA
    double latitud_enviada;
    double longitud_enviada;
    double altitud;
    String fecha_enviada;


    //////////////////////////////////////////////////

    //////////////////////////////////////////////////
    //ZONAS
    public enum MisZonas
    {
        NINGUNA("Ninguna usual", 0),
        UA("Universidad de Alicante", 1),
        CASA("Casa", 2),
        GIMNASIO("Gimnasio", 3),
        TRABAJO("Trabajo", 4);

        private String nombreZona;
        private int identificadorZona;

        private MisZonas (String nombre, int identificador)
        {
            this.nombreZona = nombre;
            this.identificadorZona = identificador;
        }

        public String getNombreZona() {
            return nombreZona;
        }

        public int getIdentificadorZona() {
            return identificadorZona;
        }
    }
    MisZonas zonaActual;
    MisZonas zonaAnterior;
    //////////////////////////////////////////////////

    /////////////////////////////////////////////////
    // RECONOCIMIENTO DE ACTIVIDADES
    int TipoActividad = 4;

    protected static final String TAG = "MainActivity";
    protected ActivityDetectionBroadcastReceiver mBroadcastReceiver;

    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    /**
     * The DetectedActivities that we track in this sample. We use this for initializing the
     * {@code DetectedActivitiesAdapter}. We also use this for persisting state in
     * {@code onSaveInstanceState()} and restoring it in {@code onCreate()}. This ensures that each
     * activity is displayed with the correct confidence level upon orientation changes.
     */
    private ArrayList<DetectedActivity> mDetectedActivities;
    ////////////////////////////////////////////////
    // ENVÍO DE DATOS A UBIDOTS



    //////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // ------------------------------------------------------------------------------
        // INTERFAZ
        seekBarTiempo = (SeekBar) findViewById(R.id.SB_TiempoUpdate);
        seekBarDistancia = (SeekBar) findViewById(R.id.SB_DistanciaActualizacion);
        checkBoxTiempo = (CheckBox) findViewById(R.id.checkbox_ConfigurarTiempo);
        checkBoxDistancia = (CheckBox) findViewById(R.id.checkbox_ConfigurarDistancia);
        checkBoxCambioZona = (CheckBox) findViewById(R.id.checkbox_CambioZona);
        ultimaInfoRegistrada = (TextView)findViewById(R.id.TV_UltimaInfoRegistrada);
        ultimaInfoEnviada = (TextView)findViewById(R.id.TV_Enviada);

        tiempoUpdate = 1000;
        distanciaUpdate = 1;
        distanciaAcumulada = 0;

        //Listener del tiempo
        seekBarTiempo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            int progress = 1;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser)
            {
                if (progresValue > 0) {
                    progress = progresValue;
                } else {
                    progress = 1;
                }
                tiempoUpdate = progress*1000;
                checkBoxTiempo.setText("Tiempo en segundos: Cada " + progress + "s");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                gestionarTiempo();
            }
        });

        //Listener de la distancia
        seekBarDistancia.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            int progress = 1;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                if (progresValue > 0) {
                    progress = progresValue;
                } else {
                    progress = 1;
                }
                distanciaUpdate = progress;
                checkBoxDistancia.setText("Distancia en metros: Cada " + progress + "m");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                gestionarDistancia();
            }
        });


        // --------------------------------------------------------------------------------
        // LOCALIZACION


        locationUni = new Location("UA");
        locationUni.setLatitude(38.383276);
        locationUni.setLongitude(-0.5124742);

        locationCasa = new Location("Casa");
        locationCasa.setLatitude(38.1813751);
        locationCasa.setLongitude(-0.8720977);

        locationGimnasio = new Location("Gimnasio");
        locationGimnasio.setLatitude(38.18267198);
        locationGimnasio.setLongitude(-0.87657809);

        locationTrabajo = new Location("Trabajo");
        locationTrabajo.setLatitude(40.4167754);
        locationTrabajo.setLongitude(-3.7037902);

        zonaActual = MisZonas.NINGUNA;

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);


        locationListener = new MyLocationListener();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Log.e("ERROR COORDENADAS", "No se pudo obtener las coordenadas con eso de los permisos");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 123);
            }
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);

        Log.i("DEBUG_ARREGLO", "obtenemos la mejor localización");
        Location localizacion = getLastBestLocation();
        if (localizacion != null)
        {
            latitud_registrada = localizacion.getLatitude();
            longitud_registrada = localizacion.getLongitude();
            altitud = localizacion.getAltitude();

            comprobarsiEstamosEnZona(localizacion);

        }
        else
        {
            Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_LONG).show();
        }


        //---------------------------------------------------------------------------------------
        // RECONOCIMIENTO DE ACTIVIDADES
        Log.i("DEBUG_ARREGLO", "Creamos ActivityDetectionBroadcastReceiver");
        // Get a receiver for broadcasts from ActivityDetectionIntentService.
        mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();
        // Reuse the value of mDetectedActivities from the bundle if possible. This maintains state
        // across device orientation changes. If mDetectedActivities is not stored in the bundle,
        // populate it with DetectedActivity objects whose confidence is set to 0. Doing this
        // ensures that the bar graphs for only only the most recently detected activities are
        // filled in.
        Log.i("DEBUG_ARREGLO", "ActivityDetectionBroadcastReceiver creada");
        if (savedInstanceState != null && savedInstanceState.containsKey(Constants.DETECTED_ACTIVITIES))
        {
            mDetectedActivities = (ArrayList<DetectedActivity>) savedInstanceState.getSerializable(
                    Constants.DETECTED_ACTIVITIES);
        }
        else
        {
            mDetectedActivities = new ArrayList<DetectedActivity>();

            // Set the confidence level of each monitored activity to zero.
            for (int i = 0; i < Constants.MONITORED_ACTIVITIES.length; i++) {
                mDetectedActivities.add(new DetectedActivity(Constants.MONITORED_ACTIVITIES[i], 0));
            }
        }
        Log.i("DEBUG_ARREGLO", "mDetectedActivities creado");
        // Kick off the request to build GoogleApiClient.
        buildGoogleApiClient();
        Log.i("DEBUG_ARREGLO", "google api client creada");

        new actualizarInterfazUltimaInfoRegistrada().execute(null, null, null);

    }

    public void gestionarTiempo()
    {
        if(checkBoxTiempo.isChecked())
        {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.postDelayed(timerRunnable, tiempoUpdate);
        }
        else
        {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }
    public void gestionarDistancia()
    {
        if (checkBoxDistancia.isChecked())
        {
            if (distanciaAcumulada >= distanciaUpdate)
            {
                enviarDatos();
            }
        }
        else
        {
            distanciaAcumulada = 0;
        }
    }
    public void gestionarCambioZona()
    {
        if (zonaAnterior!=null)
        {
            if (zonaActual != zonaAnterior)
            {
                if (checkBoxCambioZona.isChecked())
                {
                    enviarDatos();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // LOGICA DE BOTONES E INTERFAZ

    public void onCheckboxClicked(View view)
    {
        // Is the view now checked?
        boolean checked = ((CheckBox) view).isChecked();

        // Check which checkbox was clicked
        switch(view.getId()) {
            case R.id.checkbox_ConfigurarTiempo:
                if (checked)
                {
                    seekBarTiempo.setVisibility(View.VISIBLE);
                    checkBoxTiempo.setText("Tiempo en segundos: Cada "+tiempoUpdate/1000+"s");
                    gestionarTiempo();
                }
                else
                {
                    seekBarTiempo.setVisibility(View.GONE);
                    checkBoxTiempo.setText("Tiempo en segundos");
                    gestionarTiempo();
                }
                break;
            case R.id.checkbox_ConfigurarDistancia:
                if (checked)
                {
                    seekBarDistancia.setVisibility(View.VISIBLE);
                    checkBoxDistancia.setText("Distancia en metros: Cada "+distanciaUpdate+"m");
                    gestionarDistancia();
                }else
                {
                    seekBarDistancia.setVisibility(View.GONE);
                    checkBoxDistancia.setText("Distancia en metros");
                    gestionarDistancia();
                }
                break;
            case R.id.checkbox_CambioZona:
                if (checked)
                {
                    gestionarCambioZona();
                }
                else
                {
                    gestionarCambioZona();
                }
                break;
        }
    }

    public void enviarDatosYa(View view)
    {
        enviarDatos();
    }

    public void enviarDatos()
    {
        new actualizarInterfazUltimaInfoRegistrada().execute();
        new ApiUbidots().execute();
        new actualizarInterfazUltimaInfoEnviada().execute(null,null,null);
    }

    // -------------------------------------------------------------------------
    // INTERFAZ
    private class actualizarInterfazUltimaInfoRegistrada extends AsyncTask<String, Void, String>
    {
        String info;
        @Override
        protected String doInBackground(String... params)
        {

            Log.i("DEBUG_ARREGLO", "Estamos en Ultima info registrada");
            //FECHA Y HORA
            fecha_registrada = Calendar.getInstance().getTime().toString();
            info = "Fecha y hora: " + fecha_registrada + "\n";

            //COORDENADAS
            String Stringformateado = String.format("(%.5f,%.5f)",latitud_registrada, longitud_registrada );
            info += "Localización: " + Stringformateado + ")\n";

            //ZONA
            info += "Zona: " + zonaActual.getNombreZona() + "\n";

            //ACTIVIDADES
            info += "Actividades: " + Constants.getActivityString(getApplicationContext(),TipoActividad) + "\n";


            return null;
        }

        @Override
        protected void onPostExecute(String result)
        {
            ultimaInfoRegistrada.setText(info);
        }

        @Override
        protected void onPreExecute()
        {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }

    private class actualizarInterfazUltimaInfoEnviada extends AsyncTask<String, Void, String>
    {
        String info;
        @Override
        protected String doInBackground(String... params)
        {
            //FECHA Y HORA
            fecha_enviada = Calendar.getInstance().getTime().toString();
            info = "Fecha y hora: " + fecha_enviada + "\n";

            //COORDENADAS
            latitud_enviada = latitud_registrada;
            longitud_enviada = longitud_registrada;
            String Stringformateado = String.format("(%.5f,%.5f)",latitud_enviada, longitud_enviada );
            info += "Localización: " + Stringformateado + ")\n";

            //ZONA
            info += "Zona: " + zonaActual.getNombreZona() + "\n";

            //ACTIVIDADES
            info += "Actividades: " + Constants.getActivityString(getApplicationContext(),TipoActividad) + "\n";

            return null;
        }

        @Override
        protected void onPostExecute(String result)
        {
            ultimaInfoEnviada.setText(info);
        }

        @Override
        protected void onPreExecute()
        {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }

    // -------------------------------------------------------------------------
    // LOCALIZACION
    private Location getLastBestLocation()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 123);
            }
            return null;
        }

        Location locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location locationNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        long GPSLocationTime = 0;
        if (null != locationGPS) {
            GPSLocationTime = locationGPS.getTime();
        }

        long NetLocationTime = 0;

        if (null != locationNet) {
            NetLocationTime = locationNet.getTime();
        }

        if ( 0 < GPSLocationTime - NetLocationTime )
        {
            return locationGPS;
        }
        else
        {
            return locationNet;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 123:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    getLastBestLocation();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "ACCESS_FINE_LOCATION Denied", Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /*---------- Listener class to get coordinates ------------- */
    private class MyLocationListener implements LocationListener
    {

        @Override
        public void onLocationChanged(Location loc)
        {
            //Toast.makeText(getBaseContext(), "Location changed: Lat: " + loc.getLatitude() + " Lng: " + loc.getLongitude(), Toast.LENGTH_SHORT).show();

            if(checkBoxDistancia.isChecked())
            {
                Location auxDist = new Location("aux");
                auxDist.setLatitude(latitud_registrada);
                auxDist.setLongitude(longitud_registrada);
                distanciaAcumulada += loc.distanceTo(auxDist);
                gestionarDistancia();
            }


            longitud_registrada = loc.getLongitude();
            latitud_registrada = loc.getLatitude();
            altitud = loc.getAltitude();


            Log.e("Distancia", "" + loc.distanceTo(locationUni));
            Log.e("Coordenadas", "" + locationUni.getLatitude() + ", " + locationUni.getLongitude());

            comprobarsiEstamosEnZona(loc);

            Log.i("DEBUG_ARREGLO", "vamos a actualizar la interfaz");
            new actualizarInterfazUltimaInfoRegistrada().execute(null,null,null);
            Log.i("DEBUG_ARREGLO", "interfaz actualizada");

        }


        @Override
        public void onProviderDisabled(String provider) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    }

    void comprobarsiEstamosEnZona(Location loc)
    {
        if(zonaActual!=null)
        {
            zonaAnterior=zonaActual;
        }

        if (Math.abs(loc.distanceTo(locationUni))<1500)
        {
            //Si estamos en la universidad actualizamos la zona
            zonaActual = MisZonas.UA;
        }
        else if (Math.abs(loc.distanceTo(locationCasa))<10)
        {
            //Si estamos en la Casa actualizamos la zona
            zonaActual = MisZonas.CASA;
        }
        else if (Math.abs(loc.distanceTo(locationGimnasio))<100)
        {
            //Si estamos en el gimnasio actualizamos la zona
            zonaActual = MisZonas.GIMNASIO;
        }
        else if (Math.abs(loc.distanceTo(locationTrabajo))<100)
        {
            //Si estamos en la Trabajo actualizamos la zona
            zonaActual = MisZonas.TRABAJO;
        }
        else
        {
            zonaActual = MisZonas.NINGUNA;
        }

        if(checkBoxCambioZona.isChecked())
        {
            gestionarCambioZona();
        }
    }

    // -----------------------------------------------------------------------------------
    // RECONOCIMIENTO DE ACTIVIDADES
    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * ActivityRecognition API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(ActivityRecognition.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient!=null) {
            mGoogleApiClient.connect();
        }
        else
        {
            buildGoogleApiClient();
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the broadcast receiver that informs this activity of the DetectedActivity
        // object broadcast sent by the intent service.
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver,
                new IntentFilter(Constants.BROADCAST_ACTION));
    }

    @Override
    protected void onPause() {
        // Unregister the broadcast receiver that was registered during onResume().
        timerHandler.removeCallbacks(timerRunnable);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }
    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint)
    {
        Log.i(TAG, "Connected to GoogleApiClient");
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                mGoogleApiClient,
                Constants.DETECTION_INTERVAL_IN_MILLISECONDS,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }
    public void onResult(Status status) {
    }
    /**
     * Gets a PendingIntent to be sent for each activity detection.
     */
    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, DetectedActivitiesIntentService.class);

        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    /**
     * Stores the list of detected activities in the Bundle.
     */
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putSerializable(Constants.DETECTED_ACTIVITIES, mDetectedActivities);
        super.onSaveInstanceState(savedInstanceState);
    }

    protected void updateDetectedActivitiesList(ArrayList<DetectedActivity> detectedActivities)
    {
        int ActividadMasPosible = -1;
        int porcentajeMasAlto = 0;

        for (DetectedActivity activity : detectedActivities)
        {
            activity.getType();
            if(activity.getConfidence()>porcentajeMasAlto)
            {
                porcentajeMasAlto = activity.getConfidence();
                ActividadMasPosible = activity.getType();
            }
        }

        TipoActividad = ActividadMasPosible;
        new actualizarInterfazUltimaInfoRegistrada().execute(null, null, null);
    }

    /**
     * Receiver for intents sent by DetectedActivitiesIntentService via a sendBroadcast().
     * Receives a list of one or more DetectedActivity objects associated with the current state of
     * the device.
     */
    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver
    {
        protected static final String TAG = "activity-detection-response-receiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<DetectedActivity> updatedActivities =
                    intent.getParcelableArrayListExtra(Constants.ACTIVITY_EXTRA);
            updateDetectedActivitiesList(updatedActivities);
        }
    }


    private class ApiUbidots extends AsyncTask<Integer, Void, Void>
    {
        private final String API_KEY = "65160e46563178e47459a3c14cb894cb356e6c5d";
        private final String VARIABLE_ID_LOCATION = "5728954f7625424d0a920959";
        private final String VARIABLE_ID_ZONA = "57288d5776254203d4b4ddbe";
        private final String VARIABLE_ID_ACTIVIDAD = "57288d5f76254204901a3389";

        @Override
        protected Void doInBackground(Integer... params)
        {
            try {
                Log.e("DEBUG_ARREGLO", "API AsyncTask Ubidots");
                ApiClient apiClient = new ApiClient(API_KEY);

                Log.e("DEBUG_ARREGLO", "Obtenemos variables ubidots");
                Variable localizacion = apiClient.getVariable(VARIABLE_ID_LOCATION);
                Variable zona = apiClient.getVariable(VARIABLE_ID_ZONA);
                Variable actividad = apiClient.getVariable(VARIABLE_ID_ACTIVIDAD);

                //LOCALIZACION
                Map<String, Object> context = new HashMap<String, Object>();

                context.put("lat", latitud_enviada);
                context.put("lng", longitud_enviada);

                localizacion.saveValue(altitud, context);
                Log.e("DEBUG_ARREGLO", "Guardamos localizacion ubidots");
                //AZONA

                zona.saveValue(zonaActual.getIdentificadorZona());
                Log.e("DEBUG_ARREGLO", "Guardamos zona ubidots");

                //ACTIVIDAD

                actividad.saveValue(TipoActividad);
                Log.e("DEBUG_ARREGLO", "Guardamos actividad ubidots");

            }
            catch (Exception e)
            {
                Log.e("DEBUG_ARREGLO", "Excepcion: "+e.getMessage());
            }

            return null;
        }
    }


}
