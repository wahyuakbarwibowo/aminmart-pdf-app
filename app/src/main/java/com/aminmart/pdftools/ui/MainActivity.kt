package com.aminmart.pdftools.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aminmart.pdftools.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.compressCard.setOnClickListener {
            startActivity(Intent(this, CompressPdfActivity::class.java))
        }

        binding.mergeCard.setOnClickListener {
            startActivity(Intent(this, MergePdfActivity::class.java))
        }

        binding.deletePagesCard.setOnClickListener {
            startActivity(Intent(this, DeletePagesActivity::class.java))
        }

        binding.reorderPagesCard.setOnClickListener {
            startActivity(Intent(this, ReorderPagesActivity::class.java))
        }
    }
}
