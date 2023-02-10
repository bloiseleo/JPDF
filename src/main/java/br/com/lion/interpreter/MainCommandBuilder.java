package br.com.lion.interpreter;

import java.util.ArrayList;
import java.util.HashMap;

public class MainCommandBuilder {
    public static HashMap<String, Class<? extends  CommandHandler>> generate(
            ArrayList<Class<? extends  CommandHandler>> commandsDesired
    ) {
        HashMap<String, Class<? extends CommandHandler>> result = new HashMap<>();
        for (int i = 0; i < commandsDesired.size(); i++) {
            Class<? extends CommandHandler> commandClass = commandsDesired.get(i);
            result.put(
                    CommandNameGetter.getNameOf(commandClass), commandClass
            );
        }
        return result;
    }
}
