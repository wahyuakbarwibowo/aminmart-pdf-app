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
import com.aminmart.pdftools.data.parsePageOrder
import com.aminmart.pdftools.databinding.ActivityReorderPagesBinding
import com.aminmart.pdftools.utils.FileUtils
import com.aminmart.pdftools.utils.PdfUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File

class ReorderPagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReorderPagesBinding
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
                binding.pageOrderEditText.setText("")
                binding.pageOrderPreviewTextView.text = ""
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReorderPagesBinding.inflate(layoutInflater)
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

        binding.reverseButton.setOnClickListener {
            if (totalPages > 0) {
                val reversedOrder = (totalPages downTo 1).joinToString(",")
                binding.pageOrderEditText.setText(reversedOrder)
            } else {
                Toast.makeText(this, "Please select a PDF file first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.clearButton.setOnClickListener {
            binding.pageOrderEditText.setText("")
        }
    }

    private fun setupTextWatcher() {
        binding.pageOrderEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePageOrderPreview()
            }
        })
    }

    private fun updatePageOrderPreview() {
        if (totalPages == 0) return

        val input = binding.pageOrderEditText.text.toString()
        if (input.isBlank()) {
            binding.pageOrderPreviewTextView.text = ""
            return
        }

        val pages = parsePageOrder(input, totalPages)
        if (pages.isNotEmpty()) {
            val missingPages = (1..totalPages).filter { it !in pages }
            val duplicateInfo = if (pages.size != pages.distinct().size) " (duplicates will be ignored)" else ""
            
            var previewText = "New order: ${pages.joinToString(", ")}$duplicateInfo"
            if (missingPages.isNotEmpty()) {
                previewText += "\nMissing pages: ${missingPages.joinToString(", ")}"
            }
            if (pages.size != totalPages) {
                previewText += "\n⚠️ Page count mismatch (${pages.size} vs $totalPages)"
            }
            binding.pageOrderPreviewTextView.text = previewText
        } else {
            binding.pageOrderPreviewTextView.text = "No valid page order specified"
        }
    }

    private fun showConfirmDialog() {
        val file = selectedFile
        if (file == null) {
            Toast.makeText(this, "Please select a PDF file first", Toast.LENGTH_SHORT).show()
            return
        }

        val input = binding.pageOrderEditText.text.toString()
        if (input.isBlank()) {
            Toast.makeText(this, "Please enter page order", Toast.LENGTH_SHORT).show()
            return
        }

        val pages = parsePageOrder(input, totalPages)
        if (pages.isEmpty()) {
            Toast.makeText(this, "Please enter valid page numbers", Toast.LENGTH_SHORT).show()
            return
        }

        val missingPages = (1..totalPages).filter { it !in pages }
        if (missingPages.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Missing Pages")
                .setMessage("The following pages are not included in the new order: ${missingPages.joinToString(", ")}\n\nThese pages will be lost in the output file. Continue?")
                .setPositiveButton("Continue") { _, _ ->
                    processReorderPages(pages)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Reorder")
            .setMessage("Are you sure you want to reorder the pages?\n\nNew order: ${pages.joinToString(", ")}")
            .setPositiveButton("Reorder") { _, _ ->
                processReorderPages(pages)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processReorderPages(pageOrder: List<Int>) {
        val file = selectedFile ?: return

        val filename = binding.filenameEditText.text.toString().trim()
        val outputFilename = if (filename.isEmpty()) FileUtils.generateFilename("output")
        else FileUtils.generateFilename(filename)

        outputFile = File(FileUtils.getDownloadDir(this), outputFilename)

        showProgress(true)

        lifecycleScope.launch {
            reorderPagesFlow(file, outputFile!!, pageOrder).collect { result ->
                when (result) {
                    is PdfOperationResult.Progress -> {
                        binding.progressBar.progress = result.percent
                        binding.progressTextView.text = result.message
                    }
                    is PdfOperationResult.Success -> {
                        showProgress(false)
                        showDownloadButton()
                        Toast.makeText(
                            this@ReorderPagesActivity,
                            "Successfully reordered ${pageOrder.size} page(s)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is PdfOperationResult.Error -> {
                        showProgress(false)
                        Toast.makeText(
                            this@ReorderPagesActivity,
                            result.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun reorderPagesFlow(inputFile: File, outputFile: File, pageOrder: List<Int>): Flow<PdfOperationResult> = flow {
        try {
            emit(PdfOperationResult.Progress(10, "Reading PDF file..."))

            val reader = com.lowagie.text.pdf.PdfReader(inputFile.absolutePath)
            val totalPages = reader.numberOfPages

            if (pageOrder.isEmpty()) {
                reader.close()
                emit(PdfOperationResult.Error("Page order cannot be empty"))
                return@flow
            }

            val invalidPages = pageOrder.filter { it !in 1..totalPages }
            if (invalidPages.isNotEmpty()) {
                reader.close()
                emit(PdfOperationResult.Error("Invalid page numbers: ${invalidPages.joinToString(", ")}"))
                return@flow
            }

            emit(PdfOperationResult.Progress(30, "Creating new document..."))

            val document = com.lowagie.text.Document()
            val copy = com.lowagie.text.pdf.PdfCopy(document, java.io.FileOutputStream(outputFile))

            document.open()

            emit(PdfOperationResult.Progress(40, "Reordering ${pageOrder.size} pages..."))

            pageOrder.forEachIndexed { index, pageNum ->
                val percent = 40 + ((index.toFloat() / pageOrder.size) * 50).toInt()
                emit(PdfOperationResult.Progress(percent, "Copying page ${index + 1} of ${pageOrder.size}..."))

                copy.addPage(copy.getImportedPage(reader, pageNum))
            }

            emit(PdfOperationResult.Progress(90, "Finalizing document..."))

            document.close()
            copy.close()
            reader.close()

            emit(PdfOperationResult.Progress(
                100,
                "Successfully reordered ${pageOrder.size} page(s)"
            ))

            emit(PdfOperationResult.Success(outputFile))

        } catch (e: Exception) {
            outputFile.delete()
            emit(PdfOperationResult.Error("Failed to reorder pages: ${e.message}", e))
        }
    }

    private fun showProgress(show: Boolean) {
        binding.progressLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.processButton.isEnabled = !show
        binding.selectFileButton.isEnabled = !show
        binding.pageOrderEditText.isEnabled = !show
        binding.reverseButton.isEnabled = !show
        binding.clearButton.isEnabled = !show
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
