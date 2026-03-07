package com.aminmart.pdftools.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aminmart.pdftools.R
import com.aminmart.pdftools.data.CompressionLevel
import com.aminmart.pdftools.data.PdfOperationResult
import com.aminmart.pdftools.databinding.ActivityCompressPdfBinding
import com.aminmart.pdftools.utils.FileUtils
import com.aminmart.pdftools.utils.PdfUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File

class CompressPdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompressPdfBinding
    private var selectedFile: File? = null
    private var outputFile: File? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                selectedFile = FileUtils.copyUriToTempFile(this, it)
                val pageCount = PdfUtils.getPageCount(selectedFile!!)
                binding.selectedFileTextView.text = "${FileUtils.getFileNameFromUri(this, it) ?: "Unknown"}\n" +
                        "Size: ${FileUtils.formatFileSize(selectedFile!!.length())}\n" +
                        "Pages: $pageCount"
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompressPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
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

    private fun processPdf() {
        val file = selectedFile
        if (file == null) {
            Toast.makeText(this, "Please select a PDF file first", Toast.LENGTH_SHORT).show()
            return
        }

        val compressionLevel = when (binding.compressionLevelRadioGroup.checkedRadioButtonId) {
            R.id.lowCompressionRadio -> CompressionLevel.LOW
            R.id.highCompressionRadio -> CompressionLevel.HIGH
            else -> CompressionLevel.MEDIUM
        }

        val filename = binding.filenameEditText.text.toString().trim()
        val outputFilename = if (filename.isEmpty()) FileUtils.generateFilename("compressed")
        else FileUtils.generateFilename(filename)

        outputFile = File(FileUtils.getDownloadDir(this), outputFilename)

        showProgress(true)

        lifecycleScope.launch {
            compressPdfWithProgress(
                inputFile = file,
                outputFile = outputFile!!,
                compressionLevel = compressionLevel
            ).collect { result ->
                when (result) {
                    is PdfOperationResult.Progress -> {
                        binding.progressBar.progress = result.percent
                        binding.progressTextView.text = result.message
                    }
                    is PdfOperationResult.Success -> {
                        showProgress(false)
                        showDownloadButton()
                        Toast.makeText(
                            this@CompressPdfActivity,
                            "PDF compressed successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is PdfOperationResult.Error -> {
                        showProgress(false)
                        Toast.makeText(
                            this@CompressPdfActivity,
                            result.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun compressPdfWithProgress(
        inputFile: File,
        outputFile: File,
        compressionLevel: CompressionLevel
    ): Flow<PdfOperationResult> = flow {
        try {
            emit(PdfOperationResult.Progress(10, "Reading PDF file..."))
            
            val reader = com.lowagie.text.pdf.PdfReader(inputFile.absolutePath)
            val pageCount = reader.numberOfPages
            
            emit(PdfOperationResult.Progress(20, "Creating compressed document..."))
            
            val document = com.lowagie.text.Document()
            val writer = com.lowagie.text.pdf.PdfWriter(document, java.io.FileOutputStream(outputFile))
            
            when (compressionLevel) {
                CompressionLevel.LOW -> {
                    writer.fullCompression = true
                }
                CompressionLevel.MEDIUM -> {
                    writer.fullCompression = true
                    writer.setCompressionLevel(2)
                }
                CompressionLevel.HIGH -> {
                    writer.fullCompression = true
                    writer.setCompressionLevel(0)
                }
            }
            
            document.open()
            
            emit(PdfOperationResult.Progress(30, "Processing $pageCount pages..."))
            
            for (i in 1..pageCount) {
                val percent = 30 + ((i.toFloat() / pageCount) * 50).toInt()
                emit(PdfOperationResult.Progress(percent, "Processing page $i of $pageCount..."))
                
                document.newPage()
                val page = writer.getImportedPage(reader, i)
                writer.addImportedPage(page)
            }
            
            emit(PdfOperationResult.Progress(85, "Finalizing document..."))
            
            document.close()
            reader.close()
            
            val originalSize = inputFile.length()
            val compressedSize = outputFile.length()
            val reduction = ((originalSize - compressedSize).toFloat() / originalSize * 100).toInt()
            
            emit(PdfOperationResult.Progress(
                100,
                "Compression complete! Reduced by $reduction%"
            ))
            
            emit(PdfOperationResult.Success(outputFile))
            
        } catch (e: Exception) {
            outputFile.delete()
            emit(PdfOperationResult.Error("Failed to compress PDF: ${e.message}", e))
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
