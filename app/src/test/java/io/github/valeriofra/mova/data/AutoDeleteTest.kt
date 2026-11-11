package io.github.valeriofra.mova.data

import android.app.Application
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.Platform
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.data.db.DownloadStatus
import io.github.valeriofra.mova.data.db.MovaDatabase
import io.github.valeriofra.mova.data.settings.SettingsRepository
import io.github.valeriofra.mova.download.DownloadScheduler
import io.github.valeriofra.mova.download.PartialFiles
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutoDeleteTest {

    private val context = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(context, MovaDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.downloadDao()
    private val now = TimeUnit.DAYS.toMillis(100)
    private val repository = DownloadRepository(
        dao,
        DownloadScheduler(context, SettingsRepository(context)),
        PartialFiles(context),
        clock = { now },
    )

    /** Arquivos "no aparelho": a URI some da lista quando é apagada. */
    private class FakeFiles(vararg existing: String) : SavedFiles {
        val present = existing.toMutableSet()
        val protected = mutableSetOf<String>()
        override fun delete(uri: String): Boolean = uri !in protected && present.remove(uri)
        override fun exists(uri: String): Boolean = uri in present
    }

    @After
    fun close() = db.close()

    private fun daysAgo(days: Int) = now - TimeUnit.DAYS.toMillis(days.toLong())

    private fun task(
        name: String,
        status: DownloadStatus,
        finishedDaysAgo: Int,
        uri: String? = "content://media/$name",
        missing: Boolean = false,
    ) = DownloadEntity(
        url = "https://exemplo.com/$name", sourceUrl = "https://exemplo.com/$name", platform = Platform.DIRECT,
        fileName = name, title = name, mimeType = "video/mp4", mediaKind = MediaKind.VIDEO, totalBytes = 10,
        status = status, supportsResume = true, etag = null, lastModified = null,
        outputUri = if (status == DownloadStatus.COMPLETED) uri else null, fileMissing = missing,
        createdAt = daysAgo(finishedDaysAgo), updatedAt = daysAgo(finishedDaysAgo),
        completedAt = if (status == DownloadStatus.COMPLETED) daysAgo(finishedDaysAgo) else null,
    )

    @Test
    fun deletesOnlyWhatIsOlderThanThePeriod() = runTest {
        dao.insertAll(
            listOf(
                task("antigo.mp4", DownloadStatus.COMPLETED, finishedDaysAgo = 20),
                task("recente.mp4", DownloadStatus.COMPLETED, finishedDaysAgo = 2),
                task("erro-antigo.mp4", DownloadStatus.FAILED, finishedDaysAgo = 20),
                task("apagado-antes.mp4", DownloadStatus.COMPLETED, finishedDaysAgo = 30, missing = true),
                task("na-fila.mp4", DownloadStatus.QUEUED, finishedDaysAgo = 40),
                task("pausado.mp4", DownloadStatus.PAUSED, finishedDaysAgo = 40),
            ),
        )
        val files = FakeFiles("content://media/antigo.mp4", "content://media/recente.mp4")

        val result = repository.deleteOlderThan(15, files)

        assertEquals(CleanupResult(filesDeleted = 1, recordsRemoved = 3), result)
        assertEquals(setOf("content://media/recente.mp4"), files.present)
        val remaining = dao.observeRecent(100).first().map { it.fileName }.toSet()
        assertEquals(setOf("recente.mp4", "na-fila.mp4", "pausado.mp4"), remaining)
    }

    @Test
    fun keepsRecordWhenFileCannotBeDeleted() = runTest {
        dao.insertAll(
            listOf(
                task("sem-permissao.mp4", DownloadStatus.COMPLETED, finishedDaysAgo = 10),
                task("ja-sumiu.mp4", DownloadStatus.COMPLETED, finishedDaysAgo = 10),
            ),
        )
        val files = FakeFiles("content://media/sem-permissao.mp4").apply { protected += "content://media/sem-permissao.mp4" }

        val result = repository.deleteOlderThan(3, files)

        // O que não pôde ser apagado fica para a próxima tentativa; o que já sumiu sai do app.
        assertEquals(CleanupResult(filesDeleted = 0, recordsRemoved = 1), result)
        assertEquals(listOf("sem-permissao.mp4"), dao.observeRecent(100).first().map { it.fileName })
    }

    @Test
    fun offDoesNothing() = runTest {
        dao.insertAll(listOf(task("antigo.mp4", DownloadStatus.COMPLETED, finishedDaysAgo = 90)))
        assertEquals(CleanupResult.NONE, repository.deleteOlderThan(0, FakeFiles("content://media/antigo.mp4")))
        assertEquals(1, dao.observeRecent(100).first().size)
    }
}
