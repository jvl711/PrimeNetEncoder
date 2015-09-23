package jvl.primenetencoder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.io.File;
import java.util.Random;

public class Tuner extends Thread
{
    /*
     * Constants
    */
    public static final int FFMPEG_DEFAULT_PROBESIZE = 5000000;
    public static final int FFMPGEG_DEFAULT_ANALYZE_DURATION = 5000000;
    public static final int FFMPGEG_DEFAULT_DELAY = 500;
    
    
    private static final String PROTOCOL_VERSION = "3.1";
    
    private String name;
    private String id;
    private int tunerNumber;
    private int port;
    private int transcoderPort;
    private int tunerIndex;
    private boolean enabled;
    private String hdhomerunconfigPath;
    private boolean connected;
    private String localIPAddress;
    private String remoteIPAddress;
    private String logName;
    
    /*
    * Variables for tuner locking
    */
    private int lockKey = -1;
    
    /*
    * Variables for stream transfer
    */
    private boolean mediaServerTransfer;
    private boolean directStream;
    private TunerOutput tunerOutput;
    
    
    /*
     * Variables for ffmpeg
    */
    private String ffmpegPath;
    private int ffmpegDelay;
    private int ffmpegProbeSize;
    private int ffmpegAnalzyeDuration;
    private boolean ffmpegSTDINTransfer;
    
    /*
     * Variables for the current recording
    */
    private Process encoderProcess;
    private boolean recording;
    private String recordingFile;
    private String recordingQuality;
    private String recordingChannel;

    /*
     * Variables for transcoding
    */
    private boolean transcodeEnabled;
    private int averageBitrate;
    private boolean deinterlaceEnabled;
    private String transcodePreset;
    private String transcoderCodec;
    private String scaling;
    
    
    public Tuner(int tunerIndex, String name, String id, int tunerNumber, int port, int transcoderPort
            , boolean enabled, String hdhomerunconfigPath, String ffmpegPath, boolean useMediaServer, boolean useDirectStream)
    {
        this.tunerIndex = tunerIndex;
        this.name = name;
        this.id = id;
        this.tunerNumber = tunerNumber;
        this.port = port;
        this.transcoderPort = transcoderPort;
        this.enabled = enabled;
        this.connected =false;
        this.recording = false;
        this.hdhomerunconfigPath = hdhomerunconfigPath;
        this.logName = "tuner" + this.tunerIndex;
        
        this.ffmpegPath = ffmpegPath;
        this.ffmpegDelay = FFMPGEG_DEFAULT_DELAY;
        this.ffmpegProbeSize = Tuner.FFMPEG_DEFAULT_PROBESIZE;
        this.ffmpegAnalzyeDuration = Tuner.FFMPGEG_DEFAULT_ANALYZE_DURATION;
        this.ffmpegSTDINTransfer = true;
        
        this.mediaServerTransfer = useMediaServer;
        this.directStream = useDirectStream;
        
        try
        {
            this.localIPAddress = this.localIPAddress = InetAddress.getLocalHost().getHostAddress();
            PrimeNetEncoder.writeLogln("Setting local IP address to: " + this.localIPAddress, this.logName);
        }
        catch(IOException ex)
        {
            PrimeNetEncoder.writeLogln("Error determining IP address of the machine!  This is needed to determine which IP the HDHomeRun needs to connect to send the stream.", this.logName);
        }
    
        //Variables for transcoding
        this.transcodeEnabled = false;
        this.averageBitrate = 4000;
        this.deinterlaceEnabled = true;
        this.transcodePreset = "ultrafast";
        this.transcoderCodec = "libx264";
        this.scaling = "";
        
    }
    
