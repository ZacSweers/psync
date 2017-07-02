package io.sweers.psync

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.preference.PreferenceManager
import android.support.annotation.Nullable
import com.google.common.base.CaseFormat
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import groovy.util.Node
import groovy.util.XmlParser
import groovy.xml.QName
import io.reactivex.Observable
import io.reactivex.Single
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File
import java.io.IOException
import java.util.regex.Pattern
import javax.lang.model.element.Modifier
import kotlin.properties.Delegates


/**
 * Task that generates P.java source files
 */
open class PSyncTask : SourceTask() {

  companion object {
    val BOOL_TYPES = listOf("true", "false")
  }

  /**
   * The output directory.
   */
  @OutputDirectory
  lateinit var outputDir: File

  @Input
  lateinit var packageName: String

  @Input
  var className: String = "P"

  @Input
  var generateRx: Boolean = false

  @TaskAction
  fun generate(inputs: IncrementalTaskInputs) {

    // If the whole thing isn't incremental, delete the build folder (if it exists)
    // TODO If they change the className, we should probably delete the old one for good measure if it exists
    if (!inputs.isIncremental && outputDir.exists()) {
      logger.debug("PSync generation is not incremental; deleting build folder and starting fresh!")
      outputDir.delete()
    }

    if (!outputDir.exists()) {
      outputDir.mkdirs()
    }

    val entries = getPrefEntriesFromFiles(getSource()).blockingGet()

    generate(entries, packageName, outputDir, className, generateRx)
  }

  /**
   * Retrieves all the keys in the files in a given xml directory
   *
   * @return Observable of all the distinct keys in this directory.
   */
  fun getPrefEntriesFromFiles(sources: Iterable<File>): Single<List<PrefEntry<*>>> {
    return Observable.fromIterable(sources)               // Fetch the keys from each file
        .map { file -> XmlParser().parse(file) }          // Parse the file
        .flatMap { rootNode ->
          Observable.fromIterable(rootNode.depthFirst()) // Extract all the nodes
        }
        .cast(Node::class.java)
        .map { node ->
          @Suppress("UNCHECKED_CAST")
          generatePrefEntry(node.attributes() as Map<QName, String>)
        }                                                             // Generate PrefEntry objects from the attributes
        .filter { !it.isBlank }                           // Filter out ones we can't use
        .distinct()                                                     // Only want unique
        .toSortedList()                                                 // Output the sorted list
  }

  /**
   * Generates a {@link PrefEntry} from the given attributes on a Node
   *
   * @param attributes attributes on the node to parse
   * @return a generated PrefEntry, or {@link PrefEntry#BLANK} if we can't do anything with it
   */
  fun generatePrefEntry(attributes: Map<QName, String>): PrefEntry<*> {
    var entry: PrefEntry<*> by Delegates.notNull<PrefEntry<*>>()
    var key: String? = null
    var defaultValue: String = ""

    // These are present for list-type preferences
    var entries: String? = null
    var entryValues: String? = null

    attributes.entries.forEach { (qname, value) ->
      val name = qname.localPart
      when (name) {
        "key" -> key = value
        "defaultValue" -> defaultValue = value
        "entries" -> entries = value
        "entryValues" -> entryValues = value
      }
    }

    if (key.isNullOrBlank()) {
      return PrefEntry.BLANK
    }

    key?.let { nonNullKey ->
      val hasListAttributes = !entries.isNullOrEmpty() || !entryValues.isNullOrEmpty()

      if (defaultValue.isEmpty()) {
        entry = PrefEntry<Any>(nonNullKey)
      } else if (BOOL_TYPES.contains(defaultValue)) {
        entry = PrefEntry(nonNullKey, defaultValue.toBoolean())
      } else if (defaultValue.toIntOrNull() != null) {
        entry = PrefEntry(nonNullKey, Integer.valueOf(defaultValue))
      } else if (defaultValue.startsWith('@')) {
        entry = generateResourcePrefEntry(nonNullKey, defaultValue)
        if (hasListAttributes && entry.resType == "string") {
          // Only string resource entries can be list preferences
          entry.markAsListPreference(entries!!, entryValues!!)
        }
      } else {
        entry = PrefEntry(nonNullKey, defaultValue).apply {
          if (hasListAttributes) {
            markAsListPreference(entries!!, entryValues!!)
          }
        }
      }
      return entry
    }

    return PrefEntry.BLANK
  }

