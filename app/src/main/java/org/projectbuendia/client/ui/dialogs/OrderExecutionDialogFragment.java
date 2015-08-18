// Copyright 2015 The Project Buendia Authors
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not
// use this file except in compliance with the License.  You may obtain a copy
// of the License at: http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distrib-
// uted under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
// OR CONDITIONS OF ANY KIND, either express or implied.  See the License for
// specific language governing permissions and limitations under the License.

package org.projectbuendia.client.ui.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.common.base.Joiner;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.projectbuendia.client.R;
import org.projectbuendia.client.events.OrderExecutionSaveRequestedEvent;
import org.projectbuendia.client.sync.Order;
import org.projectbuendia.client.utils.Utils;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.greenrobot.event.EventBus;

/** A {@link DialogFragment} for recording that an order was executed. */
public class OrderExecutionDialogFragment extends DialogFragment {
    /** Creates a new instance and registers the given UI, if specified. */
    public static OrderExecutionDialogFragment newInstance(
            Order order, Interval interval, List<DateTime> executionTimes) {
        // Save the current time for use as the encounter time later, thus avoiding
        // confusion if the dialog is opened before midnight and submitted after midnight.
        DateTime encounterTime = DateTime.now();
        boolean viewOnly = !interval.contains(encounterTime);

        Bundle args = new Bundle();
        args.putString("orderUuid", order.uuid);
        args.putString("instructions", order.instructions);
        List<Long> millis = new ArrayList<>();
        for (DateTime dt : executionTimes) {
            if (interval.contains(dt)) {
                millis.add(dt.getMillis());
            }
        }
        args.putString("executionTimes", Joiner.on("/").join(millis));
        args.putBoolean("viewOnly", viewOnly);
        args.putLong("encounterTimeMillis", encounterTime.getMillis());
        args.putLong("intervalStartMillis", interval.getStartMillis());
        args.putLong("intervalStopMillis", interval.getEndMillis());
        OrderExecutionDialogFragment f = new OrderExecutionDialogFragment();
        f.setArguments(args);
        return f;
    }

    @InjectView(R.id.order_execution_text) TextView mText;
    @InjectView(R.id.order_execution_increment_button) ToggleButton mIncrButton;

    private LayoutInflater mInflater;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInflater = LayoutInflater.from(getActivity());
    }

    public void onSubmit() {
        if (mIncrButton.isChecked()) {
            String orderUuid = getArguments().getString("orderUuid");
            String instructions = getArguments().getString("instructions");
            Interval interval = new Interval(getArguments().getLong("intervalStartMillis"),
                    getArguments().getLong("intervalStopMillis"));
            DateTime encounterTime = new DateTime(getArguments().getLong("encounterTimeMillis"));
            Utils.logUserAction("order_execution_submitted",
                    "orderUuid", orderUuid,
                    "instructions", instructions,
                    "interval", "" + interval,
                    "encounterTime", "" + encounterTime);

            // Post an event that triggers the PatientChartController to record the order execution.
            EventBus.getDefault().post(new OrderExecutionSaveRequestedEvent(
                    orderUuid, interval, encounterTime));
        }
    }

    void updateUi(boolean orderExecutedNow) {
        LocalDate date = new DateTime(getArguments().getLong("intervalStartMillis")).toLocalDate();
        List<DateTime> executionTimes = new ArrayList<>();
        for (String millis : getArguments().getString("executionTimes").split("/")) {
            if (!millis.isEmpty()) {
                executionTimes.add(new DateTime(Long.parseLong(millis)));
            }
        }
        if (orderExecutedNow) {
            executionTimes.add(new DateTime(getArguments().getLong("encounterTimeMillis")));
        }

        // Describe how many times the order was executed during the selected interval.
        int count = executionTimes.size();
        boolean plural = count != 1;
        String label = getResources().getString(
                date.equals(LocalDate.now()) ?
                        (plural ? R.string.order_execution_today_plural
                                : R.string.order_execution_today_singular) :
                        (plural ? R.string.order_execution_historical_plural
                                : R.string.order_execution_historical_singular),
                count, Utils.toShortString(date));

        // Show the list of times that the order was executed during the selected interval.
        label += "\n";
        for (DateTime executionTime : executionTimes) {
            label += "\n    \u00b7 " + Utils.toShortString(executionTime);
        }
        label += orderExecutedNow ? "" : "\n";  // keep total height stable
        mText.setText(label + "\u00a0");  // prevent trimming of trailing whitespace
    }

    @Override
    public @NonNull Dialog onCreateDialog(Bundle savedInstanceState) {
        View fragment = mInflater.inflate(R.layout.order_execution_dialog_fragment, null);
        ButterKnife.inject(this, fragment);

        updateUi(false);
        mIncrButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                updateUi(checked);
            }
        });

        String instructions = getArguments().getString("instructions");
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(getResources().getString(R.string.order_execution_title, instructions))
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        onSubmit();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setView(fragment);

        if (getArguments().getBoolean("viewOnly")) {
            // Historical counts can be viewed but not changed.
            builder.setNegativeButton(null, null);
            mIncrButton.setVisibility(View.GONE);
        }
        return builder.create();
    }
}