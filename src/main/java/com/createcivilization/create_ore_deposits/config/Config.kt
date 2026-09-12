@file:Suppress("PropertyName")
package com.createcivilization.create_ore_deposits.config

import com.createcivilization.create_ore_deposits.registry.block.CreateOreDepositsBlocks
import com.createcivilization.create_ore_deposits.registry.worldgen.OreVeinTier
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.ModConfigSpec

data object Config {

	class Server(builder: ModConfigSpec.Builder) {

		val DEPOSIT_DRILL: DepositDrill = run {
			builder.push("deposit_drill")
			val config = DepositDrill(builder)
			builder.pop()
			return@run config
		}
		class DepositDrill(builder: ModConfigSpec.Builder) {

// Heat & cooling
			@PublishedApi
			internal val _baseTemperature: ModConfigSpec.DoubleValue =
				builder.defineInRange("baseTemperature", 293.0, -1000.0, 5000.0)
			inline val baseTemperature: Float get() = _baseTemperature.get().toFloat()

			@PublishedApi
			internal val _baseCooling: ModConfigSpec.DoubleValue = builder
				.comment("Passive cooling per tick. Cooling pulls the temperature back toward baseTemperature.")
				.defineInRange("baseCooling", 0.004, 0.0, 10.0)
			inline val baseCooling: Float get() = _baseCooling.get().toFloat()

			@PublishedApi
			internal val _heatPerTickBase: ModConfigSpec.DoubleValue = builder
				.comment("Heat added per tick per 64 RPM while mining, before hardness. Mining always makes heat, fluids only slow it.")
				.defineInRange("heatPerTickBase", 0.20, 0.0, 100.0)
			inline val heatPerTickBase: Float get() = _heatPerTickBase.get().toFloat()

			@PublishedApi
			internal val _heatPerTickHardness: ModConfigSpec.DoubleValue = builder
				.comment("Extra heat added per tick per 64 RPM for each point of deposit hardness.")
				.defineInRange("heatPerTickHardness", 0.35, 0.0, 100.0)
			inline val heatPerTickHardness: Float get() = _heatPerTickHardness.get().toFloat()

			@PublishedApi
			internal val _lubeHeatReduction: ModConfigSpec.DoubleValue = builder
				.comment(
					"Lubricant cuts heat generation. Final reduction = lubricant factor * tank fill * this value, capped at 90%.",
					"A full lubricant tank can almost stop the drill from heating up at all."
				)
				.defineInRange("lubeHeatReduction", 0.25, 0.0, 1.0)
			inline val lubeHeatReduction: Float get() = _lubeHeatReduction.get().toFloat()

			@PublishedApi
			internal val _coolantCoolingMult: ModConfigSpec.DoubleValue = builder
				.comment(
					"Coolant carries heat away. Added cooling = coolant factor * tank fill * this value.",
					"A full coolant tank can hold the temperature flat even in the hardest deposits."
				)
				.defineInRange("coolantCoolingMult", 0.03, 0.0, 1.0)
			inline val coolantCoolingMult: Float get() = _coolantCoolingMult.get().toFloat()

// Extraction pacing (ticks per simulated loot roll)
			@PublishedApi
			internal val _baseExtractionInterval: ModConfigSpec.IntValue = builder
				.comment("Base ticks per extraction attempt. Tune down to ~220-260 for the ~20 ores/min target @128 RPM with fluids.")
				.defineInRange("baseExtractionInterval", 320, 1, 5000)
			inline val baseExtractionInterval: Int get() = _baseExtractionInterval.get()

			@PublishedApi
			internal val _speedFactor: ModConfigSpec.DoubleValue = builder
				.defineInRange("speedFactor", 0.95, 0.0, 10.0)
			inline val speedFactor: Float get() = _speedFactor.get().toFloat()

