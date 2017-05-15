package io.sweers.psync

import groovy.util.Node
import groovy.util.XmlParser
import groovy.xml.QName
import io.reactivex.Observable
import io.reactivex.Single
import org.apache.commons.lang3.math.NumberUtils
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File
import kotlin.properties.Delegates


/**
 * Task that generates P.java source files
 */
class PSyncTask : SourceTask() {

  companion object {
    val BOOL_TYPES = listOf("true", "false")
  }

  /**
   * The output directory.
   */
  @OutputDirectory
  var outputDir: File = File("placeholder")

  @Input
  var packageName: String? = null

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

    PClassGenerator.generate(entries, packageName, outputDir, className, generateRx)
  }

  /**
   * Retrieves all the keys in the files in a given xml directory
   *
   * @param xmlDir Directory to search
   * @param fileRegex Regex for matching the files you want
   * @return Observable of all the distinct keys in this directory.
   */
  fun getPrefEntriesFromFiles(sources: Iterable<File>): Single<List<PrefEntry<*>>> {
    return Observable.fromIterable(
        sources)                                                // Fetch the keys from each file
        .map { file -> XmlParser().parse(file) }                      // Parse the file
        .flatMap { rootNode ->
          Observable.fromIterable(rootNode.depthFirst())
        }   // Extract all the nodes
        .cast(Node::class.java)
        .map { node ->
          generatePrefEntry(node.attributes() as Map<QName, String>)
        }                                                             // Generate PrefEntry objects from the attributes
        .filter { entry -> !entry.isBlank }                           // Filter out ones we can't use
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
    var key: String by Delegates.notNull<String>()
    var defaultValue: String = ""

    // These are present for list-type preferences
    var entries: String? = null
    var entryValues: String? = null

    attributes.entries.forEach { attribute ->
      val name = attribute.key.localPart
      when (name) {
        "key" -> key = attribute.value
        "defaultValue" -> defaultValue = attribute.value
        "entries" -> entries = attribute.value
        "entryValues" -> entryValues = attribute.value
      }
    }

    if (key.isNullOrEmpty()) {
      return PrefEntry.BLANK
    }

    val hasListAttributes = !entries.isNullOrEmpty() || !entryValues.isNullOrEmpty()

    if (defaultValue.isEmpty()) {
      entry = PrefEntry.create(key, null)
    } else if (BOOL_TYPES.contains(defaultValue)) {
      entry = PrefEntry.create(key, defaultValue.toBoolean())
    } else if (NumberUtils.isNumber(defaultValue)) {
      entry = PrefEntry.create(key, Integer.valueOf(defaultValue))
    } else if (defaultValue.startsWith('@')) {
      entry = generateResourcePrefEntry(key, defaultValue)
      if (hasListAttributes && entry.resType == "string") {
        // Only string resource entries can be list preferences
        entry.markAsListPreference(entries, entryValues)
      }
    } else {
      entry = PrefEntry.create(key, defaultValue)
      if (hasListAttributes) {
        entry.markAsListPreference(entries, entryValues)
      }
    }

    return entry
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
    return PrefEntry.create(key, resId, resType)
  }
}

