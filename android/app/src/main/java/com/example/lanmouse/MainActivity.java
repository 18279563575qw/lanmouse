package com.example.lanmouse;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity implements UdpMouseClient.Listener {
    private static final String PREFS = "lan_mouse";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_SENSITIVITY = "sensitivity";
    private static final String KEY_HAPTICS = "haptics_enabled";

    private SharedPreferences preferences;
    private UdpMouseClient client;
    private TouchpadView touchpadView;
    private EditText hostInput;
    private EditText portInput;
    private EditText tokenInput;
    private TextView statusText;
    private View statusDot;
    private TextView sensitivityText;
    private SeekBar sensitivitySeekBar;
    private Button discoverButton;
    private Switch hapticSwitch;
    private boolean hapticsEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        hapticsEnabled = preferences.getBoolean(KEY_HAPTICS, true);

        setContentView(createContentView());
        client = new UdpMouseClient(this, this);
        restoreSettings();
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackgroundColor(color(R.color.background));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                applySystemBarInsets(view, insets);
                return insets;
            }
        });

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(color(R.color.text_primary));
        title.setTextSize(29);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.app_subtitle);
        subtitle.setTextColor(color(R.color.text_secondary));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(1), 0, dp(14));
        root.addView(subtitle);

        ScrollView controlsScroll = new ScrollView(this);
        controlsScroll.setFillViewport(false);
        controlsScroll.setVerticalScrollBarEnabled(false);
        controlsScroll.setClipToPadding(false);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setClipToPadding(false);
        controlsScroll.addView(controls, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout connectionCard = createCard();
        connectionCard.addView(createSectionTitle(R.string.section_connection));

        LinearLayout hostPortRow = new LinearLayout(this);
        hostPortRow.setOrientation(LinearLayout.HORIZONTAL);

        hostInput = createInput(R.string.hint_host, InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_ACTION_NEXT);
        hostPortRow.addView(hostInput, new LinearLayout.LayoutParams(0, dp(52), 1f));

        portInput = createInput(R.string.hint_port, InputType.TYPE_CLASS_NUMBER,
                EditorInfo.IME_ACTION_NEXT);
        LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(dp(88), dp(52));
        portParams.setMargins(dp(10), 0, 0, 0);
        hostPortRow.addView(portInput, portParams);
        connectionCard.addView(hostPortRow, topMarginParams(dp(12)));

        tokenInput = createInput(
                R.string.hint_token,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                EditorInfo.IME_ACTION_DONE);
        connectionCard.addView(tokenInput, topMarginParams(dp(8), dp(52)));

        discoverButton = createButton(R.string.action_discover, false);
        discoverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                discoverWindows();
            }
        });
        connectionCard.addView(discoverButton, topMarginParams(dp(10), dp(46)));

        Button applyButton = createButton(R.string.action_apply, true);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applySettings();
            }
        });
        connectionCard.addView(applyButton, topMarginParams(dp(8), dp(50)));
        controls.addView(connectionCard);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(13), dp(11), dp(13), dp(11));
        statusCard.setBackground(roundedBackground(
                color(R.color.surface),
                color(R.color.outline),
                dp(8)));

        statusDot = new View(this);
        statusDot.setBackground(ovalBackground(color(R.color.accent)));
        statusCard.addView(statusDot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        statusText = new TextView(this);
        statusText.setText(R.string.status_initial);
        statusText.setTextColor(color(R.color.text_secondary));
        statusText.setTextSize(13);
        statusText.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statusParams.setMargins(dp(10), 0, 0, 0);
        statusCard.addView(statusText, statusParams);
        controls.addView(statusCard, topMarginParams(dp(10)));

        LinearLayout touchpadSettingsCard = createCard();
        touchpadSettingsCard.addView(createSectionTitle(R.string.section_touchpad));

        LinearLayout sensitivityHeader = new LinearLayout(this);
        sensitivityHeader.setOrientation(LinearLayout.HORIZONTAL);
        sensitivityHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView sensitivityLabel = new TextView(this);
        sensitivityLabel.setText(R.string.label_sensitivity);
        sensitivityLabel.setTextColor(color(R.color.text_secondary));
        sensitivityLabel.setTextSize(14);
        sensitivityHeader.addView(
                sensitivityLabel,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sensitivityText = new TextView(this);
        sensitivityText.setTextColor(color(R.color.accent));
        sensitivityText.setTextSize(14);
        sensitivityText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sensitivityText.setGravity(Gravity.CENTER);
        sensitivityText.setPadding(dp(9), dp(4), dp(9), dp(4));
        sensitivityText.setBackground(roundedBackground(
                colorWithAlpha(R.color.accent, 0.12f),
                colorWithAlpha(R.color.accent, 0.28f),
                dp(7)));
        sensitivityHeader.addView(sensitivityText);
        touchpadSettingsCard.addView(sensitivityHeader, topMarginParams(dp(12)));

        sensitivitySeekBar = new SeekBar(this);
        sensitivitySeekBar.setMax(300);
        sensitivitySeekBar.setProgress(150);
        int accent = color(R.color.accent);
        sensitivitySeekBar.setProgressTintList(ColorStateList.valueOf(accent));
        sensitivitySeekBar.setThumbTintList(ColorStateList.valueOf(accent));
        sensitivitySeekBar.setProgressBackgroundTintList(
                ColorStateList.valueOf(color(R.color.outline)));
        sensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = Math.max(0.25f, progress / 100f);
                touchpadView.setSensitivity(value);
                sensitivityText.setText(getString(R.string.sensitivity_value, value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        touchpadSettingsCard.addView(sensitivitySeekBar);

        View divider = new View(this);
        divider.setBackgroundColor(color(R.color.outline));
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.setMargins(0, dp(6), 0, dp(4));
        touchpadSettingsCard.addView(divider, dividerParams);

        LinearLayout hapticRow = new LinearLayout(this);
        hapticRow.setOrientation(LinearLayout.HORIZONTAL);
        hapticRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView hapticLabel = new TextView(this);
        hapticLabel.setText(R.string.label_haptics);
        hapticLabel.setTextColor(color(R.color.text_primary));
        hapticLabel.setTextSize(14);
        hapticRow.addView(
                hapticLabel,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        hapticSwitch = new Switch(this);
        hapticSwitch.setShowText(false);
        hapticSwitch.setChecked(hapticsEnabled);
        hapticSwitch.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{accent, color(R.color.text_secondary)}));
        hapticSwitch.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{colorWithAlpha(R.color.accent, 0.52f), color(R.color.outline)}));
        hapticSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            hapticsEnabled = isChecked;
            if (touchpadView != null) {
                touchpadView.setHapticFeedbackEnabled(isChecked);
            }
            preferences.edit().putBoolean(KEY_HAPTICS, isChecked).apply();
            if (isChecked) {
                buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        });
        hapticRow.addView(hapticSwitch);
        touchpadSettingsCard.addView(hapticRow, topMarginParams(dp(2)));
        controls.addView(touchpadSettingsCard, topMarginParams(dp(10)));

        LinearLayout.LayoutParams controlsParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.56f);
        root.addView(controlsScroll, controlsParams);

        touchpadView = new TouchpadView(this);
        touchpadView.setContentDescription(getString(R.string.touchpad_content_description));
        touchpadView.setMinimumHeight(dp(220));
        touchpadView.setHapticFeedbackEnabled(hapticsEnabled);
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
        LinearLayout.LayoutParams touchpadParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.44f);
        touchpadParams.setMargins(0, dp(10), 0, 0);
        root.addView(touchpadView, touchpadParams);
        touchpadView.setEnabled(false);

        TextView hint = new TextView(this);
        hint.setText(R.string.gesture_hint);
        hint.setTextColor(color(R.color.text_secondary));
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(4), dp(9), dp(4), 0);
        root.addView(hint);

        return root;
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(14));
        card.setBackground(roundedBackground(
                color(R.color.surface),
                color(R.color.outline),
                dp(8)));
        card.setElevation(dp(1));
        return card;
    }

    private TextView createSectionTitle(int textRes) {
        TextView title = new TextView(this);
        title.setText(textRes);
        title.setTextColor(color(R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return title;
    }

    private EditText createInput(int hintRes, int inputType, int imeAction) {
        EditText input = new EditText(this);
        input.setHint(hintRes);
        input.setContentDescription(getString(hintRes));
        input.setTextColor(color(R.color.text_primary));
        input.setHintTextColor(color(R.color.text_secondary));
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setImeOptions(imeAction);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setHighlightColor(colorWithAlpha(R.color.accent, 0.26f));
        input.setBackground(createInputBackground(false));
        input.setOnFocusChangeListener((view, hasFocus) ->
                view.setBackground(createInputBackground(hasFocus)));
        return input;
    }

    private Drawable createInputBackground(boolean focused) {
        GradientDrawable drawable = roundedBackground(
                color(R.color.surface_elevated),
                focused ? color(R.color.outline_focused) : color(R.color.outline),
                dp(focused ? 2 : 1));
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private Button createButton(int textRes, boolean primary) {
        Button button = new Button(this);
        button.setText(textRes);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(primary ? color(R.color.accent_on) : color(R.color.text_primary));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setStateListAnimator(null);
        button.setElevation(primary ? dp(1) : 0);
        button.setBackground(createRippleBackground(
                primary ? color(R.color.accent) : color(R.color.surface_elevated),
                primary ? color(R.color.ripple_light) : color(R.color.ripple_accent),
                primary ? 0 : color(R.color.outline),
                dp(8)));
        return button;
    }

    private Drawable createRippleBackground(
            int fillColor, int rippleColor, int strokeColor, int radius) {
        GradientDrawable shape = roundedBackground(fillColor, strokeColor, strokeColor == 0 ? 0 : dp(1));
        shape.setCornerRadius(radius);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, null);
    }

    private GradientDrawable roundedBackground(int fillColor, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(8));
        if (strokeColor != 0 && strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private Drawable ovalBackground(int fillColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        return drawable;
    }

    private LinearLayout.LayoutParams topMarginParams(int topMargin) {
        return topMarginParams(topMargin, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMarginParams(int topMargin, int height) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private void restoreSettings() {
        String host = preferences.getString(KEY_HOST, "");
        int port = preferences.getInt(KEY_PORT, 8765);
        String token = preferences.getString(KEY_TOKEN, "lanmouse");
        int sensitivity = preferences.getInt(KEY_SENSITIVITY, 150);

        hostInput.setText(host);
        portInput.setText(String.valueOf(port));
        tokenInput.setText(token);
        sensitivitySeekBar.setProgress(sensitivity);

        float sensitivityValue = sensitivity / 100f;
        touchpadView.setSensitivity(sensitivityValue);
        sensitivityText.setText(getString(R.string.sensitivity_value, sensitivityValue));

        if (host.length() > 0) {
            applySettings();
        }
    }

    private void discoverWindows() {
        if (discoverButton == null) {
            return;
        }

        setDiscoverButtonEnabled(false);
        showStatus(getString(R.string.status_searching), false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                int preferredPort = 8765;
                try {
                    preferredPort = Integer.parseInt(portInput.getText().toString().trim());
                } catch (NumberFormatException ignored) {
                }
                final UdpMouseClient.DiscoveryResult result = client.discover(
                        2200,
                        preferredPort,
                        hostInput.getText().toString().trim());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        setDiscoverButtonEnabled(true);
                        if (result == null) {
                            showStatus(getString(R.string.status_not_found), true);
                            return;
                        }

                        hostInput.setText(result.host);
                        portInput.setText(String.valueOf(result.port));
                        showStatus(getString(R.string.status_discovered, result.name), false);
                    }
                });
            }
        }, "LanMouse-Discovery").start();
    }

    private void setDiscoverButtonEnabled(boolean enabled) {
        discoverButton.setEnabled(enabled);
        discoverButton.setAlpha(enabled ? 1f : 0.45f);
    }

    private void applySettings() {
        String host = hostInput.getText().toString().trim();
        String portText = portInput.getText().toString().trim();
        String token = tokenInput.getText().toString();

        if (host.length() == 0) {
            showStatus(getString(R.string.error_host_required), true);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            showStatus(getString(R.string.error_port_format), true);
            return;
        }

        if (port < 1 || port > 65535) {
            showStatus(getString(R.string.error_port_range), true);
            return;
        }

        if (token.length() == 0) {
            showStatus(getString(R.string.error_token_required), true);
            return;
        }

        touchpadView.setEnabled(false);
        saveSettings(host, port, token);
        showStatus(getString(R.string.status_connecting, host, port), false);
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
                showStatus(getString(R.string.status_connected, host, port), false);
            }
        });
    }

    private void saveSettings(String host, int port, String token) {
        preferences.edit()
                .putString(KEY_HOST, host)
                .putInt(KEY_PORT, port)
                .putString(KEY_TOKEN, token)
                .putInt(KEY_SENSITIVITY, Math.round(touchpadView.getSensitivity() * 100f))
                .putBoolean(KEY_HAPTICS, hapticsEnabled)
                .apply();
    }

    private void showStatus(String message, boolean error) {
        statusText.setText(message);
        statusText.setTextColor(error ? color(R.color.error) : color(R.color.text_secondary));
        statusDot.setBackground(ovalBackground(error ? color(R.color.error) : color(R.color.accent)));
    }

    @Override
    public void onError(final String message) {
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
        if (touchpadView != null && hostInput != null && portInput != null && tokenInput != null) {
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

    private void applySystemBarInsets(View view, WindowInsets insets) {
        int left;
        int top;
        int right;
        int bottom;

        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            left = bars.left;
            top = bars.top;
            right = bars.right;
            bottom = bars.bottom;
        } else {
            left = insets.getSystemWindowInsetLeft();
            top = insets.getSystemWindowInsetTop();
            right = insets.getSystemWindowInsetRight();
            bottom = insets.getSystemWindowInsetBottom();
        }

        view.setPadding(
                dp(16) + left,
                dp(14) + top,
                dp(16) + right,
                dp(14) + bottom);
    }

    private int color(int colorRes) {
        return getColor(colorRes);
    }

    private int colorWithAlpha(int colorRes, float alpha) {
        int base = color(colorRes);
        return Color.argb(
                Math.round(Color.alpha(base) * alpha),
                Color.red(base),
                Color.green(base),
                Color.blue(base));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
