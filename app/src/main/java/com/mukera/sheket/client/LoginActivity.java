package com.mukera.sheket.client;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.analytics.HitBuilders;
import com.mukera.sheket.client.services.AlarmReceiver;
import com.mukera.sheket.client.utils.ConfigData;
import com.mukera.sheket.client.utils.PrefUtil;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;

import mehdi.sakout.fancybuttons.FancyButton;

/**
 * Created by fuad on 8/14/16.
 */
public class LoginActivity extends AppCompatActivity {
    //private LoginButton mFacebookSignInButton;
    private FancyButton mFacebookButton;

    private ProgressDialog mProgress = null;
    private CallbackManager mFacebookCallbackManager;

    public static final OkHttpClient client = new OkHttpClient();

    // used to measure how long it takes to login with facebook
    private long mStartTime = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FacebookSdk.sdkInitialize(getApplicationContext());

        AlarmReceiver.startPeriodicPaymentAlarm(this);

        // the user has logged in, start MainActivity
        if (PrefUtil.isUserSet(this)) {
            SheketTracker.setScreenName(this, SheketTracker.SCREEN_NAME_LOGIN);
            SheketTracker.sendTrackingData(this,
                    new HitBuilders.EventBuilder().
                            setCategory(SheketTracker.CATEGORY_LOGIN).
                            setAction("User already logged in").
                            build());
            // because we've already been logged in, we don't need to sync
            startMainActivity(false);
            return;
        }

        mFacebookCallbackManager = CallbackManager.Factory.create();

        setContentView(R.layout.activity_login);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setHomeAsUpIndicator(R.mipmap.ic_app_icon);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        LoginManager.getInstance().registerCallback(mFacebookCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                if (loginResult.getAccessToken() == null)
                    return;

                mFacebookButton.setVisibility(View.GONE);
                mProgress = ProgressDialog.show(LoginActivity.this,
                        "Logging in", "Please Wait", true);
                mStartTime = System.nanoTime();
                new SignInTask(loginResult.getAccessToken().getToken()).execute();
            }

            @Override
            public void onCancel() {
                SheketTracker.setScreenName(LoginActivity.this, SheketTracker.SCREEN_NAME_LOGIN);
                SheketTracker.sendTrackingData(LoginActivity.this,
                        new HitBuilders.EventBuilder().
                                setCategory(SheketTracker.CATEGORY_LOGIN).
                                setAction("Login cancelled").
                                build());
            }

            @Override
            public void onError(FacebookException error) {
                Toast.makeText(getApplicationContext(), "Login Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                SheketTracker.setScreenName(LoginActivity.this, SheketTracker.SCREEN_NAME_LOGIN);
                SheketTracker.sendTrackingData(LoginActivity.this,
                        new HitBuilders.EventBuilder().
                                setCategory(SheketTracker.CATEGORY_LOGIN).
                                setAction("Facebook login error").
                                setLabel(error.toString()).
                                build());
            }
        });
        mFacebookButton = (FancyButton) findViewById(R.id.facebook_login);
        mFacebookButton.setText("Login with Facebook");
        mFacebookButton.setVisibility(View.VISIBLE);
        mProgress = null;
        setTitle(R.string.app_name);
        mFacebookButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginManager.getInstance().logInWithReadPermissions(LoginActivity.this,
                        Arrays.asList("public_profile"));
            }
        });
    }

    void startMainActivity(boolean sync_on_login) {
        if (sync_on_login)
            PrefUtil.setShouldSyncOnLogin(this, true);

        this.finish();
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mFacebookCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    class SignInTask extends AsyncTask<Void, Void, Boolean> {
        public static final String REQUEST_TOKEN = "token";
        public static final String RESPONSE_USERNAME = "username";
        public static final String RESPONSE_USER_ID = "user_id";

        private String mToken;
        private String errMsg;

        public SignInTask(String token) {
            super();

            mToken = token;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                Context context = LoginActivity.this;

                Request.Builder builder = new Request.Builder();
                builder.url(ConfigData.getAddress(context) + "v1/signin/facebook");
                builder.post(RequestBody.create(MediaType.parse("application/json"),
                        new JSONObject().put(REQUEST_TOKEN, mToken).toString()));
                Response response = client.newCall(builder.build()).execute();
                if (!response.isSuccessful()) {
                    JSONObject err = new JSONObject(response.body().string());
                    errMsg = err.getString(context.getString(R.string.json_err_message));
                    return false;
                }

                String login_cookie =
                        response.header(context.getString(R.string.pref_response_key_cookie));

                JSONObject result = new JSONObject(response.body().string());

                long user_id = result.getLong(RESPONSE_USER_ID);
                String username = result.getString(RESPONSE_USERNAME);

                PrefUtil.setUserName(context, username);
                PrefUtil.setUserId(context, user_id);
                PrefUtil.setLoginCookie(context, login_cookie);
            } catch (JSONException | IOException e) {
                errMsg = e.getMessage();
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (mProgress != null) {
                mProgress.dismiss();
                mProgress = null;
            }

            if (!success) {
                SheketTracker.setScreenName(LoginActivity.this, SheketTracker.SCREEN_NAME_LOGIN);
                SheketTracker.sendTrackingData(LoginActivity.this,
                        new HitBuilders.EventBuilder().
                                setCategory(SheketTracker.CATEGORY_LOGIN).
                                setAction("Login Un-successful").
                                setLabel(errMsg).
                                build());
                // remove any-facebook "logged-in" stuff
                Toast.makeText(LoginActivity.this, errMsg, Toast.LENGTH_LONG).show();
                LoginManager.getInstance().logOut();
                mFacebookButton.setVisibility(View.VISIBLE);
                return;
            }

            long stop_time = System.nanoTime();
            long second_duration = (stop_time - mStartTime) / 1000000000;

            SheketTracker.setScreenName(LoginActivity.this, SheketTracker.SCREEN_NAME_LOGIN);
            SheketTracker.sendTrackingData(LoginActivity.this,
                    new HitBuilders.TimingBuilder().
                            setCategory(SheketTracker.CATEGORY_LOGIN).
                            setValue(second_duration).
                            setLabel("login duration").
                            setVariable("facebook login").
                            build());
            // if all goes well, start main activity
            startMainActivity(true);
        }

        @Override
        protected void onCancelled() {
            LoginManager.getInstance().logOut();
        }
    }
}
