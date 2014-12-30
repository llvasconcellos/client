package org.msf.records.events.user;

/**
 * An event bus event indicating that the set of known users failed to be synced from the server.
 */
public class KnownUsersSyncFailedEvent {

    public static final int REASON_UNKNOWN = 0;

    public final int reason;

    public KnownUsersSyncFailedEvent(int reason) {
        this.reason = reason;
    }
}
