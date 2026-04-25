package com.example.c001apk.ui.base

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import com.example.c001apk.R
import com.example.c001apk.adapter.AppAdapter
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.adapter.HeaderAdapter
import com.example.c001apk.ui.home.IOnTabClickContainer
import com.example.c001apk.ui.home.IOnTabClickListener
import com.example.c001apk.util.FeedBackupUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.ToastUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// SwipeRefreshLayout + RecyclerView
abstract class BaseAppFragment<VM : BaseAppViewModel> : BaseViewFragment<VM>(),
    IOnTabClickListener {

    private lateinit var appAdapter: AppAdapter
    private lateinit var footerAdapter: FooterAdapter
    private var pendingBackup: BaseAppViewModel.BackupPayload? = null

    private val backupPathLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            PrefManager.backupTreeUri = uri.toString()
            pendingBackup?.let { payload ->
                startBackupFlow(payload)
            }
        }

    override fun initObserve() {
        super.initObserve()

        viewModel.footerState.observe(viewLifecycleOwner) {
            footerAdapter.setLoadState(it)
            if (it !is FooterState.Loading) {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        viewModel.dataList.observe(viewLifecycleOwner) {
            viewModel.listSize = it.size
            appAdapter.submitList(it)
        }

        viewModel.backupRequest.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let { payload ->
                startBackupFlow(payload)
            }
        }

    }

    private fun startBackupFlow(payload: BaseAppViewModel.BackupPayload) {
        pendingBackup = payload
        if (!FeedBackupUtil.hasBackupPath()) {
            ToastUtil.toast(requireContext(), "请选择备份保存路径")
            backupPathLauncher.launch(null)
            return
        }
        lifecycleScope.launch {
            val exists = withContext(Dispatchers.IO) {
                viewModel.hasBackup(payload.fid)
            }
            if (exists) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("动态已备份过，你想？")
                    .setNegativeButton("取消", null)
                    .setNeutralButton("均保留") { _, _ ->
                        runBackup(payload, keepBoth = true, replace = false)
                    }
                    .setPositiveButton("替换") { _, _ ->
                        runBackup(payload, keepBoth = false, replace = true)
                    }
                    .show()
            } else {
                runBackup(payload, keepBoth = false, replace = false)
            }
        }
    }

    private fun runBackup(
        payload: BaseAppViewModel.BackupPayload,
        keepBoth: Boolean,
        replace: Boolean
    ) {
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(LayoutInflater.from(requireContext()).inflate(R.layout.dialog_refresh, null, false))
            .setTitle("正在备份")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val treeUri = Uri.parse(PrefManager.backupTreeUri)
                val detail = viewModel.fetchFeedDetail(payload.fid)
                    ?: throw IllegalStateException("获取动态详情失败")
                val data = detail.data ?: throw IllegalStateException("动态详情为空")
                val imageUrls = FeedBackupUtil.collectImageUrls(data)
                val baseName = FeedBackupUtil.buildBaseName(payload.fid, keepBoth)
                FeedBackupUtil.backupToSaf(
                    requireContext(),
                    treeUri,
                    baseName,
                    detail,
                    imageUrls,
                    replace
                )
                if (replace) {
                    viewModel.replaceBackup(payload)
                } else {
                    viewModel.addBackup(payload)
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    ToastUtil.toast(requireContext(), "备份成功")
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    ToastUtil.toast(requireContext(), it.message ?: "备份失败")
                }
            }
        }
    }

    override fun initAdapter() {
        appAdapter = AppAdapter(viewModel.ItemClickListener())
        footerAdapter = FooterAdapter(ReloadListener())
        mAdapter = ConcatAdapter(HeaderAdapter(), appAdapter, footerAdapter)
    }

    inner class ReloadListener : FooterAdapter.FooterListener {
        override fun onReLoad() {
            loadMore()
        }
    }

    override fun onReturnTop(isRefresh: Boolean?) {
        if (binding.swipeRefresh.isEnabled) {
            binding.swipeRefresh.isRefreshing = true
            binding.recyclerView.scrollToPosition(0)
            refreshData()
        }
    }

    override fun onResume() {
        super.onResume()
        (parentFragment as? IOnTabClickContainer)?.tabController = this
    }

    override fun onPause() {
        super.onPause()
        (parentFragment as? IOnTabClickContainer)?.tabController = null
    }

}
