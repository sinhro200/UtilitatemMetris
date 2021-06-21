package org.vsu.pt.team2.utilitatemmetrisapp.ui.main

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.orhanobut.logger.Logger
import com.yandex.metrica.YandexMetrica
import kotlinx.coroutines.launch
import org.vsu.pt.team2.utilitatemmetrisapp.R
import org.vsu.pt.team2.utilitatemmetrisapp.databinding.FragmentDialogAcceptChangesBinding
import org.vsu.pt.team2.utilitatemmetrisapp.databinding.FragmentMeterBinding
import org.vsu.pt.team2.utilitatemmetrisapp.managers.MeterManager
import org.vsu.pt.team2.utilitatemmetrisapp.managers.SessionManager
import org.vsu.pt.team2.utilitatemmetrisapp.models.Meter
import org.vsu.pt.team2.utilitatemmetrisapp.models.PaymentsFilter
import org.vsu.pt.team2.utilitatemmetrisapp.network.ApiResult
import org.vsu.pt.team2.utilitatemmetrisapp.ui.components.AvailableOnFullAccountDialogFragment
import org.vsu.pt.team2.utilitatemmetrisapp.ui.components.baseFragments.DisabledDrawerFragment
import org.vsu.pt.team2.utilitatemmetrisapp.ui.setFromVM
import org.vsu.pt.team2.utilitatemmetrisapp.ui.tools.*
import org.vsu.pt.team2.utilitatemmetrisapp.viewmodels.GeneralButtonViewModel
import org.vsu.pt.team2.utilitatemmetrisapp.viewmodels.MeterViewModel
import javax.inject.Inject


class MeterFragment : DisabledDrawerFragment(R.string.fragment_title_meter) {
    private lateinit var binding: FragmentMeterBinding
    private val meterIdentifier by MeterFragment.creationFragmentArgs.asProperty()
    private var meter: Meter? = null
    private var isSaved: Boolean = false
    private var menu: Menu? = null

