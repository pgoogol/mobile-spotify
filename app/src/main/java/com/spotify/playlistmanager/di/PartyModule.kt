package com.spotify.playlistmanager.di

import com.spotify.playlistmanager.data.local.PartyStateStore
import com.spotify.playlistmanager.domain.dj.BlockGenerator
import com.spotify.playlistmanager.domain.dj.IPartyStateStore
import com.spotify.playlistmanager.domain.dj.LiveAssistant
import com.spotify.playlistmanager.domain.dj.PartyPlanner
import com.spotify.playlistmanager.domain.dj.StyleDetector
import com.spotify.playlistmanager.domain.dj.TrackAnalyzer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection dla generatora "Impreza DJ".
 *
 * Wszystkie klasy algorytmu są bezstanowe (`StyleDetector`, `TrackAnalyzer`,
 * `BlockGenerator`, `PartyPlanner`, `LiveAssistant`) — `@Singleton` reuse'uje
 * jedną instancję dla całej aplikacji.
 */
@Module
@InstallIn(SingletonComponent::class)
object PartyModule {

    @Provides
    @Singleton
    fun provideStyleDetector(): StyleDetector = StyleDetector()

    @Provides
    @Singleton
    fun provideTrackAnalyzer(detector: StyleDetector): TrackAnalyzer = TrackAnalyzer(detector)

    @Provides
    @Singleton
    fun provideBlockGenerator(): BlockGenerator = BlockGenerator()

    @Provides
    @Singleton
    fun providePartyPlanner(blockGenerator: BlockGenerator): PartyPlanner =
        PartyPlanner(blockGenerator)

    @Provides
    @Singleton
    fun provideLiveAssistant(blockGenerator: BlockGenerator): LiveAssistant =
        LiveAssistant(blockGenerator)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PartyBindings {

    @Binds
    @Singleton
    abstract fun bindPartyStateStore(impl: PartyStateStore): IPartyStateStore
}
