package pl.asie.stackup.script.rule

import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.INBTSerializable

abstract class Rule : INBTSerializable<NBTTagCompound> {
    private var applied: Boolean = false

    fun apply(): Boolean {
        if (applied) {
            throw RuntimeException("Cannot apply rule twice!")
        }

        return if (applyInternal()) {
            applied = true
            true
        } else {
            false
        }
    }

    fun undo(): Boolean {
        if (!applied) {
            throw RuntimeException("Cannot undo an unapplied rule!")
        }

        return if (undoInternal()) {
            applied = false
            true
        } else {
            false
        }
    }

    protected abstract fun applyInternal(): Boolean
    protected abstract fun undoInternal(): Boolean
}
