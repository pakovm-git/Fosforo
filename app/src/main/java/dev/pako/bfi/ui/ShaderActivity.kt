package dev.pako.bfi.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import dev.pako.bfi.R
import dev.pako.bfi.effects.*

/** Uses the same bounded cards and commit queue as every other settings screen. */
class ShaderActivity:ScreenActivity() {
    private var loadedId:String?=null
    private var building=false
    private lateinit var parameters:LinearLayout
    private lateinit var selector:Spinner
    private lateinit var category:Spinner
    private lateinit var search:EditText
    private lateinit var download:Button
    private lateinit var cancelDownload:Button
    private lateinit var libraryStatus:TextView
    private var allChoices=emptyList<ShaderChoice>()
    private var visibleChoices=emptyList<ShaderChoice>()
    private var categories=listOf("all")
    private var libraryRevision:String?=null
    private var unsubscribeLibrary:(()->Unit)?=null
    private var selectionGeneration=0
    private var restoredCategory:String?=null
    private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);title(R.string.crt_settings)
        restoredCategory=savedInstanceState?.getString("shaderCategory")
        val cards=boundedCards();val left=cards.preview;parameters=cards.controls
        section(R.string.shader_title,left)
        category=Spinner(this).apply{minimumHeight=dp(48);contentDescription=getString(R.string.shader_category)}
        left.addView(category,LinearLayout.LayoutParams(-1,-2))
        search=EditText(this).apply{hint=getString(R.string.search_shaders);setSingleLine(true);minHeight=dp(48);setTextColor(getColor(R.color.bfi_text_primary));inputType=android.text.InputType.TYPE_CLASS_TEXT}
        left.addView(search,LinearLayout.LayoutParams(-1,-2))
        search.setText(savedInstanceState?.getString("shaderSearch").orEmpty())
        selector=Spinner(this).apply{minimumHeight=dp(48);contentDescription=getString(R.string.shader_title)}
        left.addView(selector,LinearLayout.LayoutParams(-1,-2))
        download=button(R.string.download_shader_library,left){
            AlertDialog.Builder(this).setTitle(R.string.download_shader_library).setMessage(R.string.download_library_confirm)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.download_shader_library){_,_->ShaderLibrary.download(applicationContext)}.show()
        }
        cancelDownload=button(R.string.cancel_download,left){ShaderLibrary.cancel()}
        libraryStatus=text(R.string.library_help,left,true)
        button(R.string.import_shader,left){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),201)}
        text(R.string.import_help,left,true)
        text(R.string.capture_privacy,left,true)
        button(R.string.third_party_notices,left){
            AlertDialog.Builder(this).setTitle(R.string.third_party_notices)
                .setMessage(assets.open("THIRD-PARTY-NOTICES.txt").bufferedReader().use{it.readText()})
                .setPositiveButton(android.R.string.ok,null).show()
        }
        refreshChoices()
        category.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?)=Unit
            override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long){if(!building && position==category.selectedItemPosition)filterChoices()}
        }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){filterChoices()}
            override fun afterTextChanged(s:Editable?)=Unit
        })
        selector.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?)=Unit
            override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
                if(building || position!=selector.selectedItemPosition)return
                val choice=visibleChoices.getOrNull(position-1)?:return
                val settings=repository.settings?:return
                if(choice.id==settings.shaderId)return
                val generation=++selectionGeneration
                worker.execute {
                    try {
                        val definition=ShaderCatalog.load(applicationContext,choice.id)
                        definition.passes.forEachIndexed { index,pass ->
                            shaderTargetFormat(definition.settings,index,pass.source)
                            ShaderSource.compile(pass.source,true,pass.path.endsWith(".slang",true))
                            ShaderSource.compile(pass.source,false,pass.path.endsWith(".slang",true))
                        }
                        runOnUiThread {if(!isDestroyed && generation==selectionGeneration){repository.settings?.let{save(it.copy(shaderId=choice.id,shaderValues=emptyMap()))};loadedId=null;showParameters()}}
                    }catch(error:Exception){runOnUiThread {if(!isDestroyed && generation==selectionGeneration){filterChoices();Toast.makeText(this@ShaderActivity,getString(R.string.unsupported_shader,error.message),Toast.LENGTH_LONG).show()}}}
                }
            }
        }
    }
    private fun refreshChoices() {
        if(repository.settings==null)return
        building=true
        val selectedCategory=restoredCategory ?: categories.getOrNull(category.selectedItemPosition)?:"all"
        restoredCategory=null
        allChoices=ShaderCatalog.choices(this)
        categories=listOf("all")+allChoices.map{it.category}.distinct().sorted()
        category.adapter=adapter(categories.map{categoryLabel(it)})
        category.setSelection(categories.indexOf(selectedCategory).coerceAtLeast(0));building=false
        filterChoices()
        showParameters()
    }
    private fun adapter(labels:List<String>)=ArrayAdapter(this,android.R.layout.simple_spinner_item,labels).apply{setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)}
    private fun categoryLabel(key:String)=when(key){"all"->getString(R.string.all_shader_categories);"imported"->getString(R.string.imported_shaders);"crt"->"CRT";"vhs"->"VHS";else->key.replace('-',' ').replaceFirstChar{it.uppercaseChar()}}
    private fun filterChoices() {
        if(building || !::selector.isInitialized)return
        selectionGeneration++
        val key=categories.getOrNull(category.selectedItemPosition)?:"all"
        val query=search.text.toString().trim()
        visibleChoices=allChoices.filter{(key=="all" || it.category==key) && (it.label.contains(query,true) || it.assetPath.contains(query,true))}
        val selected=allChoices.firstOrNull{it.id==repository.settings?.shaderId}
        building=true
        selector.adapter=adapter(listOf(getString(R.string.selected_shader,selected?.label?:getString(R.string.shader_missing)))+visibleChoices.map{it.label})
        selector.setSelection(0);building=false
    }
    override fun onStart() {
        super.onStart()
        unsubscribeLibrary=ShaderLibrary.subscribe {
            val status=ShaderLibrary.status
            download.isEnabled=!status.busy
            cancelDownload.visibility=if(status.busy)android.view.View.VISIBLE else android.view.View.GONE
            libraryStatus.text=status.message.ifEmpty{getString(R.string.library_help)}
            if(status.revision!=null && libraryRevision!=status.revision){libraryRevision=status.revision;refreshChoices()}
        }
    }
    override fun onStop(){unsubscribeLibrary?.invoke();unsubscribeLibrary=null;super.onStop()}
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("shaderCategory",categories.getOrNull(category.selectedItemPosition))
        outState.putString("shaderSearch",search.text.toString())
        super.onSaveInstanceState(outState)
    }
    private fun showParameters() {
        val settings=repository.settings?:return
        if(loadedId==settings.shaderId)return
        loadedId=settings.shaderId;parameters.removeAllViews()
        try {
            val definition=ShaderCatalog.load(this,settings.shaderId)
            section(R.string.shader_parameters,parameters)
            for(parameter in definition.parameters) {
                if(!parameter.editable){parameters.addView(TextView(this).apply{text=parameter.label;setTextColor(getColor(R.color.bfi_text_secondary))});continue}
                val value=parameter.normalize(settings.shaderValues[parameter.id]?:parameter.defaultValue)
                val label=TextView(this).apply { text="${parameter.label}: $value";textSize=16f;setTextColor(getColor(R.color.bfi_text_primary)) }
                parameters.addView(label)
                if(parameter.toggle) {
                    val toggle=Switch(this).apply{minHeight=dp(48);text=parameter.label;isChecked=value==1f}
                    parameters.addView(toggle)
                    toggle.setOnCheckedChangeListener{_,checked->saveParameter(parameter,if(checked)1f else 0f);label.text="${parameter.label}: ${if(checked)1 else 0}"}
                }else {
                    val values=parameter.sliderValues()
                    val slider=SeekBar(this).apply{max=values.lastIndex;progress=values.indexOf(value).takeIf{it>=0}?:values.indices.minBy{kotlin.math.abs(values[it]-value)};contentDescription=parameter.label}
                    parameters.addView(slider,LinearLayout.LayoutParams(-1,dp(48)))
                    slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                        override fun onStartTrackingTouch(seekBar:SeekBar)=Unit
                        override fun onStopTrackingTouch(seekBar:SeekBar)=Unit
                        override fun onProgressChanged(seekBar:SeekBar,progress:Int,fromUser:Boolean){if(fromUser){val next=values[progress];label.text="${parameter.label}: $next";saveParameter(parameter,next)}}
                    })
                }
            }
        }catch(error:Exception){text(R.string.shader_missing,parameters);Toast.makeText(this,error.message,Toast.LENGTH_LONG).show()}
    }
    private fun saveParameter(parameter:ShaderParameter,value:Float) {
        val settings=repository.settings?:return
        save(settings.copy(shaderValues=settings.shaderValues+(parameter.id to parameter.normalize(value))))
    }
    @Deprecated("Native Activity result callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
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
                            runOnUiThread {if(!isDestroyed){repository.settings?.let{save(it.copy(shaderId=choice.id,shaderValues=emptyMap()))};loadedId=null;refreshChoices()}} }
                        catch(error:Exception){runOnUiThread{if(!isDestroyed)Toast.makeText(this,error.message,Toast.LENGTH_LONG).show()}}
                    }}
                    if(archive.presets.size==1)install(archive.presets.single())
                    else AlertDialog.Builder(this).setTitle(R.string.choose_import).setItems(archive.presets.toTypedArray()){_,index->install(archive.presets[index])}.setOnCancelListener{archive.close()}.show()
                }
            }catch(error:Exception){runOnUiThread{if(!isDestroyed)Toast.makeText(this,error.message,Toast.LENGTH_LONG).show()}}
        }
    }
    override fun refreshScreen(){if(::selector.isInitialized){if(allChoices.isEmpty())refreshChoices() else showParameters()}}
    override fun saveDraft()=Unit
    override fun restoreDefaults(){val settings=repository.settings?:return;save(settings.copy(shaderValues=emptyMap()));loadedId=null;showParameters()}
    override fun onDestroy(){worker.shutdown();super.onDestroy()}
}
