package com.example.lanmouse;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity implements UdpMouseClient.Listener {
    private static final String PREFS = "lan_mouse";
    private static final int COLOR_TEXT = Color.rgb(235, 238, 244);
    private static final int COLOR_MUTED = Color.rgb(155, 163, 176);
    private static final int COLOR_ACCENT = Color.rgb(76, 214, 170);
    private static final int COLOR_ERROR = Color.rgb(255, 112, 112);

    private SharedPreferences preferences;
    private UdpMouseClient client;
    private TouchpadView touchpadView;
    private EditText hostInput;
    private EditText portInput;
    private EditText tokenInput;
    private TextView statusText;
    private TextView sensitivityText;
    private SeekBar sensitivitySeekBar;
    private Button discoverButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(createContentView());
        client = new UdpMouseClient(this);
        restoreSettings();
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackgroundColor(Color.rgb(17, 19, 24));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                view.setPadding(
                        dp(16) + insets.getSystemWindowInsetLeft(),
                        dp(14) + insets.getSystemWindowInsetTop(),
                        dp(16) + insets.getSystemWindowInsetRight(),
                        dp(14) + insets.getSystemWindowInsetBottom());
                return insets;
            }
        });

        TextView title = new TextView(this);
        title.setText("LanMouse");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(28);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("局域网 Android 触控板");
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 0, 0, dp(12));
        root.addView(subtitle);

        LinearLayout hostPortRow = new LinearLayout(this);
        hostPortRow.setOrientation(LinearLayout.HORIZONTAL);

        hostInput = createInput("Windows 局域网 IP", InputType.TYPE_CLASS_TEXT);
        hostInput.setSingleLine(true);
        hostInput.setHint("例如 192.168.1.100");
        hostPortRow.addView(hostInput, new LinearLayout.LayoutParams(0, dp(52), 1f));

        portInput = createInput("端口", InputType.TYPE_CLASS_NUMBER);
        portInput.setSingleLine(true);
        LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(dp(92), dp(52));
        portParams.setMargins(dp(10), 0, 0, 0);
        hostPortRow.addView(portInput, portParams);
        root.addView(hostPortRow);

        tokenInput = createInput("认证令牌", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        tokenInput.setSingleLine(true);
        tokenInput.setHint("默认 lanmouse");
        LinearLayout.LayoutParams tokenParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        tokenParams.setMargins(0, dp(8), 0, 0);
        root.addView(tokenInput, tokenParams);

        LinearLayout sensitivityHeader = new LinearLayout(this);
        sensitivityHeader.setOrientation(LinearLayout.HORIZONTAL);
        sensitivityHeader.setGravity(Gravity.CENTER_VERTICAL);
        sensitivityHeader.setPadding(0, dp(8), 0, 0);

        TextView sensitivityLabel = new TextView(this);
        sensitivityLabel.setText("指针灵敏度");
        sensitivityLabel.setTextColor(COLOR_MUTED);
        sensitivityLabel.setTextSize(13);
        sensitivityHeader.addView(sensitivityLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sensitivityText = new TextView(this);
        sensitivityText.setTextColor(COLOR_TEXT);
        sensitivityText.setTextSize(13);
        sensitivityHeader.addView(sensitivityText);
        root.addView(sensitivityHeader);

        sensitivitySeekBar = new SeekBar(this);
        sensitivitySeekBar.setMax(300);
        sensitivitySeekBar.setProgress(150);
        sensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = Math.max(0.25f, progress / 100f);
                touchpadView.setSensitivity(value);
                sensitivityText.setText(String.format(java.util.Locale.US, "%.2fx", value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        root.addView(sensitivitySeekBar);

        discoverButton = new Button(this);
        discoverButton.setText("搜索 Windows 设备");
        discoverButton.setAllCaps(false);
        discoverButton.setTextSize(15);
        discoverButton.setTextColor(COLOR_TEXT);
        discoverButton.setBackgroundColor(Color.rgb(45, 50, 60));
        LinearLayout.LayoutParams discoverParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        discoverParams.setMargins(0, 0, 0, dp(8));
        root.addView(discoverButton, discoverParams);
        discoverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                discoverWindows();
            }
        });
        Button applyButton = new Button(this);
        applyButton.setText("应用并启用触控板");
        applyButton.setAllCaps(false);
        applyButton.setTextSize(16);
        applyButton.setTextColor(Color.rgb(10, 32, 25));
        applyButton.setBackgroundColor(COLOR_ACCENT);
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        applyParams.setMargins(0, 0, 0, dp(8));
        root.addView(applyButton, applyParams);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applySettings();
            }
        });

        statusText = new TextView(this);
        statusText.setText("请先填写 Windows 端显示的 IP，然后点击“应用并启用触控板”。");
        statusText.setTextColor(COLOR_MUTED);
        statusText.setTextSize(13);
        statusText.setPadding(dp(4), 0, dp(4), dp(8));
        root.addView(statusText);

        touchpadView = new TouchpadView(this);
        LinearLayout.LayoutParams touchpadParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(touchpadView, touchpadParams);
        touchpadView.setEnabled(false);
        touchpadView.setListener(new TouchpadView.Listener() {
            @Override
            public void onMove(float dx, float dy) {
                client.move(dx, dy);
            }

            @Override
            public void onButton(String button, String action) {
                client.button(button, action);
            }

            @Override
            public void onScroll(int delta) {
                client.scroll(delta);
            }
        });

        TextView hint = new TextView(this);
        hint.setText("单指移动 · 轻触左键 · 长按拖动 · 双指轻触右键 · 双指上下滚动");
        hint.setTextColor(COLOR_MUTED);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(4), dp(8), dp(4), 0);
        root.addView(hint);

        return root;
    }

    private EditText createInput(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        input.setTextSize(15);
        input.setPadding(dp(12), 0, dp(12), 0);
        return input;
    }

    private void restoreSettings() {
        String host = preferences.getString("host", "");
        int port = preferences.getInt("port", 8765);
        String token = preferences.getString("token", "lanmouse");
        int sensitivity = preferences.getInt("sensitivity", 150);

        hostInput.setText(host);
        portInput.setText(String.valueOf(port));
        tokenInput.setText(token);
        sensitivitySeekBar.setProgress(sensitivity);
        sensitivityText.setText(String.format(java.util.Locale.US, "%.2fx", sensitivity / 100f));
        touchpadView.setSensitivity(sensitivity / 100f);

        if (host.length() > 0) {
            applySettings();
        }
    }

    private void discoverWindows() {
        if (discoverButton == null) {
            return;
        }

        discoverButton.setEnabled(false);
        showStatus("正在搜索同一局域网内的 Windows 设备...", false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final UdpMouseClient.DiscoveryResult result = client.discover(1800);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        discoverButton.setEnabled(true);
                        if (result == null) {
                            showStatus("没有发现服务端。请确认 Windows 端已启动、双方在同一局域网，且防火墙允许 UDP 8765。", true);
                            return;
                        }

                        hostInput.setText(result.host);
                        portInput.setText(String.valueOf(result.port));
                        showStatus("已发现 " + result.name + "，请确认令牌后点击“应用并启用触控板”。", false);
                    }
                });
            }
        }, "LanMouse-Discovery").start();
    }
    private void applySettings() {
        String host = hostInput.getText().toString().trim();
        String portText = portInput.getText().toString().trim();
        String token = tokenInput.getText().toString();

        if (host.length() == 0) {
            showStatus("请填写 Windows 端的局域网 IP。", true);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            showStatus("端口格式不正确。", true);
            return;
        }

        if (port < 1 || port > 65535) {
            showStatus("端口必须在 1-65535 之间。", true);
            return;
        }

        if (token.length() == 0) {
            showStatus("令牌不能为空；Windows 端若使用 --no-auth，可随便填写。", true);
            return;
        }

        // 地址解析、发送和等待 pong 都在客户端后台线程完成：
        // 在主线程做 socket 操作会抛 NetworkOnMainThreadException（消息为 null）。
        touchpadView.setEnabled(false);
        saveSettings(host, port, token);
        showStatus("正在连接 " + host + ":" + port + " ...", false);
        client.configure(host, port, token);
    }

    @Override
    public void onReady(final String host, final int port) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                touchpadView.setEnabled(true);
                showStatus("已连接 " + host + ":" + port + "（UDP），可以开始使用触控板。", false);
            }
        });
    }

    private void saveSettings(String host, int port, String token) {
        preferences.edit()
                .putString("host", host)
                .putInt("port", port)
                .putString("token", token)
                .putInt("sensitivity", Math.round(touchpadView.getSensitivity() * 100f))
                .apply();
    }

    private void showStatus(String message, boolean error) {
        statusText.setText(message);
        statusText.setTextColor(error ? COLOR_ERROR : COLOR_MUTED);
    }

    @Override
    public void onError(final String message) {
        // 失败可能来自后台发送线程，状态栏只能在主线程更新。
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                touchpadView.setEnabled(client != null && client.isReady());
                showStatus(message, true);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (touchpadView != null && touchpadView.isEnabled() && hostInput != null) {
            String host = hostInput.getText().toString().trim();
            String portText = portInput.getText().toString().trim();
            try {
                saveSettings(host, Integer.parseInt(portText), tokenInput.getText().toString());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (client != null) {
            client.close();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}