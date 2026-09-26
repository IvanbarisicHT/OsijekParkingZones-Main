# Osijek Parking Zones

Android app showing paid, free and unknown parking in Osijek on an interactive map.
Author: **Ivan Barišić**. UI language: Croatian.

## Map technology: MapLibre Native + OpenStreetMap

| | Google Maps SDK | Mapbox | **MapLibre (chosen)** |
|---|---|---|---|
| GeoJSON overlays | via `maps-utils` GeoJsonLayer (object-per-feature, slower) | native, GPU | native, GPU (same engine lineage as Mapbox) |
| Data-driven colours per feature | manual per feature | expressions | expressions |
| Tap on LineString/Polygon | per-object listeners | `queryRenderedFeatures` | `queryRenderedFeatures` |
| API key / billing | key + billing account | token + usage pricing | **none** (OpenFreeMap tiles) |
| Vendor lock-in | high | high | open source (BSD) |

MapLibre renders the whole dataset as one GPU-styled source. Colours come from a `match` expression on a
`category` property, and planned segments are drawn dashed. Taps hit an invisible 28 px line layer, so thin streets
are easy to tap. The built-in `LocationComponent` draws the standard blue location dot.

- **Tiles:** OpenFreeMap `positron`, a calm grey style that makes the coloured overlays stand out. It needs no key.
  Change `MAP_STYLE_URL` in `app/build.gradle.kts` to use another style.
- **Address search:** OSM Nominatim, limited to the Osijek bounding box. The public server forbids autocomplete, so
  address lookup runs when the user submits the search. Dataset locations are matched instantly as the user types,
  ignoring diacritics, so "sepera" finds "Šepera". For higher traffic, point `NominatimGeocoder.baseUrl` at a
  self-hosted server or a commercial geocoder.

## Architecture

```
data/      ParkingDataSource (assets; swappable for remote) → ParkingDataParser → ParkingRepository
           NominatimGeocoder
domain/    Models, ParkingStatusEvaluator (charging now / next start), CroatianHolidays, GeoUtils, TextMatch
ui/        MainViewModel (StateFlow) → MainScreen (Compose, Material 3)
ui/map/    ParkingMap (MapView wrapper), DisplayGeoJson (dataset → style-ready GeoJSON)
```

- The code contains no street names. All parking content lives in `app/src/main/assets/parking/`.
- `osijek_parking_segments.geojson` is the source of truth for segments. `osijek_parking_data.json` supplies
  `metadata`, `zone_rules`, `sources` and an optional `holidays` array of `YYYY-MM-DD` strings.
- If a segment lacks price, hours or SMS number, the parser uses its zone rule. If both are missing, the value is
  **unknown**. Nothing is ever assumed free.

### Status rules (`ParkingStatusEvaluator`)

| Data | Map | Sheet |
|---|---|---|
| `PAID`, `active_from` ≤ today | zone colour | NAPLATA AKTIVNA / TRENUTNO BESPLATNO + next start |
| `PAID`, `active_from` > today, or `PLANNED` | zone colour, **dashed** | NAPLATA JOŠ NIJE UVEDENA |
| `FREE` | green | BESPLATNO PARKIRANJE |
| `NOT_CONFIRMED`, missing/unknown status, or `currently_active: "NO"` contradicting `PAID` | grey | STATUS NIJE POTVRĐEN |
| Charging hours missing for today | — | STATUS NIJE POTVRĐEN (never "free") |

Sundays and Croatian public holidays use `sunday_holidays`. The holidays are the statutory list since 2020, with
Easter, Easter Monday and Corpus Christi computed each year. All times use `Europe/Zagreb`.

## ⚠ Current data: no geometry yet

All 20 features in the supplied GeoJSON have `"geometry": null` (`geometry_status: NEEDS_GEOCODING`). The app
**does not invent coordinates**, so today:

- the map draws no coloured lines;
- a chip ("20 od 20 lokacija još nije na karti") and the list button open every location with full details;
- searching an address such as "Šepera 4" moves the map there and lists the dataset entries **on that street**,
  matched by street name. For this example that is Zone 0, house numbers 8E–8F only.

### Adding geometry

Put a GeoJSON geometry into each feature. Coordinates are **[longitude, latitude]**. Supported types: `LineString`,
`MultiLineString`, `Polygon`, `MultiPolygon`, `Point`.

```json
"geometry": { "type": "LineString", "coordinates": [[18.6951, 45.5587], [18.6963, 45.5601]] }
```

Also set `"geometry_status": "VERIFIED"` once the geometry is checked. Until then, the sheet says the map position
isn't officially confirmed. On import, geometry outside the Osijek area (for example with swapped lat/lon) is
rejected and logged, and that segment stays unmapped. Tools for drawing segments: [geojson.io](https://geojson.io),
QGIS, or JOSM on OSM street ways, trimmed to `segment_from`/`segment_to`.

### Data notes

- `special_rule` and `notes` are displayed verbatim. Some are in English in the current file, such as the Trg
  Ljudevita Gaja first-free-hour rule. Write them in Croatian for a consistent UI.
- Zone II has no segments yet (`PLANNED`). It appears in the legend and the About sheet only.
- An empty `max_hours_per_day` (zones I and II) is not shown.

## Build

Requirements: Android Studio (AGP 9.4, Kotlin 2.4, JDK 17+), compileSdk 37, minSdk 26.

```
gradlew testDebugUnitTest assembleDebug
```

Unit tests cover the status evaluator, holiday calendar and the parser, including the bundled dataset.

Permissions: `INTERNET` and **foreground** location only (`FINE`/`COARSE`). The app is fully usable without location.
