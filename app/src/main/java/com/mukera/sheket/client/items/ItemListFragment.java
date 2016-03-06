package com.mukera.sheket.client.items;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.widget.*;

import com.mukera.sheket.client.MainActivity;
import com.mukera.sheket.client.R;
import com.mukera.sheket.client.b_actions.TransactionActivity;
import com.mukera.sheket.client.contentprovider.SheketContract.*;
import com.mukera.sheket.client.models.SItem;

/**
 * Created by gamma on 3/4/16.
 */
public class ItemListFragment extends Fragment implements LoaderCallbacks<Cursor> {
    final int NEW_ITEM_REQUEST = 1;

    public static final String CATEGORY_ID_KEY = "category_key";
    private int mCategoryId;

    private ListView mItemList;
    private ItemCursorAdapter mItemAdapter;

    public static ItemListFragment newInstance(int category_id) {
        Bundle args = new Bundle();
        args.putInt(CATEGORY_ID_KEY, category_id);

        ItemListFragment fragment = new ItemListFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            Bundle args = getArguments();
            mCategoryId = args.getInt(CATEGORY_ID_KEY);
            getLoaderManager().initLoader(mCategoryId, null, this);
        }
    }

    void startTransactionActivity(int action) {
        Intent intent = new Intent(getActivity(), TransactionActivity.class);
        intent.putExtra(TransactionActivity.LAUNCH_ACTION_KEY, action);
        startActivity(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == NEW_ITEM_REQUEST) {
            // Loader handles the adding of new data, we don't need to request
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_item_list, container, false);

        AppCompatActivity act = (AppCompatActivity) getActivity();
        View v_toolbar = act.findViewById(R.id.toolbar);
        if (v_toolbar != null) {
            Toolbar toolbar = (Toolbar) v_toolbar;
            ImageButton addBtn = (ImageButton) toolbar.findViewById(R.id.toolbar_btn_add);
            addBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), NewItemActivity.class);
                    intent.putExtra(NewItemActivity.CATEGORY_ID_KEY, mCategoryId);
                    startActivityForResult(intent, NEW_ITEM_REQUEST);
                }
            });
        }

        FloatingActionButton buyAction, searchAction, sellAction;

        buyAction = (FloatingActionButton) rootView.findViewById(R.id.float_btn_item_list_buy);
        buyAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTransactionActivity(TransactionActivity.LAUNCH_TYPE_BUY);
            }
        });
        searchAction = (FloatingActionButton) rootView.findViewById(R.id.float_btn_item_list_search);
        searchAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTransactionActivity(TransactionActivity.LAUNCH_TYPE_SEARCH);
            }
        });
        sellAction = (FloatingActionButton) rootView.findViewById(R.id.float_btn_item_list_sell);
        sellAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTransactionActivity(TransactionActivity.LAUNCH_TYPE_SELL);
            }
        });

        mItemList = (ListView) rootView.findViewById(R.id.list_view_item_list);
        mItemAdapter = new ItemCursorAdapter(getActivity());
        mItemList.setAdapter(mItemAdapter);
        mItemList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = mItemAdapter.getCursor();
                if (cursor != null && cursor.moveToPosition(position)) {
                    SItem item = new SItem(cursor);

                    Intent intent = new Intent(getActivity(), ItemDetailActivity.class);

                    intent.putExtra(ItemDetailActivity.ITEM_ID_KEY, item.id);
                    startActivity(intent);
                }
            }
        });

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        getLoaderManager().initLoader(mCategoryId, null, this);
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public Loader onCreateLoader(int id, Bundle args) {
        String sortOrder = ItemEntry._full(ItemEntry._ID) + " ASC";
        Uri uri;

        if (mCategoryId != CategoryEntry.DEFAULT_CATEGORY_ID)
            uri = ItemEntry.buildItemWithCategoryId(mCategoryId);
        else
            uri = ItemEntry.CONTENT_URI;

        return new CursorLoader(getActivity(),
                uri,
                SItem.ITEM_COLUMNS,
                null, null,
                sortOrder
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mItemAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader loader) {
        mItemAdapter.swapCursor(null);
    }
}
