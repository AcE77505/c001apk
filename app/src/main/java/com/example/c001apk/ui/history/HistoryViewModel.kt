package com.example.c001apk.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.FeedEntity
import com.example.c001apk.logic.repository.BlackListRepo
import com.example.c001apk.logic.model.FeedContentResponse
import com.example.c001apk.logic.repository.HistoryFavoriteRepo
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.FeedBackupUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = HistoryViewModel.Factory::class)
class HistoryViewModel @AssistedInject constructor(
    @Assisted val type: String,
    private val blackListRepo: BlackListRepo,
    private val historyRepo: HistoryFavoriteRepo,
    private val networkRepo: NetworkRepo,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(type: String): HistoryViewModel
    }

    val browseLiveData: LiveData<List<FeedEntity>> =
        if (type == "browse") {
            historyRepo.loadAllHistoryListLive()
        } else {
            historyRepo.loadAllFavoriteListLive()
        }



    suspend fun hasBackup(fid: String): Boolean {
        return historyRepo.checkFavorite(fid)
    }

    suspend fun replaceBackup(entity: FeedEntity) {
        historyRepo.deleteFavorite(entity.fid)
        historyRepo.insertFavorite(entity)
    }

    suspend fun addBackup(entity: FeedEntity) {
        historyRepo.insertFavorite(entity)
    }

    suspend fun fetchFeedDetail(fid: String): FeedContentResponse? {
        return networkRepo.getFeedContent(fid, null).first().getOrNull()
    }


    suspend fun fetchReplyImageUrls(fid: String): List<String> {
        val firstTry = networkRepo.getFeedContentReply(
            fid,
            "lastupdate_desc",
            1,
            null,
            null,
            1,
            "feed",
            0,
            0
        ).first().getOrNull()

        val firstList = FeedBackupUtil.collectReplyImageUrls(firstTry?.data)
        if (firstList.isNotEmpty()) return firstList

        val secondTry = networkRepo.getFeedContentReply(
            fid,
            "lastupdate_desc",
            1,
            null,
            null,
            1,
            "",
            0,
            0
        ).first().getOrNull()
        return FeedBackupUtil.collectReplyImageUrls(secondTry?.data)
    }

    fun saveUid(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            blackListRepo.saveUid(uid)
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            when (type) {
                "browse" -> historyRepo.deleteAllHistory()
                "favorite" -> historyRepo.deleteAllFavorite()
                else -> {}
            }
        }
    }

    fun delete(fid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            when (type) {
                "browse" -> historyRepo.deleteHistory(fid)
                "favorite" -> historyRepo.deleteFavorite(fid)
                else -> {}
            }
        }
    }

    fun saveHistory(
        id: String,
        uid: String,
        username: String,
        userAvatar: String,
        deviceTitle: String,
        message: String,
        dateline: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.saveHistory(
                id,
                uid,
                username,
                userAvatar,
                deviceTitle,
                message,
                dateline,
            )
        }
    }

}
