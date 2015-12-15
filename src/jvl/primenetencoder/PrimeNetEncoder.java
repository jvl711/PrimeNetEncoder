package jvl.primenetencoder;

import java.io.FileOutputStream;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import jvl.util.Property;


/*
 *  TODO: Add more propertys to the set command
 *  TODO: Change set command to be in the format set [variable]=[value]
 */

public class PrimeNetEncoder extends Thread
{
    public static final String CHARACTER_ENCODING = "UTF-8";
    
    /*
    * Default values
    */
    public static final int DEFAULT_DISCOVERY_PORT = 8271;
    private static final String version = "2.0.1";
    private static final String propertyFileName = "PrimeNetEncoder.properties";
    private static String bindingAddressOverride = "";
    private static Property props;
    
    private int tunerCount;
    private int discoveryPort;
    private boolean discoveryEnabled;
    private ArrayList<Tuner> tuners;
    private String hdhomerunconfigPath;
    private String ffmpegPath;
    private Discovery discovery;
    
    private static boolean echoLogs = false;
    
    //Static constructor
    static 
    {
        try
        {
            PrimeNetEncoder.props = new Property(new File(propertyFileName));
        }
        catch(Exception ex)
        {
            throw new RuntimeException("Error: Unable to load property file! " + ex.getMessage());
        }
    }
    
    public PrimeNetEncoder()
    {        
        //Properties properties = this.LoadProperties();
        tuners = new ArrayList<Tuner>();

        try
        {
            this.bindingAddressOverride = props.getProperty("bindingaddress.override", "");
            int ffmpegDelay = props.getProperty("ffmpeg.delay", 500);
            int ffmpegProbeSize = props.getProperty("ffmpeg.probesize", Tuner.FFMPEG_DEFAULT_PROBESIZE);
            int ffmpegAnalyzeDuration = props.getProperty("ffmpeg.analyzeduration", Tuner.FFMPGEG_DEFAULT_ANALYZE_DURATION);
            this.ffmpegPath = props.getProperty("ffmpeg.path", "ffmpeg.exe");
            this.hdhomerunconfigPath = props.getProperty("HDHomeRunConfig.path", "hdhomerun_config.exe");

            this.tunerCount = props.getProperty("tuners.count", 3);
            this.discoveryPort = props.getProperty("discovery.port", PrimeNetEncoder.DEFAULT_DISCOVERY_PORT);
            this.discoveryEnabled = props.getProperty("discovery.enabled", true);
            boolean useMediaServer = props.getProperty("mediaserver.transfer", false);
            
            TunerOutput.setFfmpegOutputBuferSize(props.getProperty("ffmpeg.outputbuffersize", TunerOutput.DEFAULT_FFMPEG_OUTPUT_BUFFER_SIZE));
            TunerOutput.setFfmpegInputBuferSize(props.getProperty("ffmpeg.inputbuffersize", TunerOutput.DEFAULT_FFMPEG_INPUT_BUFFER_SIZE));
            TunerOutput.setMediaServerOutputBufferSize(props.getProperty("mediaserver.outputbuffersize", TunerOutput.DEFAULT_MEDIASERVER_OUTPUT_BUFFER_SIZE));

            for(int i = 0; i < tunerCount; i++)
            {
                Tuner tuner;

                boolean useDirectStream = false; //TODO: Expose this property if it makes sense Not exposing yet

                String name = props.getProperty("tuner" + i + ".name", "PrimeNetEncoder FFFFFFFF-" + i);
                String id = props.getProperty("tuner" + i + ".id", "FFFFFFFF");
                int number = props.getProperty("tuner" + i + ".number", i);
                int port = props.getProperty("tuner" + i + ".port", 7000 + i); 
                int transcoderPort = props.getProperty("tuner" + i + ".transcoder.port", 5000 + i);
                boolean enabled = props.getProperty("tuner" + i + ".enabled", true);

                /* Transcoding options */
                boolean transcodeEnabled = props.getProperty("tuner" + i + ".transcode.enabled", false);
                boolean transcodeDeinterlace = props.getProperty("tuner" + i + ".transcode.deinterlace", true);
                int transcodeBitrate = props.getProperty("tuner" + i + ".transcode.bitrate", 4000); 
                String scaling = props.getProperty("tuner" + i + ".transcode.scaling", "");
                String transcodePreset =  props.getProperty("tuner" + i + ".transcode.preset", "veryfast");
                String transcodeCodec = props.getProperty("tuner" + i + ".transcode.codec", "libx264");
                tuner = new Tuner(i, name, id, number, port, transcoderPort, enabled
                                , this.hdhomerunconfigPath, this.ffmpegPath, useMediaServer, useDirectStream);

                tuner.setTranscodeEnabled(transcodeEnabled);
                tuner.setDeinterlaceEnabled(transcodeDeinterlace);
                tuner.setAverageBitrate(transcodeBitrate);
                tuner.setScaling(scaling);
                tuner.setTranscodePreset(transcodePreset);
                tuner.setTranscodeCodec(transcodeCodec);
                tuner.setffmpegDelay(ffmpegDelay);
                tuner.setFfmpegProbeSize(ffmpegProbeSize);
                tuner.setFfmpegAnalzyeDuration(ffmpegAnalyzeDuration);


                //tuner.setMediaServerTransfer(useMediaServer);

                tuners.add(tuner);
            }
            
            
        }
        catch(Exception ex)
        {
            throw new RuntimeException("Error:  Error loading properties file");
        }
        
        
        
    }
    
