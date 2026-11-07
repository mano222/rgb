package com.gigabyte.rgbfusion;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.gigabyte.rgbfusion.core.Device;
import com.gigabyte.rgbfusion.core.Discovery;
import com.gigabyte.rgbfusion.core.LedState;
import com.gigabyte.rgbfusion.core.Protocol;
import com.gigabyte.rgbfusion.core.RgbFusionClient;
import com.gigabyte.rgbfusion.net.WifiNet;
import com.gigabyte.rgbfusion.ui.UiUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Device list + discovery + group mode (rgbmain in the original). */
public class MainActivity extends AppCompatActivity {

    private final List<Device> devices = new ArrayList<>();
    /** States downloaded for group mode (the original kept them as <ip>RGB.xml files). */
    private final Map<String, LedState> groupStates = new HashMap<>();

    private ListView listView;
    private ImageButton scanButton;
    private DeviceAdapter adapter;

    private WifiNet net;
    private RgbFusionClient client;
    private Discovery discovery;

    private AlertDialog scanDialog;
    private AlertDialog busyDialog;

    private boolean groupMode;
    private boolean firstStart = true;
    private boolean rescanOnStart;
    private volatile boolean scanSending;
    private LedState groupBase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        net = new WifiNet(this);
        client = new RgbFusionClient(net.connectionFactory(), Protocol.HTTP_TIMEOUT_MS);
        discovery = new Discovery(net.socketFactory(), discoveryGroup(), Protocol.PC_PORT,
                Protocol.MOBILE_PORT, Protocol.RECEIVE_TIMEOUT_MS);

