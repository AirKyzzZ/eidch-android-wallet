package ch.admin.foitt.wallet.platform.sdkVersion

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

@ChecksSdkIntAtLeast(parameter = 0)
fun isAtLeast(api: Int): Boolean = Build.VERSION.SDK_INT >= api