    @Override
    public void run()
    {
        PrimeNetEncoder.writeLogln("Running PrimeNetEncoder as a Runnable Class", "");
        PrimeNetEncoder.writeLogln("PrimeNetEncoder Version: " + PrimeNetEncoder.version, "");
        PrimeNetEncoder pEncoder = new PrimeNetEncoder();
        
        PrimeNetEncoder.writeLogln("Starting tunner threads", "");
        
        for(int i = 0; i < pEncoder.tuners.size(); i++)
        {
            if(pEncoder.tuners.get(i).isEnabled())
            {
                pEncoder.tuners.get(i).setName("Tuner-" + pEncoder.tuners.get(i).getTunerId() + "-" + pEncoder.tuners.get(i).getTunerNumber());
                pEncoder.tuners.get(i).setDaemon(true);
                pEncoder.tuners.get(i).start();
            }
        }
        
        
        
        //Start discovery thread if enabled
        if(this.discoveryEnabled)
        {
            discovery = new Discovery(pEncoder.discoveryPort, pEncoder.tuners);
            discovery.setDaemon(true);
            discovery.start();
            
            //Give discovery thread time to start
            try { Thread.sleep(1000); } catch (InterruptedException ex) { }
            
            SageTVWrapper.CallSageDiscovery();
        }
    }
    
