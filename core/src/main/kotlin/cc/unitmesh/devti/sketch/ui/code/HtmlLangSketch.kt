package cc.unitmesh.devti.sketch.ui.code

import cc.unitmesh.devti.sketch.ui.ExtensionLangSketch
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.project.Project

class HtmlLangSketch(override val project: Project, override val text: String) : CodeHighlightSketch(
    project, text, HTMLLanguage.INSTANCE
), ExtensionLangSketch {
    override fun getExtensionName(): String {
        return "HTML"
    }
}