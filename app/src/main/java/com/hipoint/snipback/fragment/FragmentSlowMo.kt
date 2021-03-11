package com.hipoint.snipback.fragment

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.hipoint.snipback.R


class FragmentSlowMo : Fragment()  {
    private lateinit var rootView     : View




    companion object {
        var fragment: FragmentSlowMo? = null

        fun newInstance(): FragmentSlowMo {


            if (fragment == null) {
                fragment = FragmentSlowMo()
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