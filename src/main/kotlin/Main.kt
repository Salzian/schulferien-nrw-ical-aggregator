import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.FluentCalendar
import net.fortuna.ical4j.util.CompatibilityHints
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val BASE_URL = "https://www.schulministerium.nrw"
private const val FERIEN_URL =
    "$BASE_URL/ferienordnung-fuer-nordrhein-westfalen-fuer-die-schuljahre-bis-202930"
private const val FINAL_CALENDAR_FILE_NAME = "ferien.ics"

suspend fun main() = job().join()

private fun job() =
    CoroutineScope(Dispatchers.Default).launch {
        setCompatibilityHints()

        val httpClient = buildHttpClient()

        val documentBody = httpClient.get(FERIEN_URL).body<String>()
        val document = Jsoup.parse(documentBody)

        val uriList = extractCalendarsUriList(document)

        val tempFiles = uriList.map { downloadFile(httpClient, it) }.flowOn(Dispatchers.IO)

        val individualCalendars =
            tempFiles
                .map {
                    val stream = FileInputStream(it.toFile())
                    CalendarBuilder().build(stream)
                }
                .flowOn(Dispatchers.IO)

        val masterCalendar =
            Calendar().withDefaults().apply {
                individualCalendars.collect { individualCalendar ->
                    individualCalendar.componentList.all.asFlow().collect { component ->
                        withComponent(component)
                    }
                }
            }

        writeCalendarToFile(masterCalendar)
    }

/** Extracts the URIs of the calendars from the given document. */
private fun extractCalendarsUriList(document: Document) =
    document.select("a").asFlow().mapNotNull { element ->
        element
            .attr("href")
            .takeIf { calendarUrlPathCriteria(it) }
            ?.run { URI.create("$BASE_URL/$this") }
            ?.toURL()
    }

/**
 * Checks if the given string is a calendar URI. The website offers both iOS and Android calendars,
 * but we only want the Android ones as they might contain information better targeted for Google
 * Calendar.
 */
private fun calendarUrlPathCriteria(uriString: String) =
    uriString.contains("/system/files/media/document/file/") && !uriString.contains("ios")

/**
 * Builds an HTTP client for the base URL and installs a cache plugin to store the downloaded
 * calendars.
 */
private fun buildHttpClient() = HttpClient(CIO) { defaultRequest { url(BASE_URL) } }

/** Writes the given calendar to a file with the name [FINAL_CALENDAR_FILE_NAME]. */
suspend fun writeCalendarToFile(masterCalendar: FluentCalendar) = coroutineScope {
    val fileOutputStream = FileOutputStream(FINAL_CALENDAR_FILE_NAME)
    withContext(Dispatchers.IO) {
        CalendarOutputter().output(masterCalendar.fluentTarget, fileOutputStream)
    }
}

/**
 * Sets compatibility hints for the iCal4j library. This is necessary because the library is very
 * strict and the files from the website might not be 100% compliant with the iCal standard.
 */
private fun setCompatibilityHints() {
    CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true)
    CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true)
    CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_VALIDATION, true)
    CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true)
}

/**
 * Downloads the file from the given URL and returns the path to the downloaded file. The file is
 * stored in a temporary directory.
 */
suspend fun downloadFile(httpClient: HttpClient, url: URL): Path = coroutineScope {
    val response = httpClient.get(url) {}
    val content = response.body<ByteArray>()

    val fileName = url.path.substringAfterLast("/").substringBeforeLast(".")
    val fileExtension = url.path.substringAfterLast(".")

    withContext(Dispatchers.IO) {
        val tempFile = Files.createTempFile(fileName, ".$fileExtension")
        Files.write(tempFile, content)
    }
}
