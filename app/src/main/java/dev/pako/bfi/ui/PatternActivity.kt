package dev.pako.bfi.ui

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import dev.pako.bfi.*
import dev.pako.bfi.model.*

class PatternActivity : ScreenActivity() {
    override val hasEffectsAction=true
    companion object { const val EXTRA_PACKAGE="package"; const val EXTRA_LABEL="label" }
    private var pkg: String?=null
    private var initialized=false
    private var restored: Bundle?=null
    private var changing=false
    private lateinit var visible: EditText
    private lateinit var black: EditText
    private lateinit var preset: Spinner
    private lateinit var global: CheckBox
    private lateinit var exclusion: CheckBox
    private lateinit var preview: PatternPreview
    private lateinit var fraction: TextView
    private lateinit var cadence: TextView
    private lateinit var displayRate:TextView
    private lateinit var motion:android.opengl.GLSurfaceView
    private lateinit var motionRenderer:ShaderPreviewRenderer
    private var sequence:EditText?=null
    private var steps:PatternSteps?=null
    private var baseline=BfiPattern()
    private var baselineGlobal=false
    private var baselineExcluded=false
    private val controls=mutableListOf<View>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); restored=savedInstanceState
        pkg=intent.getStringExtra(EXTRA_PACKAGE)
        title(if (pkg == null) getString(R.string.pattern_settings) else getString(R.string.app_pattern_title,intent.getStringExtra(EXTRA_LABEL) ?: pkg))
        // Replace the whole-page scroller with two bounded, independently scrolling cards.
        val panels=boundedCards()
        val motionBody=group(R.string.bfi_effect,panels.preview)
        motionRenderer=ShaderPreviewRenderer(applicationContext,dev.pako.bfi.effects.ShaderState("lottes",emptyMap())){}
        motionRenderer.useShader=false;motionRenderer.effectsEnabled=false;motionRenderer.frozenTime=1.2f
        motion=android.opengl.GLSurfaceView(this).apply{setEGLContextClientVersion(3);setRenderer(motionRenderer);renderMode=android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY;contentDescription=getString(R.string.preview_fullscreen);setOnClickListener{startActivity(android.content.Intent(this@PatternActivity,TestEffectsActivity::class.java).putExtra("effect","bfi"))}}
        motionBody.addView(motion,LinearLayout.LayoutParams(-1,dp(108)))
        val left=group(R.string.active_pattern,panels.preview); val right=group(R.string.pattern_sequence,panels.controls)
        val compact=resources.configuration.screenWidthDp < 600
        preview=PatternPreview(this); left.addView(preview,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(4) })
        fraction=stat(R.string.stat_black,left)
        displayRate=stat(R.string.stat_display,left)
        cadence=stat(R.string.stat_cycle,left)
        val appOptions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        right.addView(appOptions,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(4) })
        global=check(R.string.use_global,appOptions); exclusion=check(R.string.explicit_exclusion,appOptions)
        for (option in listOf(global,exclusion)) {
            option.textSize=14f; option.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        }
        appOptions.visibility=if (pkg == null) View.GONE else View.VISIBLE
        section(R.string.preset_title,right).apply { textSize=16f; (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(4) }
        preset=Spinner(this).apply { minimumHeight=dp(48) }
        preset.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_item,listOf(getString(R.string.custom))+BfiPattern.presets.map { UiText.pattern(this,it) }).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        right.addView(preset,LinearLayout.LayoutParams(-1,-2)); controls.add(preset)
        visible=slotField(right,R.string.visible_slots,R.string.decrease_visible,R.string.increase_visible)
        black=slotField(right,R.string.black_slots,R.string.decrease_black,R.string.increase_black)
        text(R.string.pattern_help_compact,right,true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin=0 }
        preset.onItemSelectedListener=object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                // Spinner may deliver an older selection after a field edit switched to Custom.
                // A deferred initialization/layout callback must never replace the newer draft.
                if (!changing && initialized && position > 0 && position == preset.selectedItemPosition) {
                    val p=BfiPattern.presets[position-1]; changing=true
                    visible.setText(UiText.integer(p.visibleSlots)); black.setText(UiText.integer(p.blackSlots)); changing=false; updatePreview(); saveDraft()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val watcher=object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) = Unit
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!changing && initialized) { changing=true; preset.setSelection(0); changing=false; updatePreview(); saveDraft() }
            }
        }
        visible.addTextChangedListener(watcher); black.addTextChangedListener(watcher)
        global.setOnCheckedChangeListener { _,_ -> if (!changing && initialized) { updatePreview(); updateEditable(); saveDraft() } }
        exclusion.setOnCheckedChangeListener { _,_ -> if (!changing && initialized) saveDraft() }
        val card=right.parent as LinearLayout
        val heading=card.getChildAt(0) as TextView
        card.removeView(heading)
        (resetButton.parent as ViewGroup).removeView(resetButton)
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setBackgroundResource(R.drawable.group_card_header);setPadding(0,dp(4),dp(8),dp(4))}
        heading.background=null;header.addView(heading,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(resetButton,LinearLayout.LayoutParams(-2,dp(48)));card.addView(header,0)
        if(pkg==null){
            right.removeAllViews()
            text(R.string.multi_step_help,panels.controls,true).apply{setBackgroundResource(R.drawable.inset_card);setPadding(dp(10),dp(10),dp(10),dp(10))}
            sequence=EditText(this).apply {
                setText(restored?.getString("sequence")?:repository.settings?.multiStepPattern.orEmpty())
            }
            val settings=repository.settings
            val runs=dev.pako.bfi.effects.BfiPattern.parse(sequence!!.text.toString())?.runs
                ?: listOf(settings?.globalPattern?.visibleSlots?:1,settings?.globalPattern?.blackSlots?:1)
            val add=Button(ContextThemeWrapper(this,R.style.PreferenceAction),null,0).apply{id=R.id.add_pattern_pair;setText(R.string.add_pair_short);gravity=Gravity.CENTER;setCompoundDrawablesRelativeWithIntrinsicBounds(0,0,0,0)}
            steps=PatternSteps(this,restored?.getStringArrayList("stepDrafts")?:runs.map{it.toString()},add){value->
                sequence!!.setText(value)
                repository.settings?.let{save(it.copy(multiStepPattern=value))}
                updatePreview()
            }.also{right.addView(it,LinearLayout.LayoutParams(-1,-2))}
            right.addView(add,LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(12)})
        }
        initialize()
    }
    private fun stat(label:Int,parent:LinearLayout):TextView {
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        row.addView(TextView(this).apply{setText(label);textSize=14f;setTextColor(getColor(R.color.bfi_text_secondary))},LinearLayout.LayoutParams(0,-2,1f))
        return TextView(this).apply{textSize=14f;gravity=Gravity.END;setTextColor(getColor(R.color.bfi_text_primary));row.addView(this,LinearLayout.LayoutParams(-2,-2))}
    }
    private fun slotField(parent: LinearLayout,label: Int,minus: Int,plus: Int): EditText {
        val heading=text(label,parent).apply { textSize=16f; (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(4) }
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        val decrease=Button(ContextThemeWrapper(this,R.style.Action),null,0).apply { setText(R.string.operator_minus); contentDescription=getString(minus) }
        val field=EditText(this).apply {
            id=View.generateViewId(); inputType=android.text.InputType.TYPE_CLASS_NUMBER; minHeight=dp(48); gravity=Gravity.CENTER
            imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_ACTION_DONE; setSingleLine(true)
        }
        val increase=Button(ContextThemeWrapper(this,R.style.Action),null,0).apply { setText(R.string.operator_plus); contentDescription=getString(plus) }
        heading.labelFor=field.id
        row.addView(decrease,LinearLayout.LayoutParams(dp(48),-2)); row.addView(field,LinearLayout.LayoutParams(0,-2,1f)); row.addView(increase,LinearLayout.LayoutParams(dp(48),-2))
        decrease.setOnClickListener { field.setText(UiText.integer(((field.text.toString().toIntOrNull() ?: 1)-1).coerceIn(1,12))) }
        increase.setOnClickListener { field.setText(UiText.integer(((field.text.toString().toIntOrNull() ?: 1)+1).coerceIn(1,12))) }
        controls.addAll(listOf(decrease,field,increase)); return field
    }
    private fun initialize() {
        if (initialized) return
        val settings=repository.settings ?: return
        baseline=pkg?.let { settings.overrides[it] } ?: settings.globalPattern
        baselineGlobal=pkg != null && pkg !in settings.overrides; baselineExcluded=pkg in settings.excludedPackages
        changing=true
        if(pkg==null && restored==null){
            sequence?.setText(settings.multiStepPattern)
            steps?.replace(dev.pako.bfi.effects.BfiPattern.parse(settings.multiStepPattern)?.runs ?: listOf(baseline.visibleSlots,baseline.blackSlots))
        }
        visible.setText(restored?.getString("visible") ?: UiText.integer(baseline.visibleSlots)); black.setText(restored?.getString("black") ?: UiText.integer(baseline.blackSlots))
        global.isChecked=restored?.getBoolean("global") ?: baselineGlobal; exclusion.isChecked=restored?.getBoolean("excluded") ?: baselineExcluded
        val p=parsed(); preset.setSelection(if (restored != null) restored!!.getInt("preset",0) else BfiPattern.presets.indexOf(p).let { if (it < 0) 0 else it+1 })
        initialized=true; changing=false; updatePreview(); updateEditable()
        if (restored?.getBoolean("visibleError") == true) visible.error=getString(R.string.slot_error)
        if (restored?.getBoolean("blackError") == true) black.error=getString(R.string.slot_error)
    }
    private fun parsed(): BfiPattern? {
        val v=visible.text.toString().trim().toIntOrNull(); val b=black.text.toString().trim().toIntOrNull()
        return if (v != null && b != null && v in 1..12 && b in 1..12) BfiPattern(v,b) else null
    }
    private fun updatePreview() {
        if (!initialized) return
        if (visible.text.toString().toIntOrNull() in 1..12) visible.error=null
        if (black.text.toString().toIntOrNull() in 1..12) black.error=null
        val p=if (pkg != null && global.isChecked) repository.settings?.globalPattern else parsed()
        preview.pattern=p
        if(pkg==null){
            val runs=dev.pako.bfi.effects.BfiPattern.parse(sequence?.text.toString())?.runs ?: p?.let{listOf(it.visibleSlots,it.blackSlots)}
            preview.runs=runs
            if(runs!=null){
                val total=runs.sum();val blackFrames=runs.filterIndexed{index,_->index%2==1}.sum()
                fraction.text=getString(R.string.stat_percent,UiText.decimal(100.0*blackFrames/total))
                val hz=runtime.display()?.refreshRate?.toDouble()
                displayRate.text=if(hz!=null)getString(R.string.observed_mode,UiText.decimal(hz)) else getString(R.string.unknown_rate)
                cadence.text=if(hz!=null)getString(R.string.stat_cycle_value,UiText.decimal(hz/total)) else getString(R.string.unknown_rate)
                return
            }
        }
        fraction.text=p?.let { UiText.fraction(it) } ?: getString(R.string.slot_bounds)
        val hz=runtime.display()?.refreshRate?.toDouble()
        displayRate.text=if(hz!=null)getString(R.string.observed_mode,UiText.decimal(hz)) else getString(R.string.unknown_rate)
        cadence.text=if(p!=null && hz!=null)getString(R.string.stat_cycle_value,UiText.decimal(p.cyclesPerSecond(hz))) else getString(R.string.unknown_rate)
    }
    private fun updateEditable() {
        val enabled=initialized && repository.settings != null
        global.isEnabled=enabled; exclusion.isEnabled=enabled
        controls.forEach { it.isEnabled=enabled && !(pkg != null && global.isChecked) }
    }
    override fun onResume(){super.onResume();motion.onResume()}
    override fun onPause(){motion.onPause();super.onPause()}
    override fun refreshScreen() { initialize(); updateEditable(); updatePreview()
        val settings=repository.settings
        motionRenderer.blackPattern=if(pkg==null)dev.pako.bfi.effects.BfiPattern.parse(sequence?.text.toString())?:settings?.let{dev.pako.bfi.effects.BfiPattern.legacy(it.globalPattern.visibleSlots,it.globalPattern.blackSlots)} else parsed()?.let{dev.pako.bfi.effects.BfiPattern.legacy(it.visibleSlots,it.blackSlots)}
        motionRenderer.hz=runtime.display()?.refreshRate?.toDouble()?:120.0
        motionRenderer.effectsEnabled=false
    }
    override fun saveDraft() {
        val settings=repository.settings ?: return
        if (!initialized || changing) return
        val p=parsed()
        if (!(pkg != null && global.isChecked) && p == null) {
            if (visible.text.toString().toIntOrNull() !in 1..12) visible.error=getString(R.string.slot_error)
            if (black.text.toString().toIntOrNull() !in 1..12) black.error=getString(R.string.slot_error)
            if (pkg == null) return
        }
        val packageName=pkg
        if (packageName == null) save(settings.copy(globalPattern=p!!))
        else {
            val overrides=settings.overrides.toMutableMap(); if (global.isChecked) overrides.remove(packageName) else overrides[packageName]=p ?: settings.overrides[packageName] ?: settings.globalPattern
            val excluded=settings.excludedPackages.toMutableSet(); if (exclusion.isChecked) excluded.add(packageName) else excluded.remove(packageName)
            save(settings.copy(overrides=overrides.toMap(),excludedPackages=excluded.toSet()))
        }
    }
    override val resetPrompt=R.string.reset_pattern_prompt
    override val restoreMessage get()=if (pkg == null) R.string.restore_pattern_only_confirm else R.string.restore_app_pattern_confirm
    override fun restoreDefaults() {
        changing=true; visible.setText("1"); black.setText("1"); global.isChecked=pkg != null; exclusion.isChecked=false
        preset.setSelection(1); changing=false; visible.error=null; black.error=null
        updatePreview(); updateEditable()
        if (pkg == null) {
            val s=repository.settings ?: return; val d=BfiSettings()
            sequence?.setText("");steps?.replace(listOf(1,1));updatePreview()
            save(s.copy(globalPattern=d.globalPattern,multiStepPattern=""))
        } else saveDraft()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        steps?.let{outState.putStringArrayList("stepDrafts",it.snapshot())}
        outState.putString("sequence",sequence?.text?.toString());outState.putString("visible",visible.text.toString()); outState.putString("black",black.text.toString()); outState.putBoolean("global",global.isChecked)
        outState.putBoolean("excluded",exclusion.isChecked); outState.putInt("preset",preset.selectedItemPosition); super.onSaveInstanceState(outState)
        outState.putBoolean("visibleError",visible.error != null); outState.putBoolean("blackError",black.error != null)
    }
}

