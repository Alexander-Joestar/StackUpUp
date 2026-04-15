package pl.asie.stackup

import java.util.Random

object StackUpHelpers {
    @JvmField
    val RANDOM: Random = Random()

    @JvmStatic
    fun getMaxStackSize(): Int = StackUp.maxStackSize
}