    public static void main(String[] args) 
    {
        PrimeNetEncoder.writeLogln("Running PrimeNetEncoder in Console Mode", "");
        PrimeNetEncoder.writeLogln("PrimeNetEncoder Version: " + PrimeNetEncoder.version, "");
        
        PrimeNetEncoder pEncoder = new PrimeNetEncoder();
        
        PrimeNetEncoder.writeLogln("Starting tunner threads", "");
        
        for(int i = 0; i < pEncoder.tuners.size(); i++)
        {
            if(pEncoder.tuners.get(i).isEnabled())
            {
                pEncoder.tuners.get(i).setDaemon(true);
                pEncoder.tuners.get(i).start();
                //pEncoder.tuners.get(i).startDiscoveryThread();
            }
        }
        
        //Start discovery thread if enabled
        if(pEncoder.discoveryEnabled)
        {
            pEncoder.discovery = new Discovery(pEncoder.discoveryPort, pEncoder.tuners);
            pEncoder.discovery.setDaemon(true);
            pEncoder.discovery.start();
        }
        
        PrimeNetEncoder.writeLogln("Starting Debug Console", "");
        
        System.out.println("PrimeNetEncover Version: " + PrimeNetEncoder.version);
        System.out.println("Author: jvl711\n");
        
        
        while(true)
        {    
            System.out.print("PrimeNetEncoder>");
            String command = GetNextCommand();
            String[] params = command.split(" ");
            
            if(params[0].equalsIgnoreCase("STATUS"))
            {
                if(params.length == 1)
                {
                    pEncoder.PrintStatus();
                }
                else if(params.length == 2)
                {
                    try
                    {
                        int index = Integer.parseInt(params[1]);
                        pEncoder.PrintStatus(index);
                    } 
                    catch(NumberFormatException ex)
                    {
                        System.out.println("Parameter must be a number");
                    }
                }
                else
                {
                    System.out.println("Unknown command");
                }
                
            }
            else if(params[0].equalsIgnoreCase("SET"))
            {
                if(params.length == 3)
                {
                    pEncoder.setTunerVariable(params[1], params[2]);
                }
                else
                {
                    System.out.println("Command should be in the form: SET variable value");
                }
                        
            }
            else if(params[0].equalsIgnoreCase("GET"))
            {
                if(params.length == 2)
                {
                    pEncoder.getTunerVariable(params[1]);
                }
                else
                {
                    System.out.println("Command should be in the form: GET variable");
                }
                        
            }
            else if(params[0].equalsIgnoreCase("TRANSCODEENABLED"))
            {
                pEncoder.setTranscodeEnabled(params[1]);
            }
            else if(params[0].equalsIgnoreCase("TRANSCODEBITRATE"))
            {
                pEncoder.setTranscodeBitrate(params[1]);
            }
            else if(params[0].equalsIgnoreCase("ECHOLOGS"))
            {
                pEncoder.EchoLogs();
            }
            else if(params[0].equalsIgnoreCase("QUIT"))
            {
                boolean active = false;
                
                for(int i = 0; i < pEncoder.tuners.size(); i++)
                {
                    if(pEncoder.tuners.get(i).isRecording())
                    {
                        active = true;
                    }
                }
                
                if(active)
                {
                    System.out.println("Cannot exit while there is active recordings.");
                }
                else
                {
                    for(int i = 0; i < pEncoder.tuners.size(); i++)
                    {
                        if(pEncoder.tuners.get(i).isRecording())
                        {
                            //TODO: Add a stop processing
                        }
                    }
                    
                    break;
                }
            }
            else if(params[0].equalsIgnoreCase("FORCEGC"))
            {
                System.gc();
            }
            else if(command.equals(""))
            {
                
            }
            else
            {
                System.out.println("Unknown command: " + "(" + command + ")");
            }
                
        }
        
    }
    
    public static String GetBindingAddressOverride()
    {
        return PrimeNetEncoder.bindingAddressOverride;
    }
    
    private void EchoLogs()
    {
        System.out.println("Press 'return' to stop echoing logs.");
        
        echoLogs = true;
        
        try
        {
            while(true)
            {
                char temp = (char)System.in.read();
                
                if(temp == '\n')
                {
                    break;
                }
            }
        }
        catch(Exception ex)
        {
            echoLogs = false;
        }
        
        echoLogs = false;
    }
    
    private void PrintStatus()
    {
        for(int i = 0; i < this.tuners.size(); i++)
        {
            String locked = "";
            Tuner tuner = this.tuners.get(i);
            
            System.out.print("(" + i + ") " + tuner.getTunerName() + " (" + tuner.getTunerId() + "-" + tuner.getTunerNumber() + ") - ");
            
            if(tuner.isEnabled() && tuner.isTunerLocked())
            {
                locked = "/LOCKED";
            }
            
            if(tuner.isEnabled())
            {
                if(tuner.isRecording())
                {
                    System.out.println("RECORDING" + locked);
                }
                else if (tuner.isConnected())
                {
                    System.out.println("CONNECTED" + locked);
                }
                else
                {
                    System.out.println("IDLE" + locked);
                }
            }
            else
            {
                System.out.println("DISABLED");
            }
        }
        
        System.out.println();
    }
    
