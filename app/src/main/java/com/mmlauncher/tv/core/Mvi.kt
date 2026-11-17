package com.mmlauncher.tv.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Where work runs. Production uses Android threads; unit tests use [ImmediateScheduler]. */
interface Scheduler {
    /** Runs [block] on the main thread (immediately if already there). */
    fun main(block: () -> Unit)
    fun io(block: () -> Unit)
    fun network(block: () -> Unit)
    /** Runs [block] on the main thread after [delayMs]; the returned function cancels it. */
    fun delayed(delayMs: Long, block: () -> Unit): () -> Unit
}

class AndroidScheduler : Scheduler {
    private val handler = Handler(Looper.getMainLooper())
    // Local work runs in a small pool so one slow task cannot stall other screens; slow I/O
    // (network, local ADB that may wait for user approval) has its own thread.
    private val io = Executors.newFixedThreadPool(3)
    private val net = Executors.newSingleThreadExecutor()

    override fun main(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }
    override fun io(block: () -> Unit) = io.execute(block)
    override fun network(block: () -> Unit) = net.execute(block)
    override fun delayed(delayMs: Long, block: () -> Unit): () -> Unit {
        val r = Runnable(block)
        handler.postDelayed(r, delayMs)
        return { handler.removeCallbacks(r) }
    }
}

/** Everything synchronous; delayed work is queued until [runDelayed] so tests control time. */
class ImmediateScheduler : Scheduler {
    private val queue = ArrayList<Pair<Long, () -> Unit>>()
    override fun main(block: () -> Unit) = block()
    override fun io(block: () -> Unit) = block()
    override fun network(block: () -> Unit) = block()
    override fun delayed(delayMs: Long, block: () -> Unit): () -> Unit {
        val entry = delayMs to block
        queue += entry
        return { queue.remove(entry) }
    }
    /** Runs everything currently scheduled with [delayed] once. */
    fun runDelayed() { val due = queue.toList(); queue.clear(); due.forEach { it.second() } }
}

/**
 * MVI store: the View sends [dispatch] intents, the store reduces them into one immutable state
 * ([state]) and sends one-shot effects (toasts, navigation) that must not be replayed on rotation.
 * All state changes happen on the main thread.
 */
abstract class MviStore<S : Any, I : Any, E : Any>(initial: S, protected val scheduler: Scheduler) {

    var state: S = initial
        private set

    private var stateObserver: ((S) -> Unit)? = null
    private var effectObserver: ((E) -> Unit)? = null
    private val pendingEffects = ArrayDeque<E>()
    private var cleared = false

    fun dispatch(intent: I) = scheduler.main { if (!cleared) handle(intent) }

    protected abstract fun handle(intent: I)

    protected fun setState(reduce: S.() -> S) = scheduler.main {
        if (cleared) return@main
        state = state.reduce()
        stateObserver?.invoke(state)
    }

    protected fun emit(effect: E) = scheduler.main {
        if (cleared) return@main
        val o = effectObserver
        if (o != null) o(effect) else pendingEffects.addLast(effect)
    }

    /** Runs [work] off the main thread and delivers its result on the main thread. */
    protected fun <T> io(work: () -> T, onResult: (T) -> Unit) = scheduler.io {
        val r = work()
        scheduler.main { if (!cleared) onResult(r) }
    }

    protected fun <T> network(work: () -> T, onResult: (T) -> Unit) = scheduler.network {
        val r = work()
        scheduler.main { if (!cleared) onResult(r) }
    }

    protected fun delayed(delayMs: Long, block: () -> Unit): () -> Unit =
        scheduler.delayed(delayMs) { if (!cleared) block() }

    /** Called by the View when it becomes visible; gets the current state and any queued effects. */
    fun bind(onState: (S) -> Unit, onEffect: (E) -> Unit) {
        stateObserver = onState
        effectObserver = onEffect
        onState(state)
        while (pendingEffects.isNotEmpty()) onEffect(pendingEffects.removeFirst())
    }

    fun unbind() { stateObserver = null; effectObserver = null }

    open fun clear() { cleared = true; unbind() }
}
