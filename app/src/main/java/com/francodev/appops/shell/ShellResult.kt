package com.francodev.appops.shell

import android.os.Parcel
import android.os.Parcelable

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) : Parcelable {

    val ok: Boolean get() = exitCode == 0

    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(exitCode)
        parcel.writeString(stdout)
        parcel.writeString(stderr)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ShellResult> {
        override fun createFromParcel(parcel: Parcel): ShellResult {
            return ShellResult(parcel)
        }

        override fun newArray(size: Int): Array<ShellResult?> {
            return arrayOfNulls(size)
        }
    }
}