    @Override
    public void run()
    {
        ServerSocket binding = null;
        Socket client = null;
        boolean listening = true;
        
        PrimeNetEncoder.writeLogln("Starting encoder thread: " + this.getTunerId()+ "-" + this.getTunerNumber(), this.logName);
        
        try
        {
            binding = new ServerSocket(this.port, 1);
        }
        catch(IOException ex)
        {
            PrimeNetEncoder.writeLogln("Error binding (" + this.getTunerId()+ " " + this.getTunerNumber() + "): " + ex.getMessage(), this.logName);
            listening = false;
        }
        
        while(listening)
        {
            try 
            {
                this.connected = false;
                
                client = binding.accept();
                
                remoteIPAddress = client.getInetAddress().getHostAddress();
                
                this.connected = true;
                
                BufferedWriter output = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
                BufferedReader input = new BufferedReader(new InputStreamReader(client.getInputStream()));
                
                String command = input.readLine();
                
                while(command != null)
                {
                    
                    if(command.equals("VERSION"))
                    {
                        output.write(Tuner.PROTOCOL_VERSION + "\r\n");
                        output.flush();
                    }
                    else if (command.equalsIgnoreCase("PROPERTIES"))
                    {
                        String props = this.getPropertiesMessage();
                        
                        props = props + "OK\r\n";
                        
                        output.write(props);
                        output.flush();
                    }
                    else if(command.equals("NOOP"))
                    {
                        output.write("OK\r\n");
                        output.flush();
                    }
                    else if(command.startsWith("START"))
                    {
                        PrimeNetEncoder.writeLogln("Start commmand received: " + command, logName);
                        
                        String[] parts = command.split("\\|");
                        
                        if(Tuner.PROTOCOL_VERSION == "2.1")
                        {
                            if(parts.length != 5)
                            {
                                System.out.println("Error: SageTV start recording command does not appear to be in the proper format.  Please attempt restarting SageTV.");
                                output.write("ERROR\r\n");
                                output.flush();
                            }
                            else
                            {
                                //String tuner_name = parts[0];
                                String channel = parts[1];
                                //String time = parts[2];
                                String filePath = parts[3];
                                String quality = parts[4];

                                this.startRecording(channel, null, filePath, quality);
                            }
                        }
                        else if(Tuner.PROTOCOL_VERSION == "3.1")
                        {
                            if(parts.length != 6)
                            {
                                System.out.println("Error: SageTV start recording command does not appear to be in the proper format.  Please attempt restarting SageTV.");
                                output.write("ERROR\r\n");
                                output.flush();
                            }
                            else
                            {
                                //String tuner_name = parts[0];
                                String uploadID = parts[1];
                                String channel = parts[2];
                                //Time parts[3]
                                String filePath = parts[4];
                                String quality = parts[5];

                                this.startRecording(channel, uploadID, filePath, quality);
                            }
                        }
                        output.write("OK\r\n");
                        output.flush();
                    }
                    else if(command.startsWith("STOP"))
                    {
                        this.stopRecording();
                        
                        output.write("OK\r\n");
                        output.flush();
                    }
                    else if(command.startsWith("GET_FILE_SIZE"))
                    {                
                        String filePath  = command.replace("GET_FILE_SIZE ", "");

                        long size = this.getFileSize(filePath);
                        
                        output.write(size + "\r\n");
                        output.flush();
                    }
                    else if (command.startsWith("QUIT"))
                    {
                        /*
                        * Close input and output stream and break from loop to
                        * accept a new connection
                        */
                        input.close();
                        output.close();
                        break;
                    }
                    else
                    {
                        PrimeNetEncoder.writeLogln("Unknown Command: " + command, this.logName);
                        output.write("OK\r\n");
                        output.flush();
                    }
                    
                    command = input.readLine();
                }
            } 
            catch (Exception ex) 
            {
                if(isRecording())
                {
                    try
                    {
                        this.stopRecording();
                    }
                    catch(Exception ex1)
                    {
                        
                    }
                }
                
                ex.printStackTrace();
            }
        }
    }
    
