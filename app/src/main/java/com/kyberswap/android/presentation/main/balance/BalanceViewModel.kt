package com.kyberswap.android.presentation.main.balance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kyberswap.android.domain.model.Token
import com.kyberswap.android.domain.model.Wallet
import com.kyberswap.android.domain.usecase.send.SaveSendTokenUseCase
import com.kyberswap.android.domain.usecase.swap.SaveSwapDataTokenUseCase
import com.kyberswap.android.domain.usecase.token.GetBalancePollingUseCase
import com.kyberswap.android.domain.usecase.token.GetBalanceUseCase
import com.kyberswap.android.domain.usecase.token.PrepareBalanceUseCase
import com.kyberswap.android.domain.usecase.token.SaveTokenUseCase
import com.kyberswap.android.domain.usecase.wallet.GetSelectedWalletUseCase
import com.kyberswap.android.domain.usecase.wallet.GetWalletByAddressUseCase
import com.kyberswap.android.domain.usecase.wallet.UpdateWalletUseCase
import com.kyberswap.android.presentation.common.Event
import com.kyberswap.android.presentation.main.SelectedWalletViewModel
import com.kyberswap.android.presentation.main.swap.SaveSendState
import com.kyberswap.android.presentation.main.swap.SaveSwapDataState
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import javax.inject.Inject

class BalanceViewModel @Inject constructor(
    private val getBalanceUseCase: GetBalanceUseCase,
    private val getBalancePollingUseCase: GetBalancePollingUseCase,
    private val updateWalletUseCase: UpdateWalletUseCase,
    private val getWalletByAddressUseCase: GetWalletByAddressUseCase,
    private val saveSwapDataTokenUseCase: SaveSwapDataTokenUseCase,
    private val saveSendTokenUseCase: SaveSendTokenUseCase,
    private val prepareBalanceUseCase: PrepareBalanceUseCase,
    private val saveTokenUseCase: SaveTokenUseCase,
    getSelectedWalletUseCase: GetSelectedWalletUseCase
) : SelectedWalletViewModel(getSelectedWalletUseCase) {

    private val _getBalanceStateCallback = MutableLiveData<Event<GetBalanceState>>()
    val getBalanceStateCallback: LiveData<Event<GetBalanceState>>
        get() = _getBalanceStateCallback

    private val _saveTokenCallback = MutableLiveData<Event<SaveTokenState>>()
    val saveTokenCallback: LiveData<Event<SaveTokenState>>
        get() = _saveTokenCallback


    val searchedKeywordsCallback: LiveData<Event<String>>
        get() = _searchedKeywords

    private val _searchedKeywords = MutableLiveData<Event<String>>()

    val visibilityCallback: LiveData<Event<Boolean>>
        get() = _visibility


    private val _visibility = MutableLiveData<Event<Boolean>>()

    private val _saveSwapDataStateStateCallback = MutableLiveData<Event<SaveSwapDataState>>()
    val saveTokenSelectionCallback: LiveData<Event<SaveSwapDataState>>
        get() = _saveSwapDataStateStateCallback


    private val _callback = MutableLiveData<Event<SaveSwapDataState>>()
    val callback: LiveData<Event<SaveSwapDataState>>
        get() = _callback


    private val _callbackSaveSend = MutableLiveData<Event<SaveSendState>>()
    val callbackSaveSend: LiveData<Event<SaveSendState>>
        get() = _callbackSaveSend

    val compositeDisposable by lazy {
        CompositeDisposable()
    }

    fun updateSearchKeyword(keyword: String) {
        _searchedKeywords.value = Event(keyword)
    }

    fun updateVisibility(isVisible: Boolean) {
        _visibility.value = Event(isVisible)
    }

    fun getTokenBalance(address: String) {
        getBalancePollingUseCase.dispose()
        getBalancePollingUseCase.execute(
            Consumer {

    ,
            Consumer {
                it.printStackTrace()
    ,
            GetBalancePollingUseCase.Param(address)
        )
        getBalanceUseCase.dispose()
        _getBalanceStateCallback.postValue(Event(GetBalanceState.Loading))
        getBalanceUseCase.execute(
            Consumer {

                _getBalanceStateCallback.value = Event(
                    GetBalanceState.Success(
                        it
                    )
                )
    ,
            Consumer {
                it.printStackTrace()
                _getBalanceStateCallback.value =
                    Event(
                        GetBalanceState.ShowError(
                            it.localizedMessage
                        )
                    )
    ,
            null
        )
    }

    fun updateWallet(wallet: Wallet?) {
        if (wallet == null) return
        updateWalletUseCase.execute(
            Action {
                _saveSwapDataStateStateCallback.value = Event(SaveSwapDataState.Success())
    ,
            Consumer {
                it.printStackTrace()
                _saveSwapDataStateStateCallback.value =
                    Event(SaveSwapDataState.ShowError(it.localizedMessage))
    ,
            wallet
        )
    }

    override fun onCleared() {
        compositeDisposable.dispose()
        getBalancePollingUseCase.dispose()
        getWalletByAddressUseCase.dispose()
        getBalanceUseCase.dispose()
        updateWalletUseCase.dispose()
        saveSendTokenUseCase.dispose()
        saveSwapDataTokenUseCase.dispose()
        prepareBalanceUseCase.dispose()
        saveTokenUseCase.dispose()
        super.onCleared()
    }


    fun save(walletAddress: String, token: Token, isSell: Boolean = false) {
        saveSwapDataTokenUseCase.execute(
            Action {
                _callback.value = Event(SaveSwapDataState.Success())
    ,
            Consumer {
                it.printStackTrace()
                _callback.value =
                    Event(SaveSwapDataState.ShowError(it.localizedMessage))
    ,
            SaveSwapDataTokenUseCase.Param(walletAddress, token, isSell)
        )
    }

    fun saveSendToken(address: String, token: Token) {
        saveSendTokenUseCase.execute(
            Action {
                _callbackSaveSend.value = Event(SaveSendState.Success())
    ,
            Consumer {
                _callbackSaveSend.value = Event(SaveSendState.Success())
    ,
            SaveSendTokenUseCase.Param(address, token)
        )
    }

    fun refresh() {
        prepareBalanceUseCase.execute(
            Consumer {
                _getBalanceStateCallback.value = Event(
                    GetBalanceState.Success(
                        it
                    )
                )
    ,
            Consumer { error ->
                error.printStackTrace()
                _getBalanceStateCallback.value =
                    Event(
                        GetBalanceState.ShowError(
                            error.localizedMessage
                        )
                    )

    ,
            PrepareBalanceUseCase.Param(true)
        )
    }

    fun saveFav(token: Token) {
        saveTokenUseCase.execute(
            Action {
                _saveTokenCallback.value = Event(SaveTokenState.Success(token.fav))
    ,
            Consumer {
                it.printStackTrace()
                _saveTokenCallback.value = Event(SaveTokenState.ShowError(it.localizedMessage))
    ,
            SaveTokenUseCase.Param(token)
        )
    }

}