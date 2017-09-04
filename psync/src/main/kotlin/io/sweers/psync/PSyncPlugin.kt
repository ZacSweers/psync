package io.sweers.psync

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project

class PSyncPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val psyncModel = project.extensions.create("psync", PSyncModel::class.java)

    project.afterEvaluate {
      project.plugins.forEach {
        when (it) {
          is AppPlugin -> {
            processVariants(project, psyncModel,
                project.extensions.findByType(AppExtension::class.java).applicationVariants)
          }
          is LibraryPlugin -> {
            processVariants(project, psyncModel,
                project.extensions.findByType(LibraryExtension::class.java).libraryVariants)
          }
        }
      }
    }
  }

  private fun processVariants(project: Project, psyncModel: PSyncModel,
      variants: Iterable<BaseVariant>) {
    val includesPattern = psyncModel.includesPattern
    variants.forEach { variant ->
      // Determine the package name
      val resolvedPackageName = psyncModel.packageName ?: variant.applicationId
      val psyncTask = project.tasks.create("generatePrefKeysFor${variant.name.capitalize()}",
          PSyncTask::class.java).apply {
        setSource(variant.sourceSets.map { it.resDirectories })
        include(includesPattern)
        outputDir = project.file(
            "${project.buildDir}/generated/source/psync/${variant.flavorName}/${variant.buildType.name}/")
        packageName = resolvedPackageName
        className = psyncModel.className
        generateRx = psyncModel.generateRx
      }
      variant.registerJavaGeneratingTask(psyncTask, psyncTask.outputDir)
    }
  }
}

