package com.autoapp.store.ui.account

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.autoapp.store.data.local.PrefsManager
import com.autoapp.store.databinding.FragmentAccountBinding
import com.autoapp.store.ui.MainActivity
import com.autoapp.store.ui.viewmodel.AuthViewModel
import com.autoapp.store.ui.viewmodel.SubscriptionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    private lateinit var authViewModel: AuthViewModel
    private lateinit var subViewModel: SubscriptionViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        subViewModel = ViewModelProvider(this)[SubscriptionViewModel::class.java]

        binding.btnLogout.setOnClickListener {
            authViewModel.logout()
            (activity as? MainActivity)?.showAuth()
        }

        binding.btnSubscribeMonthly.setOnClickListener {
            subscribe("monthly")
        }

        binding.btnSubscribeYearly.setOnClickListener {
            subscribe("yearly")
        }

        subViewModel.subscription.observe(viewLifecycleOwner) { sub ->
            if (sub != null && sub.status == "active") {
                binding.tvSubscriptionStatus.text = "Subscription Active"
                binding.tvSubscriptionPlan.text = "Plan: ${sub.plan}"
                binding.tvSubscriptionExpiry.text = "Expires: ${formatDate(sub.endDate)}"
                binding.layoutSubscriptionActive.visibility = View.VISIBLE
                binding.layoutSubscribe.visibility = View.GONE
            } else {
                binding.tvSubscriptionStatus.text = "No Active Subscription"
                binding.layoutSubscriptionActive.visibility = View.GONE
                binding.layoutSubscribe.visibility = View.VISIBLE
            }
        }

        subViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        loadSubscription()
    }

    private fun loadSubscription() {
        val token = PrefsManager.token ?: return
        subViewModel.checkSubscription(token)
    }

    private fun subscribe(plan: String) {
        val token = PrefsManager.token
        if (token.isNullOrBlank()) {
            Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Subscribe")
            .setMessage("Activate $plan subscription? (MVP: no real payment)")
            .setPositiveButton("Confirm") { _, _ ->
                subViewModel.subscribe(plan, token)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            formatter.format(parser.parse(isoDate) ?: Date())
        } catch (e: Exception) {
            isoDate
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
