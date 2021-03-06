package org.projectbuendia.client.ui.dialogs;

        import android.app.AlertDialog;
        import android.app.Dialog;
        import android.content.DialogInterface;
        import android.os.Bundle;
        import android.support.annotation.NonNull;
        import android.support.v4.app.DialogFragment;
        import android.support.v4.app.FragmentManager;
        import android.view.LayoutInflater;
        import android.view.View;
        import android.widget.ExpandableListView;
        import org.projectbuendia.client.App;
        import org.projectbuendia.client.R;
        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.List;
        import butterknife.ButterKnife;
        import org.projectbuendia.client.events.actions.VoidObservationsRequestEvent;
        import org.projectbuendia.client.models.ObsRow;
        import org.projectbuendia.client.ui.lists.ExpandableVoidObsRowAdapter;
        import de.greenrobot.event.EventBus;

public class VoidObservationsDialogFragment extends DialogFragment {

    private LayoutInflater mInflater;
    private ExpandableListView expListView;
    private List<String> listDataHeader;
    private HashMap<String, ArrayList<ObsRow>> listDataChild;

    public static VoidObservationsDialogFragment newInstance(ArrayList<ObsRow> observations) {
        Bundle args = new Bundle();
        args.putParcelableArrayList("obsrows", observations);
        VoidObservationsDialogFragment f = new VoidObservationsDialogFragment();
        f.setArguments(args);
        return f;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInflater = LayoutInflater.from(getActivity());
    }

    private boolean isExistingHeader(String check){

        for (String header:listDataHeader)
            if (header.equals(check)) return true;

        return false;
    }

    private void prepareData(ArrayList<ObsRow> rows){

        ArrayList<ObsRow> child;
        String verifyTitle;
        String Title;

        listDataHeader = new ArrayList<>();
        listDataChild = new HashMap<String, ArrayList<ObsRow>>();

        for (ObsRow row: rows) {

            Title = row.conceptName + " " + row.day;

            if(!isExistingHeader(Title)){
                listDataHeader.add(Title);
            }

        }

        for (String header: listDataHeader){

            child = new ArrayList<ObsRow>();

            for (ObsRow row: rows){

                verifyTitle = row.conceptName + " " + row.day;

                if (verifyTitle.equals(header)){
                    child.add(row);
                }
            }

            if (!child.isEmpty()){
                listDataChild.put(header, child);
            }
        }
    }

    @Override public @NonNull Dialog onCreateDialog(Bundle savedInstanceState) {
        View fragment = mInflater.inflate(R.layout.view_observations_dialog_fragment, null);
        ButterKnife.inject(this, fragment);

        final ArrayList<ObsRow> obsrows = getArguments().getParcelableArrayList("obsrows");
        prepareData(obsrows);

        final ExpandableVoidObsRowAdapter listAdapter = new ExpandableVoidObsRowAdapter(App.getInstance().getApplicationContext(), listDataHeader, listDataChild);
        ExpandableListView listView = (ExpandableListView) fragment.findViewById(R.id.lvObs);
        listView.setAdapter(listAdapter);

        for(int i=0; i < listAdapter.getGroupCount(); i++)
            listView.expandGroup(i);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .setPositiveButton(R.string.voiding, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                        if ((listAdapter.mCheckedItems != null) && (!listAdapter.mCheckedItems.isEmpty())) {
                            EventBus.getDefault().post(new VoidObservationsRequestEvent(listAdapter.mCheckedItems));
                        }

                        dialogInterface.dismiss();
                    }
                }).setTitle(getResources().getString(R.string.void_observations))
                .setView(fragment);
        return builder.create();
    }

    @Override
    public void show(FragmentManager manager, String tag) {
        if (manager.findFragmentByTag(tag) == null) {
            super.show(manager, tag);
        }
    }
}
