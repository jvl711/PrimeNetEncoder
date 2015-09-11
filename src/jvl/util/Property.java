package jvl.util;

import java.util.StringTokenizer;
import java.io.*;
import java.util.*;

/**
 * This class is used to store and retrieve property names and property values in text file.
 * It allows for single line comments which are identified by // followed by
 * the comment.  Values are stored with the property name then "=" followed by the value. 
 * Each identifier and value is stored on a single line.  
 * 
 * 
 * @author Joshua Lewis
 * @version 2.0, 10/30/03
 * @since JDK1.4.1
 */
public class Property
{
    /** A reference to the property file */
    File file;
    HashMap map;
    
    /** Creates a new instance of propObject
     * <PRE> 
     * PRE: Pass a file object that is not null
     * POST: If the file exist it creates an instance of propObject.
     *       If the file does not exist it throws FileNotFoundException
     * </PRE>
     * @throws java.io.FileNotFoundException  
     * @param file the file where the properties are stored
     */
    public Property(File file)throws java.io.IOException
    {
        if(!file.exists())
        {
            file.createNewFile();
        }
        
	this.file = file;
        
        map = new HashMap();
        loadMap();
    }
    
    /** Creates a new instance of propObject
     * <PRE> 
     * PRE: Instance of propObject has been created
     * POST: Creates a hashmap of the property file
     * </PRE>
     * @throws java.io.IOException 
    */
    private void loadMap()
    {
        StringTokenizer tokenizer;
        String temp;
        String key;
        String value;
        
        try
        {
            BufferedReader ins = new BufferedReader( new FileReader(file.getPath()));
            temp = ins.readLine();
        
            while(temp != null)
            {
                //Check to see if line is a comment or line is empty
                if(temp.trim().length() > 0)
                {
            
                    if(!(temp.trim().charAt(0) == '/' && temp.trim().charAt(1) == '/'))
                    {
                        tokenizer = new StringTokenizer(temp,"=");
                        key = tokenizer.nextToken().trim().toLowerCase();
                        
                        if(tokenizer.hasMoreTokens())
                        {
                            value = tokenizer.nextToken();
                            map.put(key, value);
                        }
                        
                    }
                    
                }
                
                temp = ins.readLine();
                
            }
        }
        catch(IOException e)
        {
            //Do nothing... just wont load the cache
        }
    }
    
    
    /** Retrives the property from the property value
     * <PRE> 
     * PRE: propName is not null or emptyString.
     * </PRE> 
     * @return Returns value if there was a key and value present, and the value 
     * was able to be parsed to an integer.  If there is no value present it 
     * returns defaultValue and writes the default value out to file.  If the 
     * property name does no exist it rights the property and value onto the 
     * end of the file and retunrs defaultValue. If the value returned is not parsable
     * it returns defaultValue and sets the property equal to the default value
     *   
     * @throws java.io.IOException 
     * @param propName is the key for the property.
     * @param defaultValue is the value used if there is no value present.
     */
    public int getProperty(String propName, int defaultValue)throws java.io.IOException
    {
        String value;
        
        value = getProperty(propName,"" + defaultValue);
        
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch(NumberFormatException e)
        {
            //value returned was not an integer ... reset value
            setProperty(propName,  "" + defaultValue);
            return defaultValue;
        }
    }
    
    /** Retrives the property from the property value
     * <PRE> 
     * PRE: propName is not null or emptyString.
     * </PRE> 
     * @return Returns value if there was a key and value present, and the value 
     * was able to be parsed to an double.  If there is no value present it 
     * returns defaultValue and writes the default value out to file.  If the 
     * property name does no exist it rights the property and value onto the 
     * end of the file and retunrs defaultValue. If the value returned is not parsable
     * it returns defaultValue and sets the property equal to the default value
     *   
     * @throws java.io.IOException 
     * @param propName is the key for the property.
     * @param defaultValue is the value used if there is no value present.
     */
    public double getProperty(String propName, double defaultValue)throws java.io.IOException
    {
        String value;
        
        value = getProperty(propName,"" + defaultValue);
        
        try
        {
            return Double.parseDouble(value);
        }
        catch(NumberFormatException e)
        {
            //value returned was not an integer ... reset value
            setProperty(propName,  "" + defaultValue);
            return defaultValue;
        }
    }
    
    public boolean getProperty(String propName, boolean defaultValue)throws java.io.IOException
    {
        String value;
        value = getProperty(propName,"" + defaultValue);
        
        try
        {
            return Boolean.parseBoolean(value);
        }
        catch(Exception e)
        {
            //value returned was not an integer ... reset value
            setProperty(propName,  "" + defaultValue);
            return defaultValue;
        }
    }
    
