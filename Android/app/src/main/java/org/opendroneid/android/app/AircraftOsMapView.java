/*
 * Open Drone ID on Open Street Map(OSM) Example.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import org.opendroneid.android.R;
import org.opendroneid.android.data.AircraftObject;
import org.opendroneid.android.data.LocationData;
import org.opendroneid.android.data.SystemData;
import org.opendroneid.android.data.Util;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class AircraftOsMapView extends Fragment {
    private static final String TAG = "AircraftOsvMapView";
    private static final double DEFAULT_ZOOM = 4.0;
    private static final double LOCATION_ZOOM = 18.0;

    private Context context;
    private MapView osvMap;
    private AircraftViewModel model;
    private MyLocationNewOverlay myLocationOverlay;
    private boolean hasCenteredOnLocation;
    private final HashMap<AircraftObject, MapObserver> aircraftObservers = new HashMap<>();

    private final Util.DiffObserver<AircraftObject> allAircraftObserver = new Util.DiffObserver<AircraftObject>() {
        @Override
        public void onAdded(Collection<AircraftObject> added) {
            for (AircraftObject aircraftObject : added) {
                trackAircraft(aircraftObject);
            }
        }

        @Override
        public void onRemoved(Collection<AircraftObject> removed) {
            for (AircraftObject aircraftObject : removed) {
                stopTrackingAircraft(aircraftObject);
            }
        }
    };

    private void trackAircraft(AircraftObject aircraftObject) {
        MapObserver observer = new MapObserver(aircraftObject);
        aircraftObservers.put(aircraftObject, observer);
    }

    private void stopTrackingAircraft(AircraftObject aircraftObject) {
        MapObserver observer = aircraftObservers.remove(aircraftObject);
        if (observer == null) {
            return;
        }
        observer.stop();
    }

    private void setupModel() {
        model = new ViewModelProvider(requireActivity()).get(AircraftViewModel.class);
        model.getAllAircraft().observe(getViewLifecycleOwner(), allAircraftObserver);
        model.getActiveAircraft().observe(getViewLifecycleOwner(), new Observer<AircraftObject>() {
            MapObserver last;

            @Override
            public void onChanged(@Nullable AircraftObject object) {
                if (object == null || object.getLocation() == null || osvMap == null) {
                    return;
                }
                MapObserver observer = aircraftObservers.get(object);
                if (observer == null) {
                    return;
                }
                GeoPoint point = new GeoPoint(
                        object.getLocation().getLatitude(), object.getLocation().getLongitude());
                Log.i(TAG, "centering on " + object + " at " + point);
                if (last != null && last.marker != null) {
                    last.marker.setAlpha(0.5f);
                    if (last.markerPilot != null) {
                        last.markerPilot.setAlpha(0.5f);
                    }
                }
                if (observer.marker != null) {
                    observer.marker.setAlpha(1.0f);
                }
                if (observer.markerPilot != null) {
                    observer.markerPilot.setAlpha(1.0f);
                }
                last = observer;
                osvMap.getController().animateTo(point);
            }
        });
    }

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.fragment_osm, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        context = requireContext();
        Configuration.getInstance().load(
                context, PreferenceManager.getDefaultSharedPreferences(context));

        osvMap = view.findViewById(R.id.map);
        osvMap.setTileSource(TileSourceFactory.MAPNIK);
        osvMap.setMultiTouchControls(true);
        osvMap.setBuiltInZoomControls(true);
        osvMap.setTilesScaledToDpi(true);

        myLocationOverlay = new MyLocationNewOverlay(osvMap);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.disableFollowLocation();
        myLocationOverlay.setDrawAccuracyEnabled(true);
        osvMap.getOverlays().add(myLocationOverlay);

        CompassOverlay compassOverlay = new CompassOverlay(requireContext(), osvMap);
        compassOverlay.enableCompass();
        osvMap.getOverlays().add(compassOverlay);

        IMapController controller = osvMap.getController();
        controller.setZoom(DEFAULT_ZOOM);
        controller.setCenter(new GeoPoint(0.0, 0.0));

        Button myLocationButton = view.findViewById(R.id.map_my_location);
        myLocationButton.setOnClickListener(v -> centerOnMyLocation(true, true));

        myLocationOverlay.runOnFirstFix(() -> {
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> centerOnMyLocation(false, false));
        });

        setupModel();
    }

    private boolean centerOnMyLocation(boolean animate, boolean showMessageIfMissing) {
        if (osvMap == null || myLocationOverlay == null) {
            return false;
        }
        GeoPoint point = myLocationOverlay.getMyLocation();
        if (point == null) {
            if (showMessageIfMissing && context != null) {
                Toast.makeText(context, R.string.rid_guard_map_waiting_location, Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        IMapController controller = osvMap.getController();
        controller.setZoom(LOCATION_ZOOM);
        if (animate) {
            controller.animateTo(point);
        } else {
            controller.setCenter(point);
        }
        hasCenteredOnLocation = true;
        osvMap.invalidate();
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (osvMap != null) {
            osvMap.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (osvMap != null) {
            osvMap.onPause();
        }
    }

    @Override
    public void onDestroyView() {
        for (MapObserver observer : new ArrayList<>(aircraftObservers.values())) {
            observer.stop();
        }
        aircraftObservers.clear();
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
        if (osvMap != null) {
            osvMap.onDetach();
        }
        myLocationOverlay = null;
        osvMap = null;
        context = null;
        super.onDestroyView();
    }

    public void setMapSettings() {
        if (getActivity() == null) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
    }

    class MapObserver implements Observer<LocationData> {
        private Marker markerPilot;
        private Object markerPilotTag;
        private Marker marker;
        private Object markerTag;
        private final List<GeoPoint> polylineData;
        private Polyline polyline;
        private final AircraftObject aircraft;

        MapObserver(AircraftObject active) {
            aircraft = active;
            aircraft.location.observe(AircraftOsMapView.this, this);
            aircraft.system.observe(AircraftOsMapView.this, systemObserver);
            polylineData = new ArrayList<>();
        }

        void stop() {
            aircraft.location.removeObserver(this);
            aircraft.system.removeObserver(systemObserver);
            if (osvMap == null) {
                return;
            }
            if (marker != null) {
                osvMap.getOverlays().remove(marker);
                marker = null;
            }
            if (markerPilot != null) {
                osvMap.getOverlays().remove(markerPilot);
                markerPilot = null;
            }
            polylineData.clear();
            if (polyline != null) {
                osvMap.getOverlays().remove(polyline);
                polyline = null;
            }
        }

        private final Observer<SystemData> systemObserver = new Observer<SystemData>() {
            @Override
            public void onChanged(@Nullable SystemData ignored) {
                SystemData system = aircraft.getSystem();
                if (system == null || osvMap == null) {
                    return;
                }
                if (system.getOperatorLatitude() == 0.0 && system.getOperatorLongitude() == 0.0) {
                    return;
                }
                GeoPoint point = new GeoPoint(
                        system.getOperatorLatitude(), system.getOperatorLongitude());
                if (markerPilot == null) {
                    String id = "ID missing";
                    if (aircraft.getIdentification1() != null) {
                        id = aircraft.getIdentification1().getUasIdAsString();
                    }
                    markerPilot = new Marker(osvMap);
                    markerPilot.setIcon(context.getDrawable(R.drawable.ic_pilot));
                    markerPilot.setPosition(point);
                    markerPilot.setTitle(system.getOperatorLocationType().toString() + "\n" + id);
                    markerPilotTag = aircraft;
                    Objects.requireNonNull(markerPilot).setOnMarkerClickListener((clicked, mapView) -> {
                        Toast.makeText(context, clicked.getTitle(), Toast.LENGTH_SHORT).show();
                        if (markerPilotTag instanceof AircraftObject) {
                            model.setActiveAircraft((AircraftObject) markerPilotTag);
                            return true;
                        }
                        return false;
                    });
                    osvMap.getOverlays().add(markerPilot);
                }
                markerPilot.setPosition(point);
            }
        };

        @Override
        public void onChanged(@Nullable LocationData ignored) {
            boolean firstMarker = false;
            LocationData location = aircraft.getLocation();
            if (location == null || osvMap == null) {
                return;
            }
            if (location.getLatitude() == 0.0 && location.getLongitude() == 0.0) {
                return;
            }
            GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
            if (marker == null) {
                String id = "ID missing";
                if (aircraft.getIdentification1() != null) {
                    id = aircraft.getIdentification1().getUasIdAsString();
                }
                marker = new Marker(osvMap);
                marker.setPosition(point);
                marker.setTitle("aircraft\n" + id);
                markerTag = aircraft;
                Objects.requireNonNull(marker).setOnMarkerClickListener((clicked, mapView) -> {
                    Toast.makeText(context, clicked.getTitle(), Toast.LENGTH_SHORT).show();
                    if (markerTag instanceof AircraftObject) {
                        model.setActiveAircraft((AircraftObject) markerTag);
                        return true;
                    }
                    return false;
                });
                osvMap.getOverlays().add(marker);
                firstMarker = true;
            }

            if (polyline != null) {
                osvMap.getOverlays().remove(polyline);
            }
            polyline = new Polyline();
            polylineData.add(point);
            polyline.setPoints(polylineData);
            polyline.getOutlinePaint().setColor(Color.RED);
            osvMap.getOverlays().add(polyline);

            marker.setPosition(point);
            if (firstMarker && !hasCenteredOnLocation) {
                IMapController controller = osvMap.getController();
                controller.setZoom(17.0);
                controller.animateTo(point);
            }
            osvMap.invalidate();
        }
    }
}
