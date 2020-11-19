package inspections.variable

import com.intellij.codeInspection.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiVariable
import com.sun.istack.NotNull
import inspections.Suggestion
import inspections.SuggestionsStorage
import inspections.SuggestionsStorage.Companion.recalculateLater
import org.jetbrains.id.names.suggesting.IdNamesSuggestingService
import org.jetbrains.id.names.suggesting.ModifiedMemberInplaceRenamer
import org.jetbrains.id.names.suggesting.contributors.NGramVariableNamesContributor.caretInsideVariable
import org.jetbrains.id.names.suggesting.impl.SuggestVariableNamesIntention
import java.util.*
import kotlin.collections.ArrayList

class VariableNamesInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return VariableVisitor(holder)
    }

    class VariableVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
        override fun visitVariable(variable: PsiVariable?) {
            when {
                variable == null -> return
                SuggestionsStorage.ignore(variable) -> return
                caretInsideVariable(variable) -> recalculateLater(variable)
                else -> {
                    if (!SuggestionsStorage.contains(variable) || SuggestionsStorage.needRecalculate(variable)) {
                        val predictions = IdNamesSuggestingService.getInstance(holder.project).suggestVariableName(variable, false)
                        SuggestionsStorage.put(variable, predictionsToSuggestion(predictions))
                    }
                    val suggestions = SuggestionsStorage.getSuggestions(variable)
                    if (suggestions != null && suggestions.names.isNotEmpty()
                            && !suggestions.containsName(variable.name!!)) {
                        holder.registerProblem(variable.nameIdentifier ?: variable,
                                "There are suggestions for variable name",
                                ProblemHighlightType.WEAK_WARNING,
                                RenameMethodQuickFix(suggestionToPredictions(suggestions)))
                    }
                    super.visitVariable(variable)
                }
            }
        }

        private fun suggestionToPredictions(suggestion: Suggestion): LinkedHashMap<String, Double> {
            return suggestion.names.map { it.first to it.second }.toMap(LinkedHashMap())
        }

        private fun predictionsToSuggestion(@NotNull predictions: LinkedHashMap<String, Double>?): Suggestion {
            return Suggestion(predictions!!
                    .map { entry: Map.Entry<String, Double> -> Pair(entry.key, entry.value) }
                    .toCollection(ArrayList()))
        }
    }

    class RenameMethodQuickFix(private var predictions: LinkedHashMap<String, Double>) : LocalQuickFix {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
            val variable = SuggestVariableNamesIntention().getIdentifierOwner(descriptor.psiElement)

            if (variable !is PsiVariable) return
            val inplaceRefactoring = ModifiedMemberInplaceRenamer(variable, null, editor)
            inplaceRefactoring.performInplaceRefactoring(predictions)
        }


        override fun getFamilyName(): String {
            return "Show variable name suggestions"
        }

    }

    override fun getDisplayName(): String {
        return "Show variable name suggestions"
    }

    override fun getGroupDisplayName(): String {
        return "Plugin Id Names Suggesting"
    }

    override fun getShortName(): String {
        return "VariableNamesInspection"
    }

}