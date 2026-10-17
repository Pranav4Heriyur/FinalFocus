package com.pranav.focus.calls

import android.telecom.Call
import android.telecom.CallScreeningService
import com.pranav.focus.data.CallMatcher
import com.pranav.focus.data.Store

/** During a session (and with "allowed callers only" on) rejects calls from numbers not on the list. */
class FocusCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val store = Store.get(this)
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val number = details.handle?.schemeSpecificPart
        val block = incoming && store.sessionActive && store.callersOnly &&
            !CallMatcher.matches(number, store.callers.filter { it.enabled }.map { it.number })
        if (block) store.addBlockedCall()
        val response = CallResponse.Builder().apply {
            if (block) {
                setDisallowCall(true)
                setRejectCall(true)
                setSkipNotification(false)
            }
        }.build()
        respondToCall(details, response)
    }
}
