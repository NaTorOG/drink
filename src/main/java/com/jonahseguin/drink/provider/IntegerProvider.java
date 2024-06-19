package com.jonahseguin.drink.provider;

import com.jonahseguin.drink.argument.CommandArg;
import com.jonahseguin.drink.exception.CommandExitMessage;
import com.jonahseguin.drink.parametric.DrinkProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.List;

import static com.jonahseguin.drink.command.DrinkCommandService.providerMessages;

public class IntegerProvider extends DrinkProvider<Integer> {

    public static final IntegerProvider INSTANCE = new IntegerProvider();

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
    public Integer defaultNullValue() {
        return 0;
    }

    @Override
    @Nullable
    public Integer provide(@Nonnull CommandArg arg, @Nonnull List<? extends Annotation> annotations) throws CommandExitMessage {
        String s = arg.get();
        try {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException ex) {
            final String message = (providerMessages.containsKey(ProviderMessage.INTEGER))
                    ? providerMessages.get(ProviderMessage.INTEGER)
                    : ProviderMessage.INTEGER.msg();
            throw new CommandExitMessage(message);
        }
    }

    @Override
    public String argumentDescription() {
        return "integer";
    }

}
