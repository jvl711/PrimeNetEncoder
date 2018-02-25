
package jvl.primenetencoder;


public class PathMapping 
{

    private String inputPathPrefix;
    private String newPathPrefix;
    
    public PathMapping(String inputPathPrefix, String newPathPrefix)
    {
        this.inputPathPrefix = inputPathPrefix;
        this.newPathPrefix = newPathPrefix;
    }
    
    public String getConvertedPath(String input)
    {
        return input.replace(inputPathPrefix, newPathPrefix);
    }
    
    public boolean matches(String input)
    {
        return input.startsWith(this.inputPathPrefix);
    }
}
