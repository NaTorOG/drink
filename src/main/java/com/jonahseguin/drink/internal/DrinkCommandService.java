package com.jonahseguin.drink.internal;

import com.google.common.base.Preconditions;
import com.jonahseguin.drink.CommandService;
import com.jonahseguin.drink.annotation.Sender;
import com.jonahseguin.drink.command.*;
import com.jonahseguin.drink.exception.*;
import com.jonahseguin.drink.modifier.ModifierService;
import com.jonahseguin.drink.parametric.*;
import com.jonahseguin.drink.parametric.binder.DrinkBinder;
import com.jonahseguin.drink.provider.*;
import com.jonahseguin.drink.provider.spigot.CommandSenderProvider;
import com.jonahseguin.drink.provider.spigot.PlayerProvider;
import com.jonahseguin.drink.provider.spigot.PlayerSenderProvider;
import lombok.Getter;
import org.apache.logging.log4j.util.Strings;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
public class DrinkCommandService implements CommandService {

    public static String DEFAULT_KEY = "DRINK_DEFAULT";

    private final JavaPlugin plugin;
    private final CommandExtractor extractor;
    private final DrinkHelpService helpService;
    private final ProviderAssigner providerAssigner;
    private final ArgumentParser argumentParser;
    private final ModifierService modifierService;
    private final DrinkSpigotRegistry spigotRegistry;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private DrinkAuthorizer authorizer;

    private final ConcurrentMap<String, DrinkCommandContainer> commands = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, BindingContainer<?>> bindings = new ConcurrentHashMap<>();

    public DrinkCommandService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.extractor = new CommandExtractor(this);
        this.helpService = new DrinkHelpService(this);
        this.providerAssigner = new ProviderAssigner(this);
        this.argumentParser = new ArgumentParser(this);
        this.modifierService = new ModifierService(this);
        this.spigotRegistry = new DrinkSpigotRegistry(this);
        this.authorizer = new DrinkAuthorizer();