  /**
   * Resource PrefEntries are special, because we need to retrieve their resource ID.
   *
   * @param key Preference key
   * @param defaultValue String representation of the default value (e.g. "@string/hello")
   * @return PrefEntry object representing this, or {@link PrefEntry#BLANK} if we couldn't resolve its resource ID
   */
  fun generateResourcePrefEntry(key: String, defaultValue: String): PrefEntry<*> {
    val split = defaultValue.split('/')

    if (split.size < 2) {
      return PrefEntry.BLANK
    }

    val resType = split[0].substring(1)
    val resId = split[1]
    return PrefEntry(key, resId, resType)
  }

  private val CN_RX_PREFERENCES = ClassName.get("com.f2prateek.rx.preferences2",
      "RxSharedPreferences")
  private val CN_RX_PREFERENCE = ClassName.get("com.f2prateek.rx.preferences2", "Preference")
  private val MODIFIERS = arrayOf(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
  private val COULD_BE_CAMEL = Pattern.compile("[a-zA-Z]+[a-zA-Z0-9]*")
  private val ALL_CAPS = Pattern.compile("[A-Z0-9]*")

  /**
   * Groovy can't talk to Java vararg methods, such as JavaPoet's many vararg methods. Utility
   * class is here so we can use JavaPoet nicely.
   *
   * @param inputKeys List of the preference keys to generate for
   * @param packageName Package name to create the P class in
   * @param outputDir Output directory to create the P.java file in
   * @param className Name to use for the generated class
   * @param generateRx Boolean indicating whether or not to generate Rx-Preferences support code
   * @throws IOException because Java
   */
  @Throws(IOException::class)
  fun generate(inputKeys: List<PrefEntry<*>>,
      packageName: String,
      outputDir: File,
      className: String,
      generateRx: Boolean) {
    val pClass = TypeSpec.classBuilder(className)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)

    setUpContextAndPreferences(pClass, generateRx)

    pClass.addMethod(MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PRIVATE)
        .addStatement("throw new \$T(\$S)", AssertionError::class.java, "No instances.")
        .build())

    for (entry in inputKeys) {
      pClass.addType(generatePrefBlock(entry, packageName, generateRx))
    }

