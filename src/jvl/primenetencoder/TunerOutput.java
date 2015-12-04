package jvl.primenetencoder;

import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import java.util.concurrent.LinkedTransferQueue;
import jvl.io.AdvancedOutputStream;

/**
 * This class is responsible for transferring the stream data from the source
 * and destination.  It is meant to handle multiple sources and destinations
 * 
 */
public class TunerOutput extends Thread
{
    public static final int DEFAULT_FFMPEG_INPUT_BUFFER_SIZE = 32768;
    public static final int DEFAULT_FFMPEG_OUTPUT_BUFFER_SIZE = 32768;
    public static final int DEFAULT_MEDIASERVER_OUTPUT_BUFFER_SIZE = 32768;
    public static final int DEFAULT_MEDIASERVER_SEND_SIZE = 1024;
    public static final int DEFAULT_UDP_PACKET_SIZE = 1500;
    
    private static int mediaServerSendSize = DEFAULT_MEDIASERVER_SEND_SIZE;
    private static int mediaServerOutputBufferSize = DEFAULT_MEDIASERVER_OUTPUT_BUFFER_SIZE;
    private static int ffmpegInputBufferSize = DEFAULT_FFMPEG_INPUT_BUFFER_SIZE;
    private static int ffmpegOutputBufferSize = DEFAULT_FFMPEG_OUTPUT_BUFFER_SIZE;
    private static int udpPacketSize = DEFAULT_UDP_PACKET_SIZE;
    
    private final String logName;
    private final InputStream processOutput;
    private final OutputStream processInput;
    private final String fileNamePath;
    private final String uploadID;
    private final int updPort;
    
    private final boolean useMediaServer;
    private final boolean useDirectStream;
    private final boolean useSTDINStream;
    
    private boolean keepProcessing;
    private String mediaServerIPAddress;
    private long fileSize;
    
    private AdvancedOutputStream mediaServerOutput = null;
    private TunerOutputWatcher outputWatcher;
    private TunerBridge tunerBridge;
    private Tuner tuner;
    
    //Write data directly to file
    
    //Send data to sage by CIFS instead of media server
    //TODO: Need to test this before beta release
    public TunerOutput(Tuner tuner, int udpPort, OutputStream processInput, InputStream processOutput, String fileNamePath, String logName)
    {
        this.tuner = tuner;
        this.fileNamePath = fileNamePath;
        this.logName = logName;
        this.keepProcessing = true;
        this.useMediaServer = false;
        this.useDirectStream = false;
        this.useSTDINStream = true;
        this.updPort = udpPort;
        this.fileSize = 0;
        this.uploadID = "";
        this.processOutput = processOutput;
        this.processInput = processInput;
        
        this.outputWatcher = new TunerOutputWatcher(this);
        this.outputWatcher.setName("OutputWatcher-" + this.tuner.getTunerName());
        this.tunerBridge = new TunerBridge(this, this.processInput, this.updPort, this.logName);
        this.tunerBridge.setName("TunerBridge-" + this.tuner.getTunerName());
        PrimeNetEncoder.writeLogln("Tuner output thread HDHomeRun(UDP) -> PrimeNetEncoder(STDIN) -> ffmpeg(STDOUT) -> PrimeNetEncoder -> File(CIFS/SMB)", this.logName);
    }
    
    //Direct write to from HDHomeRun to SageTV Media Server
    public TunerOutput(Tuner tuner, int udpPort, String fileNamePath, String logName, String uploadID, String mediaServerIPAddress)
    {
        this.tuner = tuner;
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
        this.outputWatcher.setName("OutputWatcher-" + this.tuner.getTunerName());
        
        PrimeNetEncoder.writeLogln("Tuner output thread constructed for UploadID: " + uploadID, this.logName);
        PrimeNetEncoder.writeLogln("Tuner output thread HDHomeRun(UDP) -> PrimeNetEncoder -> SageTV Media Server(TCP)", this.logName);
    }
    