			@PublishedApi
			internal val _hardnessTickPenalty: ModConfigSpec.IntValue = builder
				.defineInRange("hardnessTickPenalty", 28, 0, 500)
			inline val hardnessTickPenalty: Int get() = _hardnessTickPenalty.get()

			@PublishedApi
			internal val _lubeTickBonus: ModConfigSpec.DoubleValue = builder
				.defineInRange("lubeTickBonus", 0.35, 0.0, 5.0)
			inline val lubeTickBonus: Float get() = _lubeTickBonus.get().toFloat()

			@PublishedApi
			internal val _minInterval: ModConfigSpec.IntValue = builder
				.defineInRange("minInterval", 12, 1, 1000)
			inline val minInterval: Int get() = _minInterval.get()

			@PublishedApi
			internal val _maxInterval: ModConfigSpec.IntValue = builder
				.defineInRange("maxInterval", 600, 1, 10000)
			inline val maxInterval: Int get() = _maxInterval.get()

// Fluid consumption (fluids are optional but help a lot with heat; consumed while mining)
			@PublishedApi
			internal val _lubeDrainPerLazy: ModConfigSpec.IntValue = builder
				.comment("Lubricant drained per lazy tick while mining (lazy tick = every 10 ticks). Speed adds +speed/128.")
				.defineInRange("lubeDrainPerLazyTick", 1, 0, 100)
			inline val lubeDrainPerLazy: Int get() = _lubeDrainPerLazy.get()

			@PublishedApi
			internal val _coolantDrainPerLazy: ModConfigSpec.IntValue = builder
				.comment("Coolant drained per lazy tick while mining (lazy tick = every 10 ticks). Speed adds +speed/128.")
				.defineInRange("coolantDrainPerLazyTick", 1, 0, 100)
			inline val coolantDrainPerLazy: Int get() = _coolantDrainPerLazy.get()

// Answer 3 hook: finite veins now, regeneration left for the future
			@PublishedApi
			internal val _enableRegeneration: ModConfigSpec.BooleanValue = builder
				.comment("Reserved: future regrowth hook. Veins are finite for the ship release; keep false.")
				.define("enableRegeneration", false)
			inline val enableRegeneration: Boolean get() = _enableRegeneration.get()

			@PublishedApi
			internal val _regenerationTicks: ModConfigSpec.IntValue = builder
				.defineInRange("regenerationTicks", 72000, 1, Int.MAX_VALUE)
			inline val regenerationTicks: Int get() = _regenerationTicks.get()

// Overheating. past overheatThreshold the drill slows down and starts eating the tip.
			@PublishedApi
			internal val _overheatThreshold: ModConfigSpec.DoubleValue = builder
				.comment("Above this temperature the drill extracts slower and wears its tip down.")
				.defineInRange("overheatThreshold", 600.0, 0.0, 5000.0)
			inline val overheatThreshold: Float get() = _overheatThreshold.get().toFloat()

			@PublishedApi
			internal val _criticalThreshold: ModConfigSpec.DoubleValue = builder
				.comment("Temperature where overheat effects are at full strength (max slowdown + max tip wear).")
				.defineInRange("criticalThreshold", 900.0, 0.0, 5000.0)
			inline val criticalThreshold: Float get() = _criticalThreshold.get().toFloat()

			@PublishedApi
			internal val _overheatSlowdown: ModConfigSpec.DoubleValue = builder
				.comment("At full overheat the extraction interval is multiplied by (1 + this). 2.0 = up to 3x slower.")
				.defineInRange("overheatSlowdown", 2.0, 0.0, 100.0)
			inline val overheatSlowdown: Float get() = _overheatSlowdown.get().toFloat()

			@PublishedApi
			internal val _maxTipWearPerTick: ModConfigSpec.DoubleValue = builder
				.comment("Tip durability lost per tick at full overheat. Fractional values accumulate. 0 disables tip wear.")
				.defineInRange("maxTipWearPerTick", 0.15, 0.0, 100.0)
			inline val maxTipWearPerTick: Float get() = _maxTipWearPerTick.get().toFloat()

