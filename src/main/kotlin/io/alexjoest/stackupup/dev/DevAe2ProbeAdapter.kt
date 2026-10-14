package io.alexjoest.stackupup.dev

import net.minecraft.server.MinecraftServer
import net.minecraftforge.fml.common.Loader

internal interface DevDeferredFocusedProbe : DevCompatProbe {
    fun start(server: MinecraftServer, complete: (DevCompatProbeResult) -> Unit)

    override fun run(server: MinecraftServer): DevCompatProbeResult = error("$id requires deferred server ticks")
}

internal class DevAe2ProbeAdapter(override val id: String, private val expansionEnabled: Boolean) : DevDeferredFocusedProbe {
    override fun start(server: MinecraftServer, complete: (DevCompatProbeResult) -> Unit) {
        requireAe2ProbeRuntime(Loader.isModLoaded("appliedenergistics2"))
        DevAe2OwnerInventoryProbe(id, expansionEnabled).start(server, complete)
    }
}

internal fun requireAe2ProbeRuntime(loaded: Boolean) {
    check(loaded) { "AE2 owner focus requires runtime appliedenergistics2; missing runtime is a failure" }
}
