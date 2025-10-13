package com.cam.final_demo.card_printer;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.cam.final_demo.R;
import com.cam.final_demo.databinding.FragmentUserInfoBinding;

import org.jetbrains.annotations.Nullable;

public class UserInfoFragment extends Fragment {
    private static final String TAG = "UserInfoFragment";
    private FragmentUserInfoBinding binding;
    private EditText etName, etEmail;
    private Button btnNext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = FragmentUserInfoBinding.inflate(inflater, container, false);

        binding.btnNext.setOnClickListener(view -> {
            String name = binding.inputName.getText().toString().trim();
            String email = binding.inputEmail.getText().toString().trim();

            // ✅ Validation
            if (TextUtils.isEmpty(name)) {
                binding.inputName.setError("Enter Name");
                binding.inputName.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(email)) {
                binding.inputEmail.setError("Enter Email");
                binding.inputEmail.requestFocus();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.inputEmail.setError("Enter a valid Email");
                binding.inputEmail.requestFocus();
                return;
            }

            // ✅ Create bundle and navigate
            Bundle args = new Bundle();
            args.putString("name", name);
            args.putString("email", email);

//            PrinterFragment f = new PrinterFragment();
            //f.setArguments(args);

            PanFragment f = new PanFragment();
            f.setArguments(args);

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, f)
                    .addToBackStack(null)
                    .commit();
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // ✅ Prevent memory leaks
    }
}

