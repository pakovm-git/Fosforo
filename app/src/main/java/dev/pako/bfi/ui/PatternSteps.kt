package dev.pako.bfi.ui

import android.content.Context
import android.graphics.Rect
import android.text.*
import android.view.*
import android.widget.*
import dev.pako.bfi.R

/** Edits the existing alternating runs; invalid drafts never reach settings. */
internal class PatternSteps(context:Context,initial:List<String>,private val add:Button,private val changed:(String)->Unit):LinearLayout(context) {
    private val drafts=initial.toMutableList()
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    init {
        orientation=VERTICAL
        add.setOnClickListener{if(drafts.size<32){drafts.add("1");drafts.add("1");render();publish();add.post{add.requestRectangleOnScreen(Rect(0,0,add.width,add.height),false)}}}
        render()
    }
    fun snapshot()=ArrayList(drafts)
    fun replace(runs:List<Int>){drafts.clear();drafts.addAll(runs.map{it.toString()});render()}
    private fun publish(){val value=drafts.joinToString(", ");if(dev.pako.bfi.effects.BfiPattern.parse(value)!=null)changed(value)}
    private fun action(label:String,description:String=label,click:()->Unit)=Button(ContextThemeWrapper(context,R.style.HeaderAction),null,0).apply {
        text=label;minWidth=0;setPadding(0,0,0,0);contentDescription=description;setOnClickListener{click()}
    }
    private fun render() {
        removeAllViews();add.isEnabled=drafts.size<32
        for(pair in 0 until drafts.size/2) {
            if(pair>0)addView(View(context).apply{setBackgroundColor(context.getColor(R.color.bfi_outline))},LayoutParams(-1,dp(2)).apply{topMargin=dp(8)})
            val heading=LinearLayout(context).apply{orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            addView(heading,LayoutParams(-1,dp(56)))
            heading.addView(TextView(context).apply{text=if(pair==0)context.getString(R.string.primary_pair) else context.getString(R.string.pair_number,pair+1);textSize=16f;setTextColor(context.getColor(R.color.bfi_text_primary))},LayoutParams(0,-2,1f))
            if(drafts.size>2)heading.addView(action(context.getString(R.string.remove_pair_short),context.getString(R.string.remove_frame_pair,pair+1)){
                drafts.removeAt(pair*2+1);drafts.removeAt(pair*2);render();publish()
            }.apply{setBackgroundResource(R.drawable.danger_action);setTextColor(context.getColor(R.color.bfi_on_stop));textSize=16f},LayoutParams(dp(112),dp(56)))
            val pairRow=LinearLayout(context).apply{orientation=if(resources.configuration.screenWidthDp>=600)HORIZONTAL else VERTICAL;isBaselineAligned=false}
            addView(pairRow,LayoutParams(-1,-2))
            for(index in pair*2..pair*2+1) {
                val step=LinearLayout(context).apply{orientation=VERTICAL}
                pairRow.addView(step,if(pairRow.orientation==HORIZONTAL)LayoutParams(0,-2,1f).apply{if(index%2==0)marginEnd=dp(8)} else LayoutParams(-1,-2))
                val label=context.getString(if(index%2==0)R.string.visible_step else R.string.black_step)
                step.addView(TextView(context).apply{text=label;textSize=14f;setTextColor(context.getColor(R.color.bfi_text_secondary))})
                val row=LinearLayout(context).apply{orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setBackgroundResource(R.drawable.segment_track)}
                step.addView(row,LayoutParams(-1,dp(48)))
                val field=EditText(context).apply {
                    id=View.generateViewId();contentDescription="$label ${pair+1}";setSingleLine(true);minHeight=dp(48);gravity=Gravity.CENTER
                    inputType=android.text.InputType.TYPE_CLASS_NUMBER
                    imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    background=null;setPadding(0,0,0,0);textSize=16f;setText(drafts[index])
                }
                val minus=action("−","$label −"){field.setText(((field.text.toString().toIntOrNull()?:1)-1).coerceIn(1,120).toString())}
                val plus=action("+","$label +"){field.setText(((field.text.toString().toIntOrNull()?:1)+1).coerceIn(1,120).toString())}
                row.addView(minus,LayoutParams(dp(48),dp(48)));row.addView(field,LayoutParams(0,dp(48),1f));row.addView(plus,LayoutParams(dp(48),dp(48)))
                field.addTextChangedListener(object:TextWatcher {
                    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
                    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=Unit
                    override fun afterTextChanged(s:Editable?){drafts[index]=s.toString();field.error=if(s.toString().toIntOrNull() in 1..120)null else context.getString(R.string.sequence_count_error);publish()}
                })
                if(drafts[index].toIntOrNull() !in 1..120)field.error=context.getString(R.string.sequence_count_error)
            }
        }
        UiSkin.typography(this)
    }
}
