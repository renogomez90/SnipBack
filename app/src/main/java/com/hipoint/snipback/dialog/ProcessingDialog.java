package com.hipoint.snipback.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.hipoint.snipback.R;

/**
 * Shows a progress dialog with useful information to intimate the user of ongoing background work
 * */
public class ProcessingDialog extends Dialog {

    public ProcessingDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_processing);
        setCancelable(false);
    }
}
