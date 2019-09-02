package com.studio764.digisketch;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Image;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.core.DbxApiException;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.users.FullAccount;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.api.services.drive.DriveScopes;

public class MainActivity extends AppCompatActivity {

    private int REQUEST_CODE_SIGN_IN = 100;

    // Connection
    boolean isConnected;

    // Google Drive
    private GoogleSignInAccount google_account;
    private GoogleSignInClient google_client;

    // Dropbox
    private boolean dropbox_authenticating = false;
    private FullAccount dropbox_account;
    private String dropbox_appKey = "yr0vsv7wjiujk5a";
    private DbxClientV2 dropbox_client;
    private String dropbox_authToken = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.Button_GoogleDrive).setOnClickListener(view -> drive_btnPress());
        findViewById(R.id.Button_Dropbox).setOnClickListener(view -> dropbox_btnPress());
        findViewById(R.id.Button_Camera).setVisibility(View.INVISIBLE);
        findViewById(R.id.Button_GoogleDrive).setVisibility(View.INVISIBLE);
        findViewById(R.id.Button_Dropbox).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d("drive_test", "on result is called");
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == REQUEST_CODE_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                google_account = task.getResult();
            }
        }

        updateAccountStatus();
    }

    @Override
    public void onResume() {
        super.onResume();

        checkInternet();
        drive_requestSilentSignIn();
        dropbox_onResumeHandler();
    }

    public void openCameraActivity(View view) {
        Intent intent = new Intent(this, Camera.class);
        ActivityOptionsCompat options = ActivityOptionsCompat.
                makeSceneTransitionAnimation(this, findViewById(R.id.Button_Camera), "cameraButtonTransition");
        intent.putExtra("googleAccount", google_account);
        intent.putExtra("dropboxAuthToken", dropbox_authToken);
        Log.d("drop_test", "put:" + dropbox_authToken);

        startActivity(intent, options.toBundle());
    }

    private void updateAccountStatus() {
        if (google_account == null)
            ((ImageButton) findViewById(R.id.Button_GoogleDrive)).setImageResource(R.drawable.ic_drive_gray);
        else
            ((ImageButton) findViewById(R.id.Button_GoogleDrive)).setImageResource(R.drawable.ic_drive);

        if (dropbox_account == null)
            ((ImageButton) findViewById(R.id.Button_Dropbox)).setImageResource(R.drawable.ic_dropbox_gray);
        else
            ((ImageButton) findViewById(R.id.Button_Dropbox)).setImageResource(R.drawable.ic_dropbox);

        ImageView btn_cam = findViewById(R.id.Button_Camera);
        btn_cam.setAlpha(0f);
        btn_cam.setVisibility(View.VISIBLE);
        btn_cam.animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null);
        ImageView btn_drive = findViewById(R.id.Button_GoogleDrive);
        btn_drive.setAlpha(0f);
        btn_drive.setVisibility(View.VISIBLE);
        btn_drive.animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null);
        ImageView btn_drop = findViewById(R.id.Button_Dropbox);
        btn_drop.setAlpha(0f);
        btn_drop
                .setVisibility(View.VISIBLE);
        btn_drop.animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null);
    }

    private void checkInternet() {
        ConnectivityManager cm =
                (ConnectivityManager)this.getSystemService(this.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }


    // GOOGLE DRIVE ================================================================================

    private void drive_btnPress() {
        checkInternet();
        if (!isConnected) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            google_account = null;
            ((ImageView) findViewById(R.id.Button_Dropbox)).setImageResource((R.drawable.ic_drive_gray));
            return;
        }
        if (google_account == null)
            drive_requestSignIn();
        else {
            drive_dialogPress();
        }
    }

    private void drive_dialogPress() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_signinstatus);
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
        ((ImageView) dialog.findViewById(R.id.cloud_icon)).setImageResource(R.drawable.ic_drive);
        ((TextView) dialog.findViewById(R.id.emailText)).setText(google_account.getEmail());
        ((Button) dialog.findViewById(R.id.signout_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drive_requestSignOut();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void drive_requestSignIn() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestServerAuthCode("351780386748-rs18p7jdirr03peve8uiu19kp48t08qu.apps.googleusercontent.com")
                        .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                        .build();
        google_client = GoogleSignIn.getClient(this, signInOptions);

        startActivityForResult(google_client.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    }

    private void drive_requestSilentSignIn() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestServerAuthCode("351780386748-rs18p7jdirr03peve8uiu19kp48t08qu.apps.googleusercontent.com")
                        .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                        .build();
        google_client = GoogleSignIn.getClient(this, signInOptions);

        try {
            Task<GoogleSignInAccount> task = google_client.silentSignIn()
                    .addOnCompleteListener(new OnCompleteListener<GoogleSignInAccount>() {
                        @Override
                        public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                            try {
                                google_account = task.getResult();
                                Log.d("drive_test", "silent sign in complete!" + google_account.getEmail());
                            } catch (Exception e) {
                                // User is supposed to be logged out
                            }
                            updateAccountStatus();
                        }
                    });
        } catch (Exception e) {
            Log.d("drive_test", "silent sign in doesnt work");
            // Email not selected first time
            e.printStackTrace();
        }

    }

    private void drive_requestSignOut() {
        try {
            google_client.signOut();
            google_account = null; // TODO is this correct?
        } catch (Exception e) {
            // Cannot sign out
        }

        updateAccountStatus();
    }

    // DROPBOX =====================================================================================

    private void dropbox_btnPress() {
        checkInternet();
        if (!isConnected) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            dropbox_account = null;
            ((ImageView) findViewById(R.id.Button_Dropbox)).setImageResource((R.drawable.ic_dropbox_gray));
            return;
        }
        if (dropbox_account == null)
            dropbox_requestSignIn();
        else {
            dropbox_dialogPress();
        }
    }

    private void dropbox_dialogPress() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_signinstatus);
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
        ((ImageView) dialog.findViewById(R.id.cloud_icon)).setImageResource(R.drawable.ic_dropbox);
        ((TextView) dialog.findViewById(R.id.emailText)).setText(dropbox_account.getEmail());
        ((Button) dialog.findViewById(R.id.signout_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dropbox_requestSignOut();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void dropbox_requestSignIn() {
        dropbox_authenticating = true;
        Auth.startOAuth2Authentication(this, dropbox_appKey);
    }

    private void dropbox_requestSignOut() {
        try {
            dropbox_client.auth().tokenRevoke();
            dropbox_account = null;
            dropbox_authToken = null;
        } catch (DbxApiException e) {
            e.printStackTrace();
        } catch (DbxException e) {
            e.printStackTrace();
        }

        updateAccountStatus();
    }

    private void dropbox_onResumeHandler() {
        if (dropbox_authenticating) {
            String authToken = Auth.getOAuth2Token();

            if (authToken != null) {
                // Store for later use
                SharedPreferences pref = getApplicationContext().getSharedPreferences("MyPref", 0); // 0 - for private mode
                SharedPreferences.Editor editor = pref.edit();
                editor.putString("dropbox_authToken", authToken);
                editor.commit();

                dropbox_silentSignIn(authToken);
            }

            dropbox_authenticating = false;
        }
        else {
            SharedPreferences pref = getApplicationContext().getSharedPreferences("MyPref", 0); // 0 - for private mode
            String authToken = pref.getString("dropbox_authToken", null);

            if (authToken != null)
                dropbox_silentSignIn(authToken);
        }
    }

    private void dropbox_silentSignIn(String authToken) {
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
            DbxRequestConfig config = DbxRequestConfig.newBuilder("yr0vsv7wjiujk5a").build();
            dropbox_client = new DbxClientV2(config, authToken);
            dropbox_authToken = authToken;

            try {
                dropbox_account = dropbox_client.users().getCurrentAccount();
            } catch (DbxApiException e) {
                e.printStackTrace();
            } catch (DbxException e) {
                e.printStackTrace();
            }
        }
    }
}
