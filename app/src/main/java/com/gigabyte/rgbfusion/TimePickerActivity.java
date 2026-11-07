package com.gigabyte.rgbfusion;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TimePicker;

import androidx.appcompat.app.AppCompatActivity;

import com.gigabyte.rgbfusion.core.LedState;

import java.util.Locale;

/** Start / end time for the schedule mode (TimepickerActivity in the original). */
public class TimePickerActivity extends AppCompatActivity {

    private LedState state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = LedState.decode(getIntent().getStringExtra(LedFunActivity.EXTRA_STATE));
        if (state == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_timepicker);

        TimePicker start = findViewById(R.id.s_picker);
        TimePicker end = findViewById(R.id.e_picker);
        init(start, state.startTime);
        init(end, state.endTime);
        start.setOnTimeChangedListener((v, h, m) -> state.startTime = format(h, m));
        end.setOnTimeChangedListener((v, h, m) -> state.endTime = format(h, m));

        Button apply = findViewById(R.id.apply_but);
        apply.setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra(LedFunActivity.EXTRA_STATE, state.encode());
            setResult(RESULT_OK, data);
            finish();
        });
    }

    private static String format(int h, int m) {
        return String.format(Locale.US, "%02d:%02d", h, m);
    }

    @SuppressWarnings("deprecation")
    private static void init(TimePicker picker, String hhmm) {
        int h = 0, m = 0;
        try {
            String[] p = hhmm.trim().split(":");
            h = Integer.parseInt(p[0]);
            m = Integer.parseInt(p[1]);
        } catch (RuntimeException ignored) { /* keep 00:00 */ }
        picker.setIs24HourView(Boolean.TRUE);
        if (Build.VERSION.SDK_INT >= 23) {
            picker.setHour(h);
            picker.setMinute(m);
        } else {
            picker.setCurrentHour(h);
            picker.setCurrentMinute(m);
        }
    }
}
