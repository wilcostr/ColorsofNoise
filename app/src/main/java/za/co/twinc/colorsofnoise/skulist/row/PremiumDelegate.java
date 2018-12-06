/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package za.co.twinc.colorsofnoise.skulist.row;

import com.android.billingclient.api.BillingClient.SkuType;
import za.co.twinc.colorsofnoise.R;
import za.co.twinc.colorsofnoise.billing.BillingProvider;

/**
 * Handles Ui specific to "premium" - non-consumable in-app item row
 */
public class PremiumDelegate extends UiManagingDelegate {
    public static final String SKU_ID = "premium";

    PremiumDelegate(BillingProvider billingProvider) {
        super(billingProvider);
    }

    @Override
    public @SkuType String getType() {
        return SkuType.INAPP;
    }

    @Override
    public void onBindViewHolder(SkuRowData data, RowViewHolder holder) {
        super.onBindViewHolder(data, holder);
        int textId = mBillingProvider.isPremiumPurchased() ? R.string.button_own
                : R.string.button_buy;
        holder.button.setText(textId);
        holder.skuIcon.setImageResource(R.mipmap.ic_launcher);
    }

    @Override
    public void onButtonClicked(SkuRowData data) {
        if (data != null && mBillingProvider.isPremiumPurchased()) {
            showAlreadyPurchasedToast();
        }  else {
            super.onButtonClicked(data);
        }
    }
}

