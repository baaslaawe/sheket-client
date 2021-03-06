package com.mukera.sheket.client.controller.items;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.mukera.sheket.client.R;
import com.mukera.sheket.client.data.SheketContract.*;
import com.mukera.sheket.client.models.SCategory;
import com.mukera.sheket.client.utils.PrefUtil;

import java.util.Locale;
import java.util.Stack;

/**
 * Created by fuad on 6/4/16.
 * <p>
 * <p>This fragment enables traversing category ancestry tree in the UI.
 * By extending this class and overriding its methods, sub classes can be notified
 * of the various states of the traversal. The traversal starts at the root category.
 * When the user selects a category, it will update the UI to look into the category.
 * This fragment keeps a stack of the categories visited so going back is possible.</p>
 */
public abstract class CategoryTreeNavigationFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor>,
        ExpandableCategoryTreeAdapter.ExpandableCategoryTreeListener {

    protected int mCurrentCategoryId;
    protected Stack<Integer> mCategoryBackstack;

    private ExpandableListView mExpandableListView;

    ExpandableCategoryTreeAdapter mExpandableAdapter;

    private void initCategoryLoader() {
        getLoaderManager().initLoader(getCategoryLoaderId(), null, this);
    }

    private void restartCategoryLoader() {
        getLoaderManager().restartLoader(getCategoryLoaderId(), null, this);
    }

    protected void initLoaders() {
        initCategoryLoader();
        onInitLoader();
    }

    protected void restartLoaders() {
        restartCategoryLoader();
        onRestartLoader();
    }

    /**
     * Since sub-classes inflate their own UI, they should provide the resolve layout id
     * to be inflated.
     * NOTE: the inflated UI should should embed the layout {@code R.layout.embedded_category_tree_navigation},
     * which contains the category navigation list and a divider view.
     *
     * @return
     */
    protected abstract int getLayoutResId();

    protected abstract int getCategoryLoaderId();


    /**
     * Subclasses can override this to get notified when a category is selected.
     * NOTE: This is called before any changes are done so subclasses can
     * prepare for the change.(E.g: if a category is selected to view its subcategories,
     * this will be called before the LoaderManager is Restarted. This ensures that
     * subclasses have prepared for their Loader's to restart by setting any
     * necessary internal variables to the appropriate state.)
     *
     * @param previous_category This this the last category visited before selecting the category
     * @param selected_category
     */
    protected void onCategorySelected(int previous_category, int selected_category) {
    }

    /**
     * Subclasses can override this to get notified on loader initialization
     */
    public void onInitLoader() {
    }

    /**
     * Subclasses can override this to get notified on loader restart
     */
    public void onRestartLoader() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCategoryBackstack = new Stack<>();
        // we start at the root
        mCurrentCategoryId = CategoryEntry.ROOT_CATEGORY_ID;
    }

    /**
     * Sets the category as the current and pushed the previous on the backstack.
     *
     * @param category_id
     * @return the previous category
     */
    protected int setCurrentCategory(int category_id) {
        // the root category is only added to the "bottom" of the stack
        if (category_id == CategoryEntry.ROOT_CATEGORY_ID)
            return mCurrentCategoryId;

        int previous_category = mCurrentCategoryId;
        mCategoryBackstack.push(mCurrentCategoryId);
        mCurrentCategoryId = category_id;
        return previous_category;
    }

    /**
     * Replace the current stack with the this. It won't immediately update
     * the UI. Sub-classes should handle that when necessary.
     */
    protected void setCategoryStack(Stack<Integer> category_stack, int current_category) {
        mCategoryBackstack = new Stack<>();
        for (Integer category_id : category_stack) {
            mCategoryBackstack.push(category_id);
        }
        mCurrentCategoryId = current_category;
    }

    /**
     * Use this to find the category you are in.
     */
    public int getCurrentCategory() {
        return mCurrentCategoryId;
    }

    public Stack<Integer> getCurrentStack() {
        return mCategoryBackstack;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        initLoaders();
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(getLayoutResId(), container, false);

        mExpandableAdapter = ExpandableCategoryTreeAdapter.newAdapter(getActivity(), this);
        mExpandableListView = (ExpandableListView) rootView.findViewById(R.id.expandable_category_tree_list_view);
        mExpandableListView.setAdapter(mExpandableAdapter);

        mExpandableListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                Cursor cursor = mExpandableAdapter.getChild(groupPosition, childPosition);
                if (cursor == null) {
                    return false;
                }

                if (groupPosition == ExpandableCategoryTreeAdapter.GROUP_CATEGORY) {
                    SCategory category = new SCategory(cursor);

                    int previous_category = setCurrentCategory(category.category_id);

                    onCategorySelected(previous_category, category.category_id);
                    restartLoaders();
                    return true;
                } else {
                    return onEntitySelected(cursor);
                }
            }
        });

        View listFooter = new View(getActivity());
        listFooter.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.FILL_PARENT, 140));
        mExpandableListView.addFooterView(listFooter);

        /**
         * This handles the "back" button key. If we are in a sub-category
         * and press back, we will move up to the parent category
         */
        rootView.setFocusableInTouchMode(true);
        rootView.requestFocus();
        rootView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                    if (mCategoryBackstack.isEmpty()) {
                        /**
                         * If we are not inside a sub-category and we press the back button,
                         * it should naturally do what is mostly expected which is move back to the
                         * previous fragment.
                         */
                        getActivity().onBackPressed();
                    } else {
                        /**
                         * If we were inside a sub-category, we should move back to the parent
                         * and notify of that.
                         */
                        int previous_category = mCategoryBackstack.peek();
                        mCurrentCategoryId = mCategoryBackstack.pop();
                        onCategorySelected(previous_category, mCurrentCategoryId);
                        restartLoaders();
                    }
                    return true;
                }

                return false;
            }
        });

        return rootView;
    }

    public void setEntityCursor(Cursor cursor) {
        mExpandableAdapter.setItemsCursor(cursor);
    }

    @Override
    public void onGetGroupChildrenCursor(int group) {
        /**
         * I don't know why we need to restart the loader,
         * if we only do initLoader, sometimes the category cursor
         * isn't correctly returned. It will only return a cursor.
         * that has been closed which doesn't work.
         */
        switch (group) {
            case ExpandableCategoryTreeAdapter.GROUP_CATEGORY:
                restartCategoryLoader();
                break;
            case ExpandableCategoryTreeAdapter.GROUP_ITEMS:
                onRestartLoader();
                break;
        }
    }

    private static class ViewHolder {
        TextView categoryName, childrenCount;

        public ViewHolder(View view) {
            categoryName = (TextView) view.findViewById(R.id.list_item_category_tree_text_view_name);
            childrenCount = (TextView) view.findViewById(R.id.list_item_category_tree_text_view_sub_count);
        }
    }

    @Override
    public View newCategoryView(Context context, ViewGroup parent, Cursor cursor, int position) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.list_item_category_tree_navigation, parent, false);

        ViewHolder holder = new ViewHolder(view);
        view.setTag(holder);
        return view;
    }

    @Override
    public void bindCategoryView(Context context, Cursor cursor, View view, int position) {
        SCategory category = new SCategory(cursor, true);

        ViewHolder holder = (ViewHolder) view.getTag();

        holder.categoryName.setText(category.name);
        if (category.childrenCategories.isEmpty()) {
            holder.childrenCount.setVisibility(View.GONE);
        } else {
            holder.childrenCount.setVisibility(View.VISIBLE);
            holder.childrenCount.setText(String.format(Locale.US,
                    "%d", category.childrenCategories.size()));
        }
    }

    /**
     * Override these 3 methods to load your data.
     */
    protected abstract Loader<Cursor> onEntityCreateLoader(int id, Bundle args);
    protected abstract void onEntityLoaderFinished(Loader<Cursor> loader, Cursor data);
    protected abstract void onEntityLoaderReset(Loader<Cursor> loader);

    // Override this to listen for click events for your entity,

    /**
     * You can listen for non-category list element clicks here. The cursor is the one
     * the list had been populated with on {@code onEntityLoaderFinished} call. Its position
     * is 0-index from entities returned on the loader finish call.
     * @param cursor
     * @return      True is the click was handled, false otherwise.
     */
    protected abstract boolean onEntitySelected(Cursor cursor);

    /**
     * Override this to create another loader.
     */
    protected Loader<Cursor> getCategoryTreeLoader(int id, Bundle args) {
        String sortOrder = CategoryEntry._fullCurrent(CategoryEntry.COLUMN_NAME) + " COLLATE NOCASE ASC";

        String selection = CategoryEntry._fullCurrent(CategoryEntry.COLUMN_PARENT_ID) + " = ? AND " +
                // we don't want the deleted to appear(until they are totally removed when syncing)
                CategoryEntry._fullCurrent(ChangeTraceable.COLUMN_CHANGE_INDICATOR) + " != ?";

        String[] selectionArgs = new String[] {
                String.valueOf(mCurrentCategoryId),
                String.valueOf(ChangeTraceable.CHANGE_STATUS_DELETED)
        };

        return new CursorLoader(getActivity(),
                CategoryEntry.buildBaseUri(PrefUtil.getCurrentCompanyId(getContext())),
                SCategory.CATEGORY_WITH_CHILDREN_COLUMNS,
                selection,
                selectionArgs,
                sortOrder);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id != getCategoryLoaderId())
            return onEntityCreateLoader(id, args);

        return getCategoryTreeLoader(id, args);
    }

    /**
     * Sub-classes can override this and define their own rules. This is then used
     * to determine whether to show the category navigation list view or not.
     *
     * @return
     */
    protected boolean isShowingCategoryTree() {
        return true;
        //return PrefUtil.getShowCategoryTreeState(getActivity());
    }

    /**
     * Override this method to conditionally control whether the CategoryList visibility.
     *
     * @return true if you want, false if not.
     */
    protected boolean shouldShowCategoryNavigation() {
        return true;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data.isClosed())
            return;

        if (loader.getId() == getCategoryLoaderId()) {
            mExpandableAdapter.setCategoryCursor(new SCategory.CategoryWithChildrenCursor(data));

            setCategoryListVisibility(shouldShowCategoryNavigation() && isShowingCategoryTree());
        } else {
            onEntityLoaderFinished(loader, data);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (loader.getId() == getCategoryLoaderId()) {
            mExpandableAdapter.setCategoryCursor(null);
        } else {
            onEntityLoaderReset(loader);
        }
    }


    /**
     * Set the visibility of the category navigation UI(list-view & other stuff).
     *
     * @param show_list
     */
    public void setCategoryListVisibility(boolean show_list) {
        boolean is_expanded = mExpandableListView.isGroupExpanded(ExpandableCategoryTreeAdapter.GROUP_CATEGORY);
        // if it is already opened, don't bother
        if (show_list && !is_expanded) {
            mExpandableListView.expandGroup(ExpandableCategoryTreeAdapter.GROUP_CATEGORY);
        } else if (!show_list && is_expanded) {
            mExpandableListView.collapseGroup(ExpandableCategoryTreeAdapter.GROUP_CATEGORY);
        }
    }
}