			@PublishedApi
			internal val _wornTipSlowdown: ModConfigSpec.DoubleValue = builder
				.comment("A worn tip mines slower. At 0 durability the extraction interval is multiplied by (1 + this).")
				.defineInRange("wornTipSlowdown", 1.5, 0.0, 100.0)
			inline val wornTipSlowdown: Float get() = _wornTipSlowdown.get().toFloat()

// Stress
			@PublishedApi
			internal val _baseImpact: ModConfigSpec.DoubleValue = builder
				.defineInRange("baseImpact", 48.0, 0.0, 10000.0)
			inline val baseImpact: Float get() = _baseImpact.get().toFloat()

			@PublishedApi
			internal val _hardnessStressMult: ModConfigSpec.DoubleValue = builder
				.defineInRange("hardnessStressMult", 0.45, 0.0, 10.0)
			inline val hardnessStressMult: Float get() = _hardnessStressMult.get().toFloat()

			@PublishedApi
			internal val _lubeStressReduction: ModConfigSpec.DoubleValue = builder
				.defineInRange("lubeStressReduction", 0.25, 0.0, 1.0)
			inline val lubeStressReduction: Float get() = _lubeStressReduction.get().toFloat()

			@PublishedApi
			internal val _maxDrillDepth: ModConfigSpec.IntValue = builder
				.defineInRange("maxDrillDepth", 64, 1, 512)
			inline val maxDrillDepth: Int get() = _maxDrillDepth.get()

// Answer 8: no redstone / no comparator for ship. Flags stay false; code ignores signals.
			@PublishedApi
			internal val _enableRedstonePause: ModConfigSpec.BooleanValue = builder
				.comment("Reserved for the future. The drill ignores redstone signals for the ship release.")
				.define("enableRedstonePause", false)
			inline val enableRedstonePause: Boolean get() = _enableRedstonePause.get()

			@PublishedApi
			internal val _enableComparatorOutput: ModConfigSpec.BooleanValue = builder
				.comment("Reserved for the future. No comparator output is emitted for the ship release.")
				.define("enableComparatorOutput", false)
			inline val enableComparatorOutput: Boolean get() = _enableComparatorOutput.get()
		}

		val ORE_VEINS: OreVeins = run {
			builder.push("ore_veins")
			val config = OreVeins(builder)
			builder.pop()
			return@run config
		}

// these two control the grid the whole cluster system is built on, touch with care.
// region size = how big a "tile" is before we roll for a cluster in it.
// padding = how many empty chunks we leave at the edge of a region so two clusters in
// neighbouring regions can never physically touch. if you shrink padding, go re-check
// regionLayout() math in OreVeinPlacementModifier, it assumes this is at least 1.

		class OreVeins(builder: ModConfigSpec.Builder) {
			companion object {
				const val CLUSTER_REGION_SIZE_CHUNKS: Int = 50
				const val CLUSTER_REGION_PADDING_CHUNKS: Int = 1
			}

			@PublishedApi
			internal val _enableRegionFamilyGate: ModConfigSpec.BooleanValue = builder
				.comment(
    			"true = every ore+tier fights over the same region roll, only one wins (rarer overall, but ores compete with each other).",
    			"false = each region gets reserved for one specific ore+tier up front, so ores don't steal each other's spawns.",
    			"config key stayed 'enableChunkFamilyGate' from before we switched to regions, didn't want to break people's existing configs over a rename."
				)
				.worldRestart()
				.define("enableRegionFamilyGate", true)
			inline val enableRegionFamilyGate: Boolean get() = _enableRegionFamilyGate.get()

