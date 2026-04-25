package com.example.c001apk.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.FeedEntity
import com.example.c001apk.logic.model.TotalReplyResponse
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
        return FeedBackupUtil.collectReplyImageUrls(fetchBackupReplies(fid))
    }

    suspend fun fetchBackupReplies(fid: String): List<TotalReplyResponse.Data> {
        return fetchRepliesByFeedType(fid, "feed").ifEmpty {
            fetchRepliesByFeedType(fid, "")
        }
    }

    private suspend fun fetchRepliesByFeedType(fid: String, feedType: String): List<TotalReplyResponse.Data> {
        var page = 1
        var lastItem: String? = null
        val all = ArrayList<TotalReplyResponse.Data>()
        while (page <= 20) {
            val response = networkRepo.getFeedContentReply(
                fid,
                "lastupdate_desc",
                page,
                null,
                lastItem,
                1,
                feedType,
                0,
                0
            ).first().getOrNull() ?: break
            val list = response.data.orEmpty().filter { it.entityType == "feed_reply" }
            if (list.isEmpty()) break
            all.addAll(list)
            lastItem = list.lastOrNull()?.id
            page++
        }
        return all
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
