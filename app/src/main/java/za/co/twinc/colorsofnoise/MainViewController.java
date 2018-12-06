/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.twinc.colorsofnoise;

import android.content.SharedPreferences;
import android.widget.Toast;

import com.android.billingclient.api.Purchase;
import za.co.twinc.colorsofnoise.billing.BillingManager.BillingUpdatesListener;
import za.co.twinc.colorsofnoise.skulist.row.PremiumDelegate;
import java.util.List;

/**
 * Handles control logic of the MainActivity
 */
class MainViewController {
    private final UpdateListener mUpdateListener;
    private MainActivity mActivity;

    // Tracks if we currently own premium
    private boolean mIsPremium;

    MainViewController(MainActivity activity) {
        mUpdateListener = new UpdateListener();
        mActivity = activity;
        loadData();
    }

    UpdateListener getUpdateListener() {
        return mUpdateListener;
    }

    boolean isPremiumPurchased() {
        return mIsPremium;
    }

    /**
     * Handler to billing updates
     */
    private class UpdateListener implements BillingUpdatesListener {
        @Override
        public void onBillingClientSetupFinished() {
            mActivity.onBillingManagerSetupFinished();
        }

        @Override
        public void onPurchasesUpdated(List<Purchase> purchaseList) {
            for (Purchase purchase : purchaseList) {
                switch (purchase.getSku()) {
                    case PremiumDelegate.SKU_ID:
                        // Only toast if first time updating the premium purchase
                        if (!mIsPremium)
                            Toast.makeText(mActivity.getApplicationContext(), R.string.welcome_premium, Toast.LENGTH_LONG).show();
                        mIsPremium = true;
                        saveData();
                    break;
                }
            }
            mActivity.showRefreshedUi();
        }
    }

    //TODO: The powers that be recommend you save data in a secure way to prevent tampering
    private void saveData() {
        // Save to shared preferences
        SharedPreferences main_log = mActivity.getSharedPreferences(mActivity.MAIN_PREFS, 0);
        SharedPreferences.Editor editor = main_log.edit();
        editor.putBoolean("premium",mIsPremium);
        editor.apply();
    }

    private void loadData() {
        // Load from shared preferences
        SharedPreferences main_log = mActivity.getSharedPreferences(mActivity.MAIN_PREFS, 0);
        mIsPremium = main_log.getBoolean("premium", false);
    }
}