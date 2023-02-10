package br.com.lion.interpreter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * This class represents the basic command you put into terminal.
 * You will define the name of the command through constructor.
 * When you extends this class, you call super method passing to it
 * the name of it.
 */
public abstract class CommandHandler {
    private final String commandName;
    private HashMap<String, String> params = null;

    public CommandHandler(String commandName) {
        this.commandName = commandName;
    }

    public CommandHandler(String commandName, HashMap<String, String> params) {
        this.params = params;
        this.commandName = commandName;
    }

    /**
     * Everything that extends from this class should define an implementation for it.
     * It will be called to execute the command itself.
     * @return boolean
     */
    public abstract boolean exec() throws IOException;
    public String getCommandName() {
        return this.commandName;
    }

    protected String getParam(String key) {
        String result = this.params.get(key);
        if(result == null) {
            throw new IllegalArgumentException("For " + this.getCommandName() + " you need to specify the param: " + key);
        }
        return result;
    }

    protected List<String> getAllParams(String ignore) {
        List<String> allParams = new ArrayList<>();
        Set<String> keySet = this.params.keySet();
        for(String key : keySet) {
            if (ignore == key) {
                continue;
            }
            allParams.add(this.params.get(key));
        }
        return allParams;
    }
}
