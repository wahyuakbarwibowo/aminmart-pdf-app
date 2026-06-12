package com.aminmart.pdftools.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aminmart.pdftools.data.PdfFile
import com.aminmart.pdftools.data.PdfOperationResult
import com.aminmart.pdftools.utils.FileUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Shared ViewModel for the PDF tool activities. Holds the selected file(s),
 * the output file, and the running operation so all of it survives
 * configuration changes (screen rotation).
 */
class PdfToolViewModel(application: Application) : AndroidViewModel(application) {

    var selectedFile: File? = null
    var selectedFileLabel: String? = null
    var totalPages: Int = 0
    var outputFile: File? = null

    /** Backing list for MergePdfActivity's adapter */
    val mergeFiles: MutableList<PdfFile> = mutableListOf()

    /** Whether the terminal Success/Error of the current run was already toasted */
    var resultNotified = false

    private val _operationState = MutableLiveData<PdfOperationResult?>()
    val operationState: LiveData<PdfOperationResult?> = _operationState

    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    fun run(operation: Flow<PdfOperationResult>) {
        if (isRunning) return
        resultNotified = false
        job = viewModelScope.launch {
            operation.collect { _operationState.value = it }
        }
    }

    override fun onCleared() {
        FileUtils.cleanTempFiles(getApplication())
    }
}
