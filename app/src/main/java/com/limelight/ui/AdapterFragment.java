package com.limelight.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.limelight.R;

public class AdapterFragment extends Fragment {
    private AdapterFragmentCallbacks callbacks;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof AdapterFragmentCallbacks)) {
            throw new IllegalStateException("Host activity must implement AdapterFragmentCallbacks");
        }
        callbacks = (AdapterFragmentCallbacks) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(callbacks.getAdapterFragmentLayoutId(), container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        callbacks.receiveAbsListView(view.findViewById(R.id.fragmentView));
    }

    @Override
    public void onDetach() {
        callbacks = null;
        super.onDetach();
    }
}
