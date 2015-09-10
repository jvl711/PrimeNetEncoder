/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jvl.primenetencoder;

/**
 * This is a wrapper class.  It is meant to allow me to have the same codebase work
 * against two different versions of Sage.  Only this class will differ between
 * V7 complaint and V9 compliant PrimeNetEncoder
 * @author jvl711
 */
public class SageTVWrapper 
{

    //This will do nothing in V7
    public static void CallSageDiscovery()
    {
        /*
            PrimeNetEncoder.writeLogln("Tell Sage to rediscover NetworkEncoders", "");
    
            MMC mmc = MMC.getInstance();
        
            if(mmc != null)
            {
                CaptureDeviceManager[] cdm = mmc.getCaptureDeviceManagers();

                for(int i = 0; i < cdm.length; i++)
                {
                    if(cdm[i] instanceof sage.NetworkEncoderManager)
                    {
                        PrimeNetEncoder.writeLogln("Invocing redetectCaptureDevices on NetworkEncoderManager", "");
                        mmc.redetectCaptureDevices(cdm[i]);
                    }
                }
            }
            else
            {
                PrimeNetEncoder.writeLogln("MMC is not intatiated.  Might not be running inside of sage", "");
            }

        */
    }
    
}
