package hr.ibarisic.osijekparking.data

import android.content.Context
import android.util.Log
import hr.ibarisic.osijekparking.domain.ParkingDataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Where raw parking data comes from. Swap in a remote implementation to update data without a release. */
interface ParkingDataSource {
    suspend fun loadGeoJson(): String
    suspend fun loadMetadata(): String?
}

/** Reads the dataset bundled in `assets/parking/`. Replace those files to update zones. */
class AssetParkingDataSource(
    private val context: Context,
    private val geoJsonPath: String = "parking/osijek_parking_segments.geojson",
    private val metadataPath: String = "parking/osijek_parking_data.json",
) : ParkingDataSource {
    override suspend fun loadGeoJson(): String = read(geoJsonPath)!!
    override suspend fun loadMetadata(): String? = read(metadataPath)

    private fun read(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()
}

class ParkingRepository(
    private val source: ParkingDataSource,
    private val parser: ParkingDataParser = ParkingDataParser(),
) {
    private val mutex = Mutex()
    private var cached: ParkingDataset? = null

    suspend fun dataset(): ParkingDataset = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            parser.parse(source.loadGeoJson(), source.loadMetadata())
        }.also { ds ->
            ds.importIssues.forEach { Log.w(TAG, it) }
            cached = ds
        }
    }

    private companion object { const val TAG = "ParkingRepository" }
}
