package br.com.lion.interpreter;

import java.lang.reflect.InvocationTargetException;

public class CommandNameGetter {

    public static String getNameOf(Class<? extends CommandHandler> commandHandlerClass) {
        try {
            return commandHandlerClass.getDeclaredConstructor().newInstance().getCommandName();
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException  e) {
            throw new RuntimeException(e);
        }
    }
}