			val EXAMPLE_DEPOSIT: Deposit = deposit(builder, "example_deposit", ironDefaults())
			val COAL_ORE_DEPOSIT: Deposit = deposit(builder, "coal_ore_deposit", coalDefaults())
			val IRON_ORE_DEPOSIT: Deposit = deposit(builder, "iron_ore_deposit", ironDefaults())
			val GOLD_ORE_DEPOSIT: Deposit = deposit(builder, "gold_ore_deposit", goldDefaults())
			val COPPER_ORE_DEPOSIT: Deposit = deposit(builder, "copper_ore_deposit", copperDefaults())
			val LAPIS_ORE_DEPOSIT: Deposit = deposit(builder, "lapis_ore_deposit", lapisDefaults())
			val DIAMOND_ORE_DEPOSIT: Deposit = deposit(builder, "diamond_ore_deposit", diamondDefaults())
			val EMERALD_ORE_DEPOSIT: Deposit = deposit(builder, "emerald_ore_deposit", emeraldDefaults())
			val QUARTZ_ORE_DEPOSIT: Deposit = deposit(builder, "quartz_ore_deposit", quartzDefaults())
			val NETHERITE_ORE_DEPOSIT: Deposit = deposit(builder, "netherite_ore_deposit", netheriteDefaults())

			private val DEPOSITS_BY_BLOCK: Map<Block, Deposit> by lazy {
				mapOf(
					CreateOreDepositsBlocks.EXAMPLE_DEPOSIT.get() to EXAMPLE_DEPOSIT,
					CreateOreDepositsBlocks.COAL_ORE_DEPOSIT.get() to COAL_ORE_DEPOSIT,
					CreateOreDepositsBlocks.IRON_ORE_DEPOSIT.get() to IRON_ORE_DEPOSIT,
					CreateOreDepositsBlocks.GOLD_ORE_DEPOSIT.get() to GOLD_ORE_DEPOSIT,
					CreateOreDepositsBlocks.COPPER_ORE_DEPOSIT.get() to COPPER_ORE_DEPOSIT,
					CreateOreDepositsBlocks.LAPIS_ORE_DEPOSIT.get() to LAPIS_ORE_DEPOSIT,
					CreateOreDepositsBlocks.DIAMOND_ORE_DEPOSIT.get() to DIAMOND_ORE_DEPOSIT,
					CreateOreDepositsBlocks.EMERALD_ORE_DEPOSIT.get() to EMERALD_ORE_DEPOSIT,
					CreateOreDepositsBlocks.QUARTZ_ORE_DEPOSIT.get() to QUARTZ_ORE_DEPOSIT,
					CreateOreDepositsBlocks.NETHERITE_ORE_DEPOSIT.get() to NETHERITE_ORE_DEPOSIT
				)
			}

// fails loud on purpose, missing a deposit mapping here means someone added an ore block
// and forgot to wire it up, better to crash in dev than silently break worldgen
			fun byBlock(block: Block): Deposit = DEPOSITS_BY_BLOCK[block]
				?: error("Unsupported ore deposit block in Config.SERVER.ORE_VEINS.byBlock(): ${BuiltInRegistries.BLOCK.getKey(block)}")

			private fun deposit(builder: ModConfigSpec.Builder, name: String, defaults: DepositDefaults): Deposit = run {
				builder.push(name)
				val config = Deposit(builder, defaults)
				builder.pop()
				return@run config
			}

			class Deposit(builder: ModConfigSpec.Builder, defaults: DepositDefaults) {

				val LARGE: Tier = tier(builder, "large", defaults.large)
				val MEDIUM: Tier = tier(builder, "medium", defaults.medium)
				val SMALL: Tier = tier(builder, "small", defaults.small)

				fun forTier(tier: OreVeinTier): Tier = when (tier) {
					OreVeinTier.LARGE -> LARGE
					OreVeinTier.MEDIUM -> MEDIUM
					OreVeinTier.SMALL -> SMALL
				}

