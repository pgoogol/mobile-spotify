package com.spotify.playlistmanager.di

import com.spotify.playlistmanager.domain.dj.BlockGenerator
import com.spotify.playlistmanager.domain.dj.LiveAssistant
import com.spotify.playlistmanager.domain.dj.PartyPlanner
import com.spotify.playlistmanager.domain.dj.StyleDetector
import com.spotify.playlistmanager.domain.dj.TrackAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection dla algorytmu DJ-asystenta (bloki, plan imprezy, live).
 *
 * Używane w `StepwiseViewModel` (Krok ma 3 tryby: krok-po-kroku, plan imprezy,
 * live bloki). Klasy są bezstanowe → `@Singleton`.
 */
@Module
@InstallIn(SingletonComponent::class)
object DjModule {

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
