
package jvl.primenetencoder;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

/**
 * This class is responsible for listening to UDP discovery requests by SageTV
 * servers.  When a request is received a response packet is sent for every
 * tuner that is enabled.  This thread is meant to stay running in the background
 * for as long as the PrimeNetEncoder is running.
 * 
 * @author jvl711
 */
public class Discovery extends Thread
{
    private static final int socketTimeout = 10000;
    private final int port;
    private ArrayList<Tuner> tuners;
    private final String logName;
    private boolean listening;
    
    public Discovery(int port, ArrayList<Tuner> tuners)
    {
        this.port = port;
        this.tuners = tuners;
        this.logName = "discovery";
        this.listening = true;
    }
    
    @Override
    public void run()
    {
        DatagramSocket socket = null;
        DatagramPacket packet = null;
                
        PrimeNetEncoder.writeLogln("Discovery thread starting", logName);

        while(this.listening)
        {
            try
            {
                if(socket == null)
                {
                    socket = new DatagramSocket(this.port);
                }
                
                socket.setSoTimeout(Discovery.socketTimeout);
                
                if(packet == null)
                {
                    packet = new DatagramPacket(new byte[512], 512);
                }
                
                socket.receive(packet);
                
                if(packet.getLength() >= 6);
                {
                    byte[] data = packet.getData();
                    
                    if(data[0] == 83 && data[1] == 84 && data[2] == 78)
                    {
                        PrimeNetEncoder.writeLogln("Received discovery packet", logName);
                        
                        for(int i = 0; i < tuners.size(); i++)
                        {
                            //Only send a response if the tuner is enabled and does not have an active connection
                            if(tuners.get(i).isEnabled() && !tuners.get(i).isConnected())
                            {
                                data[3] = 4; //Major version
                                data[4] = 1; //Minor version
                                data[5] = 0; //
                                data[6] = (byte)((tuners.get(i).getPort() >> 8) & 0xFF); //First byte of the port
                                data[7] = (byte)(tuners.get(i).getPort() & 0xFF); //Second byte of the port

                                //There is only 23 characters available for discovery
                                //This needs to be either the Hostname of IP of machine
                                //Running PrimeNetEncoder
                                String name = InetAddress.getLocalHost().getHostAddress();

                                if(name.length() > 23)
                                {
                                    name = name.substring(0, 22);
                                }

                                byte bname[] = name.getBytes(PrimeNetEncoder.CHARACTER_ENCODING);
                                data[8] = (byte)bname.length; 
                                System.arraycopy(bname, 0, data, 9, bname.length); //Copy tuner name into array

                                packet.setLength(9 + bname.length);

                                //packet.setData(output);
                                PrimeNetEncoder.writeLogln("Sending discovery response packet for Tuner " + this.tuners.get(i).getTunerName(), this.logName);
                                socket.send(packet);
                            }
                        }
                    }
                }

            }
            catch(SocketTimeoutException ex1)
            {
                //This is on purpose to check to see if we should continue to listen
            }
            catch(Exception ex2)
            {
                PrimeNetEncoder.writeLogln("Error in discovery thread: " + ex2.getMessage(), logName);
                PrimeNetEncoder.writeLogln("Exiting discovery thread", logName);
                break;
            }
            
        }
        
        PrimeNetEncoder.writeLogln("Discovery thread exiting", logName);
        
    }
    
    /**
     * Tells the thread to stop listening.  The socket will need to timeout
     * on the read before the thread stops.
     */
    public void stopListening()
    {
        this.listening = false;
    }
}
