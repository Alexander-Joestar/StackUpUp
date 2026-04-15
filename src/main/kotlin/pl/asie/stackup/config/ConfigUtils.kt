package pl.asie.stackup.config

import net.minecraftforge.common.config.Configuration

object ConfigUtils {
    private fun prepareCategory(config: Configuration, category: String) {
        val configCategory = config.getCategory(category)
        configCategory.setLanguageKey("config.stackup.$category.name")
    }

    @JvmStatic
    fun getBoolean(
        config: Configuration,
        category: String,
        name: String,
        defaultValue: Boolean,
        comment: String,
        requiresRestart: Boolean
    ): Boolean {
        prepareCategory(config, category)
        val prop = config.get(category, name, defaultValue)
        prop.comment = comment
        prop.setRequiresMcRestart(requiresRestart)
        prop.setLanguageKey("config.stackup.$category.$name.name")
        return prop.boolean
    }

    @JvmStatic
    fun getString(
        config: Configuration,
        category: String,
        name: String,
        defaultValue: String,
        comment: String,
        requiresRestart: Boolean
    ): String {
        prepareCategory(config, category)
        val prop = config.get(category, name, defaultValue)
        prop.comment = comment
        prop.setRequiresMcRestart(requiresRestart)
        prop.setLanguageKey("config.stackup.$category.$name.name")
        return prop.string
    }

    @JvmStatic
    fun getStringList(
        config: Configuration,
        category: String,
        name: String,
        defaultValue: Array<String>,
        comment: String,
        requiresRestart: Boolean
    ): Array<String> {
        prepareCategory(config, category)
        val prop = config.get(category, name, defaultValue)
        prop.comment = comment
        prop.setRequiresMcRestart(requiresRestart)
        prop.setLanguageKey("config.stackup.$category.$name.name")
        return prop.stringList
    }

    @JvmStatic
    fun getInt(
        config: Configuration,
        category: String,
        name: String,
        defaultValue: Int,
        minValue: Int,
        maxValue: Int,
        comment: String,
        requiresRestart: Boolean
    ): Int {
        prepareCategory(config, category)
        val prop = config.get(category, name, defaultValue)
        prop.setMinValue(minValue.toDouble())
        prop.setMaxValue(maxValue.toDouble())
        prop.comment = comment
        prop.setRequiresMcRestart(requiresRestart)
        prop.setLanguageKey("config.stackup.$category.$name.name")
        return prop.int
    }

    @JvmStatic
    fun getFloat(
        config: Configuration,
        category: String,
        name: String,
        defaultValue: Float,
        minValue: Float,
        maxValue: Float,
        comment: String,
        requiresRestart: Boolean
    ): Float {
        return getDouble(
            config,
            category,
            name,
            defaultValue.toDouble(),
            minValue.toDouble(),
            maxValue.toDouble(),
            comment,
            requiresRestart
        ).toFloat()
    }

    @JvmStatic
    fun getDouble(
        config: Configuration,
        category: String,
        name: String,
        defaultValue: Double,
        minValue: Double,
        maxValue: Double,
        comment: String,
        requiresRestart: Boolean
    ): Double {
        prepareCategory(config, category)
        val prop = config.get(category, name, defaultValue)
        prop.setMinValue(minValue)
        prop.setMaxValue(maxValue)
        prop.comment = comment
        prop.setRequiresMcRestart(requiresRestart)
        prop.setLanguageKey("config.stackup.$category.$name.name")
        return prop.double
    }
}
