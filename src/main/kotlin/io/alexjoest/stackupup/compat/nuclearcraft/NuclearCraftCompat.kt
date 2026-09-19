package io.alexjoest.stackupup.compat.nuclearcraft

import io.alexjoest.stackupup.StackUpUpConfig

object NuclearCraftCompat {
    private var upgradeStackSizes: IntArray? = null

    @JvmStatic
    fun rememberUpgradeStackSizes(upgradeStackSizes: IntArray) {
        this.upgradeStackSizes = upgradeStackSizes
        applyConfiguredUpgradeStackLimits()
    }

    @JvmStatic
    fun applyConfiguredUpgradeStackLimits() {
        val upgradeStackSizes = upgradeStackSizes ?: return
        val speedLimit = StackUpUpConfig.compat.nuclearcraft.speedUpgradeLimit
        val energyLimit = StackUpUpConfig.compat.nuclearcraft.energyUpgradeLimit
        if (speedLimit <= 0 && energyLimit <= 0) {
            return
        }
        require(upgradeStackSizes.size >= 2) {
            "NuclearCraft upgrade_stack_sizes must contain speed and energy entries"
        }
        if (speedLimit > 0) {
            upgradeStackSizes[0] = speedLimit
        }
        if (energyLimit > 0) {
            upgradeStackSizes[1] = energyLimit
        }
    }
}
