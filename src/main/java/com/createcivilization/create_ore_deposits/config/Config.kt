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
			internal val _minHeatLoad: ModConfigSpec.DoubleValue = builder
				.comment(
					"Lowest heat load a running drill can have, as a fraction of 64 RPM.",
					"Without a floor a slow drill settles at a cool equilibrium and never overheats."
				)
				.defineInRange("minHeatLoad", 1.0, 0.0, 100.0)
			inline val minHeatLoad: Float get() = _minHeatLoad.get().toFloat()

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

// Extraction pacing (ticks per simulated loot roll). one shared block for every ore.
// how the interval is built, all in ticks:
//   interval = baseExtractionInterval
//            - round(speed * (speedFactor + lubricantFactor * lubeTickBonus))
//            + round(hardness * hardnessTickPenalty)
//   then x (1 + overheat * overheatSlowdown) and x (1 + tipWear * wornTipSlowdown)
//   and finally clamped between minInterval and maxInterval.
// per-ore values live in DataMapProvider.kt (DEPOSIT_DATA, maxAttempts/hardness).
			@PublishedApi
			internal val _baseExtractionInterval: ModConfigSpec.IntValue = builder
				.comment(
					"Ticks per attempt before speed and hardness are applied.",
					"240 is the reference pace every ore is tuned around: raise to slow the whole mod, lower to speed it up."
				)
				.defineInRange("baseExtractionInterval", 240, 1, 5000) // reference pace
			inline val baseExtractionInterval: Int get() = _baseExtractionInterval.get()

			@PublishedApi
			internal val _speedFactor: ModConfigSpec.DoubleValue = builder
				.comment("Ticks removed per RPM. 0.50 keeps a single RPM step from swamping the base value.")
				.defineInRange("speedFactor", 0.50, 0.0, 10.0) // speed reward
			inline val speedFactor: Float get() = _speedFactor.get().toFloat()

			@PublishedApi
			internal val _hardnessTickPenalty: ModConfigSpec.IntValue = builder
				.comment("Extra ticks per attempt for each point of deposit hardness. Harder ores bite slower.")
				.defineInRange("hardnessTickPenalty", 24, 0, 500) // hardness cost
			inline val hardnessTickPenalty: Int get() = _hardnessTickPenalty.get()

			@PublishedApi
			internal val _lubeTickBonus: ModConfigSpec.DoubleValue = builder
				.comment("Extra ticks removed per RPM per point of lubricant factor. At factor 2.5 lubricant roughly doubles extraction speed.")
				.defineInRange("lubeTickBonus", 0.20, 0.0, 5.0) // lube reward
			inline val lubeTickBonus: Float get() = _lubeTickBonus.get().toFloat()

			@PublishedApi
			internal val _minInterval: ModConfigSpec.IntValue = builder
				.comment("Fastest allowed interval. 40 ticks = 30 attempts/min; the ceiling that stops high RPM from blowing the rates up.")
				.defineInRange("minInterval", 40, 1, 1000) // speed ceiling
			inline val minInterval: Int get() = _minInterval.get()

			@PublishedApi
			internal val _maxInterval: ModConfigSpec.IntValue = builder
				.comment("Slowest allowed interval, for parked or badly overheating drills.")
				.defineInRange("maxInterval", 600, 1, 10000) // slowest pace
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
				.comment("Base tip durability lost per tick at full overheat, before the overheat boost below.")
				.defineInRange("maxTipWearPerTick", 0.15, 0.0, 100.0)
			inline val maxTipWearPerTick: Float get() = _maxTipWearPerTick.get().toFloat()

			@PublishedApi
			internal val _overheatTipWearBoost: ModConfigSpec.DoubleValue = builder
				.comment(
					"Extra tip wear that scales with how deep you are into overheat, so running hot costs more.",
					"Final wear = maxTipWearPerTick * overheat * (1 + overheat * this). 0.5 -> 1.5x at full overheat."
				)
				.defineInRange("overheatTipWearBoost", 0.5, 0.0, 100.0) // overheat penalty
			inline val overheatTipWearBoost: Float get() = _overheatTipWearBoost.get().toFloat()

			@PublishedApi
			internal val _wornTipSlowdown: ModConfigSpec.DoubleValue = builder
				.comment("A worn tip mines slower. At 0 durability the extraction interval is multiplied by (1 + this).")
				.defineInRange("wornTipSlowdown", 1.5, 0.0, 100.0)
			inline val wornTipSlowdown: Float get() = _wornTipSlowdown.get().toFloat()

