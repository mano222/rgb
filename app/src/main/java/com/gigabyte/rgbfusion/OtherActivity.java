package com.gigabyte.rgbfusion;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.gigabyte.rgbfusion.core.LedState;
import com.gigabyte.rgbfusion.core.OtherMode;
import com.gigabyte.rgbfusion.ui.UiUtil;

/** "Intelligent" pattern: choose what the LEDs react to (OtherActivity in the original). */
public class OtherActivity extends AppCompatActivity {

    /** List order of the original screen. */
    private static final OtherMode[] ORDER = {
            OtherMode.CPU_TEMP, OtherMode.CPU_USAGE, OtherMode.CPU_FAN,
            OtherMode.SCHEDULE, OtherMode.LAN_SPEED, OtherMode.SYSTEM_TEMP };

    private LedState state;
    private TextView desp;
    private ModeAdapter adapter;
    private ActivityResultLauncher<Intent> timeLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = LedState.decode(getIntent().getStringExtra(LedFunActivity.EXTRA_STATE));
        if (state == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_other);

        timeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            Intent data = r.getData();
            if (data == null) return;
            LedState t = LedState.decode(data.getStringExtra(LedFunActivity.EXTRA_STATE));
            if (t != null) {
                state.startTime = t.startTime;
                state.endTime = t.endTime;
            }
        });

        ListView list = findViewById(R.id.other_list);
        adapter = new ModeAdapter(this);
        list.setAdapter(adapter);
        desp = findViewById(R.id.other_desp);
        desp.setText(description());
        list.setOnItemClickListener((p, v, position, id) -> {
            OtherMode m = ORDER[position];
            state.otherMode = m.value;
            desp.setText(description());
            adapter.notifyDataSetChanged();
            if (m == OtherMode.SCHEDULE) {
                Intent i = new Intent(this, TimePickerActivity.class);
                i.putExtra(LedFunActivity.EXTRA_STATE, state.encode());
                timeLauncher.launch(i);
            }
        });

        Button apply = findViewById(R.id.apply_but);
        apply.setOnClickListener(v -> finishWithState());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishWithState(); }
        });
    }

    private String description() {
        OtherMode m = OtherMode.fromValue(state.otherMode);
        return m == null ? "" : UiUtil.string(this, m.stringKey + "_Desp");
    }

    private void finishWithState() {
        Intent data = new Intent();
        data.putExtra(LedFunActivity.EXTRA_STATE, state.encode());
        setResult(RESULT_OK, data);
        finish();
    }

    private final class ModeAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final Context ctx;

        ModeAdapter(Context c) { ctx = c; inflater = LayoutInflater.from(c); }

        @Override public int getCount() { return ORDER.length; }
        @Override public Object getItem(int i) { return ORDER[i]; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView : inflater.inflate(R.layout.other_list_item, parent, false);
            OtherMode m = ORDER[position];
            ((TextView) v.findViewById(R.id.o_text)).setText(UiUtil.string(ctx, m.stringKey + "_str"));
            ((TextView) v.findViewById(R.id.o_text)).setTextColor(m.value == state.otherMode ? Color.RED : Color.WHITE);
            ((ImageView) v.findViewById(R.id.o_icon)).setImageResource(UiUtil.drawable(ctx, m.drawableKey));
            return v;
        }
    }
}
