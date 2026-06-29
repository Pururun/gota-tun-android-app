package net.mullvad.gotatunandroid.di

import android.content.Context
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlin.reflect.KClass
import net.mullvad.gotatunandroid.domain.AppSettingsRepository
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.ConnectController
import net.mullvad.gotatunandroid.vpn.VpnController
import net.mullvad.gotatunandroid.vpn.VpnControllerImpl

@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
  val vpnController: VpnController
  val configRepository: ConfigRepository
  val appSettingsRepository: AppSettingsRepository
  val connectController: ConnectController

  @SingleIn(AppScope::class)
  @Provides
  fun provideConfigRepository(context: Context): ConfigRepository = ConfigRepository(context)

  @SingleIn(AppScope::class)
  @Provides
  fun provideAppSettingsRepository(context: Context): AppSettingsRepository =
      AppSettingsRepository(context)

  @SingleIn(AppScope::class)
  @Provides
  fun provideVpnController(context: Context): VpnController = VpnControllerImpl(context)

  @SingleIn(AppScope::class)
  @Provides
  fun provideConnectController(
      vpnController: VpnController,
      configRepository: ConfigRepository,
  ): ConnectController =
      ConnectController(vpnController = vpnController, configRepository = configRepository)

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides context: Context): AppGraph
  }

  @Inject
  @ContributesBinding(AppScope::class)
  @SingleIn(AppScope::class)
  class MyViewModelFactory(
      override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
      override val assistedFactoryProviders:
          Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
      override val manualAssistedFactoryProviders:
          Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
  ) : MetroViewModelFactory()
}
