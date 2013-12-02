/**
 * Author: Andrei F.
 *
 * This file is part of the "Synapse" software and is licensed under
 * under the Microsoft Reference Source License (MS-RSL).
 *
 * Please see the attached LICENSE.txt for the full license.
 */

package com.af.synapse;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONArray;

import com.af.synapse.elements.*;
import com.af.synapse.utils.ActionValueClient;
import com.af.synapse.utils.ActionValueUpdater;
import com.af.synapse.utils.ActivityListener;
import com.af.synapse.utils.L;
import com.af.synapse.utils.Utils;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends FragmentActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
     * will keep every loaded fragment in memory. If this becomes too memory
     * intensive, it may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link android.support.v4.view.ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    private static Fragment[] fragments = null;
    private static AtomicInteger fragmentsDone = new AtomicInteger(0);
    long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.mainActivity = this;
        startTime = System.nanoTime();
        setContentView(R.layout.activity_loading);

        super.onCreate(fragments == null ? null : savedInstanceState);

        Utils.mainActivity = this;
        if (fragments == null) {
            if (!Synapse.isValidEnvironment) {
                findViewById(R.id.initialProgressBar).setVisibility(View.INVISIBLE);
                ((TextView) findViewById(R.id.initialText)).setText(R.string.initial_no_uci);
                return;
            }
        }

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        /**
         *  The UI building continues in buildFragment after fragment generation, or if
         *  the fragments are already live, continue here.
         */

        if (fragmentsDone.get() == Utils.configSections.size())
            continueCreate();
    }

    private void continueCreate() {
        setContentView(R.layout.activity_main);
        mViewPager = (ViewPager) findViewById(R.id.mainPager);
        mViewPager.setOffscreenPageLimit(Utils.configSections.size());
        mViewPager.setAdapter(mSectionsPagerAdapter);
        ActionValueUpdater.refreshButtons(true);
        L.i("Interface creation finished in " + (System.nanoTime() - startTime) + "ns");

        if (!BootService.getBootFlag() && !BootService.getBootFlagPending()) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.popup_failed_boot_title)
                .setMessage(R.string.popup_failed_boot_message)
                .setCancelable(true)
                .setPositiveButton(R.string.popup_failed_boot_ack, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {}
                })
                .show();
        }
    }

    @Override
    public void onDestroy(){
        if (!isChangingConfigurations()) {
            fragments = null;
            fragmentsDone = new AtomicInteger(0);
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        ActionValueUpdater.setMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_apply:
                ActionValueUpdater.applyElements();
                break;
            case R.id.action_cancel:
                ActionValueUpdater.cancelElements();
                break;
            case R.id.action_section_default:
                ActionValueUpdater.resetSectionDefault(mViewPager.getCurrentItem());
                break;
            case R.id.action_global_default:
                for (int i=0; i < Utils.configSections.size(); i++)
                    ActionValueUpdater.resetSectionDefault(i);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A {@link android.support.v4.app.FragmentPagerAdapter} that returns a fragment
     * corresponding to one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        private void buildFragment(int position) {
            if (fragments[position] != null)
                return;

            tabSectionFragment fragment = new tabSectionFragment();
            Bundle args = new Bundle();
            args.putInt(tabSectionFragment.ARG_SECTION_NUMBER, position);
            fragment.setArguments(args);
            fragments[position] = fragment;
            fragmentsDone.incrementAndGet();

            if (fragmentsDone.get() < Utils.configSections.size())
                return;

            /**
             *  After all fragments are created, continue building the UI.
             */
            Utils.mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    continueCreate();
                }
            });
        }

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);

            if (fragmentsDone.get() > 0)
                return;

            if (fragments == null)
                fragments = new Fragment[Utils.configSections.size()];

            for (int i = 0; i < Utils.configSections.size(); i++) {
                /**
                 *  Spawn a builder thread for each section/fragment
                 */
                final int position = i;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        buildFragment(position);
                    }
                }).start();
            }

            tabSectionFragment.startedFragments = 0;
        }

        @Override
        public Fragment getItem(int position) {
            return fragments[position];
        }

        @Override
        public int getCount() {
            return Utils.configSections.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            JSONObject section = (JSONObject)Utils.configSections.get(position);
            return section.get("name").toString();
        }
    }

    public static class tabSectionFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this fragment.
         */

        public static final String ARG_SECTION_NUMBER = "section_number";
        public static int startedFragments = 0;

        public View fragmentView = null;
        private ArrayList<BaseElement> fragmentElements = new ArrayList<BaseElement>();

        public tabSectionFragment() {
            this.setRetainInstance(true);
        }

        public void prepareView() {
            if (fragmentView != null)
                return;

            int sectionNumber = getArguments().getInt(ARG_SECTION_NUMBER);
            ScrollView tabSectionView = (ScrollView)LayoutInflater.from(Utils.mainActivity)
                                                        .inflate(R.layout.section_container, null);
            assert tabSectionView != null;
            LinearLayout tabContentLayout = (LinearLayout) tabSectionView.getChildAt(0);
            assert tabContentLayout != null;

            JSONObject section = (JSONObject)Utils.configSections.get(sectionNumber);
            JSONArray sectionElements = (JSONArray)section.get("elements");

            for (Object sectionElement : sectionElements) {
                JSONObject elm = (JSONObject) sectionElement;
                String type = elm.keySet().toString().replace("[", "").replace("]", "");
                JSONObject parameters = (JSONObject) elm.get(type);

                BaseElement elementObj = BaseElement.createObject(type, parameters, tabContentLayout);
                if (elementObj == null)
                    continue;

                try {
                    ActionValueClient perpetual = ((ActionValueClient) elementObj);
                    ActionValueUpdater.registerPerpetual(perpetual, sectionNumber);
                } catch (ClassCastException ignored) {}

                /**
                 *  Simple standalone elements may not add themselves to the layout, if so, add
                 *  them here after their creation.
                 */

                View elementView = elementObj.getView();
                if (elementView != null)
                    tabContentLayout.addView(elementView);

                fragmentElements.add(elementObj);
            }

            fragmentView = tabSectionView;
        }

        @Override
        public void setArguments(Bundle args) {
            super.setArguments(args);
            prepareView();
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            prepareView();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            return fragmentView;
        }

        @Override
        public void onStart(){
            super.onStart();
            for (BaseElement elm : fragmentElements)
                try { ((ActivityListener) elm).onStart(); } catch (ClassCastException ignored) {}

            /**
             *  Utils.appStarted serves as a flag to mark the completion of the *first*
             *  post-onStart of *all* fragments.
             */
            if (startedFragments < Utils.configSections.size())
                startedFragments++;
            else
                Utils.appStarted = true;
        }

        @Override
        public void onResume(){
            super.onResume();
            for (BaseElement elm : fragmentElements)
                try { ((ActivityListener) elm).onResume(); } catch (ClassCastException ignored) {}
        }

        @Override
        public void onPause(){
            super.onPause();
            for (BaseElement elm : fragmentElements)
                try { ((ActivityListener) elm).onPause(); } catch (ClassCastException ignored) {}
        }

        @Override
        public void onStop(){
            super.onStop();
            for (BaseElement elm : fragmentElements)
                try { ((ActivityListener) elm).onStop(); } catch (ClassCastException ignored) {}
        }

        @Override
        public void onDetach(){
            /**
             *  On main activity destruction we are keeping the fragments instead of killing them.
             *  However on the next activity re-creation they need to get added to that new View
             *  instance. So we remove the child view from the old instance so that it can be
             *  added to the new one.
             */

            if (fragmentView != null) {
                ViewGroup parent = (ViewGroup)fragmentView.getParent();
                if (parent != null)
                    parent.removeView(fragmentView);
            }
            super.onDetach();
        }
    }
}
