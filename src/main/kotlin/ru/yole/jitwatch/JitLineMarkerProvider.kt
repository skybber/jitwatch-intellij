package ru.yole.jitwatch

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.adoptopenjdk.jitwatch.model.IMetaMember
import ru.yole.jitwatch.languages.DefaultJitLanguageSupport
import ru.yole.jitwatch.languages.LanguageSupport
import ru.yole.jitwatch.languages.forElement

class JitLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val languageSupport = LanguageSupport.forElement(element)
        if (languageSupport == DefaultJitLanguageSupport) return null

        if (!languageSupport.isMethod(element)) return null
        val modelService = JitWatchModelService.getInstance(element.project)
        if (modelService.model == null) return null
        val metaMember = modelService.getMetaMember(element) ?: return notCompiledMarker(element)
        return if (metaMember.isCompiled) metaMemberMarker(element, metaMember) else notCompiledMarker(element)
    }

    private fun notCompiledMarker(element: PsiElement): LineMarkerInfo<*> {
        return LineMarkerInfo(
            element,
            LanguageSupport.forElement(element).getNameRange(element),
            AllIcons.Actions.Suspend,
            { method -> "Not compiled" },
            null,
            GutterIconRenderer.Alignment.CENTER,
            { "Not compiled accessible name" }
        )
    }

    private fun metaMemberMarker(method: PsiElement, metaMember: IMetaMember): LineMarkerInfo<*> {
        val decompiles = metaMember.compiledAttributes["decompiles"] ?: "0"
        val icon = if (decompiles.toInt() > 0) AllIcons.Actions.ForceRefresh else AllIcons.Actions.Compile
        return LineMarkerInfo(
            method,
            LanguageSupport.forElement(method).getNameRange(method),
            icon,
            { method -> buildCompiledTooltip(metaMember) },
            { e, elt ->
                JitToolWindow.getToolWindow(elt.project)?.activate {
                    JitToolWindow.getInstance(elt.project)?.navigateToMember(elt)
                }
            },
            GutterIconRenderer.Alignment.CENTER,
            {
                val elementName = if (method is PsiNamedElement) method.name else method.text
                "Compiled element: $elementName"
            }
        )
    }

    private fun buildCompiledTooltip(metaMember: IMetaMember): String {
        val compiler = metaMember.compiledAttributes["compiler"] ?: "?"
        val compileMillis = metaMember.compiledAttributes["compileMillis"] ?: "?"
        val bytecodeSize = metaMember.compiledAttributes["bytes"] ?: "?"
        val nativeSize = metaMember.compiledAttributes["nmsize"] ?: "?"
        val decompiles = metaMember.compiledAttributes["decompiles"]
        var message = "Compiled with $compiler in $compileMillis ms, bytecode size $bytecodeSize, native size $nativeSize"
        if (decompiles != null) {
            message += ". Decompiled $decompiles times"
        }
        return message
    }


    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        super.collectSlowLineMarkers(elements, result)
    }
}
