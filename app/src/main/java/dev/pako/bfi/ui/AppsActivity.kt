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
    private val adapter=AppAdapter()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); restored=savedInstanceState; title(R.string.apps_title)
        // ListView owns scrolling; avoid nesting a recycling list inside ScrollView.
        val scroll=findViewById<View>(R.id.settings_scroll); val root=scroll.parent as LinearLayout
        val position=root.indexOfChild(scroll); root.removeView(scroll)
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(content,position,LinearLayout.LayoutParams(-1,0,1f))
        policy=RadioGroup(this).apply { orientation=RadioGroup.HORIZONTAL }
        for ((index,label) in listOf(R.string.system_wide,R.string.apps_only).withIndex()) {
            policy.addView(RadioButton(this).apply { id=if(index == 0) R.id.scope_system else R.id.scope_apps; setText(label); setTextColor(getColor(R.color.bfi_text_primary)); minHeight=dp(48) },LinearLayout.LayoutParams(0,-2,1f))
        }
        content.addView(policy,LinearLayout.LayoutParams(-1,-2))
        text(R.string.scope_help,content,true)
        section(R.string.select_apps).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(4)
        }
        selectionStatus=text(R.string.empty_selected,content,true).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(4)
        }
        search=EditText(this).apply {
            id=R.id.apps_search; setSingleLine(true); minHeight=dp(48); hint=getString(R.string.search_apps)
            imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        content.addView(search,LinearLayout.LayoutParams(-1,-2))
        list=ListView(this).apply {
            id=R.id.apps_list; dividerHeight=dp(1); adapter=this@AppsActivity.adapter; itemsCanFocus=true
        }
        empty=text(R.string.apps_loading,content,true)
        content.addView(list,LinearLayout.LayoutParams(-1,0,1f)); list.emptyView=empty
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
    override fun onStart() { super.onStart(); loadApps() }
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
        val unavailable=(included+excluded+(repository.settings?.overrides?.keys ?: emptySet())).filter { it !in known }.sorted()
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
        save(s.copy(activationMode=ActivationMode.ALL_APPS,includedPackages=emptySet(),excludedPackages=emptySet(),overrides=emptyMap()))
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
                orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(4),dp(8),dp(4),dp(8)); minimumHeight=dp(64)
                val icon=ImageView(this@AppsActivity).apply { importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }; addView(icon,LinearLayout.LayoutParams(dp(32),dp(32)).apply { marginEnd=dp(8) })
                val labels=LinearLayout(this@AppsActivity).apply { orientation=LinearLayout.VERTICAL }
                val title=TextView(this@AppsActivity).apply { textSize=16f; setTextColor(getColor(R.color.bfi_text_primary)) }
                val subtitle=TextView(this@AppsActivity).apply { textSize=12f; setTextColor(getColor(R.color.bfi_text_secondary)) }
                labels.addView(title); labels.addView(subtitle); addView(labels,LinearLayout.LayoutParams(0,-2,1f))
                val member=CheckBox(this@AppsActivity); addView(member,LinearLayout.LayoutParams(dp(48),dp(48)))
                val detail=Button(ContextThemeWrapper(this@AppsActivity,R.style.Action),null,0).apply { setText(R.string.app_row_details) }; addView(detail,LinearLayout.LayoutParams(dp(72),-2))
                tag=Row(icon,title,subtitle,member,detail)
            }
            val row=root.tag as Row; val app=filtered[position]; val s=repository.settings
            row.title.text=app.label
            val override=s?.overrides?.get(app.pkg)
            row.subtitle.text=if (override == null) app.pkg else getString(R.string.app_override,UiText.integer(override.visibleSlots),UiText.integer(override.blackSlots))
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
            row.detail.setOnClickListener { startActivity(Intent(this@AppsActivity,PatternActivity::class.java).putExtra(PatternActivity.EXTRA_PACKAGE,app.pkg).putExtra(PatternActivity.EXTRA_LABEL,app.label)) }
            row.icon.tag=app.pkg
            val cached=icons.get(app.pkg)
            if (cached != null) row.icon.setImageBitmap(cached) else {
                row.icon.setImageResource(R.drawable.ic_bfi)
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
            return root
        }
    }
}
