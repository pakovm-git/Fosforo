package dev.pako.bfi.effects

/** One image in flight; diagnostic feedback and stopped sessions cannot queue more work. */
class ContinuousGate {
    private var lastTimestamp=Long.MIN_VALUE
    private var processing=false
    var stopped=false;private set
    fun observe(timestamp:Long,tagged:Boolean):Boolean {
        if(stopped || timestamp<=lastTimestamp)return false
        lastTimestamp=timestamp
        if(tagged){stop();return false}
        if(processing)return false
        processing=true
        return true
    }
    fun processed():Boolean {
        if(stopped || !processing)return false
        processing=false
        return true
    }
    fun stalled(haveFrame:Boolean,elapsedMillis:Long):Boolean =
        elapsedMillis>3000 && (!haveFrame || processing)
    fun stop(){stopped=true;processing=false}
}
