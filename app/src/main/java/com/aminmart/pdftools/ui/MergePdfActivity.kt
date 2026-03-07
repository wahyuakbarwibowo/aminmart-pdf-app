package com.aminmart.pdftools.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aminmart.pdftools.data.PdfFile
import com.aminmart.pdftools.data.PdfOperationResult
import com.aminmart.pdftools.databinding.ActivityMergePdfBinding
import com.aminmart.pdftools.utils.FileUtils
import com.aminmart.pdftools.utils.PdfUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File

class MergePdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMergePdfBinding
    private lateinit var adapter: PdfFileAdapter
    private var outputFile: File? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                val tempFile = FileUtils.copyUriToTempFile(this, uri)
                val pageCount = PdfUtils.getPageCount(tempFile)
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
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = PdfFileAdapter(mutableListOf()) { pdfFile ->
            adapter.removeFile(pdfFile)
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
            getString(com.aminmart.pdftools.R.string.no_files_selected)
        } else {
            getString(com.aminmart.pdftools.R.string.file_selected, count)
        }
        binding.processButton.isEnabled = count >= 2
    }

    private fun processPdfs() {
        val files = adapter.getFiles().map { it.file }
        if (files.size < 2) {
            Toast.makeText(this, "Please select at least 2 PDF files", Toast.LENGTH_SHORT).show()
            return
        }

        val outputFilename = FileUtils.generateFilename("merged")
        outputFile = File(FileUtils.getDownloadDir(this), outputFilename)

        showProgress(true)

        lifecycleScope.launch {
            mergePdfsFlow(files).collect { result ->
                when (result) {
                    is PdfOperationResult.Progress -> {
                        binding.progressBar.progress = result.percent
                        binding.progressTextView.text = result.message
                    }
                    is PdfOperationResult.Success -> {
                        showProgress(false)
                        showDownloadButton()
                        Toast.makeText(
                            this@MergePdfActivity,
                            "PDFs merged successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is PdfOperationResult.Error -> {
                        showProgress(false)
                        Toast.makeText(
                            this@MergePdfActivity,
                            result.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun mergePdfsFlow(inputFiles: List<File>): Flow<PdfOperationResult> = flow {
        try {
            if (inputFiles.isEmpty()) {
                emit(PdfOperationResult.Error("No files to merge"))
                return@flow
            }

            emit(PdfOperationResult.Progress(10, "Starting merge process..."))
            
            val firstReader = com.lowagie.text.pdf.PdfReader(inputFiles[0].absolutePath)
            val document = com.lowagie.text.Document(firstReader.getPageSizeWithRotation(1))
            val copy = com.lowagie.text.pdf.PdfCopy(document, java.io.FileOutputStream(outputFile!!))
            
            document.open()
            
            var totalPages = 0
            inputFiles.forEach { totalPages += PdfUtils.getPageCount(it) }
            
            var currentPage = 0
            
            inputFiles.forEachIndexed { fileIndex, file ->
                emit(PdfOperationResult.Progress(
                    20 + (fileIndex * 40 / inputFiles.size),
                    "Processing file ${fileIndex + 1} of ${inputFiles.size}..."
                ))
                
                val reader = com.lowagie.text.pdf.PdfReader(file.absolutePath)
                val n = reader.numberOfPages
                
                for (i in 1..n) {
                    currentPage++
                    val percent = 60 + (currentPage * 35 / totalPages)
                    emit(PdfOperationResult.Progress(percent, "Copying page $currentPage of $totalPages..."))
                    
                    copy.addPage(copy.getImportedPage(reader, i))
                }
                
                reader.close()
            }
            
            emit(PdfOperationResult.Progress(95, "Finalizing merged document..."))
            
            document.close()
            copy.close()
            
            emit(PdfOperationResult.Progress(100, "Merge complete!"))
            
            emit(PdfOperationResult.Success(outputFile!!))
            
        } catch (e: Exception) {
            outputFile?.delete()
            emit(PdfOperationResult.Error("Failed to merge PDFs: ${e.message}", e))
        }
    }

    private fun showProgress(show: Boolean) {
        binding.progressLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.processButton.isEnabled = !show
        binding.addFilesButton.isEnabled = !show
    }

    private fun showDownloadButton() {
        binding.downloadButton.visibility = View.VISIBLE
    }

    private fun downloadFile() {
        val file = outputFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }

            startActivity(intent)

            adapter.getFiles().forEach { pdfFile ->
                FileUtils.deleteTempFile(this, pdfFile.file)
            }

            Toast.makeText(this, "File opened successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.getFiles().forEach { pdfFile ->
            FileUtils.deleteTempFile(this, pdfFile.file)
        }
    }
}
