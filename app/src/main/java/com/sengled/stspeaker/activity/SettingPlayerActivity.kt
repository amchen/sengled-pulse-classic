package com.sengled.stspeaker.activity

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import com.sengled.stspeaker.R
import com.sengled.stspeaker.widget.SettingsMenuPopupWindow

/**
 * Lists installed music apps (from R.array.soundapp_List); tapping one
 * launches it so it can play through the Pulse speaker.
 * Ported from the original com.sengled.stspeaker.activity.SettingPlayerActivity
 * (layout activity_setting_player, rows adapter_app_list).
 */
class SettingPlayerActivity : Activity() {

    data class AppInfo(
        var appName: String = "",
        var packageName: String = "",
        var appIcon: Drawable? = null,
        var versionName: String? = "",
        var versionCode: Int = 0
    )

    private lateinit var lvApp: ListView
    private lateinit var btnMenu: Button
    private var menuWindow: SettingsMenuPopupWindow? = null
    private val soundAppList = ArrayList<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_setting_player)
        initUi()
    }

    private fun initUi() {
        getSoundAppList()
        lvApp = findViewById(R.id.lv_playlist)
        lvApp.adapter = AppRowAdapter()
        lvApp.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val info = soundAppList[position]
                val launch = packageManager.getLaunchIntentForPackage(info.packageName)
                if (launch != null) startActivity(launch)
            }
        btnMenu = findViewById(R.id.btn_menu)
        btnMenu.setOnClickListener {
            menuWindow = SettingsMenuPopupWindow(this, 3)
            menuWindow?.showAsDropDown(findViewById(R.id.control_actionbar))
            menuWindow?.setOnDismissListener(PopupWindow.OnDismissListener {
                btnMenu.setBackgroundResource(R.drawable.btn_menu_normal)
            })
        }
    }

    private fun getSoundAppList() {
        soundAppList.clear()
        val preAppList = resources.getStringArray(R.array.soundapp_List)
        val pm = packageManager
        val installed = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        } catch (_: Exception) {
            emptyList()
        }
        for (pkg in installed) {
            for (pre in preAppList) {
                if (pkg.packageName == pre) {
                    val info = AppInfo(packageName = pkg.packageName)
                    try {
                        val appInfo = pkg.applicationInfo ?: continue
                        info.appIcon = appInfo.loadIcon(pm)
                        info.appName = appInfo.loadLabel(pm).toString()
                        info.versionName = pkg.versionName
                        @Suppress("DEPRECATION")
                        info.versionCode = pkg.versionCode
                        soundAppList.add(info)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private inner class AppRowAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(this@SettingPlayerActivity)

        override fun getCount(): Int = soundAppList.size
        override fun getItem(position: Int): Any = soundAppList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.adapter_app_list, parent, false)
            val icon = view.findViewById<ImageView>(R.id.iv_appicon)
            val name = view.findViewById<TextView>(R.id.tv_appname)
            val info = soundAppList[position]
            icon.setImageDrawable(info.appIcon)
            name.text = info.appName
            return view
        }
    }
}
