package com.aminmart.pdftools.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aminmart.pdftools.data.PdfFile
import com.aminmart.pdftools.databinding.ItemPdfFileBinding
import com.aminmart.pdftools.utils.FileUtils

class PdfFileAdapter(
    private val files: MutableList<PdfFile>,
    private val onRemoveClick: (PdfFile) -> Unit
) : RecyclerView.Adapter<PdfFileAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemPdfFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(pdfFile: PdfFile) {
            binding.filenameTextView.text = pdfFile.name
            binding.fileInfoTextView.text = "${pdfFile.getFormattedSize()} • ${pdfFile.pageCount} pages"
            
            binding.removeButton.setOnClickListener {
                onRemoveClick(pdfFile)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPdfFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount() = files.size

    fun addFile(pdfFile: PdfFile) {
        files.add(pdfFile)
        notifyItemInserted(files.size - 1)
    }

    fun removeFile(pdfFile: PdfFile) {
        val index = files.indexOfFirst { it.id == pdfFile.id }
        if (index != -1) {
            files.removeAt(index)
            notifyItemRemoved(index)
            // Delete the temp file
            FileUtils.deleteTempFile(binding.root.context, pdfFile.file)
        }
    }

    fun getFiles(): List<PdfFile> = files.toList()

    fun clear() {
        val count = files.size
        files.clear()
        notifyItemRangeRemoved(0, count)
    }
}