    private void PrintStatus(int index)
    {
        
        if(index < 0 || index >= tuners.size())
        {
            System.out.println("Tuner (" + index + ") does not exist");
            return;
        }
        
        Tuner tuner = this.tuners.get(index);
        
        System.out.print(tuner.getTunerName() + " (" + tuner.getTunerId() + "-" + tuner.getTunerNumber() + ") - ");
            
            if(tuner.isEnabled())
            {
                if(tuner.isRecording())
                {
                    System.out.println("RECORDING");
                    
                    File recordingFile = new File(tuner.getRecordingFile());
            
                    System.out.println("Recording filename: " + recordingFile.getName());
                    System.out.println("Recording path: " + recordingFile.getParent());
                    System.out.println("Recording current size: " + recordingFile.length());
                    System.out.println("Channel: " + tuner.getRecordingChannel());
                    System.out.println("Quality: " + tuner.getRecordingQuality());
                    
                    
                    //Show tuner bridge statistics
                    if(tuner.getTunerOutput() != null && tuner.getTunerOutput().getTunerBridge() != null)
                    {
                        System.out.println("Performance Stats");
                        System.out.println("\tCurrent packet time (10x): " + tuner.getTunerOutput().getTunerBridge().getPacketTime() + "ms");
                        System.out.println("\tAverage packet receive time (10x): " + tuner.getTunerOutput().getTunerBridge().getAveragePacketReceiveTime() + "ms");
                        System.out.println("\tTotal packets received late (10x): " + tuner.getTunerOutput().getTunerBridge().getTotalLatePackets());
                        System.out.println("\tFFmpeg input buffer size (STDIN): " + tuner.getTunerOutput().getTunerBridge().getFFmpegOutputBufferLimit());
                        System.out.println("\tFFmpeg input buffer count (STDIN): " + tuner.getTunerOutput().getTunerBridge().getFFmpegOutputBufferCount());
                    }
                    
                    Tuner.Status status = tuner.getTunerStatus();
                    
                    if(status != null)
                    {
                        System.out.println("Tuner Status Info");
                        System.out.println("\tSignal Strength: " + status.getSignalStrength() + "%");
                        System.out.println("\tSignal Quality: " + status.getSignalQuality() + "%");
                        System.out.println("\tSymbol Quality: " + status.getSymbolQuality() + "%");
                        System.out.println("\tData Transfer Rate: " + status.getBytesPerSecond() + "bps");
                    }
                    
                    if(tuner.isTunerLocked())
                    {
                        System.out.println("Tuner Lock Info");
                        System.out.println("\tTuner locked: True");
                        System.out.println("\tlock source: " + tuner.getTunerLockSource());
                    }
                    else
                    {
                        System.out.println("Tuner Lock Info");
                        System.out.println("\tTuner locked: False");
                    }
                    
                    if(tuner.isTranscodeEnabled())
                    {
                        System.out.println("Transcoding Info");
                        System.out.println("\tTranscoding: Enabled");
                        System.out.println("\tTranscode Bitrate: " + tuner.getAverageBitrate());
                        System.out.println("\tTranscode Scaling: " + tuner.getScaling());
                        System.out.println("\tTranscode Preset: " + tuner.getTranscodePreset());
                        System.out.println("\tDeinterlace: " + tuner.isDeinterlaceEnabled());
                    }
                    else
                    {
                        System.out.println("Transcoding Info");
                        System.out.println("\tTranscoding: False");
                    }
                    
                }
                else if(tuner.isConnected())
                {
                    System.out.println("CONNECTED");
                    if(tuner.isTunerLocked())
                    {
                        System.out.println("Tuner locked: True");
                        System.out.println("lock source: " + tuner.getTunerLockSource());
                    }
                    else
                    {
                        System.out.println("Tuner locked: False");
                    }
                }
                else
                {
                    System.out.println("IDLE");
                    if(tuner.isTunerLocked())
                    {
                        System.out.println("Tuner locked: True");
                        System.out.println("lock source: " + tuner.getTunerLockSource());
                    }
                    else
                    {
                        System.out.println("Tuner locked: False");
                    }
                }
            }
            else
            {
                System.out.println("DISABLED");
            
            }
            System.out.println();
    }
    
