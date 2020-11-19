package inspections.method

import com.intellij.codeInspection.InspectionToolProvider
import downloader.Downloader.checkArchive

class MethodNamesProvider : InspectionToolProvider {
    init {
        checkArchive()
    }

    override fun getInspectionClasses(): Array<Class<MethodNamesInspection>> {
        return arrayOf(MethodNamesInspection::class.java)
    }

}