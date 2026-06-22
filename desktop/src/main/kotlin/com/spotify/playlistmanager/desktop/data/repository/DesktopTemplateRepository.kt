package com.spotify.playlistmanager.desktop.data.repository

import com.spotify.playlistmanager.domain.model.GeneratorTemplate
import com.spotify.playlistmanager.domain.repository.IGeneratorTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Plikowy magazyn szablonów generatora dla desktopu — odpowiednik Roomowego
 * `IGeneratorTemplateRepository` z `:app`. Zapisuje do
 * `~/.spotify-playlist-manager/templates.json` przez kotlinx.serialization
 * (modele [GeneratorTemplate]/`TemplateSource` są `@Serializable` w :shared).
 *
 * Lista jest zawsze utrzymywana posortowana malejąco po `updatedAt`.
 */
class DesktopTemplateRepository : IGeneratorTemplateRepository {

    private val file = File(
        System.getProperty("user.home"),
        ".spotify-playlist-manager/templates.json",
    )
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val serializer = ListSerializer(GeneratorTemplate.serializer())

    private val state = MutableStateFlow(load())
    private var nextId: Long = (state.value.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll(): Flow<List<GeneratorTemplate>> = state
    override fun observeCount(): Flow<Int> = state.map { it.size }

    override suspend fun getById(id: Long): GeneratorTemplate? =
        state.value.find { it.id == id }

    override suspend fun save(template: GeneratorTemplate): Long {
        val now = System.currentTimeMillis()
        val id = nextId++
        val saved = template.copy(id = id, createdAt = now, updatedAt = now)
        state.value = (state.value + saved).sortedByDescending { it.updatedAt }
        persist()
        return id
    }

    override suspend fun overwrite(template: GeneratorTemplate) {
        val now = System.currentTimeMillis()
        state.value = state.value
            .map { if (it.id == template.id) template.copy(updatedAt = now) else it }
            .sortedByDescending { it.updatedAt }
        persist()
    }

    override suspend fun rename(id: Long, newName: String) {
        val now = System.currentTimeMillis()
        state.value = state.value
            .map { if (it.id == id) it.copy(name = newName, updatedAt = now) else it }
            .sortedByDescending { it.updatedAt }
        persist()
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(serializer, state.value))
        }
    }

    private fun load(): List<GeneratorTemplate> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        json.decodeFromString(serializer, file.readText()).sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())
}
