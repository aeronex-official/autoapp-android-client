package com.autoapp.store.ui.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.autoapp.store.R
import com.autoapp.store.databinding.FragmentStoreBinding
import com.autoapp.store.ui.account.AccountFragment
import com.autoapp.store.ui.apps.AppsFragment
import com.autoapp.store.ui.myapps.MyAppsFragment

class StoreFragment : Fragment() {

    private var _binding: FragmentStoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            showFragment(AppsFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_apps -> showFragment(AppsFragment())
                R.id.nav_my_apps -> showFragment(MyAppsFragment())
                R.id.nav_account -> showFragment(AccountFragment())
            }
            true
        }
    }

    private fun showFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.store_container, fragment)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
