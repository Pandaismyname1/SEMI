# SEMI — Seasonal EMI

SEMI ("Seasonal EMI") is an **unofficial continuation** of [EMI](https://github.com/emilyploszaj/emi), the featureful and accessible item and recipe viewer for Minecraft, kept up to date with the latest Minecraft releases. Its goal is simple: every new Minecraft version ("season") gets a working EMI as quickly as possible.

SEMI is a drop-in replacement for EMI. It keeps the `emi` mod id and the `dev.emi.emi.api` API, so existing EMI plugins keep working. Do not install it alongside the original EMI.

## Credits

- **EMI** was created and is written by **[Emi (emilyploszaj)](https://github.com/emilyploszaj)** and the [EMI contributors](https://github.com/emilyploszaj/emi/graphs/contributors). All of the design, the features and almost all of the code in this repository are theirs, released under the [MIT License](LICENSE). SEMI would not exist without their work, and the original project remains at <https://github.com/emilyploszaj/emi>.
- The Minecraft 26.1 and 26.2 ports build on the MIT-licensed community port by **[link-fgfgui](https://github.com/link-fgfgui/emi)**, merged with full commit attribution, which in turn drew on work by [Dabolus](https://github.com/Dabolus/MEMI) and others.
- SEMI is maintained by **[Pandaismyname1](https://github.com/Pandaismyname1)**.

Please report SEMI problems to [this repository's issue tracker](https://github.com/Pandaismyname1/SEMI/issues), not to the original EMI project.

## A note on AI

This port was made with AI. AI (Claude, by Anthropic) was used to bring EMI back up to speed with the latest versions of Minecraft quickly: merging the community port, re-applying upstream changes, updating the toolchain, and running repeated adversarial code reviews and in-game checks on both loaders, with every decision recorded in [`docs/`](docs/). Treat SEMI as a community effort that leans on automation, review the code if you depend on it, and please file bugs when you find them.

## Downloads

- Modrinth: <https://modrinth.com/mod/semi> (pending review)
- CurseForge: <https://www.curseforge.com/minecraft/mc-mods/semi-seasonal-emi> (pending moderator approval)
- GitHub releases: <https://github.com/Pandaismyname1/SEMI/releases>

## Supported versions

| Minecraft | Fabric | NeoForge | Branch |
|-----------|--------|----------|--------|
| 26.2      | yes    | yes      | `26.2` |
| 26.1.x    | yes    | yes      | `26.1` |

Minecraft 26.1 and 26.2 require Java 25. Fabric builds need Fabric API; NeoForge builds need NeoForge 26.1.2 or newer (26.1) or NeoForge 26.2.0 or newer (26.2). Each Minecraft version has its own branch and its own release, for example `1.1.24+26.1.2` for 26.1.2 and `1.1.24+26.2` for 26.2.

## Developers

SEMI keeps EMI's API. To depend on it, use the Modrinth Maven (available once versions are published) and the `emi_version` you need, for example `1.1.24+26.2`:

```gradle
repositories {
	exclusiveContent {
		forRepository {
			maven {
				name = "Modrinth"
				url = "https://api.modrinth.com/maven"
			}
		}
		filter {
			includeGroup "maven.modrinth"
		}
	}
}

dependencies {
	// Fabric (26.1+: Minecraft is unobfuscated, so no mod* remapping configurations)
	compileOnly "maven.modrinth:semi:${emi_version}-fabric"
	localRuntime "maven.modrinth:semi:${emi_version}-fabric"

	// NeoForge
	compileOnly "maven.modrinth:semi:${emi_version}-neoforge"
	runtimeOnly "maven.modrinth:semi:${emi_version}-neoforge"
}
```

Building from source: `./gradlew :fabric:build :neoforge:build` with JDK 25. The port notes, contract and decision log for each port live in [`docs/`](docs/).

---

# Original EMI README

Everything below is the README of the original EMI project by Emi, reproduced unchanged for reference. Its download links, Maven coordinates and Forge instructions refer to the original EMI, not to SEMI.

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
The Gradle property `emi_version` should be something like `1.0.0+1.19.4` with EMI's version and Minecraft's version.
Here are common dependency setups for different loaders and build systems.

```gradle
dependencies {
	// Fabric
	modCompileOnly "dev.emi:emi-fabric:${emi_version}:api"
	modLocalRuntime "dev.emi:emi-fabric:${emi_version}"

	// Forge (see below block as well if you use Forge Gradle)
	compileOnly fg.deobf("dev.emi:emi-forge:${emi_version}:api")
	runtimeOnly fg.deobf("dev.emi:emi-forge:${emi_version}") 

	// NeoForge
	compileOnly "dev.emi:emi-neoforge:${emi_version}:api"
	runtimeOnly "dev.emi:emi-neoforge:${emi_version}" 

	// Architectury
	modCompileOnly "dev.emi:emi-xplat-intermediary:${emi_version}:api"

	// MultiLoader Template/VanillaGradle
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
