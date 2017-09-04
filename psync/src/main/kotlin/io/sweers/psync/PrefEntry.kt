package io.sweers.psync

/**
 * This represents a preference entry
 *
 * [T] represents the type of value this preference is backed by, such as a boolean
 */
class PrefEntry<T : Any>(var key: String,
    var defaultValue: T? = null,
    resType: String? = null) : Comparable<PrefEntry<*>> {
  var defaultType: Class<*>? = null
  var valueType: Class<*>? = null

  // For list types
  var hasListAttributes = false
  var entriesGetterStmt: String? = null
  var entryValuesGetterStmt: String? = null

  // Resource specific info
  var resType: String? = null
  var isResource = false
  var resourceDefaultValueGetterStmt: String? = null

  init {
    if (resType != null) {
      this.resType = resType
      this.isResource = true
    }

    when {
      defaultValue == null -> {
        this.defaultType = null
        this.valueType = null
      }
      isResource -> {
        this.defaultType = String::class.java
        resolveResourceInfo()
      }
      defaultValue is Boolean -> {
        this.defaultType = Boolean::class.javaPrimitiveType
        this.valueType = this.defaultType
      }
      defaultValue is Int -> {
        this.defaultType = Int::class.javaPrimitiveType
        this.valueType = this.defaultType
      }
      defaultValue is String -> {
        this.defaultType = String::class.java
        this.valueType = this.defaultType
      }
      else -> throw UnsupportedOperationException(
          "Unsupported type: " + defaultValue!!.javaClass.simpleName)
    }
  }

  /**
   * Marks this entry as a list preference if the passed parameters are value @array resource refs
   */
  internal fun markAsListPreference(entries: String, entryValues: String) {
    val entriesName = getArrayResourceName(entries)
    if (entriesName != null) {
      this.entriesGetterStmt = "getTextArray(R.array.$entriesName)"
      this.hasListAttributes = true
    }
    val entryValuesName = getArrayResourceName(entryValues)
    if (entryValuesName != null) {
      this.entryValuesGetterStmt = "getTextArray(R.array.$entryValuesName)"
      this.hasListAttributes = true
    }
  }

  override fun hashCode(): Int {
    return key.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    } else if (other == null || javaClass != other.javaClass) {
      return false
    }

    return hashCode() == other.hashCode()
  }

  override fun compareTo(other: PrefEntry<*>): Int {
    return this.key.compareTo(other.key)
  }


  val isBlank: Boolean
    get() = key.isBlank()

  // TODO
  // Float
  // Long
  // String set
  // String[] overload for array
  // int[] overload for array
  private fun resolveResourceInfo() {
    val type = resType
    val statement: String

    when (type) {
      "bool" -> {
        statement = "getBoolean(%s)"
        valueType = Boolean::class.javaPrimitiveType
      }
      "color" -> {
        statement = "getColor(%s)"
        valueType = Int::class.javaPrimitiveType
      }
      "integer" -> {
        statement = "getInteger(%s)"
        valueType = Int::class.javaPrimitiveType
      }
      "string" -> {
        statement = "getString(%s)"
        valueType = String::class.java
      }
      "array" -> {
        statement = "getTextArray(%s)"
        valueType = Array<CharSequence>::class.java
      }
      else -> {
        // We can't handle this type yet, force it to blank out
        this.key = ""
        return
      }
    }

    resourceDefaultValueGetterStmt = String.format(statement, "DEFAULT_RES_ID")
  }

  override fun toString(): String {
    return "PrefEntry(key='$key', defaultValue=$defaultValue, defaultType=$defaultType, valueType=$valueType, hasListAttributes=$hasListAttributes, entriesGetterStmt=$entriesGetterStmt, entryValuesGetterStmt=$entryValuesGetterStmt, resType=$resType, isResource=$isResource, resourceDefaultValueGetterStmt=$resourceDefaultValueGetterStmt)"
  }

  companion object {

    val BLANK = PrefEntry<Void>("", null, null)

    private fun getArrayResourceName(ref: String?): String? {
      if (ref == null || !ref.startsWith("@array/") || ref.length < 7) {
        return null
      }

      return ref.substring(7)
    }
  }
}
