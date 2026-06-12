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
import androidx.recyclerview.widget.LinearLayoutManager
import com.aminmart.pdftools.R
import com.aminmart.pdftools.data.PdfFile
import com.aminmart.pdftools.data.PdfOperationResult
import com.aminmart.pdftools.databinding.ActivityMergePdfBinding
import com.aminmart.pdftools.utils.FileUtils
import com.aminmart.pdftools.utils.PdfUtils

class MergePdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMergePdfBinding
    private lateinit var adapter: PdfFileAdapter
    private val viewModel: PdfToolViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                val tempFile = FileUtils.copyUriToTempFile(this, uri)
                val pageCount = PdfUtils.getPageCount(tempFile)
                if (pageCount <= 0) {
                    FileUtils.deleteTempFile(this, tempFile)
                    Toast.makeText(this, "Skipped invalid PDF", Toast.LENGTH_SHORT).show()
                    return@forEach
                }
                val pdfFile = PdfFile(
                    file = tempFile,
                    name = FileUtils.getFileNameFromUri(this, uri) ?: tempFile.name,
                    size = tempFile.length(),
                    pageCount = pageCount
                )
                adapter.addFile(pdfFile)
                updateFilesCount()
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMergePdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        updateFilesCount()
        observeOperation()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        // Adapter is backed by the ViewModel's list so the selection survives rotation
        adapter = PdfFileAdapter(viewModel.mergeFiles) { pdfFile ->
            adapter.removeFile(pdfFile, this)
            updateFilesCount()
        }
        binding.filesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.filesRecyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.addFilesButton.setOnClickListener {
            filePickerLauncher.launch("application/pdf")
        }

        binding.processButton.setOnClickListener {
            processPdfs()
        }

        binding.downloadButton.setOnClickListener {
            downloadFile()
        }
    }

    private fun updateFilesCount() {
        val count = adapter.itemCount
        binding.filesCountTextView.text = if (count == 0) {
            getString(R.string.no_files_selected)
        } else {
            getString(R.string.file_selected, count)
        }
        binding.processButton.isEnabled = count >= 2
    }

    private fun processPdfs() {
        val files = adapter.getFiles().map { it.file }
        if (files.size < 2) {
            Toast.makeText(this, "Please select at least 2 PDF files", Toast.LENGTH_SHORT).show()
            return
        }
        if (viewModel.isRunning) return

        val outputFile = FileUtils.createOutputFile(this, FileUtils.generateFilename("merged"))
        viewModel.outputFile = outputFile

        viewModel.run(PdfUtils.mergePdfs(files, outputFile))
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
                        Toast.makeText(this, "PDFs merged successfully!", Toast.LENGTH_SHORT).show()
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
        binding.processButton.isEnabled = !show && adapter.itemCount >= 2
        binding.addFilesButton.isEnabled = !show
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
