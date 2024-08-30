package com.mohammadkk.mymusicplayer.dialogs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore.Audio.AudioColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.BaseActivity
import com.mohammadkk.mymusicplayer.activities.PlayerListActivity
import com.mohammadkk.mymusicplayer.databinding.DialogRecyclerBinding
import com.mohammadkk.mymusicplayer.extensions.errorToast
import com.mohammadkk.mymusicplayer.extensions.hasNotificationApi
import com.mohammadkk.mymusicplayer.extensions.hasPermission
import com.mohammadkk.mymusicplayer.services.ScannerService
import com.mohammadkk.mymusicplayer.utils.FileUtils
import java.io.File

class ScanMediaFoldersDialog : DialogFragment() {
    private var initialPath: String = ""
    private var parentFolder: File? = null
    private var parentContent: List<File>? = null
    private var rootAdapter: ScanAdapter? = null
    private var isOtgDialog = false
    private var canGoUp = false
    private var isRootStorage = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        isOtgDialog = arguments?.getBoolean("is_otg_dialog") ?: false
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (!requireContext().hasPermission(Constant.STORAGE_PERMISSION)) {
            return MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.error_label)
                .setMessage(R.string.permission_storage_denied)
                .setPositiveButton(android.R.string.ok, null)
                .create()
        }
        initialPath = android.os.Environment.getExternalStorageDirectory().absolutePath
        val mSavedInstanceState = savedInstanceState ?: Bundle()
        if (!mSavedInstanceState.containsKey("current_path")) {
            mSavedInstanceState.putString("current_path", initialPath)
        }
        parentFolder = File(mSavedInstanceState.getString("current_path", File.pathSeparator))
        isRootStorage = mSavedInstanceState.getBoolean("is_root_storage") || isOtgDialog
        canGoUp = if (!isRootStorage) parentFolder?.parent != null else false
        parentContent = if (!isRootStorage) listFiles() else getListStorages()
        rootAdapter = ScanAdapter(getContentsArray())
        val binding = DialogRecyclerBinding.inflate(layoutInflater)
        binding.dialogRecycler.adapter = rootAdapter
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (!isRootStorage) parentFolder?.absolutePath else "RootStorage")
            .setCancelable(false)
            .setView(binding.root)

        if (!isOtgDialog) {
            builder.setPositiveButton(R.string.directory_scan) { _, _ ->
                val absolutePath = parentFolder?.absolutePath
                (activity as? BaseActivity)?.run {
                    if (hasNotificationApi() && absolutePath != null) {
                        val serviceIntent = Intent(this, ScannerService::class.java)
                        serviceIntent.putExtra(AudioColumns.DATA, absolutePath)
                        try {
                            startService(serviceIntent)
                        } catch (e: Exception) {
                            errorToast(e)
                        }
                    }
                }
            }
        }
        return builder.setNegativeButton(android.R.string.cancel) { _, _ ->
            dismiss()
        }.create()
    }
    private fun selectionDirectory(i: Int) {
        if (canGoUp && i == 0) {
            if (parentFolder?.absolutePath == initialPath) {
                canGoUp = false
                isRootStorage = true
                parentContent = getListStorages()
                val baseDialog = dialog as AlertDialog
                baseDialog.setTitle("Root Storage")
                rootAdapter?.reload()
                return
            }
            parentFolder = parentFolder?.parentFile
            val countSlash = parentFolder?.absolutePath?.count { it == '/' } ?: 0
            if (countSlash <= 1) {
                canGoUp = false
                isRootStorage = true
                parentContent = getListStorages()
                val baseDialog = dialog as AlertDialog
                baseDialog.setTitle("Root Storage")
                rootAdapter?.reload()
                return
            }
        } else {
            parentFolder = parentContent?.get(if (canGoUp) i - 1 else i)
            isRootStorage = false
            canGoUp = true
        }
        reloadDialog()
    }
    private fun reloadDialog() {
        parentContent = listFiles()
        val baseDialog = dialog as AlertDialog
        baseDialog.setTitle(parentFolder?.absolutePath)
        rootAdapter?.reload()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("current_path", parentFolder?.absolutePath ?: initialPath)
        outState.putBoolean("is_root_storage", isRootStorage)
    }
    private fun listFiles(): List<File>? {
        val results = arrayListOf<File>()
        parentFolder?.listFiles()?.forEach { fi ->
            if (fi.isDirectory && !fi.isHidden) {
                results.add(fi)
            }
        }
        if (results.isNotEmpty()) {
            return results.sortedWith { o1, o2 ->
                o1.name.compareTo(o2.name, true)
            }
        }
        return null
    }
    private fun getListStorages(): List<File> {
        val distance = HashSet<String>()
        val result = ArrayList<File>()
        FileUtils.listRoots().forEach { f ->
            if (distance.add(f.second.absolutePath)) {
                if (!isOtgDialog) {
                    result.add(f.second)
                } else {
                    if (f.first.contains("External")) {
                        result.add(f.second)
                    }
                }
            }
        }
        return result
    }
    private fun getContentsArray(): List<Pair<String, String>> {
        val results = arrayListOf<Pair<String, String>>()
        if (canGoUp) results.add(".." to "..")
        parentContent?.forEach { f -> results.add(f.name to f.path) }
        return results
    }
    private inner class ScanAdapter(private var items: List<Pair<String, String>>) : RecyclerView.Adapter<ScanAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(inflater.inflate(R.layout.list_scan_dialog, parent, false))
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val fileItem = items[position]
            holder.title.text = fileItem.first
            holder.itemView.setOnClickListener {
                if (!isOtgDialog) {
                    selectionDirectory(position)
                } else with(requireActivity()) {
                    dismiss()
                    BaseSettings.getInstance().otgPartition = fileItem.second
                    val intent = Intent(this, PlayerListActivity::class.java)
                    intent.putExtra("otg", true)
                    startActivity(intent)
                }
            }
        }
        fun reload() {
            items = getContentsArray()
            notifyDataSetChanged()
        }
        override fun getItemCount(): Int {
            return items.size
        }
        private inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.title)
        }
    }
    companion object {
        fun create(isOtg: Boolean, manager: FragmentManager) = ScanMediaFoldersDialog().run {
            arguments = bundleOf("is_otg_dialog" to isOtg)
            show(manager, "SCAN_MEDIAS_DIALOG")
        }
    }
}