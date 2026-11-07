package com.gigabyte.rgbfusion;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.gigabyte.rgbfusion.core.LedFunction;
import com.gigabyte.rgbfusion.core.LedState;
import com.gigabyte.rgbfusion.core.Protocol;
import com.gigabyte.rgbfusion.core.RgbFusionClient;
import com.gigabyte.rgbfusion.net.WifiNet;
import com.gigabyte.rgbfusion.ui.UiUtil;

import java.util.ArrayList;
import java.util.List;

/** The "Mode" grid + on/off switch (LedFunActivity in the original). */
public class LedFunActivity extends AppCompatActivity {

    public static final String EXTRA_FUN_TYPE = "Fun_Type";
    public static final String EXTRA_STATE = "Easy_Xml_Obj";
    public static final String EXTRA_GROUP = "Group_LED_Str_Array";

    private LedState state;
    private final List<LedState> group = new ArrayList<>();
    private boolean groupMode;

    private int orgMode;
    private int orgPattern;

    private GridView grid;
    private ImageView offSwitch;
    private TileAdapter tiles;

    private RgbFusionClient client;
    private AlertDialog busy;

    private ActivityResultLauncher<Intent> colorLauncher;
    private ActivityResultLauncher<Intent> otherLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        groupMode = in.getIntExtra(EXTRA_FUN_TYPE, 0) == 1;
        state = LedState.decode(in.getStringExtra(EXTRA_STATE));
        if (groupMode) {
            ArrayList<String> raw = in.getStringArrayListExtra(EXTRA_GROUP);
            if (raw != null) for (String r : raw) {
                LedState s = LedState.decode(r);
                if (s != null) group.add(s);
            }
            if (group.isEmpty()) state = null;
        } else if (state != null && state.stateUrl == null) {
            state = null;
        }
        if (state == null) {          // same as the original: nothing to show without a state
            finish();
            return;
        }

        client = new RgbFusionClient(new WifiNet(this).connectionFactory(), Protocol.HTTP_TIMEOUT_MS);
        setContentView(R.layout.activity_led_fun);

        colorLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                r -> onChildResult(r, true));
        otherLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                r -> onChildResult(r, false));

        grid = findViewById(R.id.fungrid);
        tiles = new TileAdapter(this);
        grid.setAdapter(tiles);
        grid.setOnItemClickListener((p, v, position, id) -> onTile(LedFunction.values()[position]));

        offSwitch = findViewById(R.id.off_switch);
        offSwitch.setOnClickListener(v -> onSwitch());
        if (state.pattern == LedState.PATTERN_OFF) showOff(true);
    }

    @Override
    protected void onDestroy() {
        UiUtil.dismiss(busy);
        super.onDestroy();
    }

    // ------------------------------------------------------------- clicks

    private void onTile(LedFunction f) {
        if (!f.isEnabled(state.supportFlag)) return;
        orgPattern = state.pattern;
        f.applyTo(state);
        tiles.notifyDataSetChanged();
        switch (f.next) {
            case UPLOAD:
                upload();
                break;
            case COLOR:
                launch(colorLauncher, ColorSelActivity.class);
                break;
            case OTHER:
                launch(otherLauncher, OtherActivity.class);
                break;
        }
    }

    private void launch(ActivityResultLauncher<Intent> launcher, Class<?> target) {
        Intent i = new Intent(this, target);
        i.putExtra(EXTRA_STATE, state.encode());
        launcher.launch(i);
    }

    private void onSwitch() {
        if (state.pattern != LedState.PATTERN_OFF) {
            orgMode = state.mode;
            orgPattern = state.pattern;
            state.mode = 0;
            state.pattern = LedState.PATTERN_OFF;
            showOff(true);
        } else {
            state.mode = orgMode;
            state.pattern = orgPattern;
            showOff(false);
        }
        tiles.notifyDataSetChanged();
        upload();
    }

    private void showOff(boolean off) {
        offSwitch.setImageResource(off ? R.drawable.sw_off : R.drawable.sw_on_r);
        grid.setAlpha(off ? 0.5f : 1f);
        grid.setEnabled(!off);
    }

    /**
     * Result of the colour / "Intelligent" screens. Like the original, any returned data
     * (Apply or Back) is applied and uploaded; no data means the screen was cancelled and
     * the previous pattern is restored.
     */
    private void onChildResult(ActivityResult r, boolean fromColor) {
        Intent data = r.getData();
        LedState child = data == null ? null : LedState.decode(data.getStringExtra(EXTRA_STATE));
        if (child == null) {
            state.pattern = orgPattern;
            tiles.notifyDataSetChanged();
            return;
        }
        if (fromColor) {
            state.color = child.color;
            state.speed = child.speed;
            state.brightness = child.brightness;
        } else {
            state.otherMode = child.otherMode;
            state.startTime = child.startTime;
            state.endTime = child.endTime;
        }
        upload();
    }

    // ------------------------------------------------------------- upload

    private void upload() {
        UiUtil.dismiss(busy);
        busy = UiUtil.progress(this, getString(R.string.app_name), getString(R.string.PGS_TITLE));
        final LedState snapshot = state.copy();
        final List<LedState> targets = new ArrayList<>(group);
        UiUtil.IO.execute(() -> {
            try {
                if (!groupMode) {
                    client.upload(snapshot);
                } else {
                    for (LedState member : targets) {
                        LedState s = snapshot.copy();
                        s.stateUrl = member.stateUrl;
                        try { client.upload(s); } catch (java.io.IOException ignored) { /* next device */ }
                    }
                }
            } catch (java.io.IOException ignored) {
                // the original ignored the upload result as well
            }
            UiUtil.MAIN.post(() -> UiUtil.dismiss(busy));
        });
    }

    // ------------------------------------------------------------ adapter

    private final class TileAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final Context ctx;

        TileAdapter(Context c) { ctx = c; inflater = LayoutInflater.from(c); }

        @Override public int getCount() { return LedFunction.values().length; }
        @Override public Object getItem(int i) { return LedFunction.values()[i]; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView : inflater.inflate(R.layout.grid_item, parent, false);
            LedFunction f = LedFunction.values()[position];
            ImageView icon = v.findViewById(R.id.pattern_icon);
            TextView label = v.findViewById(R.id.pattern_desp);
            icon.setImageResource(UiUtil.drawable(ctx, f.key));
            label.setText(UiUtil.string(ctx, nameKey(f)));
            if (!f.isEnabled(state.supportFlag)) {
                icon.setImageAlpha(30);
                label.setTextColor(getResources().getColor(R.color.txt_color_d));
                label.setAlpha(0.3f);
            } else if (f.isSelected(state)) {
                icon.setImageAlpha(255);
                label.setTextColor(0xFFFFFFFF);
                label.setAlpha(1f);
            } else {
                icon.setImageAlpha(125);
                label.setTextColor(getResources().getColor(R.color.txt_color_d));
                label.setAlpha(0.5f);
            }
            return v;
        }
    }

    static String nameKey(LedFunction f) {
        switch (f) {
            case BREATH: return "Pattern_Breath";
            case BEAT: return "Pattern_Beat";
            case AUTO: return "Pattern_Auto";
            case STILL: return "Pattern_Still";
            case FLASH: return "Pattern_Flash";
            case RANDOM: return "Pattern_Random";
            case WAVE: return "Pattern_Wave";
            case INTELLIGENT: return "Pattern_Other";
            case DFLASH: return "Pattern_Dflash";
            case DEMO: return "Pattern_Demo";
            case PROFILE_A: return "Pr1_str";
            case PROFILE_B: return "Pr2_str";
            default: return "Pr3_str";
        }
    }
}
