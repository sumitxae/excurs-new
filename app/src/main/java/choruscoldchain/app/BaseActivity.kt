package choruscoldchain.app

import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

open class BaseActivity:AppCompatActivity() {


    protected open fun setToolbar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    protected open fun setToolbarReturnFun(defaultToolbar: Toolbar, function: () -> Unit, @DrawableRes iconId: Int? = null) {
//        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowTitleEnabled(false)
            if (iconId != null) {
                it.setHomeAsUpIndicator(iconId)
            }
            defaultToolbar?.setNavigationOnClickListener { function() }
        }
    }
}