        this.bindDefaults();
    }

    private void bindDefaults() {
        bind(Boolean.class).toProvider(BooleanProvider.INSTANCE);
        bind(boolean.class).toProvider(BooleanProvider.INSTANCE);
        bind(Double.class).toProvider(DoubleProvider.INSTANCE);
        bind(double.class).toProvider(DoubleProvider.INSTANCE);
        bind(Integer.class).toProvider(IntegerProvider.INSTANCE);
        bind(int.class).toProvider(IntegerProvider.INSTANCE);
        bind(Long.class).toProvider(LongProvider.INSTANCE);
        bind(long.class).toProvider(LongProvider.INSTANCE);
        bind(String.class).toProvider(StringProvider.INSTANCE);

        bind(CommandSender.class).annotatedWith(Sender.class).toProvider(CommandSenderProvider.INSTANCE);
        bind(Player.class).annotatedWith(Sender.class).toProvider(PlayerSenderProvider.INSTANCE);
        bind(Player.class).toProvider(new PlayerProvider(plugin));
    }

    @Override
    public void setAuthorizer(@Nonnull DrinkAuthorizer authorizer) {
        Preconditions.checkNotNull(authorizer, "Authorizer cannot be null");
        this.authorizer = authorizer;
    }

    @Override
    public void registerCommands() {
        commands.values().forEach(cmd -> {
            spigotRegistry.register(cmd, cmd.isOverrideExistingCommands());
        });
    }

    @Override
    public DrinkCommandContainer register(@Nonnull Object handler, @Nonnull String name, @Nullable String... aliases) throws CommandRegistrationException {
        Preconditions.checkNotNull(handler, "Handler object cannot be null");
        Preconditions.checkNotNull(name, "Name cannot be null");
        Preconditions.checkState(name.length() > 0, "Name cannot be empty (must be > 0 characters in length)");
        Set<String> aliasesSet = new HashSet<>();
        if (aliases != null) {
            aliasesSet.addAll(Arrays.asList(aliases));
            aliasesSet.removeIf(s -> s.length() == 0);
        }
        try {
            Map<String, DrinkCommand> extractCommands = extractor.extractCommands(handler);
            if (extractCommands.isEmpty()) {
                throw new CommandRegistrationException("There were no commands to register in the " + handler.getClass().getSimpleName() + " class (" + extractCommands.size() + ")");
            }
            DrinkCommandContainer container = new DrinkCommandContainer(this, handler, name, aliasesSet, extractCommands);
            commands.put(getCommandKey(name), container);
            return container;
        } catch (MissingProviderException ex) {
            throw new CommandRegistrationException("Could not register command '" + name + "': " + ex.getMessage(), ex);
        }
    }

    @Override
    public DrinkCommandContainer registerSub(@Nonnull String root, @Nonnull Object handler) {
        Preconditions.checkNotNull(root, "Root command name cannot be null");
        Preconditions.checkNotNull(handler, "Handler object cannot be null");
        DrinkCommandContainer rootContainer = get(root);
        if (rootContainer == null) {
            throw new IllegalStateException("Couldn't find root command container to register sub command for root: " + root);
        }
        try {
            Map<String, DrinkCommand> extractCommands = extractor.extractCommands(handler);
            extractCommands.forEach((s, d) -> rootContainer.getCommands().put(s, d));
            return rootContainer;
        } catch (MissingProviderException ex) {
            throw new CommandRegistrationException("Could not register sub-command in root '" + root + "' with handler '" + handler.getClass().getSimpleName() + "': " + ex.getMessage(), ex);
        }
    }

    public void executeCommand(@Nonnull CommandSender sender, @Nonnull DrinkCommand command, @Nonnull String[] args) {
        Preconditions.checkNotNull(sender, "Sender cannot be null");
        Preconditions.checkNotNull(command, "Command cannot be null");
        Preconditions.checkNotNull(args, "Args cannot be null");
        if (authorizer.isAuthorized(sender, command)) {
            if (command.isRequiresAsync()) {
                executor.submit(() -> finishExecution(sender, command, args));
            }
            else {
                finishExecution(sender, command, args);
            }
        }
    }

    private void finishExecution(@Nonnull CommandSender sender, @Nonnull DrinkCommand command, @Nonnull String[] args) {
        CommandArgs commandArgs = new CommandArgs(sender, Arrays.asList(args));
        CommandExecution execution = new CommandExecution(this, sender, args, commandArgs, command);
        try {
            Object[] parsedArguments = argumentParser.parseArguments(execution, command, commandArgs);
            if (!execution.isCanExecute()) {
                return;
            }
            try {
                command.getMethod().invoke(command.getHandler(), parsedArguments);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                sender.sendMessage(ChatColor.RED + "Could not perform command.  Notify an administrator");
                throw new DrinkException("Failed to execute command '" + command.getName() + "' with arguments '" + Strings.join(Arrays.asList(args), ' ') + " for sender " + sender.getName(), ex);
            }
        }
        catch (CommandExitMessage ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
        } catch (CommandArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            helpService.sendUsageMessage(sender, getContainerFor(command), command);
        }
    }

    @Nullable
    public DrinkCommandContainer getContainerFor(@Nonnull DrinkCommand command) {
        Preconditions.checkNotNull(command, "DrinkCommand cannot be null");
        for (DrinkCommandContainer container : commands.values()) {
            if (container.getCommands().containsValue(command)) {
                return container;
            }
        }
        return null;
    }

    @Nullable
    public <T> BindingContainer<T> getBindingsFor(@Nonnull Class<T> type) {
        Preconditions.checkNotNull(type, "Type cannot be null");
        if (bindings.containsKey(type)) {
            return (BindingContainer<T>) bindings.get(type);
        }
        return null;
    }

    @Nullable
    @Override
    public DrinkCommandContainer get(@Nonnull String name) {
        Preconditions.checkNotNull(name, "Name cannot be null");
        return commands.get(getCommandKey(name));
    }

    public String getCommandKey(@Nonnull String name) {
        Preconditions.checkNotNull(name, "Name cannot be null");
        if (name.length() == 0) {
            return DEFAULT_KEY;
        }
        return name.toLowerCase();
    }

    @Override
    public <T> DrinkBinder<T> bind(@Nonnull Class<T> type) {
        Preconditions.checkNotNull(type, "Type cannot be null for bind");
        return new DrinkBinder<>(this, type);
    }

    public <T> void bindProvider(@Nonnull Class<T> type, @Nonnull Set<Class<? extends Annotation>> annotations, @Nonnull DrinkProvider<T> provider) {
        Preconditions.checkNotNull(type, "Type cannot be null");
        Preconditions.checkNotNull(annotations, "Annotations cannot be null");
        Preconditions.checkNotNull(provider, "Provider cannot be null");
        BindingContainer<T> container = getBindingsFor(type);
        if (container == null) {
            container = new BindingContainer<>(type);
            bindings.put(type, container);
        }
        DrinkBinding<T> binding = new DrinkBinding<>(type, annotations, provider);
        container.getBindings().add(binding);
    }

}