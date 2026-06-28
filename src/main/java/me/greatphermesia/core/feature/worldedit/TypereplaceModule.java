package me.greatphermesia.core.feature.worldedit;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.noise.PerlinNoise;
import com.sk89q.worldedit.regions.Region;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.greatphermesia.core.PhermesiaCorePlugin;
import me.greatphermesia.core.module.PluginModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class TypereplaceModule implements PluginModule {

    private static final String PERLIN_PREFIX = "#perlin[";
    private static final List<String> BLOCK_SUGGESTIONS = Arrays.stream(Material.values())
            .filter(Material::isBlock)
            .map(material -> material.getKey().getKey())
            .sorted()
            .toList();
    private static final Component PREFIX = Component.text("[Typereplace] ", NamedTextColor.GOLD);

    private final PhermesiaCorePlugin plugin;

    public TypereplaceModule(PhermesiaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "Typereplace";
    }

    @Override
    public void enable() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            LiteralCommandNode<CommandSourceStack> node = buildCommandNode();
            event.registrar().register(node, "Replace matching block states inside the current WorldEdit selection.");
            refreshClientCommands();
        });

        plugin.getLogger().info("[Typereplace] Registered Brigadier command handler for //typereplace.");
    }

    @Override
    public void disable() {
        refreshClientCommands();

        plugin.getLogger().info("[Typereplace] Module stopped.");
    }

    private void refreshClientCommands() {
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }

    private LiteralCommandNode<CommandSourceStack> buildCommandNode() {
        return Commands.literal("/typereplace")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    sendUsage(context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("from", StringArgumentType.string())
                        .suggests(this::suggestSourceMasks)
                        .executes(context -> {
                            sendUsage(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                    .then(Commands.argument("to", StringArgumentType.greedyString())
                                .suggests(this::suggestTargetMaterials)
                                .executes(this::executeReplaceCommand)))
                .build();
    }

    private CompletableFuture<Suggestions> suggestSourceMasks(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            return builder.buildFuture();
        }

        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        ParserContext parserContext = createParserContext(player, session);
        WorldEdit.getInstance().getMaskFactory().getSuggestions(builder.getRemaining(), parserContext).stream()
                .map(TypereplaceModule::stripMinecraftNamespace)
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestTargetMaterials(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String lower = remaining.toLowerCase(Locale.ROOT);

        if (lower.startsWith(PERLIN_PREFIX)) {
            String token = remaining.substring(PERLIN_PREFIX.length());
            int closeBracket = token.indexOf(']');
            if (closeBracket < 0) {
                suggestPerlinSeeds(builder, token);
            } else if (token.length() > closeBracket + 1 && token.charAt(closeBracket + 1) == '[') {
                String blockToken = token.substring(closeBracket + 2);
                completeMaterials(blockToken).forEach(material -> builder.suggest("#perlin[0][" + material + "]"));
            } else {
                builder.suggest("#perlin[0][stone]");
            }
            return builder.buildFuture();
        }

        if (lower.isBlank() || "#perlin".startsWith(lower)) {
            builder.suggest("#perlin[0][stone]");
        }

        completeMaterials(remaining).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private int executeReplaceCommand(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixed(Component.text("This command can only be used in-game.", NamedTextColor.RED)));
            return Command.SINGLE_SUCCESS;
        }

        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(player.getWorld());
        ParserContext parserContext = createParserContext(player, session);

        String fromRaw = StringArgumentType.getString(context, "from").trim();
        String toRaw = StringArgumentType.getString(context, "to").trim();

        if (fromRaw.isEmpty() || toRaw.isEmpty()) {
            sendUsage(sender);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
            return Command.SINGLE_SUCCESS;
        }

        Mask sourceMask;
        try {
            sourceMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(fromRaw, parserContext);
        } catch (InputParseException ex) {
            player.sendMessage(prefixed(Component.text(errorMessage(ex, "That mask could not be parsed."), NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
            return Command.SINGLE_SUCCESS;
        }

        TargetSelection targetSelection;
        try {
            targetSelection = parseTargetSelection(toRaw);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(prefixed(Component.text(errorMessage(ex, "Please use valid block types for the target list."), NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
            return Command.SINGLE_SUCCESS;
        }

        runReplace(player, world, session, sourceMask, targetSelection);
        return Command.SINGLE_SUCCESS;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(prefixed(Component.text("Usage: /typereplace <block(s)> <block(s)|#perlin[integer][block(s)]>", NamedTextColor.YELLOW)));
    }

    private void runReplace(Player player, com.sk89q.worldedit.world.World world, LocalSession session, Mask sourceMask, TargetSelection targetSelection) {
        Region region;
        try {
            region = session.getSelection(world);
        } catch (IncompleteRegionException ex) {
            player.sendMessage(prefixed(Component.text("Select a WorldEdit region first.", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
            return;
        }

        PerlinNoise perlinNoise = targetSelection.perlin() ? createPerlinNoise(targetSelection.seed()) : null;
        int replaced = 0;
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(world).build()) {
            try {
                for (BlockVector3 position : region) {
                    if (!sourceMask.test(position)) {
                        continue;
                    }

                    BlockData currentData = player.getWorld().getBlockAt(position.x(), position.y(), position.z()).getBlockData();
                    BlockData replacement = targetSelection.pick(position, perlinNoise).createBlockData();
                    currentData.copyTo(replacement);
                    editSession.setBlock(position, BukkitAdapter.adapt(replacement));
                    replaced++;
                }
            } finally {
                if (replaced > 0) {
                    session.remember(editSession);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[Typereplace] Failed for " + player.getName() + ": " + ex.getMessage());
            player.sendMessage(prefixed(Component.text("Typereplace failed. Check the console for details.", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
            return;
        }

        if (replaced == 0) {
            player.sendMessage(prefixed(Component.text("No blocks matched that mask in the selection.", NamedTextColor.YELLOW)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
            return;
        }

        player.sendMessage(prefixed(Component.text("Replaced ", NamedTextColor.GREEN)
                .append(Component.text(Integer.toString(replaced), NamedTextColor.WHITE))
                .append(Component.text(" ", NamedTextColor.GREEN))
                .append(Component.text("blocks with ", NamedTextColor.GREEN))
                .append(Component.text(targetSelection.describe(), targetSelection.perlin() ? NamedTextColor.AQUA : NamedTextColor.GOLD))
                .append(Component.text(".", NamedTextColor.GREEN))));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.2f);
    }

    private static Component prefixed(Component body) {
        return PREFIX.append(body);
    }

    private List<String> completeMaterials(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return BLOCK_SUGGESTIONS.stream()
                .filter(materialName -> materialName.startsWith(normalized))
                .toList();
    }

    private ParserContext createParserContext(Player player, LocalSession session) {
        ParserContext context = new ParserContext();
        context.setActor(BukkitAdapter.adapt(player));
        context.setWorld(BukkitAdapter.adapt(player.getWorld()));
        context.setSession(session);
        context.setExtent(BukkitAdapter.adapt(player.getWorld()));
        return context;
    }

    private TargetSelection parseTargetSelection(String token) {
        String normalized = token.trim();
        if (normalized.regionMatches(true, 0, PERLIN_PREFIX, 0, PERLIN_PREFIX.length())) {
            int seedEnd = normalized.indexOf(']', PERLIN_PREFIX.length());
            if (seedEnd < 0) {
                throw new IllegalArgumentException("Missing closing bracket after the perlin integer.");
            }

            String seedText = normalized.substring(PERLIN_PREFIX.length(), seedEnd).trim();
            if (seedText.isEmpty()) {
                throw new IllegalArgumentException("Provide an integer inside #perlin[...].");
            }

            int blocksStart = seedEnd + 1;
            if (blocksStart >= normalized.length() || normalized.charAt(blocksStart) != '[') {
                throw new IllegalArgumentException("Use #perlin[integer][block(s)].");
            }

            if (!normalized.endsWith("]")) {
                throw new IllegalArgumentException("Missing closing bracket after the block list.");
            }

            int seed;
            try {
                seed = Integer.parseInt(seedText);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Perlin integer must be a whole number.");
            }

            String blockText = normalized.substring(blocksStart + 1, normalized.length() - 1).trim();
            List<Material> materials = parseMaterialList(blockText);
            return new TargetSelection(true, seed, materials);
        }

        return new TargetSelection(false, 0, parseMaterialList(normalized));
    }

    private static String stripMinecraftNamespace(String suggestion) {
        return suggestion.startsWith("minecraft:") ? suggestion.substring("minecraft:".length()) : suggestion;
    }

    private static String errorMessage(Throwable throwable, String fallback) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    private static PerlinNoise createPerlinNoise(int seed) {
        PerlinNoise noise = new PerlinNoise();
        noise.setSeed(seed);
        noise.setFrequency(0.08);
        noise.setOctaveCount(4);
        noise.setPersistence(0.5);
        return noise;
    }

    private List<Material> parseMaterialList(String token) {
        if (token.isBlank()) {
            throw new IllegalArgumentException("Provide at least one target block.");
        }

        return Arrays.stream(token.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> {
                    Material material = Material.matchMaterial(entry);
                    if (material == null || !material.isBlock()) {
                        throw new IllegalArgumentException("Invalid target block: " + entry);
                    }
                    return material;
                })
                .toList();
    }

    private void suggestPerlinSeeds(SuggestionsBuilder builder, String token) {
        String seedToken = token;
        if (seedToken.isBlank()) {
            builder.suggest("0][stone]");
            builder.suggest("1][stone]");
            builder.suggest("2][stone]");
            return;
        }

        if (seedToken.chars().allMatch(character -> Character.isDigit(character) || character == '-')) {
            builder.suggest(seedToken + "][stone]");
        }
    }

    private record TargetSelection(boolean perlin, int seed, List<Material> materials) {

        private Material pick(BlockVector3 position, PerlinNoise perlinNoise) {
            if (this.materials.size() == 1) {
                return this.materials.get(0);
            }

            if (!this.perlin) {
                return this.materials.get(ThreadLocalRandom.current().nextInt(this.materials.size()));
            }

            double noiseValue = perlinNoise.noise(position.toVector3());
            int index = (int) Math.floor(noiseValue * this.materials.size());
            if (index >= this.materials.size()) {
                index = this.materials.size() - 1;
            }

            return this.materials.get(index);
        }

        private String describe() {
            String joinedMaterials = String.join(", ", this.materials.stream()
                    .map(material -> material.getKey().getKey())
                    .toList());
            return this.perlin ? "#perlin[" + this.seed + "][" + joinedMaterials + "]" : joinedMaterials;
        }
    }
}