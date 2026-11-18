package com.mmlauncher.tv.data.diagnostics

import com.mmlauncher.tv.domain.model.AdbFailure

/** Maps the last [LocalAdb] error to the domain's failure reason. */
fun LocalAdb.failure(): AdbFailure = when (lastError) {
    "refused" -> AdbFailure.NOT_LISTENING
    "denied" -> AdbFailure.WAITING_FOR_APPROVAL
    else -> AdbFailure.UNEXPECTED
}
