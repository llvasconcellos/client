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

package org.projectbuendia.client.models;

import android.support.annotation.NonNull;

import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.projectbuendia.client.json.ConceptType;
import org.projectbuendia.client.utils.Utils;

import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/** A simple bean class representing an observation with localized names and values. */
// TODO: Make ObsPoint a member of Obs; change the structure of Obs to be simply:
// { final @Nonnull String uuid; String name; final @Nonnull ObsPoint point; } then delete
// getObsPoint(), getObsValue(), compareTo(), getTypeOrdering(), getCodedValueOrdering().
public final class Obs implements Comparable<Obs> {
    /** The time at which this observation was taken. */
    public final DateTime time;

    /** The UUID of the concept that was observed. */
    public final String conceptUuid;

    /** The data type of the concept that was observed. */
    public final ConceptType conceptType;

    /** The observed value (a string, number as a string, or answer concept UUID). */
    public final @Nullable String value;

    /** The name of the answer concept, if the value is an answer concept. */
    public final @Nullable String valueName;

    public Obs(
        long millis,
        String conceptUuid,
        ConceptType conceptType,
        @Nullable String value,
        @Nullable String valueName) {
        this.time = new DateTime(millis);
        this.conceptUuid = checkNotNull(conceptUuid);
        this.conceptType = conceptType;
        this.value = value;
        this.valueName = valueName;
    }

    /** Returns the time and value of this observation as an ObsPoint. */
    public @Nullable ObsPoint getObsPoint() {
        ObsValue ov = getObsValue();
        return ov == null ? null : new ObsPoint(time, getObsValue());
    }

    /** Returns the value of this observation as an ObsValue. */
    public @Nullable ObsValue getObsValue() {
        if (value == null || conceptType == null) return null;
        switch (conceptType) {
            case CODED:
                return ObsValue.newCoded(value, valueName);
            case NUMERIC:
                return ObsValue.newNumber(Double.valueOf(value));
            case TEXT:
                return ObsValue.newText(value);
            case BOOLEAN:
                return ObsValue.newCoded(ConceptUuids.YES_UUID.equals(value));
            case DATE:
                return ObsValue.newDate(Utils.toLocalDate(value));
            case DATETIME:
                return ObsValue.newTime(Long.valueOf(value));
        }
        return null;
    }

    @Override public String toString() {
        return "Obs(time=" + time
            + ", conceptUuid=" + conceptUuid
            + ", conceptType=" + conceptType
            + ", value=" + value
            + ", valueName=" + valueName + ")";
    }

    @Override public boolean equals(Object other) {
        if (other instanceof Obs) {
            Obs o = (Obs) other;
            return Objects.equals(time, o.time)
                && Objects.equals(conceptUuid, o.conceptUuid)
                && Objects.equals(conceptType, o.conceptType)
                && Objects.equals(value, o.value)
                && Objects.equals(valueName, o.valueName);
        } else {
            return false;
        }
    }

    @Override public int hashCode() {
        return (int) time.getMillis() + conceptUuid.hashCode()
            + conceptType.hashCode() + (value == null ? 0 : value.hashCode())
            + (valueName == null ? 0 : valueName.hashCode());
    }

    /**
     * Compares value instances according to a total ordering such that:
     * - The empty value (present == false) is ordered before all others.
     * - The Boolean value false is ordered before all other values and types.
     * - Numeric values are ordered from least to greatest magnitude.
     * - Text values are ordered lexicographically from A to Z.
     * - Coded values are ordered from least severe to most severe (if they can
     * be interpreted as having a severity); or from first to last (if they can
     * be interpreted as having a typical temporal sequence).
     * - The Boolean value true is ordered after all other values and types.
     * @param other The other Value to compare to.
     * @return
     */
    @Override public int compareTo(@NonNull Obs other) {
        if (value == null || other.value == null) {
            return value == other.value ? 0 : value == null ? -1 : 1;
        }
        if (conceptType != other.conceptType) {
            return getTypeOrdering().compareTo(other.getTypeOrdering());
        }
        if (conceptType == ConceptType.NUMERIC) {
            return Double.valueOf(value).compareTo(Double.valueOf(other.value));
        }
        if (conceptType == ConceptType.CODED || conceptType == ConceptType.BOOLEAN) {
            return getCodedValueOrdering().compareTo(other.getCodedValueOrdering());
        }
        return value.compareTo(other.value);
    }

    /** Gets a number specifying the ordering of Values of different types. */
    public Integer getTypeOrdering() {
        switch (conceptType) {
            case BOOLEAN:
                return ConceptUuids.YES_UUID.equals(value) ? 5 : 1;
            case NUMERIC:
                return 2;
            case TEXT:
                return 3;
            case CODED:
                return 4;
        }
        return 0;
    }

    /**
     * Gets a number specifying the ordering of coded values.  These are
     * arranged from least to most severe so that using the Pebble "max" filter
     * will select the most severe value from a list of values.
     */
    public Integer getCodedValueOrdering() {
        final Map<String, Integer> CODED_VALUE_ORDERING = new ImmutableMap.Builder<String, Integer>()
            .put(ConceptUuids.NO_UUID, 0)
            .put(ConceptUuids.NONE_UUID, 1)
            .put(ConceptUuids.NORMAL_UUID, 2)
            .put(ConceptUuids.SOLID_FOOD_UUID, 3)
            .put(ConceptUuids.MILD_UUID, 4)
            .put(ConceptUuids.MODERATE_UUID, 5)
            .put(ConceptUuids.SEVERE_UUID, 6)
            .put(ConceptUuids.YES_UUID, 7).build();
        Integer cvo = CODED_VALUE_ORDERING.get(value);
        return cvo == null ? 0 : cvo;
    }
}
