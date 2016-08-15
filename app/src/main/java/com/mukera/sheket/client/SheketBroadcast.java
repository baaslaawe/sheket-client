package com.mukera.sheket.client;

/**
 * Created by fuad on 8/4/16.
 */
public class SheketBroadcast {
    public static final String ACTION_SYNC_STARTED = "started";
    public static final String ACTION_SYNC_SUCCESS = "success";

    /**
     * These can hold an extra error string, user {@link #ACTION_SYNC_EXTRA_ERROR_MSG}
     */
    // if we receive this broadcast, we should force the user to logout.
    public static final String ACTION_SYNC_INVALID_LOGIN_CREDENTIALS = "invalid_credentials";
    public static final String ACTION_SYNC_SERVER_ERROR = "server_error";
    public static final String ACTION_SYNC_INTERNET_ERROR = "internet_error";
    public static final String ACTION_SYNC_GENERAL_ERROR = "general_error";

    public static final String ACTION_SYNC_EXTRA_ERROR_MSG = "error_msg";

    public static final String ACTION_LOGIN = "action_login";

    public static final String ACTION_CONFIG_CHANGE = "config_change";

    public static final String ACTION_COMPANY_PERMISSION_CHANGE = "company_permission_change";
}
