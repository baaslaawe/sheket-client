package com.mukera.sheket.client;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.analytics.HitBuilders;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.mukera.sheket.client.controller.CompanyUtil;
import com.mukera.sheket.client.controller.admin.BranchFragment;
import com.mukera.sheket.client.controller.admin.EmployeesFragment;
import com.mukera.sheket.client.controller.admin.TransactionHistoryFragment;
import com.mukera.sheket.client.controller.importer.ImporterActivity;
import com.mukera.sheket.client.controller.items.AllItemsFragment;
import com.mukera.sheket.client.controller.items.BranchItemFragment;
import com.mukera.sheket.client.controller.navigation.BaseNavigation;
import com.mukera.sheket.client.controller.navigation.LeftNavigation;
import com.mukera.sheket.client.controller.navigation.RightNavigation;
import com.mukera.sheket.client.controller.user.IdEncoderUtil;
import com.mukera.sheket.client.controller.user.ProfileFragment;
import com.mukera.sheket.client.data.AndroidDatabaseManager;
import com.mukera.sheket.client.data.SheketContract.CompanyEntry;
import com.mukera.sheket.client.models.SBranch;
import com.mukera.sheket.client.models.SCompany;
import com.mukera.sheket.client.models.SPermission;
import com.mukera.sheket.client.network.CompanyAuth;
import com.mukera.sheket.client.network.CompanyID;
import com.mukera.sheket.client.network.EditCompanyRequest;
import com.mukera.sheket.client.network.EmptyResponse;
import com.mukera.sheket.client.network.SheketAuth;
import com.mukera.sheket.client.network.SheketServiceGrpc;
import com.mukera.sheket.client.services.SheketSyncService;
import com.mukera.sheket.client.utils.ConfigData;
import com.mukera.sheket.client.utils.PrefUtil;
import com.mukera.sheket.client.utils.TextWatcherAdapter;

import java.util.Locale;
import java.util.Map;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import mehdi.sakout.fancybuttons.FancyButton;

