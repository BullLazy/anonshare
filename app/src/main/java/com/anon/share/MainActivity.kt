package com.anon.share

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val btn = Button(this).apply {
            text = "Dosya seç"
            setOnClickListener { pick() }
        }
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 32, 0, 0)
        }
        root.addView(btn)
        root.addView(status)
        setContentView(root)
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != 1 || resultCode != RESULT_OK) return
        Thread {
            val msg = try { process(uri) } catch (e: Exception) { "Hata: ${e.message}" }
            runOnUiThread { status.text = msg }
        }.start()
    }

    private fun process(uri: Uri): String {
        val mime = contentResolver.getType(uri) ?: ""
        val name = UUID.randomUUID().toString().replace("-", "")
        return if (mime == "image/jpeg" || mime == "image/png" || mime == "image/webp") {
            val png = mime == "image/png"
            val out = File(cacheDir, name + if (png) ".png" else ".jpg")
            stripImage(uri, out, png)
            "Metadata temizlendi.\n${out.name}\n${out.length() / 1024} KB"
        } else {
            val out = File(cacheDir, "$name.bin")
            contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
            "Bu dosya türünde metadata temizleme henüz yok.\n${out.name}\n${out.length() / 1024} KB"
        }
    }

    private fun stripImage(uri: Uri, out: File, png: Boolean) {
        val orientation = contentResolver.openInputStream(uri)!!.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        var bmp = contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            ?: throw Exception("Görsel okunamadı")
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        out.outputStream().use {
            if (png) bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            else bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
    }
}
