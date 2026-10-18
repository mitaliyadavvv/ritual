package app.ritualhabits

import android.app.Application
import app.ritualhabits.di.AppContainer

class RitualApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.habitDay.install()
        container.focusNotifier.createChannel()
    }
}
