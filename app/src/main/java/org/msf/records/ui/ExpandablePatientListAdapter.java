package org.msf.records.ui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorTreeAdapter;
import android.widget.Filter;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.TextView;

import org.msf.records.R;
import org.msf.records.filter.FilterGroup;
import org.msf.records.filter.FilterQueryProviderFactory;
import org.msf.records.filter.LocationUuidFilter;
import org.msf.records.filter.SimpleSelectionFilter;
import org.msf.records.model.Concept;
import org.msf.records.model.LocationTree;
import org.msf.records.model.Status;

import org.msf.records.sync.LocalizedChartHelper;
import org.msf.records.sync.PatientProjection;
import org.msf.records.utils.PatientCountDisplay;

import java.util.ArrayList;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * Created by Gil on 24/11/14.
 */
public class ExpandablePatientListAdapter extends CursorTreeAdapter {

    private static final String TAG = ExpandablePatientListAdapter.class.getSimpleName();

    private Context mContext;
    private String mQueryFilterTerm;
    private SimpleSelectionFilter mFilter;

    public ExpandablePatientListAdapter(
            Cursor cursor, Context context, String queryFilterTerm,
            SimpleSelectionFilter filter) {
        super(cursor, context);
        mContext = context;
        mQueryFilterTerm = queryFilterTerm;
        mFilter = filter;
    }

    public SimpleSelectionFilter getSelectionFilter() {
        return mFilter;
    }

    public void setSelectionFilter(SimpleSelectionFilter filter) {
        mFilter = filter;
    }

    public void filter(CharSequence constraint, Filter.FilterListener listener) {
        setGroupCursor(null); // Reset the group cursor.

        // Set the query filter term as a member variable so it can be retrieved when
        // getting patients-per-tent.
        mQueryFilterTerm = constraint.toString();

        // Perform the actual filtering.
        getFilter().filter(constraint, listener);
    }

    @Override
    protected Cursor getChildrenCursor(Cursor groupCursor) {
        Cursor itemCursor = getGroup(groupCursor.getPosition());

        String tent = itemCursor.getString(PatientProjection.COUNTS_COLUMN_LOCATION_UUID);

        FilterQueryProvider queryProvider =
                new FilterQueryProviderFactory().getFilterQueryProvider(
                        mContext,
                        new FilterGroup(getSelectionFilter(),
                        new LocationUuidFilter(tent)));

        Cursor patientsCursor = null;

        try {
            patientsCursor = queryProvider.runQuery(mQueryFilterTerm);
            Log.d(TAG, "childCursor " + patientsCursor.getCount());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return patientsCursor;
    }

    @Override
    protected View newGroupView(Context context, Cursor cursor, boolean isExpanded, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.listview_tent_header, null);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
        int patientCount = getChildrenCursor(cursor).getCount();
        String locationUuid = cursor.getString(PatientProjection.COUNTS_COLUMN_LOCATION_UUID);
        String tentName = context.getResources().getString(R.string.unknown_tent);
        LocationTree location = LocationTree.getTentForUuid(locationUuid);
        if (location != null) {
            tentName = location.toString();
        }

        TextView item = (TextView) view.findViewById(R.id.patient_list_tent_tv);
        item.setText(PatientCountDisplay.getPatientCountTitle(context, patientCount, tentName));
    }

