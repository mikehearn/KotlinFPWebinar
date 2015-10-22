package typeahead

import javafx.application.Application
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import javafx.stage.Stage
import nl.komponents.kovenant.async
import nl.komponents.kovenant.jfx.configureKovenant
import nl.komponents.kovenant.ui.successUi
import org.reactfx.EventStreams
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.Executors
import kotlin.concurrent.currentThread
import kotlin.util.measureTimeMillis

val thread = Executors.newSingleThreadExecutor()

class App : Application() {
    val ngrams = ConcurrentSkipListSet<NGram>()

    override fun start(stage: Stage) {
        configureKovenant()

        val edit = TextField()
        val completionsList = ListView<String>()

        val vbox = VBox(edit, completionsList).apply {
            spacing = 15.0
            padding = Insets(15.0)
        }

        val edits = EventStreams.valuesOf(edit.textProperty()).forgetful()
        val fewerEdits = edits.successionEnds(Duration.ofMillis(100)).filter { it.isNotBlank() }

        val inBackground = fewerEdits.threadBridgeFromFx(thread)
        val completions = inBackground.map { ngrams.complete(it) }
        val inForeground = completions.threadBridgeToFx(thread)

        val lastResult = inForeground.map { FXCollections.observableList(it) }.toBinding(FXCollections.emptyObservableList())
        completionsList.itemsProperty().bind(lastResult)

        val guard = edits.suspend()
        async {
            load()
        } successUi {
            guard.close()
        }

        stage.scene = Scene(vbox)
        stage.show()
    }

    private fun load() {
        val path = Paths.get("words.txt")
        val t = measureTimeMillis {
            Files.lines(path).parallel().forEach { line ->
                val words = line.substringAfter('\t').replace('\t', ' ').toLowerCase()
                val freq = line.substringBefore('\t').toInt()
                ngrams.add(NGram(words, freq))
            }
        }
        println("$currentThread: Loaded file in $t msec")
    }
}

fun main(args: Array<String>) {
    Application.launch(App::class.java, *args)
    System.exit(0)
}