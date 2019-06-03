/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.resultOrNull
import kotlin.script.experimental.jvm.compat.mapToLegacyReports

// TODO: rename and provide alias for compatibility - this is not only about dependencies anymore
abstract class ScriptDependenciesLoader(protected val project: Project) {

    abstract fun isApplicable(file: VirtualFile): Boolean
    abstract fun loadDependencies(file: VirtualFile)

    protected abstract fun shouldShowNotification(): Boolean

    protected var shouldNotifyRootsChanged = false

    protected val cache: ScriptsCompilationConfigurationCache = ServiceManager.getService(project, ScriptsCompilationConfigurationCache::class.java)

    private val reporter: ScriptReportSink = ServiceManager.getService(project, ScriptReportSink::class.java)

    protected fun processRefinedConfiguration(result: ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>, file: VirtualFile) {
        debug(file) { "refined script compilation configuration from ${this.javaClass} received = $result" }

        val compilationConfiguration = result.resultOrNull()

        val oldResult = cache[file]

        if (oldResult == null) {
            compilationConfiguration?.let {
                save(it, file)
            }
            attachReportsIfChanged(result, file)
            return
        }

        if (oldResult != compilationConfiguration) {
            if (shouldShowNotification() && !ApplicationManager.getApplication().isUnitTestMode) {
                debug(file) {
                    "dependencies changed, notification is shown: old = $oldResult, new = $compilationConfiguration"
                }
                file.addScriptDependenciesNotificationPanel(compilationConfiguration, project) {
                    save(it, file)
                    attachReportsIfChanged(result, file)
                    submitMakeRootsChange()
                }
            } else {
                debug(file) {
                    "dependencies changed, new dependencies are applied automatically: old = $oldResult, new = $compilationConfiguration"
                }
                save(compilationConfiguration, file)
                attachReportsIfChanged(result, file)
            }
        } else {
            attachReportsIfChanged(result, file)

            if (shouldShowNotification()) {
                file.removeScriptDependenciesNotificationPanel(project)
            }
        }
    }

    private fun attachReportsIfChanged(result: ResultWithDiagnostics<*>, file: VirtualFile) {
        if (file.getUserData(IdeScriptReportSink.Reports) != result.reports.takeIf { it.isNotEmpty() }) {
            reporter.attachReports(file, result.reports.mapToLegacyReports())
        }
    }

    private fun save(compilationConfiguration: ScriptCompilationConfigurationWrapper?, file: VirtualFile) {
        if (shouldShowNotification()) {
            file.removeScriptDependenciesNotificationPanel(project)
        }
        if (compilationConfiguration != null) {
            saveToCache(file, compilationConfiguration)
        }
    }

    protected fun saveToCache(
        file: VirtualFile, compilationConfiguration: ScriptCompilationConfigurationWrapper, skipSaveToAttributes: Boolean = false
    ) {
        val rootsChanged = cache.hasNotCachedRoots(compilationConfiguration)
        if (cache.save(file, compilationConfiguration) && !skipSaveToAttributes) {
            debug(file) {
                "refined configuration is saved to file attributes: $compilationConfiguration"
            }
            if (compilationConfiguration is ScriptCompilationConfigurationWrapper.FromLegacy)
                file.scriptDependencies = compilationConfiguration.legacyDependencies
            else
                file.scriptCompilationConfiguration = compilationConfiguration.configuration
        }

        if (rootsChanged) {
            shouldNotifyRootsChanged = true
        }
    }


    open fun notifyRootsChanged(): Boolean = submitMakeRootsChange()

    protected fun submitMakeRootsChange(): Boolean {
        if (!shouldNotifyRootsChanged) return false

        val doNotifyRootsChanged = Runnable {
            runWriteAction {
                if (project.isDisposed) return@runWriteAction

                debug(null) { "root change event for ${this.javaClass}" }

                shouldNotifyRootsChanged = false
                ProjectRootManagerEx.getInstanceEx(project)?.makeRootsChange(EmptyRunnable.getInstance(), false, true)
                ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
            }
        }

        if (ApplicationManager.getApplication().isUnitTestMode) {
            TransactionGuard.submitTransaction(project, doNotifyRootsChanged)
        } else {
            TransactionGuard.getInstance().submitTransactionLater(project, doNotifyRootsChanged)
        }

        return true
    }

    companion object {
        private val LOG = Logger.getInstance("#org.jetbrains.kotlin.idea.script")

        internal fun debug(file: VirtualFile? = null, message: () -> String) {
            if (LOG.isDebugEnabled) {
                LOG.debug("[KOTLIN SCRIPT] " + (file?.let { "file = ${file.path}, " } ?: "") + message())
            }
        }
    }
}
