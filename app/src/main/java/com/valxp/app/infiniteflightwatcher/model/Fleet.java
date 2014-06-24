package com.valxp.app.infiniteflightwatcher.model;

import android.util.JsonReader;
import android.util.Log;

import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.valxp.app.infiniteflightwatcher.APIConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by ValXp on 5/20/14.
 */
public class Fleet {
    private Map<String, Flight> mFleet;
    private boolean mIsUpToDate;
    private boolean mIsUpdating = false;

    public Fleet() {
        mFleet = new HashMap<String, Flight>();
        mIsUpToDate = false;
    }

    public Runnable updateFleet(long thresholdInSeconds) {
        synchronized (this) {
            if (mIsUpdating)
                return null;
            mIsUpdating = true;
        }
        InputStream apiData = fetchJson();
        if (apiData == null) {
            mIsUpdating = false;
            return null;
        }

        parseFlightList(apiData);

        Runnable forUIThread = discardOldFlights(thresholdInSeconds);

        for (Map.Entry<String, Flight> data : mFleet.entrySet()) {
            Flight value = data.getValue();
            if (value.getNeedsUpdate()) {
                value.pullFullFlight();
                value.setNeedsUpdate(false);
            }
        }

        mIsUpToDate = true;
        mIsUpdating = false;
        return forUIThread;
    }

    public Map<String, Flight> getFleet() {
        return mFleet;
    }

    private InputStream fetchJson() {
        URL url = null;
        try {
            url = new URL(APIConstants.APICalls.FLIGHTS.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        try {
            return url.openStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    synchronized private void parseFlightList(InputStream json) {
        JsonReader reader = new JsonReader(new InputStreamReader(json));
        try {
            // The root is an array of flights
            reader.beginArray();

            // Now looping through them
            while (reader.hasNext()) {
                parseFlight(mFleet, reader);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void parseFlight(Map<String, Flight> flights, JsonReader reader) throws IOException {
        Flight tempFlight = new Flight(reader);
        if (tempFlight.getAgeMs() / 1000 > 60 * 3) {
            return;
        }
        Flight found = flights.get(tempFlight.getFlightID());
        if (found == null) {
            flights.put(tempFlight.getFlightID(), tempFlight);
        } else {
            found.addFlightData(tempFlight.getFlightHistory());
        }
    }

    public int getActiveFleetSize() {
        int activeFlights = 0;
        for (Map.Entry<String, Flight> entry : mFleet.entrySet()) {
            if (entry.getValue().getFlightHistory().size() > 0)
                activeFlights++;
        }
        return activeFlights;
    }

    synchronized private Runnable discardOldFlights(long thresholdInSeconds) {
        final List<Polyline> linesToRemove = new ArrayList<Polyline>();
        final List<Marker> markersToRemove = new ArrayList<Marker>();

        for (Iterator<Map.Entry<String, Flight>> it = mFleet.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Flight> entry = it.next();
            if (entry.getValue().getAgeMs() / 1000 > thresholdInSeconds) {
                Log.d("Fleet", "Removing old flight (" + (entry.getValue().getAgeMs() / 60000) + " minutes old): " + entry.getValue().toString());
                Marker mark = entry.getValue().getMarker();
                List<Polyline> lines = entry.getValue().getHistoryTrail();
                if (lines != null) {
                    linesToRemove.addAll(lines);
                }
                Polyline aproxLine = entry.getValue().getAproxTrail();
                if (aproxLine != null)
                    linesToRemove.add(aproxLine);
                if (mark != null)
                    markersToRemove.add(mark);
                it.remove();
            }
        }
        return new Runnable() {
            @Override
            public void run() {
                for (Polyline line : linesToRemove)
                    line.remove();
                for (Marker mark : markersToRemove)
                    mark.remove();
            }
        };
    }

    public boolean isUpToDate() {
        return mIsUpToDate;
    }
}
