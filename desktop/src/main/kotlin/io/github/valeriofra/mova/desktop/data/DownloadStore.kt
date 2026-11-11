package io.github.valeriofra.mova.desktop.data

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer

/**
 * Downloads (fila e histórico) em memória, gravados em `downloads.json`.
 * O progresso muda várias vezes por segundo; a gravação em disco é agrupada.
 */
@OptIn(FlowPreview::class)
class DownloadStore(file: File, scope: CoroutineScope, private val clock: () -> Long = System::currentTimeMillis) {
    private val storage = JsonFile(file, ListSerializer(DownloadTask.serializer()))
    private val _tasks = MutableStateFlow(
        // Um download "em andamento" ao abrir o app foi interrompido: volta para a fila.
        (storage.read() ?: emptyList()).map { if (it.status == TaskStatus.RUNNING) it.copy(status = TaskStatus.QUEUED, bytesPerSecond = 0) else it },
    )
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    init {
        _tasks.drop(1).debounce(SAVE_DELAY_MS).onEach { flush() }.launchIn(CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    }

    val all: List<DownloadTask> get() = _tasks.value

    fun get(id: Long): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    /** Acrescenta tarefas novas (os ids são atribuídos aqui). */
    fun insert(items: List<DownloadTask>): List<DownloadTask> {
        var inserted = emptyList<DownloadTask>()
        _tasks.update { current ->
            var next = (current.maxOfOrNull { it.id } ?: 0L) + 1
            inserted = items.map { it.copy(id = next++) }
            current + inserted
        }
        return inserted
    }

    /** Altera uma tarefa; devolve a versão nova (ou null se não existe / não mudou). */
    fun update(id: Long, transform: (DownloadTask) -> DownloadTask?): DownloadTask? {
        var result: DownloadTask? = null
        _tasks.update { current ->
            current.map { task ->
                if (task.id != id) {
                    task
                } else {
                    val changed = transform(task)
                    if (changed == null || changed == task) task else changed.copy(updatedAt = clock()).also { result = it }
                }
            }
        }
        return result
    }

    fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        _tasks.update { current -> current.filterNot { it.id in set } }
    }

    /** Grava já (ao fechar o app). */
    fun flush() {
        runCatching { storage.write(_tasks.value) }
    }

    private companion object {
        const val SAVE_DELAY_MS = 800L
    }
}