    private void setTranscodeEnabled(String param)
    {
        if(param.equalsIgnoreCase("TRUE"))
        {
            System.out.println("Enabled transcoding to h.264");
            
            for(int i = 0; i < tuners.size(); i++)
            {
                tuners.get(i).setTranscodeEnabled(true);
            }
        }
        else if(param.equalsIgnoreCase("FALSE"))
        {
            System.out.println("Diabled transcoding to h.264");
            
            for(int i = 0; i < tuners.size(); i++)
            {
                tuners.get(i).setTranscodeEnabled(false);
            }
        }
        else
        {
            System.out.println("Unknown parameter");
        }
    }
    
    private void setTunerVariable(String parameter, String value)
    {
        if(parameter.equalsIgnoreCase("ffmpeg.probesize"))
        {
            int probesize;
            
            try
            {
                probesize = Integer.parseInt(value);
            }
            catch(Exception ex)
            {
                System.out.println("Unknown parameter.  Value must be a whole number.");
                return;
            }
            
            for(int i = 0; i < tuners.size(); i++)
            {
                tuners.get(i).setFfmpegProbeSize(probesize);
            }
        }
        else if(parameter.equalsIgnoreCase("ffmpeg.analyzeduration"))
        {
            int analyzeduration;
            
            try
            {
                analyzeduration = Integer.parseInt(value);
            }
            catch(Exception ex)
            {
                System.out.println("Unknown parameter.  Value must be a whole number.");
                return;
            }
            
            for(int i = 0; i < tuners.size(); i++)
            {
                tuners.get(i).setFfmpegAnalzyeDuration(analyzeduration);
            }
        }
        else if(parameter.equalsIgnoreCase("ffmpeg.delay"))
        {
            int delay;
            
            try
            {
                delay = Integer.parseInt(value);
            }
            catch(Exception ex)
            {
                System.out.println("Unknown parameter.  Value must be a whole number.");
                return;
            }
            
            for(int i = 0; i < tuners.size(); i++)
            {
                tuners.get(i).setffmpegDelay(delay);
            }
            
            try
            {
                PrimeNetEncoder.getProperty().setProperty("ffmpeg.delay", delay + "");
            }
            catch(Exception ex)
            {
                System.out.println("Error setting property in property file!");
            }
            
        }
        else if(parameter.equalsIgnoreCase("ffmpeg.outputbuffersize"))
        {
            int size;
            
            try
            {
                size = Integer.parseInt(value);
            }
            catch(Exception ex)
            {
                System.out.println("Unknown parameter.  Value must be a whole number.");
                return;
            }
            
            TunerOutput.setFfmpegOutputBuferSize(size);
        }
        else if(parameter.equalsIgnoreCase("ffmpeg.inputbuffersize"))
        {
            int size;
            
            try
            {
                size = Integer.parseInt(value);
            }
            catch(Exception ex)
            {
                System.out.println("Unknown parameter.  Value must be a whole number.");
                return;
            }
            
            TunerOutput.setFfmpegInputBuferSize(size);
        }
        else if(parameter.equalsIgnoreCase("ffmpeg.usestdin"))
        {
            boolean useSTDIN;
            
            try
            {
                useSTDIN = Boolean.parseBoolean(value);
            }
            catch(Exception ex)
            {
                System.out.println("Unknown parameter.  Value must be True/False.");
                return;
            }
            
            for(int i = 0; i < tuners.size(); i++)
            {
                tuners.get(i).setFfmegUseSTDIN(useSTDIN);
            }
        }
        else if(parameter.equalsIgnoreCase("mediaserver.outputbuffersize"))
        {
            int size;
            
            try
            {
                size = Integer.parseInt(value);
            }
            catch(Exception ex)
            {
                System.out.println("Unknown parameter.  Value must be a whole number.");
                return;
            }
            
            TunerOutput.setMediaServerOutputBufferSize(size);
        }
        else if(parameter.equalsIgnoreCase("usedirectstream"))
        {
            boolean useDirectStream;
            
            try
            {
                useDirectStream = Boolean.parseBoolean(value);
            }
            catch(Exception ex)
            {
                System.out.println("Unknown parameter.  Value must be True/False.");
                return;
            }
            
            for(int i = 0; i < tuners.size(); i++)
            {
                tuners.get(i).setDirectStream(useDirectStream);
            }
        }
        else
        {
            System.out.println("Unknown variable.");
        }
        
        try
        {
            PrimeNetEncoder.props.setProperty(parameter.toLowerCase(), value);
        }
        catch(Exception ex)
        {
            System.out.println("Error: Unexpected error saving property.");
        }
                
    }
    
