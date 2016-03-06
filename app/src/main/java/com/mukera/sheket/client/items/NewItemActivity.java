package com.mukera.sheket.client.items;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.mukera.sheket.client.R;
import com.mukera.sheket.client.contentprovider.SheketContract;
import com.mukera.sheket.client.input.ScannerFragment;
import com.mukera.sheket.client.models.SItem;
import com.mukera.sheket.client.contentprovider.SheketContract.ItemEntry;
import com.squareup.okhttp.internal.framed.FrameReader;

/**
 * Created by gamma on 3/6/16.
 */
public class NewItemActivity extends AppCompatActivity {
    public static final String CATEGORY_ID_KEY = "category_id_key";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_item);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.new_item_container, new NewItemFragment())
                .addToBackStack(null)
                .commit();
    }

    public static class NewItemFragment extends Fragment {
        public static final int SELECTION_NONE = 0;
        public static final int SELECTION_BARCODE = 1;
        public static final int SELECTION_MANUAL = 2;

        private EditText mName, mManualCode, mQty, mLocation;
        private TextView mBarcodeLabel, mCategoryLabel;
        private Spinner mCategorySpinner;
        private ImageButton mBarcodeBtn;

        private Button mCancel, mOk;

        private String mSavedBarcode;

        void setOkButtonStatus() {
            boolean code_entered = !mBarcodeLabel.getText().toString().isEmpty()
                    || !mManualCode.getText().toString().trim().isEmpty();
            mOk.setEnabled(code_entered && !mName.getText().toString().toString().isEmpty());
        }

        double d(String str) {
            return Double.parseDouble(str);
        }

        String td(String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return "0.0";
            }
            return trimmed;
        }

        String t(String str) {
            return str.trim();
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_new_item, container, false);

            TextWatcherAdapter okButtonStatusChecker = new TextWatcherAdapter() {
                @Override
                public void afterTextChanged(Editable s) {
                    setOkButtonStatus();
                }
            };

            mName = (EditText) rootView.findViewById(R.id.edit_text_new_item_name);
            mName.addTextChangedListener(okButtonStatusChecker);

            mManualCode = (EditText) rootView.findViewById(R.id.edit_text_new_item_manual_code);
            mManualCode.addTextChangedListener(okButtonStatusChecker);

            mQty = (EditText) rootView.findViewById(R.id.edit_text_new_item_qty);
            mLocation = (EditText) rootView.findViewById(R.id.edit_text_new_item_location);

            mBarcodeLabel = (TextView) rootView.findViewById(R.id.text_view_new_item_bar_code);
            mCategoryLabel = (TextView) rootView.findViewById(R.id.text_view_new_item_category_label);
            mCategorySpinner = (Spinner) rootView.findViewById(R.id.spinner_new_item_category);

            // TODO: add selector for categories
            mCategoryLabel.setVisibility(View.GONE);
            mCategorySpinner.setVisibility(View.GONE);

            final AppCompatActivity activity = (AppCompatActivity)getActivity();

            mOk = (Button) rootView.findViewById(R.id.btn_new_item_ok);
            mOk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String name = t(mName.getText().toString());
                    final String manual_code = t(mManualCode.getText().toString());
                    final String bar_code = t(mBarcodeLabel.getText().toString());
                    final String location = t(mLocation.getText().toString());
                    final double qty = d(td(mQty.getText().toString()));

                    Thread t = new Thread() {
                        @Override
                        public void run() {
                            int category = SheketContract.CategoryEntry.DEFAULT_CATEGORY_ID;
                            ContentValues values = new ContentValues();
                            values.put(ItemEntry.COLUMN_NAME, name);
                            values.put(ItemEntry.COLUMN_CATEGORY_ID, category);
                            values.put(ItemEntry.COLUMN_BAR_CODE, bar_code);
                            values.put(ItemEntry.COLUMN_MANUAL_CODE, manual_code);
                            values.put(ItemEntry.COLUMN_LOCATION, location);
                            values.put(ItemEntry.COLUMN_QTY_REMAIN, qty);

                            int code_type;
                            if (!manual_code.isEmpty() && !bar_code.isEmpty()) {
                                code_type = ItemEntry.CODE_TYPE_BOTH;
                            } else if (!manual_code.isEmpty()) {
                                code_type = ItemEntry.CODE_TYPE_MANUAL;
                            } else {
                                code_type = ItemEntry.CODE_TYPE_BAR_CODE;
                            }

                            values.put(ItemEntry.COLUMN_CODE_TYPE, code_type);
                            Uri uri = activity.getContentResolver().insert(
                                    ItemEntry.CONTENT_URI, values);
                            long item_id = ContentUris.parseId(uri);

                            final SItem item;

                            if (item_id != -1) {        // success
                                // fetch it out
                                uri = ItemEntry.buildItemUri(item_id);
                                Cursor cursor = activity.getContentResolver().query(uri,
                                        SItem.ITEM_COLUMNS,
                                        null, null, null);
                                if (cursor.moveToFirst())
                                    item = new SItem(cursor);
                            } else {
                                item = null;
                            }
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // TODO: notify that we created successfully
                                    activity.finish();
                                }
                            });
                        }
                    };
                    t.start();
                }
            });
            mCancel = (Button) rootView.findViewById(R.id.btn_new_item_cancel);
            mCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // TODO: notify cancel pressed
                    activity.finish();
                }
            });

            if (mSavedBarcode != null && !mSavedBarcode.isEmpty())
                mBarcodeLabel.setText(mSavedBarcode);
            mBarcodeBtn = (ImageButton) rootView.findViewById(R.id.img_btn_new_item_bar_code);
            mBarcodeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ScannerFragment fragment = new ScannerFragment();
                    fragment.setResultListener(new ScannerFragment.ScanResultListener() {
                        @Override
                        public void resultFound(String result) {
                            result = result.trim();
                            mSavedBarcode = result;
                            setOkButtonStatus();
                            activity.getSupportFragmentManager().popBackStack();
                        }
                    });
                    activity.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.new_item_container, fragment)
                            .addToBackStack(null)
                            .commit();
                }
            });

            // To start things off
            setOkButtonStatus();

            return rootView;
        }

        static class TextWatcherAdapter implements TextWatcher {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) { }
        }

    }

}
