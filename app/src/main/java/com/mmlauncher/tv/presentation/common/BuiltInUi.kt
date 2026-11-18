package com.mmlauncher.tv.presentation.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mmlauncher.tv.R
import com.mmlauncher.tv.domain.model.AppInfo
import com.mmlauncher.tv.domain.model.BuiltInApp

/** Label and icon of the launcher's own cards (the domain only knows which card it is). */
class BuiltInUi(@StringRes val label: Int, @DrawableRes val icon: Int) {
    companion object {
        fun of(app: AppInfo): BuiltInUi? = when (app.builtIn) {
            BuiltInApp.FILES -> BuiltInUi(R.string.src_files, R.drawable.ic_files_app)
            null -> null
        }
    }
}
