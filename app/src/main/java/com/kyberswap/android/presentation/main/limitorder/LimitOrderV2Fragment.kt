package com.kyberswap.android.presentation.main.limitorder

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.util.Attributes
import com.google.android.material.tabs.TabLayout
import com.google.firebase.analytics.FirebaseAnalytics
import com.jakewharton.rxbinding3.view.focusChanges
import com.jakewharton.rxbinding3.widget.textChanges
import com.kyberswap.android.AppExecutors
import com.kyberswap.android.R
import com.kyberswap.android.databinding.FragmentLimitOrderV2Binding
import com.kyberswap.android.domain.SchedulerProvider
import com.kyberswap.android.domain.model.EligibleAddress
import com.kyberswap.android.domain.model.EligibleWalletStatus
import com.kyberswap.android.domain.model.LocalLimitOrder
import com.kyberswap.android.domain.model.NotificationLimitOrder
import com.kyberswap.android.domain.model.PendingBalances
import com.kyberswap.android.domain.model.Token
import com.kyberswap.android.domain.model.UserInfo
import com.kyberswap.android.domain.model.Wallet
import com.kyberswap.android.domain.model.WalletChangeEvent
import com.kyberswap.android.presentation.base.BaseFragment
import com.kyberswap.android.presentation.common.LoginState
import com.kyberswap.android.presentation.common.PendingTransactionNotification
import com.kyberswap.android.presentation.helper.DialogHelper
import com.kyberswap.android.presentation.helper.Navigator
import com.kyberswap.android.presentation.main.MainActivity
import com.kyberswap.android.presentation.main.MainPagerAdapter
import com.kyberswap.android.presentation.main.balance.CheckEligibleWalletState
import com.kyberswap.android.presentation.main.profile.UserInfoState
import com.kyberswap.android.presentation.main.swap.GetGasPriceState
import com.kyberswap.android.presentation.splash.GetWalletState
import com.kyberswap.android.util.USER_CLICK_100_PERCENT
import com.kyberswap.android.util.USER_CLICK_25_PERCENT
import com.kyberswap.android.util.USER_CLICK_50_PERCENT
import com.kyberswap.android.util.USER_CLICK_BUY
import com.kyberswap.android.util.USER_CLICK_BUY_SELL_ERROR
import com.kyberswap.android.util.USER_CLICK_CANCEL_ORDER
import com.kyberswap.android.util.USER_CLICK_CHART
import com.kyberswap.android.util.USER_CLICK_CHOOSE_MARKET
import com.kyberswap.android.util.USER_CLICK_LEARN_MORE
import com.kyberswap.android.util.USER_CLICK_MANAGE_ORDER
import com.kyberswap.android.util.USER_CLICK_MARKET_PRICE_TEXT
import com.kyberswap.android.util.USER_CLICK_PRICE_TEXT
import com.kyberswap.android.util.USER_CLICK_SELL
import com.kyberswap.android.util.di.ViewModelFactory
import com.kyberswap.android.util.ext.colorRateV2
import com.kyberswap.android.util.ext.createEvent
import com.kyberswap.android.util.ext.exactAmount
import com.kyberswap.android.util.ext.hideKeyboard
import com.kyberswap.android.util.ext.isNetworkAvailable
import com.kyberswap.android.util.ext.isSomethingWrongError
import com.kyberswap.android.util.ext.openUrl
import com.kyberswap.android.util.ext.percentage
import com.kyberswap.android.util.ext.setAmount
import com.kyberswap.android.util.ext.setViewEnable
import com.kyberswap.android.util.ext.showDrawer
import com.kyberswap.android.util.ext.textToDouble
import com.kyberswap.android.util.ext.toBigDecimalOrDefaultZero
import com.kyberswap.android.util.ext.toDisplayNumber
import com.kyberswap.android.util.ext.toDoubleOrDefaultZero
import com.kyberswap.android.util.ext.toDoubleSafe
import com.kyberswap.android.util.ext.underline
import io.reactivex.disposables.CompositeDisposable
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class LimitOrderV2Fragment : BaseFragment(), PendingTransactionNotification, LoginState {

    private lateinit var binding: FragmentLimitOrderV2Binding

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var appExecutors: AppExecutors

    private var wallet: Wallet? = null

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var schedulerProvider: SchedulerProvider

    private var notification: NotificationLimitOrder? = null

    private var type: Int = LocalLimitOrder.TYPE_BUY

    private val isSell
        get() = type == LocalLimitOrder.TYPE_SELL

    @Inject
    lateinit var analytics: FirebaseAnalytics

    private val viewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(LimitOrderV2ViewModel::class.java)
    }

    val baseToken: Token?
        get() = if (isSell) binding.order?.tokenSource else binding.order?.tokenDest

    val quoteToken: Token?
        get() = if (isSell) binding.order?.tokenDest else binding.order?.tokenSource

    val rateUsdQuote: BigDecimal
        get() = quoteToken?.rateUsdNow ?: BigDecimal.ZERO

    val priceUsdQuote: String
        get() {
            if (marketPrice.equals("--")) return "--"
            val priceUsd = rateUsdQuote.multiply(marketPrice.toBigDecimalOrDefaultZero())
            return if (priceUsd == BigDecimal.ZERO) "--" else priceUsd.toDisplayNumber()
        }

    private var pendingBalances: PendingBalances? = null

    private val totalAmount: String
        get() = binding.edtTotal.text.toString()
    private val amount: String
        get() = binding.edtAmount.text.toString()

    private val srcAmount: String
        get() = if (isSell) amount else totalAmount

    private val dstAmount: String
        get() = if (isSell) totalAmount else amount

    private val tokenSourceSymbol: String?
        get() = binding.order?.tokenSource?.tokenSymbol

    private val tokenDestSymbol: String?
        get() = binding.order?.tokenDest?.tokenSymbol

    val compositeDisposable = CompositeDisposable()

    var hasUserFocus: Boolean? = false

    private val totalLock = AtomicBoolean()
    private val amountLock = AtomicBoolean()

    private var currentFocus: EditText? = null

    private val isAmountFocus: Boolean
        get() = currentFocus == binding.edtAmount

    private val isTotalFocus: Boolean
        get() = currentFocus == binding.edtTotal

    private val priceText: String
        get() = binding.edtPrice.text.toString()

    private val minRate: BigDecimal
        get() = when {
            isSell -> {
                priceText.toBigDecimalOrDefaultZero()
            }
            else ->
                when {
                    priceText.toDoubleSafe() == 0.0 -> BigDecimal.ZERO
                    else -> BigDecimal.ONE.divide(
                        priceText.toBigDecimalOrDefaultZero(),
                        18,
                        RoundingMode.CEILING
                    )
                }
        }

    private val marketRate: BigDecimal
        get() = when {
            isSell -> {
                marketPrice.toBigDecimalOrDefaultZero()
            }
            else ->
                when {
                    marketPrice?.toDoubleSafe() == 0.0 -> BigDecimal.ZERO
                    else -> BigDecimal.ONE.divide(
                        marketPrice.toBigDecimalOrDefaultZero(),
                        18,
                        RoundingMode.CEILING
                    )
                }
        }

    private val balanceText: String
        get() = binding.tvBalance.text.toString().split(" ").first()

    private val marketPrice: String?
        get() {
            return if (type == LocalLimitOrder.TYPE_SELL) binding.market?.displaySellPrice
            else binding.market?.displayBuyPrice
        }

    private val calcAmount: String
        get() = calcTotalAmount(priceText, totalAmount)

    private val calcTotalAmount: String
        get() = calcAmount(priceText, amount)

    private var eleigibleAddress: EligibleAddress? = null

    private val hasRelatedOrder: Boolean
        get() = viewModel.relatedOrders.any {
            it.src == binding.order?.tokenSource?.symbol &&
                    it.dst == binding.order?.tokenDest?.symbol &&
                    it.userAddr == wallet?.address
        }

    private val viewByType: EditText
        get() = if (isSell) binding.edtAmount else binding.edtTotal

    private var orderAdapter: OrderAdapter? = null

    private var userInfo: UserInfo? = null

    private var eligibleWalletStatus: EligibleWalletStatus? = null

    private val order: LocalLimitOrder?
        get() = binding.order

    @Inject
    lateinit var dialogHelper: DialogHelper

    private val currentActivity by lazy {
        activity as MainActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notification = arguments?.getParcelable(NOTIFICATION_PARAM)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentLimitOrderV2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel.getMarkets()
        binding.tvSubmitOrder.setViewEnable(true)
        notification?.let {
            dialogHelper.showOrderFillDialog(it) { url ->
                openUrl(getString(R.string.transaction_etherscan_endpoint_url) + url)
            }
        }

        binding.imgMenu.setOnClickListener {
            showDrawer(true)
        }

        viewModel.getSelectedWallet()
        viewModel.getSelectedWalletCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetWalletState.Success -> {
                        binding.walletName = state.wallet.display()
                        if (!state.wallet.isSameWallet(wallet)) {
                            wallet = state.wallet
                            viewModel.getSelectedMarket(wallet)
                            viewModel.getLimitOrder(wallet, type)
                            viewModel.getLoginStatus()
                        }
                    }
                    is GetWalletState.ShowError -> {

                    }
                }
            }
        })

        viewModel.getSelectedMarketCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetSelectedMarketState.Success -> {
                        if (state.market != binding.market) {
                            binding.market = state.market
                            binding.executePendingBindings()
                            binding.tvPrice.text =
                                if (priceUsdQuote != "--") {
                                    "$marketPrice ~ $$priceUsdQuote"
                                } else {
                                    marketPrice
                                }

                            binding.tlHeader.getTabAt(0)?.text = String.format(
                                getString(R.string.buy_token),
                                state.market.baseSymbol
                            )
                            binding.tlHeader.getTabAt(1)?.text = String.format(
                                getString(R.string.sell_token),
                                state.market.baseSymbol
                            )
                            if (hasUserFocus != true) {
                                binding.edtPrice.setAmount(marketPrice)
                            }

                            if (isAmountFocus) {
                                binding.edtTotal.setAmount(calcTotalAmount)
                            } else {
                                if (binding.edtPrice.text.isNotEmpty()) {
                                    binding.edtAmount.setAmount(
                                        calcAmount
                                    )
                                }
                            }
                        }
                    }
                    is GetSelectedMarketState.ShowError -> {
                        Timber.e(state.message)
                    }
                }
            }
        })

        viewModel.getLocalLimitOrderCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetLocalLimitOrderState.Success -> {
                        if (!state.order.isSameTokenPairForAddress(binding.order)) {
                            binding.order = state.order
                            binding.executePendingBindings()
                            resetUI()
                            viewModel.getPendingBalances(wallet)
                            viewModel.getFee(
                                binding.order,
                                srcAmount,
                                dstAmount,
                                wallet
                            )
                            viewModel.getGasPrice()


                            binding.tvPrice.text = if (priceUsdQuote != "--") {
                                "$marketPrice ~ $$priceUsdQuote"
                            } else {
                                marketPrice
                            }
                            refresh()
                        }
                    }
                    is GetLocalLimitOrderState.ShowError -> {

                    }
                }
            }
        })

        viewModel.getFeeCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                showLoading(state == GetFeeState.Loading && binding.tvOriginalFee.visibility == View.VISIBLE)
                when (state) {
                    is GetFeeState.Success -> {

                        binding.tvFee.text = String.format(
                            getString(R.string.limit_order_fee),
                            srcAmount.toBigDecimalOrDefaultZero()
                                .times(state.fee.totalFee.toBigDecimal()).toDisplayNumber()
                                .exactAmount(),
                            tokenSourceSymbol
                        )


                        if (amount.toBigDecimalOrDefaultZero() != BigDecimal.ZERO &&
                            state.fee.discountPercent > 0.0
                        ) {
                            showDiscount(true)
                            binding.tvOriginalFee.text = String.format(
                                getString(R.string.limit_order_fee),
                                srcAmount.toBigDecimalOrDefaultZero()
                                    .times(state.fee.totalNonDiscountedFee.toBigDecimal())
                                    .toDisplayNumber().exactAmount(),
                                tokenSourceSymbol
                            )

                            binding.tvOff.text =

                                if (state.fee.discountPercent % 1 == 0.0) {
                                    String.format(
                                        getString(R.string.discount_fee_long_type),
                                        state.fee.discountPercent.toLong()
                                    )
                                } else {
                                    String.format(
                                        getString(R.string.discount_fee), state.fee.discountPercent
                                    )
                                }
                        } else {
                            showDiscount(false)
                        }

                        val order = binding.order?.copy(
                            fee = state.fee.fee.toBigDecimal(),
                            transferFee = state.fee.transferFee.toBigDecimal()
                        )
                        if (order != binding.order) {
                            binding.order = order
                            binding.executePendingBindings()
                            binding.invalidateAll()
                        }
                    }
                    is GetFeeState.ShowError -> {
                        val err = state.message ?: getString(R.string.something_wrong)
                        if (isNetworkAvailable() && !isSomethingWrongError(err)) {
                            showError(err)
                        }
                    }
                }
            }
        })

        viewModel.getPendingBalancesCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetPendingBalancesState.Success -> {

                        this.pendingBalances = state.pendingBalances

                        updateAvailableAmount(state.pendingBalances)
                    }
                    is GetPendingBalancesState.ShowError -> {
                        val err = state.message ?: getString(R.string.something_wrong)
                        if (isNetworkAvailable() && !isSomethingWrongError(err)) {
                            showError(err)
                        }
                    }
                }
            }
        })

        viewModel.getGetNonceStateCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetNonceState.Success -> {
                        val order = binding.order?.copy(nonce = state.nonce)
                        binding.order = order
                    }
                    is GetNonceState.ShowError -> {
                        if (isNetworkAvailable()) {
                            showError(
                                state.message ?: getString(R.string.something_wrong)
                            )
                        }
                    }
                }
            }
        })

        viewModel.getGetGasPriceCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetGasPriceState.Success -> {
                        val order = binding.order?.copy(
                            gasPrice = state.gas.fast
                        )
                        if (order != binding.order) {
                            binding.order = order
                            binding.executePendingBindings()
                        }
                    }
                    is GetGasPriceState.ShowError -> {

                    }
                }
            }
        })

        binding.rvRelatedOrder.layoutManager = LinearLayoutManager(
            activity,
            RecyclerView.VERTICAL,
            false
        )

        if (orderAdapter == null) {
            orderAdapter =
                OrderAdapter(
                    appExecutors
                    , {

                        dialogHelper.showCancelOrder(it) {
                            viewModel.cancelOrder(it)
                            analytics.logEvent(
                                USER_CLICK_CANCEL_ORDER,
                                Bundle().createEvent()
                            )
                        }
                    }, {

                    }, {

                    }, {
                        dialogHelper.showInvalidatedDialog(it)
                    })
        }

        orderAdapter?.mode = Attributes.Mode.Single
        binding.rvRelatedOrder.adapter = orderAdapter
        viewModel.getRelatedOrderCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetRelatedOrdersState.Success -> {
                        orderAdapter?.submitList(listOf())
                        orderAdapter?.submitList(state.orders)

                        binding.tvRelatedOrder.visibility =
                            if (hasRelatedOrder) View.VISIBLE else View.GONE
                    }
                    is GetRelatedOrdersState.ShowError -> {
                        val err = state.message ?: getString(R.string.something_wrong)
                        if (isNetworkAvailable() && !isSomethingWrongError(err)) {
                            showError(err)
                        }
                    }
                }
            }
        })

        viewModel.getLoginStatusCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is UserInfoState.Success -> {
                        userInfo = state.userInfo
                        when {
                            !(state.userInfo != null && state.userInfo.uid > 0) -> {
                                orderAdapter?.submitList(listOf())
                                pendingBalances = null
                                updateAvailableAmount(pendingBalances)
                                binding.tvRelatedOrder.visibility = View.GONE
                            }
                            else -> {
                                refresh()
                            }
                        }
                    }
                    is UserInfoState.ShowError -> {
                        if (isNetworkAvailable()) {
                            showError(
                                state.message ?: getString(R.string.something_wrong)
                            )
                        }
                    }
                }
            }
        })

        viewModel.getEligibleAddressCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is CheckEligibleAddressState.Success -> {
                        if (this.eleigibleAddress != state.eligibleAddress || state.isWalletChangeEvent) {
                            this.eleigibleAddress = state.eligibleAddress
                            if (state.eligibleAddress.success && !state.eligibleAddress.eligibleAddress &&
                                currentFragment is LimitOrderV2Fragment
                            ) {
                                showAlertWithoutIcon(
                                    title = getString(R.string.title_error),
                                    message = getString(R.string.address_not_eligible)
                                )
                            }
                        }
                    }
                    is CheckEligibleAddressState.ShowError -> {
                        if (isNetworkAvailable()) {
                            showError(
                                state.message ?: getString(R.string.something_wrong)
                            )
                        }
                    }
                }
            }
        })

        viewModel.checkEligibleWalletCallback.observe(viewLifecycleOwner, Observer { event ->
            event?.getContentIfNotHandled()?.let { state ->
                showProgress(state == CheckEligibleWalletState.Loading)
                when (state) {
                    is CheckEligibleWalletState.Success -> {
                        eligibleWalletStatus = state.eligibleWalletStatus
                        if (state.eligibleWalletStatus.success && !state.eligibleWalletStatus.eligible) {
                            binding.tvSubmitOrder.setViewEnable(false)
                        } else {
                            onVerifyWalletComplete()
                        }
                    }
                    is CheckEligibleWalletState.ShowError -> {
                        onVerifyWalletComplete()
                    }
                }
            }
        })

        currentActivity.mainViewModel.checkEligibleWalletCallback.observe(
            viewLifecycleOwner,
            Observer { event ->
                event?.peekContent()?.let { state ->
                    when (state) {
                        is CheckEligibleWalletState.Success -> {
                            eligibleWalletStatus = state.eligibleWalletStatus
                            verifyEligibleWallet(true)
                        }
                        is CheckEligibleWalletState.ShowError -> {

                        }
                    }
                }
            })

        binding.tvManageOrder.setOnClickListener {
            when {
                !isNetworkAvailable() -> {
                    showNetworkUnAvailable()
                }

                userInfo == null || userInfo!!.uid <= 0 -> {
                    moveToLoginTab()
                    showAlertWithoutIcon(
                        title = getString(R.string.sign_in_required_title), message = getString(
                            R.string.sign_in_to_use_limit_order_feature
                        )
                    )
                }

                else -> {
                    hideKeyboard()
                    navigator.navigateToManageOrder(
                        currentFragment,
                        wallet
                    )
                }
            }

            analytics.logEvent(
                USER_CLICK_MANAGE_ORDER,
                Bundle().createEvent()
            )
        }

        viewModel.cancelOrderCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                showProgress(state == CancelOrdersState.Loading)
                when (state) {
                    is CancelOrdersState.Success -> {
                        getRelatedOrders()
                        getNonce()
                        viewModel.getPendingBalances(wallet)
                    }
                    is CancelOrdersState.ShowError -> {
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }
                }
            }
        })

        compositeDisposable.add(binding.edtPrice.focusChanges()
            .skipInitialValue()
            .observeOn(schedulerProvider.ui())
            .subscribe {
                if (it) {
                    hasUserFocus = it
                    playAnimation()
                }
            })

        compositeDisposable.add(binding.edtTotal.focusChanges()
            .skipInitialValue()
            .observeOn(schedulerProvider.ui())
            .subscribe {
                totalLock.set(it || isTotalFocus)
                if (it) {
                    playAnimation()
                    updateCurrentFocus(binding.edtTotal)
                    if (binding.edtTotal.text.isNullOrEmpty()) {
                        binding.edtAmount.setText("")
                    }
                }
            })

        compositeDisposable.add(binding.edtAmount.focusChanges()
            .skipInitialValue()
            .observeOn(schedulerProvider.ui())
            .subscribe {
                amountLock.set(it)
                if (it) {
                    playAnimation()
                    updateCurrentFocus(binding.edtAmount)
                    if (binding.edtAmount.text.isNullOrEmpty()) {
                        binding.edtTotal.setText("")
                    }
                }
            })


        compositeDisposable.add(binding.edtAmount.textChanges()
            .skipInitialValue()
            .observeOn(schedulerProvider.ui())
            .subscribe { text ->
                if (amountLock.get()) {
                    binding.edtTotal.setAmount(calcTotalAmount)
                    viewModel.getFee(
                        binding.order,
                        srcAmount,
                        dstAmount,
                        wallet
                    )
                }
            })

        compositeDisposable.add(binding.edtTotal.textChanges()
            .skipInitialValue()
            .observeOn(schedulerProvider.ui())
            .subscribe { text ->
                if (!totalLock.get() || isAmountFocus) return@subscribe

                if (hasUserFocus != true && priceText.isEmpty()) {
                    binding.edtPrice.setAmount(marketPrice)
                }

                binding.edtAmount.setAmount(calcAmount)
                viewModel.getFee(
                    binding.order,
                    srcAmount,
                    dstAmount,
                    wallet
                )

                if (text.isNullOrEmpty()) {
                    binding.edtAmount.setText("")
                }
            })

        compositeDisposable.add(
            binding.edtPrice.textChanges()
                .skipInitialValue()
                .observeOn(schedulerProvider.ui())
                .subscribe { text ->

                    binding.tvRateWarning.colorRateV2(text.toString().percentage(marketPrice))
                    binding.order?.let { order ->
                        if (isAmountFocus) {
                            binding.edtTotal.setAmount(calcTotalAmount)
                        } else {
                            binding.edtAmount.setAmount(calcAmount)
                        }

                        val bindingOrder = binding.order?.copy(
                            srcAmount = srcAmount,
                            minRate = minRate
                        )

                        order.let {
                            if (binding.order != bindingOrder) {
                                binding.order = bindingOrder
                                binding.executePendingBindings()
                            }
                        }
                    }

                    if (text.isNullOrEmpty()) {
                        binding.edtAmount.setText("")
                    }
                })

        binding.tlHeader.addTab(binding.tlHeader.newTab().setText("BUY"))
        binding.tlHeader.addTab(binding.tlHeader.newTab().setText("SELL"))

        binding.tlHeader.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: 0
                type = if (position == 0) {
                    LocalLimitOrder.TYPE_BUY
                } else {
                    LocalLimitOrder.TYPE_SELL
                }
                binding.isSell = isSell
                binding.executePendingBindings()
                viewModel.getLimitOrder(wallet, type)
                resetUI()
            }
        })

        binding.tvTokenPair.setOnClickListener {
            navigator.navigateToLimitOrderMarket(currentFragment, type, quoteToken?.tokenSymbol)
            analytics.logEvent(
                USER_CLICK_CHOOSE_MARKET,
                Bundle().createEvent()
            )
        }

        binding.imgCandle.setOnClickListener {
            navigator.navigateToChartScreen(
                currentFragment,
                wallet,
                baseToken,
                binding.market?.chartMarket ?: "",
                binding.market,
                type
            )

            analytics.logEvent(
                USER_CLICK_CHART,
                Bundle().createEvent()
            )
        }

        binding.imgFlag.setOnClickListener {
            navigator.navigateToNotificationScreen(currentFragment)
        }

        binding.tv25Percent.setOnClickListener {

            updateCurrentFocus(viewByType)
            hideKeyboard()
            viewByType.setAmount(
                balanceText.toBigDecimalOrDefaultZero().multiply(
                    0.25.toBigDecimal()
                ).toDisplayNumber()
            )
            analytics.logEvent(
                USER_CLICK_25_PERCENT,
                Bundle().createEvent()
            )

        }

        binding.tv50Percent.setOnClickListener {
            updateCurrentFocus(viewByType)
            hideKeyboard()
            viewByType.setAmount(
                balanceText.toBigDecimalOrDefaultZero().multiply(
                    0.5.toBigDecimal()
                ).toDisplayNumber()
            )

            analytics.logEvent(
                USER_CLICK_50_PERCENT,
                Bundle().createEvent()
            )
        }

        binding.tv100Percent.setOnClickListener {
            updateCurrentFocus(viewByType)
            hideKeyboard()
            val currentOrder = order
            if (currentOrder != null) {
                if (currentOrder.tokenSource.isETHWETH) {
                    viewByType.setText(
                        currentOrder.availableAmountForTransfer(
                            balanceText.toBigDecimalOrDefaultZero(),
                            currentOrder.gasPrice.toBigDecimalOrDefaultZero()
                        ).toDisplayNumber()
                    )
                } else {
                    viewByType.setAmount(balanceText)
                }
            }

            analytics.logEvent(
                USER_CLICK_100_PERCENT,
                Bundle().createEvent()
            )

        }

        binding.tvPrice.setOnClickListener {
            binding.edtPrice.setAmount(marketPrice)
            binding.tvRateWarning.text = ""
            analytics.logEvent(
                USER_CLICK_MARKET_PRICE_TEXT,
                Bundle().createEvent()
            )
        }

        binding.tvPriceTitle.setOnClickListener {
            binding.edtPrice.setAmount(marketPrice)
            binding.tvRateWarning.text = ""
            analytics.logEvent(
                USER_CLICK_PRICE_TEXT,
                Bundle().createEvent()
            )
        }

        binding.tvOriginalFee.paintFlags =
            binding.tvOriginalFee.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        binding.tvLearnMore.underline(getString(R.string.learn_more))

        binding.tvLearnMore.setOnClickListener {
            openUrl(getString(R.string.order_fee_url))
            analytics.logEvent(
                USER_CLICK_LEARN_MORE,
                Bundle().createEvent()
            )
        }


        binding.tvSubmitOrder.setOnClickListener {
            var error = ""
            when {
                !isNetworkAvailable() -> {
                    showNetworkUnAvailable()
                }
                srcAmount.isEmpty() -> {
                    showAlertWithoutIcon(
                        title = getString(R.string.invalid_amount),
                        message = getString(R.string.specify_amount)
                    )
                }
                srcAmount.toBigDecimalOrDefaultZero() >
                        viewModel.calAvailableAmount(
                            binding.order?.tokenSource,
                            pendingBalances
                        ).toBigDecimalOrDefaultZero() -> {

                    error = getString(R.string.limit_order_insufficient_balance)
                    showAlertWithoutIcon(
                        title = getString(R.string.title_amount_too_big),
                        message = error
                    )

                    analytics.logEvent(
                        USER_CLICK_BUY_SELL_ERROR,
                        Bundle().createEvent(error)
                    )
                }

                binding.order?.amountTooSmall(srcAmount, dstAmount) == true -> {
                    error = getString(R.string.limit_order_amount_too_small)
                    showAlertWithoutIcon(
                        title = getString(R.string.invalid_amount),
                        message = error
                    )
                    analytics.logEvent(
                        USER_CLICK_BUY_SELL_ERROR,
                        Bundle().createEvent(error)
                    )
                }

                binding.edtPrice.textToDouble() == 0.0 -> {
                    error = getString(R.string.limit_order_invalid_rate)
                    showAlertWithoutIcon(
                        title = getString(R.string.invalid_amount),
                        message = error
                    )
                    analytics.logEvent(
                        USER_CLICK_BUY_SELL_ERROR,
                        Bundle().createEvent(error)
                    )
                }

                priceText.toBigDecimalOrDefaultZero() > marketPrice.toBigDecimalOrDefaultZero() * 10.toBigDecimal() -> {
                    error = getString(R.string.limit_order_rate_too_big)
                    showAlertWithoutIcon(
                        title = getString(R.string.invalid_amount),
                        message = error
                    )
                    analytics.logEvent(
                        USER_CLICK_BUY_SELL_ERROR,
                        Bundle().createEvent(error)
                    )
                }

                userInfo == null || userInfo!!.uid <= 0 -> {
                    moveToLoginTab()
                    error = getString(
                        R.string.sign_in_to_use_limit_order_feature
                    )
                    showAlertWithoutIcon(
                        title = getString(R.string.sign_in_required_title),
                        message = error
                    )
                    analytics.logEvent(
                        USER_CLICK_BUY_SELL_ERROR,
                        Bundle().createEvent(error)
                    )
                }

                (wallet?.isPromo == true) -> {
                    error = getString(
                        R.string.submit_order_promo_code
                    )
                    showAlertWithoutIcon(
                        title = getString(R.string.title_error),
                        message = error
                    )
                    analytics.logEvent(
                        USER_CLICK_BUY_SELL_ERROR,
                        Bundle().createEvent(error)
                    )
                }

                eleigibleAddress?.success == true && eleigibleAddress?.eligibleAddress != true -> {
                    error = getString(R.string.address_not_eligible)
                    showAlertWithoutIcon(
                        title = getString(R.string.title_error),
                        message = error
                    )
                    analytics.logEvent(
                        USER_CLICK_BUY_SELL_ERROR,
                        Bundle().createEvent(error)
                    )
                }

                else -> binding.order?.let {
                    viewModel.checkEligibleWallet(wallet)
                    analytics.logEvent(
                        if (isSell) USER_CLICK_SELL else USER_CLICK_BUY,
                        Bundle().createEvent(it.displayTokenPair)
                    )

                }
            }
        }


        viewModel.saveOrderCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is SaveLimitOrderState.Success -> {

                        val warningOrderList = viewModel.warningOrderList(
                            minRate,
                            orderAdapter?.orderList ?: listOf()
                        )

                        when {
                            warningOrderList.isNotEmpty() -> {
                                hideKeyboard()
                                navigator.navigateToCancelOrderFragment(
                                    currentFragment,
                                    wallet,
                                    ArrayList(warningOrderList),
                                    binding.order,
                                    viewModel.needConvertWETH(
                                        binding.order,
                                        pendingBalances
                                    )
                                )
                            }

                            viewModel.needConvertWETH(
                                binding.order,
                                pendingBalances
                            ) -> {
                                hideKeyboard()
                                navigator.navigateToConvertFragment(
                                    currentFragment,
                                    wallet,
                                    binding.order
                                )
                            }

                            else -> {
                                hideKeyboard()
                                navigator.navigateToOrderConfirmV2Screen(
                                    currentFragment,
                                    wallet,
                                    binding.order
                                )
                            }
                        }
                    }
                    is SaveLimitOrderState.ShowError -> {
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }

                }
            }
        })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: WalletChangeEvent) {
        viewModel.getSelectedMarket(wallet)
        viewModel.getLimitOrder(wallet, type)
        viewModel.getLoginStatus()
        wallet?.let { viewModel.checkEligibleAddress(it, true) }
    }

    private fun playAnimation() {
//        val animator = ObjectAnimator.ofInt(
//            binding.scView,
//            "scrollY",
//            binding.tvFee.bottom
//        )
//
//        animator.duration = 300
//        animator.interpolator = AccelerateDecelerateInterpolator()
//        animator.start()
    }

    fun getLimitOrder() {
        wallet?.let {
            viewModel.getLimitOrder(it, type)
        }
    }

    fun checkEligibleAddress() {
        wallet?.let {
            viewModel.checkEligibleAddress(it)
        }
    }

    fun verifyEligibleWallet(isDisablePopup: Boolean = false) {
        eligibleWalletStatus?.let {
            if (it.success && !it.eligible) {
                if (!isDisablePopup) {
                    binding.tvSubmitOrder.setViewEnable(false)
                }
                binding.tvSubmitOrder.setViewEnable(false)

                showError(it.message)
            } else {
                binding.tvSubmitOrder.setViewEnable(true)
            }
        }
    }

    private fun onVerifyWalletComplete() {
        binding.tvSubmitOrder.setViewEnable(true)
        saveLimitOrder()
    }

    private fun saveLimitOrder() {
        binding.order?.let {

            val order = it.copy(
                srcAmount = srcAmount,
                minRate = minRate,
                marketRate = marketRate.toDisplayNumber()
            )

            if (binding.order != order) {
                binding.order = order
            }
            viewModel.saveLimitOrder(
                order, true
            )
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    fun showDiscount(isShown: Boolean) {
        val state = if (isShown) View.VISIBLE else View.GONE
        binding.tvOriginalFee.visibility = state
        binding.tvOff.visibility = state
        binding.progressBar.visibility = View.GONE
    }

    private fun moveToLoginTab() {
        (activity as? MainActivity)?.moveToTab(MainPagerAdapter.PROFILE, true)
    }

    fun getRelatedOrders() {
        binding.order?.let { wallet?.let { it1 -> viewModel.getRelatedOrders(it, it1) } }
    }

    fun getNonce() {
        binding.order?.let { wallet?.let { it1 -> viewModel.getNonce(it, it1) } }
    }

    fun getPendingBalance() {
        viewModel.getPendingBalances(wallet)
    }

    fun refresh() {
        getRelatedOrders()
        getPendingBalance()
        getNonce()
    }

    private fun resetUI() {
        hasUserFocus = false
        binding.edtPrice.setAmount(marketPrice)

        binding.edtTotal.setText("")
        binding.tvFee.text = ""
        binding.edtAmount.setText("")
    }

    private fun calcTotalAmount(rateText: String, srcAmount: String): String {
        return if (rateText.toDoubleOrDefaultZero() != 0.0) {
            srcAmount.toBigDecimalOrDefaultZero()
                .divide(
                    rateText.toBigDecimalOrDefaultZero(),
                    18,
                    RoundingMode.UP
                ).toDisplayNumber()
        } else {
            ""
        }
    }

    private fun calcAmount(rate: String, dstAmount: String): String {
        return if (rate.isEmpty()) {
            ""
        } else {

            rate.toBigDecimalOrDefaultZero()
                .multiply(dstAmount.toBigDecimalOrDefaultZero())
                .toDisplayNumber()
        }
    }

    fun showFillOrder(notificationLimitOrder: NotificationLimitOrder) {
        clearFragmentBackStack()
        dialogHelper.showOrderFillDialog(notificationLimitOrder) { url ->
            openUrl(getString(R.string.transaction_etherscan_endpoint_url) + url)
        }
    }

    private fun clearFragmentBackStack() {
        val fm = currentFragment.childFragmentManager
        for (i in 0 until fm.backStackEntryCount) {
            fm.popBackStack()
        }
    }

    override fun showPendingTxNotification(showNotification: Boolean) {
        if (::binding.isInitialized) {
            binding.vNotification.visibility = if (showNotification) View.VISIBLE else View.GONE
        }
    }

    override fun showUnReadNotification(showNotification: Boolean) {
        if (::binding.isInitialized) {
            binding.vFlagNotification.visibility = if (showNotification) View.VISIBLE else View.GONE
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun updateAvailableAmount(pendingBalances: PendingBalances?) {
        val calAvailableAmount = binding.order?.let { order ->
            viewModel.calAvailableAmount(
                order.tokenSource,
                pendingBalances
            )
        }

        val availableAmount = "$calAvailableAmount $tokenSourceSymbol"

        if (binding.availableAmount != availableAmount) {
            binding.availableAmount = availableAmount
            binding.executePendingBindings()
        }
    }

//    private fun getRate(order: LocalLimitOrder) {
//        viewModel.getMarketRate(order)
//        viewModel.getExpectedRate(
//            order,
//            binding.edtAmount.getAmountOrDefaultValue()
//        )
//    }

    private fun updateCurrentFocus(view: EditText?) {
        currentFocus?.isSelected = false
        currentFocus = view
        currentFocus?.isSelected = true
        totalLock.set(view == binding.edtTotal)
        amountLock.set(view == binding.edtAmount)
    }

    override fun onDestroyView() {
        compositeDisposable.clear()
        hideKeyboard()
        super.onDestroyView()
    }

    companion object {
        private const val NOTIFICATION_PARAM = "notification_param"
        fun newInstance(notification: NotificationLimitOrder? = null) =
            LimitOrderV2Fragment().apply {
                arguments = Bundle().apply {
                    putParcelable(NOTIFICATION_PARAM, notification)
                }
            }
    }

    override fun getLoginStatus() {
        viewModel.getLoginStatus()
    }
}