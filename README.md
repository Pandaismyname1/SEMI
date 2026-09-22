# EMI
EMI is a featureful and accessible item and recipe viewer for Minecraft.

![EMI Interface](https://user-images.githubusercontent.com/14813658/224562247-1588064e-39ef-475a-9108-d7a357af6939.png)

![Recipe Tree](https://user-images.githubusercontent.com/14813658/224562258-1a5ee67a-fd7f-489f-9eed-ae67c184ddac.png)

## Developers
To add EMI to your project as a dependency you need to add the following to your `build.gradle`:
```gradle
repositories {
	maven {
		name = "Sleeping Town"
		url = "https://repo.sleeping.town/"
	}
}
```

How EMI gets added to your dependencies varies based on modloader and setup.
The Gradle property `emi_version` should be something like `1.1.24+26.1.2` with EMI's version and Minecraft's version.
EMI for Minecraft 26.1+ requires Java 25.
Here are common dependency setups for different loaders and build systems.

```gradle
dependencies {
	// Fabric (26.1+: Minecraft is unobfuscated, so no mod* remapping configurations)
	compileOnly "dev.emi:emi-fabric:${emi_version}:api"
	localRuntime "dev.emi:emi-fabric:${emi_version}"

	// Forge (see below block as well if you use Forge Gradle)
	compileOnly fg.deobf("dev.emi:emi-forge:${emi_version}:api")
	runtimeOnly fg.deobf("dev.emi:emi-forge:${emi_version}") 

	// NeoForge
	compileOnly "dev.emi:emi-neoforge:${emi_version}:api"
	runtimeOnly "dev.emi:emi-neoforge:${emi_version}" 

	// Architectury / MultiLoader Template / VanillaGradle (common project)
	// 26.1+ only publishes the Mojang-mapped cross-platform artifact
	compileOnly "dev.emi:emi-xplat-mojmap:${emi_version}:api"
}
```

For Forge Gradle users, you will need to enable Mixin refmaps in your client sourceset. This can be done by adding 2 lines inside of your client runs, to look like below.

```gradle
runs {
	client {
		// Add these two lines
		property 'mixin.env.remapRefMap', 'true'
		property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"

		// The rest of the code that was already here
		// ...
	}
}
```
