package com.example.snipback.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snipback.R;
import com.example.snipback.adapter.AdapterPhotos;

public class FragmentGallery extends Fragment  implements View.OnClickListener {
    private View rootView;
    ImageButton filter_button,view_button,menu_button;
    TextView filter_label,view_label,menu_label;
    ImageView autodelete_arrow;
    RecyclerView recycler_view;
    RelativeLayout relativeLayout_menu,relativeLayout_autodeleteactions,layout_autodelete,layout_filter,layout_multidelete;
    public  static  FragmentGallery newInstance() {
        FragmentGallery fragment = new FragmentGallery();
        return fragment;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_gallery, container, false);
        recycler_view= rootView.findViewById(R.id.recycler_view);
        relativeLayout_menu= rootView.findViewById(R.id.layout_menu);
        layout_filter= rootView.findViewById(R.id.layout_filter);
        view_button= rootView.findViewById(R.id._button_view);
        filter_button= rootView.findViewById(R.id.filter);
        filter_label= rootView.findViewById(R.id.filter_text);
        view_label= rootView.findViewById(R.id._button_view_text);
        recycler_view.setLayoutManager(new LinearLayoutManager(getActivity()));
        AdapterPhotos adapterPhotos = new AdapterPhotos(getActivity());
        recycler_view.setAdapter(adapterPhotos);
        relativeLayout_menu.setOnClickListener(this);
        layout_filter.setOnClickListener(this);
        return rootView;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.layout_menu:
                final Dialog dialog = new Dialog(getActivity());
                view_button.setImageResource(R.drawable.ic_view_unselected);
                view_label.setTextColor(getResources().getColor(R.color.colorDarkGreyDim));
                Window window = dialog.getWindow();
                WindowManager.LayoutParams wlp = window.getAttributes();
                wlp.gravity = Gravity.BOTTOM;
                wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                window.setAttributes(wlp);
                dialog.setContentView(R.layout.menu_layout);
                WindowManager.LayoutParams params = dialog.getWindow().getAttributes(); // change this to your dialog.
                params.y =150;
                dialog.getWindow().setAttributes(params);
                layout_autodelete= dialog.findViewById(R.id.layout_autodelete);
                relativeLayout_autodeleteactions= dialog.findViewById(R.id.layout_autodeleteactions);
                autodelete_arrow= dialog.findViewById(R.id.autodelete_arrow);
                layout_multidelete= dialog.findViewById(R.id.layout_multipledelete);
                layout_autodelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                       relativeLayout_autodeleteactions.setVisibility(View.VISIBLE);
                        autodelete_arrow.setImageResource(R.drawable.ic_keyboard_arrow_down_black_24dp);

                    }
                });
                layout_multidelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                       relativeLayout_autodeleteactions.setVisibility(View.VISIBLE);
                        autodelete_arrow.setImageResource(R.drawable.ic_keyboard_arrow_down_black_24dp);

                    }
                });

                dialog.show();
                break;
                case R.id.layout_filter:
                    final Dialog dialogFilter = new Dialog(getActivity());
                     window = dialogFilter.getWindow();
                     filter_button.setImageResource(R.drawable.ic_filter_selected);
                    filter_label.setTextColor(getResources().getColor(R.color.colorPrimaryDimRed));
                    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    dialogFilter.setContentView(R.layout.filter_layout);
                    dialogFilter.show();
                    break;
        }
    }
}