				private fun tier(builder: ModConfigSpec.Builder, name: String, defaults: TierDefaults): Tier = run {
					builder.push(name)
					val config = Tier(builder, defaults)
					builder.pop()
					return@run config
				}
			}

			data class DepositDefaults(
				val large: TierDefaults,
				val medium: TierDefaults,
				val small: TierDefaults
			)

// one of these per tier per ore. averageChunksPerCluster is NOT "how far apart deposits are",
// that's chunksBetweenDeposits below. this one is jst "how rare is it that a cluster
// exists at all in this area". easy to mix these two up, I did it myself at least twice.

			data class TierDefaults(
				val averageChunksPerCluster: Int,
				val minY: Int,
				val maxY: Int,
				val minDepositsPerCluster: Int,
				val maxDepositsPerCluster: Int,
				val chunksBetweenDeposits: Int,
				val biomeSelectors: List<String> = listOf("#minecraft:is_overworld")
			)

			class Tier(builder: ModConfigSpec.Builder, defaults: TierDefaults) {

				@PublishedApi
				internal val _biomeSelectors: ModConfigSpec.ConfigValue<List<out String>> = builder
					.comment(
						"Biome ids or biome tags allowed for this tier.",
						"Examples: minecraft:plains, #minecraft:is_overworld"
					)
					.worldRestart()
					.defineListAllowEmpty("biomeSelectors", defaults.biomeSelectors) { value ->
						value is String && isBiomeSelector(value)
					}
				inline val biomeSelectors: List<String> get() = _biomeSelectors.get().map { it.toString() }

				@PublishedApi
				internal val _averageChunksPerCluster: ModConfigSpec.IntValue = builder
					.comment(
						"Average chunk spacing between successful cluster origins for this tier.",
						"This controls how rare clusters are in the world, not how far apart deposits inside one cluster are.",
						"1 tries every chunk, 256 tries about once every 256 chunks, and larger numbers are rarer."
					)
					.worldRestart()
					.defineInRange("averageChunksPerCluster", defaults.averageChunksPerCluster, 0, Int.MAX_VALUE)
				inline val averageChunksPerCluster: Int get() = _averageChunksPerCluster.get()

				@PublishedApi
				internal val _minY: ModConfigSpec.IntValue = builder
					.comment("Lowest Y level for cluster origins in this tier.")
					.worldRestart()
					.defineInRange("minY", defaults.minY, -128, 512)
				inline val minY: Int get() = _minY.get()

				@PublishedApi
				internal val _maxY: ModConfigSpec.IntValue = builder
					.comment("Highest Y level for cluster origins in this tier.")
					.worldRestart()
					.defineInRange("maxY", defaults.maxY, -128, 512)
				inline val maxY: Int get() = _maxY.get()

				@PublishedApi
				internal val _minDepositsPerCluster: ModConfigSpec.IntValue = builder
					.comment("Minimum number of individual layered deposits that a successful cluster should try to place.")
					.worldRestart()
					.defineInRange("minDepositsPerCluster", defaults.minDepositsPerCluster, 1, Int.MAX_VALUE)
				inline val minDepositsPerCluster: Int get() = _minDepositsPerCluster.get()

				@PublishedApi
				internal val _maxDepositsPerCluster: ModConfigSpec.IntValue = builder
					.comment("Maximum number of individual layered deposits that a successful cluster should try to place.")
					.worldRestart()
					.defineInRange("maxDepositsPerCluster", defaults.maxDepositsPerCluster, 1, Int.MAX_VALUE)
				inline val maxDepositsPerCluster: Int get() = _maxDepositsPerCluster.get()

