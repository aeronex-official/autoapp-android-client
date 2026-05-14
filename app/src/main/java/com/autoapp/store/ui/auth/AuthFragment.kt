package com.autoapp.store.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.autoapp.store.databinding.FragmentAuthBinding
import com.autoapp.store.ui.MainActivity
import com.autoapp.store.ui.viewmodel.AuthViewModel

class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AuthViewModel
    private var isLoginMode = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        binding.btnSubmit.setOnClickListener { handleSubmit() }
        binding.tvToggleMode.setOnClickListener { toggleMode() }

        viewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthViewModel.AuthState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is AuthViewModel.AuthState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    (activity as? MainActivity)?.showStore()
                }
                is AuthViewModel.AuthState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
                else -> binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun handleSubmit() {
        val identifier = binding.etIdentifier.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (identifier.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (isLoginMode) {
            viewModel.login(identifier, password)
        } else {
            val name = binding.etName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.register(identifier, password, name)
        }
    }

    private fun toggleMode() {
        isLoginMode = !isLoginMode
        if (isLoginMode) {
            binding.tvTitle.text = "Login"
            binding.btnSubmit.text = "Login"
            binding.tvToggleMode.text = "No account? Register"
            binding.tilName.visibility = View.GONE
        } else {
            binding.tvTitle.text = "Register"
            binding.btnSubmit.text = "Register"
            binding.tvToggleMode.text = "Already have account? Login"
            binding.tilName.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
