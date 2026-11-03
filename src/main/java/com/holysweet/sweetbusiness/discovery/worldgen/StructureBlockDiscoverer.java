package com.holysweet.sweetbusiness.discovery.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StructureBlockDiscoverer {

    private StructureBlockDiscoverer() {}

    public record StructureSource(ResourceLocation structureId, Optional<ResourceLocation> poolId,
                                  ResourceLocation templateId, Optional<ResourceLocation> processorListId) {}

    public record DiscoveryResult(Map<Block, Set<StructureSource>> sources,
                                  Map<ResourceLocation, Set<String>> failures) {
        public DiscoveryResult {
            Map<Block, Set<StructureSource>> sourceSnapshot = new HashMap<>();
            sources.forEach((block, entries) -> sourceSnapshot.put(block, Set.copyOf(entries)));
            sources = Map.copyOf(sourceSnapshot);
            Map<ResourceLocation, Set<String>> failureSnapshot = new HashMap<>();
            failures.forEach((id, reasons) -> failureSnapshot.put(id, Set.copyOf(reasons)));
            failures = Map.copyOf(failureSnapshot);
        }
    }

    private record TemplateData(Set<BlockState> states, Set<ResourceLocation> childPools) {}
    private record PoolVisit(JsonElement reference, int depth, boolean fallback) {}

    public static DiscoveryResult discoverBlocks(MinecraftServer server) {
        Map<Block, Set<StructureSource>> sources = new HashMap<>();
        Map<ResourceLocation, Set<String>> failures = new HashMap<>();
        Map<ResourceLocation, TemplateData> templates = new HashMap<>();
        Set<ResourceLocation> visited = new HashSet<>();
        var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);

        // Use structure sets available to the loaded dimensions, not every registered template or pool.
        for (var level : server.getAllLevels()) {
            for (var set : level.getChunkSource().getGeneratorState().possibleStructureSets()) {
                for (var entry : set.value().structures()) {
                    if (entry.weight() <= 0) continue;
                    Structure structure = entry.structure().value();
                    ResourceLocation id = structures.getKey(structure);
                    if (id == null || !visited.add(id)) continue;
                    StructureDiscovery discovery = new StructureDiscovery(server, id, sources, templates);
                    discovery.discover(structure);
                    if (!discovery.failures.isEmpty()) failures.put(id, discovery.failures);
                }
            }
        }
        return new DiscoveryResult(sources, failures);
    }

    private static final class StructureDiscovery {
        private final MinecraftServer server;
        private final ResourceLocation structureId;
        private final DynamicOps<JsonElement> ops;
        private final Map<Block, Set<StructureSource>> sources;
        private final Map<ResourceLocation, TemplateData> templates;
        private final Set<String> failures = new HashSet<>();
        private final ArrayDeque<PoolVisit> pending = new ArrayDeque<>();
        private final Set<PoolVisit> visited = new HashSet<>();
        private int maxDepth;

        private StructureDiscovery(MinecraftServer server, ResourceLocation structureId,
                                   Map<Block, Set<StructureSource>> sources, Map<ResourceLocation, TemplateData> templates) {
            this.server = server;
            this.structureId = structureId;
            this.ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            this.sources = sources;
            this.templates = templates;
        }

        private void discover(Structure structure) {
            if (!(structure instanceof JigsawStructure)) {
                failures.add("Unsupported non-jigsaw structure: " + structure.getClass().getName());
                return;
            }
            try {
                JsonObject definition = Structure.DIRECT_CODEC.encodeStart(ops, structure).getOrThrow().getAsJsonObject();
                maxDepth = definition.get("size").getAsInt();
                if (definition.has("pool_aliases") && !definition.getAsJsonArray("pool_aliases").isEmpty()) {
                    failures.add("Pool aliases are not supported; aliased branches may be incomplete");
                }
                pending.add(new PoolVisit(definition.get("start_pool"), 0, false));
                while (!pending.isEmpty()) {
                    PoolVisit visit = pending.removeFirst();
                    if (!visited.add(visit)) continue;
                    try {
                        discoverPool(visit);
                    } catch (RuntimeException | LinkageError exception) {
                        fail("Pool " + visit.reference(), exception);
                    }
                }
            } catch (RuntimeException | LinkageError exception) {
                fail("Structure " + structureId, exception);
            }
        }

        private void discoverPool(PoolVisit visit) {
            Optional<ResourceLocation> poolId = referenceId(visit.reference());
            if (poolId.filter(id -> id.equals(ResourceLocation.withDefaultNamespace("empty"))).isPresent()) return;
            JsonObject definition;
            if (poolId.isPresent()) {
                var pool = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
                        .getOptional(poolId.get()).orElseThrow(() -> new IllegalStateException("Missing pool: " + poolId.get()));
                definition = StructureTemplatePool.DIRECT_CODEC.encodeStart(ops, pool).getOrThrow().getAsJsonObject();
            } else {
                definition = visit.reference().getAsJsonObject();
            }

            // At the depth limit, jigsaw expansion can use fallback pieces but not ordinary child pieces.
            boolean allowElements = visit.depth() == 0 || visit.depth() < maxDepth || visit.fallback();
            if (allowElements) {
                for (JsonElement element : definition.getAsJsonArray("elements")) {
                    JsonObject weighted = element.getAsJsonObject();
                    if (weighted.get("weight").getAsInt() <= 0) continue;
                    try {
                        discoverElement(weighted.getAsJsonObject("element"), poolId.orElse(null), visit.depth());
                    } catch (RuntimeException | LinkageError exception) {
                        fail("Element in pool " + poolId, exception);
                    }
                }
            }
            pending.add(new PoolVisit(definition.get("fallback"), visit.depth(), true));
        }

        private void discoverElement(JsonObject element, @Nullable ResourceLocation poolId, int depth) {
            String type = element.get("element_type").getAsString();
            switch (type) {
                case "minecraft:single_pool_element", "minecraft:legacy_single_pool_element" ->
                        discoverTemplate(element, poolId, depth);
                case "minecraft:list_pool_element" -> element.getAsJsonArray("elements")
                        .forEach(child -> discoverElement(child.getAsJsonObject(), poolId, depth));
                case "minecraft:empty_pool_element" -> { }
                default -> failures.add("Unsupported structure pool element: " + type);
            }
        }

        private void discoverTemplate(JsonObject element, @Nullable ResourceLocation poolId, int depth) {
            ResourceLocation templateId = ResourceLocation.parse(element.get("location").getAsString());
            TemplateData template = templates.computeIfAbsent(templateId, id -> readTemplate(server, id));
            JsonElement processorReference = element.get("processors");
            Optional<ResourceLocation> processorId = referenceId(processorReference);
            JsonElement processors = processorReference;
            if (processorId.isPresent()) {
                var list = server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST)
                        .getOptional(processorId.get())
                        .orElseThrow(() -> new IllegalStateException("Missing processor list: " + processorId.get()));
                processors = StructureProcessorType.DIRECT_CODEC.encodeStart(ops, list).getOrThrow();
            }
            var processed = StructureProcessorDiscoverer.discoverOutputs(template.states(), processors, ops);
            processed.failures().forEach(reason -> failures.add("Template " + templateId + ": " + reason));
            StructureSource source = new StructureSource(structureId, Optional.ofNullable(poolId), templateId, processorId);
            processed.states().forEach(state -> {
                if (!state.isAir() && !state.is(Blocks.JIGSAW) && !state.is(Blocks.STRUCTURE_BLOCK)) {
                    sources.computeIfAbsent(state.getBlock(), key -> new HashSet<>()).add(source);
                }
            });
            if (depth < maxDepth) {
                template.childPools().forEach(pool -> pending.add(new PoolVisit(new JsonPrimitive(pool.toString()),
                        depth + 1, false)));
            }
        }

        private void fail(String source, Throwable exception) {
            failures.add(source + ": " + exception.getClass().getName() + ": " + exception.getMessage());
        }
    }

    private static Optional<ResourceLocation> referenceId(JsonElement reference) {
        return reference.isJsonPrimitive()
                ? Optional.of(ResourceLocation.parse(reference.getAsString())) : Optional.empty();
    }

    private static TemplateData readTemplate(MinecraftServer server, ResourceLocation id) {
        var template = server.getStructureManager().get(id)
                .orElseThrow(() -> new IllegalStateException("Missing structure template: " + id));
        // Saving to an in-memory tag exposes every palette without selecting one randomly or placing blocks.
        CompoundTag data = template.save(new CompoundTag());
        ListTag blocks = data.getList("blocks", Tag.TAG_COMPOUND);
        Set<BlockState> states = new HashSet<>();
        if (data.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = data.getList("palettes", Tag.TAG_LIST);
            for (int i = 0; i < palettes.size(); i++) collectStates(palettes.getList(i), blocks, states);
        } else {
            collectStates(data.getList("palette", Tag.TAG_COMPOUND), blocks, states);
        }
        Set<ResourceLocation> childPools = new HashSet<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag nbt = blocks.getCompound(i).getCompound("nbt");
            if (nbt.contains("pool", Tag.TAG_STRING)) childPools.add(ResourceLocation.parse(nbt.getString("pool")));
        }
        return new TemplateData(Set.copyOf(states), Set.copyOf(childPools));
    }

    private static void collectStates(ListTag palette, ListTag blocks, Set<BlockState> states) {
        for (int i = 0; i < blocks.size(); i++) {
            int index = blocks.getCompound(i).getInt("state");
            if (index < 0 || index >= palette.size()) throw new IllegalStateException("Invalid template palette index: " + index);
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(index)));
        }
    }
}
