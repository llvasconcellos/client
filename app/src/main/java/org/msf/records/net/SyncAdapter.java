package org.msf.records.net;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.volley.toolbox.RequestFuture;

import org.msf.records.App;
import org.msf.records.model.Patient;
import org.msf.records.provider.PatientContract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Created by Gil on 21/11/14.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = SyncAdapter.class.getSimpleName();

    /**
     * Project used when querying content provider. Returns all known fields.
     */
    private static final String[] PROJECTION = new String[] {
            PatientContract.Patient._ID,
            PatientContract.Patient.COLUMN_NAME_PATIENT_ID,
            PatientContract.Patient.COLUMN_NAME_GIVEN_NAME,
            PatientContract.Patient.COLUMN_NAME_FAMILY_NAME,
            PatientContract.Patient.COLUMN_NAME_UUID,
            PatientContract.Patient.COLUMN_NAME_STATUS,
            PatientContract.Patient.COLUMN_NAME_ADMISSION_TIMESTAMP
    };

    // Constants representing column positions from PROJECTION.
    public static final int COLUMN_ID = 0;
    public static final int COLUMN_PATIENT_ID = 1;
    public static final int COLUMN_GIVEN_NAME = 2;
    public static final int COLUMN_FAMILY_NAME = 3;
    public static final int COLUMN_UUID = 4;
    public static final int COLUMN_STATUS = 5;
    public static final int COLUMN_ADMISSION_TIMESTAMP = 6;


    /**
     * Content resolver, for performing database operations.
     */
    private final ContentResolver mContentResolver;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContentResolver = context.getContentResolver();
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        mContentResolver = context.getContentResolver();
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Log.i(TAG, "Beginning network synchronization");
        try {
            updatePatientData(syncResult);
        } catch (RemoteException e) {
            Log.e(TAG, "Error updating database: " + e.toString());
            syncResult.databaseError = true;
            return;
        } catch (OperationApplicationException e) {
            Log.e(TAG, "Error updating database: " + e.toString());
            syncResult.databaseError = true;
            return;
        } catch (InterruptedException e){
            Log.e(TAG, "Error interruption: " + e.toString());
            syncResult.stats.numIoExceptions++;
            return;
        } catch (ExecutionException e){
            Log.e(TAG, "Error failed to execute: " + e.toString());
            syncResult.stats.numIoExceptions++;
            return;
        } catch (Exception e){
            Log.e(TAG, "Error reading from network: " + e.toString());
            syncResult.stats.numIoExceptions++;
            return;
        }
        Log.i(TAG, "Network synchronization complete");
    }

    private void updatePatientData(SyncResult syncResult) throws InterruptedException, ExecutionException, RemoteException, OperationApplicationException {
        final ContentResolver contentResolver = getContext().getContentResolver();

        RequestFuture<List<Patient>> future = RequestFuture.newFuture();
        App.getServer().listPatients("", "", "", future, null, TAG);

        //No need for callbacks as the {@AbstractThreadedSyncAdapter} code is executed in a background thread
        List<Patient> patients = future.get();

        ArrayList<ContentProviderOperation> batch = new ArrayList<>();


        HashMap<String, Patient> patientsMap = new HashMap<>();
        for (Patient p : patients) {
            patientsMap.put(p.id, p);
        }

        // Get list of all items
        Log.i(TAG, "Fetching local entries for merge");
        Uri uri = PatientContract.Patient.CONTENT_URI; // Get all entries
        Cursor c = contentResolver.query(uri, PROJECTION, null, null, null);
        assert c != null;
        Log.i(TAG, "Found " + c.getCount() + " local entries. Computing merge solution...");


        int id;
        String patientId, givenName, familyName, uuid, status;
        long admissionTimestamp;

        //iterate through the list of patients
        while(c.moveToNext()){
            syncResult.stats.numEntries++;

            id = c.getInt(COLUMN_ID);
            patientId = c.getString(COLUMN_PATIENT_ID);
            givenName = c.getString(COLUMN_GIVEN_NAME);
            familyName = c.getString(COLUMN_FAMILY_NAME);
            uuid = c.getString(COLUMN_UUID);
            status = c.getString(COLUMN_STATUS);
            admissionTimestamp = c.getLong(COLUMN_UUID);

            Patient patient = patientsMap.get(patientId);
            if (patient != null) {
                // Entry exists. Remove from entry map to prevent insert later.
                patientsMap.remove(patientId);
                // Check to see if the entry needs to be updated
                Uri existingUri = PatientContract.Patient.CONTENT_URI.buildUpon().appendPath(String.valueOf(id)).build();

                //check if it needs updating
                if ((patient.given_name != null && !patient.given_name.equals(givenName)) ||
                        (patient.family_name != null && !patient.family_name.equals(familyName)) ||
                        (patient.uuid != null && !patient.uuid.equals(uuid)) ||
                        (patient.status != null && !patient.status.equals(status)) ||
                        (patient.admission_timestamp != null &&
                                !patient.admission_timestamp.equals(admissionTimestamp))) {
                    // Update existing record
                    Log.i(TAG, "Scheduling update: " + existingUri);
                    batch.add(ContentProviderOperation.newUpdate(existingUri)
                            .withValue(PatientContract.Patient.COLUMN_NAME_GIVEN_NAME, givenName)
                            .withValue(PatientContract.Patient.COLUMN_NAME_FAMILY_NAME, familyName)
                            .withValue(PatientContract.Patient.COLUMN_NAME_UUID, uuid)
                            .withValue(PatientContract.Patient.COLUMN_NAME_STATUS, status)
                            .withValue(PatientContract.Patient.COLUMN_NAME_ADMISSION_TIMESTAMP, admissionTimestamp)
                            .build());
                    syncResult.stats.numUpdates++;
                } else {
                    Log.i(TAG, "No action required for " + existingUri);
                }
            } else {
                // Entry doesn't exist. Remove it from the database.
                Uri deleteUri = PatientContract.Patient.CONTENT_URI.buildUpon()
                        .appendPath(Integer.toString(id)).build();
                Log.i(TAG, "Scheduling delete: " + deleteUri);
                batch.add(ContentProviderOperation.newDelete(deleteUri).build());
                syncResult.stats.numDeletes++;
            }
        }
        c.close();


        for (Patient e : patientsMap.values()) {
            Log.i(TAG, "Scheduling insert: entry_id=" + e.id);
            batch.add(ContentProviderOperation.newInsert(PatientContract.Patient.CONTENT_URI)
                    .withValue(PatientContract.Patient.COLUMN_NAME_GIVEN_NAME, e.given_name)
                    .withValue(PatientContract.Patient.COLUMN_NAME_FAMILY_NAME, e.family_name)
                    .withValue(PatientContract.Patient.COLUMN_NAME_UUID, e.uuid)
                    .withValue(PatientContract.Patient.COLUMN_NAME_STATUS, e.status)
                    .withValue(PatientContract.Patient.COLUMN_NAME_ADMISSION_TIMESTAMP, e.admission_timestamp)
                    .build());
            syncResult.stats.numInserts++;
        }
        Log.i(TAG, "Merge solution ready. Applying batch update");
        mContentResolver.applyBatch(PatientContract.CONTENT_AUTHORITY, batch);
        mContentResolver.notifyChange(PatientContract.Patient.CONTENT_URI, null, false);




        //TODO(giljulio) update the server as well as the client
    }

}