// Stress
			@PublishedApi
			internal val _stressPerRpm: ModConfigSpec.DoubleValue = builder
				.comment(
					"Stress drawn per RPM, in SU. Create multiplies it by the drill's speed.",
					"3906.25 -> 500,000 SU @ 128 RPM and 1,000,000 SU @ 256 RPM."
				)
				.defineInRange("stressPerRpm", 3906.25, 0.0, 1000000.0)
			inline val stressPerRpm: Float get() = _stressPerRpm.get().toFloat()

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

			val COAL_ORE_DEPOSIT: Deposit = deposit(builder, "coal_ore_deposit", coalDefaults())
			val IRON_ORE_DEPOSIT: Deposit = deposit(builder, "iron_ore_deposit", ironDefaults())
			val GOLD_ORE_DEPOSIT: Deposit = deposit(builder, "gold_ore_deposit", goldDefaults())
			val COPPER_ORE_DEPOSIT: Deposit = deposit(builder, "copper_ore_deposit", copperDefaults())
			val LAPIS_ORE_DEPOSIT: Deposit = deposit(builder, "lapis_ore_deposit", lapisDefaults())
			val REDSTONE_ORE_DEPOSIT: Deposit = deposit(builder, "redstone_ore_deposit", redstoneDefaults())
			val QUARTZ_ORE_DEPOSIT: Deposit = deposit(builder, "quartz_ore_deposit", quartzDefaults())
			val NETHERITE_ORE_DEPOSIT: Deposit = deposit(builder, "netherite_ore_deposit", netheriteDefaults())

			private val DEPOSITS_BY_BLOCK: Map<Block, Deposit> by lazy {
				mapOf(
					CreateOreDepositsBlocks.COAL_ORE_DEPOSIT.get() to COAL_ORE_DEPOSIT,
					CreateOreDepositsBlocks.IRON_ORE_DEPOSIT.get() to IRON_ORE_DEPOSIT,
					CreateOreDepositsBlocks.GOLD_ORE_DEPOSIT.get() to GOLD_ORE_DEPOSIT,
					CreateOreDepositsBlocks.COPPER_ORE_DEPOSIT.get() to COPPER_ORE_DEPOSIT,
					CreateOreDepositsBlocks.LAPIS_ORE_DEPOSIT.get() to LAPIS_ORE_DEPOSIT,
					CreateOreDepositsBlocks.REDSTONE_ORE_DEPOSIT.get() to REDSTONE_ORE_DEPOSIT,
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
				val depositSize: Int,
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
				internal val _depositSize: ModConfigSpec.IntValue = builder
					.comment(
						"How big ONE deposit lobe is, as a width in blocks (radius = value / 2).",
						"12 is a blob about 12 blocks across, 32 is Create's own deposit size.",
						"Together with the lobe counts and the loot table this decides how much ore a vein holds."
					)
					.worldRestart()
					.defineInRange("depositSize", defaults.depositSize, 1, 64) // vein volume
				inline val depositSize: Int get() = _depositSize.get()

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
				size: Int,        // lobe volume
				rarity: Int,      // cluster rarity
				y: IntRange,      // spawn height
				lobes: IntRange,  // lobes per cluster
				spread: Int,      // chunk spacing
				biomeSelectors: List<String> = listOf("#minecraft:is_overworld")
			): TierDefaults = TierDefaults(
				depositSize = size,
				averageChunksPerCluster = rarity,
				minY = y.first,
				maxY = y.last,
				minDepositsPerCluster = lobes.first,
				maxDepositsPerCluster = lobes.last,
				chunksBetweenDeposits = spread,
				biomeSelectors = biomeSelectors
			)


			private fun coalDefaults(): DepositDefaults = DepositDefaults(
				small = tierDefaults(size = 8, rarity = 2500, y = 0..192, lobes = 1..2, spread = 1),
				medium = tierDefaults(size = 10, rarity = 5000, y = 0..192, lobes = 3..5, spread = 2),
				large = tierDefaults(size = 12, rarity = 7500, y = 136..320, lobes = 6..9, spread = 2)
			)

			private fun ironDefaults(): DepositDefaults = DepositDefaults(
				small = tierDefaults(size = 8, rarity = 2500, y = -64..72, lobes = 1..2, spread = 1),
				medium = tierDefaults(size = 11, rarity = 5000, y = -24..56, lobes = 3..5, spread = 2),
				large = tierDefaults(size = 13, rarity = 7500, y = 80..384, lobes = 6..9, spread = 2)
			)

			private fun copperDefaults(): DepositDefaults = DepositDefaults(
				small = tierDefaults(size = 7, rarity = 2500, y = -16..112, lobes = 1..2, spread = 1),
				medium = tierDefaults(size = 10, rarity = 5000, y = -16..112, lobes = 3..5, spread = 2),
				large = tierDefaults(size = 17, rarity = 7500, y = -16..112, lobes = 6..9, spread = 2)
			)


			private fun quartzDefaults(): DepositDefaults = DepositDefaults(
				small = tierDefaults(size = 6, rarity = 2500, y = -84..300, lobes = 1..2, spread = 1),
				medium = tierDefaults(size = 8, rarity = 5000, y = -84..300, lobes = 2..4, spread = 2),
				large = tierDefaults(size = 10, rarity = 7500, y = -84..300, lobes = 4..6, spread = 2)
			)

			private fun lapisDefaults(): DepositDefaults = DepositDefaults(
				small = tierDefaults(size = 6, rarity = 2500, y = -64..64, lobes = 1..2, spread = 1),
				medium = tierDefaults(size = 8, rarity = 5000, y = -32..32, lobes = 2..4, spread = 2),
				large = tierDefaults(size = 10, rarity = 7500, y = -64..64, lobes = 4..6, spread = 2)
			)

			private fun redstoneDefaults(): DepositDefaults = DepositDefaults(
				small = tierDefaults(size = 8, rarity = 2500, y = -64..15, lobes = 1..2, spread = 1),
				medium = tierDefaults(size = 10, rarity = 5000, y = -64..15, lobes = 3..5, spread = 2),
				large = tierDefaults(size = 12, rarity = 7500, y = -64..15, lobes = 6..9, spread = 2)
			)

			private fun goldDefaults(): DepositDefaults = DepositDefaults(
				small = tierDefaults(size = 5, rarity = 5000, y = -64..-48, lobes = 1..2, spread = 1),
				medium = tierDefaults(size = 7, rarity = 7500, y = -64..32, lobes = 2..3, spread = 2),
				large = tierDefaults(size = 9, rarity = 12500, y = -64..32, lobes = 3..4, spread = 2)
			)

			private fun netheriteDefaults(): DepositDefaults = DepositDefaults(
				small = tierDefaults(size = 3, rarity = 30000, y = -64..16, lobes = 1..1, spread = 1),
				medium = tierDefaults(size = 5, rarity = 60000, y = -64..-4, lobes = 1..1, spread = 1),
				large = tierDefaults(size = 6, rarity = 120000, y = -64..16, lobes = 1..1, spread = 1)
			)
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
