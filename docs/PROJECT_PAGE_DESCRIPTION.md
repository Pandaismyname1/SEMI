# SEMI — Seasonal EMI

**SEMI is a new project that continues EMI.** It is an **unofficial port and continuation** of [EMI](https://modrinth.com/mod/emi) by [Emi (emilyploszaj)](https://github.com/emilyploszaj), created to keep EMI working on the latest Minecraft releases. It keeps the `emi` mod id and API, so existing EMI plugins keep working, and it is a drop-in replacement: do **not** install it alongside the original EMI.

**Made with AI.** AI (Claude, by Anthropic) was used to bring EMI up to speed with the latest Minecraft releases quickly: merging the community port, re-applying upstream changes, updating the toolchain, and running repeated code reviews and in-game checks on both loaders. The port notes and every decision are documented in the repository.

**Credits.** EMI is the work of Emi and the [EMI contributors](https://github.com/emilyploszaj/emi/graphs/contributors), released under the MIT License; all of the design, features and almost all of the code are theirs. The Minecraft 26.1 port builds on the MIT-licensed community port by [link-fgfgui](https://github.com/link-fgfgui/emi). SEMI is maintained by [Pandaismyname1](https://github.com/Pandaismyname1). Please report SEMI issues at <https://github.com/Pandaismyname1/SEMI/issues>, not to the original EMI project.

The original EMI description follows.

---

# EMI
EMI is a featureful and accessible item and recipe viewer. It brings many new features, and optimizes for the user experience. Outside of the standard Fabric/Quilt API, EMI requires **zero** dependencies, and can be launched with the game simply and easily.

![](https://cdn.modrinth.com/data/fRiHVvU7/images/0d3b5b33d0016b21834ce6f3602169f7c48de13f.png)

![](https://cdn.modrinth.com/data/fRiHVvU7/images/f3c8b452eae6dde028c37e3a3e3792459468d865.png)

## Runtime JEI Compat
For runtime JEI compat, all you need to do is install JEI alongside EMI and they will work together to share recipes. JEI will be hidden.

## What does EMI bring to the table?

Of course EMI comes with every feature you'd come to expect from the outset, viewing recipes, favoriting items, searching, and the like. But what's new that EMI offers?

* A craftable mode for **quickly crafting** any recipes you're able to make, usable by toggle or as a config for empty searches
* **Recipes** can be **favorited** with items, and also can be used to quickly craft at a button press
* A **recipe tree** for breaking down the cost of a complex craft, showing you **every step**, how many **base ingredients** you need, and what you'll have leftover
* A **recipe tree crafting mode**, counting up the materials and steps you need to be complete to finish a task, including **synthetic favorites** in your sidebar you can use to **craft every step** for exactly as much as you need
* Smart display of **tags**, with translations, models, and tooltips, showing you ingredients at a glance, such as "Planks" instead of slowly rotating through every plank in the game.
* Smart breakdowns of base costs, letting recipe trees automatically break down to base ingredients, and letting you define **your own defaults** as you play
* **Tooltips** for recipes, tags, and ingredients showing you what will be crafted, and what makes up an ingredient
* Binds for quick crafting **straight into your inventory or cursor**, one or many at a time, in conjunction with recipes favorites or the craftable mode
* A **clean** and **modern** API, built from the ground up to be simple, powerful, and suit all of EMI's features
* **Zero third party dependencies**. EMI only requires standard, first party Fabric/Quilt APIs


## What does EMI stand for?
It doesn't, it's not an acronym. Or maybe it's an acronym without a meaning? If it makes you more comfortable, you can pick one from the list below, or make your own up.

* Emi Memy Imi
* Exhaustively Many Items
* Explicitly Mandated Items
* Endless Material Information
* Expounded Minutia Introspection
* Earnestly Made Imitation
* Even More Items
* Eminence, My Inception
* Emi's Magic Inventory
* Efficiently Managed Inventory
* Exploring Modified: Iridescent
* Expropriated Matter Insights