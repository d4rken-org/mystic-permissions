package eu.darken.myperm.common.upgrade.core

import android.app.Activity
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.myperm.R
import eu.darken.myperm.common.coroutine.AppScope
import eu.darken.myperm.common.coroutine.DispatcherProvider
import eu.darken.myperm.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.myperm.common.debug.logging.asLog
import eu.darken.myperm.common.debug.logging.log
import eu.darken.myperm.common.debug.logging.logTag
import eu.darken.myperm.common.error.asErrorDialogBuilder
import eu.darken.myperm.common.flow.replayingShare
import eu.darken.myperm.common.upgrade.UpgradeRepo
import eu.darken.myperm.common.upgrade.core.data.BillingData
import eu.darken.myperm.common.upgrade.core.data.BillingDataRepo
import eu.darken.myperm.common.upgrade.core.data.PurchasedSku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val billingDataRepo: BillingDataRepo,
    private val billingCache: BillingCache,
) : UpgradeRepo {

    private var lastProStateAt: Long
        get() = billingCache.lastProStateAt.value
        set(value) = billingCache.lastProStateAt.update { value }

    override val upgradeInfo: Flow<UpgradeRepo.Info> = billingDataRepo.billingData
        .map { data -> // Only relinquish pro state if we haven't had it for a while
            val now = System.currentTimeMillis()
            val proSku = data.getProSku()
            log(TAG) { "now=$now, lastProStateAt=$lastProStateAt, data=${data}" }
            when {
                proSku != null -> {
                    // If we are pro refresh timestamp
                    lastProStateAt = now
                    Info(billingData = data)
                }
                (now - lastProStateAt) < 6 * 60 * 1000L -> { // 6 hours
                    log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                    Info(gracePeriod = true, billingData = null)
                }
                else -> {
                    Info(billingData = data)
                }
            }
        }
        .catch {
            // Ignore Google Play errors if the last pro state was recent
            val now = System.currentTimeMillis()
            log(TAG) { "now=$now, lastProStateAt=$lastProStateAt, error=$it" }
            if ((now - lastProStateAt) < 7 * 24 * 60 * 1000L) { // 7 days
                log(TAG, VERBOSE) { "We are not pro, but were recently, and just and an error, what is GPlay doing???" }
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                throw it
            }
        }
        .replayingShare(scope)

    override fun launchBillingFlow(activity: Activity) {
        MaterialAlertDialogBuilder(activity).apply {
            setIcon(R.drawable.ic_heart)
            setTitle(R.string.upgrade_myperm_label)
            setMessage(R.string.upgrade_myperm_description)
            setPositiveButton(R.string.upgrade_buy_pro_action) { _, _ ->
                scope.launch {
                    try {
                        billingDataRepo.startIapFlow(activity, MyPermSku.PRO_UPGRADE.sku)
                    } catch (e: Exception) {
                        log(TAG) { "startIapFlow failed:${e.asLog()}" }
                        withContext(dispatcherProvider.Main) {
                            e.asErrorDialogBuilder(activity).show()
                        }
                    }
                }
            }
            setNeutralButton(R.string.general_check_action) { dialog, _ ->
                log(TAG) { "recheck()" }
                scope.launch {
                    try {
                        val data = billingDataRepo.getIapData()
                        log(TAG) { "Recheck successful: $data" }
                        withContext(dispatcherProvider.Main) {
                            if (data.purchases.isEmpty()) {
                                Toast.makeText(
                                    activity,
                                    R.string.upgrades_no_purchases_found_check_account,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        log(TAG) { "Recheck failed:${e.asLog()}" }
                        withContext(dispatcherProvider.Main) {
                            e.asErrorDialogBuilder(activity).show()
                        }
                    }
                }
            }
        }.show()
    }

    data class Info(
        private val gracePeriod: Boolean = false,
        private val billingData: BillingData?,
    ) : UpgradeRepo.Info {

        override val type: UpgradeRepo.Type
            get() = UpgradeRepo.Type.GPLAY

        override val isPro: Boolean
            get() = billingData?.getProSku() != null || gracePeriod

        override val upgradedAt: Instant?
            get() = billingData
                ?.getProSku()
                ?.purchase?.purchaseTime
                ?.let { Instant.ofEpochMilli(it) }
    }

    companion object {
        private fun BillingData.getProSku(): PurchasedSku? = purchasedSkus
            .firstOrNull { it.sku == MyPermSku.PRO_UPGRADE.sku }

        val TAG: String = logTag("Upgrade", "Gplay", "Control")
    }
}