package com.mukera.sheket.client.blogic.fragments;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mukera.sheket.client.R;
import com.mukera.sheket.client.contentprovider.SheketContract.ItemEntry;
import com.mukera.sheket.client.models.SItem;

/**
 * Created by gamma on 3/4/16.
 */
public class ItemCursorAdapter extends CursorAdapter {
    private static class ViewHolder {
        TextView item_name;
        TextView item_code;
        TextView qty_remain;

        public ViewHolder(View view) {
            item_name = (TextView) view.findViewById(R.id.list_item_text_view_item_name);
            item_code = (TextView) view.findViewById(R.id.list_item_text_view_item_code);
            qty_remain = (TextView) view.findViewById(R.id.list_item_text_view_item_qty);
        }
    }

    public ItemCursorAdapter(Context context) {
        super(context, null);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_inventory, parent, false);
        ViewHolder holder = new ViewHolder(view);

        view.setTag(holder);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder holder = (ViewHolder) view.getTag();
        SItem item = new SItem(cursor);

        holder.item_name.setText(item.name);
        String code;
        switch (item.code_type) {
            case ItemEntry.CODE_TYPE_BAR_CODE: code = item.bar_code; break;
            case ItemEntry.CODE_TYPE_MANUAL: code = item.manual_code; break;
            case ItemEntry.CODE_TYPE_BOTH:
            default:
                code = item.bar_code + " | " + item.manual_code; break;
        }
        holder.item_code.setText(code);
        holder.qty_remain.setText(String.valueOf(item.qty_remain));
    }
}
