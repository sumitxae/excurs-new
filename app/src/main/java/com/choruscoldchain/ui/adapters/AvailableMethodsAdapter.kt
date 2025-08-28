package com.choruscoldchain.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.choruscoldchain.databinding.ItemMethodBinding

class AvailableMethodsAdapter(
    private val methods: List<String>,
    private val onMethodClick: (String) -> Unit
) : RecyclerView.Adapter<AvailableMethodsAdapter.MethodViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MethodViewHolder {
        val binding = ItemMethodBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MethodViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MethodViewHolder, position: Int) {
        holder.bind(methods[position])
    }

    override fun getItemCount(): Int = methods.size

    inner class MethodViewHolder(
        private val binding: ItemMethodBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(method: String) {
            binding.textMethodName.text = method
            binding.root.setOnClickListener {
                onMethodClick(method)
            }
        }
    }
}
