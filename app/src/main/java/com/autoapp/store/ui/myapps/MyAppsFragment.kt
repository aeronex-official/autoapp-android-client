package com.autoapp.store.ui.myapps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.autoapp.store.databinding.FragmentMyAppsBinding

class MyAppsFragment : Fragment() {

    private var _binding: FragmentMyAppsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        val pm = requireContext().packageManager
        val installedApps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { it.loadLabel(pm).toString() }

        val adapter = InstalledAppAdapter(pm) { app ->
            val intent = pm.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                startActivity(intent)
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
        adapter.submitList(installedApps)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
