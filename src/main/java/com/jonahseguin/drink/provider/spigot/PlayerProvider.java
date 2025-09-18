package com.jonahseguin.drink.provider.spigot;

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
    public List<String> getSuggestions(@Nonnull String prefix) {
        final String finalPrefix = prefix.toLowerCase();
        
        Collection<? extends Player> onlinePlayers = new CopyOnWriteArrayList<>(
                plugin.getServer().getOnlinePlayers()
        );

        return onlinePlayers.stream()
                .filter(player -> !isVanished(player))
                .map(HumanEntity::getName)
                .filter(s -> finalPrefix.isEmpty() || s.toLowerCase().startsWith(finalPrefix))
                .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
    }

    private boolean isVanished(final @NotNull Player player){
        for(final var metadata : player.getMetadata("vanished")){
            if(metadata.asBoolean()){
                return true;
            }
        }
        return false;
    }

    private @Nullable Player getTarget(final @NotNull String target){
        final Collection<? extends Player> players = Bukkit.getOnlinePlayers();

        for (final Player player : players) {
            if (player.getName().equalsIgnoreCase(target)) {
                return player;
            }
        }

        for (final Player player : players) {
            if (player.getName().toLowerCase().startsWith(target.toLowerCase())) {
                return player;
            }
        }

        for (final Player player : players) {
            if (player.getName().toLowerCase().contains(target.toLowerCase())) {
                return player;
            }
        }

        return null;
    }
}
