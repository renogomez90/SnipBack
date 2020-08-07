package com.hipoint.snipback.Utils;

import android.content.Context;

import com.kaopiz.kprogresshud.KProgressHUD;

public class CommonUtils {

    public static KProgressHUD showProgressDialog(Context context) {
        KProgressHUD hud = KProgressHUD.create(context)
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .show();
        return hud;
    }

}
