package com.mukera.sheket.client.controller.items;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.util.Pair;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.mukera.sheket.client.R;
import com.mukera.sheket.client.data.SheketContract;
import com.mukera.sheket.client.data.SheketContract.BranchEntry;
import com.mukera.sheket.client.data.SheketContract.BranchItemEntry;
import com.mukera.sheket.client.data.SheketContract.TransItemEntry;
import com.mukera.sheket.client.models.SBranch;
import com.mukera.sheket.client.models.SBranchItem;
import com.mukera.sheket.client.models.SItem;
import com.mukera.sheket.client.models.SPermission;
import com.mukera.sheket.client.models.STransaction.STransactionItem;
import com.mukera.sheket.client.utils.LoaderId;
import com.mukera.sheket.client.utils.PrefUtil;
import com.mukera.sheket.client.utils.TextWatcherAdapter;
import com.mukera.sheket.client.utils.UnitsOfMeasurement;
import com.mukera.sheket.client.utils.Utils;

import java.util.List;
import java.util.Locale;

/**
 * Created by fuad on 7/11/16.
 */
public class QuantityDialog extends DialogFragment implements LoaderManager.LoaderCallbacks<Cursor> {
    /**
     * We made it an abstract class instead of an interface so the
     * {@code Parcelable} methods could be implemented here.
     */
    public static abstract class QuantityListener implements Parcelable {
        abstract void dialogCancel(DialogFragment dialog);

        /**
         * Says an item was selected for the transaction and the user wants to continue
         * to add more items to this transaction.
         */
        abstract void dialogOkContinue(DialogFragment dialog, STransactionItem transItem);

