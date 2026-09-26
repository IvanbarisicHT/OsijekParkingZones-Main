package hr.ibarisic.osijekparking

import android.app.Application
import hr.ibarisic.osijekparking.data.AssetParkingDataSource
import hr.ibarisic.osijekparking.data.Geocoder
import hr.ibarisic.osijekparking.data.NominatimGeocoder
import hr.ibarisic.osijekparking.data.ParkingRepository
import org.maplibre.android.MapLibre

class OsijekParkingApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        container = AppContainer(this)
    }
}

/** Manual dependency wiring; small enough that a DI framework would add more than it saves. */
class AppContainer(app: Application) {
    val repository = ParkingRepository(AssetParkingDataSource(app))
    val geocoder: Geocoder = NominatimGeocoder(BuildConfig.GEOCODER_USER_AGENT)
}
