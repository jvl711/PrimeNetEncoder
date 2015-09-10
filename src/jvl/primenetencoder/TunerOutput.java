/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jvl.primenetencoder;

import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;

/**
 * This class is responsible for transferring the stream data from the source
 * and destination.  It is meant to handle multiple sources and destinations
 * 
 */
public class TunerOutput extends Thread
{
    private static final int DEFAULT_MEDIA_BUFFER_SIZE = 8096;
    private static final int DEFAULT_UDP_BUFFER_SIZE = 8096;
    
    private final String logName;
    private final InputStream processOutput;
    private final OutputStream processInput;
    private final String fileNamePath;
    private final String uploadID;
    
    private final boolean useMediaServer;
    private final boolean useDirectStream;
    private final boolean useSTDINStream;
    
    private boolean keepProcessing;
    private BufferedInputStream input;
    private String mediaServerIPAddress;
    private long fileSize;
    private static int writeBufferSize = DEFAULT_MEDIA_BUFFER_SIZE;
    private int updPort;
    
    private TunerOutputWatcher outputWatcher;
    private TunerBridge tunerBridge;
    
    //Write data directly to file
    public TunerOutput(InputStream processOutput, String fileNamePath, String logName)
    {
        this.processOutput = processOutput;
        this.processInput = null;
        this.fileNamePath = fileNamePath;
        this.logName = logName;
        this.keepProcessing = true;
        this.useMediaServer = false;
        this.useDirectStream = false;
        this.useSTDINStream = false;
        this.fileSize = 0;
        this.uploadID = "";
        
        this.outputWatcher = new TunerOutputWatcher(this);
        
        PrimeNetEncoder.writeLogln("Tuner output thread constructed for UploadID: " + uploadID, this.logName);
        PrimeNetEncoder.writeLogln("Tuner output thread HDHomeRun(UDP) -> ffmpeg(STDOUT) -> PrimeNetEncoder -> File(CIFS/SMB)", this.logName);
    }
    
    //Write data to Media Server
    public TunerOutput(InputStream processOutput, String fileNamePath, String logName, String uploadID, String mediaServerIPAddress)
    {
        this.processOutput = processOutput;
        this.processInput = null;
        this.uploadID = uploadID;
        this.fileNamePath = fileNamePath;
        this.logName = logName;
        this.keepProcessing = true;
        this.useMediaServer = true;
        this.useDirectStream = false;
        this.useSTDINStream = false;
        this.mediaServerIPAddress = mediaServerIPAddress;
        this.fileSize = 0;
        
        this.outputWatcher = new TunerOutputWatcher(this);
        
        PrimeNetEncoder.writeLogln("Tuner output thread constructed for UploadID: " + uploadID, this.logName);
        PrimeNetEncoder.writeLogln("Tuner output thread HDHomeRun(UDP) -> ffmpeg(STDOUT) -> PrimeNetEncoder -> SageTV Media Server(TCP)", this.logName);
    }
    
    //Direct write to from HDHomeRun to SageTV Media Server
    public TunerOutput(int udpPort, String fileNamePath, String logName, String uploadID, String mediaServerIPAddress)
    {
        this.uploadID = uploadID;
        this.fileNamePath = fileNamePath;
        this.logName = logName;
        this.keepProcessing = true;
        this.useMediaServer = true;
        this.useDirectStream = true;
        this.useSTDINStream = false;
        this.mediaServerIPAddress = mediaServerIPAddress;
        this.fileSize = 0;
        this.processOutput = null;
        this.processInput = null;
        this.updPort = udpPort;
        
        this.outputWatcher = new TunerOutputWatcher(this);
        
        PrimeNetEncoder.writeLogln("Tuner output thread constructed for UploadID: " + uploadID, this.logName);
        PrimeNetEncoder.writeLogln("Tuner output thread HDHomeRun(UDP) -> PrimeNetEncoder -> SageTV Media Server(TCP)", this.logName);
    }
    
    //Use STDIN to send data to FFMPEG
    public TunerOutput(int udpPort, OutputStream processInput, InputStream processOutput, String fileNamePath, String logName, String uploadID, String mediaServerIPAddress)
    {
        this.uploadID = uploadID;
        this.fileNamePath = fileNamePath;
        this.logName = logName;
        this.keepProcessing = true;
        this.useMediaServer = true;
        this.useDirectStream = false;
        this.useSTDINStream = true;
        this.mediaServerIPAddress = mediaServerIPAddress;
        this.fileSize = 0;
        this.processOutput = processOutput;
        this.processInput = processInput;
        
        this.updPort = udpPort;
        
        this.outputWatcher = new TunerOutputWatcher(this);
        this.tunerBridge = new TunerBridge(this.processInput, this.updPort, this.logName);
        
        
        PrimeNetEncoder.writeLogln("Tuner output thread constructed for UploadID: " + uploadID, this.logName);
        PrimeNetEncoder.writeLogln("Tuner output thread HDHomeRun(UDP) -> PrimeNetEncoder(SDIN) -> ffmpeg(STDOUT) -> SageTV Media Server(TCP)", this.logName);
    }
    
