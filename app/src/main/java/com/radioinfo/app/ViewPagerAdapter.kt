package com.radioinfo.app

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 9
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> SimStatusFragment()
        1 -> RatPriorityFragment()
        2 -> WifiMonitorFragment()
        3 -> FrequencyBandFragment()
        4 -> SignalStrengthFragment()
        5 -> TrafficChartFragment()
        6 -> RfChartFragment()
        7 -> NetworkMonitorFragment()
        8 -> BluetoothFragment()
        else -> SimStatusFragment()
    }
}
