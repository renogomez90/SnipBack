package com.hipoint.snipback.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.hipoint.snipback.R


class FragmentSloMo : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_slo_mo, container, false)
    }
    companion object {
        var fragment: FragmentSloMo? = null

        fun newInstance(): FragmentSloMo {


            if (fragment == null) {
                fragment = FragmentSloMo()
            }

            return fragment!!
        }
    }


}