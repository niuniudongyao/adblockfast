package com.rocketshipapps.adblockfast;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.rocketshipapps.adblockfast.utils.Rule;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class MainActivity extends AppCompatActivity {

    boolean animating = false;

    ImageButton btnAdblock;
    TextView txtStatus;
    TextView txtTap;

    String packageName;
    String version;

    @Nullable
    Tracker tracker;

    SharedPreferences preferences;

    boolean hasBlockingBrowser = false;
    Intent samsungBrowserIntent;

    private final String RETRIEVED_ACCOUNT_PREF = "retrieved_account";
    private final int REQUEST_PERMISSION_GET_ACCOUNTS = 1;
    private final int REQUEST_CODE_ACCOUNT_INTENT = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CalligraphyConfig.initDefault(
                new CalligraphyConfig.Builder()
                        .setDefaultFontPath("fonts/AvenirNextLTPro-Light.otf")
                        .setFontAttrId(R.attr.fontPath)
                        .build()
        );

        packageName = getApplicationContext().getPackageName();
        version = BuildConfig.VERSION_NAME;

        btnAdblock = findViewById(R.id.btn_adblock);
        txtStatus = findViewById(R.id.txt_status);
        txtTap = findViewById(R.id.txt_tap);

        if (!Rule.exists(this)) {
            Rule.enable(this);
            enableAnimation();
        } else if (Rule.active(this)) {
            enableAnimation();
        } else {
            disableAnimation();
        }

        AdblockfastApplication application = (AdblockfastApplication) getApplication();
        tracker = application.getDefaultTracker();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        samsungBrowserIntent = new Intent();
        samsungBrowserIntent.setAction("com.samsung.android.sbrowser.contentBlocker.ACTION_SETTING");

        checkAccountPermission();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkPlayServices();

        if (tracker != null) {
            tracker.setScreenName("/");
            tracker.send(new HitBuilders.ScreenViewBuilder().build());
        }

        if (preferences.getBoolean(RETRIEVED_ACCOUNT_PREF, false)) {
            checkIfHasBlockingBrowser();
        }

    }

    private void checkIfHasBlockingBrowser() {
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(samsungBrowserIntent, 0);
        if (list.size() > 0) hasBlockingBrowser = true;

        if (!hasBlockingBrowser) {
            showHelpDialog(false);
        } else if (preferences.getBoolean("first_run", true)) {
            showHelpDialog(true);
            preferences.edit().putBoolean("first_run", false).apply();
        }
    }

    private void checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            apiAvailability.makeGooglePlayServicesAvailable(this);
        }
    }

    private void getAccounts() {
        Intent intent = AccountPicker.newChooseAccountIntent(null, null, new String[] {"com.google", "com.google.android.legacyimap"}, false, null, null, null, null);
        startActivityForResult(intent, REQUEST_CODE_ACCOUNT_INTENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_ACCOUNT_INTENT) {
            if (data != null) {
                final String email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

                if (email != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                URL url = new URL(BuildConfig.SUBSCRIBE_URL);
                                HttpURLConnection req = (HttpURLConnection) url.openConnection();
                                req.setRequestMethod("POST");
                                req.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                                req.setRequestProperty("Accept", "application/json");
                                req.setDoOutput(true);
                                req.setDoInput(true);

                                JSONObject params = new JSONObject();
                                params.put("email", email);

                                DataOutputStream os = new DataOutputStream(req.getOutputStream());
                                os.writeBytes(params.toString());

                                os.flush();
                                os.close();

                                req.disconnect();

                                preferences.edit().putBoolean(RETRIEVED_ACCOUNT_PREF, true).apply();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }
            }

            checkIfHasBlockingBrowser();
        }
    }

    //region OnClick

    public void onAdBlockPressed(View v) {
        if (animating) return;

        if (Rule.active(this)) {
            Rule.disable(this);
            disableAnimation();
        } else {
            Rule.enable(this);
            enableAnimation();
        }

        Intent intent = new Intent();
        intent.setAction("com.samsung.android.sbrowser.contentBlocker.ACTION_UPDATE");
        intent.setData(Uri.parse("package:" + packageName));
        sendBroadcast(intent);
    }

    public void onAboutPressed(View v) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.alert_dialog_about);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        ((TextView)dialog.findViewById(R.id.tagline)).setText(Html.fromHtml(getString(R.string.tagline)));

        TextView copyright = dialog.findViewById(R.id.copyright);
        copyright.setText(Html.fromHtml(getString(R.string.copyright)));
        copyright.setMovementMethod(LinkMovementMethod.getInstance());

        dialog.setCancelable(false);
        dialog.show();

        ((TextView) dialog.findViewById(R.id.txt_version)).setText(version);

        dialog.findViewById(R.id.btn_ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    public void onHelpPressed(View v) {
        showHelpDialog(true);
    }

    //endregion

    //region Permissions

    private void checkAccountPermission() {
        if (preferences.getBoolean(RETRIEVED_ACCOUNT_PREF, false)) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_DENIED) {
            getAccounts();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.GET_ACCOUNTS)) {
                showAccountPermissionAlert(
                );
            } else {
                requestPermissions(new String[]{Manifest.permission.GET_ACCOUNTS}, REQUEST_PERMISSION_GET_ACCOUNTS);
            }
        }
    }

    private void showAccountPermissionAlert() {
        new AlertDialog
            .Builder(this)
            .setTitle("Permission needed")
            .setMessage("Get email address")
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[]{Manifest.permission.GET_ACCOUNTS}, REQUEST_PERMISSION_GET_ACCOUNTS);
                    }
                }
            })
            .create()
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_GET_ACCOUNTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Permission Granted!", Toast.LENGTH_SHORT).show();
                getAccounts();
            } else {
                Toast.makeText(MainActivity.this, "Permission Denied!", Toast.LENGTH_SHORT).show();
            }
            getAccounts();
        }
    }

    //endregion

    //region Dialog
    void showHelpDialog(boolean cancelable) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.alert_dialog_help);

        if(dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.setCancelable(false);
        dialog.show();

        TextView summary = dialog.findViewById(R.id.summary);
        TextView details = dialog.findViewById(R.id.details);

        if (hasBlockingBrowser) {
            summary.setText(R.string.settings_summary);
            details.setText(Html.fromHtml(getString(R.string.settings_details)));
            details.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(samsungBrowserIntent);
                }
            });
        } else {
            summary.setText(R.string.install_summary);
            details.setText(Html.fromHtml(getString(R.string.install_details)));
        }
        details.setMovementMethod(LinkMovementMethod.getInstance());
        TextView contact = dialog.findViewById(R.id.contact);
        contact.setText(Html.fromHtml(getString(R.string.contact)));
        contact.setMovementMethod(LinkMovementMethod.getInstance());

        if (cancelable) {
            dialog.findViewById(R.id.btn_ok).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        } else {
            dialog.findViewById(R.id.btn_ok).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
        }
    }
    //endregion

    //region Block Animation

    void disableAnimation() {
        animator(new int[]{
            R.drawable.blocked_0,
            R.drawable.blocked_1,
            R.drawable.blocked_2,
            R.drawable.blocked_3,
            R.drawable.blocked_4,
            R.drawable.blocked_5,
            R.drawable.blocked_6,
            R.drawable.blocked_7,
            R.drawable.blocked_8,
            R.drawable.blocked_9,
            R.drawable.blocked_10,
            R.drawable.blocked_11,
            R.drawable.blocked_12,
            R.drawable.blocked_13,
            R.drawable.blocked_14,
            R.drawable.blocked_15,
            R.drawable.unblocked_0,
            R.drawable.unblocked_1,
            R.drawable.unblocked_2,
            R.drawable.unblocked_3,
            R.drawable.unblocked_4,
            R.drawable.unblocked_5,
            R.drawable.unblocked_6,
            R.drawable.unblocked_7,
            R.drawable.unblocked_8,
            R.drawable.unblocked_9,
            R.drawable.unblocked_10,
            R.drawable.unblocked_11,
            R.drawable.unblocked_12,
            R.drawable.unblocked_13,
            R.drawable.unblocked_14,
            R.drawable.unblocked_15,
            R.drawable.blocked_0,
            R.drawable.blocked_1,
            R.drawable.blocked_2,
            R.drawable.blocked_3,
            R.drawable.blocked_4,
            R.drawable.blocked_5,
            R.drawable.blocked_6,
            R.drawable.blocked_7,
            R.drawable.blocked_8,
            R.drawable.blocked_9,
            R.drawable.blocked_10,
            R.drawable.blocked_11,
            R.drawable.blocked_12,
            R.drawable.blocked_13,
            R.drawable.blocked_14,
            R.drawable.blocked_15
        }, R.string.unblocked_message, R.string.unblocked_hint);
    }

    void enableAnimation() {
        animator(new int[]{
            R.drawable.unblocked_0,
            R.drawable.unblocked_1,
            R.drawable.unblocked_2,
            R.drawable.unblocked_3,
            R.drawable.unblocked_4,
            R.drawable.unblocked_5,
            R.drawable.unblocked_6,
            R.drawable.unblocked_7,
            R.drawable.unblocked_8,
            R.drawable.unblocked_9,
            R.drawable.unblocked_10,
            R.drawable.unblocked_11,
            R.drawable.unblocked_12,
            R.drawable.unblocked_13,
            R.drawable.unblocked_14,
            R.drawable.unblocked_15,
            R.drawable.blocked_0,
            R.drawable.blocked_1,
            R.drawable.blocked_2,
            R.drawable.blocked_3,
            R.drawable.blocked_4,
            R.drawable.blocked_5,
            R.drawable.blocked_6,
            R.drawable.blocked_7,
            R.drawable.blocked_8,
            R.drawable.blocked_9,
            R.drawable.blocked_10,
            R.drawable.blocked_11,
            R.drawable.blocked_12,
            R.drawable.blocked_13,
            R.drawable.blocked_14,
            R.drawable.blocked_15,
            R.drawable.unblocked_0,
            R.drawable.unblocked_1,
            R.drawable.unblocked_2,
            R.drawable.unblocked_3,
            R.drawable.unblocked_4,
            R.drawable.unblocked_5,
            R.drawable.unblocked_6,
            R.drawable.unblocked_7,
            R.drawable.unblocked_8,
            R.drawable.unblocked_9,
            R.drawable.unblocked_10,
            R.drawable.unblocked_11,
            R.drawable.unblocked_12,
            R.drawable.unblocked_13,
            R.drawable.unblocked_14,
            R.drawable.unblocked_15
        }, R.string.blocked_message, R.string.blocked_hint);
    }

    void animator(final int[] res, final int resTxtStatus, final int resTxtTap) {
        animating = true;

        double delay = 62.5;

        for (int i=0; i<res.length; ++i) {
            if (i==0) {
                btnAdblock.setImageResource(res[i]);
            } else {
                Handler handler = new Handler();
                final int finalI = i;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnAdblock.setImageResource(res[finalI]);

                                if (finalI == res.length-1) {
                                    animating = false;
                                    txtStatus.setText(resTxtStatus);
                                    txtTap.setText(resTxtTap);
                                }
                            }
                        });
                    }
                }, Math.round(delay * i));
            }
        }
    }

    //endregion
}
