package com.zuxing.markmap

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.data.entity.AchievementEntity
import com.zuxing.markmap.databinding.ActivityAchievementsBinding
import com.zuxing.markmap.databinding.ItemAchievementBinding
import com.zuxing.markmap.domain.AchievementEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AchievementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAchievementsBinding
    private lateinit var app: MarkMapApplication
    private lateinit var adapter: AchievementAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = AchievementAdapter()
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val before = withContext(Dispatchers.IO) {
                app.repository.getAchievementsOnce().associate { it.code to it.unlockedAt }
            }
            val result = withContext(Dispatchers.IO) { AchievementEngine.evaluate(app.repository) }
            adapter.submitList(AchievementEngine.defs.map { def ->
                def to (result.firstOrNull { it.code == def.code })
            })
            val unlocked = result.count { it.unlockedAt != null }
            binding.tvSummary.text = "已解锁 $unlocked/${AchievementEngine.defs.size}"

            val newly = AchievementEngine.defs.firstOrNull { def ->
                val now = result.firstOrNull { it.code == def.code }?.unlockedAt
                now != null && before[def.code] == null
            }
            if (newly != null) showUnlock(newly)
        }
    }

    private fun showUnlock(def: AchievementEngine.Def) {
        val dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val layout = layoutInflater.inflate(R.layout.dialog_achievement_unlock, null)
        layout.findViewById<TextView>(R.id.tvIcon).text = iconOf(def.category)
        layout.findViewById<TextView>(R.id.tvTitle).text = def.title
        layout.findViewById<TextView>(R.id.tvDesc).text = def.desc
        dialog.setContentView(layout)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        val card = layout.findViewById<View>(R.id.card)
        card.scaleX = 0.6f
        card.scaleY = 0.6f
        card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(280).start()
        layout.findViewById<View>(R.id.btnOk).setOnClickListener { dialog.dismiss() }
    }

    private fun iconOf(category: AchievementEngine.Category): String = when (category) {
        AchievementEngine.Category.GEO -> "⛰"
        AchievementEngine.Category.TRANSPORT -> "✦"
        AchievementEngine.Category.MILEAGE -> "km"
        AchievementEngine.Category.SPECIAL -> "★"
    }

    private inner class AchievementAdapter :
        ListAdapter<Pair<AchievementEngine.Def, AchievementEntity?>, AchievementAdapter.VH>(AchievementDiff()) {

        inner class VH(val binding: ItemAchievementBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemAchievementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (def, entity) = getItem(position)
            val unlocked = entity?.unlockedAt != null
            val b = holder.binding
            b.tvIcon.text = iconOf(def.category)
            b.tvTitle.text = def.title
            b.tvDesc.text = def.desc
            b.tvState.text = when {
                unlocked -> "已解锁"
                entity != null && entity.progress > 0 -> "进度 ${entity.progress}%"
                else -> "未解锁"
            }
            b.tvState.setTextColor(
                ContextCompat.getColor(
                    holder.binding.root.context,
                    if (unlocked) R.color.primary else R.color.ink_tertiary
                )
            )
            b.tvIcon.setBackgroundResource(if (unlocked) R.drawable.bg_mode_dai else R.drawable.bg_pill)
            b.tvIcon.setTextColor(
                ContextCompat.getColor(
                    holder.binding.root.context,
                    if (unlocked) R.color.primary else R.color.ink_tertiary
                )
            )
            b.root.alpha = if (unlocked) 1f else 0.6f
        }

    }
}

private class AchievementDiff :
    DiffUtil.ItemCallback<Pair<AchievementEngine.Def, AchievementEntity?>>() {
    override fun areItemsTheSame(
        a: Pair<AchievementEngine.Def, AchievementEntity?>,
        b: Pair<AchievementEngine.Def, AchievementEntity?>
    ) = a.first.code == b.first.code

    override fun areContentsTheSame(
        a: Pair<AchievementEngine.Def, AchievementEntity?>,
        b: Pair<AchievementEngine.Def, AchievementEntity?>
    ) = a == b
}
