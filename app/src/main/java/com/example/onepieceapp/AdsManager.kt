package com.example.onepieceapp

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Gère le chargement et l'affichage de la pub récompensée (AdMob) qui permet de
 * révéler le personnage après 18 essais (voir [GameViewModel.canReveal]).
 *
 * Utilise pour l'instant l'ID de TEST officiel de Google (aucun revenu réel,
 * n'affiche que des pubs de démo Google) : avant de publier sur le Play Store,
 * remplace [REVEAL_AD_UNIT_ID] par l'ID d'unité pub "récompensée" créé dans ta
 * console AdMob (https://apps.admob.com), et l'ID d'application dans
 * AndroidManifest.xml (com.google.android.gms.ads.APPLICATION_ID).
 */
object AdsManager {

    private const val REVEAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    private var initialized = false
    private var rewardedAd: RewardedAd? = null
    private var loading = false

    /** À appeler une fois, typiquement dans MainActivity.onCreate. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        MobileAds.initialize(context.applicationContext) {}
        preload(context)
    }

    /** Précharge une pub récompensée pour qu'elle soit prête à s'afficher instantanément. */
    fun preload(context: Context) {
        if (rewardedAd != null || loading) return
        loading = true
        RewardedAd.load(
            context,
            REVEAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    loading = false
                    rewardedAd = null
                    Log.w("AdsManager", "Échec de chargement de la pub récompensée : ${adError.message}")
                }
            }
        )
    }

    /**
     * Affiche la pub récompensée si elle est prête. Appelle [onReward] uniquement si
     * l'utilisateur l'a regardée jusqu'au bout, [onNoRewardOrUnavailable] sinon (pub
     * fermée trop tôt, échec d'affichage, ou aucune pub encore chargée).
     */
    fun show(activity: Activity, onReward: () -> Unit, onNoRewardOrUnavailable: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            preload(activity)
            onNoRewardOrUnavailable()
            return
        }
        rewardedAd = null
        var rewardEarned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity)
                if (!rewardEarned) onNoRewardOrUnavailable()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                preload(activity)
                onNoRewardOrUnavailable()
            }
        }
        ad.show(activity) {
            rewardEarned = true
            onReward()
        }
    }
}
