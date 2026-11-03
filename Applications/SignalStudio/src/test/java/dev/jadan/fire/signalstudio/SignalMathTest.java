package dev.jadan.fire.signalstudio;

import org.junit.Test;
import static org.junit.Assert.*;

public class SignalMathTest {
    @Test public void waveformsHaveKnownReferencePoints(){
        assertEquals(0,SignalMath.waveform("sine",0),1e-9);
        assertEquals(1,SignalMath.waveform("sine",.25),1e-9);
        assertEquals(-1,SignalMath.waveform("triangle",0),1e-9);
        assertEquals(1,SignalMath.waveform("triangle",.5),1e-9);
        assertEquals(1,SignalMath.waveform("square",.25),1e-9);
        assertEquals(-1,SignalMath.waveform("square",.75),1e-9);
    }
    @Test public void boundedEmaOutputAndDeterministicRecipe(){
        double[] first=SignalMath.samples("triangle",.7,1,.35,300);
        double[] second=SignalMath.samples("triangle",.7,1,.35,300);
        assertArrayEquals(first,second,0);
        for(double v:first)assertTrue(Double.isFinite(v) && v>=-1.0001 && v<=1.0001);
    }
    @Test public void noNaNOrExtremeWorkload(){
        assertInvalid("sine",0.7,1,Double.NaN,20);
        assertInvalid("triangle",Double.POSITIVE_INFINITY,1,.4,20);
        assertInvalid("square",.7,1,.4,1000000);
    }
    private static void assertInvalid(String type,double frequency,double gain,double smooth,int count){
        try{
            SignalMath.samples(type,frequency,gain,smooth,count);
            fail("expected invalid recipe");
        }catch(IllegalArgumentException expected){}
    }
}
