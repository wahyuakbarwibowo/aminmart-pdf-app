package com.aminmart.pdftools.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.aminmart.pdftools.R
import com.aminmart.pdftools.data.CompressionLevel
import com.aminmart.pdftools.data.PdfOperationResult
import com.aminmart.pdftools.databinding.ActivityCompressPdfBinding
import com.aminmart.pdftools.utils.FileUtils
import com.aminmart.pdftools.utils.PdfUtils

class CompressPdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompressPdfBinding
    private val viewModel: PdfToolViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompressPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
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
            processPdf()
        }

        binding.downloadButton.setOnClickListener {
            downloadFile()
        }
    }

    private fun restoreState() {
        viewModel.selectedFileLabel?.let { binding.selectedFileTextView.text = it }
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
                    "Size: ${FileUtils.formatFileSize(file.length())}\n" +
                    "Pages: $pageCount"
            binding.selectedFileTextView.text = viewModel.selectedFileLabel
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processPdf() {
        val file = viewModel.selectedFile
        if (file == null) {
            Toast.makeText(this, "Please select a PDF file first", Toast.LENGTH_SHORT).show()
            return
        }
        if (viewModel.isRunning) return

        val compressionLevel = when (binding.compressionLevelRadioGroup.checkedRadioButtonId) {
            R.id.lowCompressionRadio -> CompressionLevel.LOW
            R.id.highCompressionRadio -> CompressionLevel.HIGH
            else -> CompressionLevel.MEDIUM
        }

        val filename = binding.filenameEditText.text.toString().trim()
        val outputFilename = if (filename.isEmpty()) FileUtils.generateFilename("compressed")
        else FileUtils.generateFilename(filename)

        val outputFile = FileUtils.createOutputFile(this, outputFilename)
        viewModel.outputFile = outputFile

        viewModel.run(PdfUtils.compressPdf(file, outputFile, compressionLevel))
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
                        Toast.makeText(this, "PDF compressed successfully!", Toast.LENGTH_SHORT).show()
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