    /** Retrives the property from the property value
     * <PRE> 
     * PRE: propName is not null or emptyString.
     * </PRE> 
     * @return Returns value if there was a key and value present.
     * If there is no value present it returns defaultValue and writes
     * the default value out to file.  If the property name does no exist
     * it rights the property and value onto the end of the file and retunrs
     * defaultValue
     *   
     * @throws java.io.IOException 
     * @param propName is the key for the property.
     * @param defaultValue is the value used if there is no value present.
     */
    public String getProperty(String propName, String defaultValue) throws java.io.IOException
    {
        String temp="";
        String temp2="";
        StringTokenizer tokenizer;
        boolean propFound = false;
        String hashMapValue;
        
        /* Check to see if the property is in the hashmap
         *if it is return value and exit.
         */
        hashMapValue = (String)map.get(propName.toLowerCase());
        
        if(hashMapValue != null)
        {
            return hashMapValue;
        }
        
        BufferedReader ins = new BufferedReader( new FileReader(file.getPath()));
        temp = ins.readLine();
        
        while(temp != null)
        {
            //Check to see if line is a comment or line is empty
            if(temp.trim().length() > 0)
            {
                if(!((int)temp.trim().charAt(0) == (int)'/' && (int)temp.trim().charAt(0) == (int)'/'))
                {
                    tokenizer = new StringTokenizer(temp,"=");
                    temp2 = tokenizer.nextToken().trim();
                    if (propName.toLowerCase().compareTo(temp2.toLowerCase()) == 0)//Property was found
                    {
                        propFound = true;
                        try
                        {
                            temp2 = tokenizer.nextToken();
                        }
                        catch(java.util.NoSuchElementException e)
                        {
                            //No value for the property ... Set value with default
                            //and return it.
                            ins.close();
                            this.setProperty(propName, defaultValue);
                            return defaultValue;
                        }
                        temp = null;  //ends loop, because value found
                        ins.close();
                    }
                    else
                    {
                        temp = ins.readLine();
                    }
                }
                else
                {
                    //print the comment
                    temp = ins.readLine();
                }
            }
            else
            {
                //print the line is blank
                temp = ins.readLine();
            }
        }
        
        ins.close();
        
        //the property was not found
        //add it to the end
        if (propFound == false)
        {
            addProp(propName, defaultValue);
            return (defaultValue);    
        }
        
        return (temp2);
        
    }
    
    /** Set the value of a property in the file
     * <PRE> 
     * PRE: propName is not null or emptyString
     * POST: places value in property file if property name is in the file
     *       if the propery name is not in the file than it adds the property 
     *       name and value to the file.
     * </PRE>
     * @throws java.io.IOException  
     * @param file the file where the properties are stored
     */
    public void setProperty(String propName, String propValue) throws java.io.IOException
    {
    String temp="", temp2="";
    StringTokenizer tokenizer;
    boolean propFound = false;
    Vector properties = new Vector();
        
        /* 
         * Adds new value to the hashMap
         */
        map.put(propName, propValue);

            BufferedReader ins = new BufferedReader( new FileReader(file.getPath()));


            temp = ins.readLine();
            while(temp != null)
            {
                properties.add(temp);
                temp = ins.readLine();
            }
         
            ins.close();
            
            PrintWriter out = new PrintWriter(new FileWriter(file.getPath(),false));

            for(int i = 0; i < properties.size(); i++)
            {   
                temp = (String)properties.get(i);
                //Check to see if line is a comment or line is empty
                if(temp.trim().length() > 0)
                {
                    if(!((int)temp.trim().charAt(0) == (int)'/' && (int)temp.trim().charAt(0) == (int)'/'))
                    {
                        tokenizer = new StringTokenizer((String)properties.get(i),"=");
                        temp2 = tokenizer.nextToken();
                        if (propName.toLowerCase().compareTo(temp2.toLowerCase()) == 0)
                        {
                            //right out new prop value
                            propFound = true;
                            out.println(propName + "=" + propValue);
                        }
                        else
                        {   
                            //print out property
                            out.println(temp);
                        }
                    }
                    else
                    {
                        //Commnet
                        out.println(temp);
                    }
                }
                else
                {
                    //blank line
                    out.println(temp);
                }
            }
            
            out.close();
            
            if (propFound == false)
            {
                //prop was not found
                addProp(propName, propValue);
            }
    }
    
    /** Adds a new property to file
     * <PRE> 
     * PRE: propName is not null or emptyString
     * POST: adds property name and value to file
     * </PRE> 
     * @throws java.io.IOException
     * @param file the file where the properties are stored
     */
    public void addProp(String propName, String propValue) throws java.io.IOException
    {
        boolean hasReturn = isLastLineTerminated();
        
        /* 
         * Adds new value to the hashMap
         */
        map.put(propName, propValue);
        
        PrintWriter out = new PrintWriter(new FileWriter(file.getPath(),true));
        
        //TODO:  Verify the file ends in a return before adding new property
        
        //Add a return if the last line is not properly terminated.
        if(!hasReturn)
        {
            out.println();
        }
        
        out.println(propName + "=" + propValue);
        out.flush();
        out.close();
    }
    
    /**
     * Internal method for verifying the last line has a return character.
     * @return true if '\r' or '\n' is at the end of the file
     */
    private boolean isLastLineTerminated()
    {
        try
        {
            int temp;
            char last = ' ';
            BufferedReader reader = new BufferedReader(new FileReader(file));
            
            while((temp = reader.read()) != -1)
            {
                last = (char)temp;
            }
                
            return (last == '\r' || last == '\n');
        }
        catch(Exception ex)
        {
            //Eat the exception
            return false;
        }
    }
    
}
