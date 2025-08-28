package com.choruscoldchain.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.choruscoldchain.R
import com.choruscoldchain.databinding.DialogPermissionBinding
import com.choruscoldchain.permissions.PermissionDialogType
import com.choruscoldchain.permissions.PermissionType

/**
 * Permission dialog fragment that follows the exact same structure and styling
 * as the bluetooth dialog but with configurable content for different permission types.
 */
class PermissionDialogFragment : DialogFragment() {
    
    companion object {
        private const val ARG_PERMISSION_TYPE = "permission_type"
        private const val ARG_DIALOG_TYPE = "dialog_type"
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_ICON_RES = "icon_res"
        private const val ARG_PRIMARY_BUTTON_TEXT = "primary_button_text"
        private const val ARG_SECONDARY_BUTTON_TEXT = "secondary_button_text"
        
        /**
         * Create a permission dialog with default content based on permission type
         */
        fun newInstance(
            permissionType: PermissionType,
            dialogType: PermissionDialogType = PermissionDialogType.INITIAL_REQUEST
        ): PermissionDialogFragment {
            return PermissionDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_PERMISSION_TYPE, permissionType)
                    putSerializable(ARG_DIALOG_TYPE, dialogType)
                }
            }
        }
        
        /**
         * Create a permission dialog with custom content
         */
        fun newInstance(
            @StringRes titleRes: Int,
            @StringRes messageRes: Int,
            iconRes: Int,
            @StringRes primaryButtonTextRes: Int,
            @StringRes secondaryButtonTextRes: Int,
            dialogType: PermissionDialogType = PermissionDialogType.INITIAL_REQUEST
        ): PermissionDialogFragment {
            return PermissionDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE, titleRes)
                    putInt(ARG_MESSAGE, messageRes)
                    putInt(ARG_ICON_RES, iconRes)
                    putInt(ARG_PRIMARY_BUTTON_TEXT, primaryButtonTextRes)
                    putInt(ARG_SECONDARY_BUTTON_TEXT, secondaryButtonTextRes)
                    putSerializable(ARG_DIALOG_TYPE, dialogType)
                }
            }
        }
    }
    
    private var _binding: DialogPermissionBinding? = null
    private val binding get() = _binding!!
    
    private var permissionType: PermissionType? = null
    private var dialogType: PermissionDialogType = PermissionDialogType.INITIAL_REQUEST
    
    // Callbacks
    private var onPrimaryButtonClick: (() -> Unit)? = null
    private var onSecondaryButtonClick: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismiss?.invoke()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.DialogTheme)
        
        arguments?.let { args ->
            permissionType = args.getSerializable(ARG_PERMISSION_TYPE) as? PermissionType
            dialogType = args.getSerializable(ARG_DIALOG_TYPE) as? PermissionDialogType 
                ?: PermissionDialogType.INITIAL_REQUEST
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDialogContent()
        setupButtonClickListeners()
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
            // Set proper width and margins with better sizing
            attributes = attributes.apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                // Ensure dialog doesn't get cut off
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }
        }
        return dialog
    }
    
    private fun setupDialogContent() {
        val args = arguments ?: return
        
        // Determine content based on arguments
        val titleRes = args.getInt(ARG_TITLE, -1)
        val messageRes = args.getInt(ARG_MESSAGE, -1)
        val iconRes = args.getInt(ARG_ICON_RES, -1)
        val primaryButtonTextRes = args.getInt(ARG_PRIMARY_BUTTON_TEXT, -1)
        val secondaryButtonTextRes = args.getInt(ARG_SECONDARY_BUTTON_TEXT, -1)
        
        if (titleRes != -1) {
            // Use custom content
            binding.tvPermissionTitle.setText(titleRes)
            binding.tvPermissionMessage.setText(messageRes)
            binding.ivPermissionIcon.setImageResource(iconRes)
            binding.btnSettings.setText(primaryButtonTextRes)
            binding.btnCancel.setText(secondaryButtonTextRes)
        } else {
            // Use permission type content
            permissionType?.let { type ->
                setupContentForPermissionType(type)
            }
        }
        
        // Apply dialog type specific styling
        applyDialogTypeStyling()
    }
    
    private fun setupContentForPermissionType(permissionType: PermissionType) {
        android.util.Log.d("PermissionDialog", "Setting up content for permission type: $permissionType")
        binding.apply {
            tvPermissionTitle.setText(permissionType.titleRes)
            tvPermissionMessage.setText(permissionType.messageRes)
            ivPermissionIcon.setImageResource(permissionType.iconRes)
            
            // Set button text based on dialog type
            when (dialogType) {
                PermissionDialogType.INITIAL_REQUEST -> {
                    btnSettings.setText(R.string.btn_grant_permission)
                    btnCancel.setText(R.string.btn_cancel)
                }
                PermissionDialogType.RATIONALE -> {
                    btnSettings.setText(R.string.btn_grant_permission)
                    btnCancel.setText(R.string.btn_cancel)
                }
                PermissionDialogType.SETTINGS_REDIRECT -> {
                    btnSettings.setText(R.string.btn_open_settings)
                    btnCancel.setText(R.string.btn_cancel)
                }
                PermissionDialogType.RUNTIME_REVOCATION -> {
                    btnSettings.setText(R.string.btn_open_settings)
                    btnCancel.setText(R.string.btn_cancel)
                }
                PermissionDialogType.PARTIAL_GRANT -> {
                    btnSettings.setText(R.string.btn_grant_permission)
                    btnCancel.setText(R.string.btn_cancel)
                }
                PermissionDialogType.SERVICE_DISABLED -> {
                    btnSettings.setText(R.string.btn_enable_service)
                    btnCancel.setText(R.string.btn_cancel)
                }
            }
        }
    }
    
    private fun applyDialogTypeStyling() {
        // Always tint icon with primary color for consistency
        binding.ivPermissionIcon.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.primary)
        )
    }
    
    private fun setupButtonClickListeners() {
        binding.apply {
            btnSettings.setOnClickListener {
                onPrimaryButtonClick?.invoke()
                dismiss()
            }
            
            btnCancel.setOnClickListener {
                onSecondaryButtonClick?.invoke()
                dismiss()
            }
        }
    }
    
    // override fun onDismiss(dialog: android.content.DialogInterface) {
    //     super.onDismiss(dialog)
    //     onDismiss?.invoke()
    // }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * Set callback for primary button click
     */
    fun setOnPrimaryButtonClickListener(listener: () -> Unit) {
        onPrimaryButtonClick = listener
    }
    
    /**
     * Set callback for secondary button click
     */
    fun setOnSecondaryButtonClickListener(listener: () -> Unit) {
        onSecondaryButtonClick = listener
    }
    
    /**
     * Set callback for dialog dismiss
     */
    fun setOnDismissListener(listener: () -> Unit) {
        onDismiss = listener
    }
    
    /**
     * Get the permission type associated with this dialog
     */
    fun getPermissionType(): PermissionType? = permissionType
    
    /**
     * Get the dialog type
     */
    fun getDialogType(): PermissionDialogType = dialogType
}