				@PublishedApi
				internal val _chunksBetweenDeposits: ModConfigSpec.IntValue = builder
					.comment(
						"how many chunks apart the individual deposits inside ONE cluster are spread.",
						"this has nothing to do with how rare the cluster itself is, see averageChunksPerCluster for that.",
						"if this number times maxDepositsPerCluster is too big for the region size, we auto-shrink it at runtime and log a warning once — see OreVeinFeature.warnIncompatibleRegionConfig."
					)
					.worldRestart()
					.defineInRange("chunksBetweenDeposits", defaults.chunksBetweenDeposits, 1, 64)
				inline val chunksBetweenDeposits: Int get() = _chunksBetweenDeposits.get()

				private fun isBiomeSelector(value: String): Boolean = value.isNotBlank() && runCatching {
					ResourceLocation.parse(value.removePrefix("#"))
				}.isSuccess
			}

			private fun tierDefaults(
				averageChunksPerCluster: Int,
				minY: Int,
				maxY: Int,
				minDepositsPerCluster: Int,
				maxDepositsPerCluster: Int,
				chunksBetweenDeposits: Int,
				biomeSelectors: List<String> = listOf("#minecraft:is_overworld")
			): TierDefaults = TierDefaults(
				averageChunksPerCluster = averageChunksPerCluster,
				minY = minY,
				maxY = maxY,
				minDepositsPerCluster = minDepositsPerCluster,
				maxDepositsPerCluster = maxDepositsPerCluster,
				chunksBetweenDeposits = chunksBetweenDeposits,
				biomeSelectors = biomeSelectors
			)

			private fun uniformRangeDefaults(minY: Int, maxY: Int): DepositDefaults = DepositDefaults(
				large = tierDefaults(2048, minY, maxY, 6, 9, 2),
				medium = tierDefaults(768, minY, maxY, 3, 5, 2),
				small = tierDefaults(256, minY, maxY, 1, 2, 1)
			)

			private fun coalDefaults(): DepositDefaults = DepositDefaults(
				large = tierDefaults(2048, 136, 320, 6, 9, 2),
				medium = tierDefaults(768, 0, 192, 3, 5, 2),
				small = tierDefaults(256, 0, 192, 1, 2, 1)
			)

			private fun ironDefaults(): DepositDefaults = DepositDefaults(
				large = tierDefaults(2048, 80, 384, 6, 9, 2),
				medium = tierDefaults(768, -24, 56, 3, 5, 2),
				small = tierDefaults(256, -64, 72, 1, 2, 1)
			)

			private fun goldDefaults(): DepositDefaults = DepositDefaults(
				large = tierDefaults(2048, -64, 32, 6, 9, 2),
				medium = tierDefaults(768, -64, 32, 3, 5, 2),
				small = tierDefaults(256, -64, -48, 1, 2, 1)
			)

			private fun copperDefaults(): DepositDefaults = uniformRangeDefaults(-16, 112)

			private fun lapisDefaults(): DepositDefaults = DepositDefaults(
				large = tierDefaults(2048, -64, 64, 6, 9, 2),
				medium = tierDefaults(768, -32, 32, 3, 5, 2),
				small = tierDefaults(256, -64, 64, 1, 2, 1)
			)

			private fun diamondDefaults(): DepositDefaults = DepositDefaults(
				large = tierDefaults(2048, -64, 16, 6, 9, 2),
				medium = tierDefaults(768, -64, -4, 3, 5, 2),
				small = tierDefaults(256, -64, 16, 1, 2, 1)
			)

			private fun emeraldDefaults(): DepositDefaults = uniformRangeDefaults(-16, 480)

			private fun quartzDefaults(): DepositDefaults = uniformRangeDefaults(10, 310)

			private fun netheriteDefaults(): DepositDefaults {
// overworld only mod, so netherite reuses diamond's Y range instead of ancient debris's real (nether) range.
// don't "fix" this back to nether Y values, it's on purpose.
				return diamondDefaults()
			}
		}
	}

	@JvmField val SERVER: Server
	internal val serverSpec: ModConfigSpec

	init {
		ModConfigSpec.Builder().configure(::Server).let {
			SERVER = it.left
			serverSpec = it.right
		}
	}
}
