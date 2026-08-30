plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val horizonwrightVersion = providers.gradleProperty("modVersion").get()
extra["modVersion"] = horizonwrightVersion
version = horizonwrightVersion