    private void startRecording(String channel, String uploadID, String filePath, String quality)
    {
        PrimeNetEncoder.writeLogln("-------------------------------------------------------------------------------", this.logName);
        PrimeNetEncoder.writeLogln("Switching Channel for Tuner: " + this.getTunerId()+ " " + this.getTunerNumber(), this.logName);
        PrimeNetEncoder.writeLogln("-------------------------------------------------------------------------------", this.logName);
        PrimeNetEncoder.writeLogln("Channel: " + channel, this.logName);
        PrimeNetEncoder.writeLogln("File: " + new File(filePath).getName(), this.logName);
        PrimeNetEncoder.writeLogln("UploadID: " + uploadID, this.logName);
        PrimeNetEncoder.writeLogln("Quality: " + quality, this.logName);
        PrimeNetEncoder.writeLogln("Local IP: " + this.localIPAddress, this.logName);
        PrimeNetEncoder.writeLogln("Listening Port: " + this.port, this.logName);
        PrimeNetEncoder.writeLogln("Stream listening Port: " + this.transcoderPort, this.logName);

        try
        {
            //Check to see if the tuner is locked
            PrimeNetEncoder.writeLogln("Checking to see if the tuner is locked.", logName);
            if(this.isTunerLocked())
            {
                PrimeNetEncoder.writeLogln("Tuner is locked.  Force unlocking the tuner.", logName);
                //For right now I am always going to force unlock.
                this.clearTunerLock(true);
            }
            
            this.setTunerLock();
            this.setTunerChannel(channel);
            this.setTunerStream("udp://" + this.localIPAddress + ":" + this.transcoderPort);
        
            /*
             * Unless doing direct stream, start the ffmpeg process.  ffmpeg
             * will either do a stream copy of a transcode
            */
            if(!this.directStream)
            {
                String output = filePath;
                String input = "udp://" + this.localIPAddress + ":" + this.transcoderPort;
                
                //if(this.mediaServerTransfer)
                //{
                output = "-";
                //}
                
                if(this.ffmpegSTDINTransfer) //This is defaulted to true right now
                {
                    input = "pipe:0";
                }
                
                String[] transcoderCmd = getTranscodeCmd(input, output);
                encoderProcess = Runtime.getRuntime().exec(transcoderCmd);
                
            }

            if(this.mediaServerTransfer && this.directStream)
            {
                PrimeNetEncoder.writeLogln("Starting TunerOutput thread for Direct Stream to SageTV MediaServer", this.logName);
                this.tunerOutput = new TunerOutput(this, this.transcoderPort, filePath, this.logName, uploadID, remoteIPAddress);
                this.tunerOutput.setPriority(Thread.MAX_PRIORITY);
                this.tunerOutput.start();
            }
            else if(this.mediaServerTransfer && this.ffmpegSTDINTransfer)
            {
                PrimeNetEncoder.writeLogln("Starting TunerOutput thread for stdin to ffmpeg then stdout to SageTV MediaServer", this.logName);
                this.tunerOutput = new TunerOutput(this, this.transcoderPort, encoderProcess.getOutputStream(), encoderProcess.getInputStream(), filePath, this.logName, uploadID, remoteIPAddress);
                this.tunerOutput.setPriority(Thread.MAX_PRIORITY);
                this.tunerOutput.start();
            }
            //Think I might deprecte this option
            /*
            else if(this.mediaServerTransfer)
            {
                PrimeNetEncoder.writeLogln("Starting TunerOutput thread for ffmpeg Stream to SageTV MediaServer", this.logName);
                this.tunerOutput = new TunerOutput(this, new BufferedInputStream(encoderProcess.getInputStream()), filePath, this.logName, uploadID, remoteIPAddress);
                this.tunerOutput.start();
            }
            */
            else
            {
                PrimeNetEncoder.writeLogln("Starting TunerOutput thread for ffmpeg CIFS output to SageTV", this.logName);
                this.tunerOutput = new TunerOutput(this, this.transcoderPort, encoderProcess.getOutputStream(), encoderProcess.getInputStream(), filePath, logName);
                this.tunerOutput.setPriority(Thread.MAX_PRIORITY);
                this.tunerOutput.start();
            }
            
            //Give a little time for the port to open
            if(!this.directStream)
            {
                PrimeNetEncoder.writeLogln("Sleeping to allow ffmpeg to fully launch: " + this.ffmpegDelay, this.logName);
                Thread.sleep(this.ffmpegDelay);
            }
            
        }
        catch(Exception ex)
        {
            PrimeNetEncoder.writeLogln("ERROR STARTING RECORDING!!!", this.logName);
            PrimeNetEncoder.writeLogln(ex.getMessage(), this.logName);
            ex.printStackTrace(System.out);
        }
        finally
        {
            //Assume the recording started even if an error is thrown.
            this.recording = true;
            this.recordingFile = filePath;
            this.recordingChannel = channel;
            this.recordingQuality = quality;
        }
        
    }
    
