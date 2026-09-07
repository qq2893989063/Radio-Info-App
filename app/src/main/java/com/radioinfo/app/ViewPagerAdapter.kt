package com.radioinfo.app

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SimStatusFragment()
            1 -> RatPriorityFragment()
            2 -> WifiChannelFragment()
            3 -> FrequencyBandFragment()
            4 -> SignalStrengthFragment()
            else -> SimStatusFragment()
        }
    }
}