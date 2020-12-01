package inspections.variable

import com.intellij.codeInspection.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiVariable
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.id.names.suggesting.IdNamesSuggestingService
import org.jetbrains.id.names.suggesting.ModifiedMemberInplaceRenamer

class VariableNamesInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return VariableVisitor(holder)
    }

    class VariableVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
        private val probabilityCutoff: Double = 0.001

        override fun visitVariable(variable: PsiVariable?) {
            when {
                variable == null -> return
//                ProbabilitiesStorage.isIgnored(variable) -> return
//                caretInsideVariable(variable) -> recalculateLater(variable)
                else -> {
                    // TODO: It might be removed but i think it would be better to store probabilities for each file and save only last 10 files, for example. For {@link inspections.method.MethodNamesInspection} it might be too slow to evaluate on every method every time.
//                    if (!ProbabilitiesStorage.contains(variable) || ProbabilitiesStorage.needRecalculate(variable)) {
//                        val prob = IdNamesSuggestingService.getInstance(holder.project).getVariableNameProbability(variable)
//                        ProbabilitiesStorage.put(variable, Probability(prob));
//                    }
//                    val probability = ProbabilitiesStorage.getProbability(variable)
//                    if (probability == null || probability.prob < probabilityCutoff) {
//                        holder.registerProblem(variable.nameIdentifier ?: variable,
//                                "There are suggestions for variable name",
//                                ProblemHighlightType.WEAK_WARNING,
//                                RenameMethodQuickFix(variable.createSmartPointer()))
//                    }
                    val probability = IdNamesSuggestingService.getInstance(holder.project).getVariableNameProbability(variable)
                    if (probability < probabilityCutoff) {
                        holder.registerProblem(variable.nameIdentifier ?: variable,
                                "There are suggestions for variable name",
                                ProblemHighlightType.WEAK_WARNING,
                                RenameMethodQuickFix(variable.createSmartPointer()))
                    }
                    super.visitVariable(variable)
                }
            }
        }
    }

    class RenameMethodQuickFix(private var variable: SmartPsiElementPointer<PsiVariable>) : LocalQuickFix {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
            val inplaceRefactoring = ModifiedMemberInplaceRenamer(variable.element!!, null, editor)
            inplaceRefactoring.performInplaceRefactoring(IdNamesSuggestingService.getInstance(project)
                    .suggestVariableName(variable.element!!))
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