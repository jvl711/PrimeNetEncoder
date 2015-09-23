package jvl.io;

import java.io.BufferedOutputStream;
import java.io.OutputStream;

/**
 * Extends BufferedOutputStream to provide access to the amount of the buffer
 * that is used, and the size of the buffer
 * @author jolewis
 */ 
public final class AdvancedOutputStream extends BufferedOutputStream 
{
    
    public AdvancedOutputStream(OutputStream out) 
    {
        this(out, 8192);
    }

    public AdvancedOutputStream(OutputStream out, int size) 
    {
        super(out);
        if (size <= 0) 
        {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        this.buf = new byte[size];
    }
    
    /**
     * Gets the number of bytes currently in the buffer
     * @return 
     */
    public int getCount()
    {
        return this.count;
    }

    /**
     * Gets the size of the buffer
     * @return 
     */
    public int getLimit()
    {
        return this.buf.length;
    }
}