        listView = findViewById(R.id.cmp_list_view);
        scanButton = findViewById(R.id.scan_but);
        adapter = new DeviceAdapter(this);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((p, v, position, id) -> onDeviceClicked(position));
        listView.setOnItemLongClickListener((p, v, position, id) -> {
            startGroupMode(position);
            return true;
        });
        scanButton.setOnClickListener(v -> onScanButton());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (groupMode) {
                    leaveGroupMode();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private static java.net.InetAddress discoveryGroup() {
        try {
            return java.net.InetAddress.getByName(Protocol.MULTICAST_GROUP);
        } catch (java.net.UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onStart() {
        super.onStart();
        net.acquire();
        discovery.startReceiver(new Discovery.Listener() {
            @Override public void onDevice(Device d) { UiUtil.MAIN.post(() -> deviceFound(d)); }
            @Override public void onTimeout() { UiUtil.MAIN.post(MainActivity.this::scanTimedOut); }
            @Override public void onError(Exception e) { UiUtil.MAIN.post(MainActivity.this::scanTimedOut); }
        });
        if (firstStart || rescanOnStart) {
            firstStart = false;
            rescanOnStart = false;
            groupMode = false;
            scanButton.setImageResource(R.drawable.rescan);
            startScan();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        discovery.stopReceiver();
        net.release();
        UiUtil.dismiss(scanDialog);
        UiUtil.dismiss(busyDialog);
    }

    // ------------------------------------------------------------ discovery

    private void startScan() {
        devices.clear();
        groupStates.clear();
        adapter.notifyDataSetChanged();
        listView.setVisibility(View.VISIBLE);
        UiUtil.dismiss(scanDialog);
        scanDialog = UiUtil.progress(this, getString(R.string.PGS_TITLE), getString(R.string.PGS_CONTENT));
        if (scanSending) return;
        scanSending = true;
        Thread t = new Thread(() -> {
            try {
                discovery.sendKeyword();
            } catch (IOException e) {
                UiUtil.MAIN.post(this::scanTimedOut);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                scanSending = false;
            }
        }, "rgbfusion-discovery-tx");
        t.setDaemon(true);
        t.start();
    }

    private void deviceFound(Device d) {
        if (devices.contains(d)) return;
        devices.add(d);
        UiUtil.dismiss(scanDialog);
        listView.setVisibility(View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void scanTimedOut() {
        if (scanDialog != null && scanDialog.isShowing()) {
            UiUtil.dismiss(scanDialog);
            if (devices.isEmpty()) listView.setVisibility(View.INVISIBLE);
        }
    }

    private void onScanButton() {
        if (!groupMode) {
            startScan();
            return;
        }
        // In group mode the button acts as "Set": open the mode screen for the ticked devices.
        ArrayList<String> members = new ArrayList<>();
        for (Device d : devices) {
            if (d.selected && d.selectable) {
                LedState s = groupStates.get(d.address);
                if (s != null) members.add(s.encode());
            }
        }
        if (members.isEmpty() || groupBase == null) {
            UiUtil.alert(this, R.string.SelGroup_errmsg2);
            return;
        }
        rescanOnStart = true;
        Intent i = new Intent(this, LedFunActivity.class);
        i.putExtra(LedFunActivity.EXTRA_FUN_TYPE, 1);
        i.putExtra(LedFunActivity.EXTRA_STATE, groupBase.encode());
        i.putStringArrayListExtra(LedFunActivity.EXTRA_GROUP, members);
        startActivity(i);
    }

    // --------------------------------------------------------- single device

    private void onDeviceClicked(int position) {
        if (position < 0 || position >= devices.size()) return;
        Device d = devices.get(position);
        if (groupMode) {
            if (d.selectable) {
                d.selected = !d.selected;
                adapter.notifyDataSetChanged();
            } else {
                UiUtil.alert(this, R.string.SelGroup_errmsg);
            }
            return;
        }
        UiUtil.dismiss(busyDialog);
        busyDialog = UiUtil.progress(this, getString(R.string.app_name), getString(R.string.PGS_TITLE));
        final String address = d.address;
        UiUtil.IO.execute(() -> {
            LedState state = null;
            try {
                state = client.download(address);
            } catch (IOException ignored) { /* reported below */ }
            final LedState result = state;
            UiUtil.MAIN.post(() -> {
                UiUtil.dismiss(busyDialog);
                if (result == null) UiUtil.alert(this, R.string.DL_ErrMsg);
                else askPasswordThenOpen(result);
            });
        });
    }

    private void askPasswordThenOpen(final LedState state) {
        if (!Protocol.needsPassword(state.password)) {
            openLedFun(state);
            return;
        }
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(true);
        new AlertDialog.Builder(this, R.style.AppDialog)
                .setTitle(R.string.Pwd_Msg)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dlg, w) -> {
                    if (state.password.equals(input.getText().toString())) openLedFun(state);
                    else UiUtil.alert(this, R.string.Pwd_ErrMsg);
                })
                .show();
    }

    private void openLedFun(LedState state) {
        rescanOnStart = true;
        Intent i = new Intent(this, LedFunActivity.class);
        i.putExtra(LedFunActivity.EXTRA_FUN_TYPE, 0);
        i.putExtra(LedFunActivity.EXTRA_STATE, state.encode());
        startActivity(i);
    }

    // ------------------------------------------------------------ group mode

    private void startGroupMode(final int position) {
        if (position < 0 || position >= devices.size()) return;
        UiUtil.dismiss(busyDialog);
        busyDialog = UiUtil.progress(this, getString(R.string.PGS_TITLE), getString(R.string.PGS_Groupmsg));
        final List<Device> snapshot = new ArrayList<>(devices);
        UiUtil.IO.execute(() -> {
            final Map<String, LedState> states = new HashMap<>();
            LedState base = null;
            try {
                base = downloadOnly(snapshot.get(position).address);
                states.put(snapshot.get(position).address, base);
                for (int i = 0; i < snapshot.size(); i++) {
                    if (i == position) continue;
                    try {
                        states.put(snapshot.get(i).address, downloadOnly(snapshot.get(i).address));
                    } catch (IOException unreachable) { /* shown as not selectable */ }
                }
            } catch (IOException e) {
                base = null;
            }
            final LedState b = base;
            UiUtil.MAIN.post(() -> {
                UiUtil.dismiss(busyDialog);
                if (b == null) {
                    leaveGroupMode();
                    UiUtil.alert(this, R.string.DL_ErrMsg);
                    return;
                }
                groupBase = b;
                groupStates.clear();
                groupStates.putAll(states);
                for (int i = 0; i < devices.size() && i < snapshot.size(); i++) {
                    Device d = devices.get(i);
                    LedState s = groupStates.get(d.address);
                    if (i == position) {
                        d.selected = true;
                        d.selectable = true;
                        d.pattern = b.pattern;
                    } else if (s != null && s.supportFlag == b.supportFlag) {
                        d.selected = false;
                        d.selectable = true;
                        d.pattern = s.pattern;
                    } else {
                        d.selected = false;
                        d.selectable = false;
                        d.pattern = -1;
                    }
                }
                groupMode = true;
                scanButton.setImageResource(R.drawable.set);
                adapter.notifyDataSetChanged();
            });
        });
    }

    /**
     * Original Start_Download_Only: for Music(2), Random(5) and Intelligent(10) the state is
     * posted straight back to the device after reading it.
     */
    private LedState downloadOnly(String address) throws IOException {
        LedState s = client.download(address);
        if (s.pattern == 2 || s.pattern == 5 || s.pattern == 10) client.upload(s);
        return s;
    }

    private void leaveGroupMode() {
        groupMode = false;
        groupBase = null;
        scanButton.setImageResource(R.drawable.rescan);
        for (Device d : devices) {
            d.selectable = false;
            d.selected = false;
            d.pattern = -1;
        }
        adapter.notifyDataSetChanged();
    }

    // --------------------------------------------------------------- adapter

    private final class DeviceAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        DeviceAdapter(Context c) { inflater = LayoutInflater.from(c); }

        @Override public int getCount() { return devices.size(); }
        @Override public Object getItem(int i) { return devices.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView : inflater.inflate(R.layout.cmp_list_item, parent, false);
            Device d = devices.get(position);
            ((TextView) v.findViewById(R.id.cmp_title_txt)).setText(d.title);
            ((TextView) v.findViewById(R.id.cmp_ip_txt)).setText(d.address);
            CheckBox box = v.findViewById(R.id.group_sel);
            box.setVisibility(groupMode && d.selectable ? View.VISIBLE : View.INVISIBLE);
            box.setChecked(d.selected);
            box.setClickable(false);
            return v;
        }
    }
}
