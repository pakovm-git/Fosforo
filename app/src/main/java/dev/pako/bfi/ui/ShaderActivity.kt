package dev.pako.bfi.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import android.view.*
import dev.pako.bfi.R
import dev.pako.bfi.UiText
import dev.pako.bfi.effects.*
import dev.pako.bfi.model.AppShader

/** Uses the same bounded cards and commit queue as every other settings screen. */
class ShaderActivity:ScreenActivity() {
    private val pkg get()=intent.getStringExtra(PatternActivity.EXTRA_PACKAGE)
    private val selectedShader get()=repository.settings?.shaderFor(pkg)
    private var inheritGlobal:CheckBox?=null
    private var updatingInheritance=false
    private fun pageTitle(parametersOnly:Boolean=false){
        if(pkg!=null)title(getString(R.string.app_shader_title,intent.getStringExtra(PatternActivity.EXTRA_LABEL)?:pkg))
        else title(if(parametersOnly)R.string.shader_parameters_action else R.string.shader_browser)
    }
    private lateinit var effectsAction:Button
    override val hasEffectsAction=true
    private val wide get()=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE&&resources.configuration.screenWidthDp>=600
    private lateinit var categoriesScroll:HorizontalScrollView
    private lateinit var categoryRow:LinearLayout
    private var categoryViews=emptyList<String>()
    private lateinit var browser:LinearLayout
    private lateinit var studio:LinearLayout
    private lateinit var thumbnails:ShaderThumbnails
    @Volatile private var browserActive=true
    private var studioVisible=false
    private var restoredStudio=false
    private var pendingCapture:dev.pako.bfi.state.StartRequest?=null
    private var loadedId:String?=null
    private var building=false
    private lateinit var parameters:LinearLayout
    private lateinit var search:EditText
    private lateinit var download:Button
    private lateinit var cancelDownload:Button
    private lateinit var shaderList:ListView
    private lateinit var categoryTabs:RadioGroup
    private val libraryAdapter=LibraryAdapter()
    private lateinit var removeDownloads:Button
    private lateinit var importAction:Button
    private lateinit var scan:Button
    private var shownReport:List<String>?=null
    private var shownFavourites:Set<String>?=null
    private lateinit var libraryStatus:TextView
    private var allChoices=emptyList<ShaderChoice>()
    private var visibleChoices=emptyList<ShaderChoice>()
    private var categories=listOf("all")
    private var categoryKey=""
    private var libraryRevision:String?=null
    private var unsubscribeLibrary:(()->Unit)?=null
    private var selectionGeneration=0
    private var restoredCategory:String?=null
    private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);pageTitle()
        restoredCategory=savedInstanceState?.getString("shaderCategory")
        restoredStudio=savedInstanceState?.getBoolean("studio")?:false
        val old=findViewById<View>(R.id.settings_scroll);val host=old.parent as LinearLayout;val index=host.indexOfChild(old);host.removeView(old)
        val page=LinearLayout(this).apply{orientation=if(wide)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));isBaselineAligned=false}
        host.addView(page,index,LinearLayout.LayoutParams(-1,0,1f))
        browser=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};studio=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        page.addView(browser,LinearLayout.LayoutParams(if(wide)0 else -1,if(wide)-1 else 0,1f).apply{if(wide)marginEnd=dp(6)})
        page.addView(studio,LinearLayout.LayoutParams(if(wide)0 else -1,if(wide)-1 else 0,1f))
        parameters=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val options=ScrollView(this).apply{isVerticalFadingEdgeEnabled=true;setFadingEdgeLength(dp(18));addView(parameters)};studio.addView(options,LinearLayout.LayoutParams(-1,-1))
        effectsAction=findViewById<Button>(R.id.editing_stop).apply{setOnClickListener{toggleEffects()};contentDescription=null}
        if(pkg!=null){
            val scope=column(browser,card=true)
            inheritGlobal=check(R.string.use_global_shader,scope).apply {
                tooltipText=getString(R.string.app_shader_help)
                setOnCheckedChangeListener { _,checked ->
                    if(!updatingInheritance)repository.settings?.let { settings ->
                        save(if(checked)settings.copy(shaderOverrides=settings.shaderOverrides-pkg!!)
                            else settings.withShader(pkg,settings.shaderFor(pkg)))
                        loadedId=null;showParameters()
                    }
                }
            }
        }
        val searchRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;isBaselineAligned=false;gravity=Gravity.CENTER_VERTICAL}
        browser.addView(searchRow,LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(5)})
        categoryRow=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        categoriesScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;isHorizontalFadingEdgeEnabled=true;setFadingEdgeLength(dp(18))}
        categoryTabs=RadioGroup(this).apply{UiSkin.segmentTrack(this)}
        categoriesScroll.addView(categoryTabs);categoryRow.addView(categoriesScroll,LinearLayout.LayoutParams(-1,dp(48)));browser.addView(categoryRow,LinearLayout.LayoutParams(-1,dp(48)))
        categoryTabs.setOnCheckedChangeListener{_,id->if(!building){categoryKey=categoryTabs.findViewById<RadioButton>(id)?.tag as? String?:return@setOnCheckedChangeListener;filterChoices()}}
        search=EditText(this).apply{hint=getString(R.string.search_shaders);setSingleLine(true);minHeight=dp(48);setTextColor(getColor(R.color.bfi_text_primary));inputType=android.text.InputType.TYPE_CLASS_TEXT;setBackgroundResource(R.drawable.inset_card);setPadding(dp(10),0,dp(10),0)}
        searchRow.addView(search,LinearLayout.LayoutParams(0,dp(48),1f).apply{marginEnd=dp(6)})
        search.setText(savedInstanceState?.getString("shaderSearch").orEmpty())
        thumbnails=ShaderThumbnails(applicationContext){browserActive&&screenStarted&&!isDestroyed}
        shaderList=CardListView(this).apply{id=View.generateViewId();adapter=libraryAdapter;itemsCanFocus=true;dividerHeight=dp(2);isVerticalFadingEdgeEnabled=true;setFadingEdgeLength(dp(18));setPadding(dp(2),dp(2),dp(2),dp(2))}
        val listCard=FrameLayout(this).apply{
            val rtl=resources.configuration.layoutDirection==View.LAYOUT_DIRECTION_RTL
            background=android.graphics.drawable.InsetDrawable(getDrawable(R.drawable.card),if(rtl)dp(12) else 0,0,if(rtl)0 else dp(12),0);tag="glass"
            setPadding(0,0,0,0)
            addView(shaderList,FrameLayout.LayoutParams(-1,-1))
        }
        browser.addView(listCard,LinearLayout.LayoutParams(-1,0,1f))
        val empty=TextView(this).apply{setText(R.string.library_empty);textSize=14f;setTextColor(getColor(R.color.bfi_text_secondary));gravity=Gravity.CENTER}
        listCard.addView(empty,FrameLayout.LayoutParams(-1,-1).apply{marginStart=dp(10);marginEnd=dp(22)});shaderList.emptyView=empty
        // Source/maintenance actions are a menu, never a second scroller around the shader list.
        val sources=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE};browser.addView(sources)
        download=button(R.string.download_shader_library,sources){
            AlertDialog.Builder(this).setOnKeyListener{_,_,event->stopFromController(event)}.setTitle(R.string.download_shader_library).setMessage(R.string.download_library_confirm)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.download_shader_library){_,_->pendingCapture=null;runtime.stop();ShaderLibrary.download(applicationContext)}.show()
        }
        scan=button(R.string.check_shader_compatibility,sources){
            if(ShaderLibrary.status.busy)return@button
            AlertDialog.Builder(this).setOnKeyListener{_,_,event->stopFromController(event)}.setTitle(R.string.check_shader_compatibility).setMessage(R.string.compatibility_confirm)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.check_shader_compatibility){_,_->if(!ShaderLibrary.status.busy){pendingCapture=null;runtime.stop();ShaderLibrary.scan(applicationContext)}}.show()
        }
        val report=button(R.string.unavailable_shaders,sources){showReport(ShaderLibrary.report(this))}
        cancelDownload=button(R.string.cancel_download,browser){ShaderLibrary.cancel()}
        libraryStatus=text(R.string.library_help,browser,true).apply{visibility=View.GONE}
        importAction=button(R.string.import_shader,sources){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),201)}
        removeDownloads=button(R.string.remove_downloads,sources){
            if(ShaderLibrary.status.busy)return@button
            AlertDialog.Builder(this).setOnKeyListener{_,_,event->stopFromController(event)}.setTitle(R.string.remove_downloads).setMessage(R.string.remove_downloads_confirm)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.remove_downloads){_,_->
                    if(ShaderLibrary.status.busy)return@setPositiveButton
                    selectionGeneration++;pendingCapture=null;runtime.stop()
                    val settings=repository.settings?:return@setPositiveButton
                    val bundled=ShaderCatalog.choices.map{it.id}.toSet();val keep=settings.shaderId in bundled
                    repository.save(settings.copy(shaderId=if(keep)settings.shaderId else "lottes",shaderValues=if(keep)settings.shaderValues else emptyMap(),favouriteShaders=settings.favouriteShaders.intersect(bundled),shaderOverrides=settings.shaderOverrides.filterValues{it.id in bundled})){success->
                        if(success)ShaderLibrary.removeAll(applicationContext)else Toast.makeText(this,R.string.save_failed,Toast.LENGTH_LONG).show()
                    }
                }.show()
        }
        lateinit var management:Button
        management=button(R.string.shader_sources,browser){
            AlertDialog.Builder(this).setOnKeyListener{_,_,event->stopFromController(event)}.setTitle(R.string.shader_sources).setItems(arrayOf(getString(R.string.import_shader),getString(R.string.download_shader_library),getString(R.string.library_management))){_,choice->
                when(choice){0->if(importAction.isEnabled)importAction.performClick();1->if(download.isEnabled)download.performClick();else->
                    AlertDialog.Builder(this).setOnKeyListener{_,_,event->stopFromController(event)}.setTitle(R.string.library_management).setItems(arrayOf(scan.text.toString(),report.text.toString(),removeDownloads.text.toString())){_,which->val action=listOf(scan,report,removeDownloads)[which];if(action.isEnabled)action.performClick()}.show()
                }
            }.show()
        }
        (management.parent as LinearLayout).removeView(management);management.text="…";management.gravity=Gravity.CENTER;management.setPadding(0,0,0,0);management.includeFontPadding=false;management.setSingleLine(true);management.setCompoundDrawablesRelativeWithIntrinsicBounds(0,0,0,0);management.contentDescription=getString(R.string.shader_sources);searchRow.addView(management,LinearLayout.LayoutParams(dp(48),dp(48)))
        if(!wide){
            val customize=button(R.string.shader_parameters_action,browser){showStudio()}
            (customize.parent as LinearLayout).removeView(customize)
            customize.setBackgroundResource(R.drawable.primary_action);customize.setTextColor(getColor(R.color.bfi_on_action))
            browser.addView(customize,1,LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(5)})
        }
        refreshChoices()
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){filterChoices()}
            override fun afterTextChanged(s:Editable?)=Unit
        })
        if(restoredStudio)showStudio()else showBrowser()
    }
    private fun showStudio(){
        studioVisible=true;browserActive=screenStarted&&wide;browser.visibility=if(wide)View.VISIBLE else View.GONE;studio.visibility=View.VISIBLE
        pageTitle(!wide)
        refreshScreen();UiSkin.typography(studio);updateControllerHints()
    }
    private fun showBrowser(){
        studioVisible=wide;browserActive=screenStarted;studio.visibility=if(wide)View.VISIBLE else View.GONE;browser.visibility=View.VISIBLE;pageTitle()
        resetButton.visibility=View.GONE;libraryAdapter.notifyDataSetChanged();refreshScreen();updateControllerHints()
    }
    override fun requestClose(){if(!wide&&studioVisible)showBrowser()else super.requestClose()}
    override val resetPrompt=R.string.reset_shader_prompt
    override val restoreTitle=R.string.restore_shader_defaults
    override fun canRestore()=studioVisible&&super.canRestore()
    override fun restoreConfirmation():String{
        val choice=allChoices.firstOrNull{it.id==selectedShader?.id}
        return getString(R.string.restore_shader_confirm,choice?.label?:getString(R.string.shader_title))
    }
    override fun stopEffects(){toggleEffects()}
    private fun openShaderPreview(){startActivity(Intent(this,TestEffectsActivity::class.java).putExtra("effect","crt").putExtra(PatternActivity.EXTRA_PACKAGE,pkg))}
    private fun chooseShader(choice:ShaderChoice,openPreview:Boolean=false) {
        if(ShaderLibrary.status.busy)return
        val settings=repository.settings?:return
        if(choice.id==settings.shaderFor(pkg).id){if(openPreview)openShaderPreview()else if(wide)showStudio()else refreshScreen();return}
        val generation=++selectionGeneration
        worker.execute {
            try {
                val definition=ShaderCatalog.load(applicationContext,choice.id)
                ShaderProbe(applicationContext).use{it.check(definition)}
                runOnUiThread {if(!isDestroyed && generation==selectionGeneration){repository.settings?.let{current->repository.save(current.withShader(pkg,AppShader(choice.id))){success->if(!isDestroyed&&screenStarted&&generation==selectionGeneration){if(success){loadedId=null;showParameters();if(openPreview)openShaderPreview()else if(wide)showStudio()else showBrowser()}else Toast.makeText(this,R.string.save_failed,Toast.LENGTH_LONG).show()}}}}}
            }catch(error:Exception){runOnUiThread {if(!isDestroyed && generation==selectionGeneration){filterChoices();importFailed(error)}}}
        }
    }
    private fun refreshChoices() {
        if(repository.settings==null)return
        building=true
        val selectedCategory=restoredCategory ?: categoryKey
        restoredCategory=null
        allChoices=ShaderCatalog.choices(this)
        categoryKey=selectedCategory;building=false
        filterChoices()
        showParameters()
    }
    private fun categoryLabel(key:String)=when(key){"bundled"->getString(R.string.bundled_shaders);"favourites"->getString(R.string.favourite_shaders);"all"->getString(R.string.all_shader_categories);"imported"->getString(R.string.imported_shaders);"crt"->"CRT";"vhs"->"VHS";else->key.replace('-',' ').replaceFirstChar{it.uppercaseChar()}}
    private fun filterChoices() {
        if(building || !::shaderList.isInitialized)return
        selectionGeneration++
        val favourites=repository.settings?.favouriteShaders.orEmpty()
        categories=shaderCategories(allChoices,favourites)
        if(categoryKey !in categories)categoryKey=categories.first()
        val key=categoryKey
        val query=search.text.toString().trim()
        visibleChoices=allChoices.filter{(when(key){"all"->true;"bundled"->it.bundled;"favourites"->it.id in favourites;else->it.category==key}) && (it.label.contains(query,true) || it.assetPath.contains(query,true))}
        building=true
        if(::categoryTabs.isInitialized){
            if(categoryViews!=categories){
                categoryViews=categories;categoryTabs.removeAllViews()
                for(category in categories)categoryTabs.addView(RadioButton(this).apply{id=View.generateViewId();tag=category;text=categoryLabel(category);UiSkin.segment(this);setPadding(dp(10),0,dp(10),0)},LinearLayout.LayoutParams(-2,dp(48)))
            }
            (0 until categoryTabs.childCount).firstOrNull{categoryTabs.getChildAt(it).tag==key}?.let{categoryTabs.check(categoryTabs.getChildAt(it).id)}
        }
        if(::shaderList.isInitialized)libraryAdapter.notifyDataSetChanged()
        building=false
    }
    override fun onStart() {
        super.onStart();browserActive=wide||!studioVisible
        unsubscribeLibrary=ShaderLibrary.subscribe {
            val status=ShaderLibrary.status
            download.isEnabled=!status.busy
            scan.isEnabled=!status.busy
            importAction.isEnabled=!status.busy
            shaderList.isEnabled=!status.busy;libraryAdapter.notifyDataSetChanged()
            removeDownloads.isEnabled=!status.busy && !repository.writing
            cancelDownload.visibility=if(status.busy)android.view.View.VISIBLE else android.view.View.GONE
            libraryStatus.text=status.message.ifEmpty{getString(R.string.library_help)}
            libraryStatus.visibility=if(status.busy||status.message.isNotEmpty())View.VISIBLE else View.GONE
            if(status.revision!=null){libraryRevision=status.revision;refreshChoices()}
            if(status.report!=null && shownReport!==status.report){
                shownReport=status.report
                repository.settings?.let{settings->
                    val available=allChoices.map{it.id}.toSet()
                    val keep=settings.shaderId in available
                    save(settings.copy(shaderId=if(keep)settings.shaderId else "lottes",shaderValues=if(keep)settings.shaderValues else emptyMap(),shaderOverrides=settings.shaderOverrides.filterValues{it.id in available}))
                }
                showReport(status.report)
            }
            refreshScreen()
        }
    }
    override fun onStop(){browserActive=false;unsubscribeLibrary?.invoke();unsubscribeLibrary=null;super.onStop()}
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putBoolean("studio",studioVisible)
        outState.putString("shaderCategory",categoryKey)
        outState.putString("shaderSearch",search.text.toString())
        super.onSaveInstanceState(outState)
    }
    private fun showParameters() {
        val settings=repository.settings?:return
        val shader=settings.shaderFor(pkg)
        if(loadedId==shader.id)return
        loadedId=shader.id;parameters.removeAllViews()
        try {
            val definition=ShaderCatalog.load(this,shader.id)
            var parameterList=group(getString(R.string.shader_parameter_heading,definition.choice.label),parameters)
            button(R.string.restore_shader_defaults,parameterList){confirmRestoreDefaults()}.apply{UiSkin.controller(this,"Y");setCompoundDrawablesRelativeWithIntrinsicBounds(0,0,0,0);gravity=Gravity.END or Gravity.CENTER_VERTICAL}
            for(parameter in definition.parameters) {
                if(!parameter.editable){parameterList=group(parameter.label,parameters);continue}
                val group=column(parameterList)
                val value=parameter.normalize(shader.values[parameter.id]?:parameter.defaultValue)
                val values=parameter.sliderValues()
                val valueText=ShaderValueText(parameter)
                val heading=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL}
                val name=TextView(this).apply{text=parameter.label;textSize=14f;setTextColor(getColor(R.color.bfi_text_primary))}
                val label=TextView(this).apply { text=valueText.format(value);textSize=14f;setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.END;setPadding(dp(8),0,0,0);setTextColor(getColor(R.color.bfi_text_secondary)) }
                // A changing decimal must not resize the name or move the slider beneath it.
                val valueWidth=(kotlin.math.ceil(values.maxOf{label.paint.measureText(valueText.format(it))}).toInt()+label.paddingLeft+dp(2)).coerceIn(dp(40),dp(100))
                heading.addView(name,LinearLayout.LayoutParams(0,-2,1f));heading.addView(label,LinearLayout.LayoutParams(valueWidth,-2))
                group.addView(heading)
                if(parameter.toggle) {
                    val toggle=Switch(this).apply{minHeight=dp(48);contentDescription=parameter.label;isChecked=value==1f}
                    group.addView(toggle)
                    toggle.setOnCheckedChangeListener{_,checked->saveParameter(parameter,if(checked)1f else 0f);label.text=if(checked)"1" else "0"}
                }else {
                    val slider=SeekBar(this).apply{max=values.lastIndex;progress=values.indexOf(value).takeIf{it>=0}?:values.indices.minBy{kotlin.math.abs(values[it]-value)};contentDescription=parameter.label}
                    group.addView(slider,LinearLayout.LayoutParams(-1,dp(48)))
                    slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                        override fun onStartTrackingTouch(seekBar:SeekBar)=Unit
                        override fun onStopTrackingTouch(seekBar:SeekBar)=Unit
                        override fun onProgressChanged(seekBar:SeekBar,progress:Int,fromUser:Boolean){if(fromUser){val next=values[progress];label.text=valueText.format(next);saveParameter(parameter,next)}}
                    })
                }
            }
            UiSkin.typography(parameters)
        }catch(error:Exception){text(R.string.shader_missing,parameters);Toast.makeText(this,error.message,Toast.LENGTH_LONG).show()}
    }
    private inner class LibraryAdapter:BaseAdapter(){
        override fun getCount()=visibleChoices.size
        override fun getItem(position:Int)=visibleChoices[position]
        override fun getItemId(position:Int)=position.toLong()
        override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
            val row=convertView as? LinearLayout?:LinearLayout(this@ShaderActivity).apply{
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(60);setPadding(dp(8),dp(4),dp(8),dp(4));setBackgroundResource(R.drawable.shader_row)
                addView(ImageView(this@ShaderActivity).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundResource(R.drawable.inset_card);setPadding(dp(6),dp(6),dp(6),dp(6));clipToOutline=true},LinearLayout.LayoutParams(dp(56),dp(48)).apply{marginEnd=dp(6)})
                addView(Button(ContextThemeWrapper(this@ShaderActivity,R.style.HeaderAction),null,0).apply{gravity=Gravity.START or Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(8),0);setBackgroundResource(R.drawable.row_action)},LinearLayout.LayoutParams(0,dp(52),1f))
                addView(TextView(this@ShaderActivity).apply{textSize=14f;setTextColor(getColor(R.color.bfi_action))},LinearLayout.LayoutParams(-2,-2))
                addView(Button(ContextThemeWrapper(this@ShaderActivity,R.style.HeaderAction),null,0).apply{setPadding(0,0,0,0)},LinearLayout.LayoutParams(dp(56),dp(56)))
            }
            val choice=visibleChoices[position];val settings=repository.settings
            row.isActivated=choice.id==settings?.shaderFor(pkg)?.id
            val image=row.getChildAt(0) as ImageView;image.tag=choice.id;image.setImageDrawable(null);image.contentDescription=getString(R.string.shader_thumbnail_loading)
            thumbnails.load(choice){bitmap->if(image.tag==choice.id){image.setImageBitmap(bitmap);image.contentDescription=if(bitmap==null)getString(R.string.shader_preview_unavailable)else getString(R.string.shader_thumbnail_description,choice.label)}}
            image.setOnClickListener{chooseShader(choice,true)};image.isFocusable=true;image.isEnabled=!ShaderLibrary.status.busy
            (row.getChildAt(1) as Button).apply{text=choice.label.substringAfterLast('/').removeSuffix(".slangp");isEnabled=!ShaderLibrary.status.busy;setOnClickListener{chooseShader(choice)}}
            (row.getChildAt(2) as TextView).text=if(choice.bundled)"◆" else ""
            (row.getChildAt(3) as Button).apply{
                val active=choice.id in settings?.favouriteShaders.orEmpty()
                text=if(active)"★" else "☆";setTextColor(getColor(if(active)R.color.bfi_active else R.color.bfi_action))
                contentDescription=getString(if(active)R.string.remove_favourite else R.string.add_favourite)+": "+choice.label
                isEnabled=settings!=null&&!ShaderLibrary.status.busy
                setOnClickListener{repository.settings?.let{save(it.copy(favouriteShaders=if(choice.id in it.favouriteShaders)it.favouriteShaders-choice.id else it.favouriteShaders+choice.id))}}
            }
            UiSkin.typography(row);return row
        }
    }
    private fun saveParameter(parameter:ShaderParameter,value:Float) {
        val settings=repository.settings?:return
        val shader=settings.shaderFor(pkg)
        save(settings.withShader(pkg,shader.copy(values=shader.values+(parameter.id to parameter.normalize(value)))))
    }
    @Deprecated("Native Activity result callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==204){
            val request=pendingCapture;pendingCapture=null
            if(resultCode==RESULT_OK && data!=null && request!=null)runtime.start(request,data)
            refreshScreen();return
        }
        if(requestCode!=201 || resultCode!=RESULT_OK)return
        val uri=data?.data?:return
        Toast.makeText(this,R.string.importing_shader,Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val archive=ShaderImports.prepare(applicationContext,uri)
                runOnUiThread {
                    if(isFinishing || isDestroyed){archive.close();return@runOnUiThread}
                    fun install(preset:String){worker.execute {
                        try { val choice=archive.use{ShaderImports.install(applicationContext,it,preset)}
                            runOnUiThread {if(!isDestroyed){repository.settings?.let{settings->repository.save(settings.withShader(pkg,AppShader(choice.id))){success->
                                if(!isDestroyed&&screenStarted){if(success){categoryKey="imported";loadedId=null;refreshChoices();showStudio()}else Toast.makeText(this,R.string.save_failed,Toast.LENGTH_LONG).show()}
                            }}}} }
                        catch(error:Exception){runOnUiThread{if(!isDestroyed)importFailed(error)}}
                    }}
                    if(archive.presets.size==1)install(archive.presets.single())
                    else AlertDialog.Builder(this).setOnKeyListener{_,_,event->stopFromController(event)}.setTitle(R.string.choose_import).setItems(archive.presets.toTypedArray()){_,index->install(archive.presets[index])}.setOnCancelListener{archive.close()}.show()
                }
            }catch(error:Exception){runOnUiThread{if(!isDestroyed)importFailed(error)}}
        }
    }
    private fun showReport(entries:List<String>) {
        val dialog=AlertDialog.Builder(this).setOnKeyListener{_,_,event->stopFromController(event)}.setTitle(getString(R.string.unavailable_shader_count,entries.size))
        if(entries.isEmpty())dialog.setMessage(R.string.no_unavailable_shaders)
        else dialog.setItems(entries.map{it.substringBefore('\n')}.toTypedArray()){_,index->
            AlertDialog.Builder(this).setOnKeyListener{_,_,event->stopFromController(event)}.setTitle(R.string.unavailable_shaders).setMessage(entries[index]).setPositiveButton(android.R.string.ok,null).show()
        }
        dialog.setPositiveButton(android.R.string.ok,null).show()
    }
    private fun importFailed(error:Exception) {
        if(isDestroyed || isFinishing)return
        AlertDialog.Builder(this).setOnKeyListener{_,_,event->stopFromController(event)}.setTitle(R.string.shader_import_failed).setMessage(error.message?:getString(R.string.shader_missing))
            .setPositiveButton(android.R.string.ok,null).show()
    }
    override fun toggleEffects() {
        if(runtime.state.userEnabled || pendingCapture!=null){pendingCapture=null;runtime.stop();refreshScreen();return}
        val request=runtime.prepareStart()
        val problem=runtime.readiness()
        if(problem==dev.pako.bfi.state.Reason.CAPTURE || (problem==null && repository.settings?.crtEnabled==true && !runtime.captureActive && runtime.captureConsent==null)){
            pendingCapture=request
            startActivityForResult(getSystemService(android.media.projection.MediaProjectionManager::class.java).createScreenCaptureIntent(),204)
        }else if(problem==null)runtime.start(request)
        else {
            Toast.makeText(this,UiText.reason(this,problem),Toast.LENGTH_LONG).show()
            startActivity(Intent(this,dev.pako.bfi.MainActivity::class.java))
        }
        refreshScreen()
    }
    override fun refreshScreen(){
        updatingInheritance=true
        inheritGlobal?.apply{isEnabled=repository.settings!=null;isChecked=pkg !in repository.settings?.shaderOverrides.orEmpty()}
        updatingInheritance=false
        if(::effectsAction.isInitialized){
            val enabled=runtime.state.userEnabled || pendingCapture!=null
            for(action in listOf(effectsAction)){
                action.setText(if(enabled)R.string.stop_effects else R.string.start_effects)
                action.setBackgroundResource(if(enabled)R.drawable.stop_action else R.drawable.primary_action)
                action.setTextColor(getColorStateList(if(enabled)R.color.stop_action_text else R.color.primary_action_text))
                action.isEnabled=enabled||(repository.settings!=null&&!repository.writing&&!ShaderLibrary.status.busy)
            }
            resetButton.visibility=View.GONE
            effectsAction.visibility=View.VISIBLE
        }
        if(::shaderList.isInitialized){
            val favourites=repository.settings?.favouriteShaders.orEmpty()
            if(shownFavourites!=favourites){shownFavourites=favourites.toSet();filterChoices()}
            libraryAdapter.notifyDataSetChanged()
            if(allChoices.isEmpty())refreshChoices() else showParameters()
        }
    }
    override fun saveDraft()=Unit
    override fun restoreDefaults(){val settings=repository.settings?:return;save(settings.withShader(pkg,settings.shaderFor(pkg).copy(values=emptyMap())));loadedId=null;showParameters()}
    override fun onDestroy(){browserActive=false;if(::thumbnails.isInitialized)thumbnails.close();worker.shutdown();super.onDestroy()}
}