    private void stopRecording()
    {
        PrimeNetEncoder.writeLogln("-------------------------------------------------------------------------------", this.logName);
        PrimeNetEncoder.writeLogln("Stopping Recording: " + this.getTunerId()+ " " + this.getTunerNumber(), this.logName);
        PrimeNetEncoder.writeLogln("-------------------------------------------------------------------------------", this.logName);
        
        if(isRecording())
        {
            try
            {
                
                PrimeNetEncoder.writeLogln("Stopping the TunerOutput thread", this.logName);
                this.tunerOutput.stopProcessing();

                try
                {
                    this.clearTunerStream();
                }
                catch(Exception ex)
                {
                    PrimeNetEncoder.writeLogln("Error: Unexcpected error stopping tuner stream - " + ex.getMessage(), logName);
                }
                try
                {
                    this.clearTunerChannel();
                }
                catch(Exception ex)
                {
                    PrimeNetEncoder.writeLogln("Error: Unexcpected error clearing tuner channel - " + ex.getMessage(), logName);
                }
                try
                {
                    this.clearTunerLock(true); //Do forced to be on the safe side
                }
                catch(Exception ex)
                {
                    PrimeNetEncoder.writeLogln("Error: Unexcpected error clearing tuner lock - " + ex.getMessage(), logName);
                }
                
		if(this.encoderProcess != null)
                {
                    PrimeNetEncoder.writeLogln("Stopping the encoder process", this.logName);
                    this.encoderProcess.destroy(); //Forcibly();

                    PrimeNetEncoder.writeLogln("waiting for the process to stop", this.logName);
                    this.encoderProcess.waitFor();
                }
                
                PrimeNetEncoder.writeLogln("Recording stopped", this.logName);
                
            }
            catch(Exception ex)
            {
                PrimeNetEncoder.writeLogln("Error: Unexpected error etopping recording - " + ex.getMessage(), this.logName);
                ex.printStackTrace(System.out);
            }
            finally
            {
                this.encoderProcess = null;
                this.recordingFile = "";
                this.recordingChannel = "";
                this.recordingQuality = "";
                this.recording = false;
                
                //The server appears to continue to check the file size.  I am going to leave this out for now
                //if(!this.tunerOutput.isAlive())
                //{
                //    tunerOutput = null;
                //}
            }
        }                
    }
    
    private String[] getTranscodeCmd(String input, String output)
    {
        String[] transcoderCmd;
        
        if(this.transcodeEnabled)
        {
            PrimeNetEncoder.writeLogln("Transcoding stream to SageTV as h.264", this.logName);
            //Transcode commane
            if(this.isDeinterlaceEnabled())
            {
                if(this.scaling.trim().equalsIgnoreCase(""))
                {
                    transcoderCmd = new String []{this.ffmpegPath, "-y", "-v", "quiet", "-analyzeduration", this.getFfmpegAnalzyeDuration() + "", "-probesize", this.getFfmpegProbeSize() + "","-i", input, "-f", "mpegts", "-vcodec", this.transcoderCodec, "-preset", this.getTranscodePreset(), "-b:v", this.getAverageBitrate() + "k", "-deinterlace", "-acodec", "copy", output};
                }
                else
                {
                    transcoderCmd = new String []{this.ffmpegPath, "-y", "-v", "quiet", "-analyzeduration", this.getFfmpegAnalzyeDuration() + "", "-probesize", this.getFfmpegProbeSize() + "","-i", input, "-f", "mpegts", "-vcodec", this.transcoderCodec, "-preset", this.getTranscodePreset(), "-b:v", this.getAverageBitrate() + "k", "-deinterlace", "-vf", "scale=" + this.scaling, "-acodec", "copy", output};
                }
            }
            else
            {
                if(this.scaling.trim().equalsIgnoreCase(""))
                {
                    transcoderCmd = new String []{this.ffmpegPath, "-y", "-v", "quiet", "-analyzeduration", this.getFfmpegAnalzyeDuration() + "", "-probesize", this.getFfmpegProbeSize() + "","-i", input, "-f", "mpegts", "-vcodec", this.transcoderCodec, "-preset", this.getTranscodePreset(), "-b:v", this.getAverageBitrate() + "k", "-acodec", "copy", output};
                }
                else
                {
                    transcoderCmd = new String []{this.ffmpegPath, "-y", "-v", "quiet", "-analyzeduration", this.getFfmpegAnalzyeDuration() + "", "-probesize", this.getFfmpegProbeSize() + "", "-i", input, "-f", "mpegts", "-vcodec", this.transcoderCodec, "-preset", this.getTranscodePreset(), "-b:v", this.getAverageBitrate() + "k", "-vf", "scale=" + this.scaling, "-acodec", "copy", output};
                }
            }                    
        }
        else
        {
            PrimeNetEncoder.writeLogln("Passing stream to SageTV unaltered (ffmpeg stream copy)", this.logName);
            
            transcoderCmd = new String []{this.ffmpegPath, "-y", "-v", "quiet","-analyzeduration", this.getFfmpegAnalzyeDuration() + "", "-probesize", this.getFfmpegProbeSize() + "", "-i", input, "-f", "mpegts", "-vcodec", "copy", "-acodec", "copy", output};
            
        }
        
        return transcoderCmd;
    }

