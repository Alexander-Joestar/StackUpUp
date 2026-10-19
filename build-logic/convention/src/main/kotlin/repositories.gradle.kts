repositories {
    mavenCentral()
    maven {
        name = "CleanroomMC Maven"
        url = uri("https://maven.cleanroommc.com")
    }
    maven {
        name = "SpongePowered Maven"
        url = uri("https://repo.spongepowered.org/maven")
    }
    maven {
        name = "CurseMaven"
        url = uri("https://cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    maven {
        name = "GTCEu AE2 API Maven"
        url = uri("https://maven.gtceu.com")
        content {
            includeModule("appeng", "ae2-uel")
        }
    }
    mavenLocal() // Must be last for caching to work
}
