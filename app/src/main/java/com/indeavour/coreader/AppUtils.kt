package com.indeavour.coreader

import android.widget.Toast
import android.content.Context


object AppUtils{
    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}