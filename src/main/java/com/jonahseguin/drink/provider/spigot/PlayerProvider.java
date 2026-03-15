package com.jonahseguin.drink.provider.spigot;

import com.google.common.base.Strings;
import com.jonahseguin.drink.annotation.OptArg;
import com.jonahseguin.drink.argument.CommandArg;
import com.jonahseguin.drink.exception.CommandExitMessage;
import com.jonahseguin.drink.parametric.DrinkProvider;
import com.jonahseguin.drink.provider.ProviderMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.jonahseguin.drink.command.DrinkCommandService.providerMessages;

public class PlayerProvider extends DrinkProvider<Player> {

    private final Plugin plugin;

    public PlayerProvider(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean doesConsumeArgument() {
        return true;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean allowNullArgument() {
        return false;
    }

    @Nullable
    @Override
    public Player defaultNullValue() {
        return null;
    }

    @Nullable
    @Override
    public Player provide(@Nonnull CommandArg arg, @Nonnull List<? extends Annotation> annotations) throws CommandExitMessage {
        final var sender = arg.getSender();
        final var name = arg.get();
        final var target = getTarget(name);

        if(target == null){
            final String message = (providerMessages.containsKey(ProviderMessage.PLAYER))
                    ? providerMessages.get(ProviderMessage.PLAYER)
                    : ProviderMessage.PLAYER.msg();
            throw new CommandExitMessage(message.replace("%player%", name));
        }

        if(sender instanceof ConsoleCommandSender){
            return target;
        }

        final var player = (Player) sender;
        if(player.isOp() || player.hasPermission("drink.provider.bypassvanished")) {
            return target;
        }

        if(!isVanished(target)) return target;

        final String message = (providerMessages.containsKey(ProviderMessage.PLAYER))
                ? providerMessages.get(ProviderMessage.PLAYER)
                : ProviderMessage.PLAYER.msg();
        throw new CommandExitMessage(message.replace("%player%", name));

    }

    @Override
    public String argumentDescription() {
        return "player";
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<String> getSuggestions(@Nonnull String input) {
        final String finalPrefix = input.toLowerCase();

        if(Strings.isNullOrEmpty(input)) return List.of();
        
        Collection<? extends Player> onlinePlayers = List.copyOf(
                plugin.getServer().getOnlinePlayers()
        );


        return onlinePlayers.stream()
                .filter(player -> !isVanished(player))
                .filter(player -> {
                    var name = player.getName().toLowerCase();
                    var displayName = player.getDisplayName().toLowerCase();
                    return name.startsWith(finalPrefix) || displayName.startsWith(finalPrefix);
                })
                .map(HumanEntity::getName)
                .toList();
    }

    private boolean isVanished(final @NotNull Player player){
        for(final var metadata : player.getMetadata("vanished")){
            if(metadata.asBoolean()){
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private @Nullable Player getTarget(final @NotNull String input){
        final Collection<? extends Player> players = Bukkit.getOnlinePlayers();

        for (final Player player : players) {
            var name = player.getName();
            var displayName = player.getDisplayName();

            return matchesName(name, input) || matchesName(displayName, input) ? player : null;
        }

        return null;
    }

    private boolean matchesName(String name, String input){
        return name.equalsIgnoreCase(input)
                || name.toLowerCase().startsWith(input.toLowerCase())
                || name.toLowerCase().contains(input.toLowerCase());
    }
}