    public void resetRecording()
    {
        try
        {
            this.setTunerChannel(this.getRecordingChannel());
            this.setTunerStream("udp://" + this.localIPAddress + ":" + this.transcoderPort);
        }
        catch(Exception ex) { }
            
    }
    
    private void setTunerStream(String url) throws IOException, InterruptedException
    {
        String[] sendStreamCmd;
        String output;
                
            PrimeNetEncoder.writeLogln("Send stream to UDP port: " + this.transcoderPort, this.logName);
        
        if(this.lockKey != -1)
        {
            PrimeNetEncoder.writeLogln("\tUsing Lockkey: " + this.lockKey, this.logName);
            sendStreamCmd = new String[] {this.hdhomerunconfigPath, this.id, "key", this.lockKey + "","set" ,"/tuner" + this.tunerNumber + "/target",  url};
        }    
        else
        {
            sendStreamCmd = new String[] {this.hdhomerunconfigPath, this.id, "set" ,"/tuner" + this.tunerNumber + "/target",  "udp://" + this.localIPAddress + ":" + this.transcoderPort};        
        }
        
        Process sendStreamProcess = Runtime.getRuntime().exec(sendStreamCmd);
        
        BufferedReader input = new BufferedReader(new InputStreamReader(sendStreamProcess.getInputStream()));
        output = input.readLine();
        PrimeNetEncoder.writeLogln("\tCommand output: " + output, logName);
        
        sendStreamProcess.waitFor();
    }
    
    private void clearTunerStream() throws IOException, InterruptedException
    {
        setTunerStream("none");
    }
    
    private void setTunerChannel(String channel) throws IOException, InterruptedException
    {
        String[] swichChannelCmd;
        String output = "";
        
        PrimeNetEncoder.writeLogln("Switch channel: " + channel, this.logName);
        
        if(this.lockKey != -1)
        {
            
            PrimeNetEncoder.writeLogln("\tUsing Lockkey: " + this.lockKey, this.logName);
            swichChannelCmd = new String[] {this.hdhomerunconfigPath, this.id, "key", this.lockKey + "","set" , "/tuner" + this.tunerNumber + "/vchannel", channel};
        }
        else
        {
            swichChannelCmd = new String[] {this.hdhomerunconfigPath, this.id, "set" , "/tuner" + this.tunerNumber + "/vchannel", channel};
        }
        
        Process channelChangeProcess = Runtime.getRuntime().exec(swichChannelCmd);
        
        BufferedReader input = new BufferedReader(new InputStreamReader(channelChangeProcess.getInputStream()));
        output = input.readLine();
        PrimeNetEncoder.writeLogln("\tCommand output: " + output, logName);
        
        channelChangeProcess.waitFor();
    }
    
    private void clearTunerChannel() throws IOException, InterruptedException
    {
        this.setTunerChannel("none");
    }
    
    private void setTunerLock()
    {
        Random rand = new Random();
        int lockkey = Math.abs(rand.nextInt());
        
        setTunerLock(lockkey);
    }
    
    private void setTunerLock(int number)
    {
        this.lockKey = number;
        
        try 
        {
            String[] hdhomerunCmd = {this.hdhomerunconfigPath, this.id, "set" , "/tuner" + this.tunerNumber + "/lockkey", number + ""};
            Process hdhomerunProcess = Runtime.getRuntime().exec(hdhomerunCmd);
            
            hdhomerunProcess.waitFor();
        } 
        catch (IOException ex) 
        {
            System.out.println("Unexpected Error: Error setting lock status from HDHomeRun_config");
        }
        catch (InterruptedException ex2)
        {
            
        }
    }
    
