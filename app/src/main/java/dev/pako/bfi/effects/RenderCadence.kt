package dev.pako.bfi.effects

/** Opt-in bounded shell diagnostic. No per-frame capture, file I/O or logging in ordinary sessions. */
internal class RenderCadence {
    @Volatile private var active=false
    private val rows=ArrayList<String>()
    private var coalesced=0
    @Synchronized fun start(){rows.clear();coalesced=0;active=true}
    fun record(requested:Long,consumed:Long,requestTime:Long,begin:Long,end:Long,black:Boolean,target:Long=0){
        if(active)synchronized(this){if(active && rows.size<4096)rows+="draw,$requested,$consumed,$requestTime,$begin,$end,$black,$target"}
    }
    fun nativeDraw(target:Long,black:Boolean){if(active)synchronized(this){if(active && rows.size<4096)rows+="native,$target,$target,0,${System.nanoTime()},0,$black,$target"}}
    fun updated(time:Long){if(active)synchronized(this){if(active && rows.size<4096)rows+="update,0,0,0,$time,0,false,0"}}
    fun dropped(){if(active)synchronized(this){coalesced++}}
    @Synchronized fun stop():String {active=false;return "# coalesced=$coalesced\nkind,requested,consumed,requestTime,begin,end,black,target\n"+rows.joinToString("\n")+"\n"}
}