        /**
         * The user has selected the item he wants and wants to finish the transaction.
         * <p/>
         * It would be good if you check the number of items that have been selected for
         * the transaction thus far and decide whether to display a "transaction-summary"
         * or just commit it.
         */
        abstract void dialogOkFinish(DialogFragment dialog, STransactionItem transItem);

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }
    }

    private TextView mItemCode, mItemName;

    /**
     * These ImageButtons will only be bind to the views after {@code onLoaderFinished} has been
     * called and we know if we should include "other" branch option.
     */
    private ImageButton mImgBtnSend, mImgBtnReceive, mImgBtnBuy, mImgBtnSell;

    private ImageButton mImgBtnSelectedTransaction;

    private View mLayoutOtherBranchSelector;
    private TextView mTextTransferType;
    private Button mBtnSelectOtherBranch;

    private View mLayoutOtherBranchQty;
    private TextView mTextOtherBranchName;
    // only 1 of these 2 is visible at any time
    private TextView mTextOtherBranchQty;
    private ImageView mImgOtherBranchEmpty;

    private RadioGroup mRadioGroup;
    private RadioButton mRadioStandard, mRadioDerived;

    private EditText mQtyEdit;
    // This displays the unit type to the right of the quantity
    private TextView mTextUnitExtension;
    private TextView mTextConversionFormula;

    private EditText mEditItemNote;

    private Button mBtnCancel;
    private Button mBtnContinue, mBtnFinish;

    /**
     * We need to hold on to the layout after {@code onCreateDialog} so we may access it after the cursor
     * inside {@code onLoadFinished}. This was needed because some UI elements depended on knowing whether
     * an item existed in other branches, which can only be "known" after {@code onLoadFinished}.
     * Till then, some of the UI widgets will be null.
     */
    private View mDialogLayout;

    private SItem mItem;
    private Long mCurrentBranch;

    /**
     * We use this to determine if we are trying to sell/send beyond
     * the quantity available in the current branch.
     */
    private Pair<SBranchItem, SBranch> mCurrentBranchItemPair = null;

    /**
     * A pair of the "other" branch item and branch when the transaction is
     * a transfer(send/receive). If the first element(the branch item) is null,
     * then the other branch doesn't contain that item.
     */
    private Pair<SBranchItem, SBranch> mOtherBranchItem = null;

    private QuantityListener mListener;

    private enum ActionType {NOT_SET, BUY, SELL, SEND_TO, RECEIVE_FROM}

    private ActionType mActionType = ActionType.NOT_SET;

    private static final String KEY_ARG_ITEM = "key_arg_item";
    private static final String KEY_ARG_BRANCH = "key_arg_branch";
    private static final String KEY_ARG_LISTENER = "key_arg_listener";

    public static QuantityDialog newInstance(SItem item, long branch, QuantityListener listener) {
        Bundle args = new Bundle();
        args.putLong(KEY_ARG_BRANCH, branch);
        args.putParcelable(KEY_ARG_ITEM, item);
        args.putParcelable(KEY_ARG_LISTENER, listener);

        QuantityDialog dialog = new QuantityDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        mCurrentBranch = args.getLong(KEY_ARG_BRANCH);
        mItem = args.getParcelable(KEY_ARG_ITEM);
        mListener = args.getParcelable(KEY_ARG_LISTENER);
    }

    boolean isQuantitySet() {
        return !mQtyEdit.getText().toString().trim().isEmpty();
    }

    boolean isDerivedSelected() {
        return mRadioGroup.getCheckedRadioButtonId() == mRadioDerived.getId();
    }

    double getConvertedItemQuantity() {
        if (!isQuantitySet()) return 0;

        double factor = 1;
        boolean is_derived_selected = mItem.has_derived_unit &&
                isDerivedSelected();
        if (is_derived_selected) {
            factor = mItem.derived_factor;
        }

        String qty = mQtyEdit.getText().toString().trim();
        if (qty.startsWith("."))        // handle numbers like .17 as 0.17, it CRASHES otherwise!!!
            qty = "0" + qty;
        return factor * Double.valueOf(qty);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getLoaderManager().initLoader(LoaderId.MainActivity.QUANTITY_DIALOG_BRANCH_LOADER, null, this);
        super.onActivityCreated(savedInstanceState);
    }

    /**
     * Check if the written quantity "makes sense".
     * It checks if any transaction leaves any quantity(current, sender, receiver)
     * in a negative quantity value.
     *
     * @return true if allowed
     */
    boolean isQuantityAllowed() {
        // if we haven't figured out the current branch's qty, it isn't allowed
        if (mCurrentBranchItemPair == null)
            return false;

        double qty = getConvertedItemQuantity();

        switch (mActionType) {
            case SELL:
            case SEND_TO:
                // if the item doesn't exist in the branch, we can't sell it.
                if (mCurrentBranchItemPair.first == null) {
                    return false;
                }
                if ((mCurrentBranchItemPair.first.quantity - qty) < 0) {
                    return false;
                }
                break;

            case RECEIVE_FROM: {
                if (mOtherBranchItem == null ||
                        mOtherBranchItem.first == null)
                    return false;
                SBranchItem other_branch_qty = mOtherBranchItem.first;

                if ((other_branch_qty.quantity - qty) < 0) {
                    return false;
                }
                break;
            }
        }

        return true;
    }

    void updateAcceptTransactionButtonState() {
        if ((mActionType == ActionType.NOT_SET) ||
                !isQuantitySet() ||
                !isQuantityAllowed()) {
            mBtnContinue.setVisibility(View.INVISIBLE);
            mBtnFinish.setVisibility(View.INVISIBLE);
            return;
        }

        switch (mActionType) {
            case SEND_TO:
            case RECEIVE_FROM:
                /**
                 * If this transaction is a transfer, we should've selected another branch
                 */
                if (mOtherBranchItem == null) {
                    mBtnContinue.setVisibility(View.INVISIBLE);
                    mBtnFinish.setVisibility(View.INVISIBLE);
                    return;
                }
        }

        mBtnContinue.setVisibility(View.VISIBLE);
        mBtnFinish.setVisibility(View.VISIBLE);
    }

    void updateConversionRateDisplay() {
        if (!mItem.has_derived_unit ||
                !isDerivedSelected() ||
                !isQuantitySet()) {
            mTextConversionFormula.setVisibility(View.GONE);
        } else {
            mTextConversionFormula.setVisibility(View.VISIBLE);

            String qty = mQtyEdit.getText().toString().trim();
            String base_unit = UnitsOfMeasurement.getUnitSymbol(mItem.unit_of_measurement);
            String derived = mItem.derived_name;
            String factor = Utils.formatDoubleForDisplay(mItem.derived_factor);

            String total = Utils.formatDoubleForDisplay(
                    Double.valueOf(qty) * mItem.derived_factor);

            mTextConversionFormula.setText(
                    String.format(" %s %s = %s * (%s * %s) = %s %s ",
                            qty, derived,
                            qty, factor, base_unit,
                            total, base_unit));
        }
    }

    void linkViews(View view) {
        mItemCode = (TextView) view.findViewById(R.id.dialog_qty_text_item_code);
        mItemName = (TextView) view.findViewById(R.id.dialog_qty_text_item_name);

        mEditItemNote = (EditText) view.findViewById(R.id.dialog_qty_edit_text_item_note);
        //mImgBtnSelectedTransaction = (ImageButton) view.findViewById(R.id.dialog_qty_img_btn_action);

        mLayoutOtherBranchSelector = view.findViewById(R.id.dialog_qty_layout_select_other_branch);
        mTextTransferType = (TextView) view.findViewById(R.id.dialog_qty_text_transfer_type);
        mBtnSelectOtherBranch = (Button) view.findViewById(R.id.dialog_qty_btn_select_branch);

        mLayoutOtherBranchQty = view.findViewById(R.id.dialog_qty_layout_other_branch_qty);
        mTextOtherBranchName = (TextView) view.findViewById(R.id.dialog_qty_text_selected_branch_name);
        mTextOtherBranchQty = (TextView) view.findViewById(R.id.dialog_qty_text_other_branch_qty);
        mImgOtherBranchEmpty = (ImageView) view.findViewById(R.id.dialog_qty_img_item_not_exist);

        mRadioGroup = (RadioGroup) view.findViewById(R.id.dialog_qty_radio_group);

        mRadioStandard = (RadioButton) view.findViewById(R.id.dialog_qty_radio_standard);
        mRadioDerived = (RadioButton) view.findViewById(R.id.dialog_qty_radio_derived);
        mRadioStandard.setText(UnitsOfMeasurement.getUnitSymbol(mItem.unit_of_measurement));
        if (mItem.has_derived_unit) {
            mRadioDerived.setText(mItem.derived_name);
        }
        // Make the "Standard" unit be the "default" selected
        mRadioGroup.check(mRadioStandard.getId());

        mQtyEdit = (EditText) view.findViewById(R.id.dialog_qty_edit_text_qty);
        mTextUnitExtension = (TextView) view.findViewById(R.id.dialog_qty_text_unit_extension);

        mTextConversionFormula = (TextView) view.findViewById(R.id.dialog_qty_text_conversion);

        mEditItemNote = (EditText) view.findViewById(R.id.dialog_qty_edit_text_item_note);
    }

    void updateVisibility() {
        boolean is_transfer;
        switch (mActionType) {
            case SEND_TO:
            case RECEIVE_FROM:
                is_transfer = true;
                break;
            default:
                is_transfer = false;
        }

        mLayoutOtherBranchSelector.setVisibility(is_transfer ? View.VISIBLE : View.GONE);

        if (is_transfer &&
                // you should've also specified another branch
                mOtherBranchItem != null) {
            mLayoutOtherBranchQty.setVisibility(View.VISIBLE);
        } else {
            mLayoutOtherBranchQty.setVisibility(View.GONE);
        }

        mRadioGroup.setVisibility(
                mItem.has_derived_unit ? View.VISIBLE : View.GONE);

        boolean show_conversion_rate = mItem.has_derived_unit && isQuantitySet();
        mTextConversionFormula.setVisibility(show_conversion_rate ? View.VISIBLE : View.GONE);
    }

    void updateViewContents() {
        int receive_icon = R.drawable.ic_action_receive_un_clicked;
        int send_icon = R.drawable.ic_action_send_un_clicked;
        int buy_icon = R.drawable.ic_action_buy_un_clicked;
        int sell_icon = R.drawable.ic_action_sell_un_clicked;

        switch (mActionType) {
            case RECEIVE_FROM: receive_icon = R.drawable.ic_action_receive_clicked; break;
            case SEND_TO: send_icon = R.drawable.ic_action_send_clicked; break;
            case BUY: buy_icon = R.drawable.ic_action_buy_clicked; break;
            case SELL: sell_icon = R.drawable.ic_action_sell_clicked; break;
        }

        if (mImgBtnReceive != null)
            mImgBtnReceive.setImageResource(receive_icon);
        if (mImgBtnSend != null)
            mImgBtnSend.setImageResource(send_icon);
        if (mImgBtnBuy != null)
            mImgBtnBuy.setImageResource(buy_icon);
        if (mImgBtnSell != null)
            mImgBtnSell.setImageResource(sell_icon);

        boolean is_transfer = false;
        switch (mActionType) {
            case SEND_TO:
                mTextTransferType.setText(getString(R.string.placeholder_qty_send_to));
                is_transfer = true;
                break;
            case RECEIVE_FROM:
                mTextTransferType.setText(getString(R.string.placeholder_qty_receive_from));
                is_transfer = true;
                break;
        }

        if (is_transfer &&
                mOtherBranchItem != null) {
            SBranchItem branchItem = mOtherBranchItem.first;
            SBranch branch = mOtherBranchItem.second;

            mTextOtherBranchName.setText(Utils.toTitleCase(branch.branch_name));
            if (branchItem != null) {
                mTextOtherBranchQty.setVisibility(View.VISIBLE);
                mImgOtherBranchEmpty.setVisibility(View.GONE);
                mTextOtherBranchQty.setText(Utils.formatDoubleForDisplay(branchItem.quantity));
            } else {
                mTextOtherBranchQty.setVisibility(View.GONE);
                mImgOtherBranchEmpty.setVisibility(View.VISIBLE);
            }
        }

        // update the units extension
        if (!mItem.has_derived_unit) {
            mTextUnitExtension.setVisibility(View.VISIBLE);
            mTextUnitExtension.setText(
                    UnitsOfMeasurement.getUnitSymbol(mItem.unit_of_measurement));
        } else {
            mTextUnitExtension.setVisibility(View.GONE);
        }

        if (!isQuantitySet() || isQuantityAllowed()) {
            mQtyEdit.setError(null);
        } else {
            boolean display_error = true;

            // if we are receiving/sending but have not specified the "other" branch
            // don't show the error message
            if ((mOtherBranchItem == null) &&
                    (mActionType == ActionType.RECEIVE_FROM
                            || mActionType == ActionType.SEND_TO)) {
                display_error = false;
            }
            if (display_error) {
                mQtyEdit.setError(getActivity().getResources().getString(R.string.placeholder_qty_insufficient_qty));
                mQtyEdit.requestFocus();
            } else {
                mQtyEdit.setError(null);
            }
        }
    }

    void updateViews() {
        updateVisibility();
        updateViewContents();
        updateConversionRateDisplay();
        updateAcceptTransactionButtonState();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mDialogLayout = getActivity().getLayoutInflater().
                inflate(R.layout.dialog_quantity, null);

        linkViews(mDialogLayout);

        mItemName.setText(mItem.name);
        mItemCode.setText(mItem.item_code);

        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                updateViews();
            }
        });

        mQtyEdit.addTextChangedListener(new TextWatcherAdapter() {
            @Override
            public void afterTextChanged(Editable s) {
                updateViews();
            }
        });

        mBtnSelectOtherBranch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View view = getActivity().getLayoutInflater().
                        inflate(R.layout.dialog_other_branch_select, null);
                ListView branchList = (ListView) view.findViewById(R.id.dialog_other_branch_list_view);

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setView(view);
                final AlertDialog dialog = builder.create();

                final ArrayAdapter<Pair<SBranchItem, SBranch>> branchItemAdapter = new OtherBranchItemAdapter(getContext());

                branchList.setAdapter(branchItemAdapter);
                for (Pair<SBranchItem, SBranch> branch : mItem.available_branches) {
                    branchItemAdapter.add(branch);
                }

                branchList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        Pair<SBranchItem, SBranch> selected = branchItemAdapter.getItem(position);
                        if (mActionType == ActionType.RECEIVE_FROM) {
                            // we don't allow to receive from a branch which doesn't contain the item
                            // so ignore
                            if (selected.first == null) {

                            } else {
                                mOtherBranchItem = selected;
                                dialog.dismiss();
                                updateViews();
                            }
                        } else if (mActionType == ActionType.SEND_TO) {
                            mOtherBranchItem = selected;
                            dialog.dismiss();
                            updateViews();
                        }
                    }
                });
                dialog.show();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(getContext()).
                setView(mDialogLayout).
                setPositiveButton(R.string.dialog_qty_finish, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mBtnFinish.getVisibility() == View.INVISIBLE) return;

                        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(mQtyEdit.getApplicationWindowToken(), 0);

                        mListener.dialogOkFinish(QuantityDialog.this, getTransactionItem());
                    }
                }).
                setNegativeButton(R.string.dialog_qty_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mBtnContinue.getVisibility() == View.INVISIBLE) return;

                        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(mQtyEdit.getApplicationWindowToken(), 0);

                        mListener.dialogOkContinue(QuantityDialog.this, getTransactionItem());
                    }
                }).
                setNeutralButton(R.string.dialog_qty_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mListener.dialogCancel(QuantityDialog.this);
                    }
                }).
                create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                AlertDialog alertDialog = (AlertDialog) dialog;

                mBtnFinish = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                mBtnContinue = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                mBtnCancel = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);

                // start things off
                updateViews();
            }
        });

        return dialog;
    }

    STransactionItem getTransactionItem() {
        STransactionItem transItem = new STransactionItem();

        double qty = getConvertedItemQuantity();
        transItem.item = mItem;
        transItem.item_id = mItem.item_id;
        transItem.quantity = qty;
        transItem.trans_type = getTransactionType();
        transItem.item_note = mEditItemNote.getText().toString().trim();
        transItem.company_id = PrefUtil.getCurrentCompanyId(getActivity());
        transItem.change_status = SheketContract.ChangeTraceable.CHANGE_STATUS_CREATED;
        switch (transItem.trans_type) {
            case TransItemEntry.TYPE_INCREASE_TRANSFER_FROM_OTHER_BRANCH:
            case TransItemEntry.TYPE_DECREASE_TRANSFER_TO_OTHER:
                transItem.other_branch_id = mOtherBranchItem.second.branch_id;
                break;
            default:
                transItem.other_branch_id = SheketContract.BranchEntry.DUMMY_BRANCH_ID;
        }

        return transItem;
    }

    static class OtherBranchItemAdapter extends ArrayAdapter<Pair<SBranchItem, SBranch>> {
        public OtherBranchItemAdapter(Context context) {
            super(context, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Pair<SBranchItem, SBranch> pair = getItem(position);

            ViewHolder holder;
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.list_item_dialog_other_branch_qty, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.branchName.setText(Utils.toTitleCase(pair.second.branch_name));
            if (pair.first == null) {       // the branch doesn't contain the item
                holder.notExist.setVisibility(View.VISIBLE);
                holder.qty.setVisibility(View.GONE);
            } else {
                holder.notExist.setVisibility(View.GONE);
                holder.qty.setVisibility(View.VISIBLE);

                holder.qty.setText(Utils.formatDoubleForDisplay(pair.first.quantity));
            }

            return convertView;
        }

        static class ViewHolder {
            TextView branchName;

            TextView qty;
            ImageView notExist;

            public ViewHolder(View view) {
                branchName = (TextView) view.findViewById(R.id.list_item_dialog_other_branch_text_name);
                qty = (TextView) view.findViewById(R.id.list_item_dialog_other_branch_text_qty);
                notExist = (ImageView) view.findViewById(R.id.list_item_dialog_other_branch_img_not_exist);
            }
        }
    }

    int getTransactionType() {
        switch (mActionType) {
            case BUY:
                return TransItemEntry.TYPE_INCREASE_PURCHASE;
            case SELL:
                return TransItemEntry.TYPE_DECREASE_CURRENT_BRANCH;
            case RECEIVE_FROM:
                return TransItemEntry.TYPE_INCREASE_TRANSFER_FROM_OTHER_BRANCH;
            case SEND_TO:
                return TransItemEntry.TYPE_DECREASE_TRANSFER_TO_OTHER;
            default:
                Log.e("QuantityDialog", "Transaction Type can't be of type " + mActionType);
                return -1;
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        int company_id = PrefUtil.getCurrentCompanyId(getActivity());

        String selection = null;
        String[] selectionArgs = null;

        SPermission permission = SPermission.getUserPermission(getContext());

        if (permission.getPermissionType() == SPermission.PERMISSION_TYPE_EMPLOYEE) {
            List<SPermission.BranchAccess> allowedBranches = permission.getAllowedBranches();
            selection = "(";
            selectionArgs = new String[allowedBranches.size()];
            for (int i = 0; i < allowedBranches.size(); i++) {
                if (i != 0) {
                    selection += " OR ";
                }
                selection += SheketContract.BranchEntry._full(SheketContract.BranchEntry.COLUMN_BRANCH_ID) + " = ? ";
                selectionArgs[i] = String.valueOf(allowedBranches.get(i).branch_id);
            }
            selection += ")";
        }

        /**
         * filter-out branches who've got their status_flag's set to INVISIBLE
         */
        selection = ((selection != null) ? (selection + " AND ") : "") +
                String.format(Locale.US,
                        "%s != %d",
                        BranchEntry._full(BranchEntry.COLUMN_STATUS_FLAG),
                        BranchEntry.STATUS_INVISIBLE);

        return new CursorLoader(getActivity(),
                BranchItemEntry.buildItemInAllBranches(company_id, mItem.item_id),
                SItem.ITEM_WITH_BRANCH_DETAIL_COLUMNS,
                selection,
                selectionArgs,
                BranchEntry._full(BranchEntry.COLUMN_NAME) + " ASC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) {
            // we can't show shit if there isn't an item
            getDialog().dismiss();
            return;
        }

        mItem = new SItem(data, true);
        for (int i = 0; i < mItem.available_branches.size(); i++) {
            Pair<SBranchItem, SBranch> branchItemBranchPair = mItem.available_branches.get(i);

            // remove the current branch from the branch options
            if (branchItemBranchPair.second.branch_id == mCurrentBranch) {
                mCurrentBranchItemPair = branchItemBranchPair;

                // remove it from the available list so it won't appear in the "other" branch list
                mItem.available_branches.remove(i);
                break;
            }
        }

        if (mItem.available_branches.isEmpty()) {
            // hide the 4 ImageBtn layout.
            mDialogLayout.findViewById(R.id.dialog_qty_layout_transaction_type_four).setVisibility(View.GONE);

            mImgBtnSend = null;
            mImgBtnReceive = null;

            // TODO: check if the user has permission to buy items, hide it otherwise
            mImgBtnBuy = (ImageButton) mDialogLayout.findViewById(R.id.dialog_qty_select_img_btn_two_buy);

            mImgBtnSell = (ImageButton) mDialogLayout.findViewById(R.id.dialog_qty_select_img_btn_two_sell);
        } else {
            // hide the 2 ImageBtn layout.
            mDialogLayout.findViewById(R.id.dialog_qty_layout_transaction_type_two).setVisibility(View.GONE);

            mImgBtnSend = (ImageButton) mDialogLayout.findViewById(R.id.dialog_qty_select_img_btn_four_send);
            mImgBtnReceive = (ImageButton) mDialogLayout.findViewById(R.id.dialog_qty_select_img_btn_four_receive);
            mImgBtnBuy = (ImageButton) mDialogLayout.findViewById(R.id.dialog_qty_select_img_btn_four_buy);
            mImgBtnSell = (ImageButton) mDialogLayout.findViewById(R.id.dialog_qty_select_img_btn_four_sell);
        }

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int view_id = v.getId();

                mActionType = ActionType.NOT_SET;
                if (mImgBtnReceive != null && view_id == mImgBtnReceive.getId()) {
                    mActionType = ActionType.RECEIVE_FROM;
                } else if (mImgBtnSend != null && view_id == mImgBtnSend.getId()) {
                    mActionType = ActionType.SEND_TO;
                } else if (mImgBtnBuy != null && view_id == mImgBtnBuy.getId()) {
                    mActionType = ActionType.BUY;
                } else if (mImgBtnSell != null && view_id == mImgBtnSell.getId()) {
                    mActionType = ActionType.SELL;
                }

                /**
                 * ALWAYS "reset" the other branch, this prevents some inconsistent states.
                 *
                 * e.g:     Because you can send an item to a branch it doesn't already exist
                 *          in, you can select such a branch to send an item to it. But you
                 *          shouldn't just change transaction type to "receive from" and
                 *          still use that branch the item doesn't exist in.
                 */
                mOtherBranchItem = null;

                updateViews();
            }
        };

        if (mImgBtnReceive != null)
            mImgBtnReceive.setOnClickListener(clickListener);
        if (mImgBtnSend != null)
            mImgBtnSend.setOnClickListener(clickListener);
        if (mImgBtnBuy != null)
            mImgBtnBuy.setOnClickListener(clickListener);
        if (mImgBtnSell != null)
            mImgBtnSell.setOnClickListener(clickListener);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // we can't show shit if there isn't an item
        //getDialog().dismiss();
    }
}
