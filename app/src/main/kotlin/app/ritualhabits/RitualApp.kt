package app.ritualhabits

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import app.ritualhabits.di.AppContainer

class RitualApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.habitDay.install()
        container.focusNotifier.createChannel()
        container.reminderNotifier.createChannel()
        container.reminders.start(container.appScope)
        container.sync.start()
        // Coming back to the app is a good moment to pick up changes from other devices.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) container.sync.syncNow() },
        )
    }
}
