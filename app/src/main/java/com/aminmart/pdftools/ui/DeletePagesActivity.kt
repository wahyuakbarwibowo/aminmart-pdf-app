package com.aminmart.pdftools.ui

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aminmart.pdftools.data.PdfOperationResult
import com.aminmart.pdftools.databinding.ActivityDeletePagesBinding
import com.aminmart.pdftools.utils.FileUtils
import com.aminmart.pdftools.utils.PdfUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File

class DeletePagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeletePagesBinding
    private var selectedFile: File? = null
    private var outputFile: File? = null
    private var totalPages: Int = 0

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                selectedFile = FileUtils.copyUriToTempFile(this, it)
                totalPages = PdfUtils.getPageCount(selectedFile!!)
                
                binding.selectedFileTextView.text = "${FileUtils.getFileNameFromUri(this, it) ?: "Unknown"}\n" +
                        "Size: ${FileUtils.formatFileSize(selectedFile!!.length())}"
                binding.totalPagesTextView.text = "$totalPages pages"
                binding.pagesToDeleteEditText.setText("")
                binding.pagesPreviewTextView.text = ""
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeletePagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
        setupTextWatcher()
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

    private fun updatePagesPreview() {
        if (totalPages == 0) return

        val input = binding.pagesToDeleteEditText.text.toString()
        if (input.isBlank()) {
            binding.pagesPreviewTextView.text = ""
            return
        }

        val pagesToDelete = PdfUtils.parsePageRange(input, totalPages)
        if (pagesToDelete.isNotEmpty()) {
            binding.pagesPreviewTextView.text = "Will delete: ${pagesToDelete.size} page(s) - Pages: ${pagesToDelete.joinToString(", ")}"
        } else {
            binding.pagesPreviewTextView.text = "No valid pages specified"
        }
    }

    private fun showConfirmDialog() {
        val file = selectedFile
        if (file == null) {
            Toast.makeText(this, "Please select a PDF file first", Toast.LENGTH_SHORT).show()
            return
        }

        val input = binding.pagesToDeleteEditText.text.toString()
        if (input.isBlank()) {
            Toast.makeText(this, "Please enter pages to delete", Toast.LENGTH_SHORT).show()
            return
        }

        val pagesToDelete = PdfUtils.parsePageRange(input, totalPages)
        if (pagesToDelete.isEmpty()) {
            Toast.makeText(this, "Please enter valid page numbers", Toast.LENGTH_SHORT).show()
            return
        }

        if (pagesToDelete.size >= totalPages) {
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
        val file = selectedFile ?: return

        val filename = binding.filenameEditText.text.toString().trim()
        val outputFilename = if (filename.isEmpty()) FileUtils.generateFilename("output")
        else FileUtils.generateFilename(filename)

        outputFile = File(FileUtils.getDownloadDir(this), outputFilename)

        showProgress(true)

        lifecycleScope.launch {
            deletePagesFlow(file, outputFile!!, pagesToDelete).collect { result ->
                when (result) {
                    is PdfOperationResult.Progress -> {
                        binding.progressBar.progress = result.percent
                        binding.progressTextView.text = result.message
                    }
                    is PdfOperationResult.Success -> {
                        showProgress(false)
                        showDownloadButton()
                        Toast.makeText(
                            this@DeletePagesActivity,
                            "Successfully deleted ${pagesToDelete.size} page(s)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is PdfOperationResult.Error -> {
                        showProgress(false)
                        Toast.makeText(
                            this@DeletePagesActivity,
                            result.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun deletePagesFlow(inputFile: File, outputFile: File, pagesToDelete: List<Int>): Flow<PdfOperationResult> = flow {
        try {
            emit(PdfOperationResult.Progress(10, "Reading PDF file..."))
            
            val reader = com.lowagie.text.pdf.PdfReader(inputFile.absolutePath)
            val totalPages = reader.numberOfPages
            
            val validPagesToDelete = pagesToDelete
                .filter { it in 1..totalPages }
                .distinct()
                .sorted()
            
            val pagesToKeep = (1..totalPages).filter { it !in validPagesToDelete }
            
            if (pagesToKeep.isEmpty()) {
                reader.close()
                emit(PdfOperationResult.Error("Cannot delete all pages"))
                return@flow
            }
            
            emit(PdfOperationResult.Progress(30, "Creating new document..."))
            
            val document = com.lowagie.text.Document()
            val writer = com.lowagie.text.pdf.PdfWriter(document, java.io.FileOutputStream(outputFile))
            
            document.open()
            
            emit(PdfOperationResult.Progress(40, "Copying ${pagesToKeep.size} of $totalPages pages..."))
            
            pagesToKeep.forEachIndexed { index, pageNum ->
                val percent = 40 + ((index.toFloat() / pagesToKeep.size) * 50).toInt()
                emit(PdfOperationResult.Progress(percent, "Copying page ${index + 1} of ${pagesToKeep.size}..."))
                
                document.newPage()
                val page = writer.getImportedPage(reader, pageNum)
                writer.addImportedPage(page)
            }
            
            emit(PdfOperationResult.Progress(90, "Finalizing document..."))
            
            document.close()
            reader.close()
            
            val deletedCount = totalPages - pagesToKeep.size
            emit(PdfOperationResult.Progress(
                100,
                "Successfully deleted $deletedCount page(s)"
            ))
            
            emit(PdfOperationResult.Success(outputFile))
            
        } catch (e: Exception) {
            outputFile.delete()
            emit(PdfOperationResult.Error("Failed to delete pages: ${e.message}", e))
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

            selectedFile?.let { FileUtils.deleteTempFile(this, it) }

            Toast.makeText(this, "File opened successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        selectedFile?.let { FileUtils.deleteTempFile(this, it) }
    }
}