    //Use media server to transfer to Sage
    public TunerOutput(Tuner tuner, int udpPort, OutputStream processInput, InputStream processOutput, String fileNamePath, String logName, String uploadID, String mediaServerIPAddress)
    {
        this.tuner = tuner;
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
        this.outputWatcher.setName("OutputWatcher-" + this.tuner.getTunerName());
        this.tunerBridge = new TunerBridge(this, this.processInput, this.updPort, this.logName);
        this.tunerBridge.setName("TunerBridge-" + this.tuner.getTunerName());
        
        PrimeNetEncoder.writeLogln("Tuner output thread constructed for UploadID: " + uploadID, this.logName);
        PrimeNetEncoder.writeLogln("Tuner output thread HDHomeRun(UDP) -> PrimeNetEncoder(SDIN) -> ffmpeg(STDOUT) -> SageTV Media Server(TCP)", this.logName);
    }
    
    public void stopProcessing()
    {
        this.keepProcessing = false;
        
        if(this.outputWatcher.isAlive())
        {
            this.outputWatcher.stopProcessing();
        }
        
        if(this.tunerBridge.isAlive())
        {
            this.tunerBridge.stopProcessing();
        }

    }
    
    private void sendToMediaServer()
    {
        //BufferedOutputStream output = null;
        BufferedReader sinput = null;
        BufferedInputStream input = null;
        
        //open up a channel to the MediaServer port (8171). It requests permission to stream to the file via WRITEOPEN filename UploadID\r\n. If sage recognizes the uploadID, it allows it, and awaits "WRITE offset length\r\nDATA".
        try 
        {   
            //TODO: MediaServer port needs to be in config file
            String response;
            Socket server = new Socket(this.mediaServerIPAddress, 7818);
            mediaServerOutput = new AdvancedOutputStream(server.getOutputStream(), TunerOutput.getMediaServerOutputBufferSize());
            sinput = new BufferedReader(new InputStreamReader(server.getInputStream()));
            input = new BufferedInputStream(this.processOutput, TunerOutput.getFfmpegOutputBufferSize());
            
            
            PrimeNetEncoder.writeLogln("Sending write open command", logName);
            mediaServerOutput.write(("WRITEOPEN " + this.fileNamePath + " " + this.uploadID + "\r\n").getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            mediaServerOutput.flush();
            
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
            
            byte[] buffer = new byte[TunerOutput.getMediaServerSendSize()];
            int len1;
            
            while ( (len1 = input.read(buffer)) > 0 && keepProcessing) 
            {
                mediaServerOutput.write(("WRITE " + fileSize + " " + len1 + "\r\n").getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
                mediaServerOutput.write(buffer,0, len1);
                fileSize += len1;
            }
            
            mediaServerOutput.write("CLOSE".getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            mediaServerOutput.write("QUIT".getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            
        } 
        catch (IOException ex) 
        {
            PrimeNetEncoder.writeLogln("Unhandled Exception writing stream to MediaServer: " + ex.getMessage(), this.logName);
        }
        finally
        {
            if(this.tunerBridge != null)
            {
                this.tunerBridge.stopProcessing();
            }
            if(mediaServerOutput != null)
            {
                try 
                {
                    mediaServerOutput.close(); 
                } 
                catch (IOException ex) { }
                mediaServerOutput = null;
            }
            if(input != null)
            {
                try 
                {
                    input.close(); 
                } 
                catch (IOException ex) { }
                input = null;
            }
            if(sinput != null)
            {
                try 
                {
                    sinput.close(); 
                } 
                catch (IOException ex) { }
                sinput = null;
            }
            PrimeNetEncoder.writeLogln("TunerOutput thread exited", logName);
        }
        
    }
    
    private void sendToMediaServerDirect()
    {
        //BufferedOutputStream output = null;
        BufferedReader sinput = null;
        BufferedInputStream input = null;
        DatagramSocket socket = null;
        
        //System.out.println("SENDING STREAM RAW!!!");
        
        //open up a channel to the MediaServer port (8171). It requests permission to stream to the file via WRITEOPEN filename UploadID\r\n. If sage recognizes the uploadID, it allows it, and awaits "WRITE offset length\r\nDATA".
        try 
        {   
            String response;
            Socket server = new Socket(this.mediaServerIPAddress, 7818);
            mediaServerOutput = new AdvancedOutputStream(server.getOutputStream(), TunerOutput.getMediaServerOutputBufferSize());
            sinput = new BufferedReader(new InputStreamReader(server.getInputStream()));
            input = new BufferedInputStream(this.processOutput, TunerOutput.getFfmpegOutputBufferSize());
            
            PrimeNetEncoder.writeLogln("Sending write open command", logName);
            mediaServerOutput.write(("WRITEOPEN " + this.fileNamePath + " " + this.uploadID + "\r\n").getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            mediaServerOutput.flush();
            
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

            PrimeNetEncoder.writeLogln("Sending REMUX_SETUP command", logName);
            mediaServerOutput.write(("REMUX_SETUP 1 0 8192\r\n").getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            mediaServerOutput.flush();
            
            response = sinput.readLine();
            
            if(response.equalsIgnoreCase("OK"))
            {
                PrimeNetEncoder.writeLogln("REMUX sent successfully", logName);
            }
            else
            {
                PrimeNetEncoder.writeLogln("REMUX setup failed! " + response, logName);
                return;
            }
            
            socket = new DatagramSocket(this.updPort);
            DatagramPacket packet = new DatagramPacket(new byte[TunerOutput.getUDPPacketSize()], TunerOutput.getUDPPacketSize());
            
            socket.setReceiveBufferSize(65535);
            socket.setSoTimeout(12000); //TODO: Set a global timeout for the UDP
            
            socket.receive(packet);
            
            while (packet.getLength() > 0 && keepProcessing) 
            {
                mediaServerOutput.write(("WRITE " + fileSize + " " + packet.getLength() + "\r\n").getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
                mediaServerOutput.write(packet.getData(),0, packet.getLength());
                mediaServerOutput.flush();
                
                fileSize += packet.getLength();
                
                socket.receive(packet);
            }
            
            mediaServerOutput.write("CLOSE".getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            mediaServerOutput.write("QUIT".getBytes(PrimeNetEncoder.CHARACTER_ENCODING));
            
        } 
        catch (IOException ex) 
        {
            PrimeNetEncoder.writeLogln("Unhandled Exception writing stream to MediaServer: " + ex.getMessage(), this.logName);
        }
        finally
        {
            if(socket != null)
            {
                try 
                {
                    socket.close(); 
                } 
                catch (Exception ex) { }
                socket = null;
            }
            if(mediaServerOutput != null)
            {
                try 
                {
                    mediaServerOutput.close(); 
                } 
                catch (IOException ex) { }
                mediaServerOutput = null;
            }
            if(input != null)
            {
                try 
                {
                    input.close(); 
                } 
                catch (IOException ex) { }
                input = null;
            }
            if(sinput != null)
            {
                try 
                {
                    sinput.close(); 
                } 
                catch (IOException ex) { }
                sinput = null;
            }
            PrimeNetEncoder.writeLogln("TunerOutput thread exited", logName);
        }
        
    }
    
    private void sendToFile()
    {
        FileOutputStream outputFile = null;
        BufferedInputStream input = null;
        
        try
        {
            input = new BufferedInputStream(this.processOutput, TunerOutput.getFfmpegOutputBufferSize());
            outputFile = new FileOutputStream(new File(this.fileNamePath));

            byte[] buffer = new byte[mediaServerSendSize];
            int len1;
        
            while ( (len1 = input.read(buffer)) > 0 && keepProcessing) 
            {
                this.fileSize += len1;
                outputFile.write(buffer,0, len1);
                outputFile.flush();
            }
            
        }
        catch(Exception ex)
        {
            PrimeNetEncoder.writeLogln("Unhandled Exception writing stream to file: " + ex.getMessage(), this.logName);
            ex.printStackTrace(System.out);
        }
        finally
        {
            if(this.tunerBridge != null)
            {
                this.tunerBridge.stopProcessing();
            }
            
            if(outputFile != null)
            {
                try 
                {
                    outputFile.close(); 
                } 
                catch (IOException ex) { }
                outputFile = null;
            }
            if(input != null)
            {
                try 
                {
                    input.close(); 
                } 
                catch (IOException ex) { }
                input = null;
            }
            PrimeNetEncoder.writeLogln("TunerOutput thread exited", logName);
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
    
    public TunerBridge getTunerBridge() 
    {
        return tunerBridge;
    }
    
    /**
     * The amount of data that is sent to SageTV MediaServer or file at one
     * time.
     * @param size Size of the write buffer
     */
    public static void setMediaServerWriteSize(int size)
    {
        TunerOutput.mediaServerSendSize = size;
    }
    
    /**
     * The amount of data that is sent to SageTV MediaServer or file at one
     * time.
     * @return Size of the write buffer
     */
    public static int getMediaServerWriteSize()
    {
        return TunerOutput.mediaServerSendSize;
    }
    
    public static int getFfmpegInputBufferSize()
    {
        return TunerOutput.ffmpegInputBufferSize;
    }
    
    public static void setFfmpegInputBuferSize(int size)
    {
        if(size < TunerOutput.DEFAULT_FFMPEG_INPUT_BUFFER_SIZE)
        {
            TunerOutput.ffmpegInputBufferSize = TunerOutput.DEFAULT_FFMPEG_INPUT_BUFFER_SIZE;
        }
        else
        {
            TunerOutput.ffmpegInputBufferSize = size;
        }
    }
    
    public static int getFfmpegOutputBufferSize()
    {
        return TunerOutput.ffmpegOutputBufferSize;
    }
    
    public static void setFfmpegOutputBuferSize(int size)
    {
        if(size < TunerOutput.DEFAULT_FFMPEG_OUTPUT_BUFFER_SIZE)
        {
            TunerOutput.ffmpegOutputBufferSize = TunerOutput.DEFAULT_FFMPEG_OUTPUT_BUFFER_SIZE;
        }
        else
        {
            TunerOutput.ffmpegOutputBufferSize = size;
        }
    }
    
    public static void setUDPPacketSize(int size)
    {
        if(size < TunerOutput.DEFAULT_UDP_PACKET_SIZE)
        {
            TunerOutput.udpPacketSize = TunerOutput.DEFAULT_UDP_PACKET_SIZE;
        }
        else
        {
            TunerOutput.udpPacketSize = size;
        }
    }
    
    public static int getUDPPacketSize()
    {
        return TunerOutput.udpPacketSize;
    }
    
    public static void setMediaServerOutputBufferSize(int size)
    {
        if(size < TunerOutput.DEFAULT_MEDIASERVER_OUTPUT_BUFFER_SIZE)
        {
            TunerOutput.mediaServerOutputBufferSize = TunerOutput.DEFAULT_MEDIASERVER_OUTPUT_BUFFER_SIZE;
        }
        else
        {
            TunerOutput.mediaServerOutputBufferSize = size;
        }
    }
    
    public static int getMediaServerOutputBufferSize()
    {
        return TunerOutput.mediaServerOutputBufferSize;
    }
    
    public static int getMediaServerSendSize()
    {
        return TunerOutput.mediaServerSendSize;
    }
    
    public String getLogName()
    {
        return this.logName;
    }
    
    @Override
    public void run()
    {
        
        if(this.tunerBridge != null)
        {
            this.tunerBridge.setDaemon(true);
            this.tunerBridge.start();
        }
        
        /*
        if(this.ffmpegBridge != null)
        {
            this.ffmpegBridge.start();
        }
        */
        
        this.outputWatcher.setPriority(Thread.MIN_PRIORITY);
        this.setDaemon(true);
        this.outputWatcher.start();
        
        if(useMediaServer && this.useDirectStream)
        {
            this.sendToMediaServerDirect();
        }
        else if(useMediaServer)
        {
            this.sendToMediaServer();
        }
        else if(this.useDirectStream)
        {
            //TODO:  Create direct stream to file option
            throw new RuntimeException("Direct stream to file is not currently supported");
        }
        else
        {
            this.sendToFile();
        }
    }
    
    public class TunerBridge extends Thread
    {
        private AdvancedOutputStream output = null;
        private final OutputStream processInput;
        private final int udpPort;
        private long transferSize;
        private boolean keepProcessing;
        private final String logName;
        private TunerOutput tunerOutput;
        
        /*
        * Variables for calculating average packet processing and receieve time
        */
        private long packets;
        private long numberOfPacketsReceived;
        private long totalPacketReceiveTime;
        private long averagePacketReceiveTime;
        private long lastPacketReceiveTime;
        private long tempLastPacketReceiveTime;
        private int packetTime;
        private long totalLatePackets;
        private LinkedTransferQueue<byte[]> hdhomerunQueue;
        
        private TunerBridge(TunerOutput tunerOutput, OutputStream processInput, int udpPort, String logName)
        {
            this.processInput = processInput;
            this.udpPort = udpPort;
            this.logName = logName;
            this.transferSize = 0;
            this.keepProcessing = true;
            
            this.hdhomerunQueue = hdhomerunQueue;
            this.tunerOutput = tunerOutput;
            this.numberOfPacketsReceived = 0;
            this.totalPacketReceiveTime = 0;
            this.averagePacketReceiveTime = 0;
            this.totalLatePackets = 0;
            this.lastPacketReceiveTime = 0;
            this.tempLastPacketReceiveTime = 0;
            this.packetTime = 0;
            this.packets = 0;
        }
        
        @Override
        public void run()
        {
            PrimeNetEncoder.writeLogln("TunerBridge thread started udpPort: " + udpPort, logName);
            DatagramSocket socket = null;
            
            try
            {
                output = new AdvancedOutputStream(this.processInput, TunerOutput.getFfmpegInputBufferSize());
                socket = new DatagramSocket(this.udpPort);
                DatagramPacket packet = new DatagramPacket(new byte[TunerOutput.getUDPPacketSize()], TunerOutput.getUDPPacketSize());
                
                //Set revice buffer to max size.
                socket.setReceiveBufferSize(262140);
                socket.setSoTimeout(50); //TODO: Set global udp timeout
                
                try
                {
                    socket.receive(packet);
                } catch(SocketTimeoutException ex) {}
                
                while (this.keepProcessing) 
                {
               
                    if(packet.getLength() > 0)
                    {
                        output.write(packet.getData(), 0, packet.getLength());
                        
                        //Log packet reception
                        this.transferSize += packet.getLength();        
                        //this.logPacketReceive(); 
                        packets++;
                    }
                    
                    try
                    {
                        socket.receive(packet);
                    } catch(SocketTimeoutException ex) {}
                }
                
                socket.close();
            }
            catch(Exception ex)
            {
                PrimeNetEncoder.writeLogln("Unhandled Exception in tuner bridge: " + ex.getMessage(), this.logName);
            }
            finally
            {
                if(socket != null)
                {
                    try
                    {
                        socket.close();
                    }
                    catch(Exception ex){ }
                }
                if(output != null)
                {
                    try
                    {
                        output.close();
                    }
                    catch(IOException ex){ }
                }
                PrimeNetEncoder.writeLogln("TunerBridge thread exited", logName);
            }
        }
     
        private void logPacketReceive()
        {
            long currentPacketReceiveTime = System.currentTimeMillis();
            
            if(packets % 10 == 0)
            {
                if(this.lastPacketReceiveTime != 0)
                {
                    this.packetTime = (int)(currentPacketReceiveTime - this.lastPacketReceiveTime);
                    this.numberOfPacketsReceived = this.numberOfPacketsReceived + 1;
                    this.totalPacketReceiveTime = this.totalPacketReceiveTime + this.packetTime;
                    this.averagePacketReceiveTime = this.totalPacketReceiveTime / this.numberOfPacketsReceived;
                }
                
                this.lastPacketReceiveTime = currentPacketReceiveTime;
            }
            
            //Logging an error for every late packet instead 10th
            if((currentPacketReceiveTime - this.tempLastPacketReceiveTime) > 150 && this.tempLastPacketReceiveTime != 0)
            {
                PrimeNetEncoder.writeLogln("Warning:  HDHomeRun packet was receive > 150ms after the previous packet!", this.logName);
                PrimeNetEncoder.writeLogln("\tPacket Receive Time: " + (currentPacketReceiveTime - this.tempLastPacketReceiveTime), this.logName);
                PrimeNetEncoder.writeLogln("\tAverage Packet Receive Time: " + this.averagePacketReceiveTime, this.logName);
                PrimeNetEncoder.writeLogln("\tFFmpeg Output Buffer Limit: " + this.output.getLimit(), this.logName);
                PrimeNetEncoder.writeLogln("\tFFmpeg Output Buffer Count: " + this.output.getCount(), this.logName);
                PrimeNetEncoder.writeLogln("\tMediaServer Output Buffer Limit: " + this.tunerOutput.mediaServerOutput.getLimit(), this.logName);
                PrimeNetEncoder.writeLogln("\tMediaServer Output Buffer Count: " + this.tunerOutput.mediaServerOutput.getCount(), this.logName);
                
                Tuner.Status status = this.tunerOutput.tuner.getTunerStatus();
                
                if(status != null)
                {
                    PrimeNetEncoder.writeLogln("\tSignal Strength: " + status.getSignalStrength() + "%", this.logName);
                    PrimeNetEncoder.writeLogln("\tSignal Quality: " + status.getSignalQuality() + "%", this.logName);
                    PrimeNetEncoder.writeLogln("\tSymbol Quality: " + status.getSignalQuality() + "%", this.logName);
                    PrimeNetEncoder.writeLogln("\tSymbol Quality: " + status.getBytesPerSecond() + "bps", this.logName);
                }

                this.totalLatePackets++;
            }
            
            this.tempLastPacketReceiveTime = currentPacketReceiveTime;
        }
        
        public void stopProcessing()
        {
            this.keepProcessing = false;
        }
        
        public long getTransferSize()
        {
            return this.transferSize;
        }
        
        public long getAveragePacketReceiveTime() 
        {
            return averagePacketReceiveTime;
        }

        public long getTotalLatePackets() 
        {
            return totalLatePackets;
        }

        public int getPacketTime() 
        {
            return packetTime;
        }
        
        public int getFFmpegOutputBufferLimit()
        {
            return this.output.getLimit();
        }
        
        public int getFFmpegOutputBufferCount()
        {
            return this.output.getCount();
        }

    }
    
    /*
    Attempted an extra transfer thread but it did not work...
    public class FFmpegBridge extends Thread
    {
        private boolean keepProcessing;
        private String logName;
        private LinkedTransferQueue<byte[]> hdhomerunOutputQueue;
        private OutputStream processInput;
        
        public FFmpegBridge(TunerOutput tunerOutput, LinkedTransferQueue<byte[]> hdhomerunOutputQueue, OutputStream processInput, String logName)
        {
            this.keepProcessing = true;
            this.hdhomerunOutputQueue = hdhomerunOutputQueue; 
            this.processInput = processInput;
            this.logName = logName;
        }
        
        @Override
        public void run()
        {
            AdvancedOutputStream output = new AdvancedOutputStream(processInput, TunerOutput.getFfmpegInputBufferSize());
            PrimeNetEncoder.writeLogln("FFmpegBridge has started.", logName);
            byte[] data = null;
            
            try
            {
                while(keepProcessing)
                {
                    data = hdhomerunOutputQueue.take();
                    output.write(data);
                }
            }
            catch(Exception ex)
            {
                PrimeNetEncoder.writeLogln("Unhandled Exception in hdhomerun bridge: " + ex.getMessage(), this.logName);
                ex.printStackTrace(System.out);
            }
            finally
            {
                if(output != null)
                {
                    try
                    {
                        output.close();
                    }
                    catch(Exception ex) {}
                }
            }
                    
            
        }
        
        public void stopProcessing()
        {
            keepProcessing = false;
        }
    }
    */
    
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
        private boolean keepProcessing;

        public TunerOutputWatcher(TunerOutput tunerOutput)
        {
            this.tunerOutput = tunerOutput;
            this.elapsedTimeMS = 0;
            this.dataFound = false;
            this.keepProcessing = true;
        }

        @Override
        public void run()
        {

            while(!dataFound && elapsedTimeMS < MAX_WAIT_TIME && keepProcessing)
            {
                if(this.tunerOutput.useSTDINStream)
                {
                    PrimeNetEncoder.writeLogln("Tuner bridge has transfered: " + this.tunerOutput.tunerBridge.getTransferSize(), logName);
                }
                
                if(tunerOutput.getFileSize() == 0 && (elapsedTimeMS == 5000 || elapsedTimeMS == 10000))
                {
                    PrimeNetEncoder.writeLogln("No data transfered in 6000ms.  Reseting tuner channel and stream.", logName);
                    tuner.resetRecording();
                }
                
                if(tunerOutput.getFileSize() > 0 )
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
        
        public void stopProcessing()
        {
            this.keepProcessing = false;
        }
    }
    
}