    private void clearTunerLock(boolean force)
    {
        String value = "none";
        
        if(force)
        {
            value = "force";
        }
        
        try 
        {
            String[] hdhomerunCmd;
            
            if(force)
            {
                hdhomerunCmd = new String[]{this.hdhomerunconfigPath, this.id, "set" , "/tuner" + this.tunerNumber + "/lockkey", "force"};
            }
            else
            {
                hdhomerunCmd = new String[]{this.hdhomerunconfigPath, this.id, "key", this.lockKey + "", "set" , "/tuner" + this.tunerNumber + "/lockkey", "force"};
            }
            
            Process hdhomerunProcess = Runtime.getRuntime().exec(hdhomerunCmd);
            
            hdhomerunProcess.waitFor();
            this.lockKey = -1;
        } 
        catch (IOException ex) 
        {
            System.out.println("Unexpected Error: Error clearing lock status from HDHomeRun_config");
        }
        catch (InterruptedException ex2)
        {
            
        }
    }
    
    public boolean isTunerLocked()
    {
        String lockStatus;
        
        try
        {
            String[] hdhomerunCmd = {this.hdhomerunconfigPath, this.id, "get" , "/tuner" + this.tunerNumber + "/lockkey"};
            Process hdhomerunProcess = Runtime.getRuntime().exec(hdhomerunCmd);
            
            BufferedReader input = new BufferedReader(new InputStreamReader(hdhomerunProcess.getInputStream()));
            lockStatus = input.readLine();
            
            return !lockStatus.trim().equalsIgnoreCase("none");
            
        }
        catch(IOException ex)
        {
            System.out.println("Unexpected Error: Error checking lock status from HDHomeRun_config " + ex.getMessage());
            return false;
        }
    }
    
    public String getTunerLockSource()
    {
        String lockStatus = "";
        
        try
        {
            String[] hdhomerunCmd = {this.hdhomerunconfigPath, this.id, "get" , "/tuner" + this.tunerNumber + "/lockkey"};
            Process hdhomerunProcess = Runtime.getRuntime().exec(hdhomerunCmd);
            
            BufferedReader input = new BufferedReader(new InputStreamReader(hdhomerunProcess.getInputStream()));
            lockStatus = input.readLine();

        }
        catch(IOException ex)
        {
            System.out.println("Unexpected Error: Error getting lock source from HDHomeRun_config " + ex.getMessage());
        }
        
        return lockStatus.trim();
    }
    
    private long getFileSize(String filePath)
    {
        long size = 0;
        
        if(!this.isMediaServerTransfer())
        {
            File recording = new File(filePath);
            
            if(recording.exists())
            {
                size = recording.length();
            }
        }
        else
        {
            size = this.tunerOutput.getFileSize();
        }
        
        return size;
    }
    
