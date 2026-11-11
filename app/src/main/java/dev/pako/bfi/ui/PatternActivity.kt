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
    private var sequence:EditText?=null
    private var baseline=BfiPattern()
    private var baselineGlobal=false
    private var baselineExcluded=false
    private val controls=mutableListOf<View>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); restored=savedInstanceState
        pkg=intent.getStringExtra(EXTRA_PACKAGE)
        title(if (pkg == null) getString(R.string.pattern_settings) else getString(R.string.app_pattern_title,intent.getStringExtra(EXTRA_LABEL) ?: pkg))
        // Replace the whole-page scroller with two bounded, independently scrolling cards.
        val panels=boundedCards(); val left=panels.preview; val right=panels.controls
        val compact=resources.configuration.screenWidthDp < 600
        section(R.string.pattern_preview_short,left).apply { textSize=16f; (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(if(compact) 2 else 4) }
        preview=PatternPreview(this); left.addView(preview,LinearLayout.LayoutParams(-1,dp(if(compact) 28 else 32)).apply { bottomMargin=dp(4) })
        fraction=text(R.string.loading,left,true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(2) }
        cadence=text(R.string.nominal_notice,left,true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin=0 }
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
        if (pkg == null) findViewById<Button>(R.id.advanced_action).apply {
            visibility=View.VISIBLE; setOnClickListener { startActivity(android.content.Intent(this@PatternActivity,AdvancedActivity::class.java)) }
        }
        if(pkg==null){
            section(R.string.multi_step_title,left)
            text(R.string.multi_step_help,left,true)
            sequence=EditText(this).apply {
                hint="1, 1, 2, 1";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_TEXT
                setText(restored?.getString("sequence")?:repository.settings?.multiStepPattern.orEmpty())
            }.also{left.addView(it,LinearLayout.LayoutParams(-1,-2))}
            sequence!!.addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=Unit
                override fun afterTextChanged(s:Editable?){
                    val value=s.toString().trim();val settings=repository.settings?:return
                    if(value.isEmpty() || dev.pako.bfi.effects.BfiPattern.parse(value)!=null){sequence!!.error=null;save(settings.copy(multiStepPattern=value))}
                    else sequence!!.error=getString(R.string.multi_step_error)
                }
            })
        }
        initialize()
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
        if(pkg==null && restored==null)sequence?.setText(settings.multiStepPattern)
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
        fraction.text=p?.let { getString(R.string.black_fraction,UiText.fraction(it)) } ?: getString(R.string.slot_bounds)
        val hz=runtime.display()?.refreshRate?.toDouble()
        cadence.text=if (p != null && hz != null) getString(R.string.nominal_rate_compact,UiText.decimal(hz),UiText.decimal(p.cyclesPerSecond(hz))) else getString(R.string.unknown_rate)
    }
    private fun updateEditable() {
        val enabled=initialized && repository.settings != null
        global.isEnabled=enabled; exclusion.isEnabled=enabled
        controls.forEach { it.isEnabled=enabled && !(pkg != null && global.isChecked) }
    }
    override fun refreshScreen() { initialize(); updateEditable(); updatePreview() }
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
    override val restoreMessage get()=if (pkg == null) R.string.restore_settings_confirm else R.string.restore_app_pattern_confirm
    override fun restoreDefaults() {
        changing=true; visible.setText("1"); black.setText("1"); global.isChecked=pkg != null; exclusion.isChecked=false
        preset.setSelection(1); changing=false; visible.error=null; black.error=null
        updatePreview(); updateEditable()
        if (pkg == null) {
            val s=repository.settings ?: return; val d=BfiSettings()
            sequence?.setText("")
            save(s.copy(globalPattern=d.globalPattern,multiStepPattern="",refreshTarget=d.refreshTarget,requestRefresh=d.requestRefresh,
                alwaysForce=d.alwaysForce,diagnosticsEnabled=d.diagnosticsEnabled,lastTestSeconds=d.lastTestSeconds))
        } else saveDraft()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("sequence",sequence?.text?.toString());outState.putString("visible",visible.text.toString()); outState.putString("black",black.text.toString()); outState.putBoolean("global",global.isChecked)
        outState.putBoolean("excluded",exclusion.isChecked); outState.putInt("preset",preset.selectedItemPosition); super.onSaveInstanceState(outState)
        outState.putBoolean("visibleError",visible.error != null); outState.putBoolean("blackError",black.error != null)
    }
}

private class PatternPreview(context: Context) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    var pattern: BfiPattern?=null
        set(value) {
            field=value
            contentDescription=value?.let { context.getString(R.string.pattern_preview_description,UiText.integer(it.visibleSlots),UiText.integer(it.blackSlots)) }
            invalidate()
        }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p=pattern ?: return
        val count=(p.totalSlots * 3).coerceAtMost(24)
        val columns=count.coerceAtMost(12); val rows=(count+columns-1)/columns
        val gap=4*resources.displayMetrics.density; val cellWidth=width.toFloat()/columns; val cellHeight=height.toFloat()/rows
        val outline=context.getColor(R.color.bfi_focus)
        for (i in 0 until count) {
            val col=i%columns
            val x=(if (layoutDirection == LAYOUT_DIRECTION_RTL) columns-1-col else col)*cellWidth; val y=(i/columns)*cellHeight
            paint.style=Paint.Style.FILL; paint.color=if (i%p.totalSlots < p.visibleSlots) Color.WHITE else Color.BLACK
            canvas.drawRect(x+gap,y+gap,x+cellWidth-gap,y+cellHeight-gap,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=resources.displayMetrics.density; paint.color=outline
            canvas.drawRect(x+gap,y+gap,x+cellWidth-gap,y+cellHeight-gap,paint)
        }
    }
}
