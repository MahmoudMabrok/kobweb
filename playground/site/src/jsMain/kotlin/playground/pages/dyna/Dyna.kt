package playground.pages.dyna

import androidx.compose.runtime.*
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.forms.TextInput
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import playground.components.layouts.PageLayout

@Page("/docs/{version}/test/index")
@Composable
fun HomePage() {
    PageLayout("DYNAMIC") {
        val ctx = rememberPageContext()
        Text("HI: ${ctx.route.params["version"]}")
    }
}
