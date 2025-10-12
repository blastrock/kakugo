package org.kaqui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import org.jetbrains.anko.*
import org.kaqui.startActivity
import org.kaqui.R
import org.kaqui.StatsBar
import org.kaqui.TopBar
import org.kaqui.model.Classification
import org.kaqui.model.Classifier
import org.kaqui.model.Database
import org.kaqui.model.LearningDbView
import org.kaqui.model.getClassifiers
import org.kaqui.model.name
import org.kaqui.theme.KakugoTheme
import java.io.Serializable
import kotlin.coroutines.CoroutineContext

data class ClassItem(
    val name: String,
    val stats: LearningDbView.Stats
)

data class ClassSelectionUiState(
    val mode: SelectionMode,
    val globalStats: LearningDbView.Stats,
    val classItems: List<ClassItem>
)

class ClassSelectionActivity : ComponentActivity(), CoroutineScope {
    private lateinit var dbView: LearningDbView
    private lateinit var mode: SelectionMode
    private lateinit var classifiers: List<Classifier>
    private var refreshTrigger by mutableStateOf(0)
    private var showMenu by mutableStateOf(false)
    private var showSaveDialog by mutableStateOf(false)
    private var saveDialogText by mutableStateOf("")

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()

        val classification = when (val classificationStr = PreferenceManager.getDefaultSharedPreferences(this).getString("item_classification", "jlpt")) {
            "jlpt" -> Classification.JlptLevel
            "rtk" -> Classification.RtkIndexRange
            "rtk6" -> Classification.Rtk6IndexRange
            else -> throw RuntimeException("unknown kanji classification: $classificationStr")
        }

        mode = intent.getSerializableExtra("mode") as SelectionMode

        dbView = when (mode) {
            SelectionMode.KANJI -> Database.getInstance(this).getKanjiView()
            SelectionMode.WORD -> Database.getInstance(this).getWordView()
            else -> throw IllegalArgumentException("ClassSelectionActivity only supports KANJI and WORD modes")
        }

        classifiers = getClassifiers(classification)

        setContent {
            val uiState = remember(refreshTrigger) { prepareUiState() }
            ClassSelectionScreen(
                uiState = uiState,
                onClassItemClick = { index -> onClassifierClick(classifiers[index]) },
                onBackClick = { finish() },
                onSearchClick = { onSearchClick() },
                showMenu = showMenu,
                onMenuToggle = { showMenu = it },
                onSaveSelection = { showSaveDialog = true },
                onLoadSelection = { startActivity<SavedSelectionsActivity>("mode" to mode as Serializable) },
                onSelectNone = {
                    dbView.setAllEnabled(false)
                    refreshTrigger++
                },
                onImportSelection = { importItems() }
            )

            if (showSaveDialog) {
                SaveSelectionDialog(
                    text = saveDialogText,
                    onTextChange = { saveDialogText = it },
                    onConfirm = {
                        saveSelection(saveDialogText)
                        showSaveDialog = false
                        saveDialogText = ""
                    },
                    onDismiss = {
                        showSaveDialog = false
                        saveDialogText = ""
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        // Trigger refresh when returning from other activities
        refreshTrigger++
    }

    private fun prepareUiState(): ClassSelectionUiState {
        val globalStats = dbView.getStats()
        val classItems = classifiers.map { classifier ->
            ClassItem(
                name = classifier.name(this),
                stats = dbView.withClassifier(classifier).getStats()
            )
        }
        return ClassSelectionUiState(
            mode = mode,
            globalStats = globalStats,
            classItems = classItems
        )
    }


    private fun onClassifierClick(classifier: Classifier) {
        startActivity(Intent(this, ItemSelectionActivity::class.java)
                .putExtra("mode", mode as Serializable)
                .putExtra("classifier", classifier))
    }

    private fun onSearchClick() {
        startActivity(Intent(this, ItemSearchActivity::class.java)
                .putExtra("mode", mode as Serializable))
    }

    private fun saveSelection(name: String) {
        when (mode) {
            SelectionMode.KANJI -> Database.getInstance(this).saveKanjiSelectionTo(name)
            SelectionMode.WORD -> Database.getInstance(this).saveWordSelectionTo(name)
            else -> throw IllegalArgumentException("ClassSelectionActivity only supports KANJI and WORD modes")
        }
        toast(getString(R.string.saved_selection, name))
    }

    private fun importItems() {
        val msg =
                if (mode == SelectionMode.KANJI)
                    R.string.import_kanji_help
                else
                    R.string.import_words_help

        alert(msg) {
            okButton { showSelectFileForImport() }
        }.show()
    }

    private fun showSelectFileForImport() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
                return
            }
        }

        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(intent, PICK_IMPORT_FILE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED })
            showSelectFileForImport()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != PICK_IMPORT_FILE)
            return super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null)
            return

        try {
            val items = contentResolver.openInputStream(data.data!!)!!.bufferedReader().readText()
            if (mode == SelectionMode.KANJI)
                Database.getInstance(this).setKanjiSelection(items)
            else
                Database.getInstance(this).setWordSelection(items)
        } catch (e: Exception) {
            Log.e(TAG, "Could not import file", e)
            Toast.makeText(this, getString(R.string.could_not_import_file, e.toString()), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "ClassSelectionActivity"

        private const val PICK_IMPORT_FILE = 1
    }
}

