package com.justtennis.plugin.common.fragment;

import android.annotation.SuppressLint;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.justtennis.plugin.common.manager.ServiceManager;
import com.justtennis.plugin.common.task.UserLoginServiceTask;
import com.justtennis.plugin.common.task.UserLoginTask;
import com.justtennis.plugin.common.tool.FragmentTool;
import com.justtennis.plugin.common.tool.ProgressTool;
import com.justtennis.plugin.fft.R;
import com.justtennis.plugin.fft.databinding.FragmentLoginBinding;
import com.justtennis.plugin.shared.fragment.AppFragment;
import com.justtennis.plugin.shared.manager.NotificationManager;
import com.justtennis.plugin.shared.preference.LoginSharedPref;
import com.justtennis.plugin.shared.preference.ProxySharedPref;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static android.Manifest.permission.READ_CONTACTS;

/**
 * A login screen that offers login via email/password.
 */
public class LoginFragment extends AppFragment implements LoaderCallbacks<Cursor> {

    /**
     * Id to identity READ_CONTACTS permission request.
     */
    private static final int REQUEST_READ_CONTACTS = 0;
    private static final String TAG = LoginFragment.class.getName();

    private Context context;
    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;

    // UI references.
    private AutoCompleteTextView mLoginView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;
    private TextView mResponse;
    private CheckBox mUseProxy;

    private View mProxyForm;
    private EditText mProxyHost;
    private EditText mProxyPort;
    private EditText mProxyLogin;
    private EditText mProxyPassword;
    private Spinner mService;

    private FragmentActivity activity;
    private Bundle bundle;

    public static Fragment newInstance() {
        return new LoginFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentLoginBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_login, container, false);
        activity = getActivity();
        context = activity.getApplicationContext();
        // Set up the login form.
        mLoginView = binding.login;
        populateAutoComplete();