public class MainActivity extends AppCompatActivity implements
        BaseNavigation.NavigationCallback,
        ActivityCompat.OnRequestPermissionsResultCallback {

    public static final int REQUEST_READ_PHONE_STATE = 1;

    private ProgressDialog mSyncingProgress = null;

    private LeftNavigation mLeftNav;
    private RightNavigation mRightNav;

    private SlidingMenu mNavigation;

    /**
     * If we needed to allow permission for READ_PHONE_STATE, we need to continue with what
     * we were doing after we get the permission. In our context, it is for showing {@code PaymentDialog}
     * for a company. So we need to hold a reference to the company that triggered the
     * permission request so we can show the {@code PaymentDialog} afterwards.
     * <p>
     * NOTE: we could launch the {@code PaymentDialog} in {@code onRequestPermissionsResult()}, but
     * that causes an exception saying the activity isn't Resumed yet. So, we set {@code mDidSelectCompanyBeforeRequest}
     * to true if we need to get the READ_PHONE_STATE and show the {@code PaymentDialog} afterwards.
     */
    private SCompany mPermissionRequestedCompany = null;
    private boolean mDidGrantReadPhoneStatePermission = false;
    private boolean mSyncRequiredPhoneStatePermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requireLogin();

        setUserLanguage();
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        getSupportActionBar().setHomeAsUpIndicator(R.mipmap.ic_app_icon);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        initSlidingMenuDrawer();

        syncIfIsLoginFirstTime();
        setTitle(R.string.app_name);

        if (savedInstanceState == null) {
            openNavDrawer();
        }
    }

    void setUserLanguage() {
        Locale locale = new Locale(PrefUtil.getUserLanguageLocale(this));
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        getResources().updateConfiguration(
                config, getResources().getDisplayMetrics());
    }

    void initSlidingMenuDrawer() {
        mNavigation = new SlidingMenu(this);
        mNavigation.setMode(SlidingMenu.LEFT_RIGHT);
        mNavigation.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
        mNavigation.setFadeDegree(0.35f);
        mNavigation.attachToActivity(this, SlidingMenu.SLIDING_CONTENT);

        LayoutInflater inflater = LayoutInflater.from(this);

        mLeftNav = new LeftNavigation();
        mLeftNav.setUpNavigation(this, inflater.inflate(R.layout.nav_layout_left, null));

        mRightNav = new RightNavigation();
        mRightNav.setUpNavigation(this, inflater.inflate(R.layout.nav_layout_right, null));

        mNavigation.setMenu(mLeftNav.getRootView());
        mNavigation.setSecondaryMenu(mRightNav.getRootView());

        int width = getResources().getDimensionPixelSize(R.dimen.navdrawer_width);
        mNavigation.setBehindWidth(width);
    }

    /**
     * Sync if we are loggin in, so the user can see his companies right away.
     * Only sync if you have not set any company, we don't want to be here all day!!
     */
    void syncIfIsLoginFirstTime() {
        if (PrefUtil.getShouldSyncOnLogin(this) &&
                !PrefUtil.isCompanySet(this)) {
            Intent intent = new Intent(this, SheketSyncService.class);
            startService(intent);
        }
    }

    void requireLogin() {
        if (!PrefUtil.isUserSet(this)) {
            finish();

            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        // If there are fragments, do what is normally done
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            super.onBackPressed();
            return;
        }

        if (isNavDrawerClosed()) {
            openNavDrawer();
        } else {
            super.onBackPressed();
        }
    }

    protected boolean isNavDrawerOpen() {
        return mNavigation.isMenuShowing() || mNavigation.isSecondaryMenuShowing();
    }

    protected boolean isNavDrawerClosed() {
        return !isNavDrawerOpen();
    }

    protected void closeNavDrawer() {
        mNavigation.showContent(true);
    }

    protected void openNavDrawer() {
        /**
         * If we are launching for the first time open either of the navigation drawers.
         * If we don't have any companies, open the left side. Open the right otherwise.
         */
        if (!PrefUtil.isCompanySet(this)) {
            mNavigation.showMenu();
        } else {
            mNavigation.showSecondaryMenu();
        }
    }

    private void toggleDrawerState() {
        if (isNavDrawerOpen())
            closeNavDrawer();
        else
            openNavDrawer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.main_menu_right_nav:
                toggleDrawerState();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    void emptyBackStack() {
        FragmentManager fm = getSupportFragmentManager();
        for (int i = 0; i < fm.getBackStackEntryCount(); ++i) {
            fm.popBackStack();
        }
    }

    void replaceMainFragment(Fragment fragment, boolean add_to_back_stack) {
        removeCustomActionBarViews();

        emptyBackStack();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction().
                replace(R.id.main_fragment_container, fragment);
        if (add_to_back_stack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    void removeCustomActionBarViews() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null && (actionBar.getCustomView() != null))
            actionBar.getCustomView().setVisibility(View.GONE);
        invalidateOptionsMenu();
    }

    @Override
    public void onCompanySelected(final SCompany company) {
        if (company.payment_state != CompanyEntry.PAYMENT_VALID) {

            // there is a bug in android M, declaring the permission in the manifest isn't enough
            // see: http://stackoverflow.com/a/38782876/5753416
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);

                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                    PaymentDialog.newInstance(company).show(getSupportFragmentManager(), null);
                } else {
                    // get a hold of the company so we may continue from here if we are granted permission
                    mPermissionRequestedCompany = company;
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE);
                }
            } else {
                PaymentDialog.newInstance(company).show(getSupportFragmentManager(), null);
            }
            return;
        }

        // check if we've already selected the company previously
        if (PrefUtil.getCurrentCompanyId(this) == company.company_id) {
            // if we are not a MANAGER, then close the nav drawer
            if (!SPermission.getUserPermission(this).hasManagerAccess()) {
                closeNavDrawer();
                return;
            }

            View view = getLayoutInflater().inflate(R.layout.dialog_company_profile, null);

            TextView company_name = (TextView) view.findViewById(R.id.dialog_show_company_profile_company_name);
            TextView payment_number = (TextView) view.findViewById(R.id.dialog_show_company_profile_payment_number);

            company_name.setText(company.name);
            payment_number.setText(
                    IdEncoderUtil.encodeAndDelimitId(company.company_id, IdEncoderUtil.ID_TYPE_COMPANY)
            );

            ImageButton editNameBtn = (ImageButton) view.findViewById(R.id.dialog_show_company_profile_btn_edit_name);
            editNameBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    displayEditCompanyNameDialog(company);
                }
            });

            AlertDialog.Builder builder = new AlertDialog.Builder(this).
                    setView(view);

            final AlertDialog dialog = builder.create();

            FancyButton importBtn = (FancyButton) view.findViewById(R.id.dialog_show_company_profile_btn_import);
            importBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    onNavigationOptionSelected(BaseNavigation.StaticNavigationOptions.OPTION_IMPORT);
                }
            });

            dialog.show();
            return;
        }

        CompanyUtil.switchCurrentCompanyInWorkerThread(this, company, new CompanyUtil.StateSwitchedListener() {
            @Override
            public void runAfterSwitchCompleted() {
                SheketTracker.setScreenName(MainActivity.this, SheketTracker.SCREEN_NAME_MAIN);
                SheketTracker.sendTrackingData(MainActivity.this,
                        new HitBuilders.EventBuilder().
                                setCategory(SheketTracker.CATEGORY_MAIN_CONFIGURATION).
                                setAction("company changed").
                                build());


                LocalBroadcastManager.getInstance(MainActivity.this).
                        sendBroadcast(new Intent(SheketBroadcast.ACTION_COMPANY_SWITCH));
            }
        });
    }

    void displayEditCompanyNameDialog(final SCompany company) {
        final EditText editText = new EditText(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(this).
                setTitle(R.string.dialog_edit_company_profile_title).
                setView(editText).
                setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, int which) {
                        final String new_name = editText.getText().toString().trim();

                        SheketTracker.setScreenName(MainActivity.this, SheketTracker.SCREEN_NAME_MAIN);
                        SheketTracker.sendTrackingData(MainActivity.this,
                                new HitBuilders.EventBuilder().
                                        setCategory(SheketTracker.CATEGORY_MAIN_CONFIGURATION).
                                        setAction("change company name selected").
                                        build());

                        final ProgressDialog progress = ProgressDialog.show(
                                MainActivity.this,
                                getString(R.string.dialog_edit_company_profile_progress_title),
                                getString(R.string.dialog_edit_company_profile_progress_body),
                                true);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final Pair<Boolean, String> result = updateCompanyName(company, new_name);
                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        progress.dismiss();
                                        dialog.dismiss();

                                        Map<String, String> trackingData;
                                        if (result.first == Boolean.TRUE) {
                                            trackingData = new HitBuilders.EventBuilder().
                                                    setCategory(SheketTracker.CATEGORY_MAIN_CONFIGURATION).
                                                    setAction("company name change successful").
                                                    build();
                                            new AlertDialog.Builder(MainActivity.this).
                                                    setIcon(android.R.drawable.ic_dialog_info).
                                                    setMessage(R.string.dialog_edit_company_profile_result_success).
                                                    show();
                                        } else {
                                            trackingData = new HitBuilders.EventBuilder().
                                                    setCategory(SheketTracker.CATEGORY_MAIN_CONFIGURATION).
                                                    setAction("change company name error").
                                                    setLabel(result.second).
                                                    build();

                                            new AlertDialog.Builder(MainActivity.this).
                                                    setIcon(android.R.drawable.ic_dialog_alert).
                                                    setTitle(R.string.dialog_edit_company_profile_result_error).
                                                    setMessage(result.second).
                                                    show();
                                        }

                                        SheketTracker.setScreenName(MainActivity.this, SheketTracker.SCREEN_NAME_MAIN);
                                        SheketTracker.sendTrackingData(MainActivity.this, trackingData);
                                    }
                                });
                            }
                        }).start();
                    }
                }).
                setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        // start things off with the current name, the "OK" button should be invisible
        editText.setText(company.name);

        final AlertDialog dialog = builder.create();

        editText.addTextChangedListener(new TextWatcherAdapter() {
            @Override
            public void afterTextChanged(Editable s) {
                String new_name = s.toString().trim();

                // only enable editing if there is a "non-empty name" and
                // it is different from the current one
                boolean show_ok_btn = !new_name.isEmpty() &&
                        !new_name.equals(company.name);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).
                        setVisibility(show_ok_btn ? View.VISIBLE : View.GONE);
            }
        });

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                // initially don't show the "Ok" button b/c the name hasn't changed
                ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
            }
        });

        dialog.show();
    }

    Pair<Boolean, String> updateCompanyName(SCompany company, String new_name) {
        CompanyAuth companyAuth = CompanyAuth.
                newBuilder().
                setCompanyId(
                        CompanyID.newBuilder().setCompanyId(
                                PrefUtil.getCurrentCompanyId(this)
                        ).build()
                ).
                setSheketAuth(
                        SheketAuth.newBuilder().setLoginCookie(
                                PrefUtil.getLoginCookie(this)
                        ).build()
                ).build();
        final EditCompanyRequest request = EditCompanyRequest.newBuilder().
                setCompanyAuth(companyAuth).
                setNewName(new_name).
                build();
        try {
            // only try to ask server to update name if company wasn't local

            if (!PrefUtil.isCompanyLocallyCreated(MainActivity.this, company.company_id)) {
                new SheketGRPCCall<EmptyResponse>().runBlockingCall(
                        new SheketGRPCCall.GRPCCallable<EmptyResponse>() {
                            @Override
                            public EmptyResponse runGRPCCall() throws Exception {
                                ManagedChannel managedChannel = ManagedChannelBuilder.
                                        forAddress(ConfigData.getServerIP(MainActivity.this), ConfigData.getServerPort()).
                                        usePlaintext(true).
                                        build();

                                SheketServiceGrpc.SheketServiceBlockingStub blockingStub =
                                        SheketServiceGrpc.newBlockingStub(managedChannel);
                                return blockingStub.editCompany(request);
                            }
                        }
                );
            }

            // if we've reached this point without throwing an exception, then it means success
            ContentValues values = company.toContentValues();
            values.remove(CompanyEntry.COLUMN_COMPANY_ID);
            values.put(CompanyEntry.COLUMN_NAME, new_name);

            int num_updated = getContentResolver().
                    update(CompanyEntry.CONTENT_URI, values,
                            CompanyEntry._full(CompanyEntry.COLUMN_COMPANY_ID) + " = ?",
                            new String[]{String.valueOf(company.company_id)});

            if (num_updated == 1)
                return new Pair<>(Boolean.TRUE, null);
            else
                return new Pair<>(Boolean.FALSE, "Error updating company name in local storage");
        } catch (SheketGRPCCall.SheketInvalidLoginException e) {
            LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(
                    new Intent(SheketBroadcast.ACTION_SYNC_INVALID_LOGIN_CREDENTIALS));
            return new Pair<>(Boolean.FALSE, e.getMessage());
        } catch (SheketGRPCCall.SheketInternetException e) {
            return new Pair<>(Boolean.FALSE, "Internet problem");
        } catch (SheketGRPCCall.SheketException e) {
            return new Pair<>(Boolean.FALSE, e.getMessage());
        }
    }

    @Override
    public void onBranchSelected(final SBranch branch) {
        replaceMainFragment(BranchItemFragment.newInstance(branch),
                false);
        closeNavDrawer();
    }

    @Override
    public void onNavigationOptionSelected(int item) {
        closeNavDrawer();
        removeCustomActionBarViews();

        boolean change_title = true;

        String new_screen = "";
        switch (item) {
            case BaseNavigation.StaticNavigationOptions.OPTION_ITEM_LIST:
                new_screen = "Items Fragment";
                replaceMainFragment(new AllItemsFragment(), false);
                break;
            case BaseNavigation.StaticNavigationOptions.OPTION_IMPORT: {
                change_title = false;
                startActivity(new Intent(MainActivity.this, ImporterActivity.class));
                break;
            }
            case BaseNavigation.StaticNavigationOptions.OPTION_BRANCHES:
                new_screen = "Branches Fragment";
                replaceMainFragment(new BranchFragment(), false);
                break;
            case BaseNavigation.StaticNavigationOptions.OPTION_EMPLOYEES:
                new_screen = "Employees Fragment";
                replaceMainFragment(new EmployeesFragment(), false);
                break;
            case BaseNavigation.StaticNavigationOptions.OPTION_SYNC: {
                change_title = false;

                Map<String, String> trackingData = new HitBuilders.EventBuilder().
                        setCategory(SheketTracker.CATEGORY_MAIN_NAVIGATION).
                        setAction("sync started").
                        build();

                boolean have_read_phone_state_permission = true;

                // there is a bug in android M, declaring the permission in the manifest isn't enough
                // see: http://stackoverflow.com/a/38782876/5753416
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);

                    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                        have_read_phone_state_permission = false;

                        trackingData = new HitBuilders.EventBuilder().
                                setCategory(SheketTracker.CATEGORY_MAIN_NAVIGATION).
                                setAction("READ_PHONE_STATE required for sync").
                                build();
                    }
                }

                if (have_read_phone_state_permission) {
                    Intent intent = new Intent(this, SheketSyncService.class);
                    startService(intent);
                } else {
                    mSyncRequiredPhoneStatePermission = true;
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE);
                }

                SheketTracker.setScreenName(MainActivity.this, SheketTracker.SCREEN_NAME_MAIN);
                SheketTracker.sendTrackingData(this, trackingData);

                break;
            }
            case BaseNavigation.StaticNavigationOptions.OPTION_TRANSACTIONS:
                new_screen = "Transaction Summary";
                replaceMainFragment(new TransactionHistoryFragment(), false);
                break;
            case BaseNavigation.StaticNavigationOptions.OPTION_DEBUG:
                startActivity(new Intent(this, AndroidDatabaseManager.class));
                change_title = false;
                break;
            case BaseNavigation.StaticNavigationOptions.OPTION_USER_PROFILE:
                new_screen = "User Profile";
                replaceMainFragment(new ProfileFragment(), false);
                break;
            case BaseNavigation.StaticNavigationOptions.OPTION_LOG_OUT:
                SheketTracker.setScreenName(MainActivity.this, SheketTracker.SCREEN_NAME_MAIN);
                SheketTracker.sendTrackingData(this,
                        new HitBuilders.EventBuilder().
                                setCategory(SheketTracker.CATEGORY_MAIN_NAVIGATION).
                                setAction("logout selected").
                                build());
                logoutUser();
                change_title = false;
                break;
        }

        if (change_title) {
            SheketTracker.setScreenName(MainActivity.this, SheketTracker.SCREEN_NAME_MAIN);
            SheketTracker.sendTrackingData(this,
                    new HitBuilders.EventBuilder().
                            setCategory(SheketTracker.CATEGORY_MAIN_NAVIGATION).
                            setAction("screen changed").
                            setLabel(new_screen).
                            build());
            setTitle(
                    BaseNavigation.StaticNavigationOptions.getOptionString(item));
        }
    }

    void logoutUser() {
        final ProgressDialog logoutDialog = ProgressDialog.show(this, "Logging out", "Please wait...", true);

        CompanyUtil.logoutOfCompany(this,
                new CompanyUtil.LogoutFinishListener() {
                    @Override
                    public void runAfterLogout() {
                        logoutDialog.dismiss();
                        requireLogin();
                    }

                    @Override
                    public void logoutError(String msg) {
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_READ_PHONE_STATE: {
                if ((grantResults.length > 0) &&
                        (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    mDidGrantReadPhoneStatePermission = true;
                    /*
                    // TODO: we've been granted permission, go do something with it
                    // FIXME: but trying to show a dialog *here* causes an exception saying
                    // java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState

                    if (mPermissionRequestedCompany != null)
                        PaymentDialog.newInstance(mPermissionRequestedCompany).show(getSupportFragmentManager(), null);
                    */
                }

                break;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();

        filter.addAction(SheketBroadcast.ACTION_SYNC_STARTED);
        filter.addAction(SheketBroadcast.ACTION_SYNC_SUCCESS);
        filter.addAction(SheketBroadcast.ACTION_SYNC_INVALID_LOGIN_CREDENTIALS);
        filter.addAction(SheketBroadcast.ACTION_SYNC_SERVER_ERROR);
        filter.addAction(SheketBroadcast.ACTION_SYNC_INTERNET_ERROR);
        filter.addAction(SheketBroadcast.ACTION_SYNC_GENERAL_ERROR);
        filter.addAction(SheketBroadcast.ACTION_COMPANY_SWITCH);
        filter.addAction(SheketBroadcast.ACTION_COMPANY_RESET);
        filter.addAction(SheketBroadcast.ACTION_COMPANY_PERMISSION_CHANGE);
        filter.addAction(SheketBroadcast.ACTION_PAYMENT_REQUIRED);
        filter.addAction(SheketBroadcast.ACTION_USER_CONFIG_CHANGE);


        LocalBroadcastManager.getInstance(this).
                registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).
                unregisterReceiver(mReceiver);
    }


    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (mDidGrantReadPhoneStatePermission) {
            /**
             * <p>RESET it. For subsequent actions that require the permission, we won't come here
             * because checking if the permission is GRANTED will return TRUE and we can do the action
             * in the "original" place.</p>
             *
             * <p>NOTE: If the user goes to "System Settings" and removes the permission,
             * we don't want continue to assume we've been given the permission, so we SHOULD
             * reset it for that also.</p>
             */
            mDidGrantReadPhoneStatePermission = false;

            if (mPermissionRequestedCompany != null) {
                // we requested permission for a company and it was granted, show the dialog now
                PaymentDialog.newInstance(mPermissionRequestedCompany).show(getSupportFragmentManager(), null);

                // RESET
                mPermissionRequestedCompany = null;
            } else if (mSyncRequiredPhoneStatePermission) {
                mSyncRequiredPhoneStatePermission = false;

                Intent intent = new Intent(this, SheketSyncService.class);
                startService(intent);
            }
        }
    }

    void dismissSyncDialog() {
        if (mSyncingProgress != null) {
            mSyncingProgress.dismiss();
            mSyncingProgress = null;
        }
    }

    void displayConfigurationIfFirstTime() {
        // we've already been here
        if (!PrefUtil.getIsFirstTime(this)) return;
        PrefUtil.setIsFirstTime(this, false);

        LanguageSelectionDialog.displayLanguageConfigurationDialog(this, false, new Runnable() {
            @Override
            public void run() {
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(new Intent(SheketBroadcast.ACTION_USER_CONFIG_CHANGE));
            }
        });
    }


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Activity activity = MainActivity.this;

            String action = intent.getAction();
            String error_extra = intent.getStringExtra(SheketBroadcast.ACTION_SYNC_EXTRA_ERROR_MSG);

            if (action.equals(SheketBroadcast.ACTION_PAYMENT_REQUIRED)) {
                dismissSyncDialog();
                PrefUtil.resetCompanySelection(MainActivity.this);
                restartMainActivity();
            } else if (action.equals(SheketBroadcast.ACTION_COMPANY_SWITCH)) {
                dismissSyncDialog();
                restartMainActivity();
            } else if (action.equals(SheketBroadcast.ACTION_COMPANY_RESET)) {
                dismissSyncDialog();
                PrefUtil.resetCompanySelection(MainActivity.this);
                restartMainActivity();
            } else if (action.equals(SheketBroadcast.ACTION_USER_CONFIG_CHANGE)) {
                dismissSyncDialog();
                restartMainActivity();
            } else if (action.equals(SheketBroadcast.ACTION_COMPANY_PERMISSION_CHANGE)) {
                if (mRightNav != null) {
                    mRightNav.userPermissionChanged();
                }
                if (mLeftNav != null) {
                    mLeftNav.userPermissionChanged();
                }
            } else if (action.equals(SheketBroadcast.ACTION_SYNC_INVALID_LOGIN_CREDENTIALS)) {
                dismissSyncDialog();
                logoutUser();
            } else {
                dismissSyncDialog();
                /**
                 * If we are syncing because we just logged in, we don't want
                 * to display the "sync-progress" dialog
                 */
                if (PrefUtil.getShouldSyncOnLogin(MainActivity.this)) {
                    displayConfigurationIfFirstTime();
                } else {
                    if (action.equals(SheketBroadcast.ACTION_SYNC_STARTED)) {
                        mSyncingProgress = ProgressDialog.show(activity,
                                "Syncing", "Please Wait...", true);
                    } else if (action.equals(SheketBroadcast.ACTION_SYNC_SUCCESS)) {
                        new AlertDialog.Builder(MainActivity.this).
                                setTitle("Success").
                                setMessage("You've synced successfully.").show();

                        openNavDrawer();
                    } else {
                        String err_title = "";
                        if (action.equals(SheketBroadcast.ACTION_SYNC_SERVER_ERROR)) {
                            err_title = "Sync error, Try Again...";
                        } else if (action.equals(SheketBroadcast.ACTION_SYNC_INTERNET_ERROR)) {
                            err_title = "Internet error, Try Again...";
                        } else if (action.equals(SheketBroadcast.ACTION_SYNC_GENERAL_ERROR)) {
                            err_title = "Error, Try Again...";
                        }

                        new AlertDialog.Builder(MainActivity.this).
                                setTitle(err_title).setMessage(error_extra).
                                show();
                    }
                }

                // reset the "login-sync" if we're done with that, that only happens
                // after the start-{success|error}. So wait until {success|error}
                if (!action.equals(SheketBroadcast.ACTION_SYNC_STARTED)) {
                    // reset it so the next sync shows the dialogs
                    PrefUtil.setShouldSyncOnLogin(MainActivity.this, false);
                }
            }
        }
    };

    void restartMainActivity() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {

            @Override
            public void run() {
                /**
                 * IMPORTANT: If we are viewing items in either BranchItems for ItemList
                 * and we try to restart MainActivity, the app will crash 'cause of
                 * a google ExpandableListView bug.
                 * So, we remove the fragments before we restart the activity to
                 * clear any references we have to any ExpandableListViewto any ExpandableListViews.
                 */
                emptyBackStack();

                finish();
                startActivity(getIntent());
                /*
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                } else {
                    recreate();
                }
                */
            }
        }, 100);
    }
}
