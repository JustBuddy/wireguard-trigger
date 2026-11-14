package wtf.buddyworks.wireguardtrigger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import wtf.buddyworks.wireguardtrigger.ui.AppRoot
import wtf.buddyworks.wireguardtrigger.ui.theme.TriggerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TriggerTheme {
                AppRoot()
            }
        }
    }
}