    private void getTunerVariable(String parameter)
    {
        String value = "";
        
        if(parameter.equalsIgnoreCase("ffmpeg.probesize"))
        {
            value = tuners.get(0).getFfmpegProbeSize() + "";
        }
        else if(parameter.equalsIgnoreCase("ffmpeg.analyzeduration"))
        {
            value = tuners.get(0).getFfmpegAnalzyeDuration() + "";
        }
        else if(parameter.equalsIgnoreCase("ffmpeg.delay"))
        {
            value = tuners.get(0).getffmpegDelay() + "";
        }
        else if(parameter.equalsIgnoreCase("ffmpeg.outputbuffersize"))
        {
            value = TunerOutput.getFfmpegOutputBufferSize() + "";
        }
        else if(parameter.equalsIgnoreCase("ffmpeg.inputbuffersize"))
        {
            value = TunerOutput.getFfmpegOutputBufferSize() + "";
        }
        else if(parameter.equalsIgnoreCase("mediaserver.outputbuffersize"))
        {
            value = TunerOutput.getFfmpegOutputBufferSize() + "";
        }
        else if(parameter.equalsIgnoreCase("usedirectstream"))
        {
            value = tuners.get(0).isDirectStream() + "";
        }
        else if(parameter.equalsIgnoreCase("ffmpeg.usestdin"))
        {
            value = tuners.get(0).isFfmpegUseSTDIN() + "";
        }
        else
        {
            System.out.println("Unknown variable.");
            return;
        }
        
        System.out.println(parameter + " = " + value);
    }
    
    private void setTranscodeBitrate(String param)
    {
        int bitrate;
        
        try
        {
            bitrate = Integer.parseInt(param);
        }
        catch(Exception ex)
        {
            System.out.println("Unknown parameter.  Value must be a whole number.");
            return;
        }
        
        for(int i = 0; i < tuners.size(); i++)
        {
            tuners.get(i).setAverageBitrate(bitrate);
        }
    }
    
    private static String GetNextCommand()
    {
        String command = "";
        char temp;
        
        try
        {   
            temp = (char)System.in.read();
        
            while(temp != '\n')
            {
                if(temp != '\r')
                {
                    command += temp;
                }
                
                temp = (char)System.in.read();
            }
        }
        catch(Exception ex)
        {
            PrimeNetEncoder.writeLogln("Unexpected error reading from the console. " + ex.getMessage(), "");
            ex.printStackTrace();
        }
        
        return command;
    }
    
    public synchronized static void writeLogln(String line, String logname) 
    {
        String logFileName = "PrimeNetEncoder.txt";
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        Date today = Calendar.getInstance().getTime();        
        String reportDate = df.format(today);
        
        //System.out.println(line);
        
        if(logname.equalsIgnoreCase(""))
        {
            logFileName = "PrimeNetEncoder.txt";
        }
        else
        {
            logFileName = "PrimeNetEncoder-" + logname + ".txt";
        }
        
        try
        {
            FileOutputStream logfile = new FileOutputStream(logFileName, true);

            if(echoLogs)
            {
                System.out.println(line);
            }
            
            line = reportDate + " - " + line + "\r\n";
            logfile.write(line.getBytes());
            logfile.flush();
            logfile.close();
        }
        catch(Exception ex)
        {
            
        }
    }
    
    private static Property getProperty()
    {
        return PrimeNetEncoder.props;
    }
    
}