internal class PatternPreview(context: Context) : View(context) {
    var singleRow=false
    var compactHeight=0
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=resources.getFont(R.font.jersey15)}
    private var frames=emptyList<Boolean>()
    var runs:List<Int>?=null
        set(value){
            field=value
            frames=value?.flatMapIndexed { index,count -> List(count){index%2==1} }.orEmpty()
            contentDescription=value?.mapIndexed{index,count->"${context.getString(if(index%2==0)R.string.visible_step else R.string.black_step)} $count"}?.joinToString(" → ")
            requestLayout();invalidate()
        }
    var pattern: BfiPattern?=null
        set(value) {
            field=value
            runs=value?.let{listOf(it.visibleSlots,it.blackSlots)}
            contentDescription=value?.let { context.getString(R.string.pattern_preview_description,UiText.integer(it.visibleSlots),UiText.integer(it.blackSlots)) }
            invalidate()
        }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val cell=if(singleRow&&compactHeight>0)compactHeight+4*resources.displayMetrics.density else 44*resources.displayMetrics.density
        val measuredWidth=MeasureSpec.getSize(widthMeasureSpec)
        val columns=if(singleRow)frames.size.coerceAtLeast(1) else (measuredWidth/cell).toInt().coerceAtLeast(1)
        val rows=(frames.size.coerceAtLeast(2)+columns-1)/columns
        val rowHeight=if(singleRow&&compactHeight>0)compactHeight.toFloat() else 48*resources.displayMetrics.density
        setMeasuredDimension(if(singleRow)(frames.size*cell).toInt() else measuredWidth,resolveSize((rows*rowHeight).toInt(),heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if(frames.isEmpty())return
        val count=frames.size
        val cellHeight=if(singleRow&&compactHeight>0)compactHeight.toFloat() else 48*resources.displayMetrics.density
        val cellWidth=if(singleRow&&compactHeight>0)cellHeight+4*resources.displayMetrics.density else 44*resources.displayMetrics.density
        val columns=if(singleRow)frames.size.coerceAtLeast(1) else (width/cellWidth).toInt().coerceAtLeast(1)
        val gap=(if(singleRow)1 else 4)*resources.displayMetrics.density
        val outline=context.getColor(R.color.bfi_focus)
        val clip=canvas.clipBounds
        val first=(clip.top/cellHeight).toInt().coerceAtLeast(0)*columns
        val end=minOf(count,((clip.bottom/cellHeight).toInt()+1)*columns)
        for (i in first until end) {
            val col=i%columns
            val x=(if (layoutDirection == LAYOUT_DIRECTION_RTL) columns-1-col else col)*cellWidth; val y=(i/columns)*cellHeight
            paint.style=Paint.Style.FILL; paint.color=if (frames[i]) Color.BLACK else Color.WHITE
            canvas.drawRoundRect(x+gap,y+gap,x+cellWidth-gap,y+cellHeight-gap,(if(singleRow)3 else 6)*resources.displayMetrics.density,(if(singleRow)3 else 6)*resources.displayMetrics.density,paint)
            paint.color=if(frames[i])Color.WHITE else Color.BLACK;paint.textSize=if(singleRow)cellHeight*.65f else 14*resources.displayMetrics.scaledDensity;paint.textAlign=Paint.Align.CENTER
            canvas.drawText(if(frames[i])"B" else "V",x+cellWidth/2,y+cellHeight/2-(paint.ascent()+paint.descent())/2,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=2*resources.displayMetrics.density; paint.color=outline
            canvas.drawRoundRect(x+gap,y+gap,x+cellWidth-gap,y+cellHeight-gap,(if(singleRow)3 else 6)*resources.displayMetrics.density,(if(singleRow)3 else 6)*resources.displayMetrics.density,paint)
        }
    }
}
