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
import com.hipoint.snipback.adapter.AdapterPhotos;

public class FragmentMultiDeletePhoto extends Fragment {
    private View rootView;
   RecyclerView recycler_view;
    public  static  FragmentMultiDeletePhoto newInstance() {
        FragmentMultiDeletePhoto fragment = new FragmentMultiDeletePhoto();
        return fragment;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_multideletephoto, container, false);
        recycler_view= rootView.findViewById(R.id.recycler_view);
        recycler_view.setLayoutManager(new LinearLayoutManager(getActivity()));
        AdapterPhotos adapterPhotos = new AdapterPhotos(getActivity());
        recycler_view.setAdapter(adapterPhotos);
        return rootView;
    }


}
