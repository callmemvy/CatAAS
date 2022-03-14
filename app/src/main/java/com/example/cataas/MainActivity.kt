package com.example.cataas

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cataas.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding.recyclerView) {
            val layoutManager = LinearLayoutManager(this@MainActivity)
            val adapter = CatsAdapter(this@MainActivity)

            this.layoutManager = layoutManager
            this.adapter = adapter
            setHasFixedSize(true)

            // pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - CatsAdapter.LOADING_THRESHOLD) {
                        if (adapter.loadMoreCats()) {
                            removeOnScrollListener(this)
                        }
                    }
                }
            })
        }
    }
}