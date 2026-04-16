package pl.asie.stackup

import pl.asie.stackup.limit.StackIdentity
import pl.asie.stackup.limit.StackUpServices
import java.util.Random

object StackUpHelpers {
    @JvmField
    val RANDOM: Random = Random()

    @JvmStatic
    fun getMaxStackSize(): Int = StackUp.maxStackSize

    @JvmStatic
    fun applyDynamicStackLimit(
        itemId: String,
        modId: String,
        meta: Int,
        type: String,
        baseLimit: Int,
        oreNames: Set<String>
    ): Int {
        return StackUpServices.limitService().resolve(
            StackIdentity(itemId, modId, meta, type),
            baseLimit,
            oreNames
        )
    }
}
