package org.vsu.pt.team2.utilitatemmetrisapp.ui.components

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import org.vsu.pt.team2.utilitatemmetrisapp.R
import org.vsu.pt.team2.utilitatemmetrisapp.databinding.BigGeneralButtonBinding

class BigGeneralButton @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyle: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attributeSet, defStyle, defStyleRes) {
    var button: Button
    var loading_iv: ImageView
    lateinit var buttonText: String
    var loadingAnimation: Animation

    init {
        val binding = BigGeneralButtonBinding.inflate(
            LayoutInflater.from(context),
            this,
            true
        )
        loading_iv = binding.bigGeneralButtonImageView
        button = binding.bigGeneralButtonButton

        attributeSet?.let {
            initComponents(
                context.obtainStyledAttributes(
                    it,
                    R.styleable.BigGeneralButton, 0, 0
                )
            )
        }
        loadingAnimation = AnimationUtils.loadAnimation(
            context,
            R.anim.rotate_center
        )
        loadingAnimation.interpolator = LinearInterpolator()

        setStateDefault()
    }

    private fun initComponents(typedArray: TypedArray) {
        val textBtn = resources.getText(
            typedArray.getResourceId(
                R.styleable.BigGeneralButton_button_text,
                R.string.big_general_button_default_text
            )
        )
        val img_src = resources.getDrawable(
            typedArray.getResourceId(
                R.styleable.BigGeneralButton_src_image_loader,
                R.drawable.loading
            ),
//            resources.newTheme()
        )
        buttonText = textBtn.toString()
//        button.text = buttonText
        loading_iv.setImageDrawable(img_src)

        typedArray.recycle()
    }

    override fun setOnClickListener(l: OnClickListener?) {
        button.setOnClickListener(l)
    }

    override fun hasOnClickListeners(): Boolean {
        return button.hasOnClickListeners()
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        button.setOnLongClickListener(l)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return if (button.isActivated) button.performClick() else false
    }

    fun setStateLoading() {
        button.isActivated = false
        button.isEnabled = false
        button.visibility = View.VISIBLE
        button.text = ""

        loading_iv.visibility = View.VISIBLE
        loading_iv.startAnimation(loadingAnimation)

    }

    fun setStateDefault() {
        button.isEnabled = true
        button.isActivated = true
        button.visibility = View.VISIBLE
        button.text = buttonText

        loading_iv.visibility = View.INVISIBLE
        loading_iv.clearAnimation()
    }
}