        mPasswordView = binding.password;
        mPasswordView.setOnEditorActionListener((textView, id, keyEvent) -> {
            if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                attemptLogin();
                return true;
            }
            return false;
        });

        Button mEmailSignInButton = binding.emailSignInButton;
        mEmailSignInButton.setOnClickListener(view -> attemptLogin());

        mLoginFormView = binding.loginForm;
        mProgressView = binding.loginProgress;
        mResponse = binding.tvResponse;
        mService = binding.spService;
        mUseProxy = binding.chkUseProxy;

        mProxyForm = binding.proxyForm;
        mProxyHost = binding.proxyHost;
        mProxyPort = binding.proxyPort;
        mProxyLogin = binding.proxyLogin;
        mProxyPassword = binding.proxyPassword;

        initializeForm();
        initializeService();
        bundle = (savedInstanceState != null) ? savedInstanceState : Objects.requireNonNull(activity).getIntent().getExtras();
        if (bundle != null) {
            // Something to do
        }

        LoginSharedPref.cleanSecurity(context);

        hideFab();

        return binding.getRoot();
    }

    private void populateAutoComplete() {
        if (!mayRequestContacts()) {
            return;
        }

        activity.getLoaderManager().initLoader(0, null, this);
    }

    private boolean mayRequestContacts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (Objects.requireNonNull(activity).checkSelfPermission(READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (shouldShowRequestPermissionRationale(READ_CONTACTS)) {
            Snackbar.make(mLoginView, R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, v -> requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS));
        } else {
            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
        }
        return false;
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_READ_CONTACTS) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                populateAutoComplete();
            }
        }
    }

    private void hideFab() {
        FragmentTool.onClickFab(Objects.requireNonNull(activity), null);
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mLoginView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mLoginView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mLoginView.setError(getString(R.string.error_field_required));
            focusView = mLoginView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mLoginView.setError(getString(R.string.error_invalid_email));
            focusView = mLoginView;
            cancel = true;
        } else if (mUseProxy.isChecked()) {
            if (mProxyHost.getText().length()==0) {
                mProxyHost.setError(getString(R.string.error_field_required));
                focusView = mProxyHost;
                cancel = true;
            } else if (mProxyPort.getText().length()==0) {
                mProxyPort.setError(getString(R.string.error_field_required));
                focusView = mProxyPort;
                cancel = true;
            } else if (mProxyLogin.getText().length()==0) {
                mProxyLogin.setError(getString(R.string.error_field_required));
                focusView = mProxyLogin;
                cancel = true;
            } else if (mProxyPassword.getText().length()==0) {
                mProxyPassword.setError(getString(R.string.error_field_required));
                focusView = mProxyPassword;
                cancel = true;
            }
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            closeKeyboard();

            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);

            ServiceManager.getInstance().setService(mService.getSelectedItemPosition());
            mAuthTask = new MyUserLoginTask(email, password, mService.getSelectedItem().toString());
            mAuthTask.execute((Void) null);
        }
    }

    private void closeKeyboard() {
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mLoginView.getWindowToken(), 0);
    }

    private boolean isEmailValid(String email) {
        return email.length() > 4;
    }

    private boolean isPasswordValid(String password) {
        return password.length() > 4;
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    private void showProgress(final boolean show) {
        ProgressTool.showProgress(activity, mLoginFormView, mProgressView, show);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(getContext(),
                // Retrieve data rows for the device user's 'profile' contact.
                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY), ProfileQuery.PROJECTION,

                // Select only email addresses.
                ContactsContract.Contacts.Data.MIMETYPE +
                        " = ?", new String[]{ContactsContract.CommonDataKinds.Email
                .CONTENT_ITEM_TYPE},

                // Show primary email addresses first. Note that there won't be
                // a primary email address if the user hasn't specified one.
                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        List<String> emails = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            emails.add(cursor.getString(ProfileQuery.ADDRESS));
            cursor.moveToNext();
        }

        addEmailsToAutoComplete(emails);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        // Nothing to do
    }

    private void addEmailsToAutoComplete(List<String> emailAddressCollection) {
        //Create adapter to tell the AutoCompleteTextView what to show in its dropdown list.
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(getContext(),
                        android.R.layout.simple_dropdown_item_1line, emailAddressCollection);

        mLoginView.setAdapter(adapter);
    }

    private void initializeForm() {
        mUseProxy.setOnCheckedChangeListener((v, check) -> mProxyForm.setVisibility(check ? View.VISIBLE : View.GONE));
        mUseProxy.setChecked(ProxySharedPref.getUseProxy(context));
        mProxyHost.setText(ProxySharedPref.getSite(context));
        mProxyPort.setText(String.format(Locale.FRANCE, "%1$d",ProxySharedPref.getPort(context)));
        mProxyLogin.setText(ProxySharedPref.getUser(context));
        mProxyPassword.setText(ProxySharedPref.getPwd(context));

        String login = LoginSharedPref.getLogin(context);
        if (login == null || login.isEmpty()) {
            mLoginView.setText("");
        } else {
            mLoginView.setText(login);
        }

        String paswd = LoginSharedPref.getPwd(context);
        if (paswd == null || paswd.isEmpty()) {
            mPasswordView.setText("");
        } else {
            mPasswordView.setText(paswd);
        }
    }

    private void initializeService() {
        ArrayAdapter<String> adpService = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, ServiceManager.getServiceLabel());
        mService.setAdapter(adpService);

//        mService.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                ServiceManager.getInstance().setService(position);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {
//            }
//        });
    }

    private interface ProfileQuery {
        String[] PROJECTION = {
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY,
        };

        int ADDRESS = 0;
        int IS_PRIMARY = 1;
    }

    @SuppressLint("StaticFieldLeak")
    private class MyUserLoginTask extends UserLoginServiceTask {

        MyUserLoginTask(String email, String password, String label) {
            super(context, email, password, label);
        }

        @Override
        protected void onPreExecute() {
            mResponse.setText("");
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(final Boolean connected) {
            mAuthTask = null;

            if (!connected) {
                showProgress(false);
                mPasswordView.setError(getString(R.string.error_incorrect_password));
                mPasswordView.requestFocus();
            } else {
                ServiceManager.getInstance().initializeFragment(activity, bundle);
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            NotificationManager.onTaskProcessUpdate(activity, values);
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }

        @Override
        protected void saveData(Context context) {
            super.saveData(context);
            ProxySharedPref.setUseProxy(context, mUseProxy.isChecked());
            if (mUseProxy.isChecked()) {
                ProxySharedPref.setSite(context, mProxyHost.getText().toString());
                ProxySharedPref.setPort(context, Integer.parseInt(mProxyPort.getText().toString()));
                ProxySharedPref.setUser(context, mProxyLogin.getText().toString());
                ProxySharedPref.setPwd(context, mProxyPassword.getText().toString());
            }
        }
    }
}