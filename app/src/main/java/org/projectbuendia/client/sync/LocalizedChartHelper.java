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

package org.projectbuendia.client.sync;

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.BaseColumns;

import org.projectbuendia.client.model.Concepts;
import org.projectbuendia.client.sync.providers.Contracts;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

/** A simple helper class for retrieving localized patient charts. */
public class LocalizedChartHelper {

    public static final String KNOWN_CHART_UUID = "ea43f213-66fb-4af6-8a49-70fd6b9ce5d4";
    public static final String ENGLISH_LOCALE = "en";

    /**
     * A set of uuids for concepts that represent an answer indicating everything is normal, and
     * there is no worrying symptom.
     */
    public static final ImmutableSet<String> NO_SYMPTOM_VALUES = ImmutableSet.of(
            Concepts.NO_UUID, // NO
            Concepts.SOLID_FOOD_UUID, // Solid food
            Concepts.NORMAL_UUID, // NORMAL
            Concepts.NONE_UUID); // None

    /**
     * A simple bean class representing an observation. All names and values have been localized.
     */
    public static final class LocalizedObservation {
        public final long id;
        /** The time of the encounter (hence the observation) in milliseconds since epoch. */
        public final long encounterTimeMillis;

        /** The localized name to the group/section the observation should be displayed in. */
        public final String groupName;

        /**
         * The UUID of the concept, unique and guaranteed to be stable, so suitable as a map key.
         */
        public final String conceptUuid;

        /** The localized name of the concept that was observed. */
        public final String conceptName;

        /**
         * The value that was observed non-localized. For a numeric value it will be a number,
         * for a non-numeric value it will be a UUID of the response.
         */
        // TODO: It's not clear in what situations this value can be null.
        @Nullable public final String value;

        /**
         * The value that was observed, converted to a String, and localized in the case of
         * Coded (concept) observations.
         */
        // TODO: It's not clear in what situations this value can be null.
        @Nullable public final String localizedValue;

        /**
         * Instantiates a {@link LocalizedObservation} with specified initial values.
         * @param id the unique id
         * @param encounterTimeMillis the time of the encounter (hence the observation) in
         *                            milliseconds since epoch
         * @param groupName the localized name to the group/section the observation should be
         *                  displayed in
         * @param conceptUuid the UUID of the concept that was observed
         * @param conceptName The localized name of the concept that was observed
         * @param value the value that was observed, non-localized. For a numeric value, it will be
         *              a number; for a non-numeric value, it will be a UUID of the response.
         * @param localizedValue the value that was observed, converted to a String, and localized
         *                       in the case of coded (concept) observations.
         */
        public LocalizedObservation(
                long id,
                long encounterTimeMillis,
                String groupName,
                String conceptUuid,
                String conceptName,
                @Nullable String value,
                @Nullable String localizedValue) {
            this.id = id;
            this.encounterTimeMillis = encounterTimeMillis;
            this.groupName = checkNotNull(groupName);
            this.conceptUuid = checkNotNull(conceptUuid);
            this.conceptName = checkNotNull(conceptName);
            this.value = value;
            this.localizedValue = localizedValue;
        }

