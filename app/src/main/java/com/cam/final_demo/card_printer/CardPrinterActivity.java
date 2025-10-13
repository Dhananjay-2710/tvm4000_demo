package com.cam.final_demo.card_printer;//package com.cam.final_demo;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.cam.final_demo.R;

public class CardPrinterActivity extends AppCompatActivity {
    private static final String TAG = "CardPrinterActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_printer); // FrameLayout as fragment container

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new UserInfoFragment())
                    .commit();
        }
    }
}