    @Inject
    lateinit var meterManager: MeterManager

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMeterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initFields(binding)
        YandexMetrica.reportEvent(
            "Открытие экрана счётчика"
        )
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.meter_menu, menu)
        this.menu = menu
        this.menu?.findItem(R.id.meter_menu_fav)?.let {
            setMenuItemState(isSaved, it)
        }
    }

    private fun setMenuItemState(isMeterSaved: Boolean, item: MenuItem) {
        item.isChecked = isMeterSaved

        if (isMeterSaved)
            item.setIcon(R.drawable.ic_star_filled_24)
        else
            item.setIcon(R.drawable.ic_star_outline_24)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.meter_menu_fav -> {
                val changedValue = !item.isChecked

                lifecycleScope.launch {
                    val success = changeFav(changedValue)


                    if (success) {
                        setMenuItemState(changedValue, item)
                    }
//                        item.isChecked = changedValue
//
//
//                    if (item.isChecked)
//                        item.setIcon(R.drawable.ic_star_filled_24)
//                    else
//                        item.setIcon(R.drawable.ic_star_outline_24)
                }
                true
            }
            else ->
                super.onOptionsItemSelected(item)
        }
    }

    /**
     * @param isFav value to set
     * @return is successfully changed
     */
    suspend fun changeFav(isFav: Boolean): Boolean {
        meter?.let {
            val res = if (isFav)
                meterManager.saveMeter(it.identifier)
            else
                meterManager.deleteMeter(it.identifier)
            return when (res) {
                is ApiResult.Success ->
                    true
                is ApiResult.GenericError -> {
                    genericErrorToast(res)
                    false
                }
                is ApiResult.NetworkError -> {
                    networkConnectionErrorToast()
                    false
                }
            }
        }
        return false
    }

    fun initFields(binding: FragmentMeterBinding) {
        binding.meterPayBacklog.generalButton.visibility = View.GONE
        binding.meterShowHistory.generalButton.visibility = View.GONE
        binding.fragmentMeterNewdataTextfieldboxes.endIconImageButton.setOnClickListener {
            val newData = binding.fragmentMeterNewdataExtendededittext
                .text
                .toString()
                .toDoubleOrNull()
            if (newData == null)
                Logger.e("Cant convert new data value to double")
            else {
                Logger.d(
                    "apply new data clicked, new data: " +
                            binding.fragmentMeterNewdataExtendededittext.text.toString()
                )
                meter?.let { m ->
                    AcceptChangesDialog(
                        m.prevMonthData,
                        m.curMonthData,
                        newData,
                        { nv, onSuccess -> acceptChangesClicked(nv, onSuccess) }
                    )
                        .show(parentFragmentManager, "AcceptChangesDialogFragment")
                }
            }
            //dialog показать с вопросом "Сменить значения на [новые]?"
            //если юзер во временном аккаунте, отправлять на почту письмо для подтверждения
            //или отправлять раз в 4 раза
            //или спросить у сервера, "надо ли подтверждать действие?"
        }
        loadMeterData()
        binding.fragmentMeterSwipeRefreshLayout.setOnRefreshListener {
            loadMeterData()
        }
        binding.meterShowHistory.viewmodel = GeneralButtonViewModel(
            getString(R.string.show_payment_history),
            {
                if(sessionManager.isDemo){
                    AvailableOnFullAccountDialogFragment().show(
                        childFragmentManager,"AvailableOnFullAccountDialogFragment"
                    )
                }else {
                    appCompatActivity()?.replaceFragment(
                        HistoryFragment.createWithFilter(
                            PaymentsFilter(identifierMetric = meterIdentifier)
                        )
                    )
                }
            }
        )
        binding.meterPayBacklog.viewmodel = GeneralButtonViewModel(
            getString(R.string.pay_backlog),
            {
                appCompatActivity()?.replaceFragment(
                    PaymentFragment.createWithMetersIdentifier(
                        listOf(meterIdentifier)
                    )
                )
            }
        )
    }

    private fun loadMeterData(){
        lifecycleScope.launch {
            try {
                when (val res = meterManager.getMeterByIdentifier(meterIdentifier)) {
                    is ApiResult.NetworkError -> {
                        networkConnectionErrorToast()
                        binding.fragmentMeterSwipeRefreshLayout.isRefreshing = false
                        parentFragmentManager.popBackStack()
                    }
                    is ApiResult.GenericError -> {
                        genericErrorToast(res)
                        binding.fragmentMeterSwipeRefreshLayout.isRefreshing = false
                        parentFragmentManager.popBackStack()
                    }
                    is ApiResult.Success -> {
                        meter = res.value.first.also {
                            onMeterLoaded(it)
                        }
                        isSaved = res.value.second
                        menu?.findItem(R.id.meter_menu_fav)?.let {
                            setMenuItemState(isSaved, it)
                        }
                        binding.fragmentMeterSwipeRefreshLayout.isRefreshing = false
                    }
                }
            } catch (npe: NullPointerException) {
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun onMeterLoaded(meter: Meter) {
        binding.meterPayBacklog.generalButton.visibility = View.VISIBLE
        binding.meterShowHistory.generalButton.visibility = View.VISIBLE
        binding.setFromVM(
            MeterViewModel.fromMeter(meter),
            requireContext()
        )
        if (!meter.curMonthData.toString().isNullOrBlank()) {
            binding.fragmentMeterNewdataExtendededittext.setText(meter.curMonthData.toString())
        } else {
            binding.fragmentMeterNewdataExtendededittext.setText(meter.prevMonthData.toString())
        }
        binding.meterPayBacklog.generalButton.visibility =
            if (meter.balance < 0) View.VISIBLE else View.GONE
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appCompatActivity()?.requireMyApplication()?.appComponent?.meterComponent()
            ?.injectMeterFragment(this) ?: parentFragmentManager.popBackStack()
    }

    private suspend fun acceptChangesRequest(newValue: Double, onSuccess: () -> Unit) {
        when (val updateResult = meterManager.updateMeterData(meterIdentifier, newValue)) {
            is ApiResult.NetworkError ->
                networkConnectionErrorToast()
            is ApiResult.GenericError -> {
                genericErrorToast(updateResult)
            }
            is ApiResult.Success -> {
                YandexMetrica.reportEvent(
                    "Изменение показаний"
                )
                meter?.curMonthData = newValue
                binding.fragmentMeterNewdataExtendededittext.setText(newValue.toString())
                onSuccess.invoke()
            }
        }
    }

    private fun acceptChangesClicked(newValue: Double, onSuccess: () -> Unit) {
        lifecycleScope.launch {
            acceptChangesRequest(newValue, onSuccess)
        }
    }

    class AcceptChangesDialog(
        private val prevValue: Double,
        private val oldCurValue: Double,
        private val newCurValue: Double,
        private val onAcceptClick: (newValue: Double, onSuccess: () -> Unit) -> Unit
    ) : DialogFragment() {

        lateinit var binding: FragmentDialogAcceptChangesBinding

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            if ( prevValue >= newCurValue ) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.new_value_lower_than_previous),
                    Toast.LENGTH_LONG
                ).show()
                dismiss()
            }
            dialog!!.window?.setBackgroundDrawableResource(R.drawable.round_corner)
            binding = FragmentDialogAcceptChangesBinding.inflate(
                inflater,
                container,
                false
            )

            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            binding.fragmentDialogAcceptChangesCurFromData.text = oldCurValue.toString()
            binding.fragmentDialogAcceptChangesCurToData.text = newCurValue.toString()
            binding.fragmentDialogAcceptChangesBtnAccept.setOnClickListener {
                onAcceptClick.invoke(newCurValue, { dismiss() })
            }
            binding.fragmentDialogAcceptChangesBtnCancel.setOnClickListener {
                dismiss()
            }
        }

        override fun onStart() {
            super.onStart()
            val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            val height = (resources.displayMetrics.heightPixels * 0.40).toInt()
            dialog!!.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    companion object {
        private val creationFragmentArgs = CreationFragmentArgs<String>(
            { meterIdentifier, bundle ->
                bundle.putString("Ident", meterIdentifier)
                bundle
            },
            { bundle ->
                bundle.getString("Ident", null)
            }
        )

        fun createWithMeterIdentifier(identifier: String): MeterFragment {
            return creationFragmentArgs.fill(MeterFragment(), identifier)
        }
    }
}