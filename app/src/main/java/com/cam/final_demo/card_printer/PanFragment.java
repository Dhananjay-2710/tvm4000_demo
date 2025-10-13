package com.cam.final_demo.card_printer;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.cam.final_demo.R;
import com.cam.final_demo.databinding.FragmentPanBinding;

import org.jetbrains.annotations.Nullable;

public class PanFragment extends Fragment {
    private static final String TAG = "PanFragment";
    private EditText etPan;
    private Button btnNext;

    private FragmentPanBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = FragmentPanBinding.inflate(inflater, container, false);

        binding.btnNextPan.setOnClickListener(view -> {
            String pan = binding.inputPan.getText().toString().trim();

            if (TextUtils.isEmpty(pan)) {
                Toast.makeText(getContext(), "Enter PAN Number", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create a fresh bundle
            Bundle args = new Bundle();
            if (getArguments() != null) {
                args.putAll(getArguments()); // copy existing values (name/email etc.)
            }
            args.putString("pan", pan);

            CameraFragment f = new CameraFragment();
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
        binding = null; // prevent memory leaks
    }
}

