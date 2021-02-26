package com.hipoint.snipback.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hipoint.snipback.R;

public class FragmentMultiDeletePhoto extends Fragment {
    private View rootView;
    RecyclerView recycler_view;

    public static FragmentMultiDeletePhoto newInstance() {
        return new FragmentMultiDeletePhoto();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_multideletephoto, container, false);
        recycler_view = rootView.findViewById(R.id.recycler_view);
        recycler_view.setLayoutManager(new LinearLayoutManager(requireActivity()));
//        AdapterPhotos adapterPhotos = new AdapterPhotos(requireActivity());
//        recycler_view.setAdapter(adapterPhotos);
        return rootView;
    }


}
