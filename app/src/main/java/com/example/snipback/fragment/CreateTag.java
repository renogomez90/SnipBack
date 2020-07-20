package com.example.snipback.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.snipback.AppMainActivity;
import com.example.snipback.R;


public class CreateTag extends Fragment {
    private View rootView;
    ImageButton edit;
    public  static CreateTag newInstance() {
        CreateTag fragment = new CreateTag();
        return fragment;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.create_tag_fragment, container, false);
        edit=rootView.findViewById(R.id.edit);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(Videoeditingfragment.newInstance());
            }
        });

        return rootView;
    }
}
