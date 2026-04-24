package net.mullvad.gotatunandroid.di

import android.content.Context
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import net.mullvad.gotatunandroid.domain.AppSettingsRepository
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.ui.navigation.NavigationViewModel
import net.mullvad.gotatunandroid.vpn.VpnController
import net.mullvad.gotatunandroid.vpn.VpnControllerImpl

@DependencyGraph
interface AppGraph {
    val vpnController: VpnController
    val navigationViewModel: NavigationViewModel
    val configRepository: ConfigRepository
    val appSettingsRepository: AppSettingsRepository

    @Provides
    fun provideConfigRepository(context: Context): ConfigRepository = ConfigRepository(context)

    @Provides
    fun provideAppSettingsRepository(context: Context): AppSettingsRepository =
        AppSettingsRepository(context)

    @Provides
    fun provideVpnController(context: Context): VpnController = VpnControllerImpl(context)

    @Provides
    fun provideNavigationViewModel(): NavigationViewModel = NavigationViewModel()

    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides context: Context): AppGraph
    }
}
