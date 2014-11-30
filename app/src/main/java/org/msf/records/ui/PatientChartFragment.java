package org.msf.records.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.common.collect.Maps;

import org.msf.records.App;
import org.msf.records.R;
import org.msf.records.model.ChartStructure;
import org.msf.records.model.ConceptList;
import org.msf.records.model.PatientChart;
import org.msf.records.net.OpenMrsChartServer;
import org.msf.records.sync.LocalizedChartHelper;
import org.msf.records.view.VitalView;
import org.msf.records.widget.DataGridAdapter;
import org.msf.records.widget.DataGridView;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * A {@link Fragment} that displays a patient's vitals and charts.
 */
public class PatientChartFragment extends Fragment {

    private static final String TAG = PatientChartFragment.class.getName();

    public static PatientChartFragment newInstance(String patientUuid) {
        PatientChartFragment fragment = new PatientChartFragment();
        Bundle args = new Bundle();
        args.putString(PatientChartActivity.PATIENT_ID_KEY, patientUuid);
        fragment.setArguments(args);

        OpenMrsChartServer server = new OpenMrsChartServer(App.getConnectionDetails());

        // TODO(dxchen): This doesn't properly handle configuration changes. We should pass this
        // into the fragment arguments.
        // TODO(nfortescue): get proper caching, and the dictionary working.
        server.getChart(patientUuid, new Response.Listener<PatientChart>() {
            @Override
            public void onResponse(PatientChart response) {
                Log.i(TAG, response.uuid + " " + Arrays.asList(response.encounters));
            }
        },
        new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Unexpected error on fetching chart", error);
            }
        });
        server.getConcepts(
                new Response.Listener<ConceptList>() {
                    @Override
                    public void onResponse(ConceptList response) {
                        Log.i(TAG, Integer.toString(response.results.length));
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Unexpected error fetching concepts", error);
                    }
                });
        server.getChartStructure("ea43f213-66fb-4af6-8a49-70fd6b9ce5d4",
                new Response.Listener<ChartStructure>() {
                    @Override
                    public void onResponse(ChartStructure response) {
                        Log.i(TAG, Arrays.asList(response.groups).toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Unexpected error fetching concepts", error);
                    }
                });
        return fragment;
    }

    private String mPatientUuid;
    private LayoutInflater mLayoutInflater;

    @InjectView(R.id.vital_heart) VitalView mHeart;
    @InjectView(R.id.vital_blood_pressure) VitalView mBloodPressure;
    @InjectView(R.id.vital_temperature) VitalView mTemperature;
    @InjectView(R.id.vital_respirations) VitalView mRespirations;
    @InjectView(R.id.vital_pcr) VitalView mPcr;

    public PatientChartFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = savedInstanceState != null ? savedInstanceState : getArguments();
        mPatientUuid = bundle.getString(PatientChartActivity.PATIENT_ID_KEY);
        if (mPatientUuid == null) {
            Log.e(
                    TAG,
                    "No patient ID was provided to the patient chart. This indicates a "
                            + "programming error. Returning to the patient list.");

            Intent patientListIntent = new Intent(getActivity(), PatientListActivity.class);
            startActivity(patientListIntent);

            return;
        }

        mLayoutInflater = LayoutInflater.from(getActivity());
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        ViewGroup view =
                (ViewGroup) inflater.inflate(R.layout.fragment_patient_chart, container, false);
        ButterKnife.inject(this, view);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        // TODO(dxchen,nfortescue): Background thread this, or make this call async-like.
        List<LocalizedChartHelper.LocalizedObservation> observations =
                LocalizedChartHelper.getObservations(
                        getActivity().getContentResolver(), mPatientUuid);

        // A map from a concept name to the latest observation for that concept.
        Map<String, LocalizedChartHelper.LocalizedObservation> conceptsToLatestObservations =
                Maps.newHashMap();

        // The timestamp of the latest encounter made.
        long latestEncounterTimeMillis = Integer.MIN_VALUE;

        // Find the latest observation for each observation type.
        for (LocalizedChartHelper.LocalizedObservation observation : observations) {
            // If no other observations for this concept have been seen or if this is the
            if (!conceptsToLatestObservations.containsKey(observation.conceptName)
                    || observation.encounterTimeMillis >
                            conceptsToLatestObservations.get(observation.conceptName)
                                    .encounterTimeMillis) {
                conceptsToLatestObservations.put(observation.conceptName, observation);
            }

            if (observation.encounterTimeMillis > latestEncounterTimeMillis) {
                latestEncounterTimeMillis = observation.encounterTimeMillis;
            }
        }

        // Populate each of the views that we care about.
        LocalizedChartHelper.LocalizedObservation temperature =
                conceptsToLatestObservations.get("Temperature (C)");
        String temperatureValue = "--.-°";
        if (temperature != null) {
            float temperatureFloat;
            try {
                temperatureFloat = Float.parseFloat(temperature.localizedValue);
                temperatureValue = String.format("%.1f°", temperatureFloat);
            } catch (NumberFormatException e) {}
        }
        mTemperature.setValue(temperatureValue);

        LocalizedChartHelper.LocalizedObservation pulse =
                conceptsToLatestObservations.get("Pulse");
        String pulseValue = "--";
        if (pulse != null) {
            int pulseInt;
            try {
                pulseInt = Integer.parseInt(pulse.localizedValue);
                pulseValue = String.format("%d", pulseInt);
            } catch (NumberFormatException e) {}
        }
        mHeart.setValue(pulseValue);

        LocalizedChartHelper.LocalizedObservation respirations =
                conceptsToLatestObservations.get("Respiratory rate");
        String respirationsValue = "--";
        if (respirations != null) {
            int respirationsInt;
            try {
                respirationsInt = Integer.parseInt(respirations.localizedValue);
                respirationsValue = String.format("%d", respirationsInt);
            } catch (NumberFormatException e) {}
        }
        mRespirations.setValue(respirationsValue);

        LocalizedChartHelper.LocalizedObservation systolic =
                conceptsToLatestObservations.get("SYSTOLIC BLOOD PRESSURE");
        String systolicValue = "--";
        if (systolic != null) {
            int systolicInt;
            try {
                systolicInt = Integer.parseInt(systolic.localizedValue);
                systolicValue = String.format("%d", systolicInt);
            } catch (NumberFormatException e) {}
        }

        LocalizedChartHelper.LocalizedObservation diastolic =
                conceptsToLatestObservations.get("DIASTOLIC BLOOD PRESSURE");
        String diastolicValue = "--";
        if (systolic != null) {
            int diastolicInt;
            try {
                diastolicInt = Integer.parseInt(systolic.localizedValue);
                diastolicValue = String.format("%d", diastolicInt);
            } catch (NumberFormatException e) {}
        }

        mBloodPressure.setValue(String.format("%s/%s", systolicValue, diastolicValue));

        ViewGroup.LayoutParams params =
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        DataGridView grid = new DataGridView.Builder()
                .setDoubleWidthColumnHeaders(true)
                .setDataGridAdapter(new DataGridAdapter() {

                    @Override
                    public int getColumnCount() {
                        return 16;
                    }

                    @Override
                    public int getRowCount() {
                        return 30;
                    }

                    @Override
                    public View getRowHeader(int row, View convertView, ViewGroup parent) {
                        View view = mLayoutInflater.inflate(
                                R.layout.data_grid_header_chart, null /*root*/);
                        TextView textView =
                                (TextView) view.findViewById(R.id.data_grid_header_text);

                        switch (row % 8) {
                            case 0:
                                textView.setText("Diarrhea");
                                break;
                            case 1:
                                textView.setText("Nausea");
                                break;
                            case 2:
                                textView.setText("Vomiting");
                                break;
                            case 3:
                                textView.setText("Bleeding - Nose");
                                break;
                            case 4:
                                textView.setText("Bleeding - Mouth");
                                break;
                            case 5:
                                textView.setText("Sore Throat");
                                break;
                            case 6:
                                textView.setText("Abdominal Pain");
                                break;
                            case 7:
                                textView.setText("Conjunctival Infection");
                                break;
                        }

                        return view;
                    }

                    @Override
                    public View getColumnHeader(int column, View convertView, ViewGroup parent) {
                        View view = mLayoutInflater.inflate(
                                R.layout.data_grid_header_chart, null /*root*/);
                        TextView textView =
                                (TextView) view.findViewById(R.id.data_grid_header_text);

                        // 8 days.
                        if (column == 14) {
                            textView.setText("Today");
                        } else {
                            textView.setText(String.format("-%d Day", 7 - column / 2));
                        }

                        return view;
                    }

                    @Override
                    public View getCell(int row, int column, View convertView, ViewGroup parent) {
                        View view = mLayoutInflater.inflate(
                                R.layout.data_grid_cell_chart, null /*root*/);
                        if ((row + column) % 3 == 0) {
                            view.findViewById(R.id.data_grid_cell_chart_image)
                                    .setVisibility(View.VISIBLE);
                        }

                        return view;
                    }
                })
                .build(getActivity());
        grid.setLayoutParams(params);

        ((ViewGroup) ((ViewGroup) getView()).getChildAt(0)).addView(grid);
    }
}
