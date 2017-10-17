package io.sweers.psync

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.builder.core.DefaultApiVersion
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.lang.reflect.Modifier.isFinal
import java.lang.reflect.Modifier.isPublic
import java.lang.reflect.Modifier.isStatic

private val WORKING_DIR = System.getProperty("user.dir")
private val PATH_PREFIX = if (WORKING_DIR.endsWith("psync")) WORKING_DIR else "$WORKING_DIR/psync"
private val FIXTURE_WORKING_DIR = "$PATH_PREFIX/src/test/fixtures/android_app"

fun evaluatableAppProject(): Project {
  val project = ProjectBuilder.builder().withProjectDir(File(FIXTURE_WORKING_DIR)).build()
  project.apply(mapOf(Pair("plugin", "com.android.application")))
  project.extensions.create("android", AppExtension::class.java).run {
    compileSdkVersion = "23"
    buildToolsVersion = "23.0.0"

    defaultConfig { flavor ->
      flavor.run {
        versionCode = 1
        versionName = "1.0"
        minSdkVersion = DefaultApiVersion.create(14)
        targetSdkVersion = DefaultApiVersion.create(26)
        applicationId = "io.sweers.psync.test"
      }
    }

    buildTypes.findByName("release")!!.signingConfig = signingConfigs.findByName("debug")
  }

  return project
}

fun evaluatableLibProject(): Project {
  val project = ProjectBuilder.builder().withProjectDir(File(FIXTURE_WORKING_DIR)).build()
  project.apply(mapOf(Pair("plugin", "com.android.library")))
  project.extensions.create("android", LibraryExtension::class.java).run {
    compileSdkVersion = "23"
    buildToolsVersion = "23.0.0"

    defaultConfig { flavor ->
      flavor.run {
        versionCode = 1
        versionName = "1.0"
        minSdkVersion = DefaultApiVersion.create(14)
        targetSdkVersion = DefaultApiVersion.create(26)
      }
    }

    buildTypes.findByName("release")!!.signingConfig = signingConfigs.findByName("debug")
  }

  return project
}

fun getTaskInputs(): IncrementalTaskInputs {
  return object : IncrementalTaskInputs {
    override fun outOfDate(p0: Action<in InputFileDetails>?) {

    }

    override fun isIncremental() = false

    override fun removed(p0: Action<in InputFileDetails>?) {

    }
  }
}

fun isPSF(modifier: Int): Boolean {
  return isPublic(modifier) && isStatic(modifier) && isFinal(modifier)
}
