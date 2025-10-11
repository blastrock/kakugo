package org.kaqui.settings

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import org.jetbrains.anko.toast
import org.kaqui.R
import org.kaqui.Separator
import org.kaqui.TopBar
import org.kaqui.model.Database
import org.kaqui.theme.KakugoTheme

data class SavedSelectionUiState(
    val mode: SelectionMode = SelectionMode.KANJI,
    val selections: List<Database.SavedSelection> = emptyList(),
    val showDeleteDialog: Boolean = false,
    val selectionToDelete: Database.SavedSelection? = null
)

class SavedSelectionsViewModel : ViewModel() {
    var uiState by mutableStateOf(SavedSelectionUiState())
        private set

    private lateinit var db: Database
    private lateinit var mode: SelectionMode

    fun initialize(db: Database, mode: SelectionMode) {
        this.db = db
        this.mode = mode
        uiState = uiState.copy(mode = mode)
        loadSelections()
    }

    private fun loadSelections() {
        val selections = when (mode) {
            SelectionMode.KANJI -> db.listKanjiSelections()
            SelectionMode.WORD -> db.listWordSelections()
            else -> throw IllegalArgumentException("SavedSelectionsActivity only supports KANJI and WORD modes")
        }
        uiState = uiState.copy(selections = selections)
    }

    fun showDeleteDialog(selection: Database.SavedSelection) {
        uiState = uiState.copy(
            showDeleteDialog = true,
            selectionToDelete = selection
        )
    }

    fun dismissDeleteDialog() {
        uiState = uiState.copy(
            showDeleteDialog = false,
            selectionToDelete = null
        )
    }

    fun deleteSelection() {
        val selection = uiState.selectionToDelete ?: return
        when (mode) {
            SelectionMode.KANJI -> db.deleteKanjiSelection(selection.id)
            SelectionMode.WORD -> db.deleteWordSelection(selection.id)
            else -> throw IllegalArgumentException("SavedSelectionsActivity only supports KANJI and WORD modes")
        }
        dismissDeleteDialog()
        loadSelections()
    }

    fun restoreSelection(selection: Database.SavedSelection) {
        when (mode) {
            SelectionMode.KANJI -> db.restoreKanjiSelectionFrom(selection.id)
            SelectionMode.WORD -> db.restoreWordSelectionFrom(selection.id)
            else -> throw IllegalArgumentException("SavedSelectionsActivity only supports KANJI and WORD modes")
        }
    }
}

class SavedSelectionsActivity : ComponentActivity() {
    private val viewModel: SavedSelectionsViewModel by viewModels()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("mode", SelectionMode::class.java)!!
        } else {
            intent.getSerializableExtra("mode") as SelectionMode
        }

        val db = Database.getInstance(this)
        viewModel.initialize(db, mode)

        setContent {
            val uiState = viewModel.uiState

            SavedSelectionsScreen(
                uiState = uiState,
                onBackClick = { finish() },
                onItemClick = { selection ->
                    viewModel.restoreSelection(selection)
                    toast(getString(R.string.loaded_selection, selection.name))
                    finish()
                },
                onDeleteClick = viewModel::showDeleteDialog,
                onDeleteConfirm = {
                    val deletedName = viewModel.uiState.selectionToDelete?.name
                    viewModel.deleteSelection()
                    if (deletedName != null) {
                        toast(getString(R.string.deleted_selection, deletedName))
                    }
                },
                onDeleteDismiss = viewModel::dismissDeleteDialog
            )
        }
    }
}

@Composable
fun SavedSelectionsScreen(
    uiState: SavedSelectionUiState,
    onBackClick: () -> Unit,
    onItemClick: (Database.SavedSelection) -> Unit,
    onDeleteClick: (Database.SavedSelection) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit
) {
    val title = stringResource(R.string.saved_selections)

    KakugoTheme {
        Surface(color = MaterialTheme.colors.background) {
            Scaffold(
                topBar = {
                    TopBar(
                        title = title,
                        onBackClick = onBackClick
                    )
                }
            ) { paddingValues ->
                SavedSelectionsList(
                    selections = uiState.selections,
                    onItemClick = onItemClick,
                    onDeleteClick = onDeleteClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            if (uiState.showDeleteDialog && uiState.selectionToDelete != null) {
                DeleteConfirmationDialog(
                    selectionName = uiState.selectionToDelete.name,
                    onConfirm = onDeleteConfirm,
                    onDismiss = onDeleteDismiss
                )
            }
        }
    }
}

@Composable
fun SavedSelectionsList(
    selections: List<Database.SavedSelection>,
    onItemClick: (Database.SavedSelection) -> Unit,
    onDeleteClick: (Database.SavedSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
    ) {
        items(
            items = selections,
            key = { it.id }
        ) { selection ->
            SavedSelectionRow(
                selection = selection,
                onItemClick = onItemClick,
                onDeleteClick = onDeleteClick
            )
            Separator()
        }
    }
}

@Composable
fun SavedSelectionRow(
    selection: Database.SavedSelection,
    onItemClick: (Database.SavedSelection) -> Unit,
    onDeleteClick: (Database.SavedSelection) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(selection) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context.getString(R.string.selection_presentation, selection.name, selection.count),
            modifier = Modifier.weight(1f),
            fontSize = 16.sp
        )

        IconButton(
            onClick = { onDeleteClick(selection) }
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    selectionName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.confirm_delete))
        },
        text = {
            Text(stringResource(R.string.confirm_delete_selection, selectionName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true, name = "Saved Selections Screen")
@Composable
fun PreviewSavedSelectionsScreen() {
    val sampleSelections = listOf(
        Database.SavedSelection(1, "JLPT N5", 80),
        Database.SavedSelection(2, "Grade 1", 80),
        Database.SavedSelection(3, "My Custom Selection", 150),
        Database.SavedSelection(4, "Common Kanji", 300)
    )

    val sampleUiState = SavedSelectionUiState(
        mode = SelectionMode.KANJI,
        selections = sampleSelections,
        showDeleteDialog = false
    )

    KakugoTheme {
        SavedSelectionsScreen(
            uiState = sampleUiState,
            onBackClick = { },
            onItemClick = { },
            onDeleteClick = { },
            onDeleteConfirm = { },
            onDeleteDismiss = { }
        )
    }
}

@Preview(showBackground = true, name = "Delete Dialog")
@Composable
fun PreviewDeleteDialog() {
    KakugoTheme {
        DeleteConfirmationDialog(
            selectionName = "JLPT N5",
            onConfirm = { },
            onDismiss = { }
        )
    }
}
