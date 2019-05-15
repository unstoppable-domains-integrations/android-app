package com.kyberswap.android.util.views

import android.text.Spannable
import android.text.SpannableString
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import com.kyberswap.android.R
import com.kyberswap.android.util.ext.toBigDecimalOrDefaultZero
import io.github.inflationx.calligraphy3.CalligraphyTypefaceSpan
import io.github.inflationx.calligraphy3.TypefaceUtils
import java.math.BigDecimal

object TextViewBindingAdapter {
    @BindingAdapter("app:resourceId")
    @JvmStatic
    fun setText(view: TextView, resourceId: Int) {
        view.text = view.context.resources.getString(resourceId)
    }

    @BindingAdapter("app:data", "app:font")
    @JvmStatic
    fun styleText(view: TextView, words: Array<String>, font: String) {

        val spannableString = SpannableString(view.text.toString())
        val typeface = TypefaceUtils.load(
            view.context.assets,
            font
        )

        words.forEach {
            val calligraphyTypeface = CalligraphyTypefaceSpan(
                typeface
            )

            spannableString.setSpan(
                calligraphyTypeface,
                spannableString.indexOf(it),
                spannableString.indexOf(it) + it.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )


        view.setText(spannableString, TextView.BufferType.SPANNABLE)
    }


    @BindingAdapter("app:styleBold", "app:font")
    @JvmStatic
    fun styleRadioButtonText(view: RadioButton, bold: String?, font: String) {
        try {
            val spannableString = SpannableString(view.text.toString())
            val typeface = TypefaceUtils.load(
                view.context.assets,
                font
            )

            val calligraphyTypeface = CalligraphyTypefaceSpan(
                typeface
            )

            if (bold.isNullOrEmpty()) return
            spannableString.setSpan(
                calligraphyTypeface,
                spannableString.indexOf(bold),
                spannableString.indexOf(bold) + bold.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            view.setText(spannableString, TextView.BufferType.SPANNABLE)
 catch (ex: Exception) {
            ex.printStackTrace()

    }

    @BindingAdapter("app:percentageRate")
    @JvmStatic
    fun setPercentage(view: TextView, percent: String?) {
        val percentageRate = percent.toBigDecimalOrDefaultZero()
        if (percentageRate > (-0.1).toBigDecimal()) {
            view.visibility = View.GONE
            return

        view.visibility = View.VISIBLE
        val drawable = when {
            percentageRate > BigDecimal.ZERO -> R.drawable.ic_arrow_up
            percentageRate < BigDecimal.ZERO -> R.drawable.ic_arrow_down
            else -> 0


        val color = when {
            percentageRate > BigDecimal.ZERO -> R.color.token_change24h_up
            percentageRate < BigDecimal.ZERO -> R.color.token_change24h_down
            else -> R.color.token_change24h_same


        view.setTextColor(ContextCompat.getColor(view.context, color))

        view.text =
            String.format(view.context.getString(R.string.percentage_format), percentageRate)
        view.setCompoundDrawablesWithIntrinsicBounds(0, 0, drawable, 0)
    }
}