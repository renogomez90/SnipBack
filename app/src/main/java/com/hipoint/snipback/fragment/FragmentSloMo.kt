package com.hipoint.snipback.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.ui.custom.SwipeDistanceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.hipoint.snipback.AppMainActivity
import com.hipoint.snipback.R
import com.hipoint.snipback.Utils.SnipbackTimeBar
import com.hipoint.snipback.dialog.KeepSnapbackVideoDialog
import com.hipoint.snipback.listener.ISaveListener
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue


class FragmentSloMo : Fragment()  {
    private lateinit var rootView     : View




    companion object {
        var fragment: FragmentSloMo? = null

        fun newInstance(): FragmentSloMo {


            if (fragment == null) {
                fragment = FragmentSloMo()
            }
            return fragment!!
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {

        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
    ): View? {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        rootView = inflater.inflate(R.layout.fragment_slo_mo, container, false)

        return rootView
    }


}