package com.petapp.ads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.petapp.data.UserSubscription
import com.petapp.features.FeatureGate

/**
 * Banner Ad Composable
 * 
 * Muestra un banner de AdMob solo para usuarios FREE.
 * Se oculta automáticamente para usuarios Premium/Family.
 */
@Composable
fun AdBanner(
    subscription: UserSubscription,
    modifier: Modifier = Modifier
) {
    val shouldShowAds = remember(subscription) {
        FeatureGate.shouldShowAds(subscription)
    }
    
    AnimatedVisibility(
        visible = shouldShowAds,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        AdBannerView(modifier = modifier)
    }
}

/**
 * Vista de AdView integrada en Compose
 */
@Composable
private fun AdBannerView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = AdManager.BANNER_AD_UNIT_ID
        }
    }
    
    DisposableEffect(adView) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        
        onDispose {
            adView.destroy()
        }
    }
    
    AndroidView(
        factory = { adView },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    )
}

/**
 * Banner adaptativo que se ajusta al ancho de la pantalla
 */
@Composable
fun AdaptiveBanner(
    subscription: UserSubscription,
    adManager: AdManager,
    modifier: Modifier = Modifier
) {
    val isAdEnabled by adManager.isAdEnabled.collectAsState()
    val shouldShowAds = remember(subscription) {
        FeatureGate.shouldShowAds(subscription)
    }
    
    AnimatedVisibility(
        visible = isAdEnabled && shouldShowAds,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        AdaptiveBannerView(modifier = modifier)
    }
}

@Composable
private fun AdaptiveBannerView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val adView = remember {
        AdView(context).apply {
            // Banner adaptativo
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                context,
                AdSize.FULL_WIDTH
            ))
            adUnitId = AdManager.BANNER_AD_UNIT_ID
        }
    }
    
    DisposableEffect(adView) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        
        onDispose {
            adView.destroy()
        }
    }
    
    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Contenedor que oculta su contenido para usuarios premium
 * y lo muestra para usuarios free
 */
@Composable
fun FreeOnlyContent(
    subscription: UserSubscription,
    content: @Composable () -> Unit
) {
    val shouldShow = remember(subscription) {
        FeatureGate.shouldShowAds(subscription)
    }
    
    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        content()
    }
}

/**
 * Contenedor que muestra contenido solo para usuarios premium
 */
@Composable
fun PremiumOnlyContent(
    subscription: UserSubscription,
    content: @Composable () -> Unit
) {
    val shouldShow = remember(subscription) {
        !FeatureGate.shouldShowAds(subscription)
    }
    
    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        content()
    }
}