    private String getPropertiesMessage()
    {
        String props = "";
        
        //props += "0\r\n";
        
        PrimeNetEncoder.writeLogln("Get property request recieved", logName);
        
        props += "31\r\n";
        
        /*
        props += "mmc/encoders/1234567/1/0/encode_digital_tv_as_program_stream=false\r\n";
        props += "mmc/encoders/1234567/1/0/hue=-1\r\n";
        props += "mmc/encoders/1234567/1/0/tuning_mode=Cable\r\n";
        props += "mmc/encoders/1234567/capture_config=299008\r\n";
        props += "mmc/encoders/1234567/delay_to_wait_after_tuning=0\r\n";
        props += "mmc/encoders/1234567/encoder_host=" + this.localIPAddress + ":" + this.port + "\r\n";
        props += "mmc/encoders/1234567/fast_network_encoder_switch=false\r\n";
        props += "mmc/encoders/1234567/never_stop_encoding=false\r\n";
        props += "mmc/encoders/1234567/video_capture_device_name=" + this.getTunerName() + "\r\n";
        props += "mmc/encoders/1234567/video_capture_device_num=0\r\n";
        props += "mmc/encoders/1234567/video_encoding_params=Great\r\n";
        */
        
        props += "mmc/encoders/31035317/100/0/brightness=-1\r\n";
        props += "mmc/encoders/31035317/100/0/broadcast_standard=\r\n";
        props += "mmc/encoders/31035317/100/0/contrast=-1\r\n";
        props += "mmc/encoders/31035317/100/0/device_name=" + this.getTunerName() + "\r\n";
        props += "mmc/encoders/31035317/100/0/encode_digital_tv_as_program_stream=false\r\n";
        props += "mmc/encoders/31035317/100/0/hue=-1\r\n";
        props += "mmc/encoders/31035317/100/0/saturation=-1\r\n";
        props += "mmc/encoders/31035317/100/0/sharpness=-1\r\n";
        props += "mmc/encoders/31035317/100/0/tuning_mode=Cable\r\n";
        props += "mmc/encoders/31035317/100/0/tuning_plugin=\r\n";
        props += "mmc/encoders/31035317/100/0/tuning_plugin_port=0\r\n";
        props += "mmc/encoders/31035317/100/0/video_crossbar_index=0\r\n";
        props += "mmc/encoders/31035317/100/0/video_crossbar_type=100\r\n";
        props += "mmc/encoders/31035317/audio_capture_device_name=\r\n";
        props += "mmc/encoders/31035317/capture_config=2000\r\n";
        props += "mmc/encoders/31035317/default_device_quality=Great\r\n";
        props += "mmc/encoders/31035317/delay_to_wait_after_tuning=0\r\n";
        props += "mmc/encoders/31035317/device_class=\r\n";
        props += "mmc/encoders/31035317/encoder_host=" + this.localIPAddress + ":" + this.port + "\r\n";
        props += "mmc/encoders/31035317/encoder_merit=0\r\n";
        props += "mmc/encoders/31035317/encoding_host=" + this.localIPAddress + ":" + this.port + "\r\n";
        props += "mmc/encoders/31035317/fast_network_encoder_switch=false\r\n";
        props += "mmc/encoders/31035317/forced_video_storage_path_prefix=\r\n";
        props += "mmc/encoders/31035317/last_cross_index=0\r\n";
        props += "mmc/encoders/31035317/last_cross_type=100\r\n";
        props += "mmc/encoders/31035317/live_audio_input=\r\n";
        props += "mmc/encoders/31035317/multicast_host=\r\n";
        props += "mmc/encoders/31035317/never_stop_encoding=false\r\n";
        props += "mmc/encoders/31035317/video_capture_device_name=" + this.getTunerName() + "\r\n";
        props += "mmc/encoders/31035317/video_capture_device_num=0\r\n";
        props += "mmc/encoders/31035317/video_encoding_params=Great\r\n";
        
        
        PrimeNetEncoder.writeLogln("Sending: \n" + props, logName);
        
        /*
        mmc/encoders/1012343954/1/0/available_channels=
        mmc/encoders/1012343954/1/0/brightness=-1
        mmc/encoders/1012343954/1/0/broadcast_standard=
        mmc/encoders/1012343954/1/0/contrast=-1
        mmc/encoders/1012343954/1/0/device_name=
        mmc/encoders/1012343954/1/0/encode_digital_tv_as_program_stream=false
        mmc/encoders/1012343954/1/0/hue=-1
        mmc/encoders/1012343954/1/0/last_channel=876
        mmc/encoders/1012343954/1/0/provider_id=1981238176869
        mmc/encoders/1012343954/1/0/saturation=-1
        mmc/encoders/1012343954/1/0/sharpness=-1
        mmc/encoders/1012343954/1/0/tuning_mode=Cable
        mmc/encoders/1012343954/1/0/tuning_plugin=
        mmc/encoders/1012343954/1/0/tuning_plugin_port=0
        mmc/encoders/1012343954/1/0/video_crossbar_index=0
        mmc/encoders/1012343954/1/0/video_crossbar_type=100
        mmc/encoders/1012343954/audio_capture_device_name=
        mmc/encoders/1012343954/broadcast_standard=
        mmc/encoders/1012343954/capture_config=299008
        mmc/encoders/1012343954/default_device_quality=
        mmc/encoders/1012343954/delay_to_wait_after_tuning=0
        mmc/encoders/1012343954/device_class=
        mmc/encoders/1012343954/encoder_merit=0
        mmc/encoders/1012343954/encoding_host=192.168.0.15\:7000
        mmc/encoders/1012343954/fast_network_encoder_switch=false
        mmc/encoders/1012343954/forced_video_storage_path_prefix=
        mmc/encoders/1012343954/last_cross_index=0
        mmc/encoders/1012343954/last_cross_type=100
        mmc/encoders/1012343954/live_audio_input=
        mmc/encoders/1012343954/multicast_host=
        mmc/encoders/1012343954/never_stop_encoding=false
        mmc/encoders/1012343954/video_capture_device_name=HDHomeRun Prime Tuner 0
        mmc/encoders/1012343954/video_capture_device_num=0
        mmc/encoders/1012343954/video_encoding_params=Great
        */
        
        return props;
    }
    
