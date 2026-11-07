package com.gigabyte.rgbfusion;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.gigabyte.rgbfusion.core.LedState;
import com.gigabyte.rgbfusion.ui.ColorWheelView;

/** Colour wheel + speed / brightness sliders (ColorselActivity in the original). */
public class ColorSelActivity extends AppCompatActivity {

    private LedState state;
    private int pickedColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = LedState.decode(getIntent().getStringExtra(LedFunActivity.EXTRA_STATE));
        if (state == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_colorsel);
        pickedColor = (int) state.color;

        ColorWheelView wheel = findViewById(R.id.wheel);
        wheel.setOnColorPicked(argb -> pickedColor = argb);
        wheel.setColor(pickedColor);

        View speedArea = findViewById(R.id.speed_area);
        View briArea = findViewById(R.id.bri_area);
        TextView title = findViewById(R.id.pattern_desp);
        // Which controls each pattern shows (Ini_UI in the original).
        switch (state.pattern) {
            case 0:  title.setText(R.string.Pattern_Still);  briArea.setVisibility(View.VISIBLE); speedArea.setVisibility(View.GONE);    break;
            case 1:  title.setText(R.string.Pattern_Breath); briArea.setVisibility(View.GONE);    speedArea.setVisibility(View.VISIBLE); break;
            case 2:  title.setText(R.string.Pattern_Beat);   briArea.setVisibility(View.GONE);    speedArea.setVisibility(View.GONE);    break;
            case 4:  title.setText(R.string.Pattern_Flash);  briArea.setVisibility(View.VISIBLE); speedArea.setVisibility(View.VISIBLE); break;
            case 11: title.setText(R.string.Pattern_Dflash); briArea.setVisibility(View.GONE);    speedArea.setVisibility(View.GONE);    break;
            default: break;
        }

        SeekBar bri = findViewById(R.id.bri_seek);
        bri.setProgress(state.brightness);
        bri.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { state.brightness = p; }
        });
        SeekBar sp = findViewById(R.id.sp_seek);
        sp.setProgress(state.speed);
        sp.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { state.speed = p; }
        });

        Button apply = findViewById(R.id.apply_but);
        apply.setOnClickListener(v -> {
            state.color = pickedColor & 0xFFFFFFFFL;
            finishWithState();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishWithState(); }
        });
    }

    private void finishWithState() {
        Intent data = new Intent();
        data.putExtra(LedFunActivity.EXTRA_STATE, state.encode());
        setResult(RESULT_OK, data);
        finish();
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) { }
        @Override public void onStopTrackingTouch(SeekBar s) { }
    }
}
