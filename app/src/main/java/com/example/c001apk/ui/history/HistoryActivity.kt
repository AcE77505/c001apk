package com.example.c001apk.ui.history

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.absinthe.libraries.utils.extensions.dp
import com.example.c001apk.R
import com.example.c001apk.adapter.HeaderAdapter
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.databinding.ActivityHistoryBinding
import com.example.c001apk.logic.model.FeedEntity
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.util.FeedBackupUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.ToastUtil
import com.example.c001apk.view.LinearItemDecoration
import com.example.c001apk.view.StaggerItemDecoration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class HistoryActivity : BaseActivity<ActivityHistoryBinding>() {

    private val viewModel by viewModels<HistoryViewModel>(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<HistoryViewModel.Factory> { factory ->
                factory.create(type = intent.getStringExtra("type") ?: "browse")
            }
        }
    )
    private lateinit var mAdapter: HistoryAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var sLayoutManager: StaggeredGridLayoutManager
    private val isPortrait by lazy { resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
    private var pendingBackup: FeedEntity? = null

    private val backupPathLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            PrefManager.backupTreeUri = uri.toString()
            pendingBackup?.let { runBackupFlow(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.toolBar.title =
            when (viewModel.type) {
                "browse" -> "浏览历史"
                "favorite" -> "我的备份"
                else -> throw IllegalArgumentException("error type: ${viewModel.type}")
            }

        initBar()
        initView()

        viewModel.browseLiveData.observe(this) { list ->
            mAdapter.submitList(list)
            binding.indicator.parent.isIndeterminate = false
            binding.indicator.parent.isVisible = false
        }

    }

    private fun initBar() {
        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        menu?.findItem(R.id.backupPath)?.isVisible = viewModel.type == "favorite"
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()

            R.id.clearAll -> {
                MaterialAlertDialogBuilder(this).apply {
                    if (viewModel.type == "browse") setTitle("确定清除全部浏览历史？")
                    else setTitle("确定清除全部备份记录？")
                    setNegativeButton(android.R.string.cancel, null)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        viewModel.deleteAll()
                    }
                    show()
                }
            }

            R.id.backupPath -> {
                if (PrefManager.backupTreeUri.isEmpty()) {
                    ToastUtil.toast(this, "请选择备份保存路径")
                    backupPathLauncher.launch(null)
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("当前备份路径")
                        .setMessage(PrefManager.backupTreeUri)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("重设备份路径") { _, _ ->
                            backupPathLauncher.launch(null)
                        }
                        .show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initView() {
        binding.indicator.parent.isIndeterminate = true
        binding.indicator.parent.isVisible = true

        mAdapter = HistoryAdapter(ItemClickListener())
        binding.recyclerView.apply {
            adapter = ConcatAdapter(HeaderAdapter(), mAdapter)
            layoutManager =
                if (isPortrait) {
                    mLayoutManager = LinearLayoutManager(this@HistoryActivity)
                    mLayoutManager
                } else {
                    sLayoutManager =
                        StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                    sLayoutManager
                }
            if (itemDecorationCount == 0) {
                if (isPortrait)
                    addItemDecoration(LinearItemDecoration(10.dp))
                else
                    addItemDecoration(StaggerItemDecoration(10.dp))
            }
        }
    }

    private fun runBackupFlow(entity: FeedEntity) {
        pendingBackup = entity
        if (!FeedBackupUtil.hasBackupPath()) {
            ToastUtil.toast(this, "请选择备份保存路径")
            backupPathLauncher.launch(null)
            return
        }
        lifecycleScope.launch {
            val exists = withContext(Dispatchers.IO) {
                viewModel.hasBackup(entity.fid)
            }
            if (exists) {
                MaterialAlertDialogBuilder(this@HistoryActivity)
                    .setTitle("动态已备份过，你想？")
                    .setNegativeButton("取消", null)
                    .setNeutralButton("均保留") { _, _ ->
                        runBackup(entity, keepBoth = true, replace = false)
                    }
                    .setPositiveButton("替换") { _, _ ->
                        runBackup(entity, keepBoth = false, replace = true)
                    }
                    .show()
            } else {
                runBackup(entity, keepBoth = false, replace = false)
            }
        }
    }

    private fun runBackup(entity: FeedEntity, keepBoth: Boolean, replace: Boolean) {
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setView(LayoutInflater.from(this).inflate(R.layout.dialog_refresh, null, false))
            .setTitle("正在备份")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val detail = viewModel.fetchFeedDetail(entity.fid)
                    ?: throw IllegalStateException("获取动态详情失败")
                val data = detail.data ?: throw IllegalStateException("动态详情为空")
                val treeUri = Uri.parse(PrefManager.backupTreeUri)
                val imageUrls = FeedBackupUtil.collectImageUrls(data)
                val baseName = FeedBackupUtil.buildBaseName(entity.fid, keepBoth)
                FeedBackupUtil.backupToSaf(this@HistoryActivity, treeUri, baseName, detail, imageUrls, replace)
                if (replace) viewModel.replaceBackup(entity)
                else viewModel.addBackup(entity)
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    ToastUtil.toast(this@HistoryActivity, "备份成功")
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    ToastUtil.toast(this@HistoryActivity, it.message ?: "备份失败")
                }
            }
        }
    }

    inner class ItemClickListener : ItemListener {
        override fun onViewFeed(
            view: View,
            id: String?,
            uid: String?,
            username: String?,
            userAvatar: String?,
            deviceTitle: String?,
            message: String?,
            dateline: String?,
            rid: Any?,
            isViewReply: Any?
        ) {
            super.onViewFeed(
                view,
                id,
                uid,
                username,
                userAvatar,
                deviceTitle,
                message,
                dateline,
                rid,
                isViewReply
            )
            if (!uid.isNullOrEmpty() && PrefManager.isRecordHistory)
                viewModel.saveHistory(
                    id.toString(), uid.toString(), username.toString(), userAvatar.toString(),
                    deviceTitle.toString(), message.toString(), dateline.toString()
                )
        }

        override fun onBlockUser(id: String, uid: String, position: Int) {
            viewModel.saveUid(uid)
            onDeleteClicked("", id, position)
        }

        override fun onDeleteClicked(entityType: String, id: String, position: Int) {
            viewModel.delete(id)
        }

        override fun onBackupClicked(
            id: String,
            uid: String,
            username: String?,
            userAvatar: String?,
            deviceTitle: String?,
            message: String?,
            dateline: String?
        ) {
            runBackupFlow(
                FeedEntity(
                    id,
                    uid,
                    username.orEmpty(),
                    userAvatar.orEmpty(),
                    deviceTitle.orEmpty(),
                    message.orEmpty(),
                    dateline.orEmpty()
                )
            )
        }
    }

}
