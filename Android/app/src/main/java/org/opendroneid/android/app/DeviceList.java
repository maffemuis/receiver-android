/*
 * Copyright (C) 2019 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.app;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.opendroneid.android.Constants;
import org.opendroneid.android.R;
import org.opendroneid.android.data.AircraftObject;
import org.opendroneid.android.data.Connection;
import org.opendroneid.android.data.Identification;
import org.opendroneid.android.data.LocationData;
import org.opendroneid.android.data.SystemData;
import org.opendroneid.android.ridguard.RidGuardAircraftProfile;
import org.opendroneid.android.ridguard.RidGuardDroneUtils;
import org.opendroneid.android.ridguard.RidGuardRepository;
import org.opendroneid.android.ridguard.RidGuardSettings;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ModelAdapter;
import com.mikepenz.fastadapter.commons.utils.FastAdapterUIUtils;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.select.SelectExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DeviceList extends Fragment {
    private static final String TAG = "DeviceList";

    private AircraftViewModel mModel;
    private ModelAdapter<AircraftObject, ListItem> mItemAdapter;
    private FastAdapter<ListItem> mAdapter;
    private RecyclerView recyclerView;
    private TextView emptyView;

    public static DeviceList newInstance() {
        return new DeviceList();
    }

    private void subscribeToModel(AircraftViewModel model) {
        mModel = model;
        final Observer<Set<AircraftObject>> listObserver = aircraftList -> {
            boolean isEmpty = aircraftList == null || aircraftList.isEmpty();
            if (emptyView != null) {
                emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            }
            if (recyclerView != null) {
                recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }
            if (aircraftList == null) {
                mItemAdapter.setNewList(new ArrayList<>());
                return;
            }
            mItemAdapter.setNewList(new ArrayList<>(aircraftList));
        };

        model.getActiveAircraft().observe(getViewLifecycleOwner(), object -> {
            SelectExtension<ListItem> selectExtension = mAdapter.getExtension(SelectExtension.class);
            if (selectExtension == null) {
                return;
            }
            if (object == null) {
                selectExtension.deselect();
            } else {
                selectExtension.selectByIdentifier(object.getMacAddress(), false, false);
            }
        });
        mModel.getAllAircraft().observe(getViewLifecycleOwner(), listObserver);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        if (getActivity() == null) {
            return;
        }
        super.onActivityCreated(savedInstanceState);
        AircraftViewModel model = new ViewModelProvider(getActivity()).get(AircraftViewModel.class);
        subscribeToModel(model);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.aircraft_list, container, false);

        mItemAdapter = new ModelAdapter<>(ListItem::new);
        mAdapter = FastAdapter.with(mItemAdapter);
        mAdapter.setHasStableIds(true);
        mAdapter.withSelectable(true);

        mAdapter.withSelectionListener((item, selected) -> {
            if (selected && item != null && mModel != null
                    && mModel.getActiveAircraft().getValue() != item.object) {
                mModel.setActiveAircraft(item.object);
            }
        });

        emptyView = viewGroup.findViewById(R.id.device_list_empty);
        recyclerView = viewGroup.findViewById(R.id.device_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(mAdapter);
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.scrollToPosition(0);

        return viewGroup;
    }

    static String elapsed(long start) {
        long millis = System.currentTimeMillis() - start;
        return String.format(Locale.US, "%02d:%02d ",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis)
                        - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }

    private void showDetails(AircraftObject aircraft) {
        if (getActivity() == null) {
            return;
        }
        DetailViewModel model = new ViewModelProvider(getActivity()).get(DetailViewModel.class);
        model.select(aircraft);
        DeviceDetailFragment newFragment = DeviceDetailFragment.newInstance();
        newFragment.show(getParentFragmentManager(), "dialog");
    }

    public class AircraftViewHolder extends FastAdapter.ViewHolder<ListItem> {
        private final TextView nameView;
        private final TextView locationView;
        private final TextView profileView;
        private final TextView profileDetailView;
        private final TextView metricsView;
        private final TextView rssiView;
        private AircraftObject aircraft;
        private final View view;
        private final ImageView iconImageView;
        private final Drawable droneIcon;
        private final RidGuardSettings ridGuardSettings;
        private final Button ignoreButton;

        AircraftViewHolder(View v) {
            super(v);
            view = v;
            nameView = v.findViewById(R.id.aircraftName);
            locationView = v.findViewById(R.id.aircraftFun);
            profileView = v.findViewById(R.id.aircraftProfile);
            profileDetailView = v.findViewById(R.id.aircraftProfileDetail);
            metricsView = v.findViewById(R.id.aircraftMetrics);
            rssiView = v.findViewById(R.id.rssi);
            ridGuardSettings = new RidGuardSettings(v.getContext());

            Button infoButton = v.findViewById(R.id.modButton);
            infoButton.setText(R.string.info);
            infoButton.setOnClickListener(v1 -> showDetails(aircraft));

            ignoreButton = v.findViewById(R.id.ignoreButton);
            ignoreButton.setOnClickListener(v12 -> {
                String id = RidGuardDroneUtils.getPrimaryId(aircraft);
                ridGuardSettings.ignoreTemporarily(id, 30);
            });

            droneIcon = ContextCompat.getDrawable(requireActivity(), R.mipmap.ic_plane_icon);
            iconImageView = v.findViewById(R.id.drone_icon);
        }

        private void setIdText(Identification id) {
            if (id == null) {
                return;
            }
            String value = id.getUasIdAsString();
            if (value == null || value.isEmpty()) {
                value = RidGuardDroneUtils.getPrimaryId(aircraft);
            }
            if (value != null && value.length() > Constants.MAX_ID_BYTE_SIZE) {
                nameView.setTextSize(9);
            } else {
                nameView.setTextSize(16);
            }
            nameView.setText(value == null ? "ID onbekend" : value);
        }

        @Override
        public void bindView(@NonNull ListItem aircraftItem, @NonNull List<Object> payloads) {
            if (getContext() == null) {
                return;
            }

            aircraft = aircraftItem.object;
            StateListDrawable selectableBackground =
                    FastAdapterUIUtils.getSelectableBackground(getContext(), Color.LTGRAY, true);
            view.setBackground(selectableBackground);

            setIdText(RidGuardAircraftProfile.findBestIdentification(aircraft));
            updateProfile();
            updateConnection(aircraft.getConnection());
            updateLocation(aircraft.getLocation());

            Connection connection = aircraft.getConnection();
            boolean isDemo = connection != null && connection.transportType != null
                    && connection.transportType.startsWith("DEMO");
            ignoreButton.setEnabled(!isDemo);

            aircraft.connection.observe(DeviceList.this, connectionObserver);
            aircraft.location.observe(DeviceList.this, locationObserver);
            aircraft.identification1.observe(DeviceList.this, identificationObserver);
            aircraft.identification2.observe(DeviceList.this, identificationObserver);
            aircraft.id1Shadow.observe(DeviceList.this, shadowIdObserver);
            aircraft.id2Shadow.observe(DeviceList.this, shadowIdObserver);
            aircraft.system.observe(DeviceList.this, systemObserver);
        }

        @Override
        public void unbindView(@NonNull ListItem aircraftItem) {
            aircraft.connection.removeObserver(connectionObserver);
            aircraft.location.removeObserver(locationObserver);
            aircraft.identification1.removeObserver(identificationObserver);
            aircraft.identification2.removeObserver(identificationObserver);
            aircraft.id1Shadow.removeObserver(shadowIdObserver);
            aircraft.id2Shadow.removeObserver(shadowIdObserver);
            aircraft.system.removeObserver(systemObserver);
            nameView.setText(null);
            locationView.setText(null);
            profileView.setText(null);
            profileDetailView.setText(null);
            metricsView.setText(null);
            rssiView.setText(null);
        }

        private final Observer<Connection> connectionObserver = this::updateConnection;
        private final Observer<LocationData> locationObserver = this::updateLocation;
        private final Observer<Identification> identificationObserver = identification -> updateProfile();
        private final Observer<SystemData> systemObserver = system -> updateProfile();

        private final Observer<Identification> shadowIdObserver = identification -> {
            if (identification != null) {
                setIdText(identification);
                if (droneIcon != null) {
                    droneIcon.setColorFilter(0xff00ff00, PorterDuff.Mode.MULTIPLY);
                    iconImageView.setImageDrawable(droneIcon);
                }
            }
        };

        private void updateConnection(Connection connection) {
            if (connection == null) {
                rssiView.setText("–");
                updateMetrics(null, aircraft != null ? aircraft.getLocation() : null);
                return;
            }
            String transport = connection.transportType == null ? "?" : connection.transportType;
            rssiView.setText(String.format(Locale.US, "%s\n%d dBm\n%d msg",
                    transport, connection.rssi, connection.totalMessages));
            updateMetrics(connection, aircraft != null ? aircraft.getLocation() : null);
        }

        private void updateLocation(LocationData locationData) {
            if (locationData != null) {
                Resources res = getResources();
                locationView.setText(String.format(Locale.US, "%s boven %s, %s, %s afstand",
                        locationData.getHeightLessPreciseAsString(res),
                        locationData.getHeightType().toString(),
                        locationData.getSpeedHorizontalLessPreciseAsString(res),
                        locationData.getDistanceAsString()));
            }
            updateMetrics(aircraft != null ? aircraft.getConnection() : null, locationData);
        }

        private void updateProfile() {
            if (aircraft == null) {
                return;
            }
            RidGuardAircraftProfile.Profile profile = RidGuardAircraftProfile.from(aircraft);
            profileView.setText(profile.getPrimarySummary());
            profileDetailView.setText(profile.getSecondarySummary());
        }

        private void updateMetrics(Connection connection, LocationData locationData) {
            float distance = locationData != null ? locationData.getDistance() : 0f;
            String distanceText = distance > 0 ? String.format(Locale.US, "%.0f m", distance) : "–";
            Double altitudeDiff = null;
            if (locationData != null && getContext() != null) {
                double droneAlt = locationData.getAltitudeGeodetic();
                if (droneAlt == -1000) {
                    droneAlt = locationData.getAltitudePressure();
                }
                if (droneAlt != -1000
                        && RidGuardRepository.getInstance(requireContext()).getReceiverLocation() != null) {
                    altitudeDiff = droneAlt
                            - RidGuardRepository.getInstance(requireContext()).getReceiverLocation().getAltitude();
                }
            }
            String altitudeText = altitudeDiff != null
                    ? String.format(Locale.US, "%.0f m", altitudeDiff) : "–";
            String speedText = locationData != null && locationData.getSpeedHorizontal() != 255
                    ? String.format(Locale.US, "%.1f m/s", locationData.getSpeedHorizontal()) : "–";
            String headingText = locationData != null && locationData.getDirection() != 361
                    ? String.format(Locale.US, "%.0f°", locationData.getDirection()) : "–";
            long lastSeen = connection != null ? connection.lastSeen : 0L;
            String lastSeenText = lastSeen > 0
                    ? String.format(Locale.US, "%ds",
                    Math.max(0, (System.currentTimeMillis() - lastSeen) / 1000))
                    : "–";
            metricsView.setText(String.format(Locale.US,
                    "%s · Δalt %s · v %s · hdg %s · %s",
                    distanceText, altitudeText, speedText, headingText, lastSeenText));
        }
    }

    public class ListItem extends AbstractItem<ListItem, AircraftViewHolder> {
        private final AircraftObject object;

        ListItem(AircraftObject object) {
            this.object = object;
        }

        @NonNull
        @Override
        public AircraftViewHolder getViewHolder(@NonNull View v) {
            return new AircraftViewHolder(v);
        }

        @Override
        public long getIdentifier() {
            return object.getMacAddress();
        }

        @Override
        public int getType() {
            return 0;
        }

        @Override
        public int getLayoutRes() {
            return R.layout.listitem_aircraft;
        }

        @Override
        @NonNull
        public String toString() {
            return "ListItem{" + "object=" + object + '}';
        }
    }
}