        @Override
        public String toString() {
            return "id=" + id
                    + ",time=" + encounterTimeMillis
                    + ",group=" + groupName
                    + ",conceptUuid=" + conceptUuid
                    + ",conceptName=" + conceptName
                    + ",value=" + localizedValue;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof LocalizedObservation)) {
                return false;
            }
            LocalizedObservation o = (LocalizedObservation) other;
            return encounterTimeMillis == o.encounterTimeMillis
                    && Objects.equals(groupName, o.groupName)
                    && Objects.equals(conceptUuid, o.conceptUuid)
                    && Objects.equals(conceptName, o.conceptName)
                    && Objects.equals(value, o.value)
                    && Objects.equals(localizedValue, o.localizedValue);
        }
    }

    private final ContentResolver mContentResolver;

    public LocalizedChartHelper(
            ContentResolver contentResolver) {
        mContentResolver = checkNotNull(contentResolver);
    }

    /** Get all observations for a given patient from the local cache, localized to English. */
    public List<LocalizedObservation> getObservations(
            String patientUuid) {
        return getObservations(patientUuid, ENGLISH_LOCALE);
    }

    /**
     * Get all observations for a given patient.
     *
     * @param locale the locale to return the results in, to match the server String
     */
    public List<LocalizedObservation> getObservations(
            String patientUuid,
            String locale) {
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(
                    Contracts.LocalizedCharts.getLocalizedChartUri(
                            KNOWN_CHART_UUID, patientUuid, locale),
                    null, null, null, null);

            List<LocalizedObservation> result = new ArrayList<>();
            while (cursor.moveToNext()) {
                LocalizedObservation obs = new LocalizedObservation(
                        cursor.getLong(cursor.getColumnIndex(BaseColumns._ID)),
                        cursor.getInt(cursor.getColumnIndex("encounter_time")) * 1000L,
                        cursor.getString(cursor.getColumnIndex("group_name")),
                        cursor.getString(cursor.getColumnIndex("concept_uuid")),
                        cursor.getString(cursor.getColumnIndex("concept_name")),
                        cursor.getString(cursor.getColumnIndex("value")),
                        cursor.getString(cursor.getColumnIndex("localized_value"))
                );
                result.add(obs);
            }
            return result;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Get the most recent observations for each concept for a given patient from the local cache,
     * localized to English. Ordering will be by concept uuid, and there are not groups or other
     * chart based configurations.
     */
    public Map<String, LocalizedObservation> getMostRecentObservations(
            String patientUuid) {
        return getMostRecentObservations(patientUuid, ENGLISH_LOCALE);
    }

    /**
     * Get the most recent observations for each concept for a given patient from the local cache,
     * Ordering will be by concept uuid, and there are not groups or other chart-based
     * configurations.
     *
     * @param locale the locale to return the results in, to match the server String
     */
    public Map<String, LocalizedChartHelper.LocalizedObservation> getMostRecentObservations(
            String patientUuid,
            String locale) {
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(
                    Contracts.MostRecentLocalizedCharts.getMostRecentChartUri(patientUuid, locale),
                    null,
                    null,
                    null,
                    null);

            Map<String, LocalizedChartHelper.LocalizedObservation> result = Maps.newLinkedHashMap();
            while (cursor.moveToNext()) {
                String conceptUuid = cursor.getString(cursor.getColumnIndex("concept_uuid"));

                LocalizedObservation obs = new LocalizedObservation(
                        cursor.getLong(cursor.getColumnIndex(BaseColumns._ID)),
                        cursor.getInt(cursor.getColumnIndex("encounter_time")) * 1000L,
                        "", /* no group */
                        conceptUuid,
                        cursor.getString(cursor.getColumnIndex("concept_name")),
                        cursor.getString(cursor.getColumnIndex("value")),
                        cursor.getString(cursor.getColumnIndex("localized_value"))
                );
                result.put(conceptUuid, obs);
            }
            return result;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Get the most recent observations for all concepts for a set of patients from the local
     * cache. Ordering will be by concept uuid, and there are not groups or other chart-based
     * configurations.
     *
     * @param patientUuids the uuids of patients to return data for
     * @param locale the locale to return the results in, to match the server String
     */
    public Map<String, Map<String, LocalizedChartHelper.LocalizedObservation>>
            getMostRecentObservationsBatch(
                    String[] patientUuids,
                    String locale) {
        Map<String, Map<String, LocalizedChartHelper.LocalizedObservation>> observations =
                new HashMap<String, Map<String, LocalizedObservation>>();
        for (String patientUuid : patientUuids) {
            observations.put(patientUuid, getMostRecentObservations(patientUuid, locale));
        }

        return observations;
    }


    /**
     * Get all observations for a given patient.
     *
     * @param locale the locale to return the results in, to match the server String
     */
    public List<LocalizedObservation> getEmptyChart(
            String locale) {
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(
                    Contracts.LocalizedCharts.getEmptyLocalizedChartUri(KNOWN_CHART_UUID, locale),
                    null, null, null, null);

            List<LocalizedObservation> result = new ArrayList<>();
            while (cursor.moveToNext()) {
                LocalizedObservation obs = new LocalizedObservation(
                        cursor.getLong(cursor.getColumnIndex(BaseColumns._ID)),
                        0L,
                        cursor.getString(cursor.getColumnIndex("group_name")),
                        cursor.getString(cursor.getColumnIndex("concept_uuid")),
                        cursor.getString(cursor.getColumnIndex("concept_name")),
                        "", // no value
                        "" // no value
                );
                result.add(obs);
            }
            return result;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}