package com.cam.final_demo.card_printer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.cam.final_demo.databinding.FragmentCameraBinding;

import java.io.ByteArrayOutputStream;

public class CameraFragment extends Fragment {
    private static final String TAG = "CameraFragment";
    private FragmentCameraBinding binding;
    private Bitmap capturedBitmap;

    // Permission launcher
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(getContext(), "Camera permission is required", Toast.LENGTH_SHORT).show();
                }
            });

    // Camera launcher
    private final ActivityResultLauncher<Void> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) {
                    capturedBitmap = bitmap;
                    binding.imagePreview.setImageBitmap(bitmap);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCameraBinding.inflate(inflater, container, false);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }

        // Capture button
        binding.btnCapture.setOnClickListener(v -> takePictureLauncher.launch(null));

        // Next button
        binding.btnNextCamera.setOnClickListener(v -> {
            if (capturedBitmap == null) {
                Toast.makeText(getContext(), "Please capture a photo", Toast.LENGTH_SHORT).show();
                return;
            }

            // Convert bitmap to Base64 string to pass in Bundle
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            capturedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

            // Create args bundle
            Bundle args = new Bundle();
            if (getArguments() != null) {
                args.putAll(getArguments());
            }
            args.putString("user_photo", imageBase64);

            PrinterFragment f = new PrinterFragment();
            f.setArguments(args);

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(com.cam.final_demo.R.id.fragment_container, f)
                    .addToBackStack(null)
                    .commit();
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

