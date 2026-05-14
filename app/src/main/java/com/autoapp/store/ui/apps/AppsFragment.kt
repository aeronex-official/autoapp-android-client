package com.autoapp.store.ui.apps

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.autoapp.store.data.local.PrefsManager
import com.autoapp.store.databinding.FragmentAppsBinding
import com.autoapp.store.service.DownloadService
import com.autoapp.store.ui.viewmodel.AppsViewModel

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AppsViewModel
    private lateinit var adapter: AppAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[AppsViewModel::class.java]

        adapter = AppAdapter { app ->
            onAppClicked(app.id, app.name)
        }

        binding.recyclerView.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadApps()
        }

        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            adapter.submitList(apps)
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }

        viewModel.downloadUrl.observe(viewLifecycleOwner) { downloadInfo ->
            downloadInfo?.let {
                startDownload(it.downloadUrl, it.appId, it.version)
                viewModel.clearDownloadUrl()
            }
        }

        viewModel.loadApps()
    }

    private fun onAppClicked(appId: String, appName: String) {
        if (!PrefsManager.hasActiveSubscription() && !PrefsManager.isLoggedIn()) {
            Toast.makeText(context, "Please login and subscribe", Toast.LENGTH_SHORT).show()
            return
        }
        val token = PrefsManager.token ?: return
        if (!PrefsManager.hasActiveSubscription()) {
            Toast.makeText(context, "Subscription required to download", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!requireContext().packageManager.canRequestPackageInstalls()) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${requireContext().packageName}")
                        )
                    )
                    Toast.makeText(context, "Please allow install from this app", Toast.LENGTH_LONG).show()
                    return
                } catch (e: Exception) {
                    // Some devices (e.g. Automotive) don't support this settings page
                    // Proceed with download anyway
                }
            }
        }

        viewModel.requestDownload(appId, token)
    }

    private fun startDownload(url: String, appId: String, version: String) {
        Toast.makeText(context, "Starting download...", Toast.LENGTH_SHORT).show()
        DownloadService.start(requireContext(), url, appId, version)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}