    val javaFile = JavaFile.builder(packageName, pClass.build())
        .build()
    javaFile.writeTo(outputDir)
  }

  private fun setUpContextAndPreferences(pClass: TypeSpec.Builder, generateRx: Boolean) {
    pClass.addField(FieldSpec.builder(
        Resources::class.java,
        "RESOURCES",
        Modifier.PRIVATE,
        Modifier.STATIC)
        .initializer("null")
        .build())

    pClass.addField(FieldSpec.builder(
        SharedPreferences::class.java,
        "PREFERENCES",
        Modifier.PRIVATE,
        Modifier.STATIC)
        .initializer("null")
        .build())

    if (generateRx) {
      pClass.addField(FieldSpec.builder(
          CN_RX_PREFERENCES,
          "RX_PREFERENCES",
          Modifier.PRIVATE,
          Modifier.STATIC)
          .initializer("null")
          .build())
    }

    pClass.addMethod(MethodSpec.methodBuilder("init")
        .addModifiers(*MODIFIERS)
        .addJavadoc(
            "Initializer that takes a {@link Context} for resource resolution. This should be an Application context instance, and will retrieve default shared preferences.\n")
        .addParameter(
            ParameterSpec.builder(Context::class.java, "context", Modifier.FINAL)
                .build())
        .beginControlFlow("if (context == null)")
        .addStatement("throw new \$T(\$S)",
            IllegalStateException::class.java,
            "context cannot be null!")
        .endControlFlow()
        .addStatement("\$T applicationContext = context.getApplicationContext()", Context::class.java)
        .addStatement("RESOURCES = applicationContext.getResources()")
        .addStatement("// Sensible default")
        .addStatement(
            "setSharedPreferences(\$T.getDefaultSharedPreferences(applicationContext))",
            PreferenceManager::class.java)
        .build())

    val setSharedPreferencesBuilder = MethodSpec.methodBuilder("setSharedPreferences")
        .addModifiers(*MODIFIERS)
        .addParameter(ParameterSpec.builder(SharedPreferences::class.java,
            "sharedPreferences",
            Modifier.FINAL)
            .build())
        .beginControlFlow("if (sharedPreferences == null)")
        .addStatement("throw new \$T(\$S)",
            IllegalStateException::class.java,
            "sharedPreferences cannot be null!")
        .endControlFlow()
        .addStatement("PREFERENCES = sharedPreferences")

    if (generateRx) {
      pClass.addMethod(MethodSpec.methodBuilder("setSharedPreferences")
          .addModifiers(*MODIFIERS)
          .addParameter(ParameterSpec.builder(SharedPreferences::class.java,
              "sharedPreferences",
              Modifier.FINAL)
              .build())
          .addStatement("setSharedPreferences(sharedPreferences, null)")
          .build())
      setSharedPreferencesBuilder
          .addParameter(ParameterSpec.builder(CN_RX_PREFERENCES, "rxPreferences")
              .addAnnotation(Nullable::class.java)
              .build())
          .beginControlFlow("if (rxPreferences == null)")
          .addStatement(
              "RX_PREFERENCES = \$T.create(PREFERENCES)",
              CN_RX_PREFERENCES)
          .nextControlFlow("else")
          .addStatement("RX_PREFERENCES = rxPreferences")
          .endControlFlow()
    }

    pClass.addMethod(setSharedPreferencesBuilder.build())
  }

  private fun generatePrefBlock(entry: PrefEntry<*>,
      packageName: String,
      generateRx: Boolean): TypeSpec {
    val entryClass = TypeSpec.classBuilder(camelCaseKey(entry.key))
        .addModifiers(*MODIFIERS)
    entryClass.addField(FieldSpec.builder(String::class.java, "KEY", *MODIFIERS)
        .initializer("\$S", entry.key)
        .build())

    if (entry.defaultType != null) {
      if (entry.isResource) {
        entryClass.addField(
            FieldSpec.builder(Int::class.javaPrimitiveType!!, "DEFAULT_RES_ID", *MODIFIERS)
                .initializer("\$T.\$N.\$N",
                    ClassName.get(packageName, "R"),
                    entry.resType,
                    entry.defaultValue)
                .build())
        entryClass.addMethod(generateResolveDefaultResMethod(entry))
      } else {
        val isString = entry.defaultType == String::class.java
        entryClass.addMethod(MethodSpec.methodBuilder("defaultValue")
            .addModifiers(*MODIFIERS)
            .returns(entry.defaultType)
            .addStatement(
                "return " + if (isString) "\$S" else "\$N",
                if (isString) entry.defaultValue else entry.defaultValue.toString())
            .build())
      }
    }

    // Add getter
    if (entry.valueType != null || entry.defaultType != null) {
      val prefType = (entry.valueType ?: entry.defaultType)!!
      entryClass.addMethod(MethodSpec.methodBuilder("get")
          .addModifiers(*MODIFIERS)
          .returns(prefType)
          .addStatement("return PREFERENCES.\$N", resolvePreferenceStmt(entry, true))
          .build())

      entryClass.addMethod(MethodSpec.methodBuilder("put")
          .addModifiers(*MODIFIERS)
          .returns(SharedPreferences.Editor::class.java)
          .addParameter(ParameterSpec.builder(prefType, "val", Modifier.FINAL)
              .build())
          .addStatement("return PREFERENCES.edit().\$N", resolvePreferenceStmt(entry, false))
          .build())

      val referenceType = resolveReferenceType(prefType)
      if (generateRx && referenceType != null) {
        entryClass.addMethod(MethodSpec.methodBuilder("rx")
            .addModifiers(*MODIFIERS)
            .returns(ParameterizedTypeName.get(CN_RX_PREFERENCE, TypeName.get(referenceType)))
            .addStatement("return RX_PREFERENCES.get\$N(KEY)", referenceType.simpleName)
            .build())
      }
    }

    if (entry.hasListAttributes) {
      if (entry.entriesGetterStmt != null) {
        entryClass.addMethod(MethodSpec.methodBuilder("entries")
            .addModifiers(*MODIFIERS)
            .returns(Array<CharSequence>::class.java)
            .addStatement("return RESOURCES." + entry.entriesGetterStmt)
            .build())
      }
      if (entry.entryValuesGetterStmt != null) {
        entryClass.addMethod(MethodSpec.methodBuilder("entryValues")
            .addModifiers(*MODIFIERS)
            .returns(Array<CharSequence>::class.java)
            .addStatement("return RESOURCES." + entry.entryValuesGetterStmt)
            .build())
      }
    }

    return entryClass.build()
  }

  internal fun camelCaseKey(input: String): String {

    // Default to lower_underscore, as this is the platform convention
    var format = CaseFormat.LOWER_UNDERSCORE

    val couldBeCamel = COULD_BE_CAMEL.matcher(input)
        .matches()
    if (!couldBeCamel) {
      if (input.contains("-")) {
        format = CaseFormat.LOWER_HYPHEN
      } else if (input.contains("_")) {
        val isAllCaps = ALL_CAPS.matcher(input)
            .matches()
        format = if (isAllCaps) CaseFormat.UPPER_UNDERSCORE else CaseFormat.LOWER_UNDERSCORE
      }
    } else {
      format = if (Character.isUpperCase(
          input[0])) CaseFormat.UPPER_CAMEL else CaseFormat.LOWER_CAMEL
    }

    return if (format === CaseFormat.LOWER_CAMEL) input else format.to(CaseFormat.UPPER_CAMEL,
        input)
  }

  private fun generateResolveDefaultResMethod(entry: PrefEntry<*>): MethodSpec {
    return MethodSpec.methodBuilder("defaultValue")
        .addModifiers(*MODIFIERS)
        .returns(entry.valueType)
        .addCode(CodeBlock.builder()
            .addStatement("return RESOURCES.\$N", entry.resourceDefaultValueGetterStmt)
            .build())
        .build()
  }

  private fun resolveReferenceType(clazz: Class<*>): Class<*>? {
    if (!clazz.isPrimitive) {
      return clazz
    }
    when (clazz.simpleName) {
      "Boolean", "boolean" -> return java.lang.Boolean::class.java
      "Integer", "int" -> return Integer::class.java
      else ->
        // Currently unsupported
        return null
    }
  }

  private fun resolvePreferenceStmt(entry: PrefEntry<*>, isGetter: Boolean): String {
    var defaultValue = "defaultValue()"
    val simpleName = entry.valueType!!.simpleName.capitalize()
    if (entry.defaultType == null) {
      // No defaultValue() method will be available
      when (simpleName) {
        "Boolean" -> defaultValue = "false"
        "Int" -> defaultValue = "-1"
        "String" -> defaultValue = "null"
        else -> defaultValue = "null"
      }
    }

    if (isGetter) {
      return "get$simpleName(KEY, $defaultValue)"
    } else {
      return "put$simpleName(KEY, val)"
    }
  }
}

