package dev.pako.bfi.ui

import android.content.*
import android.graphics.*
import android.os.Bundle
import android.text.*
import android.util.LruCache
import android.view.*
import android.widget.*
import dev.pako.bfi.*
import dev.pako.bfi.model.*
import java.util.concurrent.Executors

class AppsActivity : ScreenActivity() {
    private data class App(val pkg: String,val label: String,val available: Boolean)
    private var initialized=false
    private var restored: Bundle?=null
    private var mode=ActivationMode.ALL_APPS
    private val included=mutableSetOf<String>()
    private val excluded=mutableSetOf<String>()
    private var apps=emptyList<App>()
    private var filtered=emptyList<App>()
    private var shaderLabels=emptyMap<String,String>()
    private val executor=Executors.newSingleThreadExecutor()
    private val icons=LruCache<String,Bitmap>(64)
    private val iconPending=mutableSetOf<String>()
    private var loadFuture: java.util.concurrent.Future<*>?=null
    private var appsLoading=false
    private lateinit var policy: RadioGroup
    private var changing=false
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var selectionStatus: TextView
    private lateinit var globalSummary:TextView
    private val adapter=AppAdapter()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); restored=savedInstanceState; title(R.string.apps_title)
        // ListView owns scrolling; avoid nesting a recycling list inside ScrollView.
        val scroll=findViewById<View>(R.id.settings_scroll); val root=scroll.parent as LinearLayout
        val position=root.indexOfChild(scroll); root.removeView(scroll)
        val wide=resources.configuration.screenWidthDp>=600
        content=LinearLayout(this).apply { orientation=if(wide)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12)) }
        root.addView(content,position,LinearLayout.LayoutParams(-1,0,1f))
        val scopeScroll=ScrollView(this).apply { setPadding(0,0,0,0) }
        val scope=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scopeScroll.addView(scope)
        content.addView(scopeScroll,LinearLayout.LayoutParams(if(wide)0 else -1,if(wide)-1 else -2,if(wide).5f else 0f).apply { if(wide)marginEnd=dp(12) else bottomMargin=dp(12) })
        val appPanel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundResource(R.drawable.card);tag="glass";setPadding(dp(12),dp(12),dp(12),dp(12)) }
        content.addView(appPanel,LinearLayout.LayoutParams(if(wide)0 else -1,if(wide)-1 else 0,if(wide).5f else 1f))
        val scopeSettings=group(R.string.home_scope_heading,scope)
        policy=RadioGroup(this).apply { UiSkin.segmentTrack(this) }
        for ((index,label) in listOf(R.string.system_wide,R.string.home_scope_apps).withIndex()) {
            policy.addView(RadioButton(this).apply { id=if(index == 0) R.id.scope_system else R.id.scope_apps; setText(label); setTextColor(getColor(R.color.bfi_text_primary)); minHeight=dp(48);UiSkin.segment(this) },LinearLayout.LayoutParams(0,dp(48),1f))
        }
        scopeSettings.addView(policy,LinearLayout.LayoutParams(-1,-2))
        text(R.string.scope_help,scopeSettings,true)
        section(R.string.select_apps,appPanel).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(4)
        }
        selectionStatus=text(R.string.empty_selected,scopeSettings,true).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(4)
        }
        val defaults=group(R.string.global_defaults,scope)
        globalSummary=text(R.string.loading,defaults,true)
        button(R.string.configure_crt,defaults){startActivity(Intent(this,ShaderActivity::class.java))}
        button(R.string.configure_bfi,defaults){startActivity(Intent(this,PatternActivity::class.java))}
        search=EditText(this).apply {
            id=R.id.apps_search; setSingleLine(true); minHeight=dp(48); hint=getString(R.string.search_apps)
            imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        appPanel.addView(search,LinearLayout.LayoutParams(-1,-2))
        list=ListView(this).apply {
            id=R.id.apps_list; dividerHeight=dp(1); adapter=this@AppsActivity.adapter; itemsCanFocus=true
        }
        empty=text(R.string.apps_loading,appPanel,true)
        appPanel.addView(list,LinearLayout.LayoutParams(-1,0,1f)); list.emptyView=empty
        policy.setOnCheckedChangeListener { _,id ->
            if (initialized && !changing) {
                mode=if (id == R.id.scope_apps) ActivationMode.SELECTED_APPS else ActivationMode.ALL_APPS
                saveDraft(); filter()
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) = Unit
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { filter() }
        })
        search.setText(restored?.getString("search") ?: ""); initialize()
    }
    private fun initialize() {
        if (initialized) return
        val s=repository.settings ?: return
        mode=s.activationMode
        included.addAll(s.includedPackages); excluded.addAll(s.excludedPackages)
        changing=true; policy.check(if (mode == ActivationMode.ALL_APPS) R.id.scope_system else R.id.scope_apps); changing=false; initialized=true; filter()
    }
    override fun onStart() { shaderLabels=dev.pako.bfi.effects.ShaderCatalog.choices(this).associate{it.id to it.label}; super.onStart(); loadApps() }
    override fun onStop() { loadFuture?.cancel(true); appsLoading=false; iconPending.clear(); super.onStop() }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
    private fun loadApps() {
        val generation=callbackGeneration
        appsLoading=true; filter()
        loadFuture=executor.submit {
            val available=try {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
                    .distinctBy { it.activityInfo.packageName }.map { App(it.activityInfo.packageName,it.loadLabel(packageManager).toString(),true) }.sortedBy { it.label.lowercase() }
            } catch (_: RuntimeException) { emptyList() }
            runtime.main.post {
                if (!screenStarted || callbackGeneration != generation) return@post
                apps=available; appsLoading=false; filter()
            }
        }
    }
    private fun filter() {
        if (!::list.isInitialized) return
        val known=apps.map { it.pkg }.toSet()
        val unavailable=(included+excluded+(repository.settings?.overrides?.keys ?: emptySet())+(repository.settings?.shaderOverrides?.keys ?: emptySet())).filter { it !in known }.sorted()
            .map { App(it,getString(R.string.app_unavailable),false) }
        val query=search.text.toString()
        filtered=(apps+unavailable).filter { it.label.contains(query,true) || it.pkg.contains(query,true) }
        empty.setText(if (apps.isEmpty() && appsLoading) R.string.apps_loading else R.string.apps_empty)
        adapter.notifyDataSetChanged()
        selectionStatus.visibility=View.VISIBLE
        selectionStatus.text=if (mode == ActivationMode.SELECTED_APPS && included.isEmpty()) getString(R.string.empty_selected) else resources.getQuantityString(if (mode == ActivationMode.ALL_APPS) R.plurals.selection_saved_help else R.plurals.selected_count,included.size,included.size)
    }
    override fun refreshScreen() {
        initialize()
        repository.settings?.let { settings ->
            val pattern=dev.pako.bfi.effects.BfiPattern.parse(settings.multiStepPattern)?.summary?:UiText.pattern(this,settings.globalPattern)
            val shader=shaderLabels[settings.shaderId]?:getString(R.string.shader_missing)
            globalSummary.text=pattern+"\n"+getString(R.string.app_shader_summary,shader)
            if (initialized) {
                included.clear(); included.addAll(settings.includedPackages)
                excluded.clear(); excluded.addAll(settings.excludedPackages)
                mode=settings.activationMode
                changing=true; policy.check(if (mode == ActivationMode.ALL_APPS) R.id.scope_system else R.id.scope_apps); changing=false
            }
        }
        policy.isEnabled=initialized
        for (i in 0 until policy.childCount) policy.getChildAt(i).isEnabled=initialized
        search.isEnabled=initialized; filter()
    }
    override fun saveDraft() {
        val s=repository.settings ?: return
        if (initialized) save(s.copy(activationMode=mode,includedPackages=included.toSet(),excludedPackages=excluded.toSet()))
    }
    override val restoreMessage=R.string.restore_apps_confirm
    override fun restoreDefaults() {
        val s=repository.settings ?: return
        save(s.copy(activationMode=ActivationMode.ALL_APPS,includedPackages=emptySet(),excludedPackages=emptySet(),overrides=emptyMap(),shaderOverrides=emptyMap()))
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("search",search.text.toString()); super.onSaveInstanceState(outState)
    }
    private class Row(val icon: ImageView,val title: TextView,val subtitle: TextView,val member: CheckBox,val detail: Button)
    private inner class AppAdapter : BaseAdapter() {
        override fun getCount()=filtered.size
        override fun getItem(position: Int)=filtered[position]
        override fun getItemId(position: Int)=position.toLong()
        override fun getView(position: Int,convertView: View?,parent: ViewGroup): View {
            val root=convertView as? LinearLayout ?: LinearLayout(this@AppsActivity).apply {
                orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(4),0,dp(4),0); minimumHeight=dp(48)
                val icon=ImageView(this@AppsActivity).apply { importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }; addView(icon,LinearLayout.LayoutParams(dp(32),dp(32)).apply { marginEnd=dp(8) })
                val labels=LinearLayout(this@AppsActivity).apply { orientation=LinearLayout.VERTICAL }
                val title=TextView(this@AppsActivity).apply { textSize=16f; setTextColor(getColor(R.color.bfi_text_primary)) }
                val subtitle=TextView(this@AppsActivity).apply { textSize=12f; setTextColor(getColor(R.color.bfi_text_secondary)) }
                labels.addView(title); labels.addView(subtitle); addView(labels,LinearLayout.LayoutParams(0,-2,1f))
                val member=CheckBox(this@AppsActivity); addView(member,LinearLayout.LayoutParams(dp(48),dp(48)))
                val detail=Button(ContextThemeWrapper(this@AppsActivity,R.style.HeaderAction),null,0).apply { text="›";textSize=24f }; addView(detail,LinearLayout.LayoutParams(dp(48),dp(48)))
                tag=Row(icon,title,subtitle,member,detail)
            }
            val row=root.tag as Row; val app=filtered[position]; val s=repository.settings
            row.title.text=app.label
            val override=s?.overrides?.get(app.pkg)
            val shader=s?.shaderOverrides?.get(app.pkg)
            row.subtitle.setTextColor(getColor(if(override==null&&shader==null)R.color.bfi_text_secondary else R.color.bfi_active))
            row.subtitle.text=if (override == null) getString(R.string.default_app_pattern) else getString(R.string.app_override,UiText.integer(override.visibleSlots),UiText.integer(override.blackSlots))
            if(shader!=null){
                val label=shaderLabels[shader.id]?:getString(R.string.shader_missing)
                row.subtitle.text="${row.subtitle.text}\n${getString(R.string.app_shader_summary,label)}"
            }
            row.member.setOnCheckedChangeListener(null)
            row.member.isChecked=app.pkg in included
            root.setOnClickListener { row.member.toggle() }
            row.member.contentDescription=getString(R.string.app_include,app.label)
            row.member.isEnabled=initialized
            row.member.setOnCheckedChangeListener { _,checked ->
                val set=included
                if (checked) set.add(app.pkg) else set.remove(app.pkg)
                saveDraft()
                filter()
            }
            row.detail.contentDescription=getString(R.string.app_details,app.label)
            row.detail.setOnClickListener {
                android.app.AlertDialog.Builder(this@AppsActivity).setTitle(getString(R.string.app_effect_settings,app.label))
                    .setItems(arrayOf(getString(R.string.configure_crt),getString(R.string.configure_bfi))){_,which ->
                        val target=if(which==0)ShaderActivity::class.java else PatternActivity::class.java
                        startActivity(Intent(this@AppsActivity,target).putExtra(PatternActivity.EXTRA_PACKAGE,app.pkg).putExtra(PatternActivity.EXTRA_LABEL,app.label))
                    }.setNegativeButton(R.string.cancel,null).show()
            }
            row.icon.tag=app.pkg
            val cached=icons.get(app.pkg)
            if (cached != null) row.icon.setImageBitmap(cached) else {
                row.icon.setImageResource(R.mipmap.ic_fosforo)
                if (app.available && iconPending.size < 32 && iconPending.add(app.pkg)) {
                    val generation=callbackGeneration
                    executor.execute {
                        val bitmap=try {
                            val icon=packageManager.getApplicationIcon(app.pkg); val size=dp(32).coerceAtMost(128)
                            Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).also { icon.setBounds(0,0,size,size); icon.draw(Canvas(it)) }
                        } catch (_: Exception) { null } // Includes uninstall races (NameNotFoundException).
                        runtime.main.post {
                            if (!screenStarted || generation != callbackGeneration) return@post
                            iconPending.remove(app.pkg)
                            if (bitmap != null) { icons.put(app.pkg,bitmap); if (row.icon.tag == app.pkg) row.icon.setImageBitmap(bitmap) }
                        }
                    }
                }
            }
            UiSkin.typography(root)
            return root
        }
    }
}