    public void stopProcessing()
    {
        if(this.useSTDINStream)
        {
            this.tunerBridge.stopProcessing();
        }
        
        this.keepProcessing = false;
        
        //This might be causeing things to hang.  Commenting out for now.
        //Wait to see if thread stops.  If not close inuptStream.
        /*
        try 
        { 
            Thread.sleep(500);
            if(input != null)
            {
                input.close();
            }
        } 
        catch(Exception ex) {}
        */
        //TODO:  Handle the watcher if we stop stream before data is produced
        
    }
    
    private void sendToMediaServer()
    {
        BufferedOutputStream output = null;
        BufferedReader sinput = null;
        
        //open up a channel to the MediaServer port (8171). It requests permission to stream to the file via WRITEOPEN filename UploadID\r\n. If sage recognizes the uploadID, it allows it, and awaits "WRITE offset length\r\nDATA".
        try 
        {   
            //TODO: MediaServer port needs to be in config file
            String response;
            Socket server = new Socket(this.mediaServerIPAddress, 7818);
            output = new BufferedOutputStream(server.getOutputStream());
            sinput = new BufferedReader(new InputStreamReader(server.getInputStream()));
            input = new BufferedInputStream(this.processOutput);
            
            PrimeNetEncoder.writeLogln("Sending write open command", logName);
            output.write(("WRITEOPEN " + this.fileNamePath + " " + this.uploadID + "\r\n").getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            output.flush();
            
            response = sinput.readLine();
            
            if(response.equalsIgnoreCase("OK"))
            {
                PrimeNetEncoder.writeLogln("Write open sent successfully", logName);
            }
            else
            {
                PrimeNetEncoder.writeLogln("Write open failed!", logName);
                return;
            }

            byte[] buffer = new byte[writeBufferSize];
            int len1;
            
            while ( (len1 = input.read(buffer)) > 0 && keepProcessing) 
            {
                output.write(("WRITE " + fileSize + " " + len1 + "\r\n").getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
                output.write(buffer,0, len1);
                output.flush();
                
                fileSize += len1;
            }
            
            output.write("CLOSE".getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            output.write("QUIT".getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            
            //Write initialization info
            
        } 
        catch (IOException ex) 
        {
            PrimeNetEncoder.writeLogln("Unhandled Exception writing stream to MediaServer: " + ex.getMessage(), this.logName);
        }
        finally
        {
            if(output != null)
            {
                try {output.close(); input.close(); sinput.close();} catch (IOException ex) { }
                input = null;
            }
        }
        
    }
    
    private void sendToMediaServerDirect()
    {
        BufferedOutputStream output = null;
        BufferedReader sinput = null;
        
        System.out.println("SENDING STREAM RAW!!!");
        
        //open up a channel to the MediaServer port (8171). It requests permission to stream to the file via WRITEOPEN filename UploadID\r\n. If sage recognizes the uploadID, it allows it, and awaits "WRITE offset length\r\nDATA".
        try 
        {   
            String response;
            Socket server = new Socket(this.mediaServerIPAddress, 7818);
            output = new BufferedOutputStream(server.getOutputStream());
            sinput = new BufferedReader(new InputStreamReader(server.getInputStream()));
            input = new BufferedInputStream(this.processOutput);
            
            PrimeNetEncoder.writeLogln("Sending write open command", logName);
            output.write(("WRITEOPEN " + this.fileNamePath + " " + this.uploadID + "\r\n").getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            output.flush();
            
            response = sinput.readLine();
            
            if(response.equalsIgnoreCase("OK"))
            {
                PrimeNetEncoder.writeLogln("Write open sent successfully", logName);
            }
            else
            {
                PrimeNetEncoder.writeLogln("Write open failed!", logName);
                return;
            }

            DatagramSocket socket = new DatagramSocket(this.updPort);
            DatagramPacket packet = new DatagramPacket(new byte[8196], 8196);
            
            socket.receive(packet);
            
            while ( packet.getLength() > 0 && keepProcessing) 
            {
                output.write(("WRITE " + fileSize + " " + packet.getLength() + "\r\n").getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
                output.write(packet.getData(),0, packet.getLength());
                output.flush();
                
                fileSize += packet.getLength();
                
                socket.receive(packet);
            }
            
            output.write("CLOSE".getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            output.write("QUIT".getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            
            //Write initialization info
            socket.close();
        } 
        catch (IOException ex) 
        {
            PrimeNetEncoder.writeLogln("Unhandled Exception writing stream to MediaServer: " + ex.getMessage(), this.logName);
        }
        finally
        {
            if(output != null)
            {
                try {output.close(); input.close(); sinput.close();} catch (IOException ex) { }
                input = null;
            }
        }
        
    }
    
    private void sendToFile()
    {
        FileOutputStream outputFile = null;
        PrimeNetEncoder.writeLogln("Tuner output thread started for file: " + fileNamePath, this.logName);
        
        try
        {
            input = new BufferedInputStream(this.processOutput);
            
            outputFile = new FileOutputStream(new File(this.fileNamePath));

            byte[] buffer = new byte[writeBufferSize];
            int len1;
        
            while ( (len1 = input.read(buffer)) > 0 && keepProcessing) 
            {
                fileSize += len1;
                outputFile.write(buffer,0, len1);
                outputFile.flush();
            }
            
        }
        catch(Exception ex)
        {
            PrimeNetEncoder.writeLogln("Unhandled Exception writing stream to file: " + ex.getMessage(), this.logName);
        }
        finally
        {
            if(outputFile != null)
            {
                try {outputFile.close(); input.close();} catch (IOException ex) { }
                input = null;
            }
        }
    }
    
    /**
     * Gets the amount of data that we have transfered successfully at this point
     * @return Length of data transfered
     */
    public long getFileSize()
    {
        return this.fileSize;
    }
    
    /**
     * The amount of data that is sent to SageTV MediaServer or file at one
     * time.
     * @param size Size of the write buffer
     */
    public static void setWriteBufferSize(int size)
    {
        TunerOutput.writeBufferSize = size;
    }
    
    /**
     * The amount of data that is sent to SageTV MediaServer or file at one
     * time.
     * @return Size of the write buffer
     */
    public static int getWriteBufferSize()
    {
        return TunerOutput.writeBufferSize;
    }
    
    public String getLogName()
    {
        return this.logName;
    }
    
    @Override
    public void run()
    {
        
        this.outputWatcher.start();
        
        if(useMediaServer && this.useDirectStream)
        {
            this.sendToMediaServerDirect();
        }
        else if(useMediaServer)
        {
            if(this.useSTDINStream)
            {
                this.tunerBridge.start();
            }
            
            this.sendToMediaServer();
        }
        else if(this.useDirectStream)
        {
            throw new RuntimeException("Direct stream to file is not currently supported");
        }
        else
        {
            this.sendToFile();
        }
    }
    
    private class TunerBridge extends Thread
    {
        private OutputStream processInput;
        private int udpPort;
        private long transferSize;
        private boolean keepProcessing;
        private String logName;
        
        public TunerBridge(OutputStream processInput, int udpPort, String logName)
        {
            this.processInput = processInput;
            this.udpPort = udpPort;
            this.logName = logName;
            this.transferSize = 0;
            this.keepProcessing = true;
        }
        
        @Override
        public void run()
        {
            PrimeNetEncoder.writeLogln("TunerBridge thread started udpPort: " + udpPort, logName);
            
            int bufferSize = DEFAULT_UDP_BUFFER_SIZE * 4;
            
            try
            {
                DatagramSocket socket = new DatagramSocket(this.udpPort);
                DatagramPacket packet = new DatagramPacket(new byte[bufferSize], bufferSize);
                
                socket.receive(packet);

                while ( packet.getLength() > 0 && this.keepProcessing) 
                {
                    this.processInput.write(packet.getData(), 0, packet.getLength());
                    this.transferSize += packet.getLength();
                    socket.receive(packet);
                }
                
                socket.close();
            }
            catch(Exception ex)
            {
                PrimeNetEncoder.writeLogln("Unhandled Exception writing stream to file: " + ex.getMessage(), this.logName);
            }
        }
     
        public void stopProcessing()
        {
            this.keepProcessing = false;
        }
        
        public long getTransferSize()
        {
            return this.transferSize;
        }
        
    }
    
    /**
    * This thread will keep track of the tuner out, and restart process if necessary.
    * Hopefully this will be able to handle the rare occasion that ffmpeg does not
    * start producing data.
    * 
    * The thread will gracefully exit when there is an appropriate amount of data
    * transfered.
    */
    private class TunerOutputWatcher extends Thread
    {
        private static final int CHECK_INTERVAL = 250;
        private static final int MAX_WAIT_TIME = 12000;

        private TunerOutput tunerOutput;
        private long elapsedTimeMS;
        private boolean dataFound;

        public TunerOutputWatcher(TunerOutput tunerOutput)
        {
            this.tunerOutput = tunerOutput;
            this.elapsedTimeMS = 0;
            this.dataFound = false;
        }

        @Override
        public void run()
        {

            while(!dataFound && elapsedTimeMS < MAX_WAIT_TIME)
            {
                if(this.tunerOutput.useSTDINStream)
                {
                    PrimeNetEncoder.writeLogln("Tuner bridge has transfered: " + this.tunerOutput.tunerBridge.getTransferSize(), logName);
                }
                
                if(tunerOutput.getFileSize() > 0)
                {
                    this.dataFound = true;
                }
                else
                {
                    try { Thread.sleep(CHECK_INTERVAL); } catch(Exception ex) {}
                    this.elapsedTimeMS += CHECK_INTERVAL;
                }
            }

            if(dataFound)
            {
                PrimeNetEncoder.writeLogln("Tuner has produced data in (" + elapsedTimeMS + "ms)", tunerOutput.getLogName());
            }
            else
            {
                PrimeNetEncoder.writeLogln("WARNING: Tuner has not produced data in (" + elapsedTimeMS + "ms)", tunerOutput.getLogName());
                //TODO:  Modify the code to try and resolve the situation.  Possible restart processes
            }
        }
    }
    
}

