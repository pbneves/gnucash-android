/*
 * Copyright 2012 Roman Nurik
 * Copyright 2012 Ngewi Fet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.ui.wizard;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.tech.freak.wizardpager.model.AbstractWizardModel;
import com.tech.freak.wizardpager.model.ModelCallbacks;
import com.tech.freak.wizardpager.model.Page;
import com.tech.freak.wizardpager.model.ReviewItem;
import com.tech.freak.wizardpager.ui.PageFragmentCallbacks;
import com.tech.freak.wizardpager.ui.ReviewFragment;
import com.tech.freak.wizardpager.ui.StepPagerStrip;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.importer.ImportAsyncTask;
import org.gnucash.android.ui.account.AccountsActivity;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Activity for managing the wizard displayed upon first run of the application
 */
public class FirstRunWizardActivity extends AppCompatActivity implements
        PageFragmentCallbacks, ReviewFragment.Callbacks, ModelCallbacks {

    @Bind(R.id.pager) ViewPager mPager;
    private MyPagerAdapter mPagerAdapter;

    private boolean mEditingAfterReview;

    private AbstractWizardModel mWizardModel;

    private boolean mConsumePageSelectedEvent;

    @Bind(R.id.btn_save)    AppCompatButton mNextButton;
    @Bind(R.id.btn_cancel)  Button mPrevButton;
    @Bind(R.id.strip)       StepPagerStrip mStepPagerStrip;

    private List<Page> mCurrentPageSequence;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_run_wizard);
        ButterKnife.bind(this);

        setTitle(getString(R.string.title_setup_gnucash));

        mWizardModel = new FirstRunWizardModel(this);
        if (savedInstanceState != null) {
            mWizardModel.load(savedInstanceState.getBundle("model"));
        }

        mWizardModel.registerListener(this);

        mPagerAdapter = new MyPagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);
        mStepPagerStrip
                .setOnPageSelectedListener(new StepPagerStrip.OnPageSelectedListener() {
                    @Override
                    public void onPageStripSelected(int position) {
                        position = Math.min(mPagerAdapter.getCount() - 1,
                                position);
                        if (mPager.getCurrentItem() != position) {
                            mPager.setCurrentItem(position);
                        }
                    }
                });


        mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mStepPagerStrip.setCurrentPage(position);

                if (mConsumePageSelectedEvent) {
                    mConsumePageSelectedEvent = false;
                    return;
                }

                mEditingAfterReview = false;
                updateBottomBar();
            }
        });

        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mPager.getCurrentItem() == mCurrentPageSequence.size()) {
                    ArrayList<ReviewItem> reviewItems = new ArrayList<>();
                    for (Page page : mCurrentPageSequence) {
                        page.getReviewItems(reviewItems);
                    }

                    String currencyCode = GnuCashApplication.getDefaultCurrencyCode();
                    String accountOptions = getString(R.string.wizard_option_let_me_handle_it); //default value, do nothing
                    String feedbackOption = getString(R.string.wizard_option_disable_crash_reports);
                    for (ReviewItem reviewItem : reviewItems) {
                        String title = reviewItem.getTitle();
                        if (title.equals(getString(R.string.wizard_title_default_currency))){
                            currencyCode = reviewItem.getDisplayValue();
                        } else if (title.equals(getString(R.string.wizard_title_select_currency))){
                            currencyCode = reviewItem.getDisplayValue();
                        } else if (title.equals(getString(R.string.wizard_title_account_setup))){
                            accountOptions = reviewItem.getDisplayValue();
                        } else if (title.equals(getString(R.string.wizard_title_feedback_options))){
                            feedbackOption = reviewItem.getDisplayValue();
                        }
                    }

                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(FirstRunWizardActivity.this);
                    SharedPreferences.Editor preferenceEditor = preferences.edit();
                    preferenceEditor.putString(getString(R.string.key_default_currency), currencyCode);


                    if (feedbackOption.equals(getString(R.string.wizard_option_auto_send_crash_reports))){
                        preferenceEditor.putBoolean(getString(R.string.key_enable_crashlytics), true);
                    } else {
                        preferenceEditor.putBoolean(getString(R.string.key_enable_crashlytics), false);
                    }
                    preferenceEditor.apply();



                    if (accountOptions.equals(getString(R.string.wizard_option_create_default_accounts))){
                        AccountsActivity.createDefaultAccounts(currencyCode, FirstRunWizardActivity.this);
                    } else if (accountOptions.equals(getString(R.string.wizard_option_import_my_accounts))){
                        AccountsActivity.importAccounts(FirstRunWizardActivity.this);
                    }

                    finish();
                    AccountsActivity.removeFirstRunFlag();
                } else {
                    if (mEditingAfterReview) {
                        mPager.setCurrentItem(mPagerAdapter.getCount() - 1);
                    } else {
                        mPager.setCurrentItem(mPager.getCurrentItem() + 1);
                    }
                }
            }
        });

        mPrevButton.setText(R.string.wizard_btn_back);
        TypedValue v = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textAppearanceMedium, v,
                true);
        mPrevButton.setTextAppearance(this, v.resourceId);
        mNextButton.setTextAppearance(this, v.resourceId);

        mPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPager.setCurrentItem(mPager.getCurrentItem() - 1);
            }
        });

        onPageTreeChanged();
        updateBottomBar();
    }

    @Override
    public void onPageTreeChanged() {
        mCurrentPageSequence = mWizardModel.getCurrentPageSequence();
        recalculateCutOffPage();
        mStepPagerStrip.setPageCount(mCurrentPageSequence.size() + 1); // + 1 =
        // review
        // step
        mPagerAdapter.notifyDataSetChanged();
        updateBottomBar();
    }

    private void updateBottomBar() {
        int position = mPager.getCurrentItem();
        final Resources res = getResources();
        if (position == mCurrentPageSequence.size()) {
            mNextButton.setText(R.string.btn_wizard_finish);

            mNextButton.setBackgroundDrawable(new ColorDrawable(res.getColor(R.color.theme_accent)));
            mNextButton.setTextColor(res.getColor(android.R.color.white));
        } else {
            mNextButton.setText(mEditingAfterReview ? R.string.review
                    : R.string.btn_wizard_next);
            mNextButton
                    .setBackgroundDrawable(new ColorDrawable(res.getColor(android.R.color.transparent)));
            mNextButton.setTextColor(res.getColor(R.color.theme_accent));
            mNextButton.setEnabled(position != mPagerAdapter.getCutOffPage());
        }

        mPrevButton
                .setVisibility(position <= 0 ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    try {
                        InputStream accountInputStream = getContentResolver().openInputStream(data.getData());
                        new ImportAsyncTask(this).execute(accountInputStream);
                    } catch (FileNotFoundException e) {
                        Crashlytics.logException(e);
                        Toast.makeText(this, R.string.toast_error_importing_accounts, Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWizardModel.unregisterListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle("model", mWizardModel.save());
    }

    @Override
    public AbstractWizardModel onGetModel() {
        return mWizardModel;
    }

    @Override
    public void onEditScreenAfterReview(String key) {
        for (int i = mCurrentPageSequence.size() - 1; i >= 0; i--) {
            if (mCurrentPageSequence.get(i).getKey().equals(key)) {
                mConsumePageSelectedEvent = true;
                mEditingAfterReview = true;
                mPager.setCurrentItem(i);
                updateBottomBar();
                break;
            }
        }
    }

    @Override
    public void onPageDataChanged(Page page) {
        if (page.isRequired()) {
            if (recalculateCutOffPage()) {
                mPagerAdapter.notifyDataSetChanged();
                updateBottomBar();
            }
        }
    }

    @Override
    public Page onGetPage(String key) {
        return mWizardModel.findByKey(key);
    }

    private boolean recalculateCutOffPage() {
        // Cut off the pager adapter at first required page that isn't completed
        int cutOffPage = mCurrentPageSequence.size() + 1;
        for (int i = 0; i < mCurrentPageSequence.size(); i++) {
            Page page = mCurrentPageSequence.get(i);
            if (page.isRequired() && !page.isCompleted()) {
                cutOffPage = i;
                break;
            }
        }

        if (mPagerAdapter.getCutOffPage() != cutOffPage) {
            mPagerAdapter.setCutOffPage(cutOffPage);
            return true;
        }

        return false;
    }

    public class MyPagerAdapter extends FragmentStatePagerAdapter {
        private int mCutOffPage;
        private Fragment mPrimaryItem;

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            if (i >= mCurrentPageSequence.size()) {
                return new ReviewFragment();
            }

            return mCurrentPageSequence.get(i).createFragment();
        }

        @Override
        public int getItemPosition(Object object) {
            // TODO: be smarter about this
            if (object == mPrimaryItem) {
                // Re-use the current fragment (its position never changes)
                return POSITION_UNCHANGED;
            }

            return POSITION_NONE;
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position,
                                   Object object) {
            super.setPrimaryItem(container, position, object);
            mPrimaryItem = (Fragment) object;
        }

        @Override
        public int getCount() {
            return Math.min(mCutOffPage + 1, mCurrentPageSequence == null ? 1
                    : mCurrentPageSequence.size() + 1);
        }

        public void setCutOffPage(int cutOffPage) {
            if (cutOffPage < 0) {
                cutOffPage = Integer.MAX_VALUE;
            }
            mCutOffPage = cutOffPage;
        }

        public int getCutOffPage() {
            return mCutOffPage;
        }
    }
}