@Composable
fun ClassSelectionScreen(
    uiState: ClassSelectionUiState,
    onClassItemClick: (Int) -> Unit,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onSaveSelection: () -> Unit,
    onLoadSelection: () -> Unit,
    onSelectNone: () -> Unit,
    onImportSelection: () -> Unit
) {
    KakugoTheme {
        Surface(color = MaterialTheme.colors.background) {
            Scaffold(
                topBar = {
                    TopBar(
                        title = when (uiState.mode) {
                            SelectionMode.KANJI -> stringResource(R.string.kanji_selection)
                            SelectionMode.WORD -> stringResource(R.string.word_selection)
                            else -> stringResource(R.string.kanji_selection) // Shouldn't happen
                        },
                        onBackClick = onBackClick,
                        actions = {
                            IconButton(onClick = onSearchClick) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }
                            IconButton(onClick = { onMenuToggle(true) }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options"
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { onMenuToggle(false) }
                            ) {
                                DropdownMenuItem(onClick = {
                                    onMenuToggle(false)
                                    onSaveSelection()
                                }) {
                                    Text(stringResource(R.string.save_current_selection))
                                }
                                DropdownMenuItem(onClick = {
                                    onMenuToggle(false)
                                    onLoadSelection()
                                }) {
                                    Text(stringResource(R.string.load_selection))
                                }
                                DropdownMenuItem(onClick = {
                                    onMenuToggle(false)
                                    onSelectNone()
                                }) {
                                    Text(stringResource(R.string.select_none))
                                }
                                DropdownMenuItem(onClick = {
                                    onMenuToggle(false)
                                    onImportSelection()
                                }) {
                                    Text(stringResource(R.string.import_selection))
                                }
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Global stats section
                    StatsBar(
                        itemsDontKnow = uiState.globalStats.disabled,
                        itemsBad = uiState.globalStats.bad,
                        itemsMeh = uiState.globalStats.meh,
                        itemsGood = uiState.globalStats.good
                    )

                    // Class list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.classItems.size) { index ->
                            ClassListItem(
                                classItem = uiState.classItems[index],
                                onClick = { onClassItemClick(index) }
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClassListItem(
    classItem: ClassItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp
            )
    ) {
        Text(
            text = classItem.name,
            style = MaterialTheme.typography.body1,
            modifier = Modifier.padding(
                top = 8.dp,
                bottom = 8.dp
            )
        )

        StatsBar(
            itemsDontKnow = classItem.stats.disabled,
            itemsBad = classItem.stats.bad,
            itemsMeh = classItem.stats.meh,
            itemsGood = classItem.stats.good
        )
    }
}

@Preview(showBackground = true, name = "Class Selection - KANJI")
@Composable
fun ClassSelectionScreenPreviewKanji() {
    val sampleUiState = ClassSelectionUiState(
        mode = SelectionMode.KANJI,
        globalStats = LearningDbView.Stats(bad = 45, meh = 120, good = 380, disabled = 15),
        classItems = listOf(
            ClassItem("JLPT N5", LearningDbView.Stats(bad = 5, meh = 15, good = 80, disabled = 2)),
            ClassItem("JLPT N4", LearningDbView.Stats(bad = 12, meh = 35, good = 120, disabled = 5)),
            ClassItem("JLPT N3", LearningDbView.Stats(bad = 18, meh = 45, good = 150, disabled = 8)),
            ClassItem("JLPT N2", LearningDbView.Stats(bad = 8, meh = 20, good = 25, disabled = 0)),
            ClassItem("JLPT N1", LearningDbView.Stats(bad = 2, meh = 5, good = 5, disabled = 0)),
            ClassItem("Additional Kanji", LearningDbView.Stats(bad = 0, meh = 0, good = 0, disabled = 0))
        )
    )

    KakugoTheme {
        ClassSelectionScreen(
            uiState = sampleUiState,
            onClassItemClick = {},
            onBackClick = {},
            onSearchClick = {},
            showMenu = false,
            onMenuToggle = {},
            onSaveSelection = {},
            onLoadSelection = {},
            onSelectNone = {},
            onImportSelection = {}
        )
    }
}

@Preview(showBackground = true, name = "Class Selection - WORD")
@Composable
fun ClassSelectionScreenPreviewWord() {
    val sampleUiState = ClassSelectionUiState(
        mode = SelectionMode.WORD,
        globalStats = LearningDbView.Stats(bad = 25, meh = 85, good = 290, disabled = 10),
        classItems = listOf(
            ClassItem("JLPT N5", LearningDbView.Stats(bad = 3, meh = 10, good = 65, disabled = 1)),
            ClassItem("JLPT N4", LearningDbView.Stats(bad = 8, meh = 25, good = 95, disabled = 3)),
            ClassItem("JLPT N3", LearningDbView.Stats(bad = 10, meh = 30, good = 110, disabled = 4)),
            ClassItem("JLPT N2", LearningDbView.Stats(bad = 3, meh = 15, good = 18, disabled = 2)),
            ClassItem("JLPT N1", LearningDbView.Stats(bad = 1, meh = 5, good = 2, disabled = 0)),
            ClassItem("Additional Words", LearningDbView.Stats(bad = 0, meh = 0, good = 0, disabled = 0))
        )
    )

    KakugoTheme {
        ClassSelectionScreen(
            uiState = sampleUiState,
            onClassItemClick = {},
            onBackClick = {},
            onSearchClick = {},
            showMenu = false,
            onMenuToggle = {},
            onSaveSelection = {},
            onLoadSelection = {},
            onSelectNone = {},
            onImportSelection = {}
        )
    }
}

@Composable
fun SaveSelectionDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.enter_name_of_selection))
        },
        text = {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Selection name") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

