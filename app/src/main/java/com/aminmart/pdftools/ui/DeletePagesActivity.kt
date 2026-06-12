package com.aminmart.pdftools.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.aminmart.pdftools.data.PdfOperationResult
import com.aminmart.pdftools.databinding.ActivityDeletePagesBinding
import com.aminmart.pdftools.utils.FileUtils
import com.aminmart.pdftools.utils.PdfUtils

class DeletePagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeletePagesBinding
    private val viewModel: PdfToolViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeletePagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
        setupTextWatcher()
        restoreState()
        observeOperation()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.selectFileButton.setOnClickListener {
            filePickerLauncher.launch("application/pdf")
        }

        binding.processButton.setOnClickListener {
            showConfirmDialog()
        }

        binding.downloadButton.setOnClickListener {
            downloadFile()
        }
    }

    private fun setupTextWatcher() {
        binding.pagesToDeleteEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePagesPreview()
            }
        })
    }

    private fun restoreState() {
        viewModel.selectedFileLabel?.let { binding.selectedFileTextView.text = it }
        if (viewModel.totalPages > 0) {
            binding.totalPagesTextView.text = "${viewModel.totalPages} pages"
        }
    }

    private fun loadFile(uri: Uri) {
        try {
            val file = FileUtils.copyUriToTempFile(this, uri)
            val pageCount = PdfUtils.getPageCount(file)
            if (pageCount <= 0) {
                FileUtils.deleteTempFile(this, file)
                Toast.makeText(this, "Selected file is not a valid PDF", Toast.LENGTH_SHORT).show()
                return
            }

            viewModel.selectedFile = file
            viewModel.totalPages = pageCount
            viewModel.selectedFileLabel = "${FileUtils.getFileNameFromUri(this, uri) ?: "Unknown"}\n" +
                    "Size: ${FileUtils.formatFileSize(file.length())}"

            binding.selectedFileTextView.text = viewModel.selectedFileLabel
            binding.totalPagesTextView.text = "$pageCount pages"
            binding.pagesToDeleteEditText.setText("")
            binding.pagesPreviewTextView.text = ""
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePagesPreview() {
        if (viewModel.totalPages == 0) return

        val input = binding.pagesToDeleteEditText.text.toString()
        if (input.isBlank()) {
            binding.pagesPreviewTextView.text = ""
            return
        }

        val pagesToDelete = PdfUtils.parsePageRange(input, viewModel.totalPages)
        if (pagesToDelete.isNotEmpty()) {
            binding.pagesPreviewTextView.text = "Will delete: ${pagesToDelete.size} page(s) - Pages: ${pagesToDelete.joinToString(", ")}"
        } else {
            binding.pagesPreviewTextView.text = "No valid pages specified"
        }
    }

    private fun showConfirmDialog() {
        val file = viewModel.selectedFile
        if (file == null) {
            Toast.makeText(this, "Please select a PDF file first", Toast.LENGTH_SHORT).show()
            return
        }

        val input = binding.pagesToDeleteEditText.text.toString()
        if (input.isBlank()) {
            Toast.makeText(this, "Please enter pages to delete", Toast.LENGTH_SHORT).show()
            return
        }

        val pagesToDelete = PdfUtils.parsePageRange(input, viewModel.totalPages)
        if (pagesToDelete.isEmpty()) {
            Toast.makeText(this, "Please enter valid page numbers", Toast.LENGTH_SHORT).show()
            return
        }

        if (pagesToDelete.size >= viewModel.totalPages) {
            Toast.makeText(this, "Cannot delete all pages", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Delete")
            .setMessage("Are you sure you want to delete ${pagesToDelete.size} page(s) from this PDF?\n\nPages to delete: ${pagesToDelete.joinToString(", ")}")
            .setPositiveButton("Delete") { _, _ ->
                processDeletePages(pagesToDelete)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processDeletePages(pagesToDelete: List<Int>) {
        val file = viewModel.selectedFile ?: return
        if (viewModel.isRunning) return

        val filename = binding.filenameEditText.text.toString().trim()
        val outputFilename = if (filename.isEmpty()) FileUtils.generateFilename("output")
        else FileUtils.generateFilename(filename)

        val outputFile = FileUtils.createOutputFile(this, outputFilename)
        viewModel.outputFile = outputFile

        viewModel.run(PdfUtils.deletePages(file, outputFile, pagesToDelete))
    }

    private fun observeOperation() {
        viewModel.operationState.observe(this) { result ->
            when (result) {
                null -> Unit
                is PdfOperationResult.Progress -> {
                    showProgress(true)
                    binding.progressBar.progress = result.percent
                    binding.progressTextView.text = result.message
                }
                is PdfOperationResult.Success -> {
                    showProgress(false)
                    showDownloadButton()
                    if (!viewModel.resultNotified) {
                        viewModel.resultNotified = true
                        Toast.makeText(this, "Pages deleted successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
                is PdfOperationResult.Error -> {
                    showProgress(false)
                    if (!viewModel.resultNotified) {
                        viewModel.resultNotified = true
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showProgress(show: Boolean) {
        binding.progressLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.processButton.isEnabled = !show
        binding.selectFileButton.isEnabled = !show
        binding.pagesToDeleteEditText.isEnabled = !show
    }

    private fun showDownloadButton() {
        binding.downloadButton.visibility = View.VISIBLE
    }

    private fun downloadFile() {
        val file = viewModel.outputFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }

        val savedUri = FileUtils.saveToDownloads(this, file)
        if (savedUri != null) {
            Toast.makeText(this, "Saved to Downloads: ${file.name}", Toast.LENGTH_SHORT).show()
        }

        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