    public String getTunerName()
    {
        return this.name;
    }
    
    public int getTunerNumber()
    {
        return this.tunerNumber;
    }

    public String getTunerId() 
    {
        return id;
    }
    
    public int getTunerIndex()
    {
        return tunerIndex;
    }

    public int getPort() 
    {
        return port;
    }

    public boolean isEnabled() 
    {
        return enabled;
    }

    public boolean isRecording() 
    {
        return recording;
    }

    public String getRecordingFile() 
    {
        return recordingFile;
    }
    
    public String getRecordingQuality() 
    {
        return recordingQuality;
    }
    
    public String getRecordingChannel() 
    {
        return recordingChannel;
    }
    
    public boolean isTranscodeEnabled() 
    {
        return transcodeEnabled;
    }

    public void setTranscodeEnabled(boolean transcodeEnabled) 
    {
        this.transcodeEnabled = transcodeEnabled;
    }

    public int getAverageBitrate() 
    {
        return averageBitrate;
    }

    public void setAverageBitrate(int averageBitrate) 
    {
        this.averageBitrate = averageBitrate;
    }

    public boolean isDeinterlaceEnabled() 
    {
        return deinterlaceEnabled;
    }

    public void setDeinterlaceEnabled(boolean deinterlaceEnabled) {
        this.deinterlaceEnabled = deinterlaceEnabled;
    }

    public String getTranscodePreset() 
    {
        return transcodePreset;
    }
    
    public void setTranscodePreset(String transcodePreset) 
    {
        this.transcodePreset = transcodePreset;
    }
    
    public String getScaling() 
    {
        return this.scaling;
    }
    
    public void setScaling(String scaling) 
    {
        this.scaling = scaling;
    }
    
    public int getffmpegDelay()
    {
        return this.ffmpegDelay;
    }
    
    public void setffmpegDelay(int ffmpegDelay)
    {
        this.ffmpegDelay = ffmpegDelay;
    }
    
    public String getTranscodeCodec()
    {
        return this.transcoderCodec;
    }
    
    public void setTranscodeCodec(String transcoderCodec)
    {
        this.transcoderCodec = transcoderCodec;
    }
    
    public int getFfmpegProbeSize() 
    {
        return ffmpegProbeSize;
    }

    public void setFfmpegProbeSize(int ffmpegProbeSize) 
    {
        this.ffmpegProbeSize = ffmpegProbeSize;
    }

    public int getFfmpegAnalzyeDuration() 
    {
        return ffmpegAnalzyeDuration;
    }

    public void setFfmpegAnalzyeDuration(int ffmpegAnalzyeDuration) 
    {
        this.ffmpegAnalzyeDuration = ffmpegAnalzyeDuration;
    }

    public boolean isConnected() 
    {
        return connected;
    }

    public boolean isMediaServerTransfer() 
    {
        return mediaServerTransfer;
    }

    public void setMediaServerTransfer(boolean mediaServerTransfer) 
    {
        this.mediaServerTransfer = mediaServerTransfer;
    }

    public boolean isFfmpegUseSTDIN()
    {
        return this.ffmpegSTDINTransfer;
    }
    
    public void setFfmegUseSTDIN(boolean value)
    {
        this.ffmpegSTDINTransfer = value;
    }
    
    public boolean isDirectStream()
    {
        return directStream;
    }
    
    public void setDirectStream(boolean value)
    {
        this.directStream = value;
    }

    public TunerOutput getTunerOutput() 
    {
        return tunerOutput;
    }
    
    
}
