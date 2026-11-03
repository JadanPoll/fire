package dev.jadan.fire.signalstudio;

/**
 * Pure, device-independent signal algorithm.
 * Manifold Playroom/Studio recipe vocabulary: sine, triangle, square,
 * frequency, gain and EMA smoothing.
 *
 * No device broker, daemon, native process or mandatory SDK required.
 */
public final class SignalMath {
    private SignalMath() {}

    public static double waveform(String kind,double cycles) {
        if (!Double.isFinite(cycles)) throw new IllegalArgumentException("phase");
        double phase=cycles-Math.floor(cycles);
        switch(kind){
            case "sine": return Math.sin(2.0*Math.PI*phase);
            case "triangle": return 1.0-4.0*Math.abs(phase-0.5);
            case "square": return phase<0.5?1.0:-1.0;
            default: throw new IllegalArgumentException("unknown waveform");
        }
    }

    public static double[] samples(String kind,double hertz,double gain,
                                   double smoothing,int count) {
        if(!(kind.equals("sine")||kind.equals("triangle")||kind.equals("square")) ||
           !Double.isFinite(hertz)||hertz<0.05||hertz>4 ||
           !Double.isFinite(gain)||gain<0.1||gain>3 ||
           !Double.isFinite(smoothing)||smoothing<0.05||smoothing>1 ||
           count<2||count>2048)
            throw new IllegalArgumentException("Invalid signal recipe");
        double[] out=new double[count];
        double previous=0;
        for(int i=0;i<count;i++){
            double seconds=3.0*i/(count-1);
            double raw=waveform(kind,seconds*hertz)*gain;
            previous=i==0?raw:smoothing*raw+(1.0-smoothing)*previous;
            out[i]=previous;
        }
        return out;
    }
}
