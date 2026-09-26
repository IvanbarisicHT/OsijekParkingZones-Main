package hr.ibarisic.osijekparking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import hr.ibarisic.osijekparking.ui.MainScreen
import hr.ibarisic.osijekparking.ui.MainViewModel
import hr.ibarisic.osijekparking.ui.theme.OsijekParkingTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OsijekParkingTheme {
                MainScreen(viewModel)
            }
        }
    }
}