    @Override
    protected View newChildView(Context context, Cursor cursor, boolean isLastChild, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.listview_cell_search_results, parent, false);
        ViewHolder holder = new ViewHolder(view);
        view.setTag(holder);
        return view;
    }

    @Override
    protected void bindChildView(View convertView, Context context, Cursor cursor, boolean isLastChild) {
        ViewHolder holder = null;
        if (convertView != null) {
            holder = (ViewHolder) convertView.getTag();
        }

        String patient_uuid = cursor.getString(PatientProjection.COLUMN_UUID);
        String givenName = cursor.getString(PatientProjection.COLUMN_GIVEN_NAME);
        String familyName = cursor.getString(PatientProjection.COLUMN_FAMILY_NAME);
        String id = cursor.getString(PatientProjection.COLUMN_ID);
        String uuid = cursor.getString(PatientProjection.COLUMN_UUID);
        String status = cursor.getString(PatientProjection.COLUMN_STATUS);
        String gender = cursor.getString(PatientProjection.COLUMN_GENDER);
        int ageMonths = cursor.getInt(PatientProjection.COLUMN_AGE_MONTHS);
        int ageYears = cursor.getInt(PatientProjection.COLUMN_AGE_YEARS);

        holder.mPatientName.setText(givenName + " " + familyName);
        holder.mPatientId.setText(id);

        // Grab observations for this patient so we can determine condition and pregnant status.
        // TODO(akalachman): Get rid of this whole block as it's inefficient.
        boolean pregnant = false;
        String condition = null;
        Map<String, LocalizedChartHelper.LocalizedObservation> observationMap =
                LocalizedChartHelper.getMostRecentObservations(mContext.getContentResolver(), uuid);
        if (observationMap != null) {
            pregnant = observationMap.containsKey(Concept.PREGNANCY_UUID) &&
                    observationMap.get(Concept.PREGNANCY_UUID).value.equals(Concept.YES_UUID);
            if (observationMap.containsKey(Concept.GENERAL_CONDITION_UUID)) {
                condition = observationMap.get(Concept.GENERAL_CONDITION_UUID).value;
            }
        }

        // TODO(akalachman): Extract colors into helper class + resources.
        if (condition == null) {
            holder.mPatientId.setBackgroundColor( Color.parseColor( "#D8D8D8" ) );
        } else if (condition.equals(Concept.GENERAL_CONDITION_GOOD_UUID)) {
            holder.mPatientId.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else if (condition.equals(Concept.GENERAL_CONDITION_FAIR_UUID)) {
            holder.mPatientId.setBackgroundColor(Color.parseColor("#FFC927"));
        } else if (condition.equals(Concept.GENERAL_CONDITION_POOR_UUID)) {
            holder.mPatientId.setBackgroundColor(Color.parseColor("#FF2121"));
        } else if (condition.equals(Concept.GENERAL_CONDITION_VERY_POOR_UUID)) {
            holder.mPatientId.setBackgroundColor(Color.parseColor("#D0021B"));
        } else {
            holder.mPatientId.setBackgroundColor( Color.parseColor( "#D8D8D8" ) );
        }

        if (ageMonths > 0) {
            holder.mPatientAge.setText(
                    context.getResources().getString(R.string.age_months, ageMonths));
        } else if (ageYears > 0) {
            holder.mPatientAge.setText(
                    context.getResources().getString(R.string.age_years, ageYears));
        } else {
            holder.mPatientAge.setText(
                    context.getResources().getString(R.string.age_years, 99));
            holder.mPatientAge.setTextColor(context.getResources().getColor(R.color.transparent));
        }

        if (gender != null && gender.equals("M")) {
            holder.mPatientGender.setImageDrawable(context.getResources().getDrawable(R.drawable.gender_man));
        }

        if (gender != null && gender.equals("F")) {
            if (pregnant) {
                holder.mPatientGender.setImageDrawable(context.getResources().getDrawable(R.drawable.gender_pregnant));
            } else {
                holder.mPatientGender.setImageDrawable(context.getResources().getDrawable(R.drawable.gender_woman));
            }
        }

        if (gender == null) {
            holder.mPatientGender.setVisibility(View.GONE);
        }

        // Add a bottom border and extra padding to the last item in each group.
        if (isLastChild) {
            convertView.setBackgroundResource(R.drawable.bottom_border_1dp);
            convertView.setPadding(
                    convertView.getPaddingLeft(), convertView.getPaddingTop(),
                    convertView.getPaddingRight(), 40);
        } else {
            convertView.setBackgroundResource(0);
            convertView.setPadding(
                    convertView.getPaddingLeft(), convertView.getPaddingTop(),
                    convertView.getPaddingRight(), 20);
        }
    }

    static class ViewHolder {
        @InjectView(R.id.listview_cell_search_results_name) TextView mPatientName;
        @InjectView(R.id.listview_cell_search_results_id) TextView mPatientId;
        @InjectView(R.id.listview_cell_search_results_gender) ImageView mPatientGender;
        @InjectView(R.id.listview_cell_search_results_age) TextView mPatientAge;

        public ViewHolder(View view) {
            ButterKnife.inject(this, view);
        }
    